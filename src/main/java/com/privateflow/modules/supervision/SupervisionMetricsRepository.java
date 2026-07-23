package com.privateflow.modules.supervision;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SupervisionMetricsRepository {

  private final JdbcTemplate jdbcTemplate;

  public SupervisionMetricsRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Counts aiUsageRate(SupervisionMetricsQuery query) {
    return new Counts(
        countCandidateCustomersInBaseline(
            query, List.of("REPLY_COPIED"), List.of("REPLY_GENERATED")),
        countDistinctEventCustomers(query, List.of("REPLY_GENERATED")));
  }

  public Counts aiCoverage(SupervisionMetricsQuery query) {
    return new Counts(
        countCandidateCustomersInBaseline(
            query, List.of("REPLY_GENERATED", "REPLY_COPIED"), List.of("PENDING_ENTERED")),
        countDistinctEventCustomers(query, List.of("PENDING_ENTERED")));
  }

  public Counts processingEfficiency(SupervisionMetricsQuery query, int slaMinutes) {
    if (slaMinutes <= 0) {
      throw new IllegalArgumentException("processing SLA minutes must be positive");
    }
    long denominator = countDistinctEventCustomers(query, List.of("PENDING_ENTERED"));
    List<Object> arguments = new ArrayList<>();
    String pendingFilters = eventFilters("pending", query, arguments);
    arguments.add(slaMinutes);
    String responseFilters = eventFilters("response", query, arguments);
    String sql = """
        SELECT COUNT(DISTINCT pending.customer_phone)
        FROM supervision_events pending
        WHERE pending.event_type = 'PENDING_ENTERED'
          AND pending.customer_phone IS NOT NULL
          AND %s
          AND EXISTS (
            SELECT 1
            FROM supervision_events response
            WHERE response.customer_phone = pending.customer_phone
              AND response.event_type IN ('REPLY_GENERATED', 'REPLY_COPIED')
              AND response.occurred_at >= pending.occurred_at
              AND response.occurred_at <= TIMESTAMPADD(MINUTE, ?, pending.occurred_at)
              AND %s
          )
        """.formatted(pendingFilters, responseFilters);
    return new Counts(queryCount(sql, arguments), denominator);
  }

  public Counts employeeConversion(SupervisionMetricsQuery query, Collection<String> targetStages) {
    long denominator = countCustomers(query);
    if (targetStages == null || targetStages.isEmpty()) {
      return new Counts(0, denominator);
    }
    List<Object> arguments = new ArrayList<>();
    String filters = assignedCustomerFilters("customer", query, arguments);
    String placeholders = placeholders(targetStages, arguments);
    String sql = """
        SELECT COUNT(DISTINCT customer.phone)
        FROM customers customer
        WHERE %s
          AND customer.customer_stage IN (%s)
        """.formatted(filters, placeholders);
    return new Counts(queryCount(sql, arguments), denominator);
  }

  public Counts aiAssociatedConversion(
      SupervisionMetricsQuery query,
      Collection<String> targetStages) {
    long denominator = countDistinctEventCustomers(query, List.of("REPLY_COPIED"));
    if (targetStages == null || targetStages.isEmpty()) {
      return new Counts(0, denominator);
    }
    List<Object> arguments = new ArrayList<>();
    String filters = eventFilters("event", query, arguments);
    String placeholders = placeholders(targetStages, arguments);
    String sql = """
        SELECT COUNT(DISTINCT event.customer_phone)
        FROM supervision_events event
        JOIN customers customer ON customer.phone = event.customer_phone
        WHERE event.event_type = 'REPLY_COPIED'
          AND event.customer_phone IS NOT NULL
          AND %s
          AND customer.customer_stage IN (%s)
        """.formatted(filters, placeholders);
    return new Counts(queryCount(sql, arguments), denominator);
  }

  public List<String> operatorUsernames(SupervisionMetricsQuery query) {
    return dimensionValues(query, "operator_username", "assigned_keeper");
  }

  public List<String> channelCodes(SupervisionMetricsQuery query) {
    return dimensionValues(query, "channel_code", "source_channel");
  }

  public List<String> leadSources(SupervisionMetricsQuery query) {
    return dimensionValues(query, "lead_source", "source_table");
  }

  public void upsertMonthlyMetric(
      LocalDate metricMonth,
      String dimensionType,
      String dimensionValue,
      String metricKey,
      SupervisionMetric metric,
      LocalDateTime generatedAt) {
    jdbcTemplate.update("""
        INSERT INTO supervision_monthly_metric_snapshots (
          metric_month,
          dimension_type,
          dimension_value,
          metric_key,
          numerator,
          denominator,
          ratio,
          generated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
          numerator = VALUES(numerator),
          denominator = VALUES(denominator),
          ratio = VALUES(ratio),
          generated_at = VALUES(generated_at)
        """,
        Date.valueOf(metricMonth),
        dimensionType,
        dimensionValue,
        metricKey,
        metric.numerator(),
        metric.denominator(),
        metric.rate(),
        Timestamp.valueOf(generatedAt));
  }

  private long countDistinctEventCustomers(
      SupervisionMetricsQuery query,
      Collection<String> eventTypes) {
    List<Object> arguments = new ArrayList<>();
    String placeholders = placeholders(eventTypes, arguments);
    String filters = eventFilters("event", query, arguments);
    String sql = """
        SELECT COUNT(DISTINCT event.customer_phone)
        FROM supervision_events event
        WHERE event.customer_phone IS NOT NULL
          AND event.event_type IN (%s)
          AND %s
        """.formatted(placeholders, filters);
    return queryCount(sql, arguments);
  }

  private long countCandidateCustomersInBaseline(
      SupervisionMetricsQuery query,
      Collection<String> candidateTypes,
      Collection<String> baselineTypes) {
    List<Object> arguments = new ArrayList<>();
    String candidatePlaceholders = placeholders(candidateTypes, arguments);
    String candidateFilters = eventFilters("candidate", query, arguments);
    String baselinePlaceholders = placeholders(baselineTypes, arguments);
    String baselineFilters = eventFilters("baseline", query, arguments);
    String sql = """
        SELECT COUNT(DISTINCT candidate.customer_phone)
        FROM supervision_events candidate
        WHERE candidate.customer_phone IS NOT NULL
          AND candidate.event_type IN (%s)
          AND %s
          AND EXISTS (
            SELECT 1
            FROM supervision_events baseline
            WHERE baseline.customer_phone = candidate.customer_phone
              AND baseline.event_type IN (%s)
              AND %s
          )
        """.formatted(
        candidatePlaceholders,
        candidateFilters,
        baselinePlaceholders,
        baselineFilters);
    return queryCount(sql, arguments);
  }

  private long countCustomers(SupervisionMetricsQuery query) {
    List<Object> arguments = new ArrayList<>();
    String filters = assignedCustomerFilters("customer", query, arguments);
    return queryCount("SELECT COUNT(DISTINCT customer.phone) FROM customers customer WHERE " + filters,
        arguments);
  }

  private String assignedCustomerFilters(
      String alias,
      SupervisionMetricsQuery query,
      List<Object> arguments) {
    return customerFilters(alias, query, arguments)
        + " AND " + alias + ".assigned_keeper IS NOT NULL"
        + " AND " + alias + ".assigned_keeper <> ''";
  }

  private List<String> dimensionValues(
      SupervisionMetricsQuery query,
      String eventColumn,
      String customerColumn) {
    Set<String> values = new LinkedHashSet<>();
    List<Object> eventArguments = new ArrayList<>();
    String eventFilters = eventFilters("event", query, eventArguments);
    values.addAll(jdbcTemplate.queryForList("""
        SELECT DISTINCT event.%s
        FROM supervision_events event
        WHERE event.%s IS NOT NULL
          AND event.%s <> ''
          AND %s
        """.formatted(eventColumn, eventColumn, eventColumn, eventFilters),
        String.class,
        eventArguments.toArray()));

    List<Object> customerArguments = new ArrayList<>();
    String customerFilters = customerFilters("customer", query, customerArguments);
    values.addAll(jdbcTemplate.queryForList("""
        SELECT DISTINCT customer.%s
        FROM customers customer
        WHERE customer.%s IS NOT NULL
          AND customer.%s <> ''
          AND %s
        """.formatted(customerColumn, customerColumn, customerColumn, customerFilters),
        String.class,
        customerArguments.toArray()));
    return values.stream().sorted().toList();
  }

  private String eventFilters(
      String alias,
      SupervisionMetricsQuery query,
      List<Object> arguments) {
    List<String> conditions = new ArrayList<>();
    conditions.add(alias + ".occurred_at >= ?");
    arguments.add(Timestamp.valueOf(query.fromInclusive()));
    conditions.add(alias + ".occurred_at < ?");
    arguments.add(Timestamp.valueOf(query.toExclusive()));
    addOptionalFilter(conditions, arguments, alias + ".operator_username", query.operatorUsername());
    addOptionalFilter(conditions, arguments, alias + ".channel_code", query.channelCode());
    addOptionalFilter(conditions, arguments, alias + ".lead_source", query.leadSource());
    return String.join(" AND ", conditions);
  }

  private String customerFilters(
      String alias,
      SupervisionMetricsQuery query,
      List<Object> arguments) {
    List<String> conditions = new ArrayList<>();
    conditions.add(alias + ".created_at >= ?");
    arguments.add(Timestamp.valueOf(query.fromInclusive()));
    conditions.add(alias + ".created_at < ?");
    arguments.add(Timestamp.valueOf(query.toExclusive()));
    addOptionalFilter(conditions, arguments, alias + ".assigned_keeper", query.operatorUsername());
    addOptionalFilter(conditions, arguments, alias + ".source_channel", query.channelCode());
    addOptionalFilter(conditions, arguments, alias + ".source_table", query.leadSource());
    return String.join(" AND ", conditions);
  }

  private void addOptionalFilter(
      List<String> conditions,
      List<Object> arguments,
      String field,
      String value) {
    if (value != null) {
      conditions.add(field + " = ?");
      arguments.add(value);
    }
  }

  private String placeholders(Collection<String> values, List<Object> arguments) {
    List<String> nonBlankValues = values.stream()
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
    if (nonBlankValues.isEmpty()) {
      throw new IllegalArgumentException("at least one value is required");
    }
    arguments.addAll(nonBlankValues);
    return String.join(", ", java.util.Collections.nCopies(nonBlankValues.size(), "?"));
  }

  private long queryCount(String sql, List<Object> arguments) {
    Long count = jdbcTemplate.queryForObject(sql, Long.class, arguments.toArray());
    return count == null ? 0L : count;
  }

  public record Counts(long numerator, long denominator) {
  }
}
