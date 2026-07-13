package com.privateflow.modules.api.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.image.client.ConfigurableImageRecognitionClient;
import com.privateflow.modules.image.config.ImageConfig;
import com.privateflow.modules.image.config.ImageConfigProvider;
import com.privateflow.modules.image.parser.RecognitionResultParser;
import com.privateflow.modules.llm.LlmConfig;
import com.privateflow.modules.llm.LlmResponse;
import com.privateflow.modules.llm.LlmService;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

class AiEnvironmentServiceTest {

  @Test
  void testImageUsesSelectedEnvironmentCredentials() {
    AiEnvironmentRepository repository = mock(AiEnvironmentRepository.class);
    ImageConfigProvider configProvider = mock(ImageConfigProvider.class);
    ConfigurableImageRecognitionClient imageClient = mock(ConfigurableImageRecognitionClient.class);
    AtomicReference<ConfigurableImageRecognitionClient> reference = new AtomicReference<>(imageClient);
    @SuppressWarnings("unchecked")
    ObjectProvider<ConfigurableImageRecognitionClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenAnswer(invocation -> reference.get());
    AiEnvironmentService service = new AiEnvironmentService(
        repository,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        configProvider,
        provider,
        new RecognitionResultParser(new ObjectMapper()),
        mock(LlmService.class));
    when(repository.find(AiEnvironmentType.IMAGE, 8L)).thenReturn(Optional.of(environment(8L, "https://image-b.example.com")));
    when(repository.decryptApiKey(AiEnvironmentType.IMAGE, 8L)).thenReturn("key-b");
    when(configProvider.get()).thenReturn(new ImageConfig(
        "https://image-a.example.com",
        "key-a",
        4321,
        5242880,
        1920,
        85,
        "prompt",
        "qwen3-vl-plus",
        3));
    when(imageClient.recognize(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn("""
        {"nickname":"李女士","phone":"13800000001","messages":[{"role":"客户","text":"你好"}]}
        """);

    ImageEnvironmentTestResponse response = service.testImage(8L);

    assertThat(response.success()).isTrue();
    ArgumentCaptor<ImageConfig> configCaptor = ArgumentCaptor.forClass(ImageConfig.class);
    verify(imageClient).recognize(org.mockito.ArgumentMatchers.any(), configCaptor.capture());
    assertThat(configCaptor.getValue().apiBaseUrl()).isEqualTo("https://image-b.example.com");
    assertThat(configCaptor.getValue().apiKey()).isEqualTo("key-b");
    assertThat(configCaptor.getValue().timeoutMs()).isEqualTo(4321);
    verify(repository).markImageTest(8L, true);
  }

  @Test
  void updateActiveEnvironmentSynchronizesRuntimeConfigAndPublishesRefreshEvents() {
    AiEnvironmentRepository repository = mock(AiEnvironmentRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    WsPushService wsPushService = mock(WsPushService.class);
    AiEnvironmentService service = new AiEnvironmentService(
        repository,
        eventPublisher,
        wsPushService,
        mock(ImageConfigProvider.class),
        imageClientProvider(null),
        new RecognitionResultParser(new ObjectMapper()),
        mock(LlmService.class));
    AiEnvironment before = environment(9L, "skill-active", "https://skill-old.example.com", true);
    AiEnvironment after = environment(9L, "skill-active", "https://skill-new.example.com", true);
    when(repository.find(AiEnvironmentType.SKILL, 9L)).thenReturn(Optional.of(before), Optional.of(after));
    when(repository.encryptedApiKey(AiEnvironmentType.SKILL, 9L)).thenReturn("{aes-gcm}new-secret");

    AiEnvironment updated = service.update(
        AiEnvironmentType.SKILL,
        9L,
        new AiEnvironmentRequest("skill-active", "https://skill-new.example.com", "new-secret"));

    assertThat(updated.baseUrl()).isEqualTo("https://skill-new.example.com");
    verify(repository).updateConfig("skill.api_base_url", "https://skill-new.example.com");
    verify(repository).updateConfig("skill.api_key", "{aes-gcm}new-secret");
    verify(eventPublisher).publishEvent(new ConfigChangedEvent("skill.api_base_url"));
    verify(eventPublisher).publishEvent(new ConfigChangedEvent("skill.api_key"));
    verify(wsPushService, times(2)).broadcastWs(any());
  }

  @Test
  void activateLlmEnvironmentSynchronizesModelRuntimeConfig() {
    AiEnvironmentRepository repository = mock(AiEnvironmentRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    WsPushService wsPushService = mock(WsPushService.class);
    AiEnvironmentService service = new AiEnvironmentService(
        repository,
        eventPublisher,
        wsPushService,
        mock(ImageConfigProvider.class),
        imageClientProvider(null),
        new RecognitionResultParser(new ObjectMapper()),
        mock(LlmService.class));
    AiEnvironment before = llmEnvironment(12L, false);
    AiEnvironment after = llmEnvironment(12L, true);
    when(repository.find(AiEnvironmentType.LLM, 12L)).thenReturn(Optional.of(before), Optional.of(after));
    when(repository.encryptedApiKey(AiEnvironmentType.LLM, 12L)).thenReturn("{aes-gcm}llm-secret");

    AiEnvironment activated = service.activate(AiEnvironmentType.LLM, 12L);

    assertThat(activated.active()).isTrue();
    verify(repository).updateConfig("llm.api_base_url", "https://llm.example.com");
    verify(repository).updateConfig("llm.api_key", "{aes-gcm}llm-secret");
    verify(repository).updateConfig("llm.model", "gpt-4.1-mini");
    verify(repository).updateConfig("llm.protocol", "OPENAI_COMPATIBLE");
    verify(repository).updateConfig("llm.timeout_ms", "10000");
    verify(repository).updateConfig("llm.temperature", "0.2");
    verify(repository).updateConfig("llm.max_tokens", "1024");
    verify(eventPublisher).publishEvent(new ConfigChangedEvent("llm.model"));
    verify(wsPushService, times(7)).broadcastWs(any());
  }

  @Test
  void testLlmUsesUnifiedRuntimeServiceAndMarksResult() {
    AiEnvironmentRepository repository = mock(AiEnvironmentRepository.class);
    LlmService llmService = mock(LlmService.class);
    AiEnvironmentService service = new AiEnvironmentService(
        repository,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(ImageConfigProvider.class),
        imageClientProvider(null),
        new RecognitionResultParser(new ObjectMapper()),
        llmService);
    when(repository.find(AiEnvironmentType.LLM, 12L)).thenReturn(Optional.of(llmEnvironment(12L, false)));
    when(repository.decryptApiKey(AiEnvironmentType.LLM, 12L)).thenReturn("llm-secret");
    when(llmService.test(any())).thenReturn(LlmResponse.ok("OK", "gpt-4.1-mini", "OPENAI_COMPATIBLE", 88L));

    ImageEnvironmentTestResponse response = service.testLlm(12L);

    assertThat(response.success()).isTrue();
    assertThat(response.elapsedMs()).isEqualTo(88L);
    assertThat(response.result()).containsEntry("content", "OK");
    ArgumentCaptor<LlmConfig> configCaptor = ArgumentCaptor.forClass(LlmConfig.class);
    verify(llmService).test(configCaptor.capture());
    assertThat(configCaptor.getValue().apiBaseUrl()).isEqualTo("https://llm.example.com");
    assertThat(configCaptor.getValue().apiKey()).isEqualTo("llm-secret");
    assertThat(configCaptor.getValue().model()).isEqualTo("gpt-4.1-mini");
    verify(repository).markLlmTest(12L, true);
  }

  private AiEnvironment environment(long id, String baseUrl) {
    return environment(id, "image-test", baseUrl, false);
  }

  private AiEnvironment environment(long id, String name, String baseUrl, boolean active) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 7, 12, 0);
    return new AiEnvironment(id, name, "image", baseUrl, "last", active, null, null, now, now);
  }

  private AiEnvironment llmEnvironment(long id, boolean active) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 7, 12, 0);
    return new AiEnvironment(
        id,
        "llm-main",
        "llm",
        "https://llm.example.com",
        "last",
        "gpt-4.1-mini",
        "OPENAI_COMPATIBLE",
        10000,
        0.2,
        1024,
        active,
        null,
        null,
        now,
        now);
  }

  private ObjectProvider<ConfigurableImageRecognitionClient> imageClientProvider(ConfigurableImageRecognitionClient imageClient) {
    AtomicReference<ConfigurableImageRecognitionClient> reference = new AtomicReference<>(imageClient);
    @SuppressWarnings("unchecked")
    ObjectProvider<ConfigurableImageRecognitionClient> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenAnswer(invocation -> reference.get());
    return provider;
  }
}
