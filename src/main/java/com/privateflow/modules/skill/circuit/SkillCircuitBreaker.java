package com.privateflow.modules.skill.circuit;

import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SkillCircuitBreaker {

  private static final Logger log = LoggerFactory.getLogger(SkillCircuitBreaker.class);
  private final ConcurrentHashMap<Long, AtomicInteger> successes = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<Long, AtomicInteger> failures = new ConcurrentHashMap<>();
  private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
  private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);
  private final SkillConfigProvider configProvider;
  private volatile Instant openedAt;

  public SkillCircuitBreaker(SkillConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public boolean allowRequest() {
    SkillConfig config = configProvider.get();
    CircuitState current = state.get();
    if (current == CircuitState.CLOSED) {
      return true;
    }
    if (current == CircuitState.OPEN) {
      if (openedAt != null && Instant.now().isAfter(openedAt.plusSeconds(config.circuitBreakerOpenS()))) {
        state.set(CircuitState.HALF_OPEN);
      } else {
        return false;
      }
    }
    return halfOpenProbeInFlight.compareAndSet(false, true);
  }

  public void recordSuccess() {
    successes.computeIfAbsent(epochSecond(), ignored -> new AtomicInteger()).incrementAndGet();
    if (state.get() == CircuitState.HALF_OPEN) {
      successes.clear();
      failures.clear();
      halfOpenProbeInFlight.set(false);
      state.set(CircuitState.CLOSED);
      log.info("Skill circuit breaker closed after successful probe");
      return;
    }
    halfOpenProbeInFlight.set(false);
    evaluate();
  }

  public void recordFailure() {
    failures.computeIfAbsent(epochSecond(), ignored -> new AtomicInteger()).incrementAndGet();
    if (state.get() == CircuitState.HALF_OPEN) {
      open();
      halfOpenProbeInFlight.set(false);
      return;
    }
    halfOpenProbeInFlight.set(false);
    evaluate();
  }

  public CircuitState state() {
    return state.get();
  }

  public int totalCalls() {
    cleanup();
    return sum(successes) + sum(failures);
  }

  public double failureRate() {
    cleanup();
    int total = totalCalls();
    return total == 0 ? 0.0 : (double) sum(failures) / total;
  }

  private void evaluate() {
    SkillConfig config = configProvider.get();
    int total = totalCalls();
    if (state.get() == CircuitState.CLOSED && total >= config.circuitBreakerMinCalls() && failureRate() > config.circuitBreakerFailureRate()) {
      open();
    }
  }

  private void open() {
    openedAt = Instant.now();
    state.set(CircuitState.OPEN);
    log.warn("Skill circuit breaker opened, failureRate={}, totalCalls={}", failureRate(), totalCalls());
  }

  private void cleanup() {
    long min = epochSecond() - configProvider.get().circuitBreakerWindowS();
    successes.keySet().removeIf(key -> key < min);
    failures.keySet().removeIf(key -> key < min);
  }

  private int sum(Map<Long, AtomicInteger> buckets) {
    return buckets.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  private long epochSecond() {
    return Instant.now().getEpochSecond();
  }
}
