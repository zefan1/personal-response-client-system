package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Creates an API-owned Smart Sheet and prepares its default sheet for controlled acceptance. */
@Component
public final class WecomSmartSheetProvisioningService {

  static final String UNIQUE_FIELD_TITLE = "联系方式";
  private static final List<String> UNIQUE_FIELD_ALIASES = List.of(
      "手机号码", "手机号", "联系方式", "联系电话", "电话");
  private static final String FORMULA_FIELD_TYPE = "FIELD_TYPE_FORMULA";
  private static final List<Map<String, Object>> BUSINESS_FIELDS = List.of(
      field("姓名", "FIELD_TYPE_TEXT"),
      field("客资类型", "FIELD_TYPE_TEXT"),
      field("客户阶段", "FIELD_TYPE_TEXT"),
      field("购买项目", "FIELD_TYPE_TEXT"),
      field("备注", "FIELD_TYPE_TEXT"),
      field("下次跟进方向", "FIELD_TYPE_TEXT"),
      field("下次跟进时间", "FIELD_TYPE_TEXT"));

  private final WecomSmartSheetApiClient apiClient;
  private final Duration timeout;
  private final ObjectMapper objectMapper;

  @Autowired
  public WecomSmartSheetProvisioningService(WecomSmartSheetApiClient apiClient) {
    this(apiClient, Duration.ofSeconds(60), new ObjectMapper());
  }

  public WecomSmartSheetProvisioningService(WecomSmartSheetApiClient apiClient, Duration timeout) {
    this(apiClient, timeout, new ObjectMapper());
  }

  WecomSmartSheetProvisioningService(
      WecomSmartSheetApiClient apiClient, Duration timeout, ObjectMapper objectMapper) {
    this.apiClient = Objects.requireNonNull(apiClient, "apiClient is required");
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    this.timeout = timeout;
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
  }

  public ProvisionedSheet provision(String documentName) {
    return prepare(createDocument(documentName));
  }

  /** Creates a new document from the configured assignment table's readable field metadata. */
  public ProvisionedSheet provisionFromTemplate(
      String documentName, com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget template) {
    return provisionFromTemplate(documentName, template, created -> { });
  }

  /**
   * Creates a document from a verified template schema and reports the document as soon as it exists.
   * This lets the caller retain a recovery link when a later field operation fails.
   */
  public ProvisionedSheet provisionFromTemplate(
      String documentName,
      com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget template,
      Consumer<CreatedDocument> documentCreated) {
    Objects.requireNonNull(template, "template is required");
    Objects.requireNonNull(documentCreated, "documentCreated is required");
    if (!template.configured()) {
      throw new IllegalArgumentException("当前分配表尚未配置完整，无法复制其结构");
    }
    JsonNode sourceFields = fields(template.documentId(), template.sheetId(), template.viewId());
    if (sourceFields == null || !sourceFields.isArray() || sourceFields.isEmpty()) {
      throw new IllegalStateException("当前分配表没有可复制的字段");
    }
    List<TemplateField> desired = copyableFields(sourceFields);
    if (desired.isEmpty()) {
      throw new IllegalStateException("当前分配表没有可复制的字段");
    }
    String resolvedUnique = resolveUniqueFieldTitle(sourceFields, template.uniqueFieldTitle());
    CreatedDocument created = createDocument(documentName);
    documentCreated.accept(created);
    try {
      return prepareFromFields(created, desired, resolvedUnique);
    } catch (RuntimeException failure) {
      throw new IllegalStateException("新分配表创建后结构校验失败：" + safeMessage(failure), failure);
    }
  }

  /** Resumes a failed provisioning attempt whose WeCom document was already recorded. */
  public ProvisionedSheet provisionExistingFromTemplate(
      CreatedDocument created,
      com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget template) {
    Objects.requireNonNull(created, "created document is required");
    Objects.requireNonNull(template, "template is required");
    JsonNode sourceFields = fields(template.documentId(), template.sheetId(), template.viewId());
    List<TemplateField> desired = copyableFields(sourceFields);
    if (desired.isEmpty()) {
      throw new IllegalStateException("当前分配表没有可复制的字段");
    }
    String resolvedUnique = resolveUniqueFieldTitle(sourceFields, template.uniqueFieldTitle());
    try {
      return prepareFromFields(created, desired, resolvedUnique);
    } catch (RuntimeException failure) {
      throw new IllegalStateException("新分配表恢复校验失败：" + safeMessage(failure), failure);
    }
  }

  private ProvisionedSheet prepareFromFields(
      CreatedDocument createdDocument, List<TemplateField> desired, String uniqueFieldTitle) {
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

    JsonNode targetFields = fields(documentId, sheetId, viewId);
    if (targetFields == null || !targetFields.isArray() || targetFields.isEmpty()) {
      throw new IllegalStateException("新分配表没有返回默认字段");
    }
    Map<String, String> currentTypes = fieldTypes(targetFields);
    TemplateField firstDesired = desired.get(0);
    String firstCurrentType = currentTypes.get(firstDesired.title());
    if (firstCurrentType == null) {
      JsonNode firstTarget = targetFields.get(0);
      Map<String, Object> update = target(documentId, sheetId);
      update.put("fields", List.of(Map.of(
          "field_id", requiredText(firstTarget.get("field_id"), "default field identifier"),
          "field_title", firstDesired.title(),
          "field_type", firstDesired.type())));
      call("update_fields", update);
      currentTypes.put(firstDesired.title(), firstDesired.type());
    } else if (!firstDesired.type().equals(firstCurrentType)) {
      throw new IllegalStateException("字段类型不匹配：" + firstDesired.title()
          + "（期望 " + firstDesired.type() + "，实际 " + firstCurrentType + "）");
    }
    for (int index = 1; index < desired.size(); index++) {
      TemplateField field = desired.get(index);
      String currentType = currentTypes.get(field.title());
      if (currentType != null) {
        if (!field.type().equals(currentType)) {
          throw new IllegalStateException("字段类型不匹配：" + field.title()
              + "（期望 " + field.type() + "，实际 " + currentType + "）");
        }
        continue;
      }
      Map<String, Object> add = target(documentId, sheetId);
      add.put("fields", List.of(field.request()));
      call("add_fields", add);
      currentTypes.put(field.title(), field.type());
    }

    JsonNode verifiedFields = fields(documentId, sheetId, viewId);
    verifyFields(desired, verifiedFields);
    String resolvedUnique = uniqueFieldTitle == null || uniqueFieldTitle.isBlank()
        ? desired.get(0).title() : uniqueFieldTitle.trim();
    if (findFieldByTitle(verifiedFields, resolvedUnique) == null) {
      throw new IllegalStateException("新分配表缺少手机号列：" + resolvedUnique);
    }
    return new ProvisionedSheet(documentId, documentUrl, sheetId, viewId, sheetId, resolvedUnique);
  }

  private JsonNode fields(String documentId, String sheetId, String viewId) {
    return call("get_fields", Map.of(
        "docid", documentId, "sheet_id", sheetId, "view_id", viewId,
        "offset", 0, "limit", 1000)).get("fields");
  }

  private List<TemplateField> copyableFields(JsonNode sourceFields) {
    if (sourceFields == null || !sourceFields.isArray()) {
      throw new IllegalStateException("当前分配表没有可复制的字段");
    }
    List<TemplateField> result = new ArrayList<>();
    for (JsonNode source : sourceFields) {
      if (source == null || !source.isObject()) continue;
      String title = source.path("field_title").asText("").trim();
      String type = source.path("field_type").asText("").trim();
      if (title.isBlank() || type.isBlank()) {
        throw new IllegalStateException("当前分配表存在缺少名称或类型的字段");
      }
      // WeCom member fields cannot reliably be created from metadata returned by get_fields.
      // The application only needs the assigned keeper's display name, so retain the business
      // column as text instead of silently dropping it or preventing a new monthly table.
      if ("管家".equals(title)) {
        result.add(new TemplateField(title, "FIELD_TYPE_TEXT", Map.of(
            "field_title", title, "field_type", "FIELD_TYPE_TEXT")));
        continue;
      }
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("field_title", title);
      request.put("field_type", type);
      source.fields().forEachRemaining(entry -> {
        if (entry.getKey().startsWith("property_")) {
          request.put(entry.getKey(), objectMapper.convertValue(entry.getValue(), Object.class));
        }
      });
      // The WeCom schema may explicitly contain a null property value. Keep the exact
      // metadata for the request without Map.copyOf rejecting that valid JSON shape.
      result.add(new TemplateField(title, type,
          java.util.Collections.unmodifiableMap(new LinkedHashMap<>(request))));
    }
    return result;
  }

  private void verifyFields(List<TemplateField> desired, JsonNode actual) {
    Map<String, String> actualTypes = fieldTypes(actual);
    for (TemplateField expected : desired) {
      String actualType = actualTypes.get(expected.title());
      if (!expected.type().equals(actualType)) {
        throw new IllegalStateException("字段未完整复制：" + expected.title()
            + "（期望 " + expected.type() + "，实际 "
            + (actualType == null ? "未返回" : actualType) + "）");
      }
    }
  }

  private String resolveUniqueFieldTitle(JsonNode sourceFields, String configuredTitle) {
    String requested = configuredTitle == null ? "" : configuredTitle.trim();
    JsonNode exact = findFieldByTitle(sourceFields, requested);
    if (isUniqueCandidate(exact)) {
      return requested;
    }
    if (UNIQUE_FIELD_ALIASES.contains(requested)) {
      for (String alias : UNIQUE_FIELD_ALIASES) {
        JsonNode candidate = findFieldByTitle(sourceFields, alias);
        if (isUniqueCandidate(candidate)) {
          return alias;
        }
      }
    }
    throw new IllegalStateException("当前分配表缺少可用的唯一字段："
        + (requested.isBlank() ? "未配置" : requested)
        + "。请确认表中存在“手机号码”“手机号”或“联系方式”文本列");
  }

  private boolean isUniqueCandidate(JsonNode field) {
    if (field == null || !field.isObject()) {
      return false;
    }
    return switch (field.path("field_type").asText("")) {
      case "FIELD_TYPE_TEXT", "FIELD_TYPE_PHONE_NUMBER", "FIELD_TYPE_EMAIL" -> true;
      default -> false;
    };
  }

  private JsonNode findFieldByTitle(JsonNode fields, String title) {
    if (fields == null || !fields.isArray()) return null;
    for (JsonNode field : fields) {
      if (title.equals(field.path("field_title").asText(""))) return field;
    }
    return null;
  }

  private String safeMessage(RuntimeException failure) {
    String message = failure.getMessage();
    return message == null || message.isBlank() ? "远程表格接口未返回可用结果" : message;
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

  private record TemplateField(String title, String type, Map<String, Object> request) {}
}
