package com.privateflow.modules.supervision;

import com.privateflow.modules.api.chat.ChatTaskConfig;
import com.privateflow.modules.api.chat.PendingReplyTaskRepository;
import com.privateflow.modules.llm.LlmCallAnalyticsRepository;
import com.privateflow.modules.skill.admin.SkillCallAnalyticsRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupervisionCleanupScheduler {

  private final SupervisionConfig supervisionConfig;
  private final ChatTaskConfig chatTaskConfig;
  private final SupervisionEventRepository supervisionEventRepository;
  private final LlmCallAnalyticsRepository llmCallAnalyticsRepository;
  private final SkillCallAnalyticsRepository skillCallAnalyticsRepository;
  private final PendingReplyTaskRepository pendingReplyTaskRepository;

  public SupervisionCleanupScheduler(
      SupervisionConfig supervisionConfig,
      ChatTaskConfig chatTaskConfig,
      SupervisionEventRepository supervisionEventRepository,
      LlmCallAnalyticsRepository llmCallAnalyticsRepository,
      SkillCallAnalyticsRepository skillCallAnalyticsRepository,
      PendingReplyTaskRepository pendingReplyTaskRepository) {
    this.supervisionConfig = supervisionConfig;
    this.chatTaskConfig = chatTaskConfig;
    this.supervisionEventRepository = supervisionEventRepository;
    this.llmCallAnalyticsRepository = llmCallAnalyticsRepository;
    this.skillCallAnalyticsRepository = skillCallAnalyticsRepository;
    this.pendingReplyTaskRepository = pendingReplyTaskRepository;
  }

  @Scheduled(cron = "0 10 4 * * *")
  public void cleanup() {
    cleanupAt(LocalDateTime.now());
  }

  public void cleanupAt(LocalDateTime now) {
    Objects.requireNonNull(now, "cleanup time is required");
    supervisionEventRepository.deleteEventsBefore(now.minusDays(supervisionConfig.recordRetentionDays()));
    llmCallAnalyticsRepository.deleteBefore(now.minusDays(supervisionConfig.technicalLogRetentionDays()));
    skillCallAnalyticsRepository.deleteBefore(now.minusDays(supervisionConfig.technicalLogRetentionDays()));
    pendingReplyTaskRepository.recoverExpiredAndStalledTasks(
        now,
        chatTaskConfig.pendingReplyGeneratingTimeoutSeconds());
    pendingReplyTaskRepository.deletePhysicallyExpiredBefore(
        now.minusDays(supervisionConfig.expiredReplyTaskRetentionDays()));
  }
}
