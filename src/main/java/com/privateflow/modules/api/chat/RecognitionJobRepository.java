package com.privateflow.modules.api.chat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Process-local state only. Screenshot bytes remain in TemporaryRecognitionImageStore and are never
 * persisted to a business, audit, or supervision table.
 */
@Component
public class RecognitionJobRepository {

  private final Map<String, RecognitionJob> jobs = new LinkedHashMap<>();
  private final Deque<String> queuedJobIds = new ArrayDeque<>();

  public void save(RecognitionJob job) {
    jobs.put(job.jobId(), job);
    queuedJobIds.addLast(job.jobId());
  }

  public Optional<RecognitionJob> find(String jobId) {
    return Optional.ofNullable(jobs.get(jobId));
  }

  public Optional<RecognitionJob> takeNextQueued() {
    while (!queuedJobIds.isEmpty()) {
      RecognitionJob job = jobs.get(queuedJobIds.removeFirst());
      if (job != null && job.status() == RecognitionJobStatus.QUEUED) {
        return Optional.of(job);
      }
    }
    return Optional.empty();
  }

  public int runningCount() {
    return (int) jobs.values().stream().filter(RecognitionJob::running).count();
  }

  public int unfinishedCount(String username) {
    return (int) jobs.values().stream()
        .filter(job -> job.username().equals(username))
        .filter(RecognitionJob::unfinished)
        .count();
  }

  public List<RecognitionJob> activeJobs() {
    return jobs.values().stream()
        .filter(job -> job.status() == RecognitionJobStatus.QUEUED
            || job.status() == RecognitionJobStatus.RECOGNIZING)
        .toList();
  }
}
