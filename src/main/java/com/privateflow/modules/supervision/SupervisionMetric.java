package com.privateflow.modules.supervision;

public record SupervisionMetric(
    long numerator,
    long denominator,
    double rate,
    String numeratorLabel,
    String denominatorLabel,
    boolean conversionTargetConfigured) {

  public static SupervisionMetric of(
      SupervisionMetricsRepository.Counts counts,
      String numeratorLabel,
      String denominatorLabel,
      boolean conversionTargetConfigured) {
    long denominator = counts.denominator();
    double rate = denominator == 0 ? 0.0 : (double) counts.numerator() / denominator;
    return new SupervisionMetric(
        counts.numerator(),
        denominator,
        rate,
        numeratorLabel,
        denominatorLabel,
        conversionTargetConfigured);
  }
}
