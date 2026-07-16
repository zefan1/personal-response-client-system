package com.privateflow.modules.analytics;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TagAnalyticsResponse(
    Summary summary,
    List<CategoryRow> categories,
    List<TagRow> tags,
    List<DimensionRow> stores,
    List<DimensionRow> teams,
    List<DimensionRow> employees,
    List<SourceRow> tagSources,
    List<ReasonRow> unupdatedReasons,
    List<TrendRow> trend,
    FilterOptions filterOptions,
    AppliedWindow appliedWindow) {

  public TagAnalyticsResponse {
    categories = copy(categories);
    tags = copy(tags);
    stores = copy(stores);
    teams = copy(teams);
    employees = copy(employees);
    tagSources = copy(tagSources);
    unupdatedReasons = copy(unupdatedReasons);
    trend = copy(trend);
  }

  public static TagAnalyticsResponse empty(TagAnalyticsWindow window) {
    return new TagAnalyticsResponse(
        new Summary(0, 0, 0, 0.0, 0, 0, 0),
        List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
        FilterOptions.empty(),
        new AppliedWindow(window.from(), window.to(), window.granularity()));
  }

  private static <T> List<T> copy(List<T> values) {
    return values == null ? List.of() : List.copyOf(values);
  }

  public record Summary(
      long matchedCustomerCount,
      long taggedCustomerCount,
      long activeAssignmentCount,
      double coverageRate,
      long systemAddedCount,
      long manualAddedOrChangedCount,
      long systemDecidedNoUpdateCount) {
  }

  public record CategoryRow(
      long categoryId,
      String categoryKey,
      String categoryName,
      long activeAssignmentCount,
      long taggedCustomerCount) {
  }

  public record TagRow(
      long categoryId,
      String categoryKey,
      String categoryName,
      long valueId,
      String valueCode,
      String displayName,
      long activeAssignmentCount,
      long taggedCustomerCount) {
  }

  public record DimensionRow(
      String key,
      String label,
      long activeAssignmentCount,
      long taggedCustomerCount) {
  }

  public record SourceRow(
      String sourceType,
      String sourceLabel,
      long addedAssignmentCount,
      long affectedCustomerCount) {
  }

  public record ReasonRow(
      String reasonCode,
      String reasonLabel,
      String scope,
      long customerCount,
      long decisionCount,
      String sampleReason) {
  }

  public record TrendRow(
      LocalDate date,
      long addedAssignmentCount,
      long invalidatedAssignmentCount,
      long netChange,
      long systemAddedCount,
      long manualAddedOrChangedCount) {
  }

  public record ValueOption(String value, String label) {
  }

  public record TeamOption(long leaderId, String label) {
  }

  public record EmployeeOption(String account, String label, Long leaderId) {
  }

  public record FilterOptions(
      List<ValueOption> stores,
      List<TeamOption> teams,
      List<EmployeeOption> employees,
      List<ValueOption> customerSources,
      List<ValueOption> tagSources) {

    public FilterOptions {
      stores = copy(stores);
      teams = copy(teams);
      employees = copy(employees);
      customerSources = copy(customerSources);
      tagSources = copy(tagSources);
    }

    public static FilterOptions empty() {
      return new FilterOptions(List.of(), List.of(), List.of(), List.of(), List.of());
    }
  }

  public record AppliedWindow(
      LocalDateTime tagFrom,
      LocalDateTime tagTo,
      TagTrendGranularity granularity) {
  }
}
