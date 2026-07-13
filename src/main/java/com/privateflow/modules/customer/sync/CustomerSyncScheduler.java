package com.privateflow.modules.customer.sync;

import com.privateflow.common.events.NewLeadEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class CustomerSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(CustomerSyncScheduler.class);
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<Long, Boolean> runningSources = new ConcurrentHashMap<>();
  private final Map<String, LocalDateTime> lastSyncTimes = new ConcurrentHashMap<>();
  private final DatasourceAdminRepository datasourceRepository;
  private final SheetClient sheetClient;
  private final FieldMappingResolver mappingResolver;
  private final CustomerRepository customerRepository;
  private final CustomerCacheManager cacheManager;
  private final CustomerMergeEngine mergeEngine;
  private final SyncFailureRepository failureRepository;
  private final CustomerCacheProperties properties;
  private final ApplicationEventPublisher eventPublisher;

  public CustomerSyncScheduler(
      DatasourceAdminRepository datasourceRepository,
      SheetClient sheetClient,
      FieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      CustomerCacheManager cacheManager,
      CustomerMergeEngine mergeEngine,
      SyncFailureRepository failureRepository,
      CustomerCacheProperties properties,
      ApplicationEventPublisher eventPublisher) {
    this.datasourceRepository = datasourceRepository;
    this.sheetClient = sheetClient;
    this.mappingResolver = mappingResolver;
    this.customerRepository = customerRepository;
    this.cacheManager = cacheManager;
    this.mergeEngine = mergeEngine;
    this.failureRepository = failureRepository;
    this.properties = properties;
    this.eventPublisher = eventPublisher;
  }

  @Scheduled(cron = "${cache.sync-cron:0 */30 * * * *}")
  @Async("customerSyncExecutor")
  public void scheduledSync() {
    runOnce();
  }

  public void runOnce() {
    if (!lock.tryLock()) {
      log.info("customer sync skipped because previous round is still running");
      return;
    }
    try {
      for (SheetSource source : datasourceRepository.enabledSources()) {
        syncSource(source);
      }
    } finally {
      lock.unlock();
    }
  }

  public boolean runOne(SheetSource source) {
    if (runningSources.putIfAbsent(source.datasourceId(), Boolean.TRUE) != null) {
      log.info("customer sync skipped because datasource is already running, datasourceId={}", source.datasourceId());
      return false;
    }
    try {
      if (!lock.tryLock()) {
        log.info("customer sync skipped because previous round is still running");
        return false;
      }
      try {
        syncSource(source);
        return true;
      } finally {
        lock.unlock();
      }
    } finally {
      runningSources.remove(source.datasourceId());
    }
  }

  public boolean tryStartOneAsync(SheetSource source) {
    if (runningSources.putIfAbsent(source.datasourceId(), Boolean.TRUE) != null) {
      log.info("customer sync start rejected because datasource is already running, datasourceId={}", source.datasourceId());
      return false;
    }
    java.util.concurrent.CompletableFuture.runAsync(() -> {
      try {
        if (!lock.tryLock()) {
          log.info("customer sync skipped because previous round is still running");
          return;
        }
        try {
          syncSource(source);
        } finally {
          lock.unlock();
        }
      } finally {
        runningSources.remove(source.datasourceId());
      }
    });
    return true;
  }

  private void syncSource(SheetSource source) {
    LocalDateTime roundStartedAt = LocalDateTime.now();
    String sourceTable = source.sourceTable();
    try {
      LocalDateTime modifiedAfter = lastSyncTimes.getOrDefault(sourceTable, LocalDateTime.now().minusDays(1));
      java.util.List<SheetRow> rows = sheetClient.fetchIncrementalRows(source, modifiedAfter, properties.getMaxSyncRowsPerRound());
      for (SheetRow row : rows) {
        processRow(sourceTable, row);
      }
      lastSyncTimes.put(sourceTable, roundStartedAt);
    } catch (RuntimeException ex) {
      log.error("customer sync table failed, table={}, reason={}", sourceTable, ex.getMessage());
    }
  }

  private void processRow(String sourceTable, SheetRow row) {
    try {
      Customer incoming = mappingResolver.mapRow(sourceTable, row);
      if (incoming.getPhone() == null || incoming.getPhone().isBlank()) {
        failureRepository.record(sourceTable, row.rowId(), null, "手机号为空", row.values().toString());
        return;
      }
      Customer existing = customerRepository.findByPhone(incoming.getPhone()).orElse(null);
      boolean isNewPromoLead = existing == null && "推广组客资登记表".equals(sourceTable);
      Customer merged = mergeEngine.merge(incoming, existing);
      customerRepository.upsert(merged);
      customerRepository.findByPhone(merged.getPhone()).ifPresent(cacheManager::write);
      if (isNewPromoLead) {
        eventPublisher.publishEvent(new NewLeadEvent(merged.getPhone(), merged.getLeadType(), sourceTable));
      }
    } catch (RuntimeException ex) {
      failureRepository.record(sourceTable, row.rowId(), row.values().get("phone"), ex.getMessage(), row.values().toString());
    }
  }

  public boolean waitForIdle(long timeout, TimeUnit unit) throws InterruptedException {
    if (lock.tryLock(timeout, unit)) {
      lock.unlock();
      return true;
    }
    return false;
  }
}
