package com.privateflow.modules.tags;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import com.privateflow.modules.customer.Customer;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagAdminService {

  private static final int VALUE_MAX_PER_CATEGORY = 50;
  private static final Pattern TAG_VALUE = Pattern.compile("^[A-Z0-9_]{1,50}$");
  private static final Set<String> SYSTEM_FIELDS = Set.of("class", "id", "version", "createdAt", "updatedAt", "syncedAt", "sourceRowId");
  private final TagRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final AuditLogger auditLogger;
  private final Random random = new Random();

  public TagAdminService(
      TagRepository repository,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      AuditLogger auditLogger) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.auditLogger = auditLogger;
  }

  public Map<String, Object> list() {
    return Map.of("categories", repository.listTree());
  }

  @Transactional
  public TagCategory createCategory(TagCategoryRequest request) {
    validateCategoryCreate(request);
    String key = generateCategoryKey(request.categoryName());
    int sortOrder = request.sortOrder() == null ? repository.listTree().size() + 1 : request.sortOrder();
    long id = repository.createCategory(key, request, sortOrder);
    publish("TAG_CATEGORY_CREATE", "category " + request.categoryName());
    return repository.findCategory(id).orElseThrow();
  }

  @Transactional
  public TagCategory updateCategory(long id, TagCategoryRequest request) {
    TagCategory existing = requireCategory(id);
    TagCategoryRequest safeRequest = request == null ? new TagCategoryRequest(null, null, null, null) : request;
    if (safeRequest.boundField() != null && !safeRequest.boundField().isBlank() && !existing.boundField().equals(safeRequest.boundField())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "boundField cannot be changed");
    }
    if (safeRequest.categoryName() != null && (safeRequest.categoryName().isBlank() || safeRequest.categoryName().trim().length() > 30)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "categoryName length is 1-30");
    }
    repository.updateCategory(id, safeRequest);
    publish("TAG_CATEGORY_UPDATE", "category " + id);
    return repository.findCategory(id).orElseThrow();
  }

  @Transactional
  public void deleteCategory(long id) {
    TagCategory existing = requireCategory(id);
    if (existing.isBuiltin()) {
      throw new ApiException(TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN, "builtin category cannot be deleted");
    }
    if (!existing.values().isEmpty()) {
      throw new ApiException(TagErrorCodes.CATEGORY_HAS_VALUES, "category has " + existing.values().size() + " values");
    }
    repository.deleteCategory(id);
    publish("TAG_CATEGORY_DELETE", "category " + existing.categoryName());
  }

  @Transactional
  public TagValue createValue(TagValueRequest request) {
    validateValueCreate(request);
    TagCategory category = requireCategory(request.categoryId());
    if (repository.valueCount(category.id()) >= VALUE_MAX_PER_CATEGORY) {
      throw new ApiException(TagErrorCodes.VALUE_LIMIT_EXCEEDED, "tag value count limit exceeded");
    }
    if (repository.valueExists(category.id(), request.tagValue().trim())) {
      throw new ApiException(TagErrorCodes.VALUE_EXISTS, "tagValue already exists in category");
    }
    int sortOrder = request.sortOrder() == null ? repository.valueCount(category.id()) + 1 : request.sortOrder();
    long id = repository.createValue(request, sortOrder);
    publish("TAG_VALUE_CREATE", "value " + request.tagValue());
    return repository.findValue(id).orElseThrow();
  }

  @Transactional
  public TagValue updateValue(long id, TagValueRequest request) {
    requireValue(id);
    TagValueRequest safeRequest = request == null ? new TagValueRequest(null, null, null, null, null) : request;
    if (safeRequest.displayName() != null && (safeRequest.displayName().isBlank() || safeRequest.displayName().trim().length() > 30)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "displayName length is 1-30");
    }
    repository.updateValue(id, safeRequest);
    publish("TAG_VALUE_UPDATE", "value " + id);
    return repository.findValue(id).orElseThrow();
  }

  @Transactional
  public TagValue toggleValue(long id, boolean enabled) {
    requireValue(id);
    repository.toggleValue(id, enabled);
    publish("TAG_VALUE_TOGGLE", "value " + id + " enabled=" + enabled);
    return repository.findValue(id).orElseThrow();
  }

  @Transactional
  public void deleteValue(long id) {
    TagValue value = requireValue(id);
    TagCategory category = requireCategory(value.categoryId());
    int usage = repository.usageCount(category.boundField(), value.tagValue());
    if (usage > 0) {
      throw new ApiException(TagErrorCodes.VALUE_IN_USE, "tag value is used by " + usage + " customers");
    }
    repository.deleteValue(id);
    publish("TAG_VALUE_DELETE", "value " + value.tagValue());
  }

  private void validateCategoryCreate(TagCategoryRequest request) {
    if (request == null || request.categoryName() == null || request.categoryName().isBlank() || request.categoryName().trim().length() > 30) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "categoryName length is 1-30");
    }
    if (request.boundField() == null || request.boundField().isBlank() || !customerFieldExists(request.boundField().trim())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "boundField invalid");
    }
    if (repository.boundFieldExists(request.boundField().trim())) {
      throw new ApiException(TagErrorCodes.CATEGORY_EXISTS, "boundField already bound");
    }
  }

  private void validateValueCreate(TagValueRequest request) {
    if (request == null || request.categoryId() == null) {
      throw new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "category not found");
    }
    if (request.tagValue() == null || !TAG_VALUE.matcher(request.tagValue().trim()).matches()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "tagValue must match [A-Z0-9_]{1,50}");
    }
    if (request.displayName() == null || request.displayName().isBlank() || request.displayName().trim().length() > 30) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "displayName length is 1-30");
    }
  }

  private boolean customerFieldExists(String field) {
    try {
      for (PropertyDescriptor descriptor : Introspector.getBeanInfo(Customer.class).getPropertyDescriptors()) {
        if (descriptor.getWriteMethod() != null && !SYSTEM_FIELDS.contains(descriptor.getName()) && descriptor.getName().equals(field)) {
          return true;
        }
      }
      return false;
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "customer field validation failed");
    }
  }

  private TagCategory requireCategory(Long id) {
    if (id == null) {
      throw new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "category not found");
    }
    return repository.findCategory(id)
        .orElseThrow(() -> new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "category not found"));
  }

  private TagValue requireValue(long id) {
    return repository.findValue(id)
        .orElseThrow(() -> new ApiException(TagErrorCodes.VALUE_NOT_FOUND, "tag value not found"));
  }

  private String generateCategoryKey(String categoryName) {
    String base = categoryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    if (base.isBlank() || "_".equals(base)) {
      base = "tag";
    }
    base = base.replaceAll("^_+|_+$", "");
    for (int i = 0; i < 20; i++) {
      String candidate = base + "_" + Integer.toString(random.nextInt(46656), 36);
      if (!repository.categoryKeyExists(candidate)) {
        return candidate;
      }
    }
    return base + "_" + System.currentTimeMillis();
  }

  private void publish(String source, String detail) {
    auditLogger.log("UPDATE_TAG", AuthContext.username(), "tag_config", source, detail);
    eventPublisher.publishEvent(new ConfigChangedEvent("tag_config"));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", "tag_config", "source", source)));
  }
}
