package com.privateflow.modules.tags;

import com.privateflow.common.events.ConfigChangedEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class TagDirectoryService {

  private static final Logger log = LoggerFactory.getLogger(TagDirectoryService.class);
  private final TagRepository repository;
  private final TagConfigProvider configProvider;
  private final AtomicReference<TagDirectorySnapshot> current = new AtomicReference<>();
  private final AtomicReference<CompletableFuture<TagDirectorySnapshot>> initialLoad = new AtomicReference<>();
  private final Object refreshLock = new Object();

  public TagDirectoryService(TagRepository repository, TagConfigProvider configProvider) {
    this.repository = repository;
    this.configProvider = configProvider;
  }

  public TagDirectorySnapshot getSnapshot() {
    while (true) {
      TagDirectorySnapshot snapshot = current.get();
      if (snapshot != null) {
        return snapshot;
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
        TagDirectorySnapshot loaded = TagDirectorySnapshot.from(repository.listTree(), Instant.now());
        current.set(loaded);
        return loaded;
      } catch (RuntimeException ex) {
        if (previous != null) {
          log.error("刷新标签目录失败，保留上一次成功快照", ex);
          return previous;
        }
        TagDirectorySnapshot empty = TagDirectorySnapshot.empty(Instant.now());
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
    if (Duration.between(snapshot.refreshedAt(), Instant.now()).getSeconds() >= intervalSeconds) {
      refresh();
    }
  }
}
