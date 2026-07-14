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
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TagAdminService {

  private static final Set<String> SYSTEM_FIELDS = Set.of("class", "id", "version", "createdAt", "updatedAt", "syncedAt", "sourceRowId");
  private final TagRepository repository;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final AuditLogger auditLogger;
  private final TagConfigProvider configProvider;

  public TagAdminService(
      TagRepository repository,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      AuditLogger auditLogger,
      TagConfigProvider configProvider) {
    this.repository = repository;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.auditLogger = auditLogger;
    this.configProvider = configProvider;
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
    if (safeRequest.boundField() != null && !safeRequest.boundField().isBlank()
        && !Objects.equals(existing.boundField(), safeRequest.boundField().trim())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "已绑定的客户档案字段不能修改");
    }
    validateCategorySettings(safeRequest, false);
    repository.updateCategory(id, safeRequest);
    publish("TAG_CATEGORY_UPDATE", "category " + id);
    return repository.findCategory(id).orElseThrow();
  }

  @Transactional
  public void deleteCategory(long id) {
    TagCategory existing = requireCategory(id);
    if (existing.isBuiltin()) {
      throw new ApiException(TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN, "内置标签分类不能删除，可以停用");
    }
    if (!existing.values().isEmpty()) {
      throw new ApiException(TagErrorCodes.CATEGORY_HAS_VALUES, "该分类还有 " + existing.values().size() + " 个标签值，请先处理标签值");
    }
    repository.deleteCategory(id);
    publish("TAG_CATEGORY_DELETE", "category " + existing.categoryName());
  }

  @Transactional
  public TagValue createValue(TagValueRequest request) {
    validateValueCreate(request);
    TagCategory category = requireCategory(request.categoryId());
    int maxValues = configProvider.get().valueMaxPerCategory();
    if (repository.valueCount(category.id()) >= maxValues) {
      throw new ApiException(TagErrorCodes.VALUE_LIMIT_EXCEEDED, "每个分类最多只能创建 " + maxValues + " 个标签值");
    }
    String tagValue = generateTagValue(category.id(), request.displayName());
    int sortOrder = request.sortOrder() == null ? repository.valueCount(category.id()) + 1 : request.sortOrder();
    long id = repository.createValue(tagValue, request, sortOrder);
    publish("TAG_VALUE_CREATE", "value " + tagValue);
    return repository.findValue(id).orElseThrow();
  }

  @Transactional
  public TagValue updateValue(long id, TagValueRequest request) {
    requireValue(id);
    TagValueRequest safeRequest = request == null ? new TagValueRequest(null, null, null, null, null) : request;
    validateValueSettings(safeRequest, false);
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
    int usage = repository.usageCount(value.id(), category.boundField(), category.selectionMode(), value.tagValue());
    if (usage > 0) {
      throw new ApiException(TagErrorCodes.VALUE_IN_USE, "该标签已有 " + usage + " 条客户或历史记录引用，请改为停用");
    }
    repository.deleteValue(id);
    publish("TAG_VALUE_DELETE", "value " + value.tagValue());
  }

  private void validateCategoryCreate(TagCategoryRequest request) {
    validateCategorySettings(request, true);
    if (request.boundField() != null && !request.boundField().isBlank()) {
      String boundField = request.boundField().trim();
      if (!customerFieldExists(boundField)) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请选择有效的客户档案字段");
      }
      if (repository.boundFieldExists(boundField)) {
        throw new ApiException(TagErrorCodes.CATEGORY_EXISTS, "该客户档案字段已经绑定了标签分类");
      }
    }
  }

  private void validateValueCreate(TagValueRequest request) {
    if (request == null || request.categoryId() == null) {
      throw new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除");
    }
    validateValueSettings(request, true);
  }

  private void validateCategorySettings(TagCategoryRequest request, boolean create) {
    if (request == null || (create && request.categoryName() == null)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "分类名称长度必须为 1-100 个字符");
    }
    if (request.categoryName() != null && (request.categoryName().isBlank() || request.categoryName().trim().length() > 100)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "分类名称长度必须为 1-100 个字符");
    }
    validateOptionalText(request.purpose(), 500, "分类用途不能超过 500 个字符");
    BigDecimal confidence = request.minConfidence();
    if (confidence != null && (confidence.compareTo(BigDecimal.ZERO) < 0 || confidence.compareTo(BigDecimal.ONE) > 0)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "最低把握程度必须在 0-1 之间");
    }
    if (request.minEvidenceMessages() != null && (request.minEvidenceMessages() < 0 || request.minEvidenceMessages() > 1000)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "最低有效消息数必须在 0-1000 之间");
    }
    if (request.cooldownHours() != null && (request.cooldownHours() < 0 || request.cooldownHours() > 87600)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "自动更新冷却时间必须在 0-87600 小时之间");
    }
  }

  private void validateValueSettings(TagValueRequest request, boolean create) {
    if (request == null || (create && request.displayName() == null)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签展示名称长度必须为 1-100 个字符");
    }
    if (request.displayName() != null && (request.displayName().isBlank() || request.displayName().trim().length() > 100)) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签展示名称长度必须为 1-100 个字符");
    }
    validateOptionalText(request.meaning(), 500, "标签含义不能超过 500 个字符");
    validateOptionalText(request.applicableWhen(), 1000, "标签适用条件不能超过 1000 个字符");
    validateOptionalText(request.notApplicableWhen(), 1000, "标签禁止条件不能超过 1000 个字符");
    validateOptionalText(request.positiveExamples(), 1000, "标签正确例子不能超过 1000 个字符");
    validateOptionalText(request.negativeExamples(), 1000, "标签错误例子不能超过 1000 个字符");
    if (request.synonyms() != null) {
      if (request.synonyms().size() > 50
          || request.synonyms().stream().anyMatch(value -> value == null || value.isBlank() || value.length() > 100)) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签同义表达最多 50 个，每个长度必须为 1-100 个字符");
      }
      int totalLength = request.synonyms().stream().mapToInt(String::length).sum();
      if (totalLength > 1500) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签同义表达总长度不能超过 1500 个字符");
      }
    }
  }

  private void validateOptionalText(String value, int maxLength, String message) {
    if (value != null && value.trim().length() > maxLength) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, message);
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
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "客户档案字段校验失败，请稍后重试");
    }
  }

  private TagCategory requireCategory(Long id) {
    if (id == null) {
      throw new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除");
    }
    return repository.findCategory(id)
        .orElseThrow(() -> new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除"));
  }

  private TagValue requireValue(long id) {
    return repository.findValue(id)
        .orElseThrow(() -> new ApiException(TagErrorCodes.VALUE_NOT_FOUND, "标签值不存在或已被删除"));
  }

  private String generateCategoryKey(String categoryName) {
    String base = categoryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    if (base.isBlank() || "_".equals(base)) {
      base = "tag";
    }
    base = base.replaceAll("^_+|_+$", "");
    base = base.substring(0, Math.min(base.length(), 32));
    for (int i = 0; i < 20; i++) {
      String candidate = base + "_" + randomSuffix();
      if (!repository.categoryKeyExists(candidate)) {
        return candidate;
      }
    }
    throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "生成标签分类系统编号失败，请重试");
  }

  private String generateTagValue(long categoryId, String displayName) {
    String base = displayName.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
    if (base.isBlank() || "_".equals(base)) {
      base = "TAG";
    }
    base = base.replaceAll("^_+|_+$", "");
    base = base.substring(0, Math.min(base.length(), 32));
    for (int i = 0; i < 20; i++) {
      String candidate = base + "_" + randomSuffix().toUpperCase(Locale.ROOT);
      if (!repository.valueExists(categoryId, candidate)) {
        return candidate;
      }
    }
    throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "生成标签系统编号失败，请重试");
  }

  private String randomSuffix() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }

  private void publish(String source, String detail) {
    auditLogger.log("UPDATE_TAG", AuthContext.username(), "tag_config", source, detail);
    eventPublisher.publishEvent(new ConfigChangedEvent("tag_config"));
    wsPushService.broadcastWs(WsMessage.unsaved("CONFIG_REFRESH", Map.of("configKey", "tag_config", "source", source)));
  }
}
