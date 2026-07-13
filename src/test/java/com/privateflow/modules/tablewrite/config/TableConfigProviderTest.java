package com.privateflow.modules.tablewrite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.security.SecretCipher;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TableConfigProviderTest {

  private final SystemConfigRepository repository = Mockito.mock(SystemConfigRepository.class);
  private final SecretCipher secretCipher = new SecretCipher("test-secret-key");

  @Test
  void decryptsEncryptedTableApiKeyFromConfigCenter() {
    when(repository.findByPrefix("table.")).thenReturn(Map.of(
        "table.api_base_url", "https://table.example.com",
        "table.api_key", secretCipher.encrypt("table-secret-1234")));
    TableConfigProvider provider = new TableConfigProvider(repository, secretCipher, 10000, 5, 60, 1, "ADMIN", 100, 1000);

    provider.refresh();

    assertThat(provider.get().apiBaseUrl()).isEqualTo("https://table.example.com");
    assertThat(provider.get().apiKey()).isEqualTo("table-secret-1234");
  }
}
