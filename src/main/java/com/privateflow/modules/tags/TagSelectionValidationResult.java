package com.privateflow.modules.tags;

import java.util.List;

public record TagSelectionValidationResult(
    boolean accepted,
    TagSelectionValidationReason reason,
    String message,
    TagCategory category,
    List<TagValue> values
) {

  public TagSelectionValidationResult {
    values = values == null ? List.of() : List.copyOf(values);
  }

  public String reasonCode() {
    return reason.name();
  }

  static TagSelectionValidationResult accepted(TagCategory category, List<TagValue> values) {
    return create(TagSelectionValidationReason.ACCEPTED, category, values);
  }

  static TagSelectionValidationResult rejected(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values) {
    return create(reason, category, values);
  }

  private static TagSelectionValidationResult create(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values) {
    return new TagSelectionValidationResult(
        reason == TagSelectionValidationReason.ACCEPTED,
        reason,
        reason.message(),
        category,
        values);
  }
}
