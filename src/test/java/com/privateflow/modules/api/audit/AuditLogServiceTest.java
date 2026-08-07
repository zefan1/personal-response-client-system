package com.privateflow.modules.api.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuditLogServiceTest {

  @AfterEach
  void clearAuthContext() {
    AuthContext.clear();
  }

  @Test
  void exposesDeleteNoticeAsAnAuditableNoticeAction() {
    AuthContext.set(new AuthUser("admin", "System Admin", Role.ADMIN, null));
    AuditLogService service = new AuditLogService(
        mock(AuditLogRepository.class),
        mock(SystemConfigRepository.class),
        new ObjectMapper(),
        Runnable::run);

    Map<String, Object> result = service.actions();
    @SuppressWarnings("unchecked")
    List<Map<String, String>> actions = (List<Map<String, String>>) result.get("actions");

    assertThat(actions)
        .anySatisfy(action -> {
          assertThat(action.get("action")).isEqualTo("DELETE_NOTICE");
          assertThat(action.get("label")).isEqualTo("删除公告");
          assertThat(action.get("group")).isEqualTo("公告操作");
        });
  }
}
