package com.privateflow.modules.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeModeServiceTest {

  @Test
  void mockModeStatusIsExplicitForFrontend() {
    RuntimeModeService service = new RuntimeModeService(true, new MockEnvironment());

    RuntimeModeStatus status = service.currentMode();

    assertThat(status.mockExternals()).isTrue();
    assertThat(status.label()).isEqualTo("本地模拟模式");
    assertThat(status.description()).contains("Mock");
  }

  @Test
  void realModeStatusIsExplicitForFrontend() {
    RuntimeModeService service = new RuntimeModeService(false, new MockEnvironment());

    RuntimeModeStatus status = service.currentMode();

    assertThat(status.mockExternals()).isFalse();
    assertThat(status.label()).isEqualTo("真实接口模式");
    assertThat(status.description()).contains("真实接口");
  }

  @Test
  void productionMockModeFailsStartupGuard() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("app.environment", "production");
    RuntimeModeService service = new RuntimeModeService(true, environment);
    RuntimeModeStartupGuard guard = new RuntimeModeStartupGuard(service);

    assertThatThrownBy(guard::validate)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must be false in production");
  }

  @Test
  void prodSpringProfileAlsoFailsStartupGuard() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("SPRING_PROFILES_ACTIVE", "local,prod");
    RuntimeModeService service = new RuntimeModeService(true, environment);

    assertThatThrownBy(service::validateProductionSafety)
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void localMockModeIsAllowed() {
    MockEnvironment environment = new MockEnvironment()
        .withProperty("app.environment", "local");
    RuntimeModeService service = new RuntimeModeService(true, environment);

    service.validateProductionSafety();

    assertThat(service.isProductionEnvironment()).isFalse();
  }
}
