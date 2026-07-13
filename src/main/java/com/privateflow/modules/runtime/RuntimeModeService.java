package com.privateflow.modules.runtime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class RuntimeModeService {

  private static final String MOCK_LABEL = "本地模拟模式";
  private static final String REAL_LABEL = "真实接口模式";
  private static final String MOCK_DESCRIPTION = "外部表格、AI 技能和图片识别使用本地 Mock 响应。";
  private static final String REAL_DESCRIPTION = "外部表格、AI 技能和图片识别调用真实接口。";

  private final boolean mockExternals;
  private final Environment environment;

  public RuntimeModeService(
      @Value("${app.mock-externals:false}") boolean mockExternals,
      Environment environment) {
    this.mockExternals = mockExternals;
    this.environment = environment;
  }

  public RuntimeModeStatus currentMode() {
    if (mockExternals) {
      return new RuntimeModeStatus(true, MOCK_LABEL, MOCK_DESCRIPTION);
    }
    return new RuntimeModeStatus(false, REAL_LABEL, REAL_DESCRIPTION);
  }

  public void validateProductionSafety() {
    if (mockExternals && isProductionEnvironment()) {
      throw new IllegalStateException("MOCK_EXTERNALS/app.mock-externals must be false in production");
    }
  }

  public boolean isProductionEnvironment() {
    return environmentLabels().stream().anyMatch(this::isProductionLabel);
  }

  private List<String> environmentLabels() {
    List<String> labels = new ArrayList<>();
    labels.addAll(Arrays.asList(environment.getActiveProfiles()));
    addProperty(labels, "app.environment");
    addProperty(labels, "APP_ENV");
    addProperty(labels, "ENVIRONMENT");
    addProperty(labels, "SPRING_PROFILES_ACTIVE");
    return labels;
  }

  private void addProperty(List<String> labels, String key) {
    String value = environment.getProperty(key);
    if (value == null || value.isBlank()) {
      return;
    }
    labels.addAll(Arrays.asList(value.split("[,;\\s]+")));
  }

  private boolean isProductionLabel(String rawLabel) {
    if (rawLabel == null) {
      return false;
    }
    String label = rawLabel.trim().toLowerCase();
    return "prod".equals(label)
        || "production".equals(label)
        || label.startsWith("prod-")
        || label.startsWith("prod_")
        || label.startsWith("production-")
        || label.startsWith("production_");
  }
}
