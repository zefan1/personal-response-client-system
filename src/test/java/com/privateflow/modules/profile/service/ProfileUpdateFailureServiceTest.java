package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.profile.infra.ProfileUpdateFailureRecord;
import com.privateflow.modules.profile.infra.ProfileUpdateFailureRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ProfileUpdateFailureServiceTest {

  @AfterEach
  void clearAuth() {
    AuthContext.clear();
  }

  @Test
  void adminRetryMarksFailureAndPublishesCorrelatedRecognitionEvent() {
    AuthContext.set(new AuthUser("admin", "管理员", Role.ADMIN, null));
    ProfileUpdateFailureRepository repository = mock(ProfileUpdateFailureRepository.class);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    AuditLogger auditLogger = mock(AuditLogger.class);
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("client", "客户说腰痛", "12:00"));
    ProfileUpdateFailureRecord record = new ProfileUpdateFailureRecord(
        7L, 9L, "18800001111", messages, "keeper-1", "PROFILE_EXTRACTION",
        "IllegalStateException", "model unavailable", "FAILED", 2,
        LocalDateTime.now(), LocalDateTime.now());
    when(repository.find(7L)).thenReturn(java.util.Optional.of(record));
    when(repository.markRetrying(7L)).thenReturn(true);

    ProfileUpdateFailureService service = new ProfileUpdateFailureService(repository, publisher, auditLogger);
    var result = service.retry(7L);

    assertThat(result).containsEntry("accepted", true).containsEntry("retryCount", 3);
    verify(publisher).publishEvent(new RecognizedConversationEvent(
        9L, "18800001111", messages, "keeper-1", 7L));
    verify(auditLogger).log(eq("PROFILE_UPDATE_RETRY"), eq("admin"), eq("profile_update_failure"), eq("7"), any());
  }
}
