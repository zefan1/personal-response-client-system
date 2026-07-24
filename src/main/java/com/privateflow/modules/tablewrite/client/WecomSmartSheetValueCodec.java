package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetValueCodec {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final DateTimeFormatter DATE_TIME_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
  private static final DateTimeFormatter DATE_TIME_MILLIS_FORMATTER =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS");
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
    try {
      if (value == null || !field.writable()) {
        throw invalid(field);
      }
      return switch (field.type()) {
        case "FIELD_TYPE_TEXT" -> encodeText(field, value);
        case "FIELD_TYPE_PHONE_NUMBER", "FIELD_TYPE_EMAIL" ->
            JsonNodeFactory.instance.textNode(stringValue(field, value));
        case "FIELD_TYPE_NUMBER" -> encodeNumber(field, value);
        case "FIELD_TYPE_CHECKBOX" -> JsonNodeFactory.instance.booleanNode(checkbox(field, value));
        case "FIELD_TYPE_DATE_TIME" -> encodeDate(field, value);
        case "FIELD_TYPE_SINGLE_SELECT" -> encodeSingleSelect(field, value);
        case "FIELD_TYPE_SELECT" -> encodeMultiSelect(field, value);
        default -> throw invalid(field);
      };
    } catch (RuntimeException ex) {
      throw invalid(field);
    }
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
      if (!field.dateTimeIncludesTime()) {
        return local.toLocalDate().toString();
      }
      return local.getNano() == 0 ? DATE_TIME_FORMATTER.format(local) : DATE_TIME_MILLIS_FORMATTER.format(local);
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

  private static ArrayNode encodeText(WecomSmartSheetField field, Object value) {
    ObjectNode part = JsonNodeFactory.instance.objectNode();
    part.put("type", "text");
    part.put("text", stringValue(field, value));
    return JsonNodeFactory.instance.arrayNode().add(part);
  }

  private static JsonNode encodeNumber(WecomSmartSheetField field, Object value) {
    BigDecimal decimal;
    if (value instanceof String text) {
      decimal = new BigDecimal(text.trim());
    } else if (value instanceof BigDecimal number) {
      decimal = number;
    } else if (value instanceof BigInteger number) {
      decimal = new BigDecimal(number);
    } else if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
      decimal = BigDecimal.valueOf(((Number) value).longValue());
    } else if (value instanceof Float || value instanceof Double) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number)) {
        throw invalid(field);
      }
      decimal = BigDecimal.valueOf(number);
    } else {
      throw invalid(field);
    }
    return JsonNodeFactory.instance.numberNode(new BigDecimal(decimal.stripTrailingZeros().toPlainString()));
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
    if (value instanceof String text && text.trim().isEmpty()) {
      return JsonNodeFactory.instance.textNode("");
    }
    if (!field.dateTimeIncludesTime()) {
      LocalDate date = value instanceof LocalDate localDate ? localDate
          : value instanceof String text ? LocalDate.parse(text.trim()) : null;
      if (date == null) {
        throw invalid(field);
      }
      return JsonNodeFactory.instance.textNode(Long.toString(date.atStartOfDay(zoneId).toInstant().toEpochMilli()));
    }
    LocalDateTime dateTime;
    if (value instanceof LocalDate localDate) {
      dateTime = localDate.atStartOfDay();
    } else if (value instanceof LocalDateTime localDateTime) {
      dateTime = localDateTime;
    } else if (value instanceof String text) {
      dateTime = LocalDateTime.parse(text.trim());
    } else {
      throw invalid(field);
    }
    if (dateTime.getNano() % 1_000_000 != 0) {
      throw invalid(field);
    }
    return JsonNodeFactory.instance.textNode(Long.toString(dateTime.atZone(zoneId).toInstant().toEpochMilli()));
  }

  private static ArrayNode encodeSingleSelect(WecomSmartSheetField field, Object value) {
    String option = stringValue(field, value).trim();
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    if (option.isEmpty()) {
      return result;
    }
    result.add(option(field, option));
    return result;
  }

  private static ArrayNode encodeMultiSelect(WecomSmartSheetField field, Object value) {
    Set<String> names = new LinkedHashSet<>();
    if (value instanceof Collection<?> collection) {
      for (Object item : collection) {
        if (!(item instanceof String text)) {
          throw invalid(field);
        }
        names.add(text.trim());
      }
    } else if (value instanceof String string) {
      String text = string.trim();
      if (!text.isEmpty()) {
        for (String item : text.split("、", -1)) {
          names.add(item.trim());
        }
      }
    } else {
      throw invalid(field);
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

  private static String stringValue(WecomSmartSheetField field, Object value) {
    if (!(value instanceof String text)) {
      throw invalid(field);
    }
    return text;
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
