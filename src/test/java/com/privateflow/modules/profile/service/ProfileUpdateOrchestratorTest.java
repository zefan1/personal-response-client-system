package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.common.events.RecognizedConversationEvent;
import com.privateflow.common.events.RecognizedProfileFactsUpdatedEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.profile.config.ProfileConfig;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.AuditLogRepository;
import com.privateflow.modules.profile.infra.ProfileWriter;
import com.privateflow.modules.profile.infra.ProfileUpdateFailureRepository;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.FieldUpdate;
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
import org.springframework.context.ApplicationEventPublisher;

class ProfileUpdateOrchestratorTest {

  @Test
  void recognitionWritesFactsImmediatelyButDoesNotWriteFollowupFields() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerTagUpdateService customerTagUpdateService = mock(CustomerTagUpdateService.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator, customerQueryService, extractionClient, confidenceRouter, profileWriter,
        suggestionQueueManager, customerTagUpdateService, configProvider, auditLogRepository, eventPublisher);
    Customer customer = new Customer();
    customer.setId(9L);
    customer.setPhone("18800001111");
    customer.setVersion(3);
    when(customerQueryService.getById(9L)).thenReturn(customer);
    when(extractionClient.extract(any(), any(), eq(customer), eq("keeper-1")))
        .thenReturn(ProfileAnalysisResult.empty());
    when(confidenceRouter.route(any())).thenReturn(new RoutedProfileUpdates(
        Map.of(
            "bodyConcerns", new FieldUpdate("腰痛", "HIGH"),
            "nextFollowupDir", new FieldUpdate("下周回访", "HIGH")),
        Map.of()));

    orchestrator.handleRecognition(new RecognizedConversationEvent(
        9L,
        "18800001111",
        List.of(new CustomerMessageSentEvent.ChatMessage("client", "最近腰痛", "12:00")),
        "keeper-1"));

    verify(profileWriter).writeByCustomerId(
        eq(9L), eq(Map.of("bodyConcerns", "腰痛")), eq(3), eq(true));
    verify(profileWriter, never()).write(eq("18800001111"), any(), any(), eq(true));
    ArgumentCaptor<RecognizedProfileFactsUpdatedEvent> facts = ArgumentCaptor.forClass(
        RecognizedProfileFactsUpdatedEvent.class);
    verify(eventPublisher).publishEvent(facts.capture());
    assertThat(facts.getValue().customerId()).isEqualTo(9L);
    assertThat(facts.getValue().fields()).containsEntry("bodyConcerns", "腰痛");
  }

  @Test
  void recognitionFailureIsPersistedWithStageAndAuditInsteadOfOnlyBeingLogged() {
    EventDeduplicator deduplicator = mock(EventDeduplicator.class);
    CustomerQueryService customerQueryService = mock(CustomerQueryService.class);
    ProfileExtractionClient extractionClient = mock(ProfileExtractionClient.class);
    ConfidenceRouter confidenceRouter = mock(ConfidenceRouter.class);
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    SuggestionQueueManager suggestionQueueManager = mock(SuggestionQueueManager.class);
    CustomerTagUpdateService customerTagUpdateService = mock(CustomerTagUpdateService.class);
    ProfileConfigProvider configProvider = mock(ProfileConfigProvider.class);
    AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);
    ProfileUpdateFailureRepository failureRepository = mock(ProfileUpdateFailureRepository.class);
    Customer customer = new Customer();
    customer.setId(9L);
    customer.setPhone("18800001111");
    when(customerQueryService.getById(9L)).thenReturn(customer);
    when(extractionClient.extract(any(), any(), eq(customer), eq("keeper-1")))
        .thenThrow(new IllegalStateException("model unavailable"));

    ProfileUpdateOrchestrator orchestrator = new ProfileUpdateOrchestrator(
        deduplicator, customerQueryService, extractionClient, confidenceRouter, profileWriter,
        suggestionQueueManager, customerTagUpdateService, configProvider, auditLogRepository,
        mock(ApplicationEventPublisher.class), failureRepository);
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("client", "客户说腰痛", "12:00"));

    orchestrator.handleRecognition(new RecognizedConversationEvent(9L, "18800001111", messages, "keeper-1"));

    verify(failureRepository).recordFailure(
        eq(9L), eq("18800001111"), eq(messages), eq("keeper-1"), eq("PROFILE_EXTRACTION"), any(RuntimeException.class));
    verify(auditLogRepository).log(eq("PROFILE_UPDATE_FAILED"), eq("SYSTEM"), eq("customer"), eq("9"), any());
  }

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
    verify(profileWriter, never()).write(eq("18800001111"), any(), eq(1), eq(true));
  }

  @Test
  void doesNotUseQuickTemplateFollowupNotesAsCustomerProfileEvidence() {
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
    CustomerMessageSentEvent event = new CustomerMessageSentEvent(
        "18800001111",
        "Alice",
        false,
        "私域客资管理表",
        "TUAN_GOU",
        "发送模板《到店提醒》：王女士明天见",
        List.of(),
        "王女士明天见",
        "OPENING",
        null,
        true,
        "keeper-1");

    orchestrator.handleEvent(event);

    verify(extractionClient).extract(eq(""), eq(List.of()), eq(customer), eq("keeper-1"));
    verify(profileWriter, never()).write(eq("18800001111"), any(), eq(1), eq(true));
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
    assertThat(captor.getValue().expectedCustomerVersion()).isEqualTo(1);
    verify(profileWriter, never()).write(eq("18800001111"), any(), eq(1), eq(true));
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
