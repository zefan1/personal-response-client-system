package com.privateflow.modules.tags;

import java.util.List;

public record TagSelectionValidationResult(
    boolean accepted,
    TagSelectionValidationReason reason,
    String message,
    TagCategory category,
    List<TagValue> values,
    Object rejectedInput
) {

  public TagSelectionValidationResult {
    values = values == null ? List.of() : List.copyOf(values);
  }

  public String reasonCode() {
    return reason.name();
  }

  static TagSelectionValidationResult accepted(TagCategory category, List<TagValue> values) {
    return create(TagSelectionValidationReason.ACCEPTED, category, values, null);
  }

  static TagSelectionValidationResult rejected(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values,
      Object rejectedInput) {
    return create(reason, category, values, rejectedInput);
  }

  private static TagSelectionValidationResult create(
      TagSelectionValidationReason reason,
      TagCategory category,
      List<TagValue> values,
      Object rejectedInput) {
    return new TagSelectionValidationResult(
        reason == TagSelectionValidationReason.ACCEPTED,
        reason,
        rejectedInput == null ? reason.message() : reason.message() + "，失败输入：" + rejectedInput,
        category,
        values,
        rejectedInput);
  }
}
