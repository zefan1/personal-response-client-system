package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.tags.TagCacheService;
import com.privateflow.modules.tags.TagValue;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SkillRequestBuilderTagLocalizationTest {

  @Test
  void promptKeepsChineseDisplayNameAndStableInternalCode() {
    SkillConfigProvider configProvider = mock(SkillConfigProvider.class);
    TagCacheService tagCacheService = mock(TagCacheService.class);
    SkillRuntimeRouter runtimeRouter = mock(SkillRuntimeRouter.class);
    when(configProvider.get()).thenReturn(config());
    when(tagCacheService.getAllEnabledTags()).thenReturn(Map.of(
        "intent_level",
        List.of(tag("HIGH", "高意向"), tag("LOST", "已流失"))));
    when(runtimeRouter.route(any(), any(), any())).thenReturn(Optional.empty());
    SkillRequestBuilder builder = new SkillRequestBuilder(
        configProvider,
        mock(CustomerQueryService.class),
        tagCacheService,
        new ObjectMapper(),
        runtimeRouter);

    Map<String, Object> payload = builder.build(new SkillRequest(
        Scene.ACTIVE_REPLY,
        "PENDING",
        null,
        "测试",
        Map.of(),
        Map.of(),
        List.of(),
        List.of(),
        "admin"));

    assertThat(String.valueOf(payload.get("system_prompt")))
        .contains("高意向(HIGH)")
        .contains("已流失(LOST)");
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

  private TagValue tag(String code, String displayName) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
    return new TagValue(1L, 4L, "intent_level", code, displayName, true, 1, now, now);
  }
}
