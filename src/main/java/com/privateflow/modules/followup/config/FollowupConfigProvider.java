package com.privateflow.modules.followup.config;

import com.privateflow.common.events.ConfigChangedEvent;
import com.privateflow.modules.customer.infra.SystemConfigRepository;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class FollowupConfigProvider {

  private final SystemConfigRepository configRepository;
  private final AtomicReference<FollowupConfig> current;

  public FollowupConfigProvider(
      SystemConfigRepository configRepository,
      @Value("${followup.full-scan-cron:0 0 9 * * *}") String fullScanCron,
      @Value("${followup.lightweight-scan-cron:0 0 * * * *}") String lightweightScanCron,
      @Value("${followup.rule-refresh-interval-s:30}") int ruleRefreshIntervalS,
      @Value("${followup.tuan-alert-hours:24}") int tuanAlertHours,
      @Value("${followup.xiansuo-alert-hours:72}") int xiansuoAlertHours,
      @Value("${followup.pending-alert-hours:24}") int pendingAlertHours,
      @Value("${followup.sleep-risk-days:7}") int sleepRiskDays,
      @Value("${followup.loss-risk-days:14}") int lossRiskDays,
      @Value("${followup.appointment-remind-hours:24}") int appointmentRemindHours,
      @Value("${followup.scan-batch-size:5000}") int scanBatchSize,
      @Value("${followup.scan-timeout-s:300}") int scanTimeoutS,
      @Value("${followup.reminder-dedup-days:1}") int reminderDedupDays,
      @Value("${followup.tag-suggestion-dedup-days:7}") int tagSuggestionDedupDays,
      @Value("${followup.cursor-ttl-s:3600}") int cursorTtlS,
      @Value("${followup.keeper-overdue-leader-hours:48}") int keeperOverdueLeaderHours) {
    this.configRepository = configRepository;
    this.current = new AtomicReference<>(new FollowupConfig(
        fullScanCron,
        lightweightScanCron,
        clamp(ruleRefreshIntervalS, 10, 300),
        clamp(tuanAlertHours, 12, 168),
        clamp(xiansuoAlertHours, 24, 336),
        clamp(pendingAlertHours, 12, 168),
        clamp(sleepRiskDays, 3, 30),
        clamp(lossRiskDays, 7, 60),
        clamp(appointmentRemindHours, 1, 72),
        clamp(scanBatchSize, 1000, 10000),
        clamp(scanTimeoutS, 120, 600),
        clamp(reminderDedupDays, 1, 3),
        clamp(tagSuggestionDedupDays, 3, 30),
        clamp(cursorTtlS, 600, 7200),
        clamp(keeperOverdueLeaderHours, 24, 168)));
  }

  @PostConstruct
  public void load() {
    refresh();
  }

  public FollowupConfig get() {
    return current.get();
  }

  @EventListener
  public void onConfigChanged(ConfigChangedEvent event) {
    if (event.configKey() != null && event.configKey().startsWith("followup.")) {
      refresh();
    }
  }

  public void refresh() {
    FollowupConfig previous = current.get();
    Map<String, String> values = configRepository.findByPrefix("followup.");
    current.set(new FollowupConfig(
        string(values.get("followup.full_scan_cron"), previous.fullScanCron()),
        string(values.get("followup.lightweight_scan_cron"), previous.lightweightScanCron()),
        integer(values.get("followup.rule_refresh_interval_s"), previous.ruleRefreshIntervalS(), 10, 300),
        integer(values.get("followup.tuan_alert_hours"), previous.tuanAlertHours(), 12, 168),
        integer(values.get("followup.xiansuo_alert_hours"), previous.xiansuoAlertHours(), 24, 336),
        integer(values.get("followup.pending_alert_hours"), previous.pendingAlertHours(), 12, 168),
        integer(values.get("followup.sleep_risk_days"), previous.sleepRiskDays(), 3, 30),
        integer(values.get("followup.loss_risk_days"), previous.lossRiskDays(), 7, 60),
        integer(values.get("followup.appointment_remind_hours"), previous.appointmentRemindHours(), 1, 72),
        integer(values.get("followup.scan_batch_size"), previous.scanBatchSize(), 1000, 10000),
        integer(values.get("followup.scan_timeout_s"), previous.scanTimeoutS(), 120, 600),
        integer(values.get("followup.reminder_dedup_days"), previous.reminderDedupDays(), 1, 3),
        integer(values.get("followup.tag_suggestion_dedup_days"), previous.tagSuggestionDedupDays(), 3, 30),
        integer(values.get("followup.cursor_ttl_s"), previous.cursorTtlS(), 600, 7200),
        integer(values.get("followup.keeper_overdue_leader_hours"), previous.keeperOverdueLeaderHours(), 24, 168)));
  }

  private String string(String raw, String fallback) {
    return raw == null || raw.isBlank() ? fallback : raw;
  }

  private int integer(String raw, int fallback, int min, int max) {
    try {
      return raw == null || raw.isBlank() ? fallback : clamp(Integer.parseInt(raw.trim()), min, max);
    } catch (NumberFormatException ex) {
      return fallback;
    }
  }

  private static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }
}
