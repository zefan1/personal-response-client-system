package com.privateflow.modules.runtime;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class ProductionStartupGuard {

  private final ProductionSafetyService productionSafetyService;

  public ProductionStartupGuard(ProductionSafetyService productionSafetyService) {
    this.productionSafetyService = productionSafetyService;
  }

  @PostConstruct
  public void validate() {
    productionSafetyService.validateStartupSafety();
  }
}
