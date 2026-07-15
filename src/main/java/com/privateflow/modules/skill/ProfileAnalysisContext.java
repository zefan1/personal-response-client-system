package com.privateflow.modules.skill;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProfileAnalysisContext(
    long customerId,
    int customerVersion,
    int effectiveMessageCount,
    List<ConversationMessage> recentMessages,
    Map<String, Object> customerProfile,
    List<CurrentTag> currentTags,
    List<LockedCategory> lockedCategories,
    List<CategoryCandidate> candidateCategories
) {

  public ProfileAnalysisContext {
    recentMessages = recentMessages == null ? List.of() : List.copyOf(recentMessages);
    customerProfile = customerProfile == null ? Map.of() : Map.copyOf(customerProfile);
    currentTags = currentTags == null ? List.of() : List.copyOf(currentTags);
    lockedCategories = lockedCategories == null ? List.of() : List.copyOf(lockedCategories);
    candidateCategories = candidateCategories == null ? List.of() : List.copyOf(candidateCategories);
  }

  public static ProfileAnalysisContext empty() {
    return new ProfileAnalysisContext(0, 0, 0, List.of(), Map.of(), List.of(), List.of(), List.of());
  }

  public record ConversationMessage(String role, String text, String timestamp) {
  }

  public record CurrentTag(
      String categoryCode,
      String categoryName,
      String tagCode,
      String tagName,
      String sourceType
  ) {
  }

  public record LockedCategory(String categoryCode, String categoryName, String reason) {
  }

  public record CategoryCandidate(
      String categoryCode,
      String categoryName,
      String purpose,
      String selectionMode,
      String autoUpdateMode,
      BigDecimal minConfidence,
      int minEvidenceMessages,
      int cooldownHours,
      String uncertainPolicy,
      List<TagCandidate> values
  ) {
    public CategoryCandidate {
      values = values == null ? List.of() : List.copyOf(values);
    }
  }

  public record TagCandidate(
      String tagCode,
      String tagName,
      String meaning,
      String applicableWhen,
      String notApplicableWhen,
      String positiveExamples,
      String negativeExamples,
      List<String> synonyms
  ) {
    public TagCandidate {
      synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
    }
  }
}
