package com.privateflow.modules.skill.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.skill.Scene;
import com.privateflow.modules.skill.SkillRequest;
import com.privateflow.modules.skill.config.SkillConfig;
import com.privateflow.modules.skill.config.SkillConfigProvider;
import com.privateflow.modules.tags.TagCandidateBuilder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SkillRequestBuilderTest {

  @Test
  void replacesConfigCenterCustomerPlaceholdersAndRoutesSkillId() {
    SkillConfigProvider configProvider = Mockito.mock(SkillConfigProvider.class);
    SkillRuntimeRouter router = Mockito.mock(SkillRuntimeRouter.class);
    when(configProvider.get()).thenReturn(new SkillConfig(
        "",
        "",
        "LAST_FOUR",
        "",
        10000,
        30,
        0.5,
        5,
        30,
        "fallback",
        "",
        "",
        "",
        "客户类型={{leadType}} 阶段={{customerStage}} {{scene}} {{red_lines}} {{available_tags}}",
        "不得承诺疗效",
        0.3,
        15,
        8000,
        3));
    when(router.route(any(), any(), any())).thenReturn(Optional.of("skill-bound"));
    SkillRequestBuilder builder = new SkillRequestBuilder(
        configProvider,
        Mockito.mock(CustomerQueryService.class),
        Mockito.mock(TagCandidateBuilder.class),
        new ObjectMapper(),
        router);

    Map<String, Object> payload = builder.build(new SkillRequest(
        Scene.ACTIVE_REPLY,
        "TUAN_GOU",
        "18800001111",
        "hello",
        Map.of("leadType", "TUAN_GOU", "customerStage", "待联系"),
        Map.of(),
        List.of(),
        List.of(),
        "keeper"));

    assertThat(payload.get("system_prompt").toString())
        .contains("客户类型=TUAN_GOU")
        .contains("阶段=待联系")
        .doesNotContain("{{");
    assertThat(payload).containsEntry("skill_id", "skill-bound").containsEntry("skill_group_id", "skill-bound");
  }
}
