package com.privateflow.modules.profile.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.config.ProfileConfig;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.skill.ProfileUpdates;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileUpdateOrchestratorTest {

  @Test
  void passesStructuredRawMessagesToProfileExtractionClient() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        configProvider,
        auditLogRepository);
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setVersion(1);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(extractionClient.extract(any(), any(), any(), any())).thenReturn(ProfileUpdates.empty());
    when(confidenceRouter.route(any())).thenReturn(new RoutedProfileUpdates(Map.of(), Map.of()));
    when(configProvider.get()).thenReturn(new ProfileConfig(
        List.of(), 8000, 5, 7, "0 0 3 * * *", 20, 5, 500));
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("client", "客户真实原话", "12:00"),
        new CustomerMessageSentEvent.ChatMessage("keeper", "员工回复", "12:01"));
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "客户真实摘要",
        messages,
        "员工最终发送内容",
        "NEXT_STEP",
        null,
        "keeper-1");

    orchestrator.handleEvent(event);

    verify(extractionClient).extract(eq("客户真实摘要"), eq(messages), eq(customer), eq("keeper-1"));
  }
}
