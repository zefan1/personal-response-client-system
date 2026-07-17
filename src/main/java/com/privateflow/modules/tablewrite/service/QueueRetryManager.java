package com.privateflow.modules.tablewrite.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QueueRetryManager {

  private static final Logger log = LoggerFactory.getLogger(QueueRetryManager.class);
  private final PendingTableWriteRepository repository;
  private final WecomTableClient tableClient;
  private final TableConfigProvider configProvider;
  private final ObjectMapper objectMapper;
  private final CustomerQueryService customerQueryService;
  private final NewCustomerRowCreator newCustomerRowCreator;
  private final TableFieldMappingResolver mappingResolver;
  private final TagExchangeService exchangeService;

  @Autowired
  public QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator,
      TableFieldMappingResolver mappingResolver,
      TagExchangeService exchangeService) {
    this.repository = repository;
    this.tableClient = tableClient;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
    this.customerQueryService = customerQueryService;
    this.newCustomerRowCreator = newCustomerRowCreator;
    this.mappingResolver = mappingResolver;
    this.exchangeService = exchangeService;
  }

  public QueueRetryManager(
      PendingTableWriteRepository repository,
      WecomTableClient tableClient,
      TableConfigProvider configProvider,
      ObjectMapper objectMapper,
      CustomerQueryService customerQueryService,
      NewCustomerRowCreator newCustomerRowCreator) {
    this(repository, tableClient, configProvider, objectMapper, customerQueryService, newCustomerRowCreator, null, null);
  }

  @Scheduled(fixedDelayString = "#{@tableConfigProvider.get().retryIntervalS() * 1000L}")
  public void retryDueWrites() {
    TableConfig config = configProvider.get();
    for (PendingTableWrite item : repository.due(100)) {
      try {
        PendingWritePayload payload = objectMapper.readValue(item.getPayload(), PendingWritePayload.class);
        TagExchangeResult exchange = exchangeService == null
            ? new TagExchangeResult(payload.fields(), java.util.List.of(), java.util.List.of())
            : exchangeService.prepareOutbound(
                TagExchangeSourceType.TABLE_WRITE,
                String.valueOf(item.getId()),
                payload.fields());
        if (exchange.acceptedFields().isEmpty()) {
          repository.markResolved(item.getId());
          continue;
        }
        if (item.getActionType() == TableWriteActionType.INSERT) {
          MapPayload remote = remotePayload(payload, exchange);
          if (remote.fields().isEmpty()) {
            repository.markResolved(item.getId());
            continue;
          }
          String rowId = tableClient.createRow(remote.sourceTable(), remote.fields(), Duration.ofMillis(config.writeTimeoutMs()));
          newCustomerRowCreator.insertCustomerAfterQueuedCreate(item.getPhone(), remote.sourceTable(), rowId, exchange.acceptedFields());
        } else {
          PendingWritePayload resolved = resolveExistingRow(item.getPhone(), payload);
          MapPayload remote = remotePayload(resolved, exchange);
          if (remote.fields().isEmpty()) {
            repository.markResolved(item.getId());
            continue;
          }
          tableClient.updateRow(remote.sourceTable(), remote.sourceRowId(), remote.fields(), Duration.ofMillis(config.writeTimeoutMs()));
        }
        repository.markResolved(item.getId());
      } catch (Exception ex) {
        int nextRetry = item.getRetryCount() + 1;
        if (nextRetry >= config.retryMaxCount()) {
          repository.markFailed(item.getId(), nextRetry, ex.getMessage());
        } else {
          repository.markRetry(item.getId(), nextRetry, LocalDateTime.now().plusSeconds(config.retryIntervalS()), ex.getMessage());
        }
      }
    }
    int staleFailed = repository.countStaleFailed(config.alertFailureHours());
    if (staleFailed > 0) {
      log.warn("table write has {} stale FAILED records, notify target {}", staleFailed, config.alertNotifyTarget());
    }
  }

  private PendingWritePayload resolveExistingRow(String phone, PendingWritePayload payload) {
    if (!blank(payload.sourceTable()) && !blank(payload.sourceRowId())) {
      return payload;
    }
    Customer customer = customerQueryService.getByPhone(phone);
    if (customer == null || blank(customer.getSourceTable()) || blank(customer.getSourceRowId())) {
      throw new IllegalStateException("customer source table or row id is still missing");
    }
    return new PendingWritePayload(customer.getSourceTable(), customer.getSourceRowId(), payload.fields());
  }

  private MapPayload remotePayload(PendingWritePayload payload, TagExchangeResult exchange) {
    if (mappingResolver == null) {
      return new MapPayload(payload.sourceTable(), payload.sourceRowId(), exchange.acceptedFields());
    }
    return new MapPayload(
        payload.sourceTable(),
        payload.sourceRowId(),
        mappingResolver.toSourceFields(payload.sourceTable(), exchange.acceptedFields()));
  }

  private record MapPayload(String sourceTable, String sourceRowId, java.util.Map<String, Object> fields) {
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
