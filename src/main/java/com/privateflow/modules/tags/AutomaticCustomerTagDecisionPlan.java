package com.privateflow.modules.tags;

import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import java.util.List;

public record AutomaticCustomerTagDecisionPlan(
    TagCategory category,
    List<TagValue> values,
    List<CustomerTagAssignment> previousAssignments,
    TagAnalysisAction action,
    boolean accepted,
    String reason,
    TagAnalysisDecision analysisDecision
) {

  public AutomaticCustomerTagDecisionPlan {
    values = values == null ? List.of() : List.copyOf(values);
    previousAssignments = previousAssignments == null ? List.of() : List.copyOf(previousAssignments);
  }
}
