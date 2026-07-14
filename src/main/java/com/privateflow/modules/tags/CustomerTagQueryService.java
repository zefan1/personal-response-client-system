package com.privateflow.modules.tags;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.service.CustomerAccessService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class CustomerTagQueryService {

  private final CustomerTagFoundationRepository repository;
  private final TagDirectoryService directoryService;
  private final CustomerAccessService accessService;

  public CustomerTagQueryService(
      CustomerTagFoundationRepository repository,
      TagDirectoryService directoryService,
      CustomerAccessService accessService) {
    this.repository = repository;
    this.directoryService = directoryService;
    this.accessService = accessService;
  }

  public List<CustomerTagQueryDto> current(Customer customer) {
    long customerId = requireAccessibleCustomer(customer);
    return map(repository.findCurrentAssignments(customerId), directoryService.getSnapshot());
  }

  public List<CustomerTagQueryDto> history(Customer customer, int limit) {
    long customerId = requireAccessibleCustomer(customer);
    return map(repository.findAssignmentHistory(customerId, limit), directoryService.getSnapshot());
  }

  private long requireAccessibleCustomer(Customer customer) {
    if (customer == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户不能为空");
    }
    if (customer.getId() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户编号不能为空");
    }
    if (!accessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权查看该客户标签");
    }
    return customer.getId();
  }

  private List<CustomerTagQueryDto> map(
      List<CustomerTagAssignment> assignments,
      TagDirectorySnapshot snapshot) {
    return assignments.stream()
        .map(assignment -> map(assignment, snapshot))
        .toList();
  }

  private CustomerTagQueryDto map(
      CustomerTagAssignment assignment,
      TagDirectorySnapshot snapshot) {
    TagCategory category = snapshot.categoriesById().get(assignment.categoryId());
    if (category == null) {
      throw new IllegalStateException("标签目录缺少分类：" + assignment.categoryId());
    }
    TagValue value = snapshot.valuesById().get(assignment.tagValueId());
    if (value == null || value.categoryId() != category.id()) {
      throw new IllegalStateException("标签目录缺少标签值：" + assignment.tagValueId());
    }
    return new CustomerTagQueryDto(
        assignment.id(),
        assignment.customerId(),
        assignment.customerVersion(),
        category.id(),
        category.categoryKey(),
        category.categoryName(),
        category.selectionMode(),
        category.isEnabled(),
        category.mergedIntoId(),
        category.version(),
        value.id(),
        value.tagValue(),
        value.displayName(),
        value.isEnabled(),
        value.mergedIntoId(),
        value.version(),
        assignment.selectionMode(),
        assignment.active(),
        assignment.sourceType(),
        assignment.confidence(),
        assignment.evidenceText(),
        assignment.evidenceMessageCount(),
        assignment.analysisResultId(),
        assignment.skillId(),
        assignment.llmEnvironment(),
        assignment.llmModel(),
        assignment.promptVersion(),
        assignment.operatorAccount(),
        assignment.manualLocked(),
        assignment.lockedBy(),
        assignment.lockedAt(),
        assignment.supersedesAssignmentId(),
        assignment.invalidatedReason(),
        assignment.invalidatedAt(),
        assignment.createdAt(),
        assignment.updatedAt());
  }
}
