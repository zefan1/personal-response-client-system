package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.llm.LlmProfileExtractionService;
import com.privateflow.modules.profile.config.ProfileConfigProvider;
import com.privateflow.modules.profile.infra.ProfileFieldRegistry;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.SkillGatewayService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ProfileExtractionClient {

  private static final Logger log = LoggerFactory.getLogger(ProfileExtractionClient.class);
  private final SkillGatewayService skillGatewayService;
  private final ProfileFieldRegistry fieldRegistry;
  private final ProfileConfigProvider configProvider;
  private final LlmProfileExtractionService llmProfileExtractionService;
  private final ProfileAnalysisContextBuilder contextBuilder;

  public ProfileExtractionClient(
      SkillGatewayService skillGatewayService,
      ProfileFieldRegistry fieldRegistry,
      ProfileConfigProvider configProvider,
      LlmProfileExtractionService llmProfileExtractionService,
      ProfileAnalysisContextBuilder contextBuilder) {
    this.skillGatewayService = skillGatewayService;
    this.fieldRegistry = fieldRegistry;
    this.configProvider = configProvider;
    this.llmProfileExtractionService = llmProfileExtractionService;
    this.contextBuilder = contextBuilder;
  }

  public ProfileUpdates extract(String conversationText, Customer customer, String caller) {
    return extract(conversationText, List.of(), customer, caller);
  }

  public ProfileUpdates extract(
      String conversationText,
      List<CustomerMessageSentEvent.ChatMessage> rawMessages,
      Customer customer,
      String caller) {
    try {
      List<String> targetFields = configProvider.get().extractFields();
      var existingProfile = fieldRegistry.toProfileMap(customer);
      ProfileExtractRequest request = new ProfileExtractRequest(
          conversationText,
          existingProfile,
          targetFields,
          caller,
          contextBuilder.build(customer, existingProfile, rawMessages));
      java.util.Optional<ProfileUpdates> llmUpdates = llmProfileExtractionService.tryExtract(request);
      if (llmUpdates.isPresent()) {
        return llmUpdates.orElseThrow();
      }
      if (!llmProfileExtractionService.fallbackToSkill()) {
        return ProfileUpdates.empty();
      }
      return skillGatewayService.extractProfile(request);
    } catch (RuntimeException ex) {
      log.warn("profile extract degraded to empty updates, phone={}", customer == null ? null : customer.getPhone());
      return ProfileUpdates.empty();
    }
  }
}
