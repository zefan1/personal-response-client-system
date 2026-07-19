package com.privateflow.modules.image.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import org.junit.jupiter.api.Test;

class ImageRecognitionPromptContractTest {

  @Test
  void defaultRecognitionPromptAllowsNicknameOnlyMatches() {
    ImageConfigProvider provider = new ImageConfigProvider(
        mock(SystemConfigRepository.class),
        new SecretCipher("test-secret-key"));

    provider.refresh();

    assertThat(provider.get().recognitionPrompt()).contains("Nickname-only success rule");
  }
}
