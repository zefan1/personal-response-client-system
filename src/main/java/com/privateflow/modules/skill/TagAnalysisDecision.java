package com.privateflow.modules.skill;

import java.math.BigDecimal;
import java.util.List;

public record TagAnalysisDecision(
    String categoryCode,
    List<String> tagCodes,
    BigDecimal confidence,
    String evidence,
    TagAnalysisResultType resultType,
    TagAnalysisAction requestedAction
) {
  public TagAnalysisDecision {
    tagCodes = tagCodes == null ? List.of() : List.copyOf(tagCodes);
  }
}
