package com.privateflow.modules.match.service;

import com.privateflow.modules.match.Confidence;
import com.privateflow.modules.match.config.MatchConfigProvider;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class ConfidenceEvaluator {

  private final MatchConfigProvider configProvider;

  public ConfidenceEvaluator(MatchConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public Confidence evaluate(String cleanedNickname, String dbNickname) {
    if (cleanedNickname == null || dbNickname == null) {
      return Confidence.MEDIUM;
    }
    String left = cleanedNickname.trim().toUpperCase(Locale.ROOT);
    String right = dbNickname.trim().toUpperCase(Locale.ROOT);
    if (left.length() < configProvider.get().confidenceMinLength() || left.isBlank() || right.isBlank()) {
      return Confidence.MEDIUM;
    }
    boolean substring = left.contains(right) || right.contains(left);
    int min = Math.min(left.length(), right.length());
    int max = Math.max(left.length(), right.length());
    double ratio = max == 0 ? 0d : (double) min / (double) max;
    return substring && ratio >= configProvider.get().confidenceRatioThreshold() ? Confidence.HIGH : Confidence.MEDIUM;
  }
}
