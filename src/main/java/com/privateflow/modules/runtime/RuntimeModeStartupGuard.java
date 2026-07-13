package com.privateflow.modules.runtime;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class RuntimeModeStartupGuard {

  private final RuntimeModeService runtimeModeService;

  public RuntimeModeStartupGuard(RuntimeModeService runtimeModeService) {
    this.runtimeModeService = runtimeModeService;
  }

  @PostConstruct
  public void validate() {
    runtimeModeService.validateProductionSafety();
  }
}
