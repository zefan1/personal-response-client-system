package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** Executes the one-time provider acceptance sequence without starting the business application. */
public final class WecomSmartSheetLiveAcceptanceService {

  private static final String GET_RECORDS = "get_records";
  private static final String FIELD_TITLE_KEY_TYPE = "CELL_VALUE_KEY_TYPE_FIELD_TITLE";
  private static final String FIELD_ID_KEY_TYPE = "CELL_VALUE_KEY_TYPE_FIELD_ID";

  private final WecomSmartSheetConfig config;
  private final WecomSmartSheetApiClient apiClient;
  private final WecomSmartSheetFieldCatalog fieldCatalog;
  private final WecomSmartSheetRecordClient recordClient;
  private final Duration timeout;
  private final Supplier<String> tokenSuffixSupplier;

  public WecomSmartSheetLiveAcceptanceService(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetRecordClient recordClient,
      Duration timeout,
      Supplier<String> tokenSuffixSupplier) {
    this.config = Objects.requireNonNull(config, "config is required");
    this.apiClient = Objects.requireNonNull(apiClient, "apiClient is required");
    this.fieldCatalog = Objects.requireNonNull(fieldCatalog, "fieldCatalog is required");
    this.recordClient = Objects.requireNonNull(recordClient, "recordClient is required");
    this.timeout = requirePositive(timeout);
    this.tokenSuffixSupplier = Objects.requireNonNull(tokenSuffixSupplier, "tokenSuffixSupplier is required");
  }

  public Report run() {
    config.requireConfigured();
    Map<String, WecomSmartSheetField> fields = fieldCatalog.visibleFields(timeout);
    WecomSmartSheetField formulaField = requireFormulaField(fields);

    requireRecordsArray(apiClient.post(GET_RECORDS, initialQuery(), timeout), "query");

    String uniqueValue = testUniqueValue();
    String createdRecordId = recordClient.createRow(
        config.sourceTable(), Map.of(config.uniqueFieldTitle(), uniqueValue), timeout);
    String updatedValue = updatedUniqueValue();
    recordClient.updateRow(
        config.sourceTable(), createdRecordId, Map.of(config.uniqueFieldTitle(), updatedValue), timeout);

    String duplicateRecordId = recordClient.createRow(
        config.sourceTable(), Map.of(config.uniqueFieldTitle(), updatedValue), timeout);
    if (!createdRecordId.equals(duplicateRecordId)) {
      throw new IllegalStateException("Duplicate create returned a different record identifier");
    }

    JsonNode reread = apiClient.post(GET_RECORDS, lookupQuery(fields.get(config.uniqueFieldTitle())), timeout);
    if (!containsRecordValue(reread, createdRecordId, fields.get(config.uniqueFieldTitle()), updatedValue)) {
      throw new IllegalStateException("Updated test value could not be reread");
    }

    confirmFormulaProtection(formulaField, createdRecordId, uniqueValue);
    return new Report(createdRecordId, true, true, true, true, true);
  }

  private Map<String, Object> initialQuery() {
    return Map.of(
        "docid", config.documentId(),
        "sheet_id", config.sheetId(),
        "view_id", config.viewId(),
        "key_type", FIELD_TITLE_KEY_TYPE,
        "offset", 0,
        "limit", 1);
  }

  private Map<String, Object> lookupQuery(WecomSmartSheetField uniqueField) {
    if (uniqueField == null) {
      throw new IllegalStateException("Configured unique field is not visible");
    }
    return Map.of(
        "docid", config.documentId(),
        "sheet_id", config.sheetId(),
        "view_id", config.viewId(),
        "key_type", FIELD_ID_KEY_TYPE,
        "field_ids", List.of(uniqueField.fieldId()),
        "offset", 0,
        "limit", 1000);
  }

  private String testUniqueValue() {
    return testPhoneValue("198");
  }

  private String updatedUniqueValue() {
    return testPhoneValue("199");
  }

  private String testPhoneValue(String prefix) {
    String suffix = tokenSuffixSupplier.get();
    if (suffix == null || suffix.trim().isEmpty()) {
      throw new IllegalStateException("Test record suffix must not be blank");
    }
    long digits = Integer.toUnsignedLong(suffix.trim().hashCode()) % 100_000_000L;
    return prefix + String.format(Locale.ROOT, "%08d", digits);
  }

  private static WecomSmartSheetField requireFormulaField(Map<String, WecomSmartSheetField> fields) {
    return fields.values().stream()
        .filter(field -> "FIELD_TYPE_FORMULA".equals(field.type()))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("Configured view has no formula field to protect"));
  }

  private void confirmFormulaProtection(
      WecomSmartSheetField formulaField, String recordId, String uniqueValue) {
    try {
      recordClient.updateRow(
          config.sourceTable(), recordId, Map.of(formulaField.title(), uniqueValue), timeout);
    } catch (IllegalArgumentException expected) {
      if (expected.getMessage() != null && expected.getMessage().startsWith("Field is visible but read-only:")) {
        return;
      }
      throw expected;
    }
    throw new IllegalStateException("Formula field was accepted for writing");
  }

  private static void requireRecordsArray(JsonNode response, String operation) {
    if (response == null || !response.path("records").isArray()) {
      throw new IllegalStateException("Smart Sheet " + operation + " response did not contain records");
    }
  }

  private boolean containsRecordValue(
      JsonNode response,
      String expectedRecordId,
      WecomSmartSheetField field,
      String expectedValue) {
    requireRecordsArray(response, "reread");
    for (JsonNode record : response.path("records")) {
      if (expectedRecordId.equals(record.path("record_id").asText())) {
        JsonNode encoded = record.path("values").get(field.fieldId());
        return encoded != null && expectedValue.equals(valueCodec(field, encoded));
      }
    }
    return false;
  }

  private String valueCodec(WecomSmartSheetField field, JsonNode encoded) {
    return new WecomSmartSheetValueCodec(config).decode(field, encoded);
  }

  private static Duration requirePositive(Duration value) {
    if (value == null || value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("timeout must be positive");
    }
    return value;
  }

  public record Report(
      String createdRecordId,
      boolean querySucceeded,
      boolean updateSucceeded,
      boolean duplicatePrevented,
      boolean rereadSucceeded,
      boolean formulaProtectionConfirmed) {}
}
