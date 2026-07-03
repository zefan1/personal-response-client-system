package com.privateflow.modules.quicksearch.admin;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.alert.SystemAlertRepository;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.quicksearch.ContentType;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuickSearchAdminService {

  private static final String CONFIG_KEY = "quick_search";
  private static final Set<String> LEAD_TYPES = Set.of("TUAN_GOU", "XIAN_SUO", "GENERAL");
  private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
  private final QuickSearchAdminRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final SystemAlertRepository alertRepository;

  public QuickSearchAdminService(
      QuickSearchAdminRepository repository,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      SystemAlertRepository alertRepository) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.alertRepository = alertRepository;
  }

  public List<QuickSearchAdminItem> list() {
    return repository.list();
  }

  public Map<String, Object> create(QuickSearchItemRequest request) {
    validate(request, null, true);
    long id = repository.create(request, AuthContext.username());
    publishRefresh();
    return Map.of("id", id);
  }

  public QuickSearchAdminItem update(long id, QuickSearchItemRequest request) {
    QuickSearchAdminItem existing = repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "quick search item not found"));
    validate(request, id, false);
    if (existing.contentType() == ContentType.IMAGE && request.imageUrl() != null && !request.imageUrl().equals(existing.imageUrl())) {
      repository.enqueueCleanup(existing.imageUrl());
    }
    repository.update(id, request);
    publishRefresh();
    return repository.find(id).orElseThrow();
  }

  public Map<String, Object> toggle(long id) {
    repository.find(id).orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "quick search item not found"));
    boolean enabled = repository.toggle(id);
    publishRefresh();
    return Map.of("isEnabled", enabled);
  }

  public void delete(long id) {
    QuickSearchAdminItem existing = repository.find(id)
        .orElseThrow(() -> new ApiException(ApiErrorCodes.INTERNAL_ERROR, "quick search item not found"));
    if (existing.contentType() == ContentType.IMAGE) {
      repository.enqueueCleanup(existing.imageUrl());
    }
    repository.delete(id);
    publishRefresh();
  }

  public ImageUploadResponse uploadImage(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "file is required");
    }
    if (file.getSize() > MAX_IMAGE_BYTES) {
      throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "image size exceeds 10MB");
    }
    try {
      byte[] bytes = file.getBytes();
      String ext = detectImageExt(bytes);
      String url = "cos://quick-search/%s/%s.%s".formatted(LocalDate.now(), UUID.randomUUID(), ext);
      return new ImageUploadResponse(url);
    } catch (ApiException ex) {
      throw ex;
    } catch (Exception ex) {
      alertRepository.activate("COS_UPLOAD_FAILED", "WARN", "图片上传失败", "QUICK_SEARCH", ex.getMessage());
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "image upload failed");
    }
  }

  private void validate(QuickSearchItemRequest request, Long id, boolean create) {
    if (request == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "request body required");
    }
    if (create && request.contentType() == null) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "contentType is required");
    }
    if (request.contentType() != null && !List.of(ContentType.TEMPLATE, ContentType.KNOWLEDGE, ContentType.LOCATION, ContentType.IMAGE, ContentType.MINI_PROGRAM).contains(request.contentType())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "contentType invalid");
    }
    if (create && (request.title() == null || request.title().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "title is required");
    }
    if (request.title() != null && request.title().length() > 100) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "title max length is 100");
    }
    if (create && (request.content() == null || request.content().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "content is required");
    }
    if (request.leadType() != null && !LEAD_TYPES.contains(request.leadType().trim().toUpperCase())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "leadType invalid");
    }
    if (create && (request.shortcutCode() == null || request.shortcutCode().isBlank())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "shortcutCode is required");
    }
    if (request.shortcutCode() != null) {
      if (!request.shortcutCode().matches("[A-Za-z0-9]{2,20}")) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "shortcutCode must be 2-20 letters or digits");
      }
      if (repository.shortcutExists(request.shortcutCode(), id)) {
        throw new ApiException(ApiErrorCodes.CONFIG_INVALID, "shortcutCode already exists");
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
    throw new ApiException(ApiErrorCodes.BAD_REQUEST, "image type unsupported");
  }

  private void publishRefresh() {
    eventPublisher.publishEvent(new ConfigChangedEvent(CONFIG_KEY));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKeys", List.of(CONFIG_KEY))));
  }
}
