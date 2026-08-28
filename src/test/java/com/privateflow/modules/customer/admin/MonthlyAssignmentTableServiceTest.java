package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetProvisioningService;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MonthlyAssignmentTableServiceTest {

  private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
  private static final AuxiliarySmartSheetTarget CURRENT = new AuxiliarySmartSheetTarget(
      "ASSIGNMENT", "old-doc", "old-sheet", "old-view", "联系方式", "https://doc.weixin.qq.com/old");
  private static final WecomSmartSheetProvisioningService.ProvisionedSheet CREATED =
      new WecomSmartSheetProvisioningService.ProvisionedSheet(
          "new-doc", "https://doc.weixin.qq.com/new", "new-sheet", "new-view", "new-sheet", "联系方式");

  @Test
  void marksTheProductionConstructorForSpringInjection() throws Exception {
    assertThat(MonthlyAssignmentTableService.class.getConstructor(
        MonthlyAssignmentTableRepository.class,
        AuxiliarySmartSheetTargets.class,
        WecomSmartSheetProvisioningService.class,
        MonthlyAssignmentTableActivationService.class,
        AuditLogger.class).isAnnotationPresent(Autowired.class)).isTrue();
  }

  @Test
  void rejectsDuplicateNameBeforeProvisioning() {
    Fixture fixture = new Fixture();
    when(fixture.repository.findByName("9月新客分配")).thenReturn(Optional.of(table(3L, "ACTIVE")));

    assertThatThrownBy(() -> fixture.service.create(new MonthlyAssignmentTableCreateRequest(" 9月新客分配 ")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("名称已经存在");

    verify(fixture.provisioningService, never()).provisionFromTemplate(anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void activatesOnlyAfterTemplateProvisioningAndReadBackSucceeded() {
    Fixture fixture = new Fixture();
    when(fixture.repository.findByName("9月新客分配")).thenReturn(Optional.empty());
    when(fixture.repository.createPending("9月新客分配", "2026-09", "SYSTEM")).thenReturn(8L);
    when(fixture.targets.assignment()).thenReturn(Optional.of(CURRENT));
    when(fixture.provisioningService.provisionFromTemplate(
        org.mockito.ArgumentMatchers.eq("9月新客分配"), org.mockito.ArgumentMatchers.eq(CURRENT),
        org.mockito.ArgumentMatchers.any())).thenReturn(CREATED);
    when(fixture.repository.findById(8L)).thenReturn(Optional.of(table(8L, "ACTIVE")));

    fixture.service.create(new MonthlyAssignmentTableCreateRequest("9月新客分配"));

    verify(fixture.repository).markReady(8L, "new-doc", "new-sheet", "new-view", "联系方式", "https://doc.weixin.qq.com/new");
    verify(fixture.activationService).activate(8L, CREATED);
    verify(fixture.repository, never()).markFailed(org.mockito.ArgumentMatchers.eq(8L), anyString());
  }

  @Test
  void provisioningFailureMarksHistoryFailedAndDoesNotSwitchTarget() {
    Fixture fixture = new Fixture();
    when(fixture.repository.findByName("自定义分配表")).thenReturn(Optional.empty());
    when(fixture.repository.createPending("自定义分配表", "2026-09", "SYSTEM")).thenReturn(9L);
    when(fixture.targets.assignment()).thenReturn(Optional.of(CURRENT));
    when(fixture.provisioningService.provisionFromTemplate(
        org.mockito.ArgumentMatchers.eq("自定义分配表"), org.mockito.ArgumentMatchers.eq(CURRENT),
        org.mockito.ArgumentMatchers.any()))
        .thenThrow(new IllegalStateException("字段未完整复制：购买项目"));

    assertThatThrownBy(() -> fixture.service.create(new MonthlyAssignmentTableCreateRequest("自定义分配表")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("字段未完整复制");

    verify(fixture.repository).markFailed(9L, "字段未完整复制：购买项目");
    verify(fixture.activationService, never()).activate(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void recordsTheNewDocumentBeforeAFieldProvisioningFailure() {
    Fixture fixture = new Fixture();
    when(fixture.repository.findByName("活动客资")).thenReturn(Optional.empty());
    when(fixture.repository.createPending("活动客资", "2026-09", "SYSTEM")).thenReturn(10L);
    when(fixture.targets.assignment()).thenReturn(Optional.of(CURRENT));
    org.mockito.Mockito.doAnswer(invocation -> {
      @SuppressWarnings("unchecked")
      java.util.function.Consumer<WecomSmartSheetProvisioningService.CreatedDocument> created = invocation.getArgument(2);
      created.accept(new WecomSmartSheetProvisioningService.CreatedDocument("new-doc", "https://doc.weixin.qq.com/new"));
      throw new IllegalStateException("字段未完整复制：管家");
    }).when(fixture.provisioningService).provisionFromTemplate(
        org.mockito.ArgumentMatchers.eq("活动客资"), org.mockito.ArgumentMatchers.eq(CURRENT), org.mockito.ArgumentMatchers.any());

    assertThatThrownBy(() -> fixture.service.create(new MonthlyAssignmentTableCreateRequest("活动客资")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("管家");

    verify(fixture.repository).markDocumentCreated(10L, "new-doc", "https://doc.weixin.qq.com/new");
    verify(fixture.repository).markFailed(10L, "字段未完整复制：管家");
  }

  @Test
  void rebindsAnArchivedTableUsingItsSavedTargetWithoutCreatingAnotherWecomDocument() {
    Fixture fixture = new Fixture();
    MonthlyAssignmentTable archived = table(7L, "ARCHIVED");
    when(fixture.repository.findById(7L)).thenReturn(Optional.of(archived), Optional.of(table(7L, "ACTIVE")));

    MonthlyAssignmentTable result = fixture.service.rebind(7L);

    assertThat(result.status()).isEqualTo("ACTIVE");
    verify(fixture.activationService).activateExisting(archived);
    verify(fixture.provisioningService, never()).provisionFromTemplate(anyString(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void refusesToDeleteTheCurrentAssignmentTableButDeletesArchivedHistoryLocally() {
    Fixture fixture = new Fixture();
    when(fixture.repository.findById(8L)).thenReturn(Optional.of(table(8L, "ACTIVE")));

    assertThatThrownBy(() -> fixture.service.delete(8L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("不能删除");
    verify(fixture.repository, never()).delete(8L);

    when(fixture.repository.findById(7L)).thenReturn(Optional.of(table(7L, "ARCHIVED")));
    fixture.service.delete(7L);

    verify(fixture.repository).delete(7L);
  }

  private static MonthlyAssignmentTable table(long id, String status) {
    return new MonthlyAssignmentTable(
        id, "9月新客分配", "2026-09", "doc", "sheet", "view", "手机号码",
        "https://doc.weixin.qq.com/table", status, null, "SYSTEM",
        LocalDateTime.of(2026, 9, 1, 9, 0), LocalDateTime.of(2026, 9, 1, 9, 1));
  }

  private static final class Fixture {
    final MonthlyAssignmentTableRepository repository = mock(MonthlyAssignmentTableRepository.class);
    final AuxiliarySmartSheetTargets targets = mock(AuxiliarySmartSheetTargets.class);
    final WecomSmartSheetProvisioningService provisioningService = mock(WecomSmartSheetProvisioningService.class);
    final MonthlyAssignmentTableActivationService activationService = mock(MonthlyAssignmentTableActivationService.class);
    final MonthlyAssignmentTableService service = new MonthlyAssignmentTableService(
        repository, targets, provisioningService, activationService, mock(AuditLogger.class),
        Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"), SHANGHAI));
  }
}
