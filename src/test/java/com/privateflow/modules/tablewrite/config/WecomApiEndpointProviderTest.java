package com.privateflow.modules.tablewrite.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WecomApiEndpointProviderTest {

  @Test
  void usesTheRelayAddressWhenRelayModeIsConfigured() {
    SystemConfigRepository repository = mock(SystemConfigRepository.class);
    when(repository.findValue("wecom.connection_mode")).thenReturn(Optional.of("RELAY"));
    when(repository.findValue("wecom.relay_base_url")).thenReturn(Optional.of(" https://relay.example.com/// "));

    WecomApiEndpointProvider provider = new WecomApiEndpointProvider(repository);

    assertThat(provider.currentBaseUrl("https://legacy-relay.example.com")).isEqualTo("https://relay.example.com");
  }

  @Test
  void usesTheOfficialEndpointWhenDirectModeIsConfigured() {
    SystemConfigRepository repository = mock(SystemConfigRepository.class);
    when(repository.findValue("wecom.connection_mode")).thenReturn(Optional.of("DIRECT"));

    WecomApiEndpointProvider provider = new WecomApiEndpointProvider(repository);

    assertThat(provider.currentBaseUrl("https://legacy-relay.example.com")).isEqualTo("https://qyapi.weixin.qq.com");
  }

  @Test
  void keepsTheDeploymentEndpointUntilAnExistingRelayIsSavedInTheConsole() {
    SystemConfigRepository repository = mock(SystemConfigRepository.class);
    when(repository.findValue("wecom.connection_mode")).thenReturn(Optional.of("RELAY"));
    when(repository.findValue("wecom.relay_base_url")).thenReturn(Optional.empty());

    WecomApiEndpointProvider provider = new WecomApiEndpointProvider(repository);

    assertThat(provider.currentBaseUrl("https://legacy-relay.example.com")).isEqualTo("https://legacy-relay.example.com");
  }
}
