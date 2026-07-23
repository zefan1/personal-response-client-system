package com.privateflow.modules.supervision;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SupervisionMetricsService {

  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
  private static final List<String> METRIC_KEYS = List.of(
      "AI_USAGE_RATE",
      "AI_COVERAGE",
      "PROCESSING_EFFICIENCY",
      "EMPLOYEE_CONVERSION",
      "AI_ASSOCIATED_CONVERSION");

  private final SupervisionMetricsRepository repository;
  private final SupervisionConfig config;
  private final Clock clock;

  public SupervisionMetricsService(
      SupervisionMetricsRepository repository,
      SupervisionConfig config) {
    this(repository, config, Clock.system(BUSINESS_ZONE));
  }

  SupervisionMetricsService(
      SupervisionMetricsRepository repository,
      SupervisionConfig config,
      Clock clock) {
    this.repository = repository;
    this.config = config;
    this.clock = clock;
  }

  public Map<String, SupervisionMetric> report(SupervisionMetricsQuery query) {
    requireAdministrator();
    return calculate(query);
  }

  @Scheduled(cron = "0 20 4 1 * *", zone = "Asia/Shanghai")
  public void snapshotCurrentMonth() {
    snapshotCurrentMonthAt(LocalDateTime.ofInstant(clock.instant(), BUSINESS_ZONE));
  }

  void snapshotCurrentMonthAt(LocalDateTime now) {
    if (now == null) {
      throw new IllegalArgumentException("snapshot time is required");
    }
    LocalDate monthStart = YearMonth.from(now).atDay(1);
    SupervisionMetricsQuery all = new SupervisionMetricsQuery(
        monthStart.atStartOfDay(), now, null, null, null);
    LocalDate metricMonth = monthStart;
    writeSnapshot(metricMonth, "ALL", "ALL", calculate(all), now);
    for (String username : repository.operatorUsernames(all)) {
      writeSnapshot(metricMonth, "OPERATOR", username,
          calculate(new SupervisionMetricsQuery(
              all.fromInclusive(), all.toExclusive(), username, null, null)), now);
    }
    for (String channel : repository.channelCodes(all)) {
      writeSnapshot(metricMonth, "CHANNEL", channel,
          calculate(new SupervisionMetricsQuery(
              all.fromInclusive(), all.toExclusive(), null, channel, null)), now);
    }
    for (String source : repository.leadSources(all)) {
      writeSnapshot(metricMonth, "LEAD_SOURCE", source,
          calculate(new SupervisionMetricsQuery(
              all.fromInclusive(), all.toExclusive(), null, null, source)), now);
    }
  }

  private Map<String, SupervisionMetric> calculate(SupervisionMetricsQuery query) {
    SupervisionConfig.Settings settings = config.snapshot();
    boolean targetConfigured = !settings.conversionTargetStages().isEmpty();
    Map<String, SupervisionMetric> metrics = new LinkedHashMap<>();
    metrics.put("AI_USAGE_RATE", SupervisionMetric.of(
        repository.aiUsageRate(query),
        "AI copied customers",
        "AI generated customers",
        true));
    metrics.put("AI_COVERAGE", SupervisionMetric.of(
        repository.aiCoverage(query),
        "Customers with AI replies",
        "Customers entering pending work",
        true));
    metrics.put("PROCESSING_EFFICIENCY", SupervisionMetric.of(
        repository.processingEfficiency(query, settings.processingSlaMinutes()),
        "Customers handled within SLA",
        "Customers entering pending work",
        true));
    metrics.put("EMPLOYEE_CONVERSION", SupervisionMetric.of(
        repository.employeeConversion(query, settings.conversionTargetStages()),
        "Customers at configured target stages",
        "Assigned customers",
        targetConfigured));
    metrics.put("AI_ASSOCIATED_CONVERSION", SupervisionMetric.of(
        repository.aiAssociatedConversion(query, settings.conversionTargetStages()),
        "AI-used customers at configured target stages",
        "AI copied customers",
        targetConfigured));
    return Map.copyOf(metrics);
  }

  private void writeSnapshot(
      LocalDate metricMonth,
      String dimensionType,
      String dimensionValue,
      Map<String, SupervisionMetric> metrics,
      LocalDateTime generatedAt) {
    for (String key : METRIC_KEYS) {
      repository.upsertMonthlyMetric(
          metricMonth,
          dimensionType,
          dimensionValue,
          key,
          metrics.get(key),
          generatedAt);
    }
  }

  private void requireAdministrator() {
    AuthUser user = AuthContext.current();
    if (user == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "authentication is required");
    }
    if (user.role() != Role.ADMIN) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "administrator permission is required");
    }
  }
}
