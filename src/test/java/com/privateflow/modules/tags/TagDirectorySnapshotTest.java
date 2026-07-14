package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagDirectorySnapshotTest {

  @Test
  void rejectsDuplicateCategoryId() {
    assertThatThrownBy(() -> TagDirectorySnapshot.from(
        List.of(category(1L, "first", List.of()), category(1L, "second", List.of())),
        Instant.parse("2026-07-14T02:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("标签目录存在重复分类 ID：1");
  }

  @Test
  void rejectsDuplicateCategoryKey() {
    assertThatThrownBy(() -> TagDirectorySnapshot.from(
        List.of(category(1L, "duplicate", List.of()), category(2L, "duplicate", List.of())),
        Instant.parse("2026-07-14T02:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("标签目录存在重复分类编码：duplicate");
  }

  @Test
  void rejectsDuplicateValueId() {
    TagValue first = value(11L, 1L, "first", "FIRST", List.of());
    TagValue second = value(11L, 2L, "second", "SECOND", List.of());

    assertThatThrownBy(() -> TagDirectorySnapshot.from(
        List.of(category(1L, "first", List.of(first)), category(2L, "second", List.of(second))),
        Instant.parse("2026-07-14T02:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("标签目录存在重复标签值 ID：11");
  }

  @Test
  void rejectsDuplicateValueCodeWithinCategory() {
    TagValue first = value(11L, 1L, "intent", "DUPLICATE", List.of());
    TagValue second = value(12L, 1L, "intent", "DUPLICATE", List.of());

    assertThatThrownBy(() -> TagDirectorySnapshot.from(
        List.of(category(1L, "intent", List.of(first, second))),
        Instant.parse("2026-07-14T02:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("标签目录存在重复分类值编码：intent/DUPLICATE");
  }

  @Test
  void createsDeeplyImmutableTreeAndIndexesByIdKeyAndCode() {
    List<String> synonyms = new ArrayList<>(List.of("重点客户"));
    TagValue value = value(11L, 1L, "intent_level", "HIGH", synonyms);
    List<TagValue> values = new ArrayList<>(List.of(value));
    TagCategory category = category(1L, "intent_level", values);
    TagValue otherValue = value(21L, 2L, "priority_level", "HIGH", List.of("优先客户"));
    TagCategory otherCategory = category(2L, "priority_level", List.of(otherValue));
    List<TagCategory> tree = new ArrayList<>(List.of(category, otherCategory));

    TagDirectorySnapshot snapshot = TagDirectorySnapshot.from(
        tree,
        Instant.parse("2026-07-14T02:00:00Z"));

    tree.clear();
    values.clear();
    synonyms.clear();

    assertThat(snapshot.categories()).hasSize(2);
    assertThat(snapshot.categoriesById()).containsEntry(1L, snapshot.categories().get(0));
    assertThat(snapshot.categoriesByKey()).containsEntry("intent_level", snapshot.categories().get(0));
    assertThat(snapshot.valuesById()).containsEntry(11L, snapshot.categories().get(0).values().get(0));
    assertThat(snapshot.valuesByCategoryAndCode())
        .containsEntry(new TagValueCode("intent_level", "HIGH"), snapshot.categories().get(0).values().get(0));
    assertThat(snapshot.valuesByCode().get("HIGH"))
        .extracting(TagValue::id)
        .containsExactly(11L, 21L);
    assertThat(snapshot.refreshedAt()).isEqualTo(Instant.parse("2026-07-14T02:00:00Z"));
    assertThat(snapshot.categories().get(0).values().get(0).synonyms()).containsExactly("重点客户");

    assertThatThrownBy(() -> snapshot.categories().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.categories().get(0).values().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.categories().get(0).values().get(0).synonyms().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.categoriesById().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.categoriesByKey().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.valuesById().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.valuesByCategoryAndCode().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.valuesByCode().clear()).isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> snapshot.valuesByCode().get("HIGH").clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private TagCategory category(long id, String key, List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagCategory(
        id, key, "动态中文名称", "用途", null, TagSelectionMode.SINGLE,
        true, true, TagAutoUpdateMode.RECORD_ONLY, new BigDecimal("0.8500"),
        1, 0, TagUncertainPolicy.KEEP_CURRENT, true, true, true, true,
        false, true, 1, null, 0, values, TagImpact.empty(), now, now);
  }

  private TagValue value(long id, long categoryId, String categoryKey, String code, List<String> synonyms) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagValue(
        id, categoryId, categoryKey, code, "高意向", "含义", "适用", "不适用",
        "正例", "反例", synonyms, true, true, true, 1, null, 0,
        TagImpact.empty(), now, now);
  }
}
