package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.tags.TagAutoUpdateMode;
import com.privateflow.modules.tags.TagCandidateBuilder;
import com.privateflow.modules.tags.TagCategory;
import com.privateflow.modules.tags.TagDirectoryService;
import com.privateflow.modules.tags.TagDirectorySnapshot;
import com.privateflow.modules.tags.TagImpact;
import com.privateflow.modules.tags.TagSelectionMode;
import com.privateflow.modules.tags.TagUncertainPolicy;
import com.privateflow.modules.tags.TagValue;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillRequestBuilderTagLocalizationTest {

  @Test
  void promptUsesDatabaseCategoryNameChineseValueNameAndStableCodeOnlyForSystemCandidates() {
    TagCategory allowed = category(
        1L,
        "intent_level",
        "数据库动态意向等级",
        true,
        List.of(
            value(11L, 1L, "intent_level", "HIGH", "高意向", true),
            value(12L, 1L, "intent_level", "SYSTEM_BLOCKED", "系统禁止值", false)));
    TagCategory categoryBlocked = category(
        2L,
        "internal_only",
        "禁止分类",
        false,
        List.of(value(21L, 2L, "internal_only", "HIDDEN", "隐藏值", true)));
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(allowed, categoryBlocked),
        Instant.parse("2026-07-14T02:00:00Z")));

    Map<String, Object> payload = builder(new TagCandidateBuilder(directoryService)).build(request());

    assertThat(String.valueOf(payload.get("system_prompt")))
        .contains("数据库动态意向等级")
        .contains("高意向(HIGH)")
        .doesNotContain("SYSTEM_BLOCKED")
        .doesNotContain("系统禁止值")
        .doesNotContain("禁止分类")
        .doesNotContain("隐藏值")
        .doesNotContain("内部标签含义");
  }

  @Test
  void propagatesCandidateDirectoryFailures() {
    TagCandidateBuilder candidateBuilder = mock(TagCandidateBuilder.class);
    when(candidateBuilder.build(any())).thenThrow(new IllegalStateException("tag directory failed"));

    assertThatThrownBy(() -> builder(candidateBuilder).build(request()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("tag directory failed");
  }

  private SkillRequestBuilder builder(TagCandidateBuilder candidateBuilder) {
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    SkillRuntimeRouter runtimeRouter = mock(SkillRuntimeRouter.class);
    when(configProvider.get()).thenReturn(config());
    when(runtimeRouter.route(any(), any(), any())).thenReturn(Optional.empty());
    return new SkillRequestBuilder(
        configProvider,
        mock(CustomerQueryService.class),
        candidateBuilder,
        new ObjectMapper(),
        runtimeRouter);
  }

  private SkillRequest request() {
    return new SkillRequest(
        Scene.ACTIVE_REPLY,
        "PENDING",
        null,
        "测试",
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        "admin");
  }

  private SkillConfig config() {
    return new SkillConfig(
        "http://localhost",
        "key",
        "LAST_FOUR",
        "",
        1000,
        30,
        0.5,
        5,
        30,
        "fallback",
        "tuan",
        "xiansuo",
        "default",
        "{{available_tags}}\n{{red_lines}}\n{{scene}}",
        "不得夸大效果",
        0.3,
        15,
        8000,
        3);
  }

  private TagCategory category(
      long id,
      String key,
      String categoryName,
      boolean systemInferenceEnabled,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagCategory(
        id, key, categoryName, "内部分类用途", null, TagSelectionMode.SINGLE,
        systemInferenceEnabled, true, TagAutoUpdateMode.RECORD_ONLY,
        new BigDecimal("0.8500"), 1, 0, TagUncertainPolicy.KEEP_CURRENT,
        true, true, true, true, false, true, 1, null, 0,
        values, TagImpact.empty(), now, now);
  }

  private TagValue value(
      long id,
      long categoryId,
      String categoryKey,
      String code,
      String displayName,
      boolean systemSelectable) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagValue(
        id, categoryId, categoryKey, code, displayName, "内部标签含义", "适用", "不适用",
        "正例", "反例", List.of("同义词"), systemSelectable, true,
        true, 1, null, 0, TagImpact.empty(), now, now);
  }
}
