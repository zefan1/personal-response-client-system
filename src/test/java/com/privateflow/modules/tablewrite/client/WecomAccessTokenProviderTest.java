package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

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
      assertThat(server.lastMethod()).isEqualTo("GET");
      assertThat(server.lastJson().isObject()).isTrue();
      assertThat(server.lastJson().isEmpty()).isTrue();
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
  void nonzeroErrcodeNeverIncludesTokenResponseContentOrUri() throws Exception {
    String responseSecret = "raw-token-response-sentinel";
    String token = "access-token-sentinel";
    String uri = "http://wecom.invalid/cgi-bin/gettoken?corpid=CorpID-sentinel&corpsecret=app-secret-value";
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200,
          "{\"errcode\":40013,\"errmsg\":\"" + responseSecret + " " + token + " " + uri + "\"}");
      WecomAccessTokenProvider provider = provider(server, new MutableClock());

      assertThatThrownBy(provider::get)
          .isInstanceOf(WecomSmartSheetException.class)
          .satisfies(error -> assertThrowableDoesNotContain(error,
              responseSecret, token, uri, "CorpID-sentinel", "app-secret-value", "gettoken?"));
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

  @TestFactory
  Stream<DynamicTest> rejectsTrailingTokenResponseContentWithoutExposingIt() {
    return Stream.of(
        success("token-one", 600) + " trailing-token-response",
        success("token-one", 600) + "{\"second\":\"raw-token-response\"}")
        .map(body -> DynamicTest.dynamicTest("rejects trailing token response content", () -> {
          try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
            server.respond(GET_TOKEN_PATH, 200, body);

            assertThatThrownBy(() -> provider(server, new MutableClock()).get())
                .isInstanceOf(WecomSmartSheetException.class)
                .hasMessageContaining("response was not valid JSON")
                .satisfies(error -> assertThrowableDoesNotContain(error,
                    body, "token-one", "trailing-token-response", "raw-token-response"));
          }
        }));
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
  void invalidBaseUrlDoesNotLeakCredentialsAnywhereInThrowableGraph() {
    String corpId = "CorpID-sentinel";
    String secret = "app-secret-value";
    WecomSmartSheetConfig config = configured(
        "http://bad host/CorpID-sentinel/app-secret-value/raw-response-sentinel", corpId, secret);
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), config, HttpClient.newHttpClient(), new MutableClock());

    assertThatThrownBy(provider::get)
        .isInstanceOf(WecomSmartSheetException.class)
        .satisfies(error -> assertThrowableDoesNotContain(error,
            corpId, secret, "access-token-sentinel", "corpid", "corpsecret", "raw-response-sentinel"));
  }

  @TestFactory
  Stream<DynamicTest> rejectsNonTextualAccessTokens() {
    return Stream.of("123", "true", "{}", "[]")
        .map(tokenValue -> DynamicTest.dynamicTest("rejects token JSON " + tokenValue, () -> {
          try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
            server.respond(GET_TOKEN_PATH, 200,
                "{\"errcode\":0,\"access_token\":" + tokenValue + ",\"expires_in\":600}");

            assertThatThrownBy(() -> provider(server, new MutableClock()).get())
                .isInstanceOf(WecomSmartSheetException.class)
                .hasMessageContaining("gettoken");
          }
        }));
  }

  @Test
  void concurrentColdCacheFetchesOnlyOneToken() throws Exception {
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200, success("token-one", 600));
      WecomAccessTokenProvider provider = provider(server, new MutableClock());
      ExecutorService executor = Executors.newFixedThreadPool(8);
      CountDownLatch ready = new CountDownLatch(8);
      CountDownLatch start = new CountDownLatch(1);
      try {
        List<Future<String>> responses = Stream.generate(() -> executor.submit(() -> {
          ready.countDown();
          start.await();
          return provider.get();
        })).limit(8).toList();
        assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        start.countDown();

        for (Future<String> response : responses) {
          assertThat(response.get(5, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo("token-one");
        }
      } finally {
        executor.shutdownNow();
      }
      assertThat(server.requestCount()).isEqualTo(1);
    }
  }

  @Test
  void requiresPositiveTimeoutBeforeStartingTokenRequest() {
    RecordingHttpClient http = new RecordingHttpClient();
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        http, new MutableClock());

    assertThatThrownBy(() -> provider.get(null)).isInstanceOf(WecomSmartSheetException.class);
    assertThatThrownBy(() -> provider.get(Duration.ZERO)).isInstanceOf(WecomSmartSheetException.class);
    assertThatThrownBy(() -> provider.get(Duration.ofNanos(-1))).isInstanceOf(WecomSmartSheetException.class);

    assertThat(http.requests).isEmpty();
  }

  @Test
  void tokenRequestUsesRemainingTimeoutAndDefaultGetIsFinite() {
    RecordingHttpClient http = new RecordingHttpClient();
    LongSupplier fixedTicker = () -> 100L;
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        http, new MutableClock(), fixedTicker);

    assertThat(provider.get(Duration.ofMillis(1234))).isEqualTo("token-one");
    assertThat(http.requests.get(0).timeout()).contains(Duration.ofMillis(1234));

    provider.invalidate("token-one");
    assertThat(provider.get()).isEqualTo("token-one");
    assertThat(http.requests.get(1).timeout()).hasValueSatisfying(timeout -> {
      assertThat(timeout).isPositive();
      assertThat(timeout).isLessThanOrEqualTo(Duration.ofSeconds(30));
    });
  }

  @Test
  void tokenReturnedAtDeadlineIsNotCachedAndNextBudgetRequestsAgain() {
    MutableTicker ticker = new MutableTicker();
    ExhaustingOnceHttpClient http = new ExhaustingOnceHttpClient(ticker);
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        http, new MutableClock(), ticker);

    assertThatThrownBy(() -> provider.get(Duration.ofSeconds(1)))
        .isInstanceOf(WecomSmartSheetException.class)
        .hasMessageContaining("timeout expired");
    assertThat(http.requests).hasSize(1);

    assertThat(provider.get(Duration.ofSeconds(1))).isEqualTo("token-one");
    assertThat(http.requests).hasSize(2);
  }

  @Test
  void refreshFollowerTimesOutWithoutStartingAnotherRequestAndLoaderStillCaches() throws Exception {
    BlockingHttpClient http = new BlockingHttpClient();
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        http, new MutableClock());
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<String> loader = executor.submit(() -> provider.get(Duration.ofSeconds(5)));
    try {
      assertThat(http.started.await(2, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> provider.get(Duration.ofMillis(250)))
          .isInstanceOf(WecomSmartSheetException.class)
          .hasMessageContaining("timed out");
      assertThat(http.requests).hasSize(1);

      http.release.countDown();
      assertThat(loader.get(2, TimeUnit.SECONDS)).isEqualTo("token-one");
      assertThat(provider.get(Duration.ofSeconds(1))).isEqualTo("token-one");
      assertThat(http.requests).hasSize(1);
    } finally {
      http.release.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void interruptedRefreshFollowerRestoresFlagWithoutAffectingLoaderOrCache() throws Exception {
    BlockingHttpClient http = new BlockingHttpClient();
    SignalingTicker ticker = new SignalingTicker();
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        http, new MutableClock(), ticker);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<String> loader = executor.submit(() -> provider.get(Duration.ofSeconds(5)));
    AtomicReference<RuntimeException> followerFailure = new AtomicReference<>();
    AtomicReference<Boolean> followerInterrupted = new AtomicReference<>(false);
    Thread follower = new Thread(() -> {
      try {
        provider.get(Duration.ofSeconds(5));
      } catch (RuntimeException ex) {
        followerFailure.set(ex);
        followerInterrupted.set(Thread.currentThread().isInterrupted());
      }
    }, "token-refresh-follower");
    try {
      assertThat(http.started.await(2, TimeUnit.SECONDS)).isTrue();
      ticker.watch(follower);
      follower.start();
      assertThat(ticker.beforeFollowerWait.await(2, TimeUnit.SECONDS)).isTrue();

      follower.interrupt();
      follower.join(2_000);

      assertThat(follower.isAlive()).isFalse();
      assertThat(followerFailure.get()).isInstanceOf(WecomSmartSheetException.class)
          .hasMessageContaining("interrupted");
      assertThat(followerInterrupted.get()).isTrue();
      assertThat(http.requests).hasSize(1);

      http.release.countDown();
      assertThat(loader.get(2, TimeUnit.SECONDS)).isEqualTo("token-one");
      assertThat(provider.get(Duration.ofSeconds(1))).isEqualTo("token-one");
    } finally {
      http.release.countDown();
      follower.interrupt();
      follower.join(2_000);
      executor.shutdownNow();
    }
  }

  @Test
  void followerThatAcquiresRefreshLockAtItsDeadlineDoesNotReturnLoadersToken() throws Exception {
    BlockingHttpClient http = new BlockingHttpClient();
    BoundaryLockTicker ticker = new BoundaryLockTicker();
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        http, new MutableClock(), ticker);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<String> loader = executor.submit(() -> provider.get(Duration.ofSeconds(10)));
    AtomicReference<String> followerResult = new AtomicReference<>();
    AtomicReference<RuntimeException> followerFailure = new AtomicReference<>();
    Thread follower = new Thread(() -> {
      try {
        followerResult.set(provider.get(Duration.ofSeconds(5)));
      } catch (RuntimeException ex) {
        followerFailure.set(ex);
      }
    }, "token-deadline-lock-follower");
    try {
      assertThat(http.started.await(2, TimeUnit.SECONDS)).isTrue();
      ticker.watch(follower);
      follower.start();
      assertThat(ticker.beforeFollowerLock.await(2, TimeUnit.SECONDS)).isTrue();

      ticker.advance(Duration.ofSeconds(5));
      http.release.countDown();
      assertThat(loader.get(2, TimeUnit.SECONDS)).isEqualTo("token-one");
      follower.join(2_000);

      assertThat(follower.isAlive()).isFalse();
      assertThat(followerResult.get()).isNull();
      assertThat(followerFailure.get()).isInstanceOf(WecomSmartSheetException.class)
          .hasMessageContaining("timeout expired");
      assertThat(provider.get(Duration.ofSeconds(1))).isEqualTo("token-one");
      assertThat(http.requests).hasSize(1);
    } finally {
      http.release.countDown();
      follower.interrupt();
      follower.join(2_000);
      executor.shutdownNow();
    }
  }

  @Test
  void interruptedSendRestoresInterruptStatusWithoutLeakingCauseText() {
    WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
        new ObjectMapper(), configured("http://127.0.0.1", "corp id", "app-secret-value"),
        new InterruptingHttpClient(), new MutableClock());
    try {
      assertThatThrownBy(provider::get)
          .isInstanceOf(WecomSmartSheetException.class)
          .satisfies(error -> assertThrowableDoesNotContain(error, "interrupted-sentinel", "corp id", "app-secret-value"));
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void urlEncodingRoundTripsSpecialCredentialValues() throws Exception {
    String corpId = "corp+&=中文";
    String secret = "secret+&=密码";
    try (WecomTestHttpServer server = WecomTestHttpServer.start()) {
      server.respond(GET_TOKEN_PATH, 200, success("token-one", 600));
      WecomAccessTokenProvider provider = new WecomAccessTokenProvider(
          new ObjectMapper(), configured(server.baseUrl(), corpId, secret), HttpClient.newHttpClient(), new MutableClock());

      assertThat(provider.get()).isEqualTo("token-one");
      Map<String, String> query = decodeQuery(server.lastQuery());
      assertRoundTripsWithoutExposingValue(corpId, query.get("corpid"), "CorpID");
      assertRoundTripsWithoutExposingValue(secret, query.get("corpsecret"), "app secret");
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
    return new WecomAccessTokenProvider(new ObjectMapper(), configured(server.baseUrl(), "corp id", "app-secret-value"),
        HttpClient.newHttpClient(), clock);
  }

  private WecomSmartSheetConfig configured(String apiBaseUrl, String corpId, String appSecret) {
    return new WecomSmartSheetConfig(
        apiBaseUrl, corpId, appSecret, "document-1", "sheet-1", "view-1",
        "Customers", "Customer ID", ZoneId.of("Asia/Shanghai"));
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

  private static void assertRoundTripsWithoutExposingValue(String expected, String actual, String label) {
    assertThat(Arrays.equals(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
        actual == null ? null : actual.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        .as(label + " must round trip through URL encoding")
        .isTrue();
  }

  private static void assertThrowableDoesNotContain(Throwable error, String... sensitiveValues) {
    StringWriter writer = new StringWriter();
    error.printStackTrace(new PrintWriter(writer));
    String trace = writer.toString();
    for (String sensitiveValue : sensitiveValues) {
      assertThat(trace.contains(sensitiveValue))
          .as("complete throwable graph must not contain sensitive data")
          .isFalse();
    }
    for (Throwable current = error; current != null; current = current.getCause()) {
      String message = current.getMessage();
      for (String sensitiveValue : sensitiveValues) {
        assertThat(message == null || !message.contains(sensitiveValue))
            .as("throwable message must not contain sensitive data")
            .isTrue();
      }
    }
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

  private static class RecordingHttpClient extends HttpClient {
    protected final List<HttpRequest> requests = new ArrayList<>();

    @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
    @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
    @Override public Redirect followRedirects() { return Redirect.NEVER; }
    @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
    @Override public SSLContext sslContext() {
      try {
        return SSLContext.getDefault();
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new IllegalStateException(ex);
      }
    }
    @Override public SSLParameters sslParameters() { return new SSLParameters(); }
    @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
    @Override public Version version() { return Version.HTTP_1_1; }
    @Override public Optional<Executor> executor() { return Optional.empty(); }
    @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      requests.add(request);
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new FixedResponse(200, success("token-one", 600));
      return response;
    }
    @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> handler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
    @Override public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> handler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
  }

  private static final class BlockingHttpClient extends RecordingHttpClient {
    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      requests.add(request);
      started.countDown();
      if (!release.await(5, TimeUnit.SECONDS)) {
        throw new IOException("test token request did not release");
      }
      @SuppressWarnings("unchecked")
      HttpResponse<T> response = (HttpResponse<T>) new FixedResponse(200, success("token-one", 600));
      return response;
    }
  }

  private static final class ExhaustingOnceHttpClient extends RecordingHttpClient {
    private final MutableTicker ticker;
    private final AtomicInteger calls = new AtomicInteger();

    private ExhaustingOnceHttpClient(MutableTicker ticker) {
      this.ticker = ticker;
    }

    @Override public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
        throws IOException, InterruptedException {
      HttpResponse<T> response = super.send(request, handler);
      if (calls.getAndIncrement() == 0) {
        ticker.advance(Duration.ofSeconds(1));
      }
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

  private static final class SignalingTicker implements LongSupplier {
    private final AtomicReference<Thread> watched = new AtomicReference<>();
    private final AtomicInteger watchedReads = new AtomicInteger();
    private final CountDownLatch beforeFollowerWait = new CountDownLatch(1);

    void watch(Thread thread) {
      watched.set(thread);
    }

    @Override
    public long getAsLong() {
      if (Thread.currentThread() == watched.get() && watchedReads.incrementAndGet() == 2) {
        beforeFollowerWait.countDown();
      }
      return System.nanoTime();
    }
  }

  private static final class BoundaryLockTicker implements LongSupplier {
    private final AtomicLong nanos = new AtomicLong();
    private final AtomicReference<Thread> watched = new AtomicReference<>();
    private final AtomicInteger watchedReads = new AtomicInteger();
    private final CountDownLatch beforeFollowerLock = new CountDownLatch(1);

    void watch(Thread thread) {
      watched.set(thread);
    }

    void advance(Duration duration) {
      nanos.addAndGet(duration.toNanos());
    }

    @Override
    public long getAsLong() {
      if (Thread.currentThread() == watched.get() && watchedReads.incrementAndGet() == 2) {
        beforeFollowerLock.countDown();
      }
      return nanos.get();
    }
  }

  private record FixedResponse(int statusCode, String body) implements HttpResponse<String> {
    @Override public HttpRequest request() { return null; }
    @Override public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
    @Override public java.net.http.HttpHeaders headers() {
      return java.net.http.HttpHeaders.of(Map.of(), (left, right) -> true);
    }
    @Override public java.net.URI uri() { return java.net.URI.create("http://127.0.0.1"); }
    @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    @Override public Optional<javax.net.ssl.SSLSession> sslSession() { return Optional.empty(); }
  }

  private static final class InterruptingHttpClient extends HttpClient {

    @Override
    public Optional<CookieHandler> cookieHandler() {
      return Optional.empty();
    }

    @Override
    public Optional<Duration> connectTimeout() {
      return Optional.empty();
    }

    @Override
    public Redirect followRedirects() {
      return Redirect.NEVER;
    }

    @Override
    public Optional<ProxySelector> proxy() {
      return Optional.empty();
    }

    @Override
    public SSLContext sslContext() {
      try {
        return SSLContext.getDefault();
      } catch (java.security.NoSuchAlgorithmException ex) {
        throw new IllegalStateException(ex);
      }
    }

    @Override
    public SSLParameters sslParameters() {
      return new SSLParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
      return Optional.empty();
    }

    @Override
    public Version version() {
      return Version.HTTP_1_1;
    }

    @Override
    public Optional<Executor> executor() {
      return Optional.empty();
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler)
        throws InterruptedException {
      throw new InterruptedException("interrupted-sentinel");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(
        HttpRequest request,
        HttpResponse.BodyHandler<T> responseBodyHandler,
        HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
      return CompletableFuture.failedFuture(new UnsupportedOperationException());
    }
  }
}
