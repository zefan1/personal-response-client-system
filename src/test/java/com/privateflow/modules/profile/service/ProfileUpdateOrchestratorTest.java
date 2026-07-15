package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import com.privateflow.modules.tags.AutomaticCustomerTagUpdateRequest;
import com.privateflow.modules.tags.CustomerTagUpdateService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProfileUpdateOrchestratorTest {

  @Test
  void passesStructuredRawMessagesToProfileExtractionClient() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerTagUpdateService customerTagUpdateService = mock(CustomerTagUpdateService.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        customerTagUpdateService,
        configProvider,
        auditLogRepository);
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setVersion(1);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(extractionClient.extract(any(), any(), any(), any())).thenReturn(ProfileAnalysisResult.empty());
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

  @Test
  void doesNotUseEmployeeSentTextAsFallbackProfileEvidence() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerTagUpdateService customerTagUpdateService = mock(CustomerTagUpdateService.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        customerTagUpdateService,
        configProvider,
        auditLogRepository);
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setVersion(1);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(extractionClient.extract(any(), any(), any(), any())).thenReturn(ProfileAnalysisResult.empty());
    when(confidenceRouter.route(any())).thenReturn(new RoutedProfileUpdates(Map.of(), Map.of()));
    when(configProvider.get()).thenReturn(new ProfileConfig(
        List.of(), 8000, 5, 7, "0 0 3 * * *", 20, 5, 500));
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("keeper", "员工历史回复", "12:00"));
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "",
        messages,
        "员工最终发送内容",
        "NEXT_STEP",
        null,
        "keeper-1");

    orchestrator.handleEvent(event);

    verify(extractionClient).extract(eq(""), eq(messages), eq(customer), eq("keeper-1"));
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> updates = ArgumentCaptor.forClass(Map.class);
    verify(profileWriter).write(eq("18800001111"), updates.capture(), eq(1), eq(true));
    assertThat(updates.getValue()).containsEntry("followupNotes", "");
  }

  @Test
  void appliesTagDecisionsWithVersionReturnedByProfileWrite() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerTagUpdateService customerTagUpdateService = mock(CustomerTagUpdateService.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        customerTagUpdateService,
        configProvider,
        auditLogRepository);
    Customer customer = new Customer();
    customer.setId(7L);
    customer.setPhone("18800001111");
    customer.setVersion(1);
    TagAnalysisDecision decision = new TagAnalysisDecision(
        "intent_level",
        List.of("HIGH"),
        new BigDecimal("0.9200"),
        "客户明确表示本周到店",
        TagAnalysisResultType.UPDATE,
        TagAnalysisAction.REPLACE);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(extractionClient.extract(any(), any(), any(), any()))
        .thenReturn(new ProfileAnalysisResult(ProfileUpdates.empty(), List.of(decision)));
    when(confidenceRouter.route(any())).thenReturn(new RoutedProfileUpdates(Map.of(), Map.of()));
    when(profileWriter.write(eq("18800001111"), any(), eq(1), eq(true))).thenReturn(2);
    when(configProvider.get()).thenReturn(new ProfileConfig(
        List.of(), 8000, 5, 7, "0 0 3 * * *", 20, 5, 500));
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("client", "客户真实原话", "12:00"));

    orchestrator.handleEvent(new CustomerMessageSentEvent(
        "18800001111", "Alice", false, "私域客资管理表", "TUAN_GOU",
        "客户真实摘要", messages, "员工发送内容", "NEXT_STEP", null, "keeper-1"));

    ArgumentCaptor<AutomaticCustomerTagUpdateRequest> captor =
        ArgumentCaptor.forClass(AutomaticCustomerTagUpdateRequest.class);
    verify(customerTagUpdateService).applyAutomatic(captor.capture());
    assertThat(captor.getValue().customerId()).isEqualTo(7L);
    assertThat(captor.getValue().expectedCustomerVersion()).isEqualTo(2);
    assertThat(captor.getValue().effectiveMessageCount()).isEqualTo(1);
    assertThat(captor.getValue().decisions()).containsExactly(decision);
  }

  @Test
  void automaticTagFailureDoesNotStopSuggestionsOrProfileAudit() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerTagUpdateService customerTagUpdateService = mock(CustomerTagUpdateService.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator,
        customerQueryService,
        extractionClient,
        confidenceRouter,
        profileWriter,
        suggestionQueueManager,
        customerTagUpdateService,
        configProvider,
        auditLogRepository);
    Customer customer = new Customer();
    customer.setId(7L);
    customer.setPhone("18800001111");
    customer.setVersion(1);
    when(customerQueryService.getByPhone("18800001111")).thenReturn(customer);
    when(extractionClient.extract(any(), any(), any(), any()))
        .thenReturn(new ProfileAnalysisResult(ProfileUpdates.empty(), List.of(new TagAnalysisDecision(
            "intent_level", List.of("HIGH"), new BigDecimal("0.9200"), "明确证据",
            TagAnalysisResultType.UPDATE, TagAnalysisAction.REPLACE))));
    when(confidenceRouter.route(any())).thenReturn(new RoutedProfileUpdates(Map.of(), Map.of()));
    when(profileWriter.write(eq("18800001111"), any(), eq(1), eq(true))).thenReturn(2);
    when(customerTagUpdateService.applyAutomatic(any())).thenThrow(new IllegalStateException("db down"));
    when(configProvider.get()).thenReturn(new ProfileConfig(
        List.of(), 8000, 5, 7, "0 0 3 * * *", 20, 5, 500));

    orchestrator.handleEvent(new CustomerMessageSentEvent(
        "18800001111", "Alice", false, "私域客资管理表", "TUAN_GOU",
        "客户真实摘要",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "客户真实原话", "12:00")),
        "员工发送内容", "NEXT_STEP", null, "keeper-1"));

    verify(suggestionQueueManager).enqueue(eq("18800001111"), eq(customer), eq(Map.of()));
    verify(auditLogRepository).log(
        "UPDATE_PROFILE", "SYSTEM", "customer", "18800001111", "auto profile update");
  }
}
