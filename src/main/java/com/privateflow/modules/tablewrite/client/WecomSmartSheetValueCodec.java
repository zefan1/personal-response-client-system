package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetValueCodec {

  private static final ObjectMapper JSON = new ObjectMapper();
  private final ZoneId zoneId;

  public WecomSmartSheetValueCodec(WecomSmartSheetConfig config) {
    this.zoneId = config.zoneId();
  }

  public String decode(WecomSmartSheetField field, JsonNode value) {
    requireField(field);
    if (value == null || value.isNull() || value.isMissingNode()) {
      return "";
    }
    return switch (field.type()) {
      case "FIELD_TYPE_TEXT" -> text(value);
      case "FIELD_TYPE_NUMBER" -> numberText(value);
      case "FIELD_TYPE_CHECKBOX" -> value.isBoolean() ? Boolean.toString(value.booleanValue()) : compact(value);
      case "FIELD_TYPE_DATE_TIME" -> dateText(field, value);
      case "FIELD_TYPE_SELECT", "FIELD_TYPE_SINGLE_SELECT" -> selectText(value);
      case "FIELD_TYPE_PHONE_NUMBER", "FIELD_TYPE_EMAIL" -> value.isTextual() ? value.textValue() : compact(value);
      default -> compact(value);
    };
  }

  public JsonNode encode(WecomSmartSheetField field, Object value) {
    requireField(field);
    if (value == null) {
      throw invalid(field);
    }
    if (!field.writable()) {
      throw invalid(field);
    }
    return switch (field.type()) {
      case "FIELD_TYPE_TEXT" -> encodeText(value);
      case "FIELD_TYPE_PHONE_NUMBER", "FIELD_TYPE_EMAIL" -> JsonNodeFactory.instance.textNode(textValue(value));
      case "FIELD_TYPE_NUMBER" -> encodeNumber(field, value);
      case "FIELD_TYPE_CHECKBOX" -> JsonNodeFactory.instance.booleanNode(checkbox(field, value));
      case "FIELD_TYPE_DATE_TIME" -> encodeDate(field, value);
      case "FIELD_TYPE_SINGLE_SELECT" -> encodeSingleSelect(field, value);
      case "FIELD_TYPE_SELECT" -> encodeMultiSelect(field, value);
      default -> throw invalid(field);
    };
  }

  private static String text(JsonNode value) {
    if (!value.isArray()) {
      return compact(value);
    }
    StringBuilder result = new StringBuilder();
    for (JsonNode part : value) {
      if (part.isObject() && part.path("type").isTextual() && part.path("text").isTextual()) {
        result.append(part.path("text").textValue());
      }
    }
    return result.toString();
  }

  private static String numberText(JsonNode value) {
    if (!value.isNumber()) {
      return compact(value);
    }
    return value.decimalValue().stripTrailingZeros().toPlainString();
  }

  private String dateText(WecomSmartSheetField field, JsonNode value) {
    try {
      long millis = value.isIntegralNumber() ? value.longValue() : Long.parseLong(value.textValue());
      LocalDateTime local = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), zoneId);
      return field.dateTimeIncludesTime() ? local.withNano(0).toString() : local.toLocalDate().toString();
    } catch (RuntimeException ex) {
      return compact(value);
    }
  }

  private static String selectText(JsonNode value) {
    if (!value.isArray()) {
      return compact(value);
    }
    List<String> texts = new ArrayList<>();
    for (JsonNode item : value) {
      if (item.isObject() && item.path("text").isTextual()) {
        texts.add(item.path("text").textValue());
      }
    }
    return String.join("、", texts);
  }

  private static ArrayNode encodeText(Object value) {
    ObjectNode part = JsonNodeFactory.instance.objectNode();
    part.put("type", "text");
    part.put("text", textValue(value));
    return JsonNodeFactory.instance.arrayNode().add(part);
  }

  private static JsonNode encodeNumber(WecomSmartSheetField field, Object value) {
    try {
      if (value instanceof Number number) {
        return JsonNodeFactory.instance.numberNode(new BigDecimal(number.toString()));
      }
      String text = textValue(value).trim();
      if (text.isEmpty()) {
        throw invalid(field);
      }
      return JsonNodeFactory.instance.numberNode(new BigDecimal(text));
    } catch (NumberFormatException ex) {
      throw invalid(field);
    }
  }

  private static boolean checkbox(WecomSmartSheetField field, Object value) {
    if (value instanceof Boolean checked) {
      return checked;
    }
    if (value instanceof String text) {
      if ("true".equals(text.trim())) {
        return true;
      }
      if ("false".equals(text.trim())) {
        return false;
      }
    }
    throw invalid(field);
  }

  private JsonNode encodeDate(WecomSmartSheetField field, Object value) {
    String text = value instanceof String string ? string.trim() : null;
    if (text != null && text.isEmpty()) {
      return JsonNodeFactory.instance.textNode("");
    }
    try {
      Instant instant;
      if (value instanceof LocalDate date) {
        instant = date.atStartOfDay(zoneId).toInstant();
      } else if (value instanceof LocalDateTime dateTime) {
        instant = dateTime.atZone(zoneId).toInstant();
      } else if (text != null) {
        instant = field.dateTimeIncludesTime()
            ? LocalDateTime.parse(text).atZone(zoneId).toInstant()
            : LocalDate.parse(text).atStartOfDay(zoneId).toInstant();
      } else {
        throw invalid(field);
      }
      return JsonNodeFactory.instance.textNode(Long.toString(instant.toEpochMilli()));
    } catch (RuntimeException ex) {
      if (ex instanceof IllegalArgumentException) {
        throw (IllegalArgumentException) ex;
      }
      throw invalid(field);
    }
  }

  private static ArrayNode encodeSingleSelect(WecomSmartSheetField field, Object value) {
    String option = textValue(value).trim();
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    if (option.isEmpty()) {
      return result;
    }
    result.add(option(field, option));
    return result;
  }

  private static ArrayNode encodeMultiSelect(WecomSmartSheetField field, Object value) {
    List<String> names = new ArrayList<>();
    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (item == null) {
          throw invalid(field);
        }
        names.add(textValue(item).trim());
      }
    } else {
      String text = textValue(value).trim();
      if (!text.isEmpty()) {
        for (String item : text.split("、", -1)) {
          names.add(item.trim());
        }
      }
    }
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    for (String name : names) {
      if (name.isEmpty()) {
        throw invalid(field);
      }
      result.add(option(field, name));
    }
    return result;
  }

  private static ObjectNode option(WecomSmartSheetField field, String name) {
    String id = field.optionId(name).orElseThrow(() -> invalid(field));
    return JsonNodeFactory.instance.objectNode().put("id", id);
  }

  private static String textValue(Object value) {
    return value instanceof CharSequence sequence ? sequence.toString() : String.valueOf(value);
  }

  private static void requireField(WecomSmartSheetField field) {
    if (field == null) {
      throw new IllegalArgumentException("Field is required");
    }
  }

  private static IllegalArgumentException invalid(WecomSmartSheetField field) {
    return new IllegalArgumentException("Invalid value for field: " + field.title());
  }

  private static String compact(JsonNode value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception ex) {
      return "";
    }
  }
}
