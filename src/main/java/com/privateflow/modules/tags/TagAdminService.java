package com.privateflow.modules.tags;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.common.events.ProfileUpdatedEvent;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.ws.WsMessage;
import com.privateflow.modules.api.ws.WsPushService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class TagAdminService {

  private final TagRepository repository;
  private final TagMergeRepository mergeRepository;
  private final TagRuleReferenceService ruleReferenceService;
  private final ApplicationEventPublisher eventPublisher;
  private final WsPushService wsPushService;
  private final AuditLogger auditLogger;
  private final TagConfigProvider configProvider;
  private final ObjectMapper objectMapper;

  public TagAdminService(
      TagRepository repository,
      TagMergeRepository mergeRepository,
      TagRuleReferenceService ruleReferenceService,
      ApplicationEventPublisher eventPublisher,
      WsPushService wsPushService,
      AuditLogger auditLogger,
      TagConfigProvider configProvider,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.mergeRepository = mergeRepository;
    this.ruleReferenceService = ruleReferenceService;
    this.eventPublisher = eventPublisher;
    this.wsPushService = wsPushService;
    this.auditLogger = auditLogger;
    this.configProvider = configProvider;
    this.objectMapper = objectMapper;
  }

  public Map<String, Object> list() {
    List<TagCategory> categories = enrichCategories(repository.listTree());
    return Map.of("items", categories, "categories", categories);
  }

  public TagCategoryPage searchCategories(
      String keyword,
      Boolean enabled,
      Boolean merged,
      TagSelectionMode selectionMode,
      Boolean builtin,
      int page,
      int size,
      String sortBy,
      String sortDirection) {
    TagCategoryPage result = repository.searchCategories(
        keyword, enabled, merged, selectionMode, builtin, page, size, sortBy, sortDirection);
    return new TagCategoryPage(
        result.page(), result.size(), result.total(), result.totalPages(), enrichCategories(result.items()));
  }

  public TagCategory categoryDetail(long id) {
    TagCategory category = requireCategory(id);
    List<TagValue> values = enrichValues(category.values());
    TagCategory enriched = category.withValues(values);
    return enriched.withImpact(categoryImpact(enriched));
  }

  public TagValuePage searchValues(
      Long categoryId,
      String keyword,
      Boolean enabled,
      Boolean merged,
      Boolean systemSelectable,
      Boolean manualSelectable,
      int page,
      int size,
      String sortBy,
      String sortDirection) {
    if (categoryId != null) {
      requireCategory(categoryId);
    }
    TagValuePage result = repository.searchValues(
        categoryId, keyword, enabled, merged, systemSelectable, manualSelectable,
        page, size, sortBy, sortDirection);
    return new TagValuePage(
        result.page(), result.size(), result.total(), result.totalPages(), enrichValues(result.items()));
  }

  public TagValue valueDetail(long id) {
    return enrichValues(List.of(requireValue(id))).get(0);
  }

  @Transactional
  public TagCategory createCategory(TagCategoryRequest request) {
    validateCategoryCreate(request);
    String key = generateCategoryKey(request.categoryName());
    int sortOrder = request.sortOrder() == null ? repository.listTree().size() + 1 : request.sortOrder();
    try {
      long id = repository.createCategory(key, request, sortOrder);
      publishAfterCommit("TAG_CATEGORY_CREATE", "category " + request.categoryName(), false);
      return categoryDetail(id);
    } catch (DuplicateKeyException ex) {
      throw new ApiException(TagErrorCodes.CATEGORY_EXISTS, "同名或同编号的标签分类已经存在，请刷新后重试");
    }
  }

  @Transactional
  public TagCategory updateCategory(long id, TagCategoryRequest request) {
    TagCategory existing = requireEditableCategory(id);
    TagCategoryRequest safeRequest = request == null ? new TagCategoryRequest(null, null, null, null) : request;
    requireExpectedVersion(safeRequest.version());
    if (safeRequest.boundField() != null && !safeRequest.boundField().isBlank()
        && !Objects.equals(existing.boundField(), safeRequest.boundField().trim())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "已绑定的客户档案字段不能修改");
    }
    if (safeRequest.selectionMode() != null && safeRequest.selectionMode() != existing.selectionMode()
        && categoryImpact(existing).hasReferences()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "该分类已有客户、规则或历史记录，不能修改单选/多选模式");
    }
    validateCategorySettings(safeRequest, false);
    if (repository.updateCategory(id, safeRequest) == 0) {
      throw versionConflict();
    }
    publishAfterCommit("TAG_CATEGORY_UPDATE", "category " + id, false);
    return categoryDetail(id);
  }

  @Transactional
  public TagCategory toggleCategory(long id, boolean enabled, Integer version) {
    requireEditableCategory(id);
    requireExpectedVersion(version);
    if (repository.toggleCategory(id, enabled, version) == 0) {
      throw versionConflict();
    }
    publishAfterCommit("TAG_CATEGORY_TOGGLE", "category " + id + " enabled=" + enabled, false);
    return categoryDetail(id);
  }

  @Transactional
  public void deleteCategory(long id) {
    TagCategory existing = requireCategory(id);
    if (existing.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGED_ITEM_READ_ONLY, "已合并分类用于保留历史编号，不能删除");
    }
    if (existing.isBuiltin()) {
      throw new ApiException(TagErrorCodes.BUILTIN_CATEGORY_DELETE_FORBIDDEN, "内置标签分类不能删除，可以停用或合并");
    }
    if (!existing.values().isEmpty()) {
      throw new ApiException(TagErrorCodes.CATEGORY_HAS_VALUES, "该分类还有 " + existing.values().size() + " 个标签，请先处理这些标签");
    }
    TagImpact impact = categoryImpact(existing);
    if (impact.hasReferences()) {
      throw new ApiException(TagErrorCodes.CATEGORY_IN_USE,
          "该分类仍影响 " + impact.customerCount() + " 位客户、" + impact.ruleCount() + " 条规则和 "
              + impact.historyCount() + " 条历史记录，只能停用或合并");
    }
    repository.deleteCategory(id);
    publishAfterCommit("TAG_CATEGORY_DELETE", "category " + existing.categoryName(), false);
  }

  @Transactional
  public TagValue createValue(TagValueRequest request) {
    validateValueCreate(request);
    TagCategory category = requireActiveCategoryForValueCreation(request.categoryId());
    int maxValues = configProvider.get().valueMaxPerCategory();
    if (repository.valueCount(category.id()) >= maxValues) {
      throw new ApiException(TagErrorCodes.VALUE_LIMIT_EXCEEDED, "每个分类最多只能创建 " + maxValues + " 个标签");
    }
    String tagValue = generateTagValue(category.id(), request.displayName());
    int sortOrder = request.sortOrder() == null ? repository.valueCount(category.id()) + 1 : request.sortOrder();
    try {
      long id = repository.createValue(tagValue, request, sortOrder);
      if (id == 0L) {
        throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签分类已停用或已合并，不能创建标签值");
      }
      publishAfterCommit("TAG_VALUE_CREATE", "value " + tagValue, false);
      return valueDetail(id);
    } catch (DuplicateKeyException ex) {
      throw new ApiException(TagErrorCodes.VALUE_EXISTS, "该分类中同编号的标签已经存在，请刷新后重试");
    }
  }

  @Transactional
  public TagValue updateValue(long id, TagValueRequest request) {
    TagValue existing = requireEditableValue(id);
    TagValueRequest safeRequest = request == null ? new TagValueRequest(null, null, null, null, null) : request;
    requireExpectedVersion(safeRequest.version());
    if (safeRequest.categoryId() != null && safeRequest.categoryId() != existing.categoryId()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签所属分类不能直接修改，请使用合并功能");
    }
    validateValueSettings(safeRequest, false);
    if (repository.updateValue(id, safeRequest) == 0) {
      throw versionConflict();
    }
    publishAfterCommit("TAG_VALUE_UPDATE", "value " + id, false);
    return valueDetail(id);
  }

  @Transactional
  public TagValue toggleValue(long id, boolean enabled, Integer version) {
    requireEditableValue(id);
    requireExpectedVersion(version);
    if (repository.toggleValue(id, enabled, version) == 0) {
      throw versionConflict();
    }
    publishAfterCommit("TAG_VALUE_TOGGLE", "value " + id + " enabled=" + enabled, false);
    return valueDetail(id);
  }

  @Transactional
  public void deleteValue(long id) {
    TagValue value = requireValue(id);
    if (value.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGED_ITEM_READ_ONLY, "已合并标签用于保留历史编号，不能删除");
    }
    TagImpact impact = valueImpact(value);
    if (impact.hasReferences()) {
      throw new ApiException(TagErrorCodes.VALUE_IN_USE,
          "该标签仍影响 " + impact.customerCount() + " 位客户、" + impact.ruleCount() + " 条规则和 "
              + impact.historyCount() + " 条历史记录，只能停用或合并");
    }
    repository.deleteValue(id);
    publishAfterCommit("TAG_VALUE_DELETE", "value " + value.tagValue(), false);
  }

  public TagMergePreview previewCategoryMerge(long sourceId, TagMergeRequest request) {
    TagCategory source = requireCategory(sourceId);
    TagCategory target = requireMergeTargetCategory(source, request);
    TagImpact impact = categoryImpact(source);
    int conflicts = (int) source.values().stream()
        .filter(value -> repository.findValueByCategoryAndCode(target.id(), value.tagValue()).isPresent())
        .count();
    List<String> warnings = mergeWarnings(impact);
    if (source.boundField() != null && target.boundField() == null) {
      warnings.add("合并时会把历史客户字段绑定转移到目标分类，并继续同步该兼容字段");
    } else if (source.boundField() == null && target.boundField() != null) {
      warnings.add("合并后的有效标签会同步到目标分类绑定的历史客户字段");
    } else if (!Objects.equals(source.boundField(), target.boundField())) {
      warnings.add("合并后的有效标签会写入目标历史字段，并清空受影响客户的源历史字段");
    }
    if (conflicts > 0) {
      warnings.add("有 " + conflicts + " 个同编号标签会合并到目标分类已有标签");
    }
    return new TagMergePreview(
        "CATEGORY", source.id(), target.id(), source.categoryKey(), source.categoryName(),
        target.categoryKey(), target.categoryName(), impact, source.values().size(), conflicts, List.copyOf(warnings));
  }

  @Transactional
  public TagCategory mergeCategory(long sourceId, TagMergeRequest request) {
    lockAndValidateCategoryVersions(sourceId, request);
    TagCategory initialSource = requireCategory(sourceId);
    TagCategory initialTarget = requireMergeTargetCategory(initialSource, request);
    TagImpact impact = categoryImpact(initialSource);
    CategoryMergePair pair = prepareCategoryMerge(initialSource, initialTarget);
    TagCategory source = pair.source();
    TagCategory target = pair.target();
    Map<Long, TagValue> targetBySourceValue = new LinkedHashMap<>();
    Set<String> affectedPhones = new LinkedHashSet<>();
    for (TagValue sourceValue : source.values()) {
      TagValue targetValue = repository.findValueByCategoryAndCode(target.id(), sourceValue.tagValue())
          .map(this::resolveFinalValue)
          .orElseGet(() -> {
            long cloneId = mergeRepository.cloneValue(sourceValue.id(), target.id());
            return requireValue(cloneId);
          });
      if (targetValue.categoryId() != target.id()) {
        throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "目标分类中的同编号标签已经合并到其他分类，请先处理该标签");
      }
      mergeRepository.ensureTargetAvailability(sourceValue, targetValue);
      affectedPhones.addAll(mergeRepository.mergeValueReferences(
          sourceValue,
          source,
          targetValue,
          target,
          "标签分类合并到「" + target.categoryName() + "」"));
      mergeRepository.saveLegacyMappings(sourceValue, source, targetValue, target);
      mergeRepository.markValueMerged(sourceValue.id(), targetValue.id());
      targetBySourceValue.put(sourceValue.id(), targetValue);
    }
    mergeRepository.mergeCategoryOnlyReferences(source, target);
    int changedRules = ruleReferenceService.rewriteCategory(source, target, targetBySourceValue);
    mergeRepository.saveCategoryMapping(source, target);
    mergeRepository.markCategoryMerged(source.id(), target.id());
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("sourceName", source.categoryName());
    detail.put("targetName", target.categoryName());
    detail.put("valueMappings", targetBySourceValue.entrySet().stream()
        .map(entry -> Map.of("sourceValueId", entry.getKey(), "targetValueId", entry.getValue().id()))
        .toList());
    detail.put("changedRuleCount", changedRules);
    mergeRepository.recordOperation(
        "CATEGORY", source.id(), target.id(), source.categoryKey(), target.categoryKey(),
        impact, json(detail), AuthContext.username());
    publishAfterCommit("TAG_CATEGORY_MERGE", json(detail), changedRules > 0, affectedPhones);
    return categoryDetail(target.id());
  }

  public TagMergePreview previewValueMerge(long sourceId, TagMergeRequest request) {
    TagValue source = requireValue(sourceId);
    TagValue target = requireMergeTargetValue(source, request);
    TagImpact impact = valueImpact(source);
    return new TagMergePreview(
        "VALUE", source.id(), target.id(), source.tagValue(), source.displayName(),
        target.tagValue(), target.displayName(), impact, 0, 0, List.copyOf(mergeWarnings(impact)));
  }

  @Transactional
  public TagValue mergeValue(long sourceId, TagMergeRequest request) {
    lockAndValidateValueVersions(sourceId, request);
    TagValue source = requireValue(sourceId);
    TagValue target = requireMergeTargetValue(source, request);
    TagCategory category = requireCategory(source.categoryId());
    TagImpact impact = valueImpact(source);
    Set<String> affectedPhones = mergeRepository.mergeValueReferences(
        source, category, target, category, "标签合并到「" + target.displayName() + "」");
    int changedRules = ruleReferenceService.rewriteValue(source, category, target, category);
    mergeRepository.saveLegacyMappings(source, category, target, category);
    mergeRepository.markValueMerged(source.id(), target.id());
    Map<String, Object> detail = Map.of(
        "sourceName", source.displayName(),
        "targetName", target.displayName(),
        "changedRuleCount", changedRules);
    mergeRepository.recordOperation(
        "VALUE", source.id(), target.id(), source.tagValue(), target.tagValue(),
        impact, json(detail), AuthContext.username());
    publishAfterCommit("TAG_VALUE_MERGE", json(detail), changedRules > 0, affectedPhones);
    return valueDetail(target.id());
  }

  public byte[] exportCategories(
      String keyword,
      Boolean enabled,
      Boolean merged,
      TagSelectionMode selectionMode,
      Boolean builtin,
      String sortBy,
      String sortDirection) {
    List<TagCategory> categories = enrichCategories(repository.findCategoriesForExport(
        keyword, enabled, merged, selectionMode, builtin, sortBy, sortDirection));
    StringBuilder csv = new StringBuilder("\uFEFF分类系统编号,中文名称,分类用途,单选多选,系统判断,员工手动,启用状态,显示顺序,标签数量,受影响客户,受影响规则,历史记录,合并目标编号,创建时间,更新时间\r\n");
    for (TagCategory category : categories) {
      appendCsvRow(csv, List.of(
          category.categoryKey(), category.categoryName(), category.purpose(),
          category.selectionMode() == TagSelectionMode.SINGLE ? "单选" : "多选",
          yesNo(category.systemInferenceEnabled()), yesNo(category.manualEditEnabled()), yesNo(category.isEnabled()),
          category.sortOrder(), category.values().size(), category.impact().customerCount(),
          category.impact().ruleCount(), category.impact().historyCount(),
          category.mergedIntoId() == null ? "" : category.mergedIntoId(),
          category.createdAt(), category.updatedAt()));
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  public byte[] exportValues(
      Long categoryId,
      String keyword,
      Boolean enabled,
      Boolean merged,
      Boolean systemSelectable,
      Boolean manualSelectable,
      String sortBy,
      String sortDirection) {
    List<TagValue> values = enrichValues(repository.findValuesForExport(
        categoryId, keyword, enabled, merged, systemSelectable, manualSelectable, sortBy, sortDirection));
    StringBuilder csv = new StringBuilder("\uFEFF分类系统编号,标签系统编号,中文名称,标签含义,适用条件,禁止条件,正确例子,错误例子,同义说法,系统可选,员工可选,启用状态,显示顺序,受影响客户,受影响规则,历史记录,合并目标编号,创建时间,更新时间\r\n");
    for (TagValue value : values) {
      appendCsvRow(csv, List.of(
          value.categoryKey(), value.tagValue(), value.displayName(), value.meaning(),
          value.applicableWhen(), value.notApplicableWhen(), value.positiveExamples(), value.negativeExamples(),
          String.join("、", value.synonyms()), yesNo(value.systemSelectable()), yesNo(value.manualSelectable()),
          yesNo(value.isEnabled()), value.sortOrder(), value.impact().customerCount(), value.impact().ruleCount(),
          value.impact().historyCount(), value.mergedIntoId() == null ? "" : value.mergedIntoId(),
          value.createdAt(), value.updatedAt()));
    }
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  private List<TagCategory> enrichCategories(List<TagCategory> categories) {
    if (categories.isEmpty()) {
      return List.of();
    }
    List<TagValue> values = categories.stream().flatMap(category -> category.values().stream()).toList();
    TagRuleReferenceService.ReferenceCounts counts = ruleReferenceService.countReferences(categories, values);
    return categories.stream()
        .map(category -> category.withImpact(
            repository.categoryImpact(category.id()).withRuleCount(counts.category(category.id()))))
        .toList();
  }

  private List<TagValue> enrichValues(List<TagValue> values) {
    if (values.isEmpty()) {
      return List.of();
    }
    Map<Long, TagCategory> categories = new LinkedHashMap<>();
    values.forEach(value -> categories.computeIfAbsent(value.categoryId(), this::requireCategory));
    TagRuleReferenceService.ReferenceCounts counts = ruleReferenceService.countReferences(
        List.copyOf(categories.values()), values);
    return values.stream()
        .map(value -> value.withImpact(repository.valueImpact(value)
            .withRuleCount(counts.value(value.id()))))
        .toList();
  }

  private TagImpact categoryImpact(TagCategory category) {
    TagRuleReferenceService.ReferenceCounts counts = ruleReferenceService.countReferences(
        List.of(category), category.values());
    return repository.categoryImpact(category.id()).withRuleCount(counts.category(category.id()));
  }

  private TagImpact valueImpact(TagValue value) {
    TagCategory category = requireCategory(value.categoryId());
    TagRuleReferenceService.ReferenceCounts counts = ruleReferenceService.countReferences(List.of(category), List.of(value));
    return repository.valueImpact(value).withRuleCount(counts.value(value.id()));
  }

  private TagCategory requireMergeTargetCategory(TagCategory source, TagMergeRequest request) {
    if (request == null || request.targetId() == null || request.targetId() == source.id()) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "请选择其他有效分类作为合并目标");
    }
    TagCategory target = requireCategory(request.targetId());
    validateExpectedVersion(source.version(), request.sourceVersion());
    validateExpectedVersion(target.version(), request.targetVersion());
    if (source.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGED_ITEM_READ_ONLY, "源分类已经合并，不能重复操作");
    }
    if (target.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "目标分类已经合并，请选择最终有效分类");
    }
    if (!target.isEnabled()) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "目标分类必须处于启用状态");
    }
    if (source.selectionMode() != target.selectionMode()) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "单选分类和多选分类不能互相合并");
    }
    return target;
  }

  private TagValue requireMergeTargetValue(TagValue source, TagMergeRequest request) {
    if (request == null || request.targetId() == null || request.targetId() == source.id()) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "请选择同一分类中的其他有效标签作为合并目标");
    }
    TagValue target = requireValue(request.targetId());
    validateExpectedVersion(source.version(), request.sourceVersion());
    validateExpectedVersion(target.version(), request.targetVersion());
    if (source.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGED_ITEM_READ_ONLY, "源标签已经合并，不能重复操作");
    }
    if (target.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "目标标签已经合并，请选择最终有效标签");
    }
    if (source.categoryId() != target.categoryId()) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "标签只能合并到同一分类中的目标标签");
    }
    if (!target.isEnabled()) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "目标标签必须处于启用状态");
    }
    return target;
  }

  private void lockAndValidateCategoryVersions(long sourceId, TagMergeRequest request) {
    if (request == null || request.targetId() == null) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "请选择合并目标分类");
    }
    long first = Math.min(sourceId, request.targetId());
    long second = Math.max(sourceId, request.targetId());
    Integer firstVersion = mergeRepository.lockCategory(first)
        .orElseThrow(() -> new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除"));
    Integer secondVersion = first == second ? firstVersion : mergeRepository.lockCategory(second)
        .orElseThrow(() -> new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除"));
    validateExpectedVersion(sourceId == first ? firstVersion : secondVersion, request.sourceVersion());
    validateExpectedVersion(request.targetId() == first ? firstVersion : secondVersion, request.targetVersion());
  }

  private void lockAndValidateValueVersions(long sourceId, TagMergeRequest request) {
    if (request == null || request.targetId() == null) {
      throw new ApiException(TagErrorCodes.MERGE_NOT_ALLOWED, "请选择合并目标标签");
    }
    long first = Math.min(sourceId, request.targetId());
    long second = Math.max(sourceId, request.targetId());
    Integer firstVersion = mergeRepository.lockValue(first)
        .orElseThrow(() -> new ApiException(TagErrorCodes.VALUE_NOT_FOUND, "标签不存在或已被删除"));
    Integer secondVersion = first == second ? firstVersion : mergeRepository.lockValue(second)
        .orElseThrow(() -> new ApiException(TagErrorCodes.VALUE_NOT_FOUND, "标签不存在或已被删除"));
    validateExpectedVersion(sourceId == first ? firstVersion : secondVersion, request.sourceVersion());
    validateExpectedVersion(request.targetId() == first ? firstVersion : secondVersion, request.targetVersion());
  }

  private TagValue resolveFinalValue(TagValue value) {
    TagValue current = value;
    Set<Long> visited = new LinkedHashSet<>();
    while (current.mergedIntoId() != null) {
      if (!visited.add(current.id()) || visited.size() > 100) {
        throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "标签合并关系存在循环，请联系管理员检查数据");
      }
      current = requireValue(current.mergedIntoId());
    }
    return current;
  }

  private CategoryMergePair prepareCategoryMerge(TagCategory source, TagCategory target) {
    if (source.boundField() == null || target.boundField() != null) {
      return new CategoryMergePair(source, target);
    }
    if (mergeRepository.transferBoundField(source, target) != 2) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "迁移标签分类绑定字段失败，请稍后重试");
    }
    return new CategoryMergePair(requireCategory(source.id()), requireCategory(target.id()));
  }

  private List<String> mergeWarnings(TagImpact impact) {
    List<String> warnings = new ArrayList<>();
    if (impact.customerCount() > 0) {
      warnings.add("将更新 " + impact.customerCount() + " 位客户的标签引用");
    }
    if (impact.ruleCount() > 0) {
      warnings.add("将结构化更新 " + impact.ruleCount() + " 条跟进规则中的标签引用");
    }
    if (impact.historyCount() > 0) {
      warnings.add("将更新 " + impact.historyCount() + " 条分析、建议或旧值映射记录");
    }
    if (warnings.isEmpty()) {
      warnings.add("当前没有客户、规则或历史记录引用源项");
    }
    return warnings;
  }

  private void validateCategoryCreate(TagCategoryRequest request) {
    validateCategorySettings(request, true);
    if (request.boundField() != null && !request.boundField().isBlank()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "新标签分类无需绑定客户档案字段，请直接使用统一标签记录");
    }
  }

  private void validateValueCreate(TagValueRequest request) {
    if (request == null || request.categoryId() == null) {
      throw new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除");
    }
    validateValueSettings(request, true);
  }

  private void validateCategorySettings(TagCategoryRequest request, boolean create) {
    if (request == null || create && request.categoryName() == null) {
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
    if (request == null || create && request.displayName() == null) {
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

  private TagCategory requireCategory(Long id) {
    if (id == null) {
      throw new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除");
    }
    return repository.findCategory(id)
        .orElseThrow(() -> new ApiException(TagErrorCodes.CATEGORY_NOT_FOUND, "标签分类不存在或已被删除"));
  }

  private TagCategory requireEditableCategory(Long id) {
    TagCategory category = requireCategory(id);
    if (category.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGED_ITEM_READ_ONLY, "已合并分类只能查看历史信息，不能继续修改");
    }
    return category;
  }

  private TagCategory requireActiveCategoryForValueCreation(Long id) {
    TagCategory category = requireEditableCategory(id);
    if (!category.isEnabled()) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "标签分类已停用，不能创建标签值");
    }
    return category;
  }

  private TagValue requireValue(long id) {
    return repository.findValue(id)
        .orElseThrow(() -> new ApiException(TagErrorCodes.VALUE_NOT_FOUND, "标签不存在或已被删除"));
  }

  private TagValue requireEditableValue(long id) {
    TagValue value = requireValue(id);
    if (value.mergedIntoId() != null) {
      throw new ApiException(TagErrorCodes.MERGED_ITEM_READ_ONLY, "已合并标签只能查看历史信息，不能继续修改");
    }
    requireEditableCategory(value.categoryId());
    return value;
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

  private void validateExpectedVersion(int actual, Integer expected) {
    requireExpectedVersion(expected);
    if (expected != actual) {
      throw versionConflict();
    }
  }

  private void requireExpectedVersion(Integer expected) {
    if (expected == null) {
      throw new ApiException(TagErrorCodes.VERSION_REQUIRED, "缺少数据版本，请刷新后重试");
    }
  }

  private ApiException versionConflict() {
    return new ApiException(TagErrorCodes.VERSION_CONFLICT, "数据已被其他人修改，请刷新后重试");
  }

  private String json(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "生成标签操作记录失败，请稍后重试");
    }
  }

  private String yesNo(boolean value) {
    return value ? "是" : "否";
  }

  private void appendCsvRow(StringBuilder csv, List<?> values) {
    for (int index = 0; index < values.size(); index++) {
      if (index > 0) {
        csv.append(',');
      }
      csv.append(csvCell(values.get(index)));
    }
    csv.append("\r\n");
  }

  private String csvCell(Object value) {
    String raw = value == null ? "" : String.valueOf(value);
    if (!raw.isEmpty() && "=+-@".indexOf(raw.charAt(0)) >= 0) {
      raw = "'" + raw;
    }
    return '"' + raw.replace("\"", "\"\"") + '"';
  }

  private void publishAfterCommit(String source, String detail, boolean rulesChanged) {
    publishAfterCommit(source, detail, rulesChanged, Set.of());
  }

  private void publishAfterCommit(
      String source,
      String detail,
      boolean rulesChanged,
      Set<String> affectedPhones) {
    auditLogger.log("UPDATE_TAG", AuthContext.username(), "tag_config", source, detail);
    Runnable publish = () -> {
      eventPublisher.publishEvent(new ConfigChangedEvent("tag_config"));
      if (rulesChanged) {
        eventPublisher.publishEvent(new ConfigChangedEvent("rules.tag_merge"));
      }
      wsPushService.broadcastWs(WsMessage.unsaved(
          "CONFIG_REFRESH",
          Map.of("configKeys", rulesChanged ? List.of("tag_config", "rules.tag_merge") : List.of("tag_config"), "source", source)));
      affectedPhones.forEach(phone -> eventPublisher.publishEvent(new ProfileUpdatedEvent(phone)));
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          publish.run();
        }
      });
    } else {
      publish.run();
    }
  }

  private record CategoryMergePair(TagCategory source, TagCategory target) {
  }
}
