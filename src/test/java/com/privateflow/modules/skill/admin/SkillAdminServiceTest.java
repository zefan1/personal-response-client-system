package com.privateflow.modules.skill.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.profile.service.ProfileAnalysisContextBuilder;
import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.client.SkillHttpClient;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import com.privateflow.modules.skill.service.SkillRequestBuilder;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class SkillAdminServiceTest {

  private SkillSceneBindingRepository bindingRepository;
  private SkillRequestBuilder requestBuilder;
  private SkillHttpClient skillHttpClient;
  private SystemConfigRepository configRepository;
  private AuditLogger auditLogger;
  private ProfileAnalysisContextBuilder profileContextBuilder;
  private SkillProfileAnalysisResponseParser profileParser;
  private TagAnalysisDecisionValidator decisionValidator;
  private SkillAdminService service;

  @BeforeEach
  void setUp() {
    bindingRepository = mock(SkillSceneBindingRepository.class);
    requestBuilder = mock(SkillRequestBuilder.class);
    skillHttpClient = mock(SkillHttpClient.class);
    configRepository = mock(SystemConfigRepository.class);
    auditLogger = mock(AuditLogger.class);
    profileContextBuilder = mock(ProfileAnalysisContextBuilder.class);
    profileParser = mock(SkillProfileAnalysisResponseParser.class);
    decisionValidator = mock(TagAnalysisDecisionValidator.class);
    service = new SkillAdminService(
        bindingRepository,
        requestBuilder,
        skillHttpClient,
        new SkillResponseParser(new ObjectMapper()),
        profileContextBuilder,
        profileParser,
        decisionValidator,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        configRepository,
        auditLogger,
        new ObjectMapper());
  }

  @Test
  void createBindingWritesAuditLog() {
    when(bindingRepository.create(any())).thenReturn(9L);
    when(bindingRepository.findById(9L)).thenReturn(Optional.of(binding()));
    when(bindingRepository.existsInGroup("skill-a", Scene.OPENING, "PENDING", null)).thenReturn(false);

    service.create(new SkillBindingRequest("skill-a", "Skill A", Scene.OPENING, "PENDING", 1));

    verify(auditLogger).log(eq("SKILL_BINDING_CREATE"), any(), eq("skill"), eq("9"), any());
  }

  @Test
  void onlineTestUsesConfiguredTimeout() {
    when(configRepository.findValue("skill.admin.test_message_max_chars")).thenReturn(Optional.of("2000"));
    when(configRepository.findValue("skill.admin.test_timeout_ms")).thenReturn(Optional.of("3210"));
    when(bindingRepository.findById(9L)).thenReturn(Optional.of(binding()));
    when(requestBuilder.build(any())).thenReturn(new java.util.HashMap<>(Map.of("messages", java.util.List.of())));
    when(skillHttpClient.call(any(), eq(3210))).thenReturn("""
        {"suggestions":[{"text":"hello","direction":"OPENING","reason":"test"}]}
        """);

    service.test(9L, new SkillTestRequest("hello"));

    verify(skillHttpClient).call(any(), eq(3210));
    verify(bindingRepository).markTested(9L);
  }

  @Test
  void onlineTestUsesConfiguredMaxMessageChars() {
    when(configRepository.findValue("skill.admin.test_message_max_chars")).thenReturn(Optional.of("120"));
    when(bindingRepository.findById(9L)).thenReturn(Optional.of(binding()));

    assertThatThrownBy(() -> service.test(9L, new SkillTestRequest("x".repeat(121))))
        .isInstanceOf(SkillAdminException.class)
        .hasMessageContaining("120 字符");
  }

  @Test
  void profileOnlineTestUsesProductionEquivalentContextBuilderParserAndValidator() {
    ProfileAnalysisContext context = new ProfileAnalysisContext(
        0L,
        0,
        1,
        List.of(new ProfileAnalysisContext.ConversationMessage("client", "customer evidence", null)),
        Map.of("leadType", "TUAN_GOU"),
        List.of(),
        List.of(),
        List.of());
    ProfileAnalysisResult parsed = ProfileAnalysisResult.empty();
    ProfileAnalysisResult validated = new ProfileAnalysisResult(ProfileUpdates.empty(), List.of());
    when(bindingRepository.findById(10L)).thenReturn(Optional.of(binding(Scene.PROFILE_EXTRACT)));
    when(profileContextBuilder.buildForOnlineTest("PENDING", "customer evidence")).thenReturn(context);
    when(requestBuilder.buildProfileExtract(any(ProfileExtractRequest.class)))
        .thenReturn(new java.util.HashMap<>(Map.of("scene", "PROFILE_EXTRACT")));
    when(skillHttpClient.call(any(), eq(12000))).thenReturn("strict-profile-json");
    when(profileParser.parse("strict-profile-json")).thenReturn(parsed);
    when(decisionValidator.validate(eq(parsed), any(ProfileExtractRequest.class))).thenReturn(validated);

    SkillTestResponse response = service.test(10L, new SkillTestRequest("customer evidence"));

    assertThat(response.suggestions()).isEmpty();
    assertThat(response.rawResponse()).isNull();
    assertThat(response.profileAnalysis()).isSameAs(validated);
    verify(requestBuilder).buildProfileExtract(any(ProfileExtractRequest.class));
    verify(profileParser).parse("strict-profile-json");
    verify(decisionValidator).validate(eq(parsed), any(ProfileExtractRequest.class));
    verify(bindingRepository).markTested(10L);
  }

  private SkillSceneBinding binding() {
    return binding(Scene.OPENING);
  }

  private SkillSceneBinding binding(Scene scene) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 7, 12, 0);
    return new SkillSceneBinding(
        scene == Scene.PROFILE_EXTRACT ? 10L : 9L,
        "skill-a",
        "Skill A",
        scene,
        "PENDING",
        1,
        true,
        null,
        now,
        now);
  }
}
