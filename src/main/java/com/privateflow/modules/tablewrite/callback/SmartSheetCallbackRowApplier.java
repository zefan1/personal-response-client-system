package com.privateflow.modules.tablewrite.callback;

import com.privateflow.modules.api.auth.AccountRepository;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.customer.history.CustomerFieldHistoryService;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerMergeEngine;
import com.privateflow.modules.customer.sync.FieldMappingResolver;
import com.privateflow.modules.customer.sync.SheetRow;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SmartSheetCallbackRowApplier {

  private static final Set<String> PROTECTED_FIELDS = Set.of(
      "id", "phone", "sourceTable", "sourceRowId", "syncedAt", "version", "createdAt", "updatedAt");

  private final FieldMappingResolver mappingResolver;
  private final CustomerRepository customerRepository;
  private final CustomerMergeEngine mergeEngine;
  private final CustomerFieldHistoryService historyService;
  private final AccountRepository accountRepository;

  SmartSheetCallbackRowApplier(
      FieldMappingResolver mappingResolver,
      CustomerRepository customerRepository,
      CustomerMergeEngine mergeEngine,
      CustomerFieldHistoryService historyService,
      AccountRepository accountRepository) {
    this.mappingResolver = mappingResolver;
    this.customerRepository = customerRepository;
    this.mergeEngine = mergeEngine;
    this.historyService = historyService;
    this.accountRepository = accountRepository;
  }

  @Transactional
  void apply(SmartSheetCallbackTargetResolver.ResolvedTarget target, SheetRow row, String operator) {
    Map<String, String> values = new HashMap<>(mappingResolver.mappedRawValues(target.sourceTable(), row));
    String rawKeeper = values.get("assignedKeeper");
    if (rawKeeper != null && !rawKeeper.isBlank()) {
      values.put("assignedKeeper", accountRepository.resolveEnabledDisplayName(rawKeeper).orElse(rawKeeper.trim()));
    }
    String phone = values.get("phone");
    if (phone == null || !phone.matches("\\d{11}")) {
      throw new IllegalArgumentException("callback row has no valid phone identifier");
    }
    Customer existing = customerRepository.findByPhone(phone).orElse(null);
    if (existing == null && !"ASSIGNMENT".equals(target.role())) {
      throw new IllegalArgumentException("callback row does not match an existing customer");
    }
    CustomerFieldHistoryService.CustomerSnapshot before = historyService.snapshotByPhone(phone);
    Customer working = existing == null ? new Customer() : mergeEngine.copyOf(existing);
    if (existing == null) {
      working.setPhone(phone);
      working.setSourceTable(target.sourceTable());
      working.setSourceRowId(row.rowId());
    }
    Set<String> changed = mappingResolver.applyMappedValues(working, values, PROTECTED_FIELDS);
    if (existing != null && changed.isEmpty()) {
      return;
    }
    working.setSyncedAt(LocalDateTime.now());
    customerRepository.upsert(working);
    historyService.recordProfileWrite(
        before,
        phone,
        changed,
        CustomerFieldHistoryContext.external(
            "WECOM_SMART_SHEET_" + target.role(),
            mappingResolver.sourceFieldsFor(target.sourceTable()),
            operator));
  }
}
