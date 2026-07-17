package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TagExchangeServiceTest {

  private TagDirectoryService directoryService;
  private TagSelectionValidator validator;
  private TagExchangeService service;
  private TagDirectorySnapshot snapshot;

  @BeforeEach
  void setUp() {
    directoryService = mock(TagDirectoryService.class);
    validator = mock(TagSelectionValidator.class);
    LocalDateTime now = LocalDateTime.of(2026, 7, 17, 10, 0);
    TagCategory body = category(
        1L,
        "body_concerns",
        "bodyConcerns",
        TagSelectionMode.MULTI,
        List.of(value(11L, 1L, "body_concerns", "LEAKAGE", "漏尿", List.of("尿漏"))));
    TagCategory intent = category(
        2L,
        "intent_level",
        "intentLevel",
        TagSelectionMode.SINGLE,
        List.of(value(21L, 2L, "intent_level", "HIGH", "高意向", List.of("重点"))));
    snapshot = TagDirectorySnapshot.from(List.of(body, intent), Instant.parse("2026-07-17T02:00:00Z"));
    when(directoryService.getSnapshot()).thenReturn(snapshot);
    when(validator.validateCodes(
        eq(TagCandidatePurpose.IMPORT),
        any(String.class),
        any(List.class),
        any(TagSelectionContext.class)))
        .thenAnswer(invocation -> {
          String categoryKey = invocation.getArgument(1);
          @SuppressWarnings("unchecked")
          List<String> codes = invocation.getArgument(2);
          TagCategory category = snapshot.categoriesByKey().get(categoryKey);
          List<TagValue> values = category.values().stream()
              .filter(value -> codes.contains(value.tagValue()))
              .toList();
          return TagSelectionValidationResult.accepted(category, values);
        });
    service = new TagExchangeService(directoryService, validator);
  }

  @Test
  void resolvesCodeDisplayNameAndSynonymWithoutFuzzyMatching() {
    TagExchangeResult code = service.prepareInbound(
        TagExchangeSourceType.CSV_IMPORT,
        "row-3",
        Map.of("bodyConcerns", "LEAKAGE"));
    TagExchangeResult displayName = service.prepareInbound(
        TagExchangeSourceType.CSV_IMPORT,
        "row-4",
        Map.of("bodyConcerns", "漏尿"));
    TagExchangeResult synonym = service.prepareInbound(
        TagExchangeSourceType.CSV_IMPORT,
        "row-5",
        Map.of("bodyConcerns", "尿漏"));

    assertThat(code.acceptedFields()).containsEntry("bodyConcerns", "LEAKAGE");
    assertThat(displayName.acceptedFields()).containsEntry("bodyConcerns", "LEAKAGE");
    assertThat(synonym.acceptedFields()).containsEntry("bodyConcerns", "LEAKAGE");
    assertThat(code.unmatched()).isEmpty();
    assertThat(displayName.unmatched()).isEmpty();
    assertThat(synonym.unmatched()).isEmpty();
  }

  @Test
  void recordsUnknownTokenAndDoesNotGuessAValue() {
    TagExchangeResult result = service.prepareInbound(
        TagExchangeSourceType.EXTERNAL_SYNC,
        "row-8",
        Map.of("bodyConcerns", "漏尿,unknown concern"));

    assertThat(result.acceptedFields()).containsEntry("bodyConcerns", "LEAKAGE");
    assertThat(result.unmatched()).singleElement().satisfies(item -> {
      assertThat(item.rawValue()).isEqualTo("漏尿,unknown concern");
      assertThat(item.unmatchedTokens()).containsExactly("unknown concern");
      assertThat(item.sourceType()).isEqualTo(TagExchangeSourceType.EXTERNAL_SYNC);
      assertThat(item.sourceRecordId()).isEqualTo("row-8");
    });
  }

  @Test
  void invalidSingleValueIsOmittedFromInboundAndFilteredFromOutbound() {
    TagExchangeResult inbound = service.prepareInbound(
        TagExchangeSourceType.CSV_IMPORT,
        "row-9",
        Map.of("intentLevel", "unknown"));

    assertThat(inbound.acceptedFields()).doesNotContainKey("intentLevel");
    assertThat(inbound.unmatched()).singleElement()
        .extracting(TagExchangeUnmatchedValue::unmatchedTokens)
        .isEqualTo(List.of("unknown"));

    TagExchangeResult outbound = service.prepareOutbound(
        TagExchangeSourceType.TABLE_WRITE,
        "row-9",
        Map.of("intentLevel", "unknown", "nickname", "Alice"));

    assertThat(outbound.acceptedFields()).containsEntry("nickname", "Alice");
    assertThat(outbound.acceptedFields()).doesNotContainKey("intentLevel");
    assertThat(outbound.filteredFields()).contains("intentLevel");
  }

  private TagCategory category(
      long id,
      String categoryKey,
      String boundField,
      TagSelectionMode selectionMode,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 17, 10, 0);
    return new TagCategory(
        id,
        categoryKey,
        categoryKey,
        "",
        boundField,
        selectionMode,
        false,
        true,
        TagAutoUpdateMode.RECORD_ONLY,
        new BigDecimal("0.8500"),
        1,
        0,
        TagUncertainPolicy.KEEP_CURRENT,
        true,
        true,
        true,
        true,
        false,
        true,
        1,
        null,
        0,
        values,
        TagImpact.empty(),
        now,
        now);
  }

  private TagValue value(
      long id,
      long categoryId,
      String categoryKey,
      String code,
      String displayName,
      List<String> synonyms) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 17, 10, 0);
    return new TagValue(
        id,
        categoryId,
        categoryKey,
        code,
        displayName,
        "",
        "",
        "",
        "",
        "",
        synonyms,
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
