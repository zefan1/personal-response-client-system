package com.privateflow.modules.supervision;

import com.privateflow.modules.llm.LlmCallAnalyticsRepository;
import com.privateflow.modules.skill.admin.SkillCallAnalyticsRepository;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SupervisionCleanupScheduler {

  private final SupervisionConfig supervisionConfig;
  private final SupervisionEventRepository supervisionEventRepository;
  private final LlmCallAnalyticsRepository llmCallAnalyticsRepository;
  private final SkillCallAnalyticsRepository skillCallAnalyticsRepository;

  public SupervisionCleanupScheduler(
      SupervisionConfig supervisionConfig,
      SupervisionEventRepository supervisionEventRepository,
      LlmCallAnalyticsRepository llmCallAnalyticsRepository,
      SkillCallAnalyticsRepository skillCallAnalyticsRepository) {
    this.supervisionConfig = supervisionConfig;
    this.supervisionEventRepository = supervisionEventRepository;
    this.llmCallAnalyticsRepository = llmCallAnalyticsRepository;
    this.skillCallAnalyticsRepository = skillCallAnalyticsRepository;
  }

  @Scheduled(cron = "0 10 4 * * *", zone = "Asia/Shanghai")
  public void cleanup() {
    cleanupAt(LocalDateTime.now());
  }

  public void cleanupAt(LocalDateTime now) {
    Objects.requireNonNull(now, "cleanup time is required");
    SupervisionConfig.Settings settings = supervisionConfig.snapshot();
    supervisionEventRepository.deleteEventsBefore(now.minusDays(settings.recordRetentionDays()));
    llmCallAnalyticsRepository.deleteBefore(now.minusDays(settings.technicalLogRetentionDays()));
    skillCallAnalyticsRepository.deleteBefore(now.minusDays(settings.technicalLogRetentionDays()));
  }
}
