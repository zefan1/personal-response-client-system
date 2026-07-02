package com.privateflow.modules.profile.service;

import com.privateflow.modules.customer.Customer;
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

  public ProfileExtractionClient(
      SkillGatewayService skillGatewayService,
      ProfileFieldRegistry fieldRegistry,
      ProfileConfigProvider configProvider) {
    this.skillGatewayService = skillGatewayService;
    this.fieldRegistry = fieldRegistry;
    this.configProvider = configProvider;
  }

  public ProfileUpdates extract(String conversationText, Customer customer, String caller) {
    try {
      List<String> targetFields = configProvider.get().extractFields();
      ProfileExtractRequest request = new ProfileExtractRequest(
          conversationText,
          fieldRegistry.toProfileMap(customer),
          targetFields,
          caller);
      return skillGatewayService.extractProfile(request);
    } catch (RuntimeException ex) {
      log.warn("profile extract degraded to empty updates, phone={}", customer == null ? null : customer.getPhone());
      return ProfileUpdates.empty();
    }
  }
}
