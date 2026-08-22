package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerMasterFieldCatalog;
import com.privateflow.modules.customer.history.CustomerFieldHistoryEntry;
import com.privateflow.modules.customer.history.CustomerFieldHistoryRepository;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Read-only view of the MariaDB customer master, scoped to the signed-in operator. */
@Service
public class CustomerMasterRecordService {

  private static final int SEARCH_LIMIT = 20;
  private final CustomerAdminSearchRepository repository;
  private final CustomerAccessScopeResolver accessScopeResolver;
  private final CustomerFieldHistoryRepository historyRepository;

  @Autowired
  public CustomerMasterRecordService(
      CustomerAdminSearchRepository repository,
      CustomerAccessScopeResolver accessScopeResolver,
      CustomerFieldHistoryRepository historyRepository) {
    this.repository = repository;
    this.accessScopeResolver = accessScopeResolver;
    this.historyRepository = historyRepository;
  }

  public CustomerMasterRecordService(
      CustomerAdminSearchRepository repository,
      CustomerAccessScopeResolver accessScopeResolver) {
    this(repository, accessScopeResolver, null);
  }

  public CustomerMasterRecord defaultRecord() {
    return repository.findLatestInScope(accessScopeResolver.currentScope())
        .map(this::toRecord)
        .orElse(null);
  }

  public List<CustomerMasterCandidate> search(String keyword) {
    String normalized = keyword == null ? "" : keyword.trim();
    if (normalized.isBlank()) {
      return List.of();
    }
    if (normalized.length() > 100) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "搜索关键词不能超过 100 个字符");
    }
    return repository.searchMasterCandidates(normalized, accessScopeResolver.currentScope(), SEARCH_LIMIT)
        .stream()
        .map(this::toCandidate)
        .toList();
  }

  public CustomerMasterRecord record(long customerId) {
    if (customerId <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户编号无效");
    }
    Customer customer = repository.findByIdInScope(customerId, accessScopeResolver.currentScope())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "未找到可查看的客户"));
    return toRecord(customer);
  }

  private CustomerMasterRecord toRecord(Customer customer) {
    List<CustomerMasterFieldValue> fields;
    Map<String, CustomerFieldHistoryEntry> latestByField = historyRepository == null
        ? Map.of() : historyRepository.latestByCustomer(customer.getId());
    try {
      Map<String, PropertyDescriptor> descriptors = java.util.Arrays.stream(
              Introspector.getBeanInfo(Customer.class).getPropertyDescriptors())
          .collect(java.util.stream.Collectors.toMap(PropertyDescriptor::getName, descriptor -> descriptor));
      fields = CustomerMasterFieldCatalog.fields().stream()
          .map(definition -> {
            String fieldName = definition.name();
            PropertyDescriptor descriptor = descriptors.get(fieldName);
            if (descriptor == null || descriptor.getReadMethod() == null) {
              throw new IllegalStateException("missing customer master field: " + fieldName);
            }
            CustomerFieldHistoryEntry latest = latestByField.get(fieldName);
            return new CustomerMasterFieldValue(
                fieldName,
                definition.label(),
                readValue(customer, descriptor.getReadMethod()),
                latest == null ? "" : latest.source(),
                latest == null ? "" : latest.sourceField());
          })
          .toList();
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "无法读取客户主档案字段");
    }
    return new CustomerMasterRecord(toCandidate(customer), fields);
  }

  public List<CustomerFieldHistoryEntry> history(long customerId, String fieldName) {
    if (customerId <= 0 || fieldName == null || fieldName.isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户和字段不能为空");
    }
    repository.findByIdInScope(customerId, accessScopeResolver.currentScope())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "未找到可查看的客户"));
    if (historyRepository == null) {
      return List.of();
    }
    return historyRepository.list(customerId, fieldName);
  }

  private Object readValue(Customer customer, Method getter) {
    try {
      return getter.invoke(customer);
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "无法读取客户主档案字段");
    }
  }

  private CustomerMasterCandidate toCandidate(Customer customer) {
    return new CustomerMasterCandidate(
        customer.getId(), customer.getNickname(), customer.getPhone(), customer.getWechatId(),
        customer.getSourceTable(), customer.getUpdatedAt());
  }

}
