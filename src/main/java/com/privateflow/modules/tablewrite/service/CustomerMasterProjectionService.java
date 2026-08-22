package com.privateflow.modules.tablewrite.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Projects assignment intake into the configured customer-master Smart Sheet by phone. */
@Service
public class CustomerMasterProjectionService {

  private static final Logger log = LoggerFactory.getLogger(CustomerMasterProjectionService.class);
  private static final String INVALID_FIELD_PREFIX = "Invalid value for field: ";

  private final WecomTableClient tableClient;
  private final WecomSmartSheetConfig smartSheetConfig;
  private final TableConfigProvider tableConfigProvider;
  private final TableFieldMappingResolver mappingResolver;

  public CustomerMasterProjectionService(
      WecomTableClient tableClient,
      WecomSmartSheetConfig smartSheetConfig,
      TableConfigProvider tableConfigProvider,
      TableFieldMappingResolver mappingResolver) {
    this.tableClient = tableClient;
    this.smartSheetConfig = smartSheetConfig;
    this.tableConfigProvider = tableConfigProvider;
    this.mappingResolver = mappingResolver;
  }

  public void projectAssignment(Customer customer) {
    if (customer == null || blank(customer.getPhone())) {
      return;
    }
    projectFields(customer, fields(customer));
  }

  /** Upserts only the committed fields into the unique customer-master Smart Sheet. */
  public void projectFields(Customer customer, Map<String, Object> fields) {
    if (customer == null || blank(customer.getPhone()) || fields == null || fields.isEmpty()) {
      return;
    }
    String sourceTable = smartSheetConfig.sourceTable();
    Map<String, Object> sourceFields = mappingResolver.toSourceFields(sourceTable, fields);
    if (sourceFields.isEmpty()) {
      return;
    }
    Duration timeout = Duration.ofMillis(tableConfigProvider.get().writeTimeoutMs());
    String identityField = mappingResolver.sourceFieldFor(sourceTable, "phone");
    while (true) {
      try {
        String rowId = tableClient.createRow(sourceTable, sourceFields, timeout);
        tableClient.updateRow(sourceTable, rowId, sourceFields, timeout);
        return;
      } catch (IllegalArgumentException failure) {
        String invalidField = invalidField(failure.getMessage());
        if (invalidField == null || invalidField.equals(identityField)
            || sourceFields.remove(invalidField) == null) {
          throw failure;
        }
        log.warn("skip incompatible customer-master projection field, field={}", invalidField);
      }
    }
  }

  private static String invalidField(String message) {
    if (message == null || !message.startsWith(INVALID_FIELD_PREFIX)) {
      return null;
    }
    String field = message.substring(INVALID_FIELD_PREFIX.length()).trim();
    return field.isEmpty() ? null : field;
  }

  private static Map<String, Object> fields(Customer customer) {
    Map<String, Object> fields = new LinkedHashMap<>();
    put(fields, "phone", customer.getPhone());
    put(fields, "nickname", customer.getNickname());
    put(fields, "leadType", customer.getLeadType());
    put(fields, "assignedKeeper", customer.getAssignedKeeper());
    put(fields, "assignedAt", customer.getAssignedAt());
    put(fields, "intendedStore", customer.getIntendedStore());
    put(fields, "intendedProject", customer.getIntendedProject());
    put(fields, "customerStage", customer.getCustomerStage());
    put(fields, "followupNotes", customer.getFollowupNotes());
    put(fields, "nextFollowupAt", customer.getNextFollowupAt());
    put(fields, "nextFollowupDir", customer.getNextFollowupDir());
    return fields;
  }

  private static void put(Map<String, Object> fields, String name, Object value) {
    if (value != null && (!(value instanceof String text) || !text.isBlank())) {
      fields.put(name, value);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
