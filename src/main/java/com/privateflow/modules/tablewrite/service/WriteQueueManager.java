package com.privateflow.modules.tablewrite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WriteQueueManager {

  private static final Logger log = LoggerFactory.getLogger(WriteQueueManager.class);
  private final PendingTableWriteRepository repository;
  private final TableConfigProvider configProvider;
  private final ObjectMapper objectMapper;

  public WriteQueueManager(
      PendingTableWriteRepository repository,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
  }

  public void enqueue(String phone, TableWriteActionType actionType, PendingWritePayload payload, String errorMsg) {
    enqueue(null, phone, actionType, payload, errorMsg);
  }

  public void enqueue(Long customerId, String phone, TableWriteActionType actionType, PendingWritePayload payload, String errorMsg) {
    if (actionType == TableWriteActionType.INSERT && blank(phone)) {
      log.warn("skip table write queue item without unique phone, action=INSERT, customerId={}", customerId);
      return;
    }
    if (actionType == TableWriteActionType.UPDATE && cannotResolveUpdate(payload, customerId, phone)) {
      log.warn("skip table write queue item without resolvable source row, customerId={}, phoneLast4={}",
          customerId, last4(phone));
      return;
    }
    int pending = repository.countPending();
    int warnThreshold = configProvider.get().queueWarnThreshold();
    int alertThreshold = configProvider.get().queueAlertThreshold();
    if (pending >= alertThreshold) {
      throw new TableWriteException(TableWriteErrorCodes.TABLE_WRITE_QUEUE_FULL, "table write queue alert threshold reached");
    }
    if (pending >= warnThreshold) {
      log.warn("table write queue pending count {} exceeds warn threshold {}", pending, warnThreshold);
    }
    try {
      repository.enqueue(
          customerId,
          phone,
          actionType,
          objectMapper.writeValueAsString(payload),
          LocalDateTime.now().plusSeconds(configProvider.get().retryIntervalS()),
          errorMsg);
    } catch (JsonProcessingException ex) {
      throw new TableWriteException(TableWriteErrorCodes.BAD_REQUEST, "table write payload cannot be serialized");
    }
  }

  private boolean cannotResolveUpdate(PendingWritePayload payload, Long customerId, String phone) {
    if (payload == null) {
      return true;
    }
    if (!blank(payload.sourceRowId())) {
      return false;
    }
    // Auxiliary projections deliberately upsert by phone and do not have a source row id.
    if ("ASSIGNMENT".equals(payload.sourceTable()) || "ARRIVAL".equals(payload.sourceTable())) {
      return blank(phone);
    }
    return customerId == null && blank(phone);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private String last4(String value) {
    if (blank(value)) return "";
    String normalized = value.trim();
    return normalized.length() <= 4 ? normalized : normalized.substring(normalized.length() - 4);
  }
}
