package com.privateflow.modules.tablewrite.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.tablewrite.PendingTableWrite;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.infra.PendingTableWriteRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TableWriteQueueAdminServiceTest {

  private final PendingTableWriteRepository repository = mock(PendingTableWriteRepository.class);
  private final AuditLogger auditLogger = mock(AuditLogger.class);
  private final TableWriteQueueAdminService service = new TableWriteQueueAdminService(
      repository, auditLogger, new ObjectMapper());

  @AfterEach
  void clearAuth() {
    AuthContext.clear();
  }

  @Test
  void adminCanSeeFailedWritesWithoutFullPhoneOrPayload() {
    AuthContext.set(admin());
    PendingTableWrite item = failed(17L);
    when(repository.failed(50)).thenReturn(List.of(item));

    List<Map<String, Object>> rows = service.listFailed(0);

    assertThat(rows).singleElement().satisfies(row -> {
      assertThat(row).containsEntry("id", 17L)
          .containsEntry("customerId", 31L)
          .containsEntry("phoneLast4", "1111")
          .containsEntry("retryCount", 5)
          .containsEntry("errorMsg", "relay timeout");
      assertThat(row).doesNotContainKeys("phone", "payload");
    });
  }

  @Test
  void requeueResetsOnlyTerminalFailedItemAndAuditsOriginalError() {
    AuthContext.set(admin());
    PendingTableWrite item = failed(17L);
    when(repository.findFailed(17L)).thenReturn(Optional.of(item));
    when(repository.requeueFailed(eq(17L), any(LocalDateTime.class))).thenReturn(1);

    Map<String, Object> result = service.requeueFailed(17L);

    assertThat(result).containsEntry("id", 17L).containsEntry("status", "PENDING");
    verify(repository).requeueFailed(eq(17L), any(LocalDateTime.class));
    ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
    verify(auditLogger).log(eq("TABLE_WRITE_REQUEUE"), eq("admin"), eq("table_write"), eq("17"), detail.capture());
    assertThat(detail.getValue()).contains("relay timeout").contains("retryCountBefore");
  }

  @Test
  void adminCanResolvePermanentFailureWithoutDeletingItsAuditContext() {
    AuthContext.set(admin());
    PendingTableWrite item = failed(12L);
    when(repository.findFailed(12L)).thenReturn(Optional.of(item));
    when(repository.resolveFailed(12L)).thenReturn(1);

    Map<String, Object> result = service.resolveFailed(12L);

    assertThat(result).containsEntry("id", 12L).containsEntry("status", "RESOLVED");
    verify(repository).resolveFailed(12L);
    ArgumentCaptor<String> detail = ArgumentCaptor.forClass(String.class);
    verify(auditLogger).log(eq("TABLE_WRITE_RESOLVE"), eq("admin"), eq("table_write"), eq("12"), detail.capture());
    assertThat(detail.getValue()).contains("relay timeout").contains("originalError");
  }

  @Test
  void nonAdminCannotReadOrRequeueFailures() {
    AuthContext.set(new AuthUser("keeper", "管家", Role.KEEPER, null));

    assertThatThrownBy(() -> service.listFailed(50))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("管理员");
    assertThatThrownBy(() -> service.requeueFailed(17L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("管理员");
    assertThatThrownBy(() -> service.resolveFailed(17L))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("管理员");
  }

  private PendingTableWrite failed(long id) {
    PendingTableWrite item = new PendingTableWrite();
    item.setId(id);
    item.setCustomerId(31L);
    item.setPhone("13800001111");
    item.setActionType(TableWriteActionType.UPDATE);
    item.setRetryCount(5);
    item.setErrorMsg("relay timeout");
    item.setCreatedAt(LocalDateTime.of(2026, 8, 15, 9, 0));
    item.setUpdatedAt(LocalDateTime.of(2026, 8, 15, 9, 5));
    return item;
  }

  private AuthUser admin() {
    return new AuthUser("admin", "管理员", Role.ADMIN, null);
  }
}
