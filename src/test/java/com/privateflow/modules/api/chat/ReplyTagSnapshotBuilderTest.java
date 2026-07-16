package com.privateflow.modules.api.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.skill.ReplyTagSnapshot;
import com.privateflow.modules.tags.CustomerTagQueryDto;
import com.privateflow.modules.tags.CustomerTagQueryService;
import com.privateflow.modules.tags.TagAutoUpdateMode;
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
import org.junit.jupiter.api.Test;

class ReplyTagSnapshotBuilderTest {

  private final CustomerTagQueryService queryService = mock(CustomerTagQueryService.class);
  private final TagDirectoryService directoryService = mock(TagDirectoryService.class);
  private final ReplyTagSnapshotBuilder builder =
      new ReplyTagSnapshotBuilder(queryService, directoryService);

  @Test
  void buildsReplySafeChineseTagSnapshot() {
    when(queryService.current(5L)).thenReturn(List.of(currentTag(1L, 11L, true)));
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(category(1L, true, value(
            11L,
            "LOYALIST",
            "\\u5fe0\\u8bda\\u578b",
            "\\u91cd\\u89c6\\u5b89\\u5168\\u611f\\u548c\\u4e13\\u4e1a\\u80cc\\u4e66"))),
        Instant.parse("2026-07-16T00:00:00Z")));

    assertThat(builder.build(5L)).singleElement().satisfies(tag -> {
      assertThat(tag.categoryKey()).isEqualTo("personality_type");
      assertThat(tag.categoryName()).isEqualTo("\\u6027\\u683c\\u7c7b\\u578b");
      assertThat(tag.tagValue()).isEqualTo("LOYALIST");
      assertThat(tag.tagDisplayName()).isEqualTo("\\u5fe0\\u8bda\\u578b");
      assertThat(tag.meaning()).isEqualTo("\\u91cd\\u89c6\\u5b89\\u5168\\u611f\\u548c\\u4e13\\u4e1a\\u80cc\\u4e66");
      assertThat(tag.sourceType()).isEqualTo("MANUAL");
      assertThat(tag.evidenceText()).isEqualTo("\\u5ba2\\u6237\\u591a\\u6b21\\u8be2\\u95ee\\u6848\\u4f8b");
      assertThat(tag.manualLocked()).isTrue();
    });
  }

  @Test
  void excludesCategoriesDisabledForReply() {
    when(queryService.current(5L)).thenReturn(List.of(currentTag(1L, 11L, false)));
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.from(
        List.of(category(1L, false, value(
            11L,
            "INTERNAL",
            "\\u5185\\u90e8\\u6807\\u7b7e",
            "\\u4ec5\\u5185\\u90e8\\u7edf\\u8ba1"))),
        Instant.parse("2026-07-16T00:00:00Z")));

    assertThat(builder.build(5L)).isEmpty();
  }

  @Test
  void rejectsMissingDirectoryMetadataForCurrentAssignment() {
    when(queryService.current(5L)).thenReturn(List.of(currentTag(1L, 11L, false)));
    when(directoryService.getSnapshot()).thenReturn(TagDirectorySnapshot.empty(
        Instant.parse("2026-07-16T00:00:00Z")));

    assertThatThrownBy(() -> builder.build(5L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("directory");
  }

  private CustomerTagQueryDto currentTag(long categoryId, long tagValueId, boolean manualLocked) {
    return new CustomerTagQueryDto(
        101L,
        5L,
        3,
        categoryId,
        "personality_type",
        "\\u6027\\u683c\\u7c7b\\u578b",
        TagSelectionMode.SINGLE,
        true,
        null,
        4,
        tagValueId,
        "LOYALIST",
        "\\u5fe0\\u8bda\\u578b",
        true,
        null,
        5,
        TagSelectionMode.SINGLE,
        true,
        "MANUAL",
        new BigDecimal("0.9300"),
        "\\u5ba2\\u6237\\u591a\\u6b21\\u8be2\\u95ee\\u6848\\u4f8b",
        2,
        null,
        null,
        null,
        null,
        null,
        "keeper-1",
        manualLocked,
        "leader-1",
        LocalDateTime.of(2026, 7, 14, 9, 0),
        null,
        null,
        null,
        LocalDateTime.of(2026, 7, 14, 10, 0),
        LocalDateTime.of(2026, 7, 14, 10, 5));
  }

  private TagCategory category(long id, boolean useForReply, TagValue value) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagCategory(
        id,
        "personality_type",
        "\\u6027\\u683c\\u7c7b\\u578b",
        "\\u5ba2\\u6237\\u56de\\u590d\\u65b9\\u5411",
        null,
        TagSelectionMode.SINGLE,
        true,
        true,
        TagAutoUpdateMode.RECORD_ONLY,
        new BigDecimal("0.8500"),
        1,
        0,
        TagUncertainPolicy.KEEP_CURRENT,
        useForReply,
        true,
        true,
        true,
        false,
        true,
        1,
        null,
        0,
        List.of(value),
        TagImpact.empty(),
        now,
        now);
  }

  private TagValue value(long id, String code, String displayName, String meaning) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagValue(
        id,
        1L,
        "personality_type",
        code,
        displayName,
        meaning,
        "",
        "",
        "",
        "",
        List.of(),
        true,
        true,
        true,
        1,
        null,
        0,
        TagImpact.empty(),
        now,
        now);
  }
}
