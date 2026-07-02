package com.privateflow.modules.image.health;

import com.privateflow.common.events.ImageServiceStatusEvent;
import com.privateflow.modules.image.config.ImageConfigProvider;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class ImageServiceHealthMonitor {

  private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
  private final AtomicReference<ServiceStatus> status = new AtomicReference<>(ServiceStatus.UP);
  private final ImageConfigProvider configProvider;
  private final ApplicationEventPublisher eventPublisher;
  private volatile String lastErrorMsg;
  private volatile Instant lastFailureAt;
  private volatile Instant lastSuccessAt;

  public ImageServiceHealthMonitor(ImageConfigProvider configProvider, ApplicationEventPublisher eventPublisher) {
    this.configProvider = configProvider;
    this.eventPublisher = eventPublisher;
  }

  public void recordFailure(Throwable error) {
    lastErrorMsg = error == null ? null : error.getMessage();
    lastFailureAt = Instant.now();
    int failures = consecutiveFailures.incrementAndGet();
    if (failures >= configProvider.get().consecutiveFailuresAlert()
        && status.compareAndSet(ServiceStatus.UP, ServiceStatus.DOWN)) {
      eventPublisher.publishEvent(new ImageServiceStatusEvent("DOWN", failures, lastErrorMsg, Instant.now()));
    }
  }

  public void recordSuccess() {
    consecutiveFailures.set(0);
    lastErrorMsg = null;
    lastSuccessAt = Instant.now();
    if (status.compareAndSet(ServiceStatus.DOWN, ServiceStatus.UP)) {
      eventPublisher.publishEvent(new ImageServiceStatusEvent("UP", 0, null, Instant.now()));
    }
  }

  public ServiceStatus status() {
    return status.get();
  }

  public int consecutiveFailures() {
    return consecutiveFailures.get();
  }

  public String lastErrorMsg() {
    return lastErrorMsg;
  }

  public Instant lastFailureAt() {
    return lastFailureAt;
  }

  public Instant lastSuccessAt() {
    return lastSuccessAt;
  }
}
