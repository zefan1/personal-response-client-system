package com.privateflow.modules.tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.ConfigChangedEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
  void initialLoadFailureReturnsEmptyWithoutCachingAndNextReadRetries() {
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    when(repository.listTree())
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(List.of(category(1L, "recovered")));
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);

    TagDirectorySnapshot failed = service.getSnapshot();
    TagDirectorySnapshot recovered = service.getSnapshot();

    assertThat(failed).isNotNull();
    assertThat(failed.categories()).isEmpty();
    assertThat(failed.refreshedAt()).isNotNull();
    assertThat(recovered.categoriesByKey()).containsKey("recovered");
    verify(repository, times(2)).listTree();
  }

  @Test
  @Timeout(10)
  void concurrentInitialFailureIsSingleFlightAndLaterReadRetries() throws Exception {
    int callerCount = 4;
    TagRepository repository = mock(TagRepository.class);
    TagConfigProvider configProvider = mock(TagConfigProvider.class);
    AtomicInteger repositoryCalls = new AtomicInteger();
    CountDownLatch firstQueryEntered = new CountDownLatch(1);
    CountDownLatch releaseFirstQuery = new CountDownLatch(1);
    when(repository.listTree()).thenAnswer(invocation -> {
      if (repositoryCalls.incrementAndGet() == 1) {
        firstQueryEntered.countDown();
        assertThat(releaseFirstQuery.await(5, TimeUnit.SECONDS)).isTrue();
        throw new IllegalStateException("database unavailable");
      }
      return List.of(category(1L, "recovered"));
    });
    TagDirectoryService service = new TagDirectoryService(repository, configProvider);

    CyclicBarrier start = new CyclicBarrier(callerCount);
    CountDownLatch passedBarrier = new CountDownLatch(callerCount);
    List<Thread> workers = new ArrayList<>();
    ExecutorService executor = Executors.newFixedThreadPool(callerCount, task -> {
      Thread thread = new Thread(task, "tag-directory-test-" + workers.size());
      workers.add(thread);
      return thread;
    });
    try {
      List<Future<TagDirectorySnapshot>> futures = new ArrayList<>();
      for (int i = 0; i < callerCount; i++) {
        futures.add(executor.submit(() -> {
          start.await();
          passedBarrier.countDown();
          return service.getSnapshot();
        }));
      }
      assertThat(passedBarrier.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(firstQueryEntered.await(5, TimeUnit.SECONDS)).isTrue();
      awaitAllWorkersWaiting(workers);
      releaseFirstQuery.countDown();

      List<TagDirectorySnapshot> failedSnapshots = new ArrayList<>();
      for (Future<TagDirectorySnapshot> future : futures) {
        failedSnapshots.add(future.get(5, TimeUnit.SECONDS));
      }
      assertThat(failedSnapshots).allSatisfy(snapshot -> assertThat(snapshot.categories()).isEmpty());
      assertThat(failedSnapshots).allSatisfy(snapshot -> assertThat(snapshot).isSameAs(failedSnapshots.get(0)));
      assertThat(repositoryCalls).hasValue(1);

      TagDirectorySnapshot recovered = service.getSnapshot();
      assertThat(recovered.categoriesByKey()).containsKey("recovered");
      assertThat(repositoryCalls).hasValue(2);
    } finally {
      releaseFirstQuery.countDown();
      executor.shutdownNow();
    }
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

  private void awaitAllWorkersWaiting(List<Thread> workers) throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      long waiting = workers.stream()
          .filter(thread -> thread.getState() == Thread.State.BLOCKED
              || thread.getState() == Thread.State.WAITING
              || thread.getState() == Thread.State.TIMED_WAITING)
          .count();
      if (waiting == workers.size()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("并发首次读取者未全部进入等待状态");
  }
}
