package com.privateflow.modules.tags;

import com.privateflow.common.events.CustomerTagsUpdatedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class CustomerTagUpdateService {

  private final CustomerRepository customerRepository;
  private final CustomerAccessService accessService;
  private final TagDirectoryService directoryService;
  private final TagSelectionValidator selectionValidator;
  private final CustomerTagUpdateRepository updateRepository;
  private final Clock clock;
  private final ApplicationEventPublisher eventPublisher;

  @Autowired
  public CustomerTagUpdateService(
      CustomerRepository customerRepository,
      CustomerAccessService accessService,
      TagDirectoryService directoryService,
      TagSelectionValidator selectionValidator,
      CustomerTagUpdateRepository updateRepository,
      ApplicationEventPublisher eventPublisher) {
    this(
        customerRepository,
        accessService,
        directoryService,
        selectionValidator,
        updateRepository,
        Clock.systemDefaultZone(),
        eventPublisher);
  }

  CustomerTagUpdateService(
      CustomerRepository customerRepository,
      CustomerAccessService accessService,
      TagDirectoryService directoryService,
        TagSelectionValidator selectionValidator,
        CustomerTagUpdateRepository updateRepository,
        Clock clock) {
    this(
        customerRepository,
        accessService,
        directoryService,
        selectionValidator,
        updateRepository,
        clock,
        event -> { });
  }

  CustomerTagUpdateService(
      CustomerRepository customerRepository,
      CustomerAccessService accessService,
      TagDirectoryService directoryService,
      TagSelectionValidator selectionValidator,
      CustomerTagUpdateRepository updateRepository,
      Clock clock,
      ApplicationEventPublisher eventPublisher) {
    this.customerRepository = customerRepository;
    this.accessService = accessService;
    this.directoryService = directoryService;
    this.selectionValidator = selectionValidator;
    this.updateRepository = updateRepository;
    this.clock = clock;
    this.eventPublisher = eventPublisher;
  }

  public CustomerTagUpdateResult applyAutomatic(AutomaticCustomerTagUpdateRequest request) {
    if (request == null || request.customerId() <= 0) {
      return rejected(0, request, "客户不存在");
    }
    Customer customer = customerRepository.findById(request.customerId()).orElse(null);
    if (customer == null) {
      return rejected(0, request, "客户不存在");
    }
    int currentVersion = customer.getVersion() == null ? 0 : customer.getVersion();
    if (!accessService.canAccess(customer)) {
      return rejected(currentVersion, request, "当前账号或系统任务无权处理该客户");
    }

    TagDirectorySnapshot snapshot = directoryService.getSnapshot();
    List<AutomaticCustomerTagDecisionPlan> plans = new ArrayList<>();
    for (TagAnalysisDecision decision : request.decisions()) {
      plans.add(evaluateDecision(request, snapshot, decision));
    }

    Customer latest = customerRepository.findById(request.customerId()).orElse(null);
    int latestVersion = latest == null || latest.getVersion() == null ? -1 : latest.getVersion();
    if (latestVersion != request.expectedCustomerVersion()) {
      plans = plans.stream().map(plan -> plan.accepted()
          ? rejectedPlan(plan, "客户数据版本已被其他操作更新")
          : plan).toList();
    }

    AutomaticCustomerTagUpdatePlan plan = new AutomaticCustomerTagUpdatePlan(
        UUID.randomUUID().toString(),
        request.customerId(),
        request.phone(),
        request.expectedCustomerVersion(),
        request.effectiveMessageCount(),
        request.operator(),
        LocalDateTime.now(clock),
        plans);
    CustomerTagUpdateResult result = updateRepository.applyAutomatic(plan);
    publishIfUpdated(request.phone(), request.customerId(), result, "SYSTEM");
    return result;
  }

  public CustomerTagUpdateResult applyManual(
      String phone,
      long categoryId,
      ManualCustomerTagUpdateRequest request) {
    if (request == null || request.version() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户版本不能为空");
    }
    Customer customer = customerRepository.findByPhone(phone)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "客户不存在"));
    if (!accessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权修改该客户标签");
    }
    if (customer.getVersion() == null || !request.version().equals(customer.getVersion())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "客户数据版本已被其他操作更新");
    }
    TagCategory category = directoryService.getSnapshot().categoriesById().get(categoryId);
    if (category == null || !category.isEnabled() || category.mergedIntoId() != null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签分类不存在或已停用");
    }
    if (!category.manualEditEnabled()) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "标签分类不允许员工手工修改");
    }
    if (new HashSet<>(request.tagValueIds()).size() != request.tagValueIds().size()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签值不能重复");
    }
    List<TagValue> desiredValues = List.of();
    if (!request.tagValueIds().isEmpty()) {
      TagSelectionValidationResult validation = selectionValidator.validateIds(
          TagCandidatePurpose.MANUAL_ASSIGNMENT,
          categoryId,
          request.tagValueIds(),
          TagSelectionContext.empty());
      if (!validation.accepted()) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, validation.message());
      }
      desiredValues = validation.values();
    }
    List<CustomerTagAssignment> previous = updateRepository.findActiveAssignments(
        customer.getId(), categoryId);
    ManualCustomerTagUpdatePlan plan = new ManualCustomerTagUpdatePlan(
        customer.getId(),
        customer.getPhone(),
        request.version(),
        category,
        desiredValues,
        previous,
        AuthContext.username(),
        request.reason(),
        true,
        LocalDateTime.now(clock));
    CustomerTagUpdateResult result = updateRepository.applyManual(plan);
    throwIfVersionConflict(result);
    publishIfUpdated(phone, customer.getId(), result, "MANUAL");
    return result;
  }

  public CustomerTagUpdateResult applyLock(
      String phone,
      long categoryId,
      CustomerTagLockUpdateRequest request) {
    if (request == null || request.version() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "客户版本不能为空");
    }
    Customer customer = customerRepository.findByPhone(phone)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "客户不存在"));
    if (!accessService.canAccess(customer)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "无权修改该客户标签");
    }
    if (customer.getVersion() == null || !request.version().equals(customer.getVersion())) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "客户数据版本已被其他操作更新");
    }
    TagCategory category = directoryService.getSnapshot().categoriesById().get(categoryId);
    if (category == null || !category.isEnabled() || category.mergedIntoId() != null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签分类不存在或已停用");
    }
    if (!category.manualEditEnabled()) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "标签分类不允许员工手工修改");
    }
    CustomerTagLockUpdatePlan plan = new CustomerTagLockUpdatePlan(
        customer.getId(),
        customer.getPhone(),
        request.version(),
        category,
        request.locked(),
        AuthContext.username(),
        request.reason(),
        LocalDateTime.now(clock));
    CustomerTagUpdateResult result = updateRepository.applyLock(plan);
    throwIfVersionConflict(result);
    publishIfUpdated(phone, customer.getId(), result, request.locked() ? "MANUAL_LOCK" : "MANUAL_UNLOCK");
    return result;
  }

  private void publishIfUpdated(
      String phone,
      long customerId,
      CustomerTagUpdateResult result,
      String source) {
    if (result == null || !result.updated()) {
      return;
    }
    eventPublisher.publishEvent(new CustomerTagsUpdatedEvent(
        phone,
        customerId,
        result.customerVersion(),
        source,
        result));
  }

  private void throwIfVersionConflict(CustomerTagUpdateResult result) {
    if (result != null
        && !result.updated()
        && result.decisions().stream()
            .anyMatch(decision -> decision.reason() != null && decision.reason().contains("版本"))) {
      throw new ApiException(ApiErrorCodes.CONFLICT, "客户数据版本已被其他操作更新");
    }
  }

  private AutomaticCustomerTagDecisionPlan evaluateDecision(
      AutomaticCustomerTagUpdateRequest request,
      TagDirectorySnapshot snapshot,
      TagAnalysisDecision decision) {
    if (decision == null || decision.categoryCode() == null) {
      return rejectedPlan(null, decision, "标签分类不存在");
    }
    TagCategory category = snapshot.categoriesByKey().get(decision.categoryCode());
    if (category == null) {
      return rejectedPlan(null, decision, "标签分类不存在");
    }
    if (!category.isEnabled() || category.mergedIntoId() != null) {
      return rejectedPlan(category, decision, "标签分类不存在或已停用");
    }
    if (!category.systemInferenceEnabled()
        || category.autoUpdateMode() == TagAutoUpdateMode.RECORD_ONLY) {
      return rejectedPlan(category, decision, "标签分类不允许系统自动更新");
    }
    TagAnalysisAction expectedAction = category.autoUpdateMode() == TagAutoUpdateMode.ADD_ONLY
        ? TagAnalysisAction.ADD
        : TagAnalysisAction.REPLACE;
    if (decision.resultType() != TagAnalysisResultType.UPDATE
        || decision.requestedAction() != expectedAction) {
      return rejectedPlan(category, decision, "标签更新动作与分类设置不一致");
    }
    TagSelectionValidationResult validation = selectionValidator.validateCodes(
        TagCandidatePurpose.SYSTEM_INFERENCE,
        decision.categoryCode(),
        decision.tagCodes(),
        new TagSelectionContext(
            decision.evidence(),
            request.effectiveMessageCount(),
            decision.confidence(),
            null));
    if (!validation.accepted()) {
      return rejectedPlan(category, decision, validation.message());
    }
    List<CustomerTagAssignment> previous = updateRepository.findActiveAssignments(
        request.customerId(), category.id());
    Optional<CustomerTagCategoryLock> lock = updateRepository.findCategoryLock(
        request.customerId(), category.id());
    if (lock.filter(CustomerTagCategoryLock::locked).isPresent()) {
      return rejectedPlan(category, decision, previous, validation.values(), "标签分类已被员工锁定");
    }
    Optional<LocalDateTime> lastAutomaticUpdate = updateRepository.findLastAutomaticUpdateAt(
        request.customerId(), category.id());
    if (category.cooldownHours() > 0
        && lastAutomaticUpdate
            .map(last -> last.plusHours(category.cooldownHours()).isAfter(LocalDateTime.now(clock)))
            .orElse(false)) {
      return rejectedPlan(category, decision, previous, validation.values(), "标签分类仍在自动更新冷却期内");
    }
    return new AutomaticCustomerTagDecisionPlan(
        category,
        validation.values(),
        previous,
        decision.requestedAction(),
        true,
        "自动标签更新校验通过",
        decision);
  }

  private AutomaticCustomerTagDecisionPlan rejectedPlan(
      AutomaticCustomerTagDecisionPlan plan,
      String reason) {
    return new AutomaticCustomerTagDecisionPlan(
        plan.category(),
        plan.values(),
        plan.previousAssignments(),
        plan.action(),
        false,
        reason,
        plan.analysisDecision());
  }

  private AutomaticCustomerTagDecisionPlan rejectedPlan(
      TagCategory category,
      TagAnalysisDecision decision,
      String reason) {
    return rejectedPlan(category, decision, List.of(), List.of(), reason);
  }

  private AutomaticCustomerTagDecisionPlan rejectedPlan(
      TagCategory category,
      TagAnalysisDecision decision,
      List<CustomerTagAssignment> previous,
      List<TagValue> values,
      String reason) {
    return new AutomaticCustomerTagDecisionPlan(
        category,
        values,
        previous,
        decision == null || decision.requestedAction() == null
            ? TagAnalysisAction.NONE
            : decision.requestedAction(),
        false,
        reason,
        decision);
  }

  private CustomerTagUpdateResult rejected(
      int customerVersion,
      AutomaticCustomerTagUpdateRequest request,
      String reason) {
    List<TagAnalysisDecision> decisions = request == null ? List.of() : request.decisions();
    List<CustomerTagDecisionResult> results = decisions.isEmpty()
        ? List.of(new CustomerTagDecisionResult(0, "", TagAnalysisAction.NONE.name(), false, reason))
        : decisions.stream().map(decision -> new CustomerTagDecisionResult(
            0,
            decision == null || decision.categoryCode() == null ? "" : decision.categoryCode(),
            decision == null || decision.requestedAction() == null
                ? TagAnalysisAction.NONE.name()
                : decision.requestedAction().name(),
            false,
            reason)).toList();
    return new CustomerTagUpdateResult(customerVersion, false, results);
  }
}
