package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CustomerTagUpdateServiceTest {

  @Test
  void rejectsInaccessibleCustomerBeforeLoadingTagDirectory() {
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    TagSelectionValidator selectionValidator = mock(TagSelectionValidator.class);
    CustomerTagUpdateRepository updateRepository = mock(CustomerTagUpdateRepository.class);
    Customer customer = customer(7L, 3);
    when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
    when(accessService.canAccess(customer)).thenReturn(false);
    CustomerTagUpdateService service = service(
        customerRepository, accessService, directoryService, selectionValidator, updateRepository);

    CustomerTagUpdateResult result = service.applyAutomatic(request(7L, 3));

    assertThat(result.updated()).isFalse();
    assertThat(result.decisions()).singleElement()
        .extracting(CustomerTagDecisionResult::reason)
        .isEqualTo("当前账号或系统任务无权处理该客户");
    verifyNoInteractions(directoryService, selectionValidator, updateRepository);
  }

  @Test
  void buildsSingleReplacePlanOnlyAfterPolicyLockCooldownAndVersionChecksPass() {
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    CustomerTagUpdateRepository updateRepository = mock(CustomerTagUpdateRepository.class);
    Customer customer = customer(7L, 3);
    TagValue current = value(11L, "LOW");
    TagValue requested = value(12L, "HIGH");
    TagCategory category = category(List.of(current, requested));
    TagDirectorySnapshot snapshot = TagDirectorySnapshot.from(
        List.of(category), Instant.parse("2026-07-15T02:00:00Z"));
    when(customerRepository.findById(7L)).thenReturn(Optional.of(customer));
    when(accessService.canAccess(customer)).thenReturn(true);
    when(directoryService.getSnapshot()).thenReturn(snapshot);
    TagSelectionValidator selectionValidator = new TagSelectionValidator(
        directoryService, new TagCandidateBuilder(directoryService));
    when(updateRepository.findActiveAssignments(7L, 1L)).thenReturn(List.of(assignment(21L, current)));
    when(updateRepository.findCategoryLock(7L, 1L)).thenReturn(Optional.empty());
    when(updateRepository.findLastAutomaticUpdateAt(7L, 1L)).thenReturn(Optional.empty());
    when(updateRepository.applyAutomatic(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new CustomerTagUpdateResult(
            4,
            true,
            List.of(new CustomerTagDecisionResult(1L, "intent_level", "REPLACE", true, "自动替换完成"))));
    CustomerTagUpdateService service = service(
        customerRepository, accessService, directoryService, selectionValidator, updateRepository);

    CustomerTagUpdateResult result = service.applyAutomatic(request(7L, 3));

    assertThat(result.updated()).isTrue();
    ArgumentCaptor<AutomaticCustomerTagUpdatePlan> captor =
        ArgumentCaptor.forClass(AutomaticCustomerTagUpdatePlan.class);
    org.mockito.Mockito.verify(updateRepository).applyAutomatic(captor.capture());
    AutomaticCustomerTagUpdatePlan plan = captor.getValue();
    assertThat(plan.expectedCustomerVersion()).isEqualTo(3);
    assertThat(plan.decisions()).singleElement().satisfies(decision -> {
      assertThat(decision.category()).isEqualTo(category);
      assertThat(decision.values()).containsExactly(requested);
      assertThat(decision.previousAssignments()).extracting(CustomerTagAssignment::tagValueId)
          .containsExactly(current.id());
      assertThat(decision.action()).isEqualTo(TagAnalysisAction.REPLACE);
      assertThat(decision.accepted()).isTrue();
    });
  }

  @Test
  void manualReplacementUsesAuthenticatedOperatorAndLocksCategoryByDefault() {
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    CustomerTagUpdateRepository updateRepository = mock(CustomerTagUpdateRepository.class);
    Customer customer = customer(7L, 3);
    TagValue current = value(11L, "LOW");
    TagValue requested = value(12L, "HIGH");
    TagCategory category = category(List.of(current, requested));
    TagDirectorySnapshot snapshot = TagDirectorySnapshot.from(
        List.of(category), Instant.parse("2026-07-15T02:00:00Z"));
    when(customerRepository.findByPhone("18800001111")).thenReturn(Optional.of(customer));
    when(accessService.canAccess(customer)).thenReturn(true);
    when(directoryService.getSnapshot()).thenReturn(snapshot);
    TagSelectionValidator selectionValidator = new TagSelectionValidator(
        directoryService, new TagCandidateBuilder(directoryService));
    when(updateRepository.findActiveAssignments(7L, 1L)).thenReturn(List.of(assignment(21L, current)));
    when(updateRepository.applyManual(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new CustomerTagUpdateResult(
            4,
            true,
            List.of(new CustomerTagDecisionResult(1L, "intent_level", "REPLACE", true, "人工标签修改完成"))));
    CustomerTagUpdateService service = service(
        customerRepository, accessService, directoryService, selectionValidator, updateRepository);
    AuthContext.set(new AuthUser("keeper-auth", "管家", Role.KEEPER, null));
    try {
      CustomerTagUpdateResult result = service.applyManual(
          "18800001111",
          1L,
          new ManualCustomerTagUpdateRequest(3, List.of(12L), "客户明确确认购买"));

      assertThat(result.updated()).isTrue();
      ArgumentCaptor<ManualCustomerTagUpdatePlan> captor =
          ArgumentCaptor.forClass(ManualCustomerTagUpdatePlan.class);
      org.mockito.Mockito.verify(updateRepository).applyManual(captor.capture());
      ManualCustomerTagUpdatePlan plan = captor.getValue();
      assertThat(plan.operator()).isEqualTo("keeper-auth");
      assertThat(plan.lockAfterUpdate()).isTrue();
      assertThat(plan.desiredValues()).containsExactly(requested);
      assertThat(plan.previousAssignments()).extracting(CustomerTagAssignment::tagValueId)
          .containsExactly(current.id());
      assertThat(plan.reason()).isEqualTo("客户明确确认购买");
    } finally {
      AuthContext.clear();
    }
  }

  @Test
  void unlockUsesAuthenticatedOperatorAndOptimisticCustomerVersion() {
    CustomerRepository customerRepository = mock(CustomerRepository.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    CustomerTagUpdateRepository updateRepository = mock(CustomerTagUpdateRepository.class);
    Customer customer = customer(7L, 3);
    TagCategory category = category(List.of(value(11L, "LOW"), value(12L, "HIGH")));
    when(customerRepository.findByPhone("18800001111")).thenReturn(Optional.of(customer));
    when(accessService.canAccess(customer)).thenReturn(true);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(category), Instant.parse("2026-07-15T02:00:00Z")));
    when(updateRepository.applyLock(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new CustomerTagUpdateResult(
            4,
            true,
            List.of(new CustomerTagDecisionResult(1L, "intent_level", "UNLOCK", true, "分类已解除锁定"))));
    CustomerTagUpdateService service = service(
        customerRepository,
        accessService,
        directoryService,
        new TagSelectionValidator(directoryService, new TagCandidateBuilder(directoryService)),
        updateRepository);
    AuthContext.set(new AuthUser("leader-auth", "组长", Role.LEADER, 10L));
    try {
      CustomerTagUpdateResult result = service.applyLock(
          "18800001111",
          1L,
          new CustomerTagLockUpdateRequest(3, false, "客户主动要求重新判断"));

      assertThat(result.updated()).isTrue();
      ArgumentCaptor<CustomerTagLockUpdatePlan> captor =
          ArgumentCaptor.forClass(CustomerTagLockUpdatePlan.class);
      org.mockito.Mockito.verify(updateRepository).applyLock(captor.capture());
      assertThat(captor.getValue().operator()).isEqualTo("leader-auth");
      assertThat(captor.getValue().locked()).isFalse();
      assertThat(captor.getValue().expectedCustomerVersion()).isEqualTo(3);
      assertThat(captor.getValue().reason()).isEqualTo("客户主动要求重新判断");
    } finally {
      AuthContext.clear();
    }
  }

  private CustomerTagUpdateService service(
      CustomerRepository customerRepository,
      CustomerAccessService accessService,
      TagDirectoryService directoryService,
      TagSelectionValidator selectionValidator,
      CustomerTagUpdateRepository updateRepository) {
    return new CustomerTagUpdateService(
        customerRepository,
        accessService,
        directoryService,
        selectionValidator,
        updateRepository,
        Clock.fixed(Instant.parse("2026-07-15T02:00:00Z"), ZoneOffset.UTC));
  }

  private AutomaticCustomerTagUpdateRequest request(long customerId, int expectedVersion) {
    return new AutomaticCustomerTagUpdateRequest(
        customerId,
        "18800001111",
        expectedVersion,
        5,
        "keeper-1",
        List.of(new TagAnalysisDecision(
            "intent_level",
            List.of("HIGH"),
            new BigDecimal("0.9200"),
            "客户明确表示本周到店并询问付款方式",
            TagAnalysisResultType.UPDATE,
            TagAnalysisAction.REPLACE)));
  }

  private Customer customer(long id, int version) {
    Customer customer = new Customer();
    customer.setId(id);
    customer.setPhone("18800001111");
    customer.setAssignedKeeper("keeper-1");
    customer.setVersion(version);
    return customer;
  }

  private TagCategory category(List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new TagCategory(
        1L,
        "intent_level",
        "意向度",
        "识别客户购买意向",
        "intentLevel",
        TagSelectionMode.SINGLE,
        true,
        true,
        TagAutoUpdateMode.REPLACE,
        new BigDecimal("0.8500"),
        3,
        24,
        TagUncertainPolicy.KEEP_CURRENT,
        true,
        true,
        true,
        true,
        true,
        true,
        1,
        null,
        0,
        values,
        TagImpact.empty(),
        now,
        now);
  }

  private TagValue value(long id, String code) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new TagValue(
        id,
        1L,
        "intent_level",
        code,
        code,
        "",
        "",
        "",
        "",
        "",
        List.of(),
        true,
        true,
        true,
        (int) id,
        null,
        0,
        TagImpact.empty(),
        now,
        now);
  }

  private CustomerTagAssignment assignment(long id, TagValue value) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 9, 0);
    return new CustomerTagAssignment(
        id,
        7L,
        1L,
        value.id(),
        TagSelectionMode.SINGLE,
        true,
        "SYSTEM_INFERENCE",
        new BigDecimal("0.9000"),
        "旧证据",
        4,
        null,
        null,
        null,
        null,
        null,
        "SYSTEM",
        false,
        null,
        null,
        null,
        2,
        null,
        null,
        now,
        now,
        value.id(),
        1L);
  }
}
