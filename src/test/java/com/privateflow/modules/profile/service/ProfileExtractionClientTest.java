package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.llm.LlmProfileExtractionService;
import com.privateflow.modules.profile.config.ProfileConfig;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.SkillGatewayService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ProfileExtractionClientTest {

  private SkillGatewayService skillGatewayService;
  private LlmProfileExtractionService llmProfileExtractionService;
  private ProfileAnalysisContextBuilder contextBuilder;
  private ProfileExtractionClient client;

  @BeforeEach
  void setUp() {
    skillGatewayService = Mockito.mock(SkillGatewayService.class);
    llmProfileExtractionService = Mockito.mock(LlmProfileExtractionService.class);
    contextBuilder = Mockito.mock(ProfileAnalysisContextBuilder.class);
    ProfileConfigProvider configProvider = Mockito.mock(ProfileConfigProvider.class);
    when(configProvider.get()).thenReturn(new ProfileConfig(
        List.of("bodyConcerns", "intentLevel"),
        8000,
        5,
        7,
        "0 0 3 * * *",
        20,
        5,
        500));
    when(contextBuilder.build(any(), any(), any())).thenReturn(new ProfileAnalysisContext(
        7L, 1, 1, List.of(), Map.of("nickname", "测试客户"), List.of(), List.of(), List.of()));
    client = new ProfileExtractionClient(
        skillGatewayService,
        new ProfileFieldRegistry(),
        configProvider,
        llmProfileExtractionService,
        contextBuilder);
  }

  @Test
  void usesLlmProfileUpdatesWhenAvailable() {
    ProfileUpdates llmUpdates = new ProfileUpdates(Map.of("bodyConcerns", new FieldUpdate("腹直肌分离", "HIGH")));
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.of(llmUpdates));

    ProfileAnalysisResult result = client.extract("客户担心腹直肌", customer(), "keeper");

    assertThat(result.profileUpdates().fields()).containsKey("bodyConcerns");
    verify(skillGatewayService, never()).extractProfile(any());
  }

  @Test
  void fallsBackToSkillWhenLlmDisabledOrFails() {
    ProfileUpdates skillUpdates = new ProfileUpdates(Map.of("intentLevel", new FieldUpdate("HIGH", "MEDIUM")));
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.empty());
    when(llmProfileExtractionService.fallbackToSkill()).thenReturn(true);
    when(skillGatewayService.extractProfile(any(ProfileExtractRequest.class)))
        .thenReturn(new ProfileAnalysisResult(skillUpdates, List.of()));

    ProfileAnalysisResult result = client.extract("客户想到店评估", customer(), "keeper");

    assertThat(result.profileUpdates().fields()).containsKey("intentLevel");
    verify(skillGatewayService).extractProfile(any(ProfileExtractRequest.class));
  }

  @Test
  void returnsEmptyWhenLlmFailsAndSkillFallbackDisabled() {
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.empty());
    when(llmProfileExtractionService.fallbackToSkill()).thenReturn(false);

    ProfileAnalysisResult result = client.extract("客户想到店评估", customer(), "keeper");

    assertThat(result.profileUpdates().fields()).isEmpty();
    verify(skillGatewayService, never()).extractProfile(any());
  }

  @Test
  void attachesSharedAnalysisContextBuiltFromRecentMessages() {
    ProfileAnalysisContext analysisContext = new ProfileAnalysisContext(
        7L, 1, 1, List.of(), Map.of("nickname", "测试客户"), List.of(), List.of(), List.of());
    when(contextBuilder.build(any(), any(), any())).thenReturn(analysisContext);
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.empty());
    when(llmProfileExtractionService.fallbackToSkill()).thenReturn(true);
    when(skillGatewayService.extractProfile(any(ProfileExtractRequest.class))).thenReturn(ProfileAnalysisResult.empty());
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("client", "客户真实原话", "12:00"));

    client.extract("摘要", messages, customer(), "keeper");

    org.mockito.ArgumentCaptor<ProfileExtractRequest> captor = org.mockito.ArgumentCaptor.forClass(ProfileExtractRequest.class);
    verify(skillGatewayService).extractProfile(captor.capture());
    assertThat(captor.getValue().analysisContext()).isSameAs(analysisContext);
    verify(contextBuilder).build(any(Customer.class), any(), org.mockito.ArgumentMatchers.eq(messages));
  }

  @Test
  void skipsProfileAnalysisWhenThereAreNoCustomerMessages() {
    when(contextBuilder.build(any(), any(), any())).thenReturn(ProfileAnalysisContext.empty());
    List<CustomerMessageSentEvent.ChatMessage> messages = List.of(
        new CustomerMessageSentEvent.ChatMessage("keeper", "员工发送内容", "12:00"));

    ProfileAnalysisResult result = client.extract("员工发送内容", messages, customer(), "keeper");

    assertThat(result).isEqualTo(ProfileAnalysisResult.empty());
    verifyNoInteractions(llmProfileExtractionService, skillGatewayService);
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setId(7L);
    customer.setNickname("测试客户");
    customer.setLeadType("TUAN_GOU");
    customer.setVersion(1);
    return customer;
  }
}
