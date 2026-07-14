package com.privateflow.modules.tags;

import com.privateflow.common.events.ConfigChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TagDirectoryService {

  private static final Logger log = LoggerFactory.getLogger(TagDirectoryService.class);
  private static final Duration INITIAL_FAILURE_WINDOW = Duration.ofMillis(500);
  private final TagRepository repository;
  private final TagConfigProvider configProvider;
  private final Supplier<Instant> now;
  private final AtomicReference<TagDirectorySnapshot> current = new AtomicReference<>();
  private final AtomicReference<CompletableFuture<TagDirectorySnapshot>> initialLoad = new AtomicReference<>();
  private final AtomicReference<InitialFailure> initialFailure = new AtomicReference<>();
  private final Object refreshLock = new Object();

  @Autowired
  public TagDirectoryService(TagRepository repository, TagConfigProvider configProvider) {
    this(repository, configProvider, Instant::now);
  }

  TagDirectoryService(
      TagRepository repository,
      TagConfigProvider configProvider,
      Supplier<Instant> now) {
    this.repository = repository;
    this.configProvider = configProvider;
    this.now = now;
  }

  public TagDirectorySnapshot getSnapshot() {
    while (true) {
      TagDirectorySnapshot snapshot = current.get();
      if (snapshot != null) {
        return snapshot;
      }
      InitialFailure failure = initialFailure.get();
      if (failure != null) {
        if (now.get().isBefore(failure.expiresAt())) {
          return failure.snapshot();
        }
        initialFailure.compareAndSet(failure, null);
      }
      CompletableFuture<TagDirectorySnapshot> inFlight = initialLoad.get();
      if (inFlight != null) {
        return inFlight.join();
      }
      CompletableFuture<TagDirectorySnapshot> started = new CompletableFuture<>();
      if (!initialLoad.compareAndSet(null, started)) {
        continue;
      }
      try {
        TagDirectorySnapshot loaded = refresh();
        started.complete(loaded);
        return loaded;
      } catch (RuntimeException | Error ex) {
        started.completeExceptionally(ex);
        throw ex;
      } finally {
        initialLoad.compareAndSet(started, null);
      }
    }
  }

  public TagDirectorySnapshot refresh() {
    synchronized (refreshLock) {
      TagDirectorySnapshot previous = current.get();
      try {
        TagDirectorySnapshot loaded = TagDirectorySnapshot.from(repository.listTree(), now.get());
        current.set(loaded);
        initialFailure.set(null);
        return loaded;
      } catch (RuntimeException ex) {
        if (previous != null) {
          log.error("刷新标签目录失败，保留上一次成功快照", ex);
          return previous;
        }
        Instant failedAt = now.get();
        TagDirectorySnapshot empty = TagDirectorySnapshot.empty(failedAt);
        initialFailure.set(new InitialFailure(empty, failedAt.plus(INITIAL_FAILURE_WINDOW)));
        log.error("首次加载标签目录失败，返回明确空快照", ex);
        return empty;
      }
    }
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if ("tag_config".equals(event.configKey())) {
      refresh();
    }
  }

  @Scheduled(fixedDelayString = "${tag.directory-check-interval-ms:60000}")
  public void scheduledRefresh() {
    TagDirectorySnapshot snapshot = current.get();
    if (snapshot == null) {
      refresh();
      return;
    }
    long intervalSeconds = configProvider.get().cacheRefreshIntervalSeconds();
    if (Duration.between(snapshot.refreshedAt(), now.get()).getSeconds() >= intervalSeconds) {
      refresh();
    }
  }

  private record InitialFailure(TagDirectorySnapshot snapshot, Instant expiresAt) {
  }
}
