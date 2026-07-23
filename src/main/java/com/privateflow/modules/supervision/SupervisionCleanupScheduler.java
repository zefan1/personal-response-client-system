package com.privateflow.modules.supervision;

import com.privateflow.modules.api.chat.PendingReplyTaskRepository;
import com.privateflow.modules.api.chat.PendingReplyTaskService;
import com.privateflow.modules.api.chat.ReplyTaskClock;
import com.privateflow.modules.llm.LlmCallAnalyticsRepository;
import com.privateflow.modules.skill.admin.SkillCallAnalyticsRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupervisionCleanupScheduler {

  private final SupervisionConfig supervisionConfig;
  private final ReplyTaskClock taskClock;
  private final SupervisionEventRepository supervisionEventRepository;
  private final LlmCallAnalyticsRepository llmCallAnalyticsRepository;
  private final SkillCallAnalyticsRepository skillCallAnalyticsRepository;
  private final PendingReplyTaskRepository pendingReplyTaskRepository;
  private final PendingReplyTaskService pendingReplyTaskService;

  public SupervisionCleanupScheduler(
      SupervisionConfig supervisionConfig,
      ReplyTaskClock taskClock,
      SupervisionEventRepository supervisionEventRepository,
      LlmCallAnalyticsRepository llmCallAnalyticsRepository,
      SkillCallAnalyticsRepository skillCallAnalyticsRepository,
      PendingReplyTaskRepository pendingReplyTaskRepository,
      PendingReplyTaskService pendingReplyTaskService) {
    this.supervisionConfig = supervisionConfig;
    this.taskClock = taskClock;
    this.supervisionEventRepository = supervisionEventRepository;
    this.llmCallAnalyticsRepository = llmCallAnalyticsRepository;
    this.skillCallAnalyticsRepository = skillCallAnalyticsRepository;
    this.pendingReplyTaskRepository = pendingReplyTaskRepository;
    this.pendingReplyTaskService = pendingReplyTaskService;
  }

  @Scheduled(cron = "0 10 4 * * *", zone = ReplyTaskClock.BUSINESS_TIME_ZONE_ID)
  public void cleanup() {
    cleanupAt(taskClock.now());
  }

  public void cleanupAt(LocalDateTime now) {
    Objects.requireNonNull(now, "cleanup time is required");
    supervisionEventRepository.deleteEventsBefore(now.minusDays(supervisionConfig.recordRetentionDays()));
    llmCallAnalyticsRepository.deleteBefore(now.minusDays(supervisionConfig.technicalLogRetentionDays()));
    skillCallAnalyticsRepository.deleteBefore(now.minusDays(supervisionConfig.technicalLogRetentionDays()));
    pendingReplyTaskService.recoverTasksAt(now);
    pendingReplyTaskRepository.deletePhysicallyExpiredBefore(
        now.minusDays(supervisionConfig.expiredReplyTaskRetentionDays()));
  }
}
