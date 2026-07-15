package com.privateflow.modules.skill.circuit;

import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
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
  private final ConcurrentHashMap<Scene, SceneCircuit> circuits = new ConcurrentHashMap<>();
  private final SkillConfigProvider configProvider;

  public SkillCircuitBreaker(SkillConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public boolean allowRequest(Scene scene) {
    SkillConfig config = configProvider.get();
    SceneCircuit circuit = circuit(scene);
    CircuitState current = circuit.state.get();
    if (current == CircuitState.CLOSED) {
      return true;
    }
    if (current == CircuitState.OPEN) {
      if (circuit.openedAt != null
          && Instant.now().isAfter(circuit.openedAt.plusSeconds(config.circuitBreakerOpenS()))) {
        circuit.state.set(CircuitState.HALF_OPEN);
      } else {
        return false;
      }
    }
    return circuit.halfOpenProbeInFlight.compareAndSet(false, true);
  }

  public void recordSuccess(Scene scene) {
    SceneCircuit circuit = circuit(scene);
    circuit.successes.computeIfAbsent(epochSecond(), ignored -> new AtomicInteger()).incrementAndGet();
    if (circuit.state.get() == CircuitState.HALF_OPEN) {
      circuit.successes.clear();
      circuit.failures.clear();
      circuit.halfOpenProbeInFlight.set(false);
      circuit.state.set(CircuitState.CLOSED);
      log.info("Skill circuit breaker closed after successful probe, scene={}", scene);
      return;
    }
    circuit.halfOpenProbeInFlight.set(false);
    evaluate(scene, circuit);
  }

  public void recordFailure(Scene scene) {
    SceneCircuit circuit = circuit(scene);
    circuit.failures.computeIfAbsent(epochSecond(), ignored -> new AtomicInteger()).incrementAndGet();
    if (circuit.state.get() == CircuitState.HALF_OPEN) {
      open(scene, circuit);
      circuit.halfOpenProbeInFlight.set(false);
      return;
    }
    circuit.halfOpenProbeInFlight.set(false);
    evaluate(scene, circuit);
  }

  public CircuitState state() {
    if (circuits.values().stream().anyMatch(circuit -> circuit.state.get() == CircuitState.OPEN)) {
      return CircuitState.OPEN;
    }
    if (circuits.values().stream().anyMatch(circuit -> circuit.state.get() == CircuitState.HALF_OPEN)) {
      return CircuitState.HALF_OPEN;
    }
    return CircuitState.CLOSED;
  }

  public CircuitState state(Scene scene) {
    return circuit(scene).state.get();
  }

  public int totalCalls(Scene scene) {
    SceneCircuit circuit = circuit(scene);
    cleanup(circuit);
    return sum(circuit.successes) + sum(circuit.failures);
  }

  public double failureRate(Scene scene) {
    SceneCircuit circuit = circuit(scene);
    cleanup(circuit);
    int total = sum(circuit.successes) + sum(circuit.failures);
    return total == 0 ? 0.0 : (double) sum(circuit.failures) / total;
  }

  private void evaluate(Scene scene, SceneCircuit circuit) {
    SkillConfig config = configProvider.get();
    int total = totalCalls(scene);
    if (circuit.state.get() == CircuitState.CLOSED
        && total >= config.circuitBreakerMinCalls()
        && failureRate(scene) > config.circuitBreakerFailureRate()) {
      open(scene, circuit);
    }
  }

  private void open(Scene scene, SceneCircuit circuit) {
    circuit.openedAt = Instant.now();
    circuit.state.set(CircuitState.OPEN);
    log.warn(
        "Skill circuit breaker opened, scene={}, failureRate={}, totalCalls={}",
        scene,
        failureRate(scene),
        totalCalls(scene));
  }

  private void cleanup(SceneCircuit circuit) {
    long min = epochSecond() - configProvider.get().circuitBreakerWindowS();
    circuit.successes.keySet().removeIf(key -> key < min);
    circuit.failures.keySet().removeIf(key -> key < min);
  }

  private int sum(Map<Long, AtomicInteger> buckets) {
    return buckets.values().stream().mapToInt(AtomicInteger::get).sum();
  }

  private long epochSecond() {
    return Instant.now().getEpochSecond();
  }

  private SceneCircuit circuit(Scene scene) {
    return circuits.computeIfAbsent(Objects.requireNonNull(scene, "scene"), ignored -> new SceneCircuit());
  }

  private static final class SceneCircuit {
    private final ConcurrentHashMap<Long, AtomicInteger> successes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final AtomicReference<CircuitState> state = new AtomicReference<>(CircuitState.CLOSED);
    private final AtomicBoolean halfOpenProbeInFlight = new AtomicBoolean(false);
    private volatile Instant openedAt;
  }
}
