package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.llm.LlmProfileExtractionService;
import com.privateflow.modules.profile.config.ProfileConfig;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileExtractRequest;
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
  private ProfileExtractionClient client;

  @BeforeEach
  void setUp() {
    skillGatewayService = Mockito.mock(SkillGatewayService.class);
    llmProfileExtractionService = Mockito.mock(LlmProfileExtractionService.class);
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
    client = new ProfileExtractionClient(
        skillGatewayService,
        new ProfileFieldRegistry(),
        configProvider,
        llmProfileExtractionService);
  }

  @Test
  void usesLlmProfileUpdatesWhenAvailable() {
    ProfileUpdates llmUpdates = new ProfileUpdates(Map.of("bodyConcerns", new FieldUpdate("腹直肌分离", "HIGH")));
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.of(llmUpdates));

    ProfileUpdates result = client.extract("客户担心腹直肌", customer(), "keeper");

    assertThat(result.fields()).containsKey("bodyConcerns");
    verify(skillGatewayService, never()).extractProfile(any());
  }

  @Test
  void fallsBackToSkillWhenLlmDisabledOrFails() {
    ProfileUpdates skillUpdates = new ProfileUpdates(Map.of("intentLevel", new FieldUpdate("HIGH", "MEDIUM")));
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.empty());
    when(llmProfileExtractionService.fallbackToSkill()).thenReturn(true);
    when(skillGatewayService.extractProfile(any(ProfileExtractRequest.class))).thenReturn(skillUpdates);

    ProfileUpdates result = client.extract("客户想到店评估", customer(), "keeper");

    assertThat(result.fields()).containsKey("intentLevel");
    verify(skillGatewayService).extractProfile(any(ProfileExtractRequest.class));
  }

  @Test
  void returnsEmptyWhenLlmFailsAndSkillFallbackDisabled() {
    when(llmProfileExtractionService.tryExtract(any(ProfileExtractRequest.class))).thenReturn(Optional.empty());
    when(llmProfileExtractionService.fallbackToSkill()).thenReturn(false);

    ProfileUpdates result = client.extract("客户想到店评估", customer(), "keeper");

    assertThat(result.fields()).isEmpty();
    verify(skillGatewayService, never()).extractProfile(any());
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setNickname("测试客户");
    customer.setLeadType("TUAN_GOU");
    customer.setVersion(1);
    return customer;
  }
}
