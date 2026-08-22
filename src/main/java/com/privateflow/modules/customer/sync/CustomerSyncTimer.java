package com.privateflow.modules.customer.sync;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Runs inbound Smart Sheet reads independently from the global retry scheduler. */
@Component
public final class CustomerSyncTimer implements ApplicationRunner, DisposableBean {

  private final CustomerSyncScheduler scheduler;
  private final long intervalMs;
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
    Thread thread = new Thread(runnable, "customer-sync-timer");
    thread.setDaemon(false);
    return thread;
  });

  public CustomerSyncTimer(
      CustomerSyncScheduler scheduler,
      @Value("${cache.sync-interval-ms:60000}") long intervalMs) {
    this.scheduler = scheduler;
    this.intervalMs = intervalMs;
  }

  @Override
  public void run(org.springframework.boot.ApplicationArguments args) {
    executor.scheduleWithFixedDelay(scheduler::scheduledSync, 1, intervalMs, TimeUnit.MILLISECONDS);
  }

  @Override
  public void destroy() {
    executor.shutdown();
  }
}
