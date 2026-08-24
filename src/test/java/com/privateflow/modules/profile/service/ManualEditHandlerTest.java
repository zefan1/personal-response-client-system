package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ManualProfileUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.profile.ManualProfileUpdateRequest;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ManualEditHandlerTest {

  @Test
  void publishesAutomaticProjectionEventOnlyAfterTheMariaDbWriteSucceeds() throws Exception {
    CustomerQueryService customers = mock(CustomerQueryService.class);
    ProfileWriter writer = mock(ProfileWriter.class);
    AuditLogRepository audit = mock(AuditLogRepository.class);
    CustomerAccessService access = mock(CustomerAccessService.class);
    ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    ManualEditHandler handler = new ManualEditHandler(
        customers, writer, audit, access, events, new ObjectMapper());
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setVersion(3);
    Map<String, Object> fields = Map.of("nickname", "新昵称");
    when(customers.getByPhone("18800001111")).thenReturn(customer);
    when(access.canAccess(customer)).thenReturn(true);
    when(writer.write(eq("18800001111"), eq(fields), eq(3), eq(true), any(CustomerFieldHistoryContext.class)))
        .thenReturn(4);

    assertThat(handler.update("18800001111", new ManualProfileUpdateRequest(3, fields, "admin")).version())
        .isEqualTo(4);

    verify(writer).write(eq("18800001111"), eq(fields), eq(3), eq(true), any(CustomerFieldHistoryContext.class));
    verify(audit).log(eq("UPDATE_PROFILE"), eq("admin"), eq("customer"), eq("18800001111"),
        org.mockito.ArgumentMatchers.contains("AUTOMATIC"));
    ArgumentCaptor<ManualProfileUpdatedEvent> event = ArgumentCaptor.forClass(ManualProfileUpdatedEvent.class);
    verify(events).publishEvent(event.capture());
    assertThat(event.getValue()).isEqualTo(new ManualProfileUpdatedEvent("18800001111", fields, "admin"));
  }

  @Test
  void projectionEventPreservesIntentionalNullFieldClears() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("leadInitialProcessedAt", null);
    fields.put("leadInitialProcessedBy", null);

    ManualProfileUpdatedEvent event = new ManualProfileUpdatedEvent("18800001111", fields, "admin");

    assertThat(event.fields()).containsEntry("leadInitialProcessedAt", null)
        .containsEntry("leadInitialProcessedBy", null);
  }
}
