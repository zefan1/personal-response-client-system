package com.privateflow.modules.skill.health;

import com.privateflow.modules.skill.circuit.SkillCircuitBreaker;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SkillHealthMonitor {

  private static final Logger log = LoggerFactory.getLogger(SkillHealthMonitor.class);
  private final ConcurrentHashMap<Long, AtomicInteger> successes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, AtomicInteger> failures = new ConcurrentHashMap<>();
  private final SkillCircuitBreaker circuitBreaker;
  private final SkillConfigProvider configProvider;
  private volatile Instant lastCallAt;
  private int consecutiveAlertCount;

  public SkillHealthMonitor(SkillCircuitBreaker circuitBreaker, SkillConfigProvider configProvider) {
    this.circuitBreaker = circuitBreaker;
    this.configProvider = configProvider;
  }

  public void record(boolean success) {
    lastCallAt = Instant.now();
    ConcurrentHashMap<Long, AtomicInteger> target = success ? successes : failures;
    target.computeIfAbsent(epochSecond(), ignored -> new AtomicInteger()).incrementAndGet();
  }

  public int totalCalls5Min() {
    cleanup();
    return sum(successes) + sum(failures);
  }

  public double successRate5Min() {
    int total = totalCalls5Min();
    return total == 0 ? 1.0 : (double) sum(successes) / total;
  }

  public Instant lastCallAt() {
    return lastCallAt;
  }

  @Scheduled(fixedDelay = 300_000)
  public void checkHealth() {
    int total = totalCalls5Min();
    double failureRate = 1.0 - successRate5Min();
    if (total >= 10 && failureRate > configProvider.get().alertFailureRate()) {
      consecutiveAlertCount++;
    } else {
      consecutiveAlertCount = 0;
    }
    if (consecutiveAlertCount >= Math.max(1, configProvider.get().alertFailureDurationMinutes() / 5)) {
      log.error("SKILL_HEALTH_ALERT: failure_rate={}%, total_calls={}, circuit_state={}, duration={}min",
          failureRate * 100, total, circuitBreaker.state(), consecutiveAlertCount * 5);
    }
  }

  private void cleanup() {
    long min = epochSecond() - 300;
    successes.keySet().removeIf(key -> key < min);
    failures.keySet().removeIf(key -> key < min);
  }

  private int sum(ConcurrentHashMap<Long, AtomicInteger> map) {
    return map.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  private long epochSecond() {
    return Instant.now().getEpochSecond();
  }
}
