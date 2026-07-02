package com.privateflow.modules.api.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ApiModuleConfiguration {

  @Bean(name = "wsBroadcastExecutor")
  public Executor wsBroadcastExecutor() {
    return executor("ws-broadcast-", 2, 4, 200, 30);
  }

  @Bean(name = "auditLogExecutor")
  public Executor auditLogExecutor() {
    return executor("audit-log-", 2, 4, 500, 30);
  }

  @Bean(name = "apiOrchestrationExecutor")
  public Executor apiOrchestrationExecutor() {
    return executor("api-orchestration-", 4, 8, 200, 30);
  }

  private Executor executor(String prefix, int core, int max, int queue, int shutdownSeconds) {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix(prefix);
    executor.setCorePoolSize(core);
    executor.setMaxPoolSize(max);
    executor.setQueueCapacity(queue);
    executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(shutdownSeconds);
    executor.initialize();
    return executor;
  }
}
