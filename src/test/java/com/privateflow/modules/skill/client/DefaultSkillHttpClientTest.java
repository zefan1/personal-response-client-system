package com.privateflow.modules.skill.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultSkillHttpClientTest {

  private HttpServer server;
  private String lastRequestBody;
  private String lastToolRequestBody;

  @BeforeEach
  void setUp() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/mcp", this::handleMcp);
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void callsMcpSkillUsingStreamableHttpSessionAndReturnsToolText() {
    SkillConfigProvider provider = mock(SkillConfigProvider.class);
    when(provider.get()).thenReturn(config("MCP_STREAMABLE_HTTP"));
    DefaultSkillHttpClient client = new DefaultSkillHttpClient(new ObjectMapper(), provider);

    String result = client.call(Map.of(
        "scene", "CHAT_RECOGNIZE",
        "client_message", "客户说预算比较紧",
        "skill_id", "sales-champion-coach",
        "system_prompt", "只返回 JSON",
        "customer", Map.of("lead_type", "GENERAL")), 5000);

    assertThat(result).isEqualTo("{\"suggestions\":[{\"text\":\"收到\"}]}");
    assertThat(lastRequestBody).contains("\"method\":\"initialize\"");
    assertThat(lastToolRequestBody).contains("sales_champion_coach__query");
    assertThat(lastToolRequestBody).contains("客户说预算比较紧");
    assertThat(lastToolRequestBody).contains("CHAT_RECOGNIZE");
  }

  private SkillConfig config(String protocol) {
    return new SkillConfig(
        "http://localhost:" + server.getAddress().getPort() + "/mcp",
        "test-key",
        "LAST_FOUR",
        "",
        5000,
        30,
        0.5,
        5,
        30,
        "fallback",
        "",
        "",
        "sales-champion-coach",
        "",
        "",
        0.3,
        15,
        8000,
        3,
        protocol);
  }

  private void handleMcp(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (body.contains("\"method\":\"initialize\"")) {
      lastRequestBody = body;
      exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
      exchange.getResponseHeaders().set("Mcp-Session-Id", "session-1");
      write(exchange, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}\n\n");
      return;
    }
    if (body.contains("notifications/initialized")) {
      exchange.sendResponseHeaders(202, -1);
      exchange.close();
      return;
    }
    lastToolRequestBody = body;
    exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
    write(exchange, "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"suggestions\\\":[{\\\"text\\\":\\\"收到\\\"}]}\"}]}}\n\n");
  }

  private void write(HttpExchange exchange, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.sendResponseHeaders(200, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
