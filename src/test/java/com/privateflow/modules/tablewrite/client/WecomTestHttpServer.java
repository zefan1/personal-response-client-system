package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

final class WecomTestHttpServer implements AutoCloseable {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  record Reply(int status, String body) {}

  private final HttpServer server;
  private final Object monitor = new Object();
  private final Map<String, ArrayDeque<Reply>> repliesByPath = new HashMap<>();
  private int requestCount;
  private String lastMethod;
  private String lastQuery;
  private String lastJson;

  private WecomTestHttpServer(HttpServer server) {
    this.server = server;
    server.createContext("/", this::handle);
  }

  static WecomTestHttpServer start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    WecomTestHttpServer testServer = new WecomTestHttpServer(server);
    server.start();
    return testServer;
  }

  void respond(String path, int status, String body) {
    respondInOrder(path, new Reply(status, body));
  }

  void respondInOrder(String path, Reply... replies) {
    synchronized (monitor) {
      ArrayDeque<Reply> queue = new ArrayDeque<>();
      for (Reply reply : replies) {
        queue.addLast(reply);
      }
      repliesByPath.put(path, queue);
    }
  }

  String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  int requestCount() {
    synchronized (monitor) {
      return requestCount;
    }
  }

  String lastMethod() {
    synchronized (monitor) {
      return lastMethod;
    }
  }

  String lastQuery() {
    synchronized (monitor) {
      return lastQuery;
    }
  }

  /** Returns captured UTF-8 JSON, with an empty request body represented by an empty object. */
  JsonNode lastJson() {
    synchronized (monitor) {
      try {
        return lastJson == null || lastJson.isBlank()
            ? OBJECT_MAPPER.createObjectNode()
            : OBJECT_MAPPER.readTree(lastJson);
      } catch (IOException ex) {
        throw new IllegalStateException("Captured request body was not valid JSON", ex);
      }
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String query = exchange.getRequestURI().getRawQuery();
    String json = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    Reply reply;
    synchronized (monitor) {
      requestCount++;
      lastMethod = exchange.getRequestMethod();
      lastQuery = query;
      lastJson = json;
      ArrayDeque<Reply> queue = repliesByPath.get(path);
      reply = queue == null || queue.isEmpty()
          ? new Reply(500, "{\"errcode\":-1,\"errmsg\":\"missing test reply\"}")
          : queue.removeFirst();
    }
    byte[] response = reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(reply.status(), response.length);
    exchange.getResponseBody().write(response);
    exchange.close();
  }
}
