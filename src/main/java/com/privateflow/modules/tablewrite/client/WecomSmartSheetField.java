package com.privateflow.modules.tablewrite.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record WecomSmartSheetField(
    String fieldId,
    String title,
    String type,
    Map<String, String> optionIdsByText,
    boolean dateTimeIncludesTime) {

  private static final Set<String> WRITABLE_TYPES = Set.of(
      "FIELD_TYPE_TEXT",
      "FIELD_TYPE_PHONE_NUMBER",
      "FIELD_TYPE_NUMBER",
      "FIELD_TYPE_CHECKBOX",
      "FIELD_TYPE_DATE_TIME",
      "FIELD_TYPE_SINGLE_SELECT",
      "FIELD_TYPE_SELECT",
      "FIELD_TYPE_EMAIL");

  public WecomSmartSheetField {
    fieldId = required(fieldId);
    title = required(title);
    type = required(type);
    optionIdsByText = Map.copyOf(new LinkedHashMap<>(optionIdsByText == null ? Map.of() : optionIdsByText));
  }

  public boolean writable() {
    return WRITABLE_TYPES.contains(type);
  }

  public Optional<String> optionId(String text) {
    if (text == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(optionIdsByText.get(text.trim()));
  }

  private static String required(String value) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Field metadata must not be blank");
    }
    return value.trim();
  }
}
