package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LlmServiceTest {

  @Test
  void routedGenerateUsesResolvedConfigAndWritesCallLog() {
    LlmClient client = Mockito.mock(LlmClient.class);
    LlmRoutingService routingService = Mockito.mock(LlmRoutingService.class);
    LlmCallLogger callLogger = Mockito.mock(LlmCallLogger.class);
    LlmConfig config = new LlmConfig("https://llm.example.com", "secret", "gpt-4.1-mini", "OPENAI_COMPATIBLE", 10000, 0.2, 1024);
    LlmRequest request = LlmRequest.singleTurn("system", "hello");
    when(routingService.resolveCandidates(LlmScene.REPLY_GENERATION, "PENDING")).thenReturn(List.of(new LlmRouteResolution(
        LlmScene.REPLY_GENERATION,
        "PENDING",
        7L,
        9L,
        "llm-prod",
        config,
        false)));
    when(client.generate(eq(request), eq(config))).thenReturn(LlmResponse.ok("OK", "gpt-4.1-mini", "OPENAI_COMPATIBLE", 88));
    LlmService service = new LlmService(client, routingService, callLogger);

    LlmResponse response = service.generate(LlmScene.REPLY_GENERATION, "PENDING", "admin", "hello", request);

    assertThat(response.success()).isTrue();
    assertThat(response.content()).isEqualTo("OK");
    verify(client).generate(request, config);
    verify(callLogger).logCall(
        eq(LlmScene.REPLY_GENERATION),
        eq("PENDING"),
        eq("admin"),
        eq(7L),
        eq(9L),
        eq("gpt-4.1-mini"),
        eq("OPENAI_COMPATIBLE"),
        eq("hello"),
        eq(88L),
        eq(true),
        any(),
        any());
  }

  @Test
  void routedGenerateTriesBackupCandidateWhenPrimaryFails() {
    LlmClient client = Mockito.mock(LlmClient.class);
    LlmRoutingService routingService = Mockito.mock(LlmRoutingService.class);
    LlmCallLogger callLogger = Mockito.mock(LlmCallLogger.class);
    LlmConfig primary = new LlmConfig("https://primary.example.com", "secret-a", "gpt-primary", "OPENAI_COMPATIBLE", 10000, 0.2, 1024);
    LlmConfig backup = new LlmConfig("https://backup.example.com", "secret-b", "gpt-backup", "OPENAI_COMPATIBLE", 10000, 0.2, 1024);
    LlmRequest request = LlmRequest.singleTurn("system", "hello");
    when(routingService.resolveCandidates(LlmScene.REPLY_GENERATION, "PENDING")).thenReturn(List.of(
        new LlmRouteResolution(LlmScene.REPLY_GENERATION, "PENDING", 7L, 9L, "primary", primary, false),
        new LlmRouteResolution(LlmScene.REPLY_GENERATION, "PENDING", 8L, 10L, "backup", backup, false)));
    when(client.generate(eq(request), eq(primary))).thenReturn(LlmResponse.failed(LlmErrorCodes.TIMEOUT, "timeout", "gpt-primary", "OPENAI_COMPATIBLE", 1000));
    when(client.generate(eq(request), eq(backup))).thenReturn(LlmResponse.ok("OK from backup", "gpt-backup", "OPENAI_COMPATIBLE", 120));
    LlmService service = new LlmService(client, routingService, callLogger);

    LlmResponse response = service.generate(LlmScene.REPLY_GENERATION, "PENDING", "admin", "hello", request);

    assertThat(response.success()).isTrue();
    assertThat(response.content()).isEqualTo("OK from backup");
    verify(client).generate(request, primary);
    verify(client).generate(request, backup);
    verify(callLogger).logCall(
        eq(LlmScene.REPLY_GENERATION),
        eq("PENDING"),
        eq("admin"),
        eq(7L),
        eq(9L),
        eq("gpt-primary"),
        eq("OPENAI_COMPATIBLE"),
        eq("hello"),
        eq(1000L),
        eq(false),
        eq(LlmErrorCodes.TIMEOUT),
        eq("timeout"));
    verify(callLogger).logCall(
        eq(LlmScene.REPLY_GENERATION),
        eq("PENDING"),
        eq("admin"),
        eq(8L),
        eq(10L),
        eq("gpt-backup"),
        eq("OPENAI_COMPATIBLE"),
        eq("hello"),
        eq(120L),
        eq(true),
        any(),
        any());
  }

  @Test
  void validatedGenerateTriesBackupWhenPrimaryResponseIsInvalid() {
    LlmClient client = Mockito.mock(LlmClient.class);
    LlmRoutingService routingService = Mockito.mock(LlmRoutingService.class);
    LlmCallLogger callLogger = Mockito.mock(LlmCallLogger.class);
    LlmConfig primary = new LlmConfig("https://primary.example.com", "secret-a", "gpt-primary", "OPENAI_COMPATIBLE", 10000, 0.2, 1024);
    LlmConfig backup = new LlmConfig("https://backup.example.com", "secret-b", "gpt-backup", "OPENAI_COMPATIBLE", 10000, 0.2, 1024);
    LlmRequest request = LlmRequest.singleTurn("system", "hello");
    when(routingService.resolveCandidates(LlmScene.PROFILE_EXTRACTION, "PENDING")).thenReturn(List.of(
        new LlmRouteResolution(LlmScene.PROFILE_EXTRACTION, "PENDING", 7L, 9L, "primary", primary, false),
        new LlmRouteResolution(LlmScene.PROFILE_EXTRACTION, "PENDING", 8L, 10L, "backup", backup, false)));
    when(client.generate(eq(request), eq(primary)))
        .thenReturn(LlmResponse.ok("legacy schema", "gpt-primary", "OPENAI_COMPATIBLE", 88));
    when(client.generate(eq(request), eq(backup)))
        .thenReturn(LlmResponse.ok("valid schema", "gpt-backup", "OPENAI_COMPATIBLE", 120));
    LlmService service = new LlmService(client, routingService, callLogger);

    Optional<String> result = service.generateValidated(
        LlmScene.PROFILE_EXTRACTION,
        "PENDING",
        "admin",
        "hello",
        request,
        content -> {
          if (!content.startsWith("valid")) {
            throw new IllegalArgumentException("invalid profile schema");
          }
          return content;
        });

    assertThat(result).contains("valid schema");
    verify(client).generate(request, primary);
    verify(client).generate(request, backup);
    verify(callLogger).logCall(
        eq(LlmScene.PROFILE_EXTRACTION),
        eq("PENDING"),
        eq("admin"),
        eq(7L),
        eq(9L),
        eq("gpt-primary"),
        eq("OPENAI_COMPATIBLE"),
        eq("hello"),
        eq(88L),
        eq(false),
        eq(LlmErrorCodes.RESPONSE_INVALID),
        eq("invalid profile schema"));
    verify(callLogger).logCall(
        eq(LlmScene.PROFILE_EXTRACTION),
        eq("PENDING"),
        eq("admin"),
        eq(8L),
        eq(10L),
        eq("gpt-backup"),
        eq("OPENAI_COMPATIBLE"),
        eq("hello"),
        eq(120L),
        eq(true),
        any(),
        any());
  }
}
