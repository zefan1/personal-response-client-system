package com.privateflow.modules.customer.sync;

import com.privateflow.common.events.NewLeadEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.api.auth.Account;
import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.customer.config.CustomerCacheProperties;
import com.privateflow.modules.customer.infra.CustomerCacheManager;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.history.CustomerFieldHistoryService;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
import com.privateflow.modules.tablewrite.service.AuxiliarySmartSheetProjectionService;
import com.privateflow.modules.tablewrite.service.CustomerMasterProjectionService;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CustomerSyncScheduler {

  private static final Logger log = LoggerFactory.getLogger(CustomerSyncScheduler.class);
  private static final LocalDateTime FIRST_SYNC_FROM = LocalDate.of(2000, 1, 1).atStartOfDay();
  private final ReentrantLock lock = new ReentrantLock();
  private final Map<Long, Boolean> runningSources = new ConcurrentHashMap<>();
  private final DatasourceAdminRepository datasourceRepository;
  private final SheetClient sheetClient;
  private final FieldMappingResolver mappingResolver;
  private final CustomerRepository customerRepository;
  private final CustomerCacheManager cacheManager;
  private final CustomerMergeEngine mergeEngine;
  private final SyncFailureRepository failureRepository;
  private final CustomerCacheProperties properties;
  private final ApplicationEventPublisher eventPublisher;
  private final AuxiliarySmartSheetProjectionService projectionService;
  private final CustomerMasterProjectionService customerMasterProjectionService;
  private final CustomerFieldHistoryService historyService;
  private final AccountRepository accountRepository;

  @Autowired
  public CustomerSyncScheduler(
      DatasourceAdminRepository datasourceRepository,
      SheetClient sheetClient,
      FieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      CustomerCacheManager cacheManager,
      CustomerMergeEngine mergeEngine,
      SyncFailureRepository failureRepository,
      CustomerCacheProperties properties,
      ApplicationEventPublisher eventPublisher,
      AuxiliarySmartSheetProjectionService projectionService,
      CustomerMasterProjectionService customerMasterProjectionService,
      CustomerFieldHistoryService historyService,
      AccountRepository accountRepository) {
    this.datasourceRepository = datasourceRepository;
    this.sheetClient = sheetClient;
    this.mappingResolver = mappingResolver;
    this.customerRepository = customerRepository;
    this.cacheManager = cacheManager;
    this.mergeEngine = mergeEngine;
    this.failureRepository = failureRepository;
    this.properties = properties;
    this.eventPublisher = eventPublisher;
    this.projectionService = projectionService;
    this.customerMasterProjectionService = customerMasterProjectionService;
    this.historyService = historyService;
    this.accountRepository = accountRepository;
  }

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
    this(datasourceRepository, sheetClient, mappingResolver, customerRepository, cacheManager,
        mergeEngine, failureRepository, properties, eventPublisher, null, null, null, null);
  }

  public CustomerSyncScheduler(
      DatasourceAdminRepository datasourceRepository,
      SheetClient sheetClient,
      FieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      CustomerCacheManager cacheManager,
      CustomerMergeEngine mergeEngine,
      SyncFailureRepository failureRepository,
      CustomerCacheProperties properties,
      ApplicationEventPublisher eventPublisher,
      AuxiliarySmartSheetProjectionService projectionService,
      CustomerMasterProjectionService customerMasterProjectionService,
      CustomerFieldHistoryService historyService) {
    this(datasourceRepository, sheetClient, mappingResolver, customerRepository, cacheManager,
        mergeEngine, failureRepository, properties, eventPublisher, projectionService,
        customerMasterProjectionService, historyService, null);
  }

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
        syncSource(source, false);
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
        syncSource(source, true);
        return true;
      } finally {
        lock.unlock();
      }
    } finally {
      runningSources.remove(source.datasourceId());
    }
  }

  public boolean tryStartOneAsync(SheetSource source) {
    return tryStartOneAsync(source, false);
  }

  public boolean tryStartOneAsync(SheetSource source, boolean forceFullSync) {
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
          syncSource(source, true, forceFullSync);
        } finally {
          lock.unlock();
        }
      } finally {
        runningSources.remove(source.datasourceId());
      }
    });
    return true;
  }

  private void syncSource(SheetSource source, boolean allowInitialFullSync) {
    syncSource(source, allowInitialFullSync, false);
  }

  private void syncSource(SheetSource source, boolean allowInitialFullSync, boolean forceFullSync) {
    LocalDateTime roundStartedAt = LocalDateTime.now();
    String sourceTable = source.sourceTable();
    try {
      java.util.Optional<LocalDateTime> successfulSync = datasourceRepository.lastSuccessfulSync(sourceTable);
      boolean fullBackfill = forceFullSync || (allowInitialFullSync && successfulSync.isEmpty());
      LocalDateTime modifiedAfter = fullBackfill
          ? FIRST_SYNC_FROM
          : successfulSync.orElseGet(() -> roundStartedAt.minusDays(1));
      String mode = fullBackfill ? "initial-full" : "incremental";
      log.info("customer sync started, table={}, mode={}, modifiedAfter={}, outboundProjection={}",
          sourceTable, mode, modifiedAfter, false);
      java.util.List<SheetRow> rows = sheetClient.fetchIncrementalRows(source, modifiedAfter, properties.getMaxSyncRowsPerRound());
      // This heartbeat proves the Smart Sheet was actually readable. It deliberately
      // advances before row validation so an empty phone number cannot look like a
      // WeCom connection outage in the health screen.
      datasourceRepository.saveSuccessfulRemoteRead(sourceTable, LocalDateTime.now());
      boolean allRowsSucceeded = true;
      for (SheetRow row : rows) {
        // A Smart Sheet change is inbound-only. Echoing it back to WeCom would create a sync loop.
        allRowsSucceeded &= processRow(sourceTable, row, false);
      }
      if (allRowsSucceeded) {
        datasourceRepository.saveSuccessfulSync(sourceTable, roundStartedAt);
        log.info("customer sync completed, table={}, mode={}, rows={}", sourceTable, mode, rows.size());
      } else {
        log.warn("customer sync watermark not advanced because at least one row failed, table={}", sourceTable);
      }
    } catch (RuntimeException ex) {
      log.error("customer sync table failed, table={}, reason={}", sourceTable, ex.getMessage());
    }
  }

  private boolean processRow(String sourceTable, SheetRow row, boolean allowOutboundProjection) {
    Customer incoming = null;
    try {
      FieldMappingResult mapping = mappingResolver.mapRowResult(sourceTable, row);
      incoming = mapping.customer();
      if (incoming.getPhone() == null || incoming.getPhone().isBlank()) {
        failureRepository.record(sourceTable, row.rowId(), null, "手机号为空", row.values().toString());
        return false;
      }
      Customer existing = customerRepository.findByPhone(incoming.getPhone()).orElse(null);
      if (incoming.getAssignedKeeper() != null && !incoming.getAssignedKeeper().isBlank()) {
        incoming.setAssignedKeeper(resolveKeeperName(incoming.getAssignedKeeper()));
      }
      Customer merged = mergeEngine.merge(incoming, existing);
      boolean mappedBusinessChanged = hasMappedBusinessChange(mapping, existing, merged);
      boolean leadValidityChanged = needsLeadValiditySynchronization(mapping, existing, merged);
      if (leadValidityChanged) {
        synchronizeLeadValidityFromStage(mapping, merged, existing);
      }
      if (mappedBusinessChanged || leadValidityChanged) {
        customerRepository.upsert(
            merged,
            mapping.tagExchange(),
            com.privateflow.modules.tags.TagExchangeSourceType.EXTERNAL_SYNC,
            row.rowId());
        if (leadValidityChanged) {
          customerRepository.updateLeadProcessingState(merged);
        }
        if (historyService != null) {
          historyService.recordExternalSync(
              existing,
              merged.getPhone(),
              sourceTable,
              mappingResolver.sourceFieldsFor(sourceTable));
        }
        customerRepository.findByPhone(merged.getPhone()).ifPresent(cacheManager::write);
      }
      if (allowOutboundProjection && customerMasterProjectionService != null && sourceTable.startsWith("ASSIGNMENT:")) {
        customerMasterProjectionService.projectAssignment(merged);
      }
      if (allowOutboundProjection && projectionService != null) {
        try {
          projectionService.project(merged);
        } catch (RuntimeException projectionFailure) {
          log.warn("客户辅助表投影失败, phone={}, reason={}",
              merged.getPhone(), projectionFailure.getMessage());
        }
      }
      // The current assignment sheet is the source of truth for new ownership. The
      // event is idempotent in ActionExecutor, so repeated sync rounds are harmless.
      if (sourceTable.startsWith("ASSIGNMENT:")
          && merged.getAssignedKeeper() != null
          && !merged.getAssignedKeeper().isBlank()
          && merged.getAssignedAt() != null
          && merged.getAssignedAt().toLocalDate().equals(LocalDate.now())) {
        eventPublisher.publishEvent(new NewLeadEvent(merged.getPhone(), merged.getLeadType(), sourceTable));
      }
      return true;
    } catch (RuntimeException ex) {
      failureRepository.record(sourceTable, row.rowId(), incoming == null ? null : incoming.getPhone(),
          ex.getMessage(), row.values().toString());
      return false;
    }
  }

  public boolean waitForIdle(long timeout, TimeUnit unit) throws InterruptedException {
    if (lock.tryLock(timeout, unit)) {
      lock.unlock();
      return true;
    }
    return false;
  }

  private String resolveKeeperName(String raw) {
    if (raw == null || raw.isBlank() || accountRepository == null) {
      return raw;
    }
    String value = raw.trim();
    return accountRepository.resolveEnabledDisplayName(value).orElse(value);
  }

  /**
   * A manual write is projected to WeCom and then appears in the next inbound sync round.
   * Do not treat that identical read-back as a new edit: upsert increments the optimistic
   * version, which would make an unchanged desktop card stale.
   */
  private boolean hasMappedBusinessChange(FieldMappingResult mapping, Customer existing, Customer merged) {
    if (existing == null) {
      return true;
    }
    for (String field : mapping.mappedFields()) {
      try {
        PropertyDescriptor descriptor = new PropertyDescriptor(field, Customer.class);
        Method getter = descriptor.getReadMethod();
        if (getter == null) {
          log.warn("customer sync cannot compare mapped field without getter, field={}", field);
          return true;
        }
        if (!Objects.equals(getter.invoke(existing), getter.invoke(merged))) {
          return true;
        }
      } catch (Exception ex) {
        // An unknown mapping can still carry a tag change. Persist it rather than discarding it.
        log.warn("customer sync cannot compare mapped field, field={}", field);
        return true;
      }
    }
    return false;
  }

  private boolean needsLeadValiditySynchronization(FieldMappingResult mapping,
      Customer existing, Customer merged) {
    if (mapping == null || merged == null || !mapping.mappedFields().contains("customerStage")) {
      return false;
    }
    String stage = merged.getCustomerStage();
    if (stage == null || stage.isBlank()) {
      return false;
    }
    if (existing == null) {
      return true;
    }
    if (isInvalidLeadStage(stage)) {
      return !existing.isLeadInvalid()
          || existing.getLeadInitialProcessedAt() == null
          || existing.getLeadInitialProcessedBy() == null
          || existing.getLeadRetainedUntil() == null;
    }
    return existing.isLeadInvalid();
  }

  /**
   * The customer stage is the external business input. Keep the workbench's
   * processed/invalid state aligned only when this row actually supplied that
   * field; unrelated Smart Sheet edits must not reset a lead.
   */
  private void synchronizeLeadValidityFromStage(FieldMappingResult mapping,
      Customer merged, Customer existing) {
    if (mapping == null || merged == null || !mapping.mappedFields().contains("customerStage")) {
      return;
    }
    String stage = merged.getCustomerStage();
    if (stage == null || stage.isBlank()) {
      return;
    }
    LocalDateTime now = LocalDateTime.now();
    if (isInvalidLeadStage(stage)) {
      merged.setLeadInvalid(true);
      merged.setLeadInitialProcessedAt(now);
      merged.setLeadInitialProcessedBy("企业微信同步");
      merged.setLeadRetainedUntil(now.plusDays(1));
      return;
    }
    if ((existing != null && existing.isLeadInvalid()) || merged.isLeadInvalid()) {
      merged.setLeadInvalid(false);
      merged.setLeadInitialProcessedAt(null);
      merged.setLeadInitialProcessedBy(null);
      merged.setLeadRetainedUntil(null);
    }
  }

  private boolean isInvalidLeadStage(String stage) {
    String value = stage.trim();
    return "无效线索".equals(value) || "无效".equals(value);
  }
}
