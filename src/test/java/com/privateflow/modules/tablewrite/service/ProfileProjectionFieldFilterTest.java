package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.tablewrite.client.WecomSmartSheetField;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProfileProjectionFieldFilterTest {

  private final TableFieldMappingResolver mappingResolver = mock(TableFieldMappingResolver.class);
  private final WecomSmartSheetFieldCatalog fieldCatalog = mock(WecomSmartSheetFieldCatalog.class);
  private final ProfileProjectionFieldFilter filter = new ProfileProjectionFieldFilter(mappingResolver, fieldCatalog);

  @Test
  void skipsInvalidSingleSelectAndKeepsMappedTextFields() {
    when(mappingResolver.toSourceFields("th1zyU", Map.of(
        "customerStage", "intent-stage",
        "bodyConcerns", "lower-back-pain")))
        .thenReturn(Map.of(
            "客户阶段", "intent-stage",
            "客户关注点", "lower-back-pain"));
    when(fieldCatalog.visibleFields(Duration.ofSeconds(5))).thenReturn(Map.of(
        "客户阶段", new WecomSmartSheetField(
            "field-stage", "客户阶段", "FIELD_TYPE_SINGLE_SELECT", Map.of("待联系", "option-1"), false),
        "客户关注点", new WecomSmartSheetField(
            "field-concerns", "客户关注点", "FIELD_TYPE_TEXT", Map.of(), false)));

    ProfileProjectionFieldFilter.Result result = filter.filter(
        "th1zyU",
        Map.of("customerStage", "intent-stage", "bodyConcerns", "lower-back-pain"),
        Duration.ofSeconds(5));

    assertThat(result.fields()).containsOnly(Map.entry("客户关注点", "lower-back-pain"));
    assertThat(result.skippedSelectableFields()).containsExactly("客户阶段");
  }

  @Test
  void retainsAllowedSingleSelect() {
    when(mappingResolver.toSourceFields("th1zyU", Map.of("customerStage", "待联系")))
        .thenReturn(Map.of("客户阶段", "待联系"));
    when(fieldCatalog.visibleFields(Duration.ofSeconds(5))).thenReturn(Map.of(
        "客户阶段", new WecomSmartSheetField(
            "field-stage", "客户阶段", "FIELD_TYPE_SINGLE_SELECT", Map.of("待联系", "option-1"), false)));

    ProfileProjectionFieldFilter.Result result = filter.filter(
        "th1zyU", Map.of("customerStage", "待联系"), Duration.ofSeconds(5));

    assertThat(result.fields()).containsOnly(Map.entry("客户阶段", "待联系"));
    assertThat(result.skippedSelectableFields()).isEmpty();
  }
}
