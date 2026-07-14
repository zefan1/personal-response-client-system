package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.ConfigChangedEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TagDirectoryServiceTest {

  @Test
  void loadsLazilyOnlyOnceOnFirstRead() {
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    when(repository.listTree()).thenReturn(List.of(category(1L, "first")));
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);

    TagDirectorySnapshot first = service.getSnapshot();
    TagDirectorySnapshot second = service.getSnapshot();

    assertThat(first).isSameAs(second);
    assertThat(first.categoriesByKey()).containsKey("first");
    verify(repository, times(1)).listTree();
  }

  @Test
  void initialLoadFailureReturnsExplicitEmptySnapshot() {
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    when(repository.listTree()).thenThrow(new IllegalStateException("database unavailable"));
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);

    TagDirectorySnapshot snapshot = service.getSnapshot();

    assertThat(snapshot).isNotNull();
    assertThat(snapshot.categories()).isEmpty();
    assertThat(snapshot.refreshedAt()).isNotNull();
  }

  @Test
  void refreshFailureKeepsLastSuccessfulSnapshot() {
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    when(repository.listTree())
        .thenReturn(List.of(category(1L, "stable")))
        .thenThrow(new IllegalStateException("database unavailable"));
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);
    TagDirectorySnapshot stable = service.getSnapshot();

    TagDirectorySnapshot afterFailure = service.refresh();

    assertThat(afterFailure).isSameAs(stable);
    assertThat(service.getSnapshot()).isSameAs(stable);
  }

  @Test
  void tagConfigEventRefreshesTheSnapshot() {
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    when(repository.listTree())
        .thenReturn(List.of(category(1L, "before")))
        .thenReturn(List.of(category(2L, "after")));
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);
    service.getSnapshot();

    service.onConfigChanged(new ConfigChangedEvent("other"));
    assertThat(service.getSnapshot().categoriesByKey()).containsKey("before");

    service.onConfigChanged(new ConfigChangedEvent("tag_config"));
    assertThat(service.getSnapshot().categoriesByKey()).containsKey("after").doesNotContainKey("before");
    verify(repository, times(2)).listTree();
  }

  @Test
  void scheduledRefreshUsesConfiguredInterval() {
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    when(configProvider.get()).thenReturn(new TagRuntimeConfig(60, 50));
    when(repository.listTree()).thenReturn(List.of(category(1L, "stable")));
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);
    service.getSnapshot();

    service.scheduledRefresh();

    verify(repository, times(1)).listTree();
  }

  private TagCategory category(long id, String key) {
    LocalDateTime now = LocalDateTime.of(2026, 7, 14, 10, 0);
    return new TagCategory(id, key, key, null, false, true, 1, List.of(), now, now);
  }
}
