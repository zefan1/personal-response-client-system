package com.privateflow.modules.tablewrite.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

final class WecomUrlConnectionTransport implements WecomHttpTransport {

  private static final int MAX_CONNECT_TIMEOUT_MILLIS = 10_000;

  @Override
  public WecomHttpResponse send(
      URI uri,
      String method,
      Map<String, String> headers,
      byte[] body,
      Duration timeout) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
    int requestTimeoutMillis = timeoutMillis(timeout);
    connection.setConnectTimeout(Math.min(requestTimeoutMillis, MAX_CONNECT_TIMEOUT_MILLIS));
    connection.setReadTimeout(requestTimeoutMillis);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod(method);
    headers.forEach(connection::setRequestProperty);
    try {
      if (body.length > 0) {
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(body.length);
        connection.getOutputStream().write(body);
      }
      int statusCode = connection.getResponseCode();
      InputStream responseStream = statusCode >= 400
          ? connection.getErrorStream()
          : connection.getInputStream();
      String responseBody;
      if (responseStream == null) {
        responseBody = "";
      } else {
        try (responseStream) {
          responseBody = new String(responseStream.readAllBytes(), StandardCharsets.UTF_8);
        }
      }
      return new WecomHttpResponse(statusCode, responseBody);
    } finally {
      connection.disconnect();
    }
  }

  private static int timeoutMillis(Duration timeout) {
    long millis;
    try {
      millis = timeout.toMillis();
    } catch (ArithmeticException ex) {
      millis = Integer.MAX_VALUE;
    }
    if (millis <= 0) {
      return 1;
    }
    return (int) Math.min(millis, Integer.MAX_VALUE);
  }
}
