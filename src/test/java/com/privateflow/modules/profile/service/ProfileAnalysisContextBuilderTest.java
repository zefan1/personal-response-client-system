package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.skill.ProfileAnalysisContext;
import com.privateflow.modules.tags.CustomerTagCategoryLock;
import com.privateflow.modules.tags.CustomerTagFoundationRepository;
import com.privateflow.modules.tags.CustomerTagQueryDto;
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
import org.junit.jupiter.api.Test;

class ProfileAnalysisContextBuilderTest {

  @Test
  void buildsSanitizedDynamicContextAndExcludesLockedCategoriesFromCandidates() {
    TagCategory dynamic = category(
        1L,
        "custom_goal",
        "自定义目标",
        TagSelectionMode.MULTI,
        TagAutoUpdateMode.ADD_ONLY,
        List.of(value(11L, 1L, "custom_goal", "GOAL_A", "目标 A")));
    TagCategory locked = category(
        2L,
        "intent_level",
        "意向等级",
        TagSelectionMode.SINGLE,
        TagAutoUpdateMode.REPLACE,
        List.of(value(21L, 2L, "intent_level", "HIGH", "高意向")));
    TagDirectoryService directoryService = mock(TagDirectoryService.class);
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(dynamic, locked),
        Instant.parse("2026-07-15T04:00:00Z")));
    CustomerTagFoundationRepository repository = mock(CustomerTagFoundationRepository.class);
    CustomerTagQueryDto current = mock(CustomerTagQueryDto.class);
    when(current.categoryKey()).thenReturn("custom_goal");
    when(current.categoryName()).thenReturn("自定义目标");
    when(current.tagValue()).thenReturn("GOAL_A");
    when(current.tagDisplayName()).thenReturn("目标 A");
    when(current.sourceType()).thenReturn("MANUAL");
    when(repository.findCurrentTagDetails(7L)).thenReturn(List.of(current));
    when(repository.findCategoryLocks(7L)).thenReturn(List.of(new CustomerTagCategoryLock(
        31L,
        7L,
        2L,
        true,
        "keeper-1",
        "人工修改",
        LocalDateTime.of(2026, 7, 15, 11, 0),
        null,
        null,
        1,
        LocalDateTime.of(2026, 7, 15, 11, 0),
        LocalDateTime.of(2026, 7, 15, 11, 0))));
    ProfileAnalysisContextBuilder builder = new ProfileAnalysisContextBuilder(
        new TagCandidateBuilder(directoryService),
        directoryService,
        repository);

    ProfileAnalysisContext context = builder.build(
        customer(),
        Map.of(
            "phone", "18800001111",
            "assignedKeeper", "keeper-1",
            "nickname", "Alice",
            "customerStage", "待跟进"),
        List.of(
            new CustomerMessageSentEvent.ChatMessage("client", "客户明确表达目标", "12:00"),
            new CustomerMessageSentEvent.ChatMessage("keeper", "员工说明方案", "12:01"),
            new CustomerMessageSentEvent.ChatMessage("system", "内部系统消息", "12:02")));

    assertThat(context.customerId()).isEqualTo(7L);
    assertThat(context.customerVersion()).isEqualTo(4);
    assertThat(context.effectiveMessageCount()).isEqualTo(1);
    assertThat(context.customerProfile())
        .containsEntry("nickname", "Alice")
        .containsEntry("phoneLast4", "1111")
        .doesNotContainKeys("phone", "assignedKeeper");
    assertThat(context.recentMessages()).extracting(ProfileAnalysisContext.ConversationMessage::role)
        .containsExactly("client", "keeper");
    assertThat(context.currentTags()).singleElement().satisfies(tag -> {
      assertThat(tag.categoryCode()).isEqualTo("custom_goal");
      assertThat(tag.tagCode()).isEqualTo("GOAL_A");
      assertThat(tag.sourceType()).isEqualTo("MANUAL");
    });
    assertThat(context.lockedCategories()).singleElement().satisfies(category ->
        assertThat(category.categoryCode()).isEqualTo("intent_level"));
    assertThat(context.candidateCategories()).singleElement().satisfies(category -> {
      assertThat(category.categoryCode()).isEqualTo("custom_goal");
      assertThat(category.purpose()).isEqualTo("动态分类用途");
      assertThat(category.selectionMode()).isEqualTo("MULTI");
      assertThat(category.autoUpdateMode()).isEqualTo("ADD_ONLY");
      assertThat(category.minConfidence()).isEqualByComparingTo("0.9100");
      assertThat(category.minEvidenceMessages()).isEqualTo(2);
      assertThat(category.cooldownHours()).isEqualTo(12);
      assertThat(category.uncertainPolicy()).isEqualTo("KEEP_CURRENT");
      assertThat(category.values()).singleElement().satisfies(tag -> {
        assertThat(tag.tagCode()).isEqualTo("GOAL_A");
        assertThat(tag.meaning()).isEqualTo("动态标签含义");
        assertThat(tag.applicableWhen()).isEqualTo("明确表达目标时");
        assertThat(tag.notApplicableWhen()).isEqualTo("信息不足时");
        assertThat(tag.positiveExamples()).isEqualTo("我想改善核心力量");
        assertThat(tag.negativeExamples()).isEqualTo("我再看看");
        assertThat(tag.synonyms()).containsExactly("核心目标");
      });
    });
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setId(7L);
    customer.setPhone("18800001111");
    customer.setVersion(4);
    return customer;
  }

  private TagCategory category(
      long id,
      String code,
      String name,
      TagSelectionMode mode,
      TagAutoUpdateMode updateMode,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new TagCategory(
        id,
        code,
        name,
        "动态分类用途",
        null,
        mode,
        true,
        true,
        updateMode,
        new BigDecimal("0.9100"),
        2,
        12,
        TagUncertainPolicy.KEEP_CURRENT,
        true,
        true,
        true,
        true,
        false,
        true,
        1,
        null,
        3,
        values,
        TagImpact.empty(),
        now,
        now);
  }

  private TagValue value(
      long id,
      long categoryId,
      String categoryCode,
      String code,
      String name) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 15, 10, 0);
    return new TagValue(
        id,
        categoryId,
        categoryCode,
        code,
        name,
        "动态标签含义",
        "明确表达目标时",
        "信息不足时",
        "我想改善核心力量",
        "我再看看",
        List.of("核心目标"),
        true,
        true,
        true,
        1,
        null,
        2,
        TagImpact.empty(),
        now,
        now);
  }
}
