package com.privateflow.modules.quicksearch.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import com.privateflow.modules.quicksearch.ContentType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuickSearchAdminService {

  private static final String CONFIG_KEY = "quick_search";
  private static final Set<String> LEAD_TYPES = Set.of("TUAN_GOU", "XIAN_SUO", "GENERAL");
  private static final int DEFAULT_MAX_IMAGE_MB = 10;
  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 50;
  private static final Set<String> LIST_SORT_FIELDS = Set.of(
      "contentType",
      "leadType",
      "title",
      "shortcutCode",
      "sortOrder",
      "enabled",
      "createdAt",
      "updatedAt");
  private final QuickSearchAdminRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final SystemAlertRepository alertRepository;
  private final QuickSearchImageStorage imageStorage;
  private final SystemConfigRepository configRepository;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  public QuickSearchAdminService(
      QuickSearchAdminRepository repository,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      SystemAlertRepository alertRepository,
      QuickSearchImageStorage imageStorage,
      SystemConfigRepository configRepository,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.alertRepository = alertRepository;
    this.imageStorage = imageStorage;
    this.configRepository = configRepository;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> list(QuickSearchAdminListQuery query) {
    QuickSearchAdminListQuery safeQuery = normalizeListQuery(query);
    long total = repository.count(safeQuery);
    int totalPages = (int) Math.max(1, Math.ceil(total / (double) safeQuery.size()));
    return Map.of(
        "total", total,
        "page", safeQuery.page(),
        "size", safeQuery.size(),
        "totalPages", totalPages,
        "items", repository.list(safeQuery));
  }

  public Map<String, Object> create(QuickSearchItemRequest request) {
    validate(request, null, true);
    long id = repository.create(request, AuthContext.username());
    publishRefresh();
    repository.find(id).ifPresent(item -> audit("QUICK_SEARCH_CREATE", item, quickSearchDetail(item)));
    return Map.of("id", id);
  }

  public QuickSearchAdminItem update(long id, QuickSearchItemRequest request) {
    QuickSearchAdminItem existing = repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "速搜内容不存在"));
    validate(request, id, false);
    if (existing.contentType() == ContentType.IMAGE && request.imageUrl() != null && !request.imageUrl().equals(existing.imageUrl())) {
      repository.enqueueCleanup(existing.imageUrl());
    }
    repository.update(id, request);
    publishRefresh();
    QuickSearchAdminItem saved = repository.find(id).orElseThrow();
    Map<String, Object> detail = quickSearchDetail(saved);
    detail.put("previousContentType", existing.contentType().name());
    detail.put("previousShortcutCode", existing.shortcutCode());
    audit("QUICK_SEARCH_UPDATE", saved, detail);
    return saved;
  }

  public Map<String, Object> toggle(long id) {
    QuickSearchAdminItem existing = repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "速搜内容不存在"));
    boolean enabled = repository.toggle(id);
    publishRefresh();
    Map<String, Object> detail = quickSearchDetail(existing);
    detail.put("enabledBefore", existing.enabled());
    detail.put("enabledAfter", enabled);
    audit("QUICK_SEARCH_TOGGLE", existing, detail);
    return Map.of("isEnabled", enabled);
  }

  public void delete(long id) {
    QuickSearchAdminItem existing = repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "速搜内容不存在"));
    if (existing.contentType() == ContentType.IMAGE) {
      repository.enqueueCleanup(existing.imageUrl());
    }
    repository.delete(id);
    publishRefresh();
    audit("QUICK_SEARCH_DELETE", existing, quickSearchDetail(existing));
  }

  public ImageUploadResponse uploadImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择要上传的图片");
    }
    int maxMb = imageMaxSizeMb();
    if (file.getSize() > (long) maxMb * 1024 * 1024) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "图片大小不能超过 " + maxMb + "MB");
    }
    try {
      byte[] bytes = file.getBytes();
      String ext = detectImageExt(bytes);
      String url = imageStorage.store(bytes, ext);
      auditLogger.log("QUICK_SEARCH_IMAGE_UPLOAD", AuthContext.username(), "template", url, toJson(Map.of(
          "extension", ext,
          "size", file.getSize(),
          "imageUrl", url)));
      return new ImageUploadResponse(url);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      alertRepository.activate("COS_UPLOAD_FAILED", "WARN", "图片上传失败", "QUICK_SEARCH", ex.getMessage());
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "图片上传失败");
    }
  }

  private void validate(QuickSearchItemRequest request, Long id, boolean create) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请求内容不能为空");
    }
    if (create && request.contentType() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择内容类型");
    }
    if (request.contentType() != null && !List.of(ContentType.TEMPLATE, ContentType.KNOWLEDGE, ContentType.LOCATION, ContentType.IMAGE, ContentType.MINI_PROGRAM).contains(request.contentType())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "内容类型不合法");
    }
    if (create && (request.title() == null || request.title().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请填写标题");
    }
    if (request.title() != null && request.title().length() > 100) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标题不能超过 100 个字符");
    }
    if (create && (request.content() == null || request.content().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请填写正文内容");
    }
    if (request.leadType() != null && !LEAD_TYPES.contains(request.leadType().trim().toUpperCase())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "线索类型不合法");
    }
    if (create && (request.shortcutCode() == null || request.shortcutCode().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请填写快线码");
    }
    if (request.shortcutCode() != null) {
      if (!request.shortcutCode().matches("[A-Za-z0-9]{2,20}")) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "快线码必须是 2-20 位字母或数字");
      }
      if (repository.shortcutExists(request.shortcutCode(), id)) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "快线码已存在");
      }
    }
  }

  private String detectImageExt(byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
      return "jpg";
    }
    if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
      return "png";
    }
    if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
        && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) {
      return "webp";
    }
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "仅支持 jpg、png 或 webp 图片");
  }

  private int imageMaxSizeMb() {
    return configRepository.findValue("quicksearch.admin.image_max_size_mb")
        .map(value -> {
          try {
            int parsed = Integer.parseInt(value.trim());
            return Math.max(1, Math.min(50, parsed));
          } catch (NumberFormatException ex) {
            return DEFAULT_MAX_IMAGE_MB;
          }
        })
        .orElse(DEFAULT_MAX_IMAGE_MB);
  }

  private QuickSearchAdminListQuery normalizeListQuery(QuickSearchAdminListQuery query) {
    QuickSearchAdminListQuery actual = query == null
        ? new QuickSearchAdminListQuery(null, null, null, null, 1, DEFAULT_PAGE_SIZE, null, null)
        : query;
    String leadType = actual.leadType() == null || actual.leadType().isBlank() ? null : actual.leadType().trim().toUpperCase();
    if (leadType != null && !LEAD_TYPES.contains(leadType)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "线索类型不合法");
    }
    String keyword = actual.keyword() == null || actual.keyword().isBlank() ? null : actual.keyword().trim();
    int safePage = Math.max(1, actual.page());
    int requestedSize = actual.size() <= 0 ? DEFAULT_PAGE_SIZE : actual.size();
    int safeSize = Math.max(10, Math.min(requestedSize, MAX_PAGE_SIZE));
    String sortBy = actual.sortBy() == null || actual.sortBy().isBlank() ? "default" : actual.sortBy().trim();
    if (!"default".equals(sortBy) && !LIST_SORT_FIELDS.contains(sortBy)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "排序字段不合法");
    }
    String sortDir = "DESC".equalsIgnoreCase(actual.sortDir()) ? "DESC" : "ASC";
    return new QuickSearchAdminListQuery(
        actual.contentType(),
        leadType,
        actual.enabled(),
        keyword,
        safePage,
        safeSize,
        sortBy,
        sortDir);
  }

  private void publishRefresh() {
    eventPublisher.publishEvent(new ConfigChangedEvent(CONFIG_KEY));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKeys", List.of(CONFIG_KEY))));
  }

  private Map<String, Object> quickSearchDetail(QuickSearchAdminItem item) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("id", item.id());
    detail.put("contentType", item.contentType().name());
    detail.put("leadType", item.leadType());
    detail.put("title", item.title());
    detail.put("shortcutCode", item.shortcutCode());
    detail.put("sortOrder", item.sortOrder());
    detail.put("enabled", item.enabled());
    detail.put("contentLength", item.content() == null ? 0 : item.content().length());
    detail.put("hasImage", item.imageUrl() != null && !item.imageUrl().isBlank());
    return detail;
  }

  private void audit(String action, QuickSearchAdminItem item, Map<String, Object> detail) {
    auditLogger.log(action, AuthContext.username(), "template", String.valueOf(item.id()), toJson(detail));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      return "{}";
    }
  }
}
