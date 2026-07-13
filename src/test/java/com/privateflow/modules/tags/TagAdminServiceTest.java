package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.api.ws.WsPushService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class TagAdminServiceTest {

  private TagRepository repository;
  private TagAdminService service;

  @BeforeEach
  void setUp() {
    repository = mock(TagRepository.class);
    service = new TagAdminService(
        repository,
        mock(ApplicationEventPublisher.class),
        mock(WsPushService.class),
        mock(AuditLogger.class));
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
    when(repository.usageCount("intentLevel", "HIGH")).thenReturn(3);

    assertThatThrownBy(() -> service.deleteValue(2L))
        .isInstanceOf(ApiException.class)
        .hasMessage("该标签正在被 3 个客户使用，请改为停用");
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
