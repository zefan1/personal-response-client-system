package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;

/** Normalizes the field-title variants returned by Smart Sheet APIs. */
public final class WecomSmartSheetFieldMetadata {

  private static final String[] TITLE_KEYS = {"field_title", "title", "name"};

  private WecomSmartSheetFieldMetadata() {
  }

  public static String title(JsonNode field) {
    if (field == null || !field.isObject()) {
      return "";
    }
    for (String key : TITLE_KEYS) {
      JsonNode value = field.get(key);
      if (value != null && value.isTextual() && !value.textValue().trim().isEmpty()) {
        return value.textValue().trim();
      }
    }
    return "";
  }

  public static String requireTitle(JsonNode field) {
    String title = title(field);
    if (title.isEmpty()) {
      throw new IllegalStateException("WeCom visible field catalog was invalid");
    }
    return title;
  }
}
