package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileExtractRequest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import com.privateflow.modules.tags.TagAutoUpdateMode;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagDirectorySnapshot;
import com.privateflow.modules.tags.TagImpact;
import com.privateflow.modules.tags.TagSelectionMode;
import com.privateflow.modules.tags.TagSelectionValidator;
import com.privateflow.modules.tags.TagUncertainPolicy;
import com.privateflow.modules.tags.TagValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagAnalysisDecisionValidatorTest {

  private TagAnalysisDecisionValidator validator;

  @BeforeEach
  void setUp() {
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(
            category(1L, "custom_goal", TagSelectionMode.MULTI, TagAutoUpdateMode.ADD_ONLY, List.of(
                value(11L, 1L, "custom_goal", "GOAL_A", true),
                value(12L, 1L, "custom_goal", "GOAL_B", true))),
            category(2L, "intent_level", TagSelectionMode.SINGLE, TagAutoUpdateMode.REPLACE, List.of(
                value(21L, 2L, "intent_level", "HIGH", true),
                value(22L, 2L, "intent_level", "DISABLED", false)))),
        Instant.parse("2026-07-15T05:00:00Z")));
    TagCandidateBuilder candidateBuilder = new TagCandidateBuilder(directoryService);
    validator = new TagAnalysisDecisionValidator(new TagSelectionValidator(directoryService, candidateBuilder));
  }

  @Test
  void acceptsValidNewMultiValueAndNoChangeResults() {
    ProfileAnalysisResult result = new ProfileAnalysisResult(
        ProfileUpdates.empty(),
        List.of(
            decision("custom_goal", List.of("GOAL_B"), "0.95", "客户明确表达目标", TagAnalysisResultType.UPDATE, TagAnalysisAction.ADD),
            decision("intent_level", List.of(), "0.30", "信息不足", TagAnalysisResultType.UNABLE_TO_DETERMINE, TagAnalysisAction.NONE)));

    ProfileAnalysisResult validated = validator.validate(result, request());

    assertThat(validated.tagDecisions()).hasSize(2);
  }

  @Test
  void rejectsExistingMultiValueDictionaryAndEvidenceViolations() {
    assertRejected(decision("custom_goal", List.of("GOAL_A"), "0.95", "客户明确表达目标", TagAnalysisResultType.UPDATE, TagAnalysisAction.ADD));
    assertRejected(decision("custom_goal", List.of("UNKNOWN"), "0.95", "客户明确表达目标", TagAnalysisResultType.UPDATE, TagAnalysisAction.ADD));
    assertRejected(decision("intent_level", List.of("DISABLED"), "0.95", "客户明确表示预约", TagAnalysisResultType.UPDATE, TagAnalysisAction.REPLACE));
    assertRejected(decision("intent_level", List.of("GOAL_B"), "0.95", "客户明确表示预约", TagAnalysisResultType.UPDATE, TagAnalysisAction.REPLACE));
    assertRejected(decision("intent_level", List.of("HIGH"), "0.95", "", TagAnalysisResultType.UPDATE, TagAnalysisAction.REPLACE));
    assertRejected(decision("intent_level", List.of("HIGH"), "0.50", "客户明确表示预约", TagAnalysisResultType.UPDATE, TagAnalysisAction.REPLACE));
  }

  @Test
  void rejectsIllegalActionOrValuesForNoChangeResults() {
    assertRejected(decision("intent_level", List.of("HIGH"), "0.30", "保持当前", TagAnalysisResultType.KEEP_CURRENT, TagAnalysisAction.NONE));
    assertRejected(decision("intent_level", List.of(), "0.30", "保持当前", TagAnalysisResultType.KEEP_CURRENT, TagAnalysisAction.ADD));
    assertRejected(decision("intent_level", List.of("HIGH"), "0.95", "客户明确表示预约", TagAnalysisResultType.UPDATE, TagAnalysisAction.ADD));
  }

  @Test
  void rejectsMultipleDecisionsForTheSameCategory() {
    TagAnalysisDecision first = decision(
        "custom_goal", List.of("GOAL_B"), "0.95", "客户明确表达目标", TagAnalysisResultType.UPDATE, TagAnalysisAction.ADD);
    TagAnalysisDecision second = decision(
        "custom_goal", List.of("GOAL_B"), "0.96", "客户再次确认目标", TagAnalysisResultType.UPDATE, TagAnalysisAction.ADD);

    assertThatThrownBy(() -> validator.validate(
        new ProfileAnalysisResult(ProfileUpdates.empty(), List.of(first, second)),
        request()))
        .isInstanceOf(SkillGatewayException.class)
        .extracting(ex -> ((SkillGatewayException) ex).getErrorCode())
        .isEqualTo(SkillErrorCodes.SKILL_RESPONSE_INVALID);
  }

  private void assertRejected(TagAnalysisDecision decision) {
    assertThatThrownBy(() -> validator.validate(
        new ProfileAnalysisResult(ProfileUpdates.empty(), List.of(decision)),
        request()))
        .isInstanceOf(SkillGatewayException.class)
        .extracting(ex -> ((SkillGatewayException) ex).getErrorCode())
        .isEqualTo(SkillErrorCodes.SKILL_RESPONSE_INVALID);
  }

  private ProfileExtractRequest request() {
    return new ProfileExtractRequest(
        "客户对话",
        Map.of("leadType", "TUAN_GOU"),
        List.of(),
        "keeper-1",
        new ProfileAnalysisContext(
            7L,
            4,
            2,
            List.of(),
            Map.of("leadType", "TUAN_GOU"),
            List.of(new ProfileAnalysisContext.CurrentTag(
                "custom_goal", "自定义目标", "GOAL_A", "目标 A", "MANUAL")),
            List.of(),
            List.of(
                candidate("custom_goal", "MULTI", "ADD_ONLY", List.of("GOAL_A", "GOAL_B")),
                candidate("intent_level", "SINGLE", "REPLACE", List.of("HIGH", "DISABLED")))));
  }

  private ProfileAnalysisContext.CategoryCandidate candidate(
      String code,
      String mode,
      String updateMode,
      List<String> values) {
    return new ProfileAnalysisContext.CategoryCandidate(
        code,
        code,
        "用途",
        mode,
        updateMode,
        new BigDecimal("0.9000"),
        1,
        0,
        "KEEP_CURRENT",
        values.stream().map(value -> new ProfileAnalysisContext.TagCandidate(
            value, value, "含义", "适用", "禁止", "正例", "反例", List.of())).toList());
  }

  private TagAnalysisDecision decision(
      String categoryCode,
      List<String> tagCodes,
      String confidence,
      String evidence,
      TagAnalysisResultType resultType,
      TagAnalysisAction action) {
    return new TagAnalysisDecision(
        categoryCode,
        tagCodes,
        new BigDecimal(confidence),
        evidence,
        resultType,
        action);
  }

  private TagCategory category(
      long id,
      String code,
      TagSelectionMode mode,
      TagAutoUpdateMode updateMode,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new TagCategory(
        id, code, code, "用途", null, mode, true, true, updateMode,
        new BigDecimal("0.9000"), 1, 0, TagUncertainPolicy.KEEP_CURRENT,
        true, true, true, true, false, true, 1, null, 1,
        values, TagImpact.empty(), now, now);
  }

  private TagValue value(
      long id,
      long categoryId,
      String categoryCode,
      String code,
      boolean enabled) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new TagValue(
        id, categoryId, categoryCode, code, code, "含义", "适用", "禁止", "正例", "反例",
        List.of(), true, true, enabled, 1, null, 1, TagImpact.empty(), now, now);
  }
}
