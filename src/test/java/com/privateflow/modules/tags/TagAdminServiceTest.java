package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ws.WsPushService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TagAdminServiceTest {

  private TagRepository repository;
  private TagConfigProvider configProvider;
  private TagAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(TagRepository.class);
    configProvider = mock(TagConfigProvider.class);
    when(configProvider.get()).thenReturn(new TagRuntimeConfig(300, 50));
    service = new TagAdminService(
        repository,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(AuditLogger.class),
        configProvider);
  }

  @Test
  void builtinCategoryDeleteUsesChineseBusinessMessage() {
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of())));

    assertThatThrownBy(() -> service.deleteCategory(1L))
        .isInstanceOf(ApiException.class)
        .hasMessage("内置标签分类不能删除，可以停用");
  }

  @Test
  void usedTagValueDeleteUsesChineseBusinessMessage() {
    TagValue value = value();
    when(repository.findValue(2L)).thenReturn(Optional.of(value));
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of(value))));
    when(repository.usageCount(2L, "intentLevel", TagSelectionMode.SINGLE, "HIGH")).thenReturn(3);

    assertThatThrownBy(() -> service.deleteValue(2L))
        .isInstanceOf(ApiException.class)
        .hasMessage("该标签已有 3 条客户或历史记录引用，请改为停用");
  }

  @Test
  void valueLimitComesFromRuntimeConfiguration() {
    when(configProvider.get()).thenReturn(new TagRuntimeConfig(300, 2));
    when(repository.findCategory(1L)).thenReturn(Optional.of(category(true, List.of())));
    when(repository.valueCount(1L)).thenReturn(2);

    assertThatThrownBy(() -> service.createValue(new TagValueRequest(1L, "NEW_TAG", "新标签", true, 3)))
        .isInstanceOf(ApiException.class)
        .hasMessage("每个分类最多只能创建 2 个标签值");
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

  private TagCategory category(boolean builtin, List<TagValue> values) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
    return new TagCategory(1L, "intent_level", "意向等级", "intentLevel", builtin, true, 1, values, now, now);
  }

  private TagValue value() {
    LocalDateTime now = LocalDateTime.of(2026, 7, 13, 12, 0);
    return new TagValue(2L, 1L, "intent_level", "HIGH", "高意向", true, 1, now, now);
  }
}
