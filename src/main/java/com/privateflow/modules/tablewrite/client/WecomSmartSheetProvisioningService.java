package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Creates an API-owned Smart Sheet and prepares its default sheet for controlled acceptance. */
public final class WecomSmartSheetProvisioningService {

  static final String UNIQUE_FIELD_TITLE = "联系方式";
  private static final String FORMULA_FIELD_TYPE = "FIELD_TYPE_FORMULA";
  private static final List<Map<String, Object>> BUSINESS_FIELDS = List.of(
      field("姓名", "FIELD_TYPE_TEXT"),
      field("客资类型", "FIELD_TYPE_TEXT"),
      field("客户阶段", "FIELD_TYPE_TEXT"),
      field("备注", "FIELD_TYPE_TEXT"),
      field("下次跟进方向", "FIELD_TYPE_TEXT"),
      field("下次跟进时间", "FIELD_TYPE_TEXT"));

  private final WecomSmartSheetApiClient apiClient;
  private final Duration timeout;

  public WecomSmartSheetProvisioningService(WecomSmartSheetApiClient apiClient, Duration timeout) {
    this.apiClient = Objects.requireNonNull(apiClient, "apiClient is required");
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.timeout = timeout;
  }

  public ProvisionedSheet provision(String documentName) {
    return prepare(createDocument(documentName));
  }

  public CreatedDocument createDocument(String documentName) {
    String normalizedName = documentName == null ? "" : documentName.trim();
    if (normalizedName.isEmpty()) {
      throw new IllegalArgumentException("document name is required");
    }

    JsonNode created = call("create_doc", Map.of("doc_type", 10, "doc_name", normalizedName));
    String documentId = requiredText(created.get("docid"), "created document identifier");
    String documentUrl = requiredText(created.get("url"), "created document URL");
    return new CreatedDocument(documentId, documentUrl);
  }

  public ProvisionedSheet prepare(CreatedDocument createdDocument) {
    Objects.requireNonNull(createdDocument, "createdDocument is required");
    String documentId = required(createdDocument.documentId(), "created document identifier");
    String documentUrl = required(createdDocument.documentUrl(), "created document URL");

    JsonNode sheet = firstMatching(call("get_sheet", Map.of("docid", documentId)).get("sheet_list"),
        "type", "smartsheet", "Smart Sheet child sheet");
    String sheetId = requiredText(sheet.get("sheet_id"), "child sheet identifier");

    Map<String, Object> viewRequest = new LinkedHashMap<>();
    viewRequest.put("docid", documentId);
    viewRequest.put("sheet_id", sheetId);
    viewRequest.put("offset", 0);
    viewRequest.put("limit", 1000);
    JsonNode view = firstMatching(call("get_views", viewRequest).get("views"),
        "view_type", "VIEW_TYPE_GRID", "grid view");
    String viewId = requiredText(view.get("view_id"), "view identifier");

    Map<String, Object> fieldRequest = target(documentId, sheetId);
    fieldRequest.put("view_id", viewId);
    fieldRequest.put("offset", 0);
    fieldRequest.put("limit", 1000);
    JsonNode fields = call("get_fields", fieldRequest).get("fields");
    JsonNode firstField = firstObject(fields, "default field");
    String fieldId = requiredText(firstField.get("field_id"), "default field identifier");
    String fieldType = requiredText(firstField.get("field_type"), "default field type");
    String fieldTitle = requiredText(firstField.get("field_title"), "default field title");
    if (!UNIQUE_FIELD_TITLE.equals(fieldTitle)) {
      Map<String, Object> updateRequest = target(documentId, sheetId);
      updateRequest.put("fields", List.of(Map.of(
          "field_id", fieldId,
          "field_title", UNIQUE_FIELD_TITLE,
          "field_type", fieldType)));
      call("update_fields", updateRequest);
    }

    Map<String, String> existingTypes = fieldTypes(fields);
    existingTypes.remove(fieldTitle);
    existingTypes.put(UNIQUE_FIELD_TITLE, fieldType);
    List<Map<String, Object>> missingFields = new ArrayList<>();
    for (Map<String, Object> desired : BUSINESS_FIELDS) {
      String title = desired.get("field_title").toString();
      String type = desired.get("field_type").toString();
      String existingType = existingTypes.get(title);
      if (existingType == null) {
        missingFields.add(desired);
      } else if (!type.equals(existingType)) {
        throw new IllegalStateException("Created document field type did not match: " + title);
      }
    }
    if (!missingFields.isEmpty()) {
      Map<String, Object> addRequest = target(documentId, sheetId);
      addRequest.put("fields", missingFields);
      call("add_fields", addRequest);
    }
    if (existingTypes.values().stream().noneMatch(FORMULA_FIELD_TYPE::equals)) {
      throw new IllegalStateException(
          "Created document has no formula field; WeCom does not support creating formula fields through add_fields");
    }

    return new ProvisionedSheet(
        documentId, documentUrl, sheetId, viewId, sheetId, UNIQUE_FIELD_TITLE);
  }

  private JsonNode call(String operation, Object body) {
    return apiClient.postWithApplicationCredentials(operation, body, timeout);
  }

  private static Map<String, Object> target(String documentId, String sheetId) {
    Map<String, Object> target = new LinkedHashMap<>();
    target.put("docid", documentId);
    target.put("sheet_id", sheetId);
    return target;
  }

  private static Map<String, Object> field(String title, String type) {
    return Map.of("field_title", title, "field_type", type);
  }

  private static Map<String, String> fieldTypes(JsonNode fields) {
    Map<String, String> result = new HashMap<>();
    if (fields != null && fields.isArray()) {
      for (JsonNode field : fields) {
        if (field != null && field.isObject()) {
          String title = field.path("field_title").asText("").trim();
          String type = field.path("field_type").asText("").trim();
          if (!title.isEmpty() && !type.isEmpty()) {
            result.put(title, type);
          }
        }
      }
    }
    return result;
  }

  private static JsonNode firstMatching(
      JsonNode array, String property, String expected, String description) {
    if (array != null && array.isArray()) {
      for (JsonNode element : array) {
        if (element != null && element.isObject() && expected.equals(element.path(property).asText())) {
          return element;
        }
      }
    }
    throw new IllegalStateException("Created document did not contain a valid " + description);
  }

  private static JsonNode firstObject(JsonNode array, String description) {
    if (array != null && array.isArray() && !array.isEmpty() && array.get(0).isObject()) {
      return array.get(0);
    }
    throw new IllegalStateException("Created document did not contain a valid " + description);
  }

  private static String requiredText(JsonNode value, String description) {
    if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
      throw new IllegalStateException("WeCom response was missing " + description);
    }
    return value.textValue().trim();
  }

  private static String required(String value, String description) {
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException(description + " is required");
    }
    return value.trim();
  }

  public record CreatedDocument(String documentId, String documentUrl) {}

  public record ProvisionedSheet(
      String documentId,
      String documentUrl,
      String sheetId,
      String viewId,
      String sourceTable,
      String uniqueFieldTitle) {}
}
