package com.privateflow.modules.image.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ImageConfigProviderTest {

  private final SystemConfigRepository repository = Mockito.mock(SystemConfigRepository.class);
  private final SecretCipher secretCipher = new SecretCipher("test-secret-key");
  private final ImageConfigProvider provider = new ImageConfigProvider(repository, secretCipher);

  @Test
  void decryptsEncryptedImageApiKeyFromSystemConfig() {
    when(repository.findValue("image.api_key")).thenReturn(Optional.of(secretCipher.encrypt("image-secret")));

    provider.refresh();

    assertThat(provider.get().apiKey()).isEqualTo("image-secret");
  }
}
