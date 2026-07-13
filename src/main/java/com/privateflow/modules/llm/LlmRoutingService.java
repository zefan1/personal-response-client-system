package com.privateflow.modules.llm;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.ai.AiEnvironment;
import com.privateflow.modules.api.ai.AiEnvironmentRepository;
import com.privateflow.modules.api.ai.AiEnvironmentType;
import com.privateflow.modules.customer.LeadTypes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class LlmRoutingService {

  private final LlmRouteRepository routeRepository;
  private final AiEnvironmentRepository environmentRepository;
  private final LlmConfigProvider configProvider;

  public LlmRoutingService(
      LlmRouteRepository routeRepository,
      AiEnvironmentRepository environmentRepository,
      LlmConfigProvider configProvider) {
    this.routeRepository = routeRepository;
    this.environmentRepository = environmentRepository;
    this.configProvider = configProvider;
  }

  public List<LlmSceneRoute> list(LlmScene scene, String leadType) {
    return routeRepository.findAll(scene, normalizeLeadTypeForQuery(leadType));
  }

  public List<LlmScene> scenes() {
    return Arrays.asList(LlmScene.values());
  }

  public LlmSceneRoute create(LlmRouteRequest request) {
    validate(request, null);
    String leadType = normalizeLeadType(request.leadType());
    boolean enabled = request.enabled() == null || request.enabled();
    long id = routeRepository.create(request, leadType, enabled);
    return routeRepository.findById(id).orElseThrow();
  }

  public LlmSceneRoute update(long id, LlmRouteRequest request) {
    LlmSceneRoute existing = routeRepository.findById(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "LLM 路由不存在"));
    validate(request, existing.id());
    String leadType = normalizeLeadType(request.leadType());
    boolean enabled = request.enabled() == null ? existing.enabled() : request.enabled();
    routeRepository.update(id, request, leadType, enabled);
    return routeRepository.findById(id).orElseThrow();
  }

  public void delete(long id) {
    routeRepository.findById(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "LLM 路由不存在"));
    routeRepository.delete(id);
  }

  public LlmSceneRoute toggle(long id, boolean enabled) {
    routeRepository.findById(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "LLM 路由不存在"));
    routeRepository.toggle(id, enabled);
    return routeRepository.findById(id).orElseThrow();
  }

  LlmRouteResolution resolve(LlmScene scene, String leadType) {
    return resolveCandidates(scene, leadType).stream().findFirst()
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择 LLM 场景"));
  }

  List<LlmRouteResolution> resolveCandidates(LlmScene scene, String leadType) {
    if (scene == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择 LLM 场景");
    }
    String normalizedLeadType = normalizeLeadTypeForRuntime(leadType);
    List<LlmRouteResolution> resolutions = new ArrayList<>();
    Set<Long> environmentIds = new LinkedHashSet<>();
    for (LlmSceneRoute route : routeRepository.findEnabledCandidates(scene, normalizedLeadType)) {
      if (environmentIds.add(route.environmentId())) {
        resolutions.add(configFromEnvironment(scene, normalizedLeadType, route, false));
      }
    }
    for (LlmSceneRoute route : routeRepository.findEnabledCandidates(scene, "")) {
      if (environmentIds.add(route.environmentId())) {
        resolutions.add(configFromEnvironment(scene, normalizedLeadType, route, false));
      }
    }
    activeFallback(scene, normalizedLeadType)
        .filter(resolution -> resolution.environmentId() == null || environmentIds.add(resolution.environmentId()))
        .ifPresent(resolutions::add);
    if (resolutions.isEmpty()) {
      resolutions.add(globalConfigFallback(scene, normalizedLeadType));
    }
    return resolutions;
  }

  private LlmRouteResolution configFromEnvironment(LlmScene scene, String leadType, LlmSceneRoute route, boolean fallback) {
    AiEnvironment environment = environmentRepository.find(AiEnvironmentType.LLM, route.environmentId())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "LLM 环境不存在"));
    return new LlmRouteResolution(
        scene,
        leadType,
        route.id(),
        environment.id(),
        environment.envName(),
        new LlmConfig(
            environment.baseUrl(),
            environmentRepository.decryptApiKey(AiEnvironmentType.LLM, environment.id()),
            environment.model(),
            environment.protocol() == null ? LlmConfigProvider.OPENAI_COMPATIBLE : environment.protocol(),
            environment.timeoutMs() == null ? 10000 : environment.timeoutMs(),
            environment.temperature() == null ? 0.2 : environment.temperature(),
            environment.maxTokens() == null ? 1024 : environment.maxTokens()),
        fallback);
  }

  private java.util.Optional<LlmRouteResolution> activeFallback(LlmScene scene, String leadType) {
    return environmentRepository.findActive(AiEnvironmentType.LLM)
        .map(environment -> new LlmRouteResolution(
            scene,
            leadType,
            null,
            environment.id(),
            environment.envName(),
            new LlmConfig(
                environment.baseUrl(),
                environmentRepository.decryptApiKey(AiEnvironmentType.LLM, environment.id()),
                environment.model(),
                environment.protocol() == null ? LlmConfigProvider.OPENAI_COMPATIBLE : environment.protocol(),
                environment.timeoutMs() == null ? 10000 : environment.timeoutMs(),
                environment.temperature() == null ? 0.2 : environment.temperature(),
                environment.maxTokens() == null ? 1024 : environment.maxTokens()),
            true));
  }

  private LlmRouteResolution globalConfigFallback(LlmScene scene, String leadType) {
    return new LlmRouteResolution(scene, leadType, null, null, "", configProvider.get(), true);
  }

  private void validate(LlmRouteRequest request, Long existingId) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请求内容不能为空");
    }
    if (request.scene() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择 LLM 场景");
    }
    if (request.environmentId() == null || request.environmentId() <= 0) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择 LLM 环境");
    }
    environmentRepository.find(AiEnvironmentType.LLM, request.environmentId())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.BAD_REQUEST, "LLM 环境不存在"));
    String leadType = normalizeLeadType(request.leadType());
    if (routeRepository.existsInGroup(request.scene(), leadType, request.environmentId(), existingId)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "同一场景、线索类型和 LLM 环境不能重复配置");
    }
  }

  private String normalizeLeadType(String raw) {
    if (raw == null || raw.isBlank()) {
      return "";
    }
    String normalized = LeadTypes.normalize(raw);
    if (LeadTypes.TUAN_GOU.equals(normalized) || LeadTypes.XIAN_SUO.equals(normalized) || LeadTypes.PENDING.equals(normalized)) {
      return normalized;
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "线索类型必须是团购客资、线索客资、待确认或通用");
  }

  private String normalizeLeadTypeForRuntime(String raw) {
    String normalized = normalizeLeadTypeForQuery(raw);
    return normalized.isBlank() ? LeadTypes.PENDING : normalized;
  }

  private String normalizeLeadTypeForQuery(String raw) {
    return raw == null || raw.isBlank() ? "" : normalizeLeadType(raw);
  }
}
