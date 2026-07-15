package com.privateflow.modules.skill.circuit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import org.junit.jupiter.api.Test;

class SkillCircuitBreakerTest {

  @Test
  void isolatesFailureWindowsAndOpenStateByScene() {
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    when(configProvider.get()).thenReturn(config());
    SkillCircuitBreaker breaker = new SkillCircuitBreaker(configProvider);

    breaker.recordFailure(Scene.PROFILE_EXTRACT);

    assertThat(breaker.state(Scene.PROFILE_EXTRACT)).isEqualTo(CircuitState.OPEN);
    assertThat(breaker.allowRequest(Scene.PROFILE_EXTRACT)).isFalse();
    assertThat(breaker.state(Scene.ACTIVE_REPLY)).isEqualTo(CircuitState.CLOSED);
    assertThat(breaker.allowRequest(Scene.ACTIVE_REPLY)).isTrue();
  }

  private SkillConfig config() {
    return new SkillConfig(
        "http://localhost", "key", "LAST_FOUR", "", 1000, 30, 0.5, 1, 30,
        "fallback", "", "", "", "prompt", "", 0.3, 15, 8000, 3);
  }
}
