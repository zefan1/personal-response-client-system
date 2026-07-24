package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.stream.Stream;

class WecomSmartSheetApiClientTest {

  private static final String GET_FIELDS_PATH = "/cgi-bin/wedoc/smartsheet/get_fields";
  private static final String GET_RECORDS_PATH = "/cgi-bin/wedoc/smartsheet/get_records";
  private static final String ADD_RECORDS_PATH = "/cgi-bin/wedoc/smartsheet/add_records";
  private static final String UPDATE_RECORDS_PATH = "/cgi-bin/wedoc/smartsheet/update_records";
  private static final String COMPLETE_SUCCESS = "{\"errcode\":0,\"errmsg\":\"ok\",\"total\":1,"
      + "\"fields\":[{\"field_id\":\"f_name\",\"title\":\"Name\",\"type\":\"text\"}],"
      + "\"has_more\":true,\"next\":\"cursor-1\",\"records\":[{\"record_id\":\"rec-1\","
      + "\"values\":{\"Name\":\"Ada\"}}]}";

  @TestFactory
  Stream<DynamicTest> postsEachOperationToItsFixedPathAndPreservesCompleteOfficialResponses() {
    return Stream.of(
        new OperationFixture("get_fields", GET_FIELDS_PATH),
        new OperationFixture("get_records", GET_RECORDS_PATH),
        new OperationFixture("add_records", ADD_RECORDS_PATH),
        new OperationFixture("update_records", UPDATE_RECORDS_PATH))
        .map(operation -> DynamicTest.dynamicTest(operation.name(), () -> {
          try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
            server.respond(operation.path(), 200, COMPLETE_SUCCESS);

            var result = client(server, tokens("token-one"))
                .post(operation.name(), Map.of("docid", "s3_doc"), Duration.ofSeconds(2));

            assertThat(server.lastMethod()).isEqualTo("POST");
            assertThat(server.lastJson().path("docid").asText()).isEqualTo("s3_doc");
            assertThat(decodeQuery(server.lastQuery()).get("access_token")).isEqualTo("token-one");
            assertThat(result).isEqualTo(new ObjectMapper().readTree(COMPLETE_SUCCESS));
            assertThat(result.path("errmsg").asText()).isEqualTo("ok");
            assertThat(result.path("total").intValue()).isEqualTo(1);
            assertThat(result.path("fields").get(0).path("field_id").asText()).isEqualTo("f_name");
            assertThat(result.path("has_more").booleanValue()).isTrue();
            assertThat(result.path("next").asText()).isEqualTo("cursor-1");
            assertThat(result.path("records").get(0).path("record_id").asText()).isEqualTo("rec-1");
          }
        }));
  }

  @Test
  void retriesOnceWithANewTokenAfter42001() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respondInOrder(GET_FIELDS_PATH,
          new WecomTestHttpServer.Reply(200, "{\"errcode\":42001,\"errmsg\":\"expired\"}"),
          new WecomTestHttpServer.Reply(200, "{\"errcode\":0,\"fields\":[]}"));
      WecomAccessTokenProvider tokens = mock(WecomAccessTokenProvider.class);
      when(tokens.get(any(Duration.class))).thenReturn("old-token", "new-token");

      assertThat(client(server, tokens).post("get_fields", Map.of(), Duration.ofSeconds(2))
          .path("errcode").intValue()).isZero();

      assertThat(server.requestCount()).isEqualTo(2);
      assertThat(decodeQuery(server.lastQuery()).get("access_token")).isEqualTo("new-token");
      verify(tokens).invalidate("old-token");
    }
  }

  @Test
  void retriesOnlyOnceForRepeated40014() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respondInOrder(GET_FIELDS_PATH,
          new WecomTestHttpServer.Reply(200, "{\"errcode\":40014,\"errmsg\":\"first\"}"),
          new WecomTestHttpServer.Reply(200, "{\"errcode\":40014,\"errmsg\":\"second\"}"));
      WecomAccessTokenProvider tokens = mock(WecomAccessTokenProvider.class);
      when(tokens.get(any(Duration.class))).thenReturn("old-token", "new-token");

      assertThatThrownBy(() -> client(server, tokens).post("get_fields", Map.of(), Duration.ofSeconds(2)))
          .isInstanceOf(WecomSmartSheetException.class)
          .satisfies(error -> assertThat(((WecomSmartSheetException) error).errcode()).isEqualTo(40014));

      assertThat(server.requestCount()).isEqualTo(2);
      verify(tokens).invalidate("old-token");
    }
  }

  @Test
  void rejectsNonTokenErrorsWithoutInvalidating() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_FIELDS_PATH, 200,
          "{\"errcode\":40058,\"errmsg\":\"raw-response-secret token-one CorpID-sentinel app-secret-value\"}");
      WecomAccessTokenProvider tokens = tokens("token-one");

      assertThatThrownBy(() -> client(server, tokens).post("get_fields", Map.of(), Duration.ofSeconds(2)))
          .isInstanceOf(WecomSmartSheetException.class)
          .satisfies(error -> assertThrowableDoesNotContain(error,
              "raw-response-secret", "token-one", "CorpID-sentinel", "app-secret-value", "access_token"));

      assertThat(server.requestCount()).isEqualTo(1);
      verify(tokens, never()).invalidate("token-one");
    }
  }

  @TestFactory
  Stream<DynamicTest> rejectsTransportAndMalformedResponsesWithoutExposingBodies() {
    return Stream.of(
        new FailureCase(500, "raw-response-secret", "HTTP status 500"),
        new FailureCase(200, "not-json raw-response-secret", "response was not valid JSON"),
        new FailureCase(200, "", "response was not valid JSON"),
        new FailureCase(200, "[]", "response was not a JSON object"),
        new FailureCase(200, "{}", "response missing valid errcode"),
        new FailureCase(200, "{\"errcode\":1.5}", "response missing valid errcode"),
        new FailureCase(200, "{\"errcode\":2147483648}", "response missing valid errcode"))
        .map(failure -> DynamicTest.dynamicTest(failure.reason(), () -> {
          try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
            server.respond(GET_FIELDS_PATH, failure.status(), failure.body());
            assertThatThrownBy(() -> client(server, tokens("token-one"))
                .post("get_fields", Map.of("raw", "raw-request-value"), Duration.ofSeconds(2)))
                .isInstanceOf(WecomSmartSheetException.class)
                .hasMessageContaining("get_fields")
                .hasMessageContaining(failure.reason())
                .satisfies(error -> assertThrowableDoesNotContain(error,
                    "raw-response-secret", "not-json", "raw-request-value", "token-one", "access_token"));
          }
        }));
  }

  @TestFactory
  Stream<DynamicTest> rejectsTrailingJsonTokensWithoutExposingThem() {
    return Stream.of("{\"errcode\":0} trailing-secret", "{\"errcode\":0}{\"second\":1}")
        .map(body -> DynamicTest.dynamicTest("rejects trailing response tokens", () -> {
          try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
            server.respond(GET_FIELDS_PATH, 200, body);

            assertThatThrownBy(() -> client(server, tokens("token-one"))
                .post("get_fields", Map.of(), Duration.ofSeconds(2)))
                .isInstanceOf(WecomSmartSheetException.class)
                .hasMessageContaining("response was not valid JSON")
                .satisfies(error -> assertThrowableDoesNotContain(error, body, "trailing-secret", "second"));
          }
        }));
  }

  @Test
  void rejectsUnknownOperationsAndInvalidTimeoutsBeforeTokenOrNetwork() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      WecomAccessTokenProvider tokens = tokens("token-one");
      WecomSmartSheetApiClient client = client(server, tokens);

      assertThatThrownBy(() -> client.post("delete_records", Map.of(), Duration.ofSeconds(1)))
          .isInstanceOf(WecomSmartSheetException.class);
      assertThatThrownBy(() -> client.post(null, Map.of(), Duration.ofSeconds(1)))
          .isInstanceOf(WecomSmartSheetException.class)
          .satisfies(error -> assertThat(((WecomSmartSheetException) error).operation()).isEqualTo("request"));
      assertThatThrownBy(() -> client.post("token-one access_token raw-request-value", Map.of(), Duration.ofSeconds(1)))
          .isInstanceOf(WecomSmartSheetException.class)
          .satisfies(error -> {
            assertThat(((WecomSmartSheetException) error).operation()).isEqualTo("request");
            assertThrowableDoesNotContain(error, "token-one", "access_token", "raw-request-value");
          });
      assertThatThrownBy(() -> client.post("get_fields", Map.of(), null))
          .isInstanceOf(WecomSmartSheetException.class);
      assertThatThrownBy(() -> client.post("get_fields", Map.of(), Duration.ZERO))
          .isInstanceOf(WecomSmartSheetException.class);
      assertThatThrownBy(() -> client.post("get_fields", Map.of(), Duration.ofMillis(-1)))
          .isInstanceOf(WecomSmartSheetException.class);

      verify(tokens, never()).get(any(Duration.class));
      assertThat(server.requestCount()).isZero();
    }
  }

  @Test
  void rethrowsSanitizedTokenProviderExceptionsUnchanged() {
    WecomAccessTokenProvider tokens = mock(WecomAccessTokenProvider.class);
    WecomSmartSheetException tokenFailure = new WecomSmartSheetException("gettoken", 40013, "invalid corpid");
    when(tokens.get(any(Duration.class))).thenThrow(tokenFailure);

    assertThatThrownBy(() -> new WecomSmartSheetApiClient(new ObjectMapper(), configured("http://127.0.0.1"), tokens)
        .post("get_fields", Map.of(), Duration.ofSeconds(1)))
        .isSameAs(tokenFailure)
        .satisfies(error -> {
          WecomSmartSheetException wecomError = (WecomSmartSheetException) error;
          assertThat(wecomError.operation()).isEqualTo("gettoken");
          assertThat(wecomError.errcode()).isEqualTo(40013);
          assertThrowableDoesNotContain(error, "CorpID-sentinel", "app-secret-value", "token-one", "access_token");
        });
  }

  @Test
  void preservesSanitizedMissingEnvironmentVariableNames() {
    WecomSmartSheetConfig incomplete = new WecomSmartSheetConfig("http://127.0.0.1", "", "app-secret-value",
        "document-1", "sheet-1", "view-1", "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));

    assertThatThrownBy(() -> new WecomSmartSheetApiClient(new ObjectMapper(), incomplete, tokens("token-one"))
        .post("get_fields", Map.of(), Duration.ofSeconds(1)))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("WECOM_CORP_ID")
        .satisfies(error -> assertThrowableDoesNotContain(error, "app-secret-value", "document-1", "token-one"));
  }

  @Test
  void URLencodesTokensWithoutChangingTheirValue() throws Exception {
    String token = "token+&=\u4e2d\u6587";
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_FIELDS_PATH, 200, "{\"errcode\":0}");

      client(server, tokens(token)).post("get_fields", Map.of(), Duration.ofSeconds(2));

      assertThat(decodeQuery(server.lastQuery()).get("access_token")).isEqualTo(token);
    }
  }

  @Test
  void sendsTheExactCallerTimeout() {
    CapturingHttpClient http = new CapturingHttpClient();
    WecomSmartSheetApiClient client = new WecomSmartSheetApiClient(new ObjectMapper(), configured("http://127.0.0.1"),
        tokens("token-one"), http, () -> 0L);
    Duration timeout = Duration.ofMillis(1234);

    assertThat(client.post("get_fields", Map.of(), timeout).path("errcode").intValue()).isZero();

    assertThat(http.request.timeout()).contains(timeout);
    assertThat(http.request.headers().firstValue("Content-Type")).contains("application/json");
  }

  @Test
  void obtainsTokenWithTheCallsRemainingTimeout() {
    CapturingHttpClient http = new CapturingHttpClient();
    WecomAccessTokenProvider tokens = mock(WecomAccessTokenProvider.class);
    when(tokens.get()).thenReturn("legacy-token");
    when(tokens.get(any(Duration.class))).thenReturn("deadline-token");
    WecomSmartSheetApiClient client = new WecomSmartSheetApiClient(
        new ObjectMapper(), configured("http://127.0.0.1"), tokens, http);

    assertThat(client.post("get_fields", Map.of(), Duration.ofSeconds(2)).path("errcode").intValue()).isZero();

    verify(tokens).get(any(Duration.class));
    verify(tokens, never()).get();
  }

  @Test
  void decreasesOneDeadlineAcrossTokenRefreshAndBothHttpRequests() {
    MutableTicker ticker = new MutableTicker();
    List<Duration> tokenTimeouts = new ArrayList<>();
    AtomicInteger tokenCalls = new AtomicInteger();
    WecomAccessTokenProvider tokens = mock(WecomAccessTokenProvider.class);
    when(tokens.get(any(Duration.class))).thenAnswer(invocation -> {
      tokenTimeouts.add(invocation.getArgument(0));
      ticker.advance(Duration.ofMillis(100));
      return tokenCalls.getAndIncrement() == 0 ? "old-token" : "new-token";
    });
    DeadlineHttpClient http = new DeadlineHttpClient(ticker, Duration.ofMillis(200),
        "{\"errcode\":42001,\"errmsg\":\"expired\"}",
        "{\"errcode\":0,\"fields\":[]}");
    WecomSmartSheetApiClient client = new WecomSmartSheetApiClient(
        new ObjectMapper(), configured("http://127.0.0.1"), tokens, http, ticker);

    assertThat(client.post("get_fields", Map.of(), Duration.ofSeconds(2)).path("errcode").intValue()).isZero();

    assertThat(tokenTimeouts).containsExactly(Duration.ofSeconds(2), Duration.ofMillis(1700));
    assertThat(http.requests).extracting(request -> request.timeout().orElseThrow())
        .containsExactly(Duration.ofMillis(1900), Duration.ofMillis(1600));
    verify(tokens).invalidate("old-token");
  }

  @Test
  void exhaustedBudgetAfterFirstHttpDoesNotObtainAnotherTokenOrSendAnotherRequest() {
    MutableTicker ticker = new MutableTicker();
    List<Duration> tokenTimeouts = new ArrayList<>();
    WecomAccessTokenProvider tokens = mock(WecomAccessTokenProvider.class);
    when(tokens.get(any(Duration.class))).thenAnswer(invocation -> {
      tokenTimeouts.add(invocation.getArgument(0));
      ticker.advance(Duration.ofMillis(100));
      return "old-token";
    });
    DeadlineHttpClient http = new DeadlineHttpClient(ticker, Duration.ofMillis(900),
        "{\"errcode\":40014,\"errmsg\":\"expired\"}");
    WecomSmartSheetApiClient client = new WecomSmartSheetApiClient(
        new ObjectMapper(), configured("http://127.0.0.1"), tokens, http, ticker);

    assertThatThrownBy(() -> client.post("get_fields", Map.of(), Duration.ofSeconds(1)))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("timeout expired");

    assertThat(tokenTimeouts).containsExactly(Duration.ofSeconds(1));
    assertThat(http.requests).singleElement().satisfies(request ->
        assertThat(request.timeout()).contains(Duration.ofMillis(900)));
    verify(tokens).invalidate("old-token");
  }

  @Test
  void sanitizesInvalidUriIOExceptionAndInterruptedTransportFailures() {
    assertTransportFailure(new WecomSmartSheetApiClient(new ObjectMapper(), configured(
        "http://bad host/CorpID-sentinel/app-secret-value/raw-response-secret"), tokens("token-one"),
        HttpClient.newHttpClient()));
    assertTransportFailure(new WecomSmartSheetApiClient(new ObjectMapper(), configured("http://127.0.0.1"),
        tokens("token-one"), new FailingHttpClient(new IOException(
            "raw-response-secret CorpID-sentinel app-secret-value token-one access_token raw-request-value"))));
    try {
      assertTransportFailure(new WecomSmartSheetApiClient(new ObjectMapper(), configured("http://127.0.0.1"),
          tokens("token-one"), new FailingHttpClient(new InterruptedException(
              "raw-response-secret CorpID-sentinel app-secret-value token-one access_token raw-request-value"))));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void sanitizesJsonSerializationFailuresWithoutProductionTestHooks() {
    WecomSmartSheetApiClient client = new WecomSmartSheetApiClient(new ObjectMapper(), configured("http://127.0.0.1"),
        tokens("token-one"), new CapturingHttpClient());
    SelfReferencingBody body = new SelfReferencingBody();
    body.self = body;

    assertThatThrownBy(() -> client.post("get_fields", body, Duration.ofSeconds(1)))
        .isInstanceOf(WecomSmartSheetException.class)
        .satisfies(error -> assertThrowableDoesNotContain(error,
            "SelfReferencingBody", "token-one", "CorpID-sentinel", "app-secret-value", "access_token"));
  }

  private void assertTransportFailure(WecomSmartSheetApiClient client) {
    assertThatThrownBy(() -> client.post("get_fields", Map.of("value", "raw-request-value"), Duration.ofSeconds(1)))
        .isInstanceOf(WecomSmartSheetException.class)
        .satisfies(error -> assertThrowableDoesNotContain(error,
            "CorpID-sentinel", "app-secret-value", "token-one", "access_token", "raw-request-value",
            "raw-response-secret"));
  }

  private WecomSmartSheetApiClient client(WecomTestHttpServer server, WecomAccessTokenProvider tokens) {
    return new WecomSmartSheetApiClient(new ObjectMapper(), configured(server.baseUrl()), tokens);
  }

  private WecomAccessTokenProvider tokens(String value) {
    WecomAccessTokenProvider provider = mock(WecomAccessTokenProvider.class);
    when(provider.get(any(Duration.class))).thenReturn(value);
    return provider;
  }

  private WecomSmartSheetConfig configured(String baseUrl) {
    return new WecomSmartSheetConfig(baseUrl, "CorpID-sentinel", "app-secret-value", "document-1", "sheet-1",
        "view-1", "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
  }

  private static Map<String, String> decodeQuery(String query) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String pair : query.split("&")) {
      String[] parts = pair.split("=", 2);
      values.put(URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
          URLDecoder.decode(parts.length == 2 ? parts[1] : "", java.nio.charset.StandardCharsets.UTF_8));
    }
    return values;
  }

  private static void assertThrowableDoesNotContain(Throwable error, String... sensitiveValues) {
    StringWriter writer = new StringWriter();
    error.printStackTrace(new PrintWriter(writer));
    String trace = writer.toString();
    for (String sensitiveValue : sensitiveValues) {
      assertThat(trace).doesNotContain(sensitiveValue);
    }
    for (Throwable current = error; current != null; current = current.getCause()) {
      for (String sensitiveValue : sensitiveValues) {
        assertThat(current.getMessage() == null || !current.getMessage().contains(sensitiveValue)).isTrue();
      }
    }
  }

  private record FailureCase(int status, String body, String reason) {}

  private record OperationFixture(String name, String path) {}

  private static final class SelfReferencingBody {
    private SelfReferencingBody self;
  }

  private static class CapturingHttpClient extends HttpClient {
    private HttpRequest request;

    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    @Override public SSLContext sslContext() { return sslContextDefault(); }
    @Override public SSLParameters sslParameters() { return new SSLParameters(); }
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    @Override public Version version() { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor() { return Optional.empty(); }
    @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      this.request = request;
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new FixedResponse(200, "{\"errcode\":0}");
      return response;
    }
    @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
    @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> handler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return CompletableFuture.failedFuture(new UnsupportedOperationException()); }
  }

  private static final class FailingHttpClient extends CapturingHttpClient {
    private final Exception failure;

    private FailingHttpClient(Exception failure) { this.failure = failure; }

    @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      if (failure instanceof IOException ioException) {
        throw ioException;
      }
      throw (InterruptedException) failure;
    }
  }

  private static final class DeadlineHttpClient extends CapturingHttpClient {
    private final MutableTicker ticker;
    private final Duration elapsedPerRequest;
    private final ArrayDeque<String> responses = new ArrayDeque<>();
    private final List<HttpRequest> requests = new ArrayList<>();

    private DeadlineHttpClient(MutableTicker ticker, Duration elapsedPerRequest, String... responses) {
      this.ticker = ticker;
      this.elapsedPerRequest = elapsedPerRequest;
      this.responses.addAll(List.of(responses));
    }

    @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      requests.add(request);
      ticker.advance(elapsedPerRequest);
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new FixedResponse(200, responses.removeFirst());
      return response;
    }
  }

  private static final class MutableTicker implements LongSupplier {
    private long nanos;

    void advance(Duration duration) {
      nanos += duration.toNanos();
    }

    @Override
    public long getAsLong() {
      return nanos;
    }
  }

  private record FixedResponse(int statusCode, String body) implements HttpResponse<String> {
    @Override public HttpRequest request() { return null; }
    @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    @Override public java.net.http.HttpHeaders headers() { return java.net.http.HttpHeaders.of(Map.of(), (a, b) -> true); }
    @Override public java.net.URI uri() { return java.net.URI.create("http://127.0.0.1"); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
  }

  private static SSLContext sslContextDefault() {
    try {
      return SSLContext.getDefault();
    } catch (java.security.NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
