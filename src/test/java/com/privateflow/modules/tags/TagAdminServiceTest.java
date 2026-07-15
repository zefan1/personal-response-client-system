package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ws.WsPushService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.api.ApiErrorCodes;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TagAdminServiceTest {

  private TagRepository repository;
  private TagMergeRepository mergeRepository;
  private TagConfigProvider configProvider;
  private TagRuleReferenceService ruleReferenceService;
  private TagAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(TagRepository.class);
    mergeRepository = mock(TagMergeRepository.class);
    configProvider = mock(TagConfigProvider.class);
    ruleReferenceService = mock(TagRuleReferenceService.class);
    when(configProvider.get()).thenReturn(new TagRuntimeConfig(300, 50));
    when(repository.categoryImpact(anyLong())).thenReturn(TagImpact.empty());
    when(repository.valueImpact(any(TagValue.class))).thenReturn(TagImpact.empty());
    when(ruleReferenceService.countReferences(any(), any())).thenReturn(
        new TagRuleReferenceService.ReferenceCounts(Map.of(), Map.of()));
    service = new TagAdminService(
        repository,
        mergeRepository,
        ruleReferenceService,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(AuditLogger.class),
        configProvider,
        new ObjectMapper());
  }

  @Test
  void builtinCategoryDeleteUsesChineseBusinessMessage() {
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of())));

    assertThatThrownBy(() -> service.deleteCategory(1L))
        .isInstanceOf(ApiException.class)
        .hasMessage("内置标签分类不能删除，可以停用或合并");
  }

  @Test
  void historicalCategoryAssignmentStillBlocksDelete() {
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(false, List.of())));
    when(repository.categoryImpact(1L)).thenReturn(new TagImpact(1, 0, 1, 0, 0, 0, 0, 0, 0));

    assertThatThrownBy(() -> service.deleteCategory(1L))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(TagErrorCodes.CATEGORY_IN_USE);

    verify(repository).categoryImpact(1L);
    verify(repository, never()).deleteCategory(anyLong());
  }

  @Test
  void usedTagValueDeleteUsesChineseBusinessMessage() {
    TagValue value = value();
    when(repository.findValue(2L)).thenReturn(Optional.of(value));
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of(value))));
    when(repository.valueImpact(value))
        .thenReturn(new TagImpact(3, 0, 3, 0, 0, 0, 0, 0, 0));

    assertThatThrownBy(() -> service.deleteValue(2L))
        .isInstanceOf(ApiException.class)
        .hasMessage("该标签仍影响 3 位客户、0 条规则和 3 条历史记录，只能停用或合并");

    verify(repository).valueImpact(value);
    verify(repository, never()).deleteValue(anyLong());
  }

  @Test
  void valueLimitComesFromRuntimeConfiguration() {
    when(configProvider.get()).thenReturn(new TagRuntimeConfig(300, 2));
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of())));
    when(repository.valueCount(1L)).thenReturn(2);

    assertThatThrownBy(() -> service.createValue(new TagValueRequest(1L, "NEW_TAG", "新标签", true, 3)))
        .isInstanceOf(ApiException.class)
        .hasMessage("每个分类最多只能创建 2 个标签");
  }

  @Test
  void createValueRejectsMissingCategoryFromDatabase() {
    assertThatThrownBy(() -> service.createValue(
        new TagValueRequest(999L, null, "新标签", true, 3)))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(TagErrorCodes.CATEGORY_NOT_FOUND);

    verify(repository).findCategory(999L);
    verify(repository, never()).createValue(anyString(), any(TagValueRequest.class), anyInt());
  }

  @Test
  void createValueRejectsDisabledCategory() {
    when(repository.findCategory(1L)).thenReturn(Optional.of(
        category(1L, "intent_level", "意向等级", "intentLevel", false, false, 0, List.of())));

    assertThatThrownBy(() -> service.createValue(
        new TagValueRequest(1L, null, "新标签", true, 3)))
        .isInstanceOf(ApiException.class)
        .hasMessage("标签分类已停用，不能创建标签值")
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.BAD_REQUEST);

    verify(repository, never()).valueCount(anyLong());
    verify(repository, never()).createValue(anyString(), any(TagValueRequest.class), anyInt());
  }

  @Test
  void createValueRejectsMergedCategory() {
    when(repository.findCategory(1L)).thenReturn(Optional.of(mergedCategory()));

    assertThatThrownBy(() -> service.createValue(
        new TagValueRequest(1L, null, "新标签", true, 3)))
        .isInstanceOf(ApiException.class)
        .hasMessage("已合并分类只能查看历史信息，不能继续修改")
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(TagErrorCodes.MERGED_ITEM_READ_ONLY);

    verify(repository, never()).valueCount(anyLong());
    verify(repository, never()).createValue(anyString(), any(TagValueRequest.class), anyInt());
  }

  @Test
  void createValueRejectsCategoryThatBecameUnavailableBeforeInsert() {
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of())));
    when(repository.valueCount(1L)).thenReturn(0);
    when(repository.valueExists(eq(1L), anyString())).thenReturn(false);
    when(repository.createValue(anyString(), any(TagValueRequest.class), anyInt())).thenReturn(0L);

    assertThatThrownBy(() -> service.createValue(
        new TagValueRequest(1L, null, "新标签", true, 3)))
        .isInstanceOf(ApiException.class)
        .hasMessage("标签分类已停用或已合并，不能创建标签值")
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.BAD_REQUEST);
  }

  @Test
  void valueCodeIsGeneratedByBackendAndClientCodeIsIgnored() {
    TagValue created = value();
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of())));
    when(repository.valueCount(1L)).thenReturn(0);
    when(repository.valueExists(eq(1L), anyString())).thenReturn(false);
    when(repository.createValue(anyString(), any(TagValueRequest.class), anyInt())).thenReturn(2L);
    when(repository.findValue(2L)).thenReturn(Optional.of(created));

    service.createValue(new TagValueRequest(1L, "CLIENT_CONTROLLED", "新标签", true, 3));

    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(repository).createValue(code.capture(), any(TagValueRequest.class), eq(3));
    assertThat(code.getValue()).matches("TAG_[A-F0-9]{12}").isNotEqualTo("CLIENT_CONTROLLED");
  }

  @Test
  void customCategoryDoesNotRequireLegacyCustomerFieldBinding() {
    when(repository.categoryKeyExists(anyString())).thenReturn(false);
    when(repository.createCategory(anyString(), any(TagCategoryRequest.class), anyInt())).thenReturn(1L);
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(false, List.of())));

    service.createCategory(new TagCategoryRequest("新分类", null, true, 8));

    verify(repository).createCategory(anyString(), any(TagCategoryRequest.class), eq(8));
  }

  @Test
  void customCategoryRejectsLegacyCustomerFieldBinding() {
    assertThatThrownBy(() -> service.createCategory(
        new TagCategoryRequest("新分类", "intentLevel", true, 8)))
        .isInstanceOf(ApiException.class)
        .hasMessage("新标签分类无需绑定客户档案字段，请直接使用统一标签记录");
  }

  @Test
  void updateAndToggleRequireOptimisticLockVersion() {
    TagCategory category = category(true, List.of(value()));
    when(repository.findCategory(1L)).thenReturn(Optional.of(category));
    when(repository.findValue(2L)).thenReturn(Optional.of(value()));

    assertThatThrownBy(() -> service.updateCategory(1L, new TagCategoryRequest("新名称", null, true, 1)))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(TagErrorCodes.VERSION_REQUIRED);
    assertThatThrownBy(() -> service.toggleValue(2L, false, null))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(TagErrorCodes.VERSION_REQUIRED);

    verify(repository, never()).updateCategory(anyLong(), any());
    verify(repository, never()).toggleValue(anyLong(), anyBoolean(), any());
  }

  @Test
  void mergePreviewRequiresBothVersions() {
    TagCategory source = category(1L, "source", "源分类", null, false, true, 4, List.of());
    TagCategory target = category(3L, "target", "目标分类", null, false, true, 7, List.of());
    when(repository.findCategory(1L)).thenReturn(Optional.of(source));
    when(repository.findCategory(3L)).thenReturn(Optional.of(target));

    assertThatThrownBy(() -> service.previewCategoryMerge(1L, new TagMergeRequest(3L, null, 7)))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(TagErrorCodes.VERSION_REQUIRED);
  }

  @Test
  void categoryMergeTransfersLegacyBindingToUnboundTargetBeforeRuleRewrite() {
    TagCategory source = category(1L, "personality_type", "性格类型", "personalityType", true, true, 4, List.of());
    TagCategory target = category(3L, "custom_personality", "客户风格", null, false, true, 7, List.of());
    TagCategory transferredSource = category(1L, "personality_type", "性格类型", null, true, true, 5, List.of());
    TagCategory transferredTarget = category(3L, "custom_personality", "客户风格", "personalityType", false, true, 8, List.of());
    when(mergeRepository.lockCategory(1L)).thenReturn(Optional.of(4));
    when(mergeRepository.lockCategory(3L)).thenReturn(Optional.of(7));
    when(repository.findCategory(1L)).thenReturn(Optional.of(source), Optional.of(transferredSource));
    when(repository.findCategory(3L)).thenReturn(
        Optional.of(target), Optional.of(transferredTarget), Optional.of(transferredTarget));
    when(mergeRepository.transferBoundField(source, target)).thenReturn(2);

    service.mergeCategory(1L, new TagMergeRequest(3L, 4, 7));

    verify(mergeRepository).transferBoundField(source, target);
    verify(ruleReferenceService).rewriteCategory(transferredSource, transferredTarget, Map.of());
  }

  @Test
  void csvExportNeutralizesSpreadsheetFormulaPrefixes() {
    TagCategory category = category(
        9L, "formula", "=HYPERLINK(\"https://example.invalid\")", null, false, true, 0, List.of());
    when(repository.findCategoriesForExport(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(List.of(category));

    String csv = new String(
        service.exportCategories(null, null, null, null, null, null, null),
        StandardCharsets.UTF_8);

    assertThat(csv).contains("\"'=HYPERLINK(\"\"https://example.invalid\"\")\"");
  }

  private TagCategory category(boolean builtin, List<TagValue> values) {
    return category(1L, "intent_level", "意向等级", "intentLevel", builtin, true, 0, values);
  }

  private TagCategory category(
      long id,
      String key,
      String name,
      String boundField,
      boolean builtin,
      boolean enabled,
      int version,
      List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
    return new TagCategory(
        id, key, name, "", boundField, TagSelectionMode.SINGLE, false, true,
        TagAutoUpdateMode.RECORD_ONLY, new BigDecimal("0.8500"), 1, 0,
        TagUncertainPolicy.KEEP_CURRENT, true, true, true, true, builtin, enabled,
        1, null, version, values, TagImpact.empty(), now, now);
  }

  private TagCategory mergedCategory() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
    return new TagCategory(
        1L, "intent_level", "意向等级", "", "intentLevel", TagSelectionMode.SINGLE, false, true,
        TagAutoUpdateMode.RECORD_ONLY, new BigDecimal("0.8500"), 1, 0,
        TagUncertainPolicy.KEEP_CURRENT, true, true, true, true, false, true,
        1, 2L, 0, List.of(), TagImpact.empty(), now, now);
  }

  private TagValue value() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
    return new TagValue(2L, 1L, "intent_level", "HIGH", "高意向", true, 1, now, now);
  }
}
