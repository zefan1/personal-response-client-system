package com.privateflow.modules.skill.admin;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.client.SkillHttpClient;
import com.privateflow.modules.skill.parser.SkillResponseParser;
import com.privateflow.modules.skill.service.SkillRequestBuilder;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class SkillAdminService {

  private static final List<String> LEAD_TYPES = List.of("TUAN_GOU", "XIAN_SUO", "PENDING");
  private static final String BAD_REQUEST_CODE = "80-10001";
  private static final List<Scene> SCENES = List.of(
      Scene.CHAT_RECOGNIZE,
      Scene.ACTIVE_REPLY,
      Scene.REGENERATE,
      Scene.PROFILE_EXTRACT,
      Scene.OPENING);
  private static final int TEST_MESSAGE_MAX_CHARS = 2000;
  private static final int TEST_TIMEOUT_MS = 12000;
  private final SkillSceneBindingRepository bindingRepository;
  private final SkillRequestBuilder requestBuilder;
  private final SkillHttpClient skillHttpClient;
  private final SkillResponseParser responseParser;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;

  public SkillAdminService(
      SkillSceneBindingRepository bindingRepository,
      SkillRequestBuilder requestBuilder,
      SkillHttpClient skillHttpClient,
      SkillResponseParser responseParser,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService) {
    this.bindingRepository = bindingRepository;
    this.requestBuilder = requestBuilder;
    this.skillHttpClient = skillHttpClient;
    this.responseParser = responseParser;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
  }

  public List<SkillSceneBinding> list(Scene scene, String leadType) {
    return bindingRepository.findAll(scene, leadType);
  }

  public SkillSceneBinding create(SkillBindingRequest request) {
    validate(request, null);
    long id = bindingRepository.create(request);
    publishRefresh("skill_scene_bindings");
    return bindingRepository.findById(id).orElseThrow();
  }

  public SkillSceneBinding update(long id, SkillBindingRequest request) {
    bindingRepository.findById(id)
        .orElseThrow(() -> new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "Skill 绑定不存在"));
    validate(request, id);
    bindingRepository.update(id, request);
    publishRefresh("skill_scene_bindings");
    return bindingRepository.findById(id).orElseThrow();
  }

  public void delete(long id) {
    SkillSceneBinding existing = bindingRepository.findById(id)
        .orElseThrow(() -> new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "Skill 绑定不存在"));
    bindingRepository.delete(existing.id());
    publishRefresh("skill_scene_bindings");
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
    if (request.testMessage() == null || request.testMessage().isBlank() || request.testMessage().length() > TEST_MESSAGE_MAX_CHARS) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "testMessage 必填且不能超过 2000 字符");
    }
    long start = System.currentTimeMillis();
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
    String raw = skillHttpClient.call(payload, TEST_TIMEOUT_MS);
    SkillResponse response = responseParser.parseReplies(raw);
    long elapsed = System.currentTimeMillis() - start;
    bindingRepository.markTested(id);
    return new SkillTestResponse(response.suggestions(), elapsed, response);
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
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "leadType 必须是 TUAN_GOU / XIAN_SUO / PENDING");
    }
    if (bindingRepository.existsInGroup(request.skillId().trim(), request.scene(), leadType, existingId)) {
      throw new SkillAdminException(SkillAdminErrorCodes.BAD_REQUEST, "same scene+leadType 下 skillId 不能重复");
    }
  }

  private void publishRefresh(String key) {
    eventPublisher.publishEvent(new ConfigChangedEvent(key));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", key)));
  }
}
