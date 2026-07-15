package com.privateflow.modules.skill.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.profile.service.ProfileAnalysisContextBuilder;
import com.privateflow.modules.profile.service.TagAnalysisDecisionValidator;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.client.SkillHttpClient;
import com.privateflow.modules.skill.parser.SkillProfileAnalysisResponseParser;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import com.privateflow.modules.skill.service.SkillRequestBuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SkillAdminService {

  private static final List<String> LEAD_TYPES = List.of("GENERAL", "TUAN_GOU", "XIAN_SUO", "PENDING");
  private static final String BAD_REQUEST_CODE = "80-10001";
  private static final List<Scene> SCENES = List.of(
      Scene.CHAT_RECOGNIZE,
      Scene.ACTIVE_REPLY,
      Scene.REGENERATE,
      Scene.PROFILE_EXTRACT,
      Scene.OPENING);
  private static final int DEFAULT_TEST_MESSAGE_MAX_CHARS = 2000;
  private static final int DEFAULT_TEST_TIMEOUT_MS = 12000;
  private final SkillSceneBindingRepository bindingRepository;
  private final SkillRequestBuilder requestBuilder;
  private final SkillHttpClient skillHttpClient;
  private final SkillResponseParser responseParser;
  private final ProfileAnalysisContextBuilder profileContextBuilder;
  private final SkillProfileAnalysisResponseParser profileResponseParser;
  private final TagAnalysisDecisionValidator decisionValidator;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final SystemConfigRepository configRepository;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  public SkillAdminService(
      SkillSceneBindingRepository bindingRepository,
      SkillRequestBuilder requestBuilder,
      SkillHttpClient skillHttpClient,
      SkillResponseParser responseParser,
      ProfileAnalysisContextBuilder profileContextBuilder,
      SkillProfileAnalysisResponseParser profileResponseParser,
      TagAnalysisDecisionValidator decisionValidator,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      SystemConfigRepository configRepository,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.bindingRepository = bindingRepository;
    this.requestBuilder = requestBuilder;
    this.skillHttpClient = skillHttpClient;
    this.responseParser = responseParser;
    this.profileContextBuilder = profileContextBuilder;
    this.profileResponseParser = profileResponseParser;
    this.decisionValidator = decisionValidator;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.configRepository = configRepository;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public List<SkillSceneBinding> list(Scene scene, String leadType) {
    return bindingRepository.findAll(scene, leadType);
  }

  public SkillSceneBinding create(SkillBindingRequest request) {
    validate(request, null);
    long id = bindingRepository.create(request);
    publishRefresh("skill_scene_bindings");
    SkillSceneBinding saved = bindingRepository.findById(id).orElseThrow();
    audit("SKILL_BINDING_CREATE", saved, bindingDetail(saved));
    return saved;
  }

  public SkillSceneBinding update(long id, SkillBindingRequest request) {
    SkillSceneBinding existing = bindingRepository.findById(id)
        .orElseThrow(() -> new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "Skill 绑定不存在"));
    validate(request, id);
    bindingRepository.update(id, request);
    publishRefresh("skill_scene_bindings");
    SkillSceneBinding saved = bindingRepository.findById(id).orElseThrow();
    Map<String, Object> detail = bindingDetail(saved);
    detail.put("previousSkillId", existing.skillId());
    detail.put("previousScene", existing.scene().name());
    detail.put("previousLeadType", existing.leadType());
    audit("SKILL_BINDING_UPDATE", saved, detail);
    return saved;
  }

  public void delete(long id) {
    SkillSceneBinding existing = bindingRepository.findById(id)
        .orElseThrow(() -> new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "Skill 绑定不存在"));
    bindingRepository.delete(existing.id());
    publishRefresh("skill_scene_bindings");
    audit("SKILL_BINDING_DELETE", existing, bindingDetail(existing));
  }

  public SkillToggleResponse toggle(long id, boolean enabled) {
    SkillSceneBinding existing = bindingRepository.findById(id)
        .orElseThrow(() -> new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "Skill 绑定不存在"));
    String warning = null;
    if (!enabled && existing.enabled() && bindingRepository.countEnabledPeers(existing) == 0) {
      warning = "这是当前场景和客户类型组合最后一个启用的绑定。停用后该路由将断开，建议先配置替代绑定再停用。";
    }
    bindingRepository.toggle(id, enabled);
    publishRefresh("skill_scene_bindings");
    Map<String, Object> detail = bindingDetail(existing);
    detail.put("enabledBefore", existing.enabled());
    detail.put("enabledAfter", enabled);
    audit("SKILL_BINDING_TOGGLE", existing, detail);
    return new SkillToggleResponse(id, enabled, warning);
  }

  public List<AvailableSkill> availableSkills() {
    return bindingRepository.findAll(null, null).stream()
        .map(binding -> new AvailableSkill(binding.skillId(), binding.skillName()))
        .distinct()
        .toList();
  }

  public SkillTestResponse test(long id, SkillTestRequest request) {
    SkillSceneBinding binding = bindingRepository.findById(id)
        .orElseThrow(() -> new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "Skill 绑定不存在"));
    int maxChars = testMessageMaxChars();
    if (request.testMessage() == null || request.testMessage().isBlank() || request.testMessage().length() > maxChars) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "testMessage 必填且不能超过 " + maxChars + " 字符");
    }
    long start = System.currentTimeMillis();
    SkillTestResponse response = binding.scene() == Scene.PROFILE_EXTRACT
        ? testProfileExtract(binding, request, start)
        : testReplies(binding, request, start);
    bindingRepository.markTested(id);
    return response;
  }

  private SkillTestResponse testReplies(
      SkillSceneBinding binding,
      SkillTestRequest request,
      long start) {
    SkillRequest skillRequest = new SkillRequest(
        binding.scene(),
        binding.leadType(),
        "",
        request.testMessage(),
        Map.of("leadType", binding.leadType()),
        Map.of("skillId", binding.skillId(), "skillName", binding.skillName()),
        List.of(),
        List.of(Map.of("role", "customer", "content", request.testMessage())),
        AuthContext.username() + ":ADMIN_TEST");
    Map<String, Object> payload = requestBuilder.build(skillRequest);
    payload.put("skill_id", binding.skillId());
    String raw = skillHttpClient.call(payload, testTimeoutMs());
    SkillResponse response = responseParser.parseReplies(raw);
    return new SkillTestResponse(
        response.suggestions(),
        System.currentTimeMillis() - start,
        response);
  }

  private SkillTestResponse testProfileExtract(
      SkillSceneBinding binding,
      SkillTestRequest request,
      long start) {
    String caller = AuthContext.username() + ":ADMIN_TEST";
    ProfileAnalysisContext context = profileContextBuilder.buildForOnlineTest(
        binding.leadType(),
        request.testMessage());
    ProfileExtractRequest profileRequest = new ProfileExtractRequest(
        request.testMessage(),
        context.customerProfile(),
        List.of(),
        caller,
        context);
    Map<String, Object> payload = requestBuilder.buildProfileExtract(profileRequest);
    payload.put("skill_id", binding.skillId());
    payload.put("skill_group_id", binding.skillId());
    String raw = skillHttpClient.call(payload, testTimeoutMs());
    ProfileAnalysisResult analysis = decisionValidator.validate(
        profileResponseParser.parse(raw),
        profileRequest);
    return new SkillTestResponse(
        List.of(),
        System.currentTimeMillis() - start,
        null,
        analysis);
  }

  private int testMessageMaxChars() {
    return integerConfig("skill.admin.test_message_max_chars", DEFAULT_TEST_MESSAGE_MAX_CHARS, 100, 20000);
  }

  private int testTimeoutMs() {
    return integerConfig("skill.admin.test_timeout_ms", DEFAULT_TEST_TIMEOUT_MS, 1000, 60000);
  }

  private int integerConfig(String key, int fallback, int min, int max) {
    return configRepository.findValue(key)
        .map(value -> {
          try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(min, Math.min(max, parsed));
          } catch (NumberFormatException ex) {
            return fallback;
          }
        })
        .orElse(fallback);
  }

  private void validate(SkillBindingRequest request, Long existingId) {
    if (request == null) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "请求体不能为空");
    }
    if (request.skillId() == null || request.skillId().isBlank() || request.skillId().length() > 100) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "skillId 必填且不能超过 100 字符");
    }
    if (request.skillName() == null || request.skillName().isBlank() || request.skillName().length() > 100) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "skillName 必填且不能超过 100 字符");
    }
    if (request.scene() == null) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "scene 必填");
    }
    if (!SCENES.contains(request.scene())) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "scene 参数非法");
    }
    String leadType = request.leadType() == null ? "" : request.leadType().trim();
    if (!LEAD_TYPES.contains(leadType)) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "线索类型必须是全部客资、团购客资、线索客资或待确认");
    }
    if (bindingRepository.existsInGroup(request.skillId().trim(), request.scene(), leadType, existingId)) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "同一场景和线索类型下不能重复绑定同一个 Skill");
    }
  }

  private void publishRefresh(String key) {
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key)));
  }

  private Map<String, Object> bindingDetail(SkillSceneBinding binding) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", binding.id());
    detail.put("skillId", binding.skillId());
    detail.put("skillName", binding.skillName());
    detail.put("scene", binding.scene().name());
    detail.put("leadType", binding.leadType());
    detail.put("priority", binding.priority());
    detail.put("enabled", binding.enabled());
    return detail;
  }

  private void audit(String action, SkillSceneBinding binding, Map<String, Object> detail) {
    String fallbackDetail = "skill binding " + binding.id() + " changed";
    try {
      auditLogger.log(
          action,
          AuthContext.username(),
          "skill",
          String.valueOf(binding.id()),
          objectMapper.writeValueAsString(detail));
    } catch (Exception ex) {
      auditLogger.log(action, AuthContext.username(), "skill", String.valueOf(binding.id()), fallbackDetail);
    }
  }
}
