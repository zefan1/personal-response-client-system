package com.privateflow.modules.skill.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SkillConfigProviderTest {

  private final SystemConfigRepository repository = Mockito.mock(SystemConfigRepository.class);
  private final SecretCipher secretCipher = new SecretCipher("test-secret-key");
  private final SkillConfigProvider provider = new SkillConfigProvider(repository, secretCipher);

  @BeforeEach
  void setUp() {
    when(repository.findValue(anyString())).thenReturn(Optional.empty());
  }

  @Test
  void prefersConfigCenterPromptKeysAndConvertsRedLineArray() {
    when(repository.findValue("skill.system_prompt_format")).thenReturn(Optional.of("新版 Prompt {{red_lines}} {{available_tags}} {{scene}}"));
    when(repository.findValue("skill.system_prompt_red_lines")).thenReturn(Optional.of("[\"不得承诺疗效\",\"不得虚假优惠\"]"));

    provider.refresh();

    SkillConfig config = provider.get();
    assertThat(config.systemPromptTemplate()).isEqualTo("新版 Prompt {{red_lines}} {{available_tags}} {{scene}}");
    assertThat(config.redLines()).isEqualTo("不得承诺疗效\n不得虚假优惠");
  }

  @Test
  void fallsBackToLegacyPromptKeysForExistingDeployments() {
    when(repository.findValue("skill.system_prompt_format")).thenReturn(Optional.empty());
    when(repository.findValue("skill.system_prompt_template")).thenReturn(Optional.of("旧版 Prompt"));
    when(repository.findValue("skill.system_prompt_red_lines")).thenReturn(Optional.empty());
    when(repository.findValue("skill.red_lines")).thenReturn(Optional.of("旧红线"));

    provider.refresh();

    SkillConfig config = provider.get();
    assertThat(config.systemPromptTemplate()).isEqualTo("旧版 Prompt");
    assertThat(config.redLines()).isEqualTo("旧红线");
  }

  @Test
  void decryptsEncryptedSkillApiKeyFromSystemConfig() {
    when(repository.findValue("skill.api_key")).thenReturn(Optional.of(secretCipher.encrypt("skill-secret")));

    provider.refresh();

    assertThat(provider.get().apiKey()).isEqualTo("skill-secret");
  }

  @Test
  void readsRegenerateMaxCountFromConfigCenter() {
    when(repository.findValue("skill.regenerate_max_count")).thenReturn(Optional.of("5"));

    provider.refresh();

    assertThat(provider.get().regenerateMaxCount()).isEqualTo(5);
  }

  @Test
  void keepsPreviousRegenerateMaxCountForInvalidConfigCenterValue() {
    assertThat(provider.get().regenerateMaxCount()).isEqualTo(3);
    when(repository.findValue("skill.regenerate_max_count")).thenReturn(Optional.of("5"));
    provider.refresh();
    assertThat(provider.get().regenerateMaxCount()).isEqualTo(5);

    when(repository.findValue("skill.regenerate_max_count")).thenReturn(Optional.of("99"));
    provider.refresh();

    assertThat(provider.get().regenerateMaxCount()).isEqualTo(5);
  }
}
