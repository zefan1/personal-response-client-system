package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class WecomAccessTokenProviderTest {

  private static final String GET_TOKEN_PATH = "/cgi-bin/gettoken";

  @Test
  void firstGetReturnsTokenAndSecondGetUsesCache() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200, success("token-one", 600));
      WecomAccessTokenProvider provider = provider(server, new MutableClock());

      assertThat(provider.get()).isEqualTo("token-one");
      assertThat(provider.get()).isEqualTo("token-one");

      assertThat(server.requestCount()).isEqualTo(1);
      assertThat(server.lastQuery()).isEqualTo("corpid=corp+id&corpsecret=app-secret-value");
      assertThat(server.lastJson()).isEmpty();
    }
  }

  @Test
  void refreshesEarlyAndInvalidatesOnlyTheCurrentToken() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respondInOrder(GET_TOKEN_PATH,
          new WecomTestHttpServer.Reply(200, success("token-one", 600)),
          new WecomTestHttpServer.Reply(200, success("token-two", 600)),
          new WecomTestHttpServer.Reply(200, success("token-three", 600)));
      MutableClock clock = new MutableClock();
      WecomAccessTokenProvider provider = provider(server, clock);

      assertThat(provider.get()).isEqualTo("token-one");
      clock.advance(Duration.ofSeconds(301));
      assertThat(provider.get()).isEqualTo("token-two");
      provider.invalidate("token-two");
      assertThat(provider.get()).isEqualTo("token-three");

      assertThat(server.requestCount()).isEqualTo(3);
    }
  }

  @Test
  void invalidatingDifferentTokenKeepsCurrentCacheEntry() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200, success("token-one", 600));
      WecomAccessTokenProvider provider = provider(server, new MutableClock());

      assertThat(provider.get()).isEqualTo("token-one");
      provider.invalidate("other-token");
      assertThat(provider.get()).isEqualTo("token-one");

      assertThat(server.requestCount()).isEqualTo(1);
    }
  }

  @Test
  void nonzeroErrcodeIsSanitized() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200,
          "{\"errcode\":40013,\"errmsg\":\"invalid corp id\\nwith detail\"}");
      WecomAccessTokenProvider provider = provider(server, new MutableClock());

      assertThatThrownBy(provider::get)
          .isInstanceOf(WecomSmartSheetException.class)
          .hasMessageContaining("gettoken")
          .hasMessageContaining("40013")
          .satisfies(error -> {
            assertThat(error.getMessage()).doesNotContain("corp id", "app-secret-value", "corpid=", "corpsecret=")
                .doesNotContain("\n", "\r");
            assertThat(((WecomSmartSheetException) error).operation()).isEqualTo("gettoken");
            assertThat(((WecomSmartSheetException) error).errcode()).isEqualTo(40013);
          });
    }
  }

  @Test
  void transportAndMalformedResponsesAreSanitized() throws Exception {
    assertSanitizedFailure(503, "unavailable");
    assertSanitizedFailure(200, "not-json");
    assertSanitizedFailure(200, "{\"errcode\":0,\"expires_in\":600}");
    assertSanitizedFailure(200, "{\"errcode\":0,\"access_token\":\"  \",\"expires_in\":600}");
    assertSanitizedFailure(200, "{\"errcode\":0,\"access_token\":\"token-one\",\"expires_in\":0}");
  }

  @Test
  void unavailableLocalServerIsSanitized() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      WecomAccessTokenProvider provider = provider(server, new MutableClock());
      server.close();

      assertThatThrownBy(provider::get)
          .isInstanceOf(WecomSmartSheetException.class)
          .hasMessageContaining("network request failed")
          .satisfies(error -> assertThat(error.getMessage()).doesNotContain(
              "corp id", "app-secret-value", "corpid=", "corpsecret="));
    }
  }

  @Test
  void shortLifetimeStillRemainsCachedForAPositiveDuration() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200, success("token-one", 10));
      MutableClock clock = new MutableClock();
      WecomAccessTokenProvider provider = provider(server, clock);

      assertThat(provider.get()).isEqualTo("token-one");
      assertThat(provider.get()).isEqualTo("token-one");
      clock.advance(Duration.ofSeconds(6));
      assertThatThrownBy(provider::get).isInstanceOf(WecomSmartSheetException.class);

      assertThat(server.requestCount()).isEqualTo(2);
    }
  }

  private void assertSanitizedFailure(int status, String body) throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, status, body);
      WecomAccessTokenProvider provider = provider(server, new MutableClock());

      assertThatThrownBy(provider::get)
          .isInstanceOf(WecomSmartSheetException.class)
          .hasMessageContaining("gettoken")
          .satisfies(error -> assertThat(error.getMessage()).doesNotContain(
              "corp id", "app-secret-value", "corpid=", "corpsecret=", "not-json", "token-one"));
    }
  }

  private WecomAccessTokenProvider provider(WecomTestHttpServer server, Clock clock) {
    return new WecomAccessTokenProvider(new ObjectMapper(), configured(server), HttpClient.newHttpClient(), clock);
  }

  private WecomSmartSheetConfig configured(WecomTestHttpServer server) {
    return new WecomSmartSheetConfig(
        server.baseUrl(), "corp id", "app-secret-value", "document-1", "sheet-1", "view-1",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }

  private static String success(String token, long expiresIn) {
    return "{\"errcode\":0,\"errmsg\":\"ok\",\"access_token\":\"" + token
        + "\",\"expires_in\":" + expiresIn + "}";
  }

  private static final class MutableClock extends Clock {

    private Instant instant = Instant.parse("2026-07-24T00:00:00Z");

    void advance(Duration duration) {
      instant = instant.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }
}
