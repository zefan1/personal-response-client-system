package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PendingSendConfirmationAdminServiceTest {

  private final SendConfirmationRepository repository = mock(SendConfirmationRepository.class);
  private final PendingSendConfirmationAdminService service = new PendingSendConfirmationAdminService(repository);

  @AfterEach
  void clearAuth() {
    AuthContext.clear();
  }

  @Test
  void adminReadsStatusSummaryForSelectedOperator() {
    AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
    when(repository.summary(7, "keeper-a")).thenReturn(Map.of(
        "awaitingDecisionCount", 2L,
        "unsentCount", 3L,
        "recognitionRetryCount", 1L,
        "sentCount", 12L,
        "confirmedSendRate", 0.75));

    assertThat(service.summary(7, "keeper-a"))
        .containsEntry("awaitingDecisionCount", 2L)
        .containsEntry("unsentCount", 3L)
        .containsEntry("sentCount", 12L);
  }

  @Test
  void nonAdminCannotReadSummary() {
    AuthContext.set(new AuthUser("keeper", "管家", Role.KEEPER, null));

    assertThatThrownBy(() -> service.summary(7, null))
        .hasMessageContaining("permission denied");
  }
}
