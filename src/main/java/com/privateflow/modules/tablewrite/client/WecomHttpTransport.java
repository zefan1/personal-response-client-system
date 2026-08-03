package com.privateflow.modules.tablewrite.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

@FunctionalInterface
interface WecomHttpTransport {

  WecomHttpResponse send(
      URI uri,
      String method,
      Map<String, String> headers,
      byte[] body,
      Duration timeout) throws IOException, InterruptedException;

  static WecomHttpTransport from(HttpClient httpClient) {
    Objects.requireNonNull(httpClient, "httpClient");
    return (uri, method, headers, body, timeout) -> {
      HttpRequest.Builder builder = HttpRequest.newBuilder()
          .uri(uri)
          .timeout(timeout);
      headers.forEach(builder::header);
      HttpRequest.BodyPublisher publisher = body.length == 0
          ? HttpRequest.BodyPublishers.noBody()
          : HttpRequest.BodyPublishers.ofByteArray(body);
      HttpResponse<String> response = httpClient.send(
          builder.method(method, publisher).build(),
          HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new WecomHttpResponse(response.statusCode(), response.body());
    };
  }
}
