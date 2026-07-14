package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class TagCandidateBuilderTest {

  @Test
  void rejectsNullPurposeExplicitly() {
    assertThatThrownBy(() -> builder().build(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("标签候选用途不能为空");
  }

  @Test
  void systemInferenceAppliesCategoryValueAndCommonStateSwitches() {
    TagCategory allowed = category(
        1L, "system_allowed", true, true, true, true, true, true, true, null,
        List.of(
            value(11L, 1L, "system_allowed", "VISIBLE", true, true, true, null),
            value(12L, 1L, "system_allowed", "SYSTEM_OFF", false, true, true, null),
            value(13L, 1L, "system_allowed", "DISABLED", true, true, false, null),
            value(14L, 1L, "system_allowed", "MERGED", true, true, true, 99L)));
    TagCategory categorySwitchOff = category(
        2L, "system_off", false, true, true, true, true, true, true, null,
        List.of(value(21L, 2L, "system_off", "VALUE", true, true, true, null)));
    TagCategory disabled = category(
        3L, "disabled", true, true, true, true, true, true, false, null,
        List.of(value(31L, 3L, "disabled", "VALUE", true, true, true, null)));
    TagCategory merged = category(
        4L, "merged", true, true, true, true, true, true, true, 1L,
        List.of(value(41L, 4L, "merged", "VALUE", true, true, true, null)));

    List<TagCategory> candidates = builder(allowed, categorySwitchOff, disabled, merged)
        .build(TagCandidatePurpose.SYSTEM_INFERENCE);

    assertThat(candidates).extracting(TagCategory::categoryKey).containsExactly("system_allowed");
    assertThat(candidates.get(0).values()).extracting(TagValue::tagValue).containsExactly("VISIBLE");
  }

  @ParameterizedTest
  @EnumSource(value = TagCandidatePurpose.class, names = {"MANUAL_ASSIGNMENT", "IMPORT"})
  void manualAndImportApplyCategoryAndValueManualSwitches(TagCandidatePurpose purpose) {
    TagCategory allowed = category(
        1L, "manual_allowed", false, true, true, true, true, true, true, null,
        List.of(
            value(11L, 1L, "manual_allowed", "VISIBLE", false, true, true, null),
            value(12L, 1L, "manual_allowed", "MANUAL_OFF", true, false, true, null)));
    TagCategory categorySwitchOff = category(
        2L, "manual_off", true, false, true, true, true, true, true, null,
        List.of(value(21L, 2L, "manual_off", "VALUE", true, true, true, null)));

    List<TagCategory> candidates = builder(allowed, categorySwitchOff).build(purpose);

    assertThat(candidates).extracting(TagCategory::categoryKey).containsExactly("manual_allowed");
    assertThat(candidates.get(0).values()).extracting(TagValue::tagValue).containsExactly("VISIBLE");
  }

  @ParameterizedTest
  @MethodSource("operationalPurposes")
  void operationalPurposesUseTheirCategorySwitchAndKeepCompleteMetadata(
      TagCandidatePurpose purpose,
      String allowedKey) {
    TagCategory followup = category(
        1L, "followup", false, false, false, false, false, true, true, null,
        List.of(value(11L, 1L, "followup", "FOLLOWUP", false, false, true, null)));
    TagCategory reply = category(
        2L, "reply", false, false, true, false, false, false, true, null,
        List.of(value(21L, 2L, "reply", "REPLY", false, false, true, null)));
    TagCategory filter = category(
        3L, "filter", false, false, false, true, false, false, true, null,
        List.of(value(31L, 3L, "filter", "FILTER", false, false, true, null)));
    TagCategory statistics = category(
        4L, "statistics", false, false, false, false, true, false, true, null,
        List.of(value(41L, 4L, "statistics", "STATISTICS", false, false, true, null)));

    List<TagCategory> candidates = builder(followup, reply, filter, statistics).build(purpose);

    assertThat(candidates).singleElement().satisfies(category -> {
      assertThat(category.categoryKey()).isEqualTo(allowedKey);
      assertThat(category.purpose()).isEqualTo("完整分类用途");
      assertThat(category.values()).singleElement().satisfies(value -> {
        assertThat(value.meaning()).isEqualTo("完整标签含义");
        assertThat(value.systemSelectable()).isFalse();
        assertThat(value.manualSelectable()).isFalse();
      });
    });
  }

  private static Stream<Arguments> operationalPurposes() {
    return Stream.of(
        Arguments.of(TagCandidatePurpose.FOLLOWUP_RULE, "followup"),
        Arguments.of(TagCandidatePurpose.REPLY, "reply"),
        Arguments.of(TagCandidatePurpose.FILTER, "filter"),
        Arguments.of(TagCandidatePurpose.STATISTICS, "statistics"));
  }

  private TagCandidateBuilder builder(TagCategory... categories) {
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(categories),
        Instant.parse("2026-07-14T02:00:00Z")));
    return new TagCandidateBuilder(directoryService);
  }

  private TagCategory category(
      long id,
      String key,
      boolean systemInferenceEnabled,
      boolean manualEditEnabled,
      boolean useForReply,
      boolean useForFilter,
      boolean useForStatistics,
      boolean useForFollowupRules,
      boolean enabled,
      Long mergedIntoId,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagCategory(
        id, key, "动态分类名", "完整分类用途", null, TagSelectionMode.SINGLE,
        systemInferenceEnabled, manualEditEnabled, TagAutoUpdateMode.RECORD_ONLY,
        new BigDecimal("0.8500"), 1, 0, TagUncertainPolicy.KEEP_CURRENT,
        useForReply, useForFilter, useForStatistics, useForFollowupRules,
        false, enabled, 1, mergedIntoId, 0, values, TagImpact.empty(), now, now);
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
        id, categoryId, categoryKey, code, "动态标签名", "完整标签含义", "适用", "不适用",
        "正例", "反例", List.of("同义词"), systemSelectable, manualSelectable,
        enabled, 1, mergedIntoId, 0, TagImpact.empty(), now, now);
  }
}
