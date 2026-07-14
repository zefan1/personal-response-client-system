package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TagSelectionValidatorTest {

  @Test
  void acceptsStableCodesAndReturnsResolvedMetadata() {
    TagCategory category = category(
        1L, "intent_level", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 2,
        List.of(value(11L, 1L, "intent_level", "HIGH", true, true, true, null)));
    TagSelectionValidator validator = validator(category);

    TagSelectionValidationResult result = validator.validateCodes(
        TagCandidatePurpose.SYSTEM_INFERENCE,
        "intent_level",
        List.of("HIGH"),
        new TagSelectionContext("客户明确表示本周到店", 2, new BigDecimal("0.9000"), null));

    assertThat(result.accepted()).isTrue();
    assertThat(result.reasonCode()).isEqualTo("ACCEPTED");
    assertThat(result.message()).isEqualTo("标签选择校验通过");
    assertThat(result.category()).isEqualTo(category);
    assertThat(result.values()).extracting(TagValue::tagValue).containsExactly("HIGH");
  }

  @Test
  void acceptsIdInput() {
    TagCategory category = category(
        1L, "manual", TagSelectionMode.SINGLE, false, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "manual", "VALUE", false, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT,
        1L,
        List.of(11L),
        TagSelectionContext.empty());

    assertThat(result.accepted()).isTrue();
    assertThat(result.category().categoryKey()).isEqualTo("manual");
    assertThat(result.values()).extracting(TagValue::id).containsExactly(11L);
  }

  @Test
  void rejectsMissingCategory() {
    TagSelectionValidationResult result = validator().validateCodes(
        TagCandidatePurpose.MANUAL_ASSIGNMENT,
        "missing",
        List.of("VALUE"),
        TagSelectionContext.empty());

    assertRejected(result, "CATEGORY_NOT_FOUND", "标签分类不存在");
  }

  @Test
  void rejectsDisabledCategory() {
    TagCategory category = category(
        1L, "disabled", TagSelectionMode.SINGLE, true, true, false, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "disabled", "VALUE", true, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(11L), TagSelectionContext.empty());

    assertRejected(result, "CATEGORY_DISABLED", "标签分类已停用");
  }

  @Test
  void rejectsMergedCategory() {
    TagCategory category = category(
        1L, "merged", TagSelectionMode.SINGLE, true, true, true, 2L,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "merged", "VALUE", true, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(11L), TagSelectionContext.empty());

    assertRejected(result, "CATEGORY_MERGED", "标签分类已合并");
  }

  @Test
  void rejectsMissingValue() {
    TagCategory category = category(
        1L, "intent", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1, List.of());

    TagSelectionValidationResult result = validator(category).validateCodes(
        TagCandidatePurpose.MANUAL_ASSIGNMENT,
        "intent",
        List.of("MISSING"),
        TagSelectionContext.empty());

    assertRejected(result, "VALUE_NOT_FOUND", "标签值不存在");
  }

  @Test
  void rejectsNullValueAsStructuredMissingValue() {
    TagCategory category = category(
        1L, "intent", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1, List.of());

    TagSelectionValidationResult result = validator(category).validateCodes(
        TagCandidatePurpose.MANUAL_ASSIGNMENT,
        "intent",
        Arrays.asList((String) null),
        TagSelectionContext.empty());

    assertRejected(result, "VALUE_NOT_FOUND", "标签值不存在");
  }

  @Test
  void rejectsDisabledValue() {
    TagCategory category = category(
        1L, "intent", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "intent", "DISABLED", true, true, false, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(11L), TagSelectionContext.empty());

    assertRejected(result, "VALUE_DISABLED", "标签值已停用");
  }

  @Test
  void rejectsMergedValue() {
    TagCategory category = category(
        1L, "intent", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "intent", "MERGED", true, true, true, 12L)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(11L), TagSelectionContext.empty());

    assertRejected(result, "VALUE_MERGED", "标签值已合并");
  }

  @Test
  void rejectsValueIdFromAnotherCategory() {
    TagCategory selected = category(
        1L, "selected", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "selected", "OWN", true, true, true, null)));
    TagCategory other = category(
        2L, "other", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(21L, 2L, "other", "OTHER", true, true, true, null)));

    TagSelectionValidationResult result = validator(selected, other).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(21L), TagSelectionContext.empty());

    assertRejected(result, "VALUE_CATEGORY_MISMATCH", "标签值不属于所选分类");
  }

  @Test
  void rejectsValueCodeThatExistsOnlyInAnotherCategoryAsCategoryMismatch() {
    TagCategory selected = category(
        1L, "selected", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1, List.of());
    TagCategory other = category(
        2L, "other", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(21L, 2L, "other", "SHARED_CODE", true, true, true, null)));

    TagSelectionValidationResult result = validator(selected, other).validateCodes(
        TagCandidatePurpose.MANUAL_ASSIGNMENT,
        "selected",
        List.of("SHARED_CODE"),
        TagSelectionContext.empty());

    assertRejected(result, "VALUE_CATEGORY_MISMATCH", "标签值不属于所选分类");
    assertThat(result.values()).extracting(TagValue::id).containsExactly(21L);
  }

  @Test
  void rejectsPurposeNotAllowedByCategoryOrValue() {
    TagCategory category = category(
        1L, "system_off", TagSelectionMode.SINGLE, false, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "system_off", "VALUE", true, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateCodes(
        TagCandidatePurpose.SYSTEM_INFERENCE,
        "system_off",
        List.of("VALUE"),
        new TagSelectionContext("证据", 1, new BigDecimal("0.9000"), null));

    assertRejected(result, "PURPOSE_NOT_ALLOWED", "当前来源或用途不允许选择该标签");
  }

  @Test
  void singleCategoryRequiresExactlyOneValue() {
    TagCategory category = category(
        1L, "single", TagSelectionMode.SINGLE, false, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(
            value(11L, 1L, "single", "ONE", false, true, true, null),
            value(12L, 1L, "single", "TWO", false, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(11L, 12L), TagSelectionContext.empty());

    assertRejected(result, "SINGLE_VALUE_COUNT_INVALID", "单选分类必须且只能选择一个标签值");
  }

  @Test
  void multiCategoryRequiresAtLeastOneValue() {
    TagCategory category = category(
        1L, "multi", TagSelectionMode.MULTI, false, true, true, null,
        new BigDecimal("0.8000"), 1, List.of());

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(), TagSelectionContext.empty());

    assertRejected(result, "MULTI_VALUE_REQUIRED", "多选分类至少需要选择一个标签值");
  }

  @Test
  void rejectsDuplicateValuesBeforeTheyCanMaskSelectionCount() {
    TagCategory category = category(
        1L, "multi", TagSelectionMode.MULTI, false, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "multi", "ONE", false, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        TagCandidatePurpose.MANUAL_ASSIGNMENT, 1L, List.of(11L, 11L), TagSelectionContext.empty());

    assertRejected(result, "DUPLICATE_VALUES", "标签值不能重复");
  }

  @Test
  void systemInferenceRequiresEvidence() {
    TagSelectionValidationResult result = validateSystem(new TagSelectionContext(
        " ", 2, new BigDecimal("0.9000"), null));

    assertRejected(result, "EVIDENCE_REQUIRED", "系统推断必须提供非空证据");
  }

  @Test
  void systemInferenceRequiresEnoughValidMessages() {
    TagSelectionValidationResult result = validateSystem(new TagSelectionContext(
        "明确证据", 1, new BigDecimal("0.9000"), null));

    assertRejected(result, "EVIDENCE_MESSAGES_INSUFFICIENT", "有效证据消息数未达到分类要求");
  }

  @Test
  void systemInferenceRequiresConfidence() {
    TagSelectionValidationResult result = validateSystem(new TagSelectionContext(
        "明确证据", 2, null, null));

    assertRejected(result, "CONFIDENCE_REQUIRED", "系统推断必须提供置信度");
  }

  @Test
  void systemInferenceRequiresMinimumConfidence() {
    TagSelectionValidationResult result = validateSystem(new TagSelectionContext(
        "明确证据", 2, new BigDecimal("0.7900"), null));

    assertRejected(result, "CONFIDENCE_TOO_LOW", "系统推断置信度未达到分类阈值");
  }

  @ParameterizedTest
  @EnumSource(value = TagCandidatePurpose.class, names = {"IMPORT", "FOLLOWUP_RULE"})
  void importAndFollowupRuleRequireBusinessBasis(TagCandidatePurpose purpose) {
    TagCategory category = category(
        1L, "basis", TagSelectionMode.SINGLE, false, true, true, null,
        new BigDecimal("0.8000"), 1,
        List.of(value(11L, 1L, "basis", "VALUE", false, true, true, null)));

    TagSelectionValidationResult result = validator(category).validateIds(
        purpose,
        1L,
        List.of(11L),
        new TagSelectionContext(null, 0, null, " "));

    assertRejected(result, "BUSINESS_BASIS_REQUIRED", "当前来源必须提供非空业务依据");
  }

  private TagSelectionValidationResult validateSystem(TagSelectionContext context) {
    TagCategory category = category(
        1L, "intent", TagSelectionMode.SINGLE, true, true, true, null,
        new BigDecimal("0.8000"), 2,
        List.of(value(11L, 1L, "intent", "HIGH", true, true, true, null)));
    return validator(category).validateCodes(
        TagCandidatePurpose.SYSTEM_INFERENCE, "intent", List.of("HIGH"), context);
  }

  private TagSelectionValidator validator(TagCategory... categories) {
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(categories),
        Instant.parse("2026-07-14T02:00:00Z")));
    return new TagSelectionValidator(directoryService, new TagCandidateBuilder(directoryService));
  }

  private void assertRejected(TagSelectionValidationResult result, String reasonCode, String message) {
    assertThat(result.accepted()).isFalse();
    assertThat(result.reasonCode()).isEqualTo(reasonCode);
    assertThat(result.message()).isEqualTo(message);
  }

  private TagCategory category(
      long id,
      String key,
      TagSelectionMode selectionMode,
      boolean systemInferenceEnabled,
      boolean manualEditEnabled,
      boolean enabled,
      Long mergedIntoId,
      BigDecimal minConfidence,
      int minEvidenceMessages,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagCategory(
        id, key, "动态分类名", "分类用途", null, selectionMode,
        systemInferenceEnabled, manualEditEnabled, TagAutoUpdateMode.RECORD_ONLY,
        minConfidence, minEvidenceMessages, 0, TagUncertainPolicy.KEEP_CURRENT,
        true, true, true, true, false, enabled, 1, mergedIntoId, 0,
        values, TagImpact.empty(), now, now);
  }

  private TagValue value(
      long id,
      long categoryId,
      String categoryKey,
      String code,
      boolean systemSelectable,
      boolean manualSelectable,
      boolean enabled,
      Long mergedIntoId) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagValue(
        id, categoryId, categoryKey, code, "动态标签名", "标签含义", "适用", "不适用",
        "正例", "反例", List.of("同义词"), systemSelectable, manualSelectable,
        enabled, 1, mergedIntoId, 0, TagImpact.empty(), now, now);
  }
}
