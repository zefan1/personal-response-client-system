package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class HttpLlmClientTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsOpenAiCompatibleRequestAndExtractsContent() throws Exception {
    server = startServer(200, """
        {"choices":[{"message":{"content":"OK"}}]}
        """);
    HttpLlmClient client = client(config(baseUrl(), "secret", "gpt-4.1-mini"));

    LlmResponse response = client.generate(LlmRequest.singleTurn("system", "hello"));

    assertThat(response.success()).isTrue();
    assertThat(response.content()).isEqualTo("OK");
    assertThat(response.model()).isEqualTo("gpt-4.1-mini");
    assertThat(response.protocol()).isEqualTo("OPENAI_COMPATIBLE");
    assertThat(response.elapsedMs()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void mapsAuthFailureToLlmErrorCode() throws Exception {
    server = startServer(401, "{}");
    HttpLlmClient client = client(config(baseUrl(), "bad-secret", "gpt-4.1-mini"));

    LlmResponse response = client.generate(LlmRequest.singleTurn("system", "hello"));

    assertThat(response.success()).isFalse();
    assertThat(response.errorCode()).isEqualTo(LlmErrorCodes.AUTH_FAILED);
  }

  @Test
  void returnsConfigMissingWhenRuntimeConfigIsIncomplete() {
    HttpLlmClient client = client(new LlmConfig("", "", "", "OPENAI_COMPATIBLE", 10000, 0.2, 1024));

    LlmResponse response = client.generate(LlmRequest.singleTurn("system", "hello"));

    assertThat(response.success()).isFalse();
    assertThat(response.errorCode()).isEqualTo(LlmErrorCodes.CONFIG_MISSING);
  }

  private HttpLlmClient client(LlmConfig config) {
    SystemConfigRepository repository = Mockito.mock(SystemConfigRepository.class);
    when(repository.findValue("llm.api_base_url")).thenReturn(Optional.of(config.apiBaseUrl()));
    when(repository.findValue("llm.api_key")).thenReturn(Optional.of(config.apiKey()));
    when(repository.findValue("llm.model")).thenReturn(Optional.of(config.model()));
    when(repository.findValue("llm.protocol")).thenReturn(Optional.of(config.protocol()));
    when(repository.findValue("llm.timeout_ms")).thenReturn(Optional.of(String.valueOf(config.timeoutMs())));
    when(repository.findValue("llm.temperature")).thenReturn(Optional.of(String.valueOf(config.temperature())));
    when(repository.findValue("llm.max_tokens")).thenReturn(Optional.of(String.valueOf(config.maxTokens())));
    LlmConfigProvider provider = new LlmConfigProvider(repository, new SecretCipher("test-secret-key"));
    provider.refresh();
    return new HttpLlmClient(provider, new ObjectMapper());
  }

  private LlmConfig config(String baseUrl, String apiKey, String model) {
    return new LlmConfig(baseUrl, apiKey, model, "OPENAI_COMPATIBLE", 10000, 0.2, 1024);
  }

  private String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  private HttpServer startServer(int status, String body) throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext("/v1/chat/completions", exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    httpServer.start();
    return httpServer;
  }
}
