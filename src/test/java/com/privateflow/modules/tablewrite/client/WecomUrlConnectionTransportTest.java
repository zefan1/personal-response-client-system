package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WecomUrlConnectionTransportTest {

  @Test
  void sendsGetAndReturnsTheCompleteResponse() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond("/probe", 200, "{\"ok\":true}");

      WecomHttpResponse response = new WecomUrlConnectionTransport().send(
          URI.create(server.baseUrl() + "/probe?source=get"),
          "GET",
          Map.of(),
          new byte[0],
          Duration.ofSeconds(2));

      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("{\"ok\":true}");
      assertThat(server.lastMethod()).isEqualTo("GET");
      assertThat(server.lastQuery()).isEqualTo("source=get");
    }
  }

  @Test
  void sendsUtf8JsonPostAndReadsErrorResponses() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond("/probe", 400, "{\"errcode\":40058}");
      byte[] body = "{\"name\":\"Ada\"}".getBytes(StandardCharsets.UTF_8);

      WecomHttpResponse response = new WecomUrlConnectionTransport().send(
          URI.create(server.baseUrl() + "/probe"),
          "POST",
          Map.of("Content-Type", "application/json"),
          body,
          Duration.ofSeconds(2));

      assertThat(response.statusCode()).isEqualTo(400);
      assertThat(response.body()).isEqualTo("{\"errcode\":40058}");
      assertThat(server.lastMethod()).isEqualTo("POST");
      assertThat(server.lastJson().path("name").asText()).isEqualTo("Ada");
    }
  }
}
