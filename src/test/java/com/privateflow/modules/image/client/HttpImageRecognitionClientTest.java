package com.privateflow.modules.image.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.image.ImageRecognitionException;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class HttpImageRecognitionClientTest {

  @Test
  void retriesOnceWhenTheFirstConnectionCloses() throws Exception {
    AtomicInteger requests = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      try (var input = exchange.getRequestBody()) {
        input.transferTo(java.io.OutputStream.nullOutputStream());
      }
      if (requests.incrementAndGet() == 1) {
        exchange.close();
        return;
      }
      byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"
          .getBytes(StandardCharsets.UTF_8);
      try {
        exchange.sendResponseHeaders(200, response.length);
        try (var output = exchange.getResponseBody()) {
          output.write(response);
        }
      } catch (IOException ignored) {
        exchange.close();
      }
    });
    server.start();
    try {
      HttpImageRecognitionClient client = new HttpImageRecognitionClient(
          mock(ImageConfigProvider.class),
          new ObjectMapper());
      ImageConfig config = new ImageConfig(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
          "",
          15_000,
          5_000_000,
          1920,
          85,
          "prompt",
          "qwen3-vl-plus",
          3);

      assertThat(client.recognize(new byte[] {1, 2, 3}, config)).isEqualTo("{}");
      assertThat(requests).hasValue(2);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void rejectsMissingModelInsteadOfChoosingAProviderSpecificDefault() {
    HttpImageRecognitionClient client = new HttpImageRecognitionClient(
        mock(ImageConfigProvider.class),
        new ObjectMapper());
    ImageConfig config = new ImageConfig(
        "https://example.com",
        "",
        5000,
        5_000_000,
        1920,
        85,
        "prompt",
        "",
        3);

    assertThatThrownBy(() -> client.recognize(new byte[] {1, 2, 3}, config))
        .isInstanceOf(ImageRecognitionException.class)
        .hasMessageContaining("model");
  }

  @Test
  void usesAVisionSafeDefaultDeadlineWhenStoredTimeoutIsZero() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/v1/chat/completions", exchange -> {
      try (var input = exchange.getRequestBody()) {
        input.transferTo(java.io.OutputStream.nullOutputStream());
      }
      try {
        Thread.sleep(5_250);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
      byte[] response = "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      try (var output = exchange.getResponseBody()) {
        output.write(response);
      }
    });
    server.start();
    try {
      HttpImageRecognitionClient client = new HttpImageRecognitionClient(
          mock(ImageConfigProvider.class),
          new ObjectMapper());
      ImageConfig config = new ImageConfig(
          "http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
          "",
          0,
          5_000_000,
          1920,
          85,
          "prompt",
          "qwen3-vl-plus",
          3);

      assertThat(client.recognize(new byte[] {1, 2, 3}, config)).isEqualTo("{}");
    } finally {
      server.stop(0);
    }
  }
}
