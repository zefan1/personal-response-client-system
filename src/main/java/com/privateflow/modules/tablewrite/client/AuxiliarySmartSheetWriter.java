package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes only the fixed, non-authoritative auxiliary projections. */
@Component
public class AuxiliarySmartSheetWriter {

  private final WecomSmartSheetApiClient apiClient;
  private final WecomSmartSheetValueCodec valueCodec;

  public AuxiliarySmartSheetWriter(
      WecomSmartSheetApiClient apiClient, WecomSmartSheetValueCodec valueCodec) {
    this.apiClient = apiClient;
    this.valueCodec = valueCodec;
  }

  public String upsert(AuxiliarySmartSheetTarget target, Map<String, Object> values, Duration timeout) {
    return upsert(target, values, target == null ? "" : target.uniqueFieldTitle(), timeout);
  }

  public String upsert(
      AuxiliarySmartSheetTarget target,
      Map<String, Object> values,
      String identityFieldTitle,
      Duration timeout) {
    if (target == null || !target.configured()) {
      throw new IllegalArgumentException("辅助表尚未配置");
    }
    String identityField = identityFieldTitle == null ? "" : identityFieldTitle.trim();
    String phone = values == null || values.get(identityField) == null
        ? "" : String.valueOf(values.get(identityField)).trim();
    if (phone.isBlank()) {
      throw new IllegalArgumentException("手机号不能为空");
    }
    Map<String, WecomSmartSheetField> fields = fields(target, timeout);
    WecomSmartSheetField identity = fields.get(identityField);
    if (identity == null || !identity.writable()) {
      throw new IllegalArgumentException("辅助表缺少可写的手机号映射列");
    }
    Map<String, Object> normalized = new LinkedHashMap<>(values);
    normalized.put(identityField, phone);
    Map<String, JsonNode> encoded = encode(fields, normalized);
    if (!encoded.containsKey(identity.fieldId())) {
      throw new IllegalArgumentException("辅助表手机号映射列无法写入");
    }
    String existingId = findRecord(target, identity, phone, timeout);
    if (existingId == null) {
      return add(target, encoded, timeout);
    }
    update(target, existingId, encoded, timeout);
    return existingId;
  }

  private Map<String, WecomSmartSheetField> fields(AuxiliarySmartSheetTarget target, Duration timeout) {
    JsonNode response = apiClient.postForTarget("get_fields", request(target, false), timeout, false);
    Map<String, WecomSmartSheetField> result = new LinkedHashMap<>();
    JsonNode items = response == null ? null : response.get("fields");
    if (items == null || !items.isArray()) {
      throw new IllegalStateException("辅助表字段读取失败");
    }
    for (JsonNode item : items) {
      String id = text(item.get("field_id"));
      String title = WecomSmartSheetFieldMetadata.title(item);
      String type = text(item.get("field_type"));
      if (id != null && title != null && type != null) {
        result.put(title, new WecomSmartSheetField(
            id, title, type, options(item, type), includesTime(item, type)));
      }
    }
    return result;
  }

  private String findRecord(
      AuxiliarySmartSheetTarget target,
      WecomSmartSheetField contactField,
      String contact,
      Duration timeout) {
    Map<String, Object> body = request(target, true);
    body.put("key_type", "CELL_VALUE_KEY_TYPE_FIELD_ID");
    body.put("field_ids", List.of(contactField.fieldId()));
    body.put("offset", 0);
    body.put("limit", 1000);
    JsonNode response = apiClient.postForTarget("get_records", body, timeout, false);
    JsonNode records = response == null ? null : response.get("records");
    if (records == null || !records.isArray()) {
      throw new IllegalStateException("辅助表记录读取失败");
    }
    for (JsonNode record : records) {
      String recordId = text(record.get("record_id"));
      JsonNode rawContact = record.path("values").get(contactField.fieldId());
      if (recordId != null && rawContact != null
          && contact.equals(valueCodec.decode(contactField, rawContact))) {
        return recordId;
      }
    }
    return null;
  }

  private Map<String, JsonNode> encode(
      Map<String, WecomSmartSheetField> fields, Map<String, Object> values) {
    Map<String, JsonNode> result = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      WecomSmartSheetField field = fields.get(entry.getKey());
      if (field != null && field.writable()) {
        try {
          result.put(field.fieldId(), valueCodec.encode(field, String.valueOf(entry.getValue())));
        } catch (IllegalArgumentException ignored) {
          // A fixed projection omits an unsupported select value instead of blocking MariaDB.
        }
      }
    }
    return result;
  }

  private String add(AuxiliarySmartSheetTarget target, Map<String, JsonNode> values, Duration timeout) {
    Map<String, Object> body = request(target, false);
    body.put("key_type", "CELL_VALUE_KEY_TYPE_FIELD_ID");
    body.put("records", List.of(Map.of("values", values)));
    JsonNode records = apiClient.postForTarget("add_records", body, timeout, false).get("records");
    String id = records != null && records.isArray() && records.size() == 1
        ? text(records.get(0).get("record_id")) : null;
    if (id == null) {
      throw new IllegalStateException("辅助表新增记录失败");
    }
    return id;
  }

  private void update(
      AuxiliarySmartSheetTarget target, String recordId, Map<String, JsonNode> values, Duration timeout) {
    Map<String, Object> body = request(target, false);
    body.put("key_type", "CELL_VALUE_KEY_TYPE_FIELD_ID");
    body.put("records", List.of(Map.of("record_id", recordId, "values", values)));
    JsonNode records = apiClient.postForTarget("update_records", body, timeout, false).get("records");
    if (records == null || !records.isArray() || records.size() != 1
        || !recordId.equals(text(records.get(0).get("record_id")))) {
      throw new IllegalStateException("辅助表更新记录失败");
    }
  }

  private static Map<String, Object> request(AuxiliarySmartSheetTarget target, boolean includeView) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("docid", target.documentId());
    body.put("sheet_id", target.sheetId());
    if (includeView) {
      body.put("view_id", target.viewId());
    }
    return body;
  }

  private static String text(JsonNode node) {
    if (node == null || !node.isTextual() || node.textValue().trim().isEmpty()) {
      return null;
    }
    return node.textValue().trim();
  }

  private static Map<String, String> options(JsonNode field, String type) {
    String propertyName = switch (type) {
      case "FIELD_TYPE_SELECT" -> "property_select";
      case "FIELD_TYPE_SINGLE_SELECT" -> "property_single_select";
      default -> null;
    };
    if (propertyName == null) return Map.of();
    JsonNode items = field.path(propertyName).path("options");
    if (!items.isArray()) return Map.of();
    Map<String, String> result = new LinkedHashMap<>();
    for (JsonNode item : items) {
      String id = text(item.get("id"));
      String label = text(item.get("text"));
      if (id != null && label != null) result.put(label, id);
    }
    return result;
  }

  private static boolean includesTime(JsonNode field, String type) {
    return "FIELD_TYPE_DATE_TIME".equals(type)
        && field.path("property_date_time").path("format").asText("")
            .toLowerCase(java.util.Locale.ROOT).contains("h");
  }
}
