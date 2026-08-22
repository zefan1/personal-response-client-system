package com.privateflow.modules.tablewrite.config;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import org.springframework.stereotype.Component;

/** Resolves the official WeCom endpoint at request time so administrators can switch relay modes safely. */
@Component
public final class WecomApiEndpointProvider {

  private static final String DIRECT_ENDPOINT = "https://qyapi.weixin.qq.com";
  private final SystemConfigRepository configRepository;

  public WecomApiEndpointProvider(SystemConfigRepository configRepository) {
    this.configRepository = configRepository;
  }

  public String currentBaseUrl(String deploymentBaseUrl) {
    if (currentMode() == WecomTransportMode.DIRECT) {
      return DIRECT_ENDPOINT;
    }
    return currentRelayBaseUrl(deploymentBaseUrl);
  }

  public WecomTransportMode currentMode() {
    return configRepository.findValue("wecom.connection_mode")
        .map(WecomTransportMode::from)
        .orElse(WecomTransportMode.RELAY);
  }

  public String currentRelayBaseUrl(String deploymentBaseUrl) {
    return configRepository.findValue("wecom.relay_base_url")
        .map(WecomApiEndpointProvider::normalized)
        .filter(value -> !value.isBlank())
        .orElseGet(() -> normalized(deploymentBaseUrl));
  }

  private static String normalized(String value) {
    String result = value == null ? "" : value.trim();
    while (result.endsWith("/")) {
      result = result.substring(0, result.length() - 1);
    }
    if (result.endsWith("/v1/wecom/api")) {
      result = result.substring(0, result.length() - "/v1/wecom/api".length());
    } else if (result.endsWith("/wecom")) {
      result = result.substring(0, result.length() - "/wecom".length());
    }
    return result;
  }
}
