package com.privateflow.modules.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LlmConfigProviderTest {

  private final SystemConfigRepository repository = Mockito.mock(SystemConfigRepository.class);
  private final SecretCipher secretCipher = new SecretCipher("test-secret-key");
  private final LlmConfigProvider provider = new LlmConfigProvider(repository, secretCipher);

  @Test
  void decryptsEncryptedLlmApiKeyAndParsesRuntimeValues() {
    when(repository.findValue("llm.api_base_url")).thenReturn(Optional.of("https://llm.example.com"));
    when(repository.findValue("llm.api_key")).thenReturn(Optional.of(secretCipher.encrypt("llm-secret")));
    when(repository.findValue("llm.model")).thenReturn(Optional.of("gpt-4.1-mini"));
    when(repository.findValue("llm.protocol")).thenReturn(Optional.of("openai_compatible"));
    when(repository.findValue("llm.timeout_ms")).thenReturn(Optional.of("15000"));
    when(repository.findValue("llm.temperature")).thenReturn(Optional.of("0.7"));
    when(repository.findValue("llm.max_tokens")).thenReturn(Optional.of("2048"));

    provider.refresh();

    assertThat(provider.get().apiBaseUrl()).isEqualTo("https://llm.example.com");
    assertThat(provider.get().apiKey()).isEqualTo("llm-secret");
    assertThat(provider.get().model()).isEqualTo("gpt-4.1-mini");
    assertThat(provider.get().protocol()).isEqualTo("OPENAI_COMPATIBLE");
    assertThat(provider.get().timeoutMs()).isEqualTo(15000);
    assertThat(provider.get().temperature()).isEqualTo(0.7);
    assertThat(provider.get().maxTokens()).isEqualTo(2048);
  }
}
