package com.privateflow.modules.match.service;

import com.privateflow.modules.match.config.MatchConfigProvider;
import java.util.Comparator;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class TagRemovalProcessor {

  private final MatchConfigProvider configProvider;

  public TagRemovalProcessor(MatchConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public String clean(String nickname) {
    if (nickname == null) {
      return "";
    }
    String trimmed = nickname.trim();
    if (trimmed.isBlank()) {
      return "";
    }
    String upper = trimmed.toUpperCase(Locale.ROOT);
    for (String rule : configProvider.get().tagRemovalRules().stream()
        .sorted(Comparator.comparingInt(String::length).reversed())
        .toList()) {
      String prefix = rule.trim();
      if (!prefix.isEmpty() && upper.startsWith(prefix.toUpperCase(Locale.ROOT))) {
        return normalize(trimmed.substring(prefix.length()));
      }
    }
    return normalize(trimmed);
  }

  private String normalize(String value) {
    String cleaned = value == null ? "" : value.trim();
    return cleaned.replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "").trim();
  }
}
