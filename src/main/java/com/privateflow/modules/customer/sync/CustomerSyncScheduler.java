package com.privateflow.modules.customer.sync;

import com.privateflow.common.events.NewLeadEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
import java.time.LocalDateTime;
import java.util.List;
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
  private static final List<String> TABLE_ORDER = List.of("推广组客资登记表", "私域客资管理表", "新客管理衔接表");
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<String, LocalDateTime> lastSyncTimes = new ConcurrentHashMap<>();
  private final SheetClient sheetClient;
  private final FieldMappingResolver mappingResolver;
  private final CustomerRepository customerRepository;
  private final CustomerCacheManager cacheManager;
  private final CustomerMergeEngine mergeEngine;
  private final SyncFailureRepository failureRepository;
  private final CustomerCacheProperties properties;
  private final ApplicationEventPublisher eventPublisher;

  public CustomerSyncScheduler(
      SheetClient sheetClient,
      FieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      CustomerCacheManager cacheManager,
      CustomerMergeEngine mergeEngine,
      SyncFailureRepository failureRepository,
      CustomerCacheProperties properties,
      ApplicationEventPublisher eventPublisher) {
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
      for (String table : TABLE_ORDER) {
        syncTable(table);
      }
    } finally {
      lock.unlock();
    }
  }

  private void syncTable(String sourceTable) {
    LocalDateTime roundStartedAt = LocalDateTime.now();
    try {
      LocalDateTime modifiedAfter = lastSyncTimes.getOrDefault(sourceTable, LocalDateTime.now().minusDays(1));
      List<SheetRow> rows = sheetClient.fetchIncrementalRows(sourceTable, modifiedAfter, properties.getMaxSyncRowsPerRound());
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
