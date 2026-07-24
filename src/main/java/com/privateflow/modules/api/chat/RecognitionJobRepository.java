package com.privateflow.modules.api.chat;

import java.util.ArrayDeque;
import java.util.Comparator;
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
        .filter(job -> job.requiresTemporaryImage()
            || job.status() == RecognitionJobStatus.READY
            || job.status() == RecognitionJobStatus.WAITING_CUSTOMER)
        .toList();
  }

  /** Keeps each employee's recent task history bounded without evicting unfinished work. */
  public void pruneTerminalHistory(int terminalHistoryCap) {
    if (terminalHistoryCap < 1) {
      throw new IllegalArgumentException("terminal history cap is required");
    }
    List<String> usernames = jobs.values().stream()
        .map(RecognitionJob::username)
        .distinct()
        .toList();
    for (String username : usernames) {
      List<RecognitionJob> employeeJobs = jobs.values().stream()
          .filter(job -> username.equals(job.username()))
          .toList();
      int excess = employeeJobs.size() - terminalHistoryCap;
      if (excess <= 0) {
        continue;
      }
      List<RecognitionJob> terminalJobs = jobs.values().stream()
          .filter(job -> username.equals(job.username()))
          .filter(RecognitionJob::terminal)
          .sorted(Comparator.comparing(RecognitionJob::updatedAt))
          .toList();
      terminalJobs.subList(0, Math.min(excess, terminalJobs.size()))
          .forEach(job -> remove(job.jobId()));
    }
  }

  private void remove(String jobId) {
    jobs.remove(jobId);
    queuedJobIds.removeIf(jobId::equals);
  }
}
