package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetRecordClient {

  private static final String GET_OPERATION = "get_records";
  private static final String ADD_OPERATION = "add_records";
  private static final String UPDATE_OPERATION = "update_records";
  private static final int PAGE_SIZE = 1000;
  private static final int MAX_PAGES = 100;
  private static final String FIELD_TITLE_KEY_TYPE = "CELL_VALUE_KEY_TYPE_FIELD_TITLE";
  private static final String FIELD_ID_KEY_TYPE = "CELL_VALUE_KEY_TYPE_FIELD_ID";

  private final WecomSmartSheetConfig config;
  private final WecomSmartSheetApiClient apiClient;
  private final WecomSmartSheetFieldCatalog fieldCatalog;
  private final WecomSmartSheetValueCodec valueCodec;
  private final ConcurrentHashMap<String, CreateLock> createLocks = new ConcurrentHashMap<>();

  public WecomSmartSheetRecordClient(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetValueCodec valueCodec) {
    this.config = Objects.requireNonNull(config, "config is required");
    this.apiClient = Objects.requireNonNull(apiClient, "apiClient is required");
    this.fieldCatalog = Objects.requireNonNull(fieldCatalog, "fieldCatalog is required");
    this.valueCodec = Objects.requireNonNull(valueCodec, "valueCodec is required");
  }

  public List<SheetRow> fetchIncrementalRows(
      SheetSource source, LocalDateTime modifiedAfter, int limit, Duration timeout) {
    validate(source, modifiedAfter, limit, timeout);
    config.requireTarget(source.sheetId(), source.sourceTable());
    Map<String, WecomSmartSheetField> fields = fieldCatalog.visibleFields(timeout);
    List<TimestampedRow> included = new ArrayList<>();
    Set<String> recordIds = new HashSet<>();
    Long expectedTotal = null;
    long loadedCount = 0;
    long offset = 0;

    for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
      JsonNode response = apiClient.post(GET_OPERATION, request(offset), timeout);
      Page page = page(response);
      expectedTotal = expectedTotal(expectedTotal, page.total());
      loadedCount = add(loadedCount, page.records().size());
      if (expectedTotal != null && loadedCount > expectedTotal) {
        throw invalid("total metadata was inconsistent");
      }
      for (int recordIndex = 0; recordIndex < page.records().size(); recordIndex++) {
        TimestampedRow row = row(page.records().get(recordIndex), fields, recordIds, recordIndex);
        if (row.updatedAt().isAfter(modifiedAfter)) {
          included.add(row);
        }
      }
      if (!page.hasMore()) {
        if (expectedTotal != null && loadedCount != expectedTotal) {
          throw invalid("total metadata was inconsistent");
        }
        return limitedSorted(included, limit);
      }
      if (pageNumber == MAX_PAGES - 1) {
        throw invalid("pagination exceeded maximum pages");
      }
      offset = nextOffset(page.next(), offset);
    }
    throw invalid("pagination exceeded maximum pages");
  }

  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    validateWrite(sourceTable, fields, timeout);
    String uniqueTitle = config.uniqueFieldTitle();
    Object uniqueValue = fields.get(uniqueTitle);
    if (!(uniqueValue instanceof String exactValue) || exactValue.isBlank()) {
      throw new IllegalArgumentException("A nonblank value is required for unique field: " + uniqueTitle);
    }
    Map<String, JsonNode> encoded = encodeFields(fields, timeout);
    WecomSmartSheetField uniqueField = fieldCatalog.requireWritable(uniqueTitle, timeout);
    String lockKey = normalizedLockKey(exactValue);
    CreateLock lock = acquireCreateLock(lockKey);
    try {
      synchronized (lock) {
        Match match = exactMatch(uniqueField, exactValue, timeout);
        if (match.count() > 1) {
          throw invalid(GET_OPERATION, "multiple exact unique-field matches were found");
        }
        if (match.count() == 1) {
          return match.recordId();
        }
        return add(encoded, timeout);
      }
    } finally {
      releaseCreateLock(lockKey, lock);
    }
  }

  public void updateRow(
      String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    validateWrite(sourceTable, fields, timeout);
    String recordId = sourceRowId == null ? "" : sourceRowId.trim();
    if (recordId.isEmpty()) {
      throw new IllegalArgumentException("Record identifier is required");
    }
    if (fields.values().stream().allMatch(Objects::isNull)) {
      throw new IllegalArgumentException("At least one non-null field is required");
    }
    Map<String, JsonNode> encoded = encodeFields(fields, timeout);
    if (encoded.isEmpty()) {
      throw new IllegalArgumentException("At least one non-null field is required");
    }
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("record_id", recordId);
    record.put("values", encoded);
    JsonNode response = apiClient.post(UPDATE_OPERATION, writeRequest(record), timeout);
    confirmUpdated(response, recordId);
  }

  private Map<String, Object> request(long offset) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("docid", config.documentId());
    request.put("sheet_id", config.sheetId());
    request.put("view_id", config.viewId());
    request.put("key_type", FIELD_TITLE_KEY_TYPE);
    request.put("offset", offset);
    request.put("limit", PAGE_SIZE);
    return request;
  }

  private Match exactMatch(WecomSmartSheetField uniqueField, String exactValue, Duration timeout) {
    Set<String> recordIds = new HashSet<>();
    Long expectedTotal = null;
    long loadedCount = 0;
    long offset = 0;
    int matchCount = 0;
    String matchedRecordId = null;

    for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
      JsonNode response = apiClient.post(GET_OPERATION, lookupRequest(uniqueField.fieldId(), offset), timeout);
      Page page = page(response);
      expectedTotal = expectedTotal(expectedTotal, page.total());
      loadedCount = add(loadedCount, page.records().size());
      if (expectedTotal != null && loadedCount > expectedTotal) {
        throw invalid("total metadata was inconsistent");
      }
      for (JsonNode record : page.records()) {
        LookupRecord candidate = lookupRecord(record, uniqueField, recordIds);
        if (exactValue.equals(candidate.uniqueValue())) {
          if (matchCount == 0) {
            matchedRecordId = candidate.recordId();
          }
          matchCount = Math.min(2, matchCount + 1);
        }
      }
      if (!page.hasMore()) {
        if (expectedTotal != null && loadedCount != expectedTotal) {
          throw invalid("total metadata was inconsistent");
        }
        return new Match(matchCount, matchedRecordId);
      }
      if (pageNumber == MAX_PAGES - 1) {
        throw invalid("pagination exceeded maximum pages");
      }
      offset = nextOffset(page.next(), offset);
    }
    throw invalid("pagination exceeded maximum pages");
  }

  private Map<String, Object> lookupRequest(String uniqueFieldId, long offset) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("docid", config.documentId());
    request.put("sheet_id", config.sheetId());
    request.put("view_id", config.viewId());
    request.put("key_type", FIELD_ID_KEY_TYPE);
    request.put("field_ids", List.of(uniqueFieldId));
    request.put("offset", offset);
    request.put("limit", PAGE_SIZE);
    return request;
  }

  private LookupRecord lookupRecord(
      JsonNode record, WecomSmartSheetField uniqueField, Set<String> recordIds) {
    if (record == null || !record.isObject()) {
      throw invalid("record metadata was invalid");
    }
    String recordId = requiredText(record.get("record_id"), "record identifier");
    if (!recordIds.add(recordId)) {
      throw invalid("duplicate record identifier");
    }
    JsonNode values = record.get("values");
    if (values == null || !values.isObject()) {
      throw invalid("record values metadata was invalid");
    }
    JsonNode value = values.get(uniqueField.fieldId());
    if (value == null) {
      return new LookupRecord(recordId, null);
    }
    try {
      return new LookupRecord(recordId, valueCodec.decode(uniqueField, value));
    } catch (RuntimeException ex) {
      throw invalid("unique field value could not be decoded");
    }
  }

  private Map<String, JsonNode> encodeFields(Map<String, Object> fields, Duration timeout) {
    Map<String, JsonNode> encoded = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      WecomSmartSheetField field = fieldCatalog.requireWritable(entry.getKey(), timeout);
      encoded.put(field.fieldId(), valueCodec.encode(field, entry.getValue()));
    }
    return encoded;
  }

  private String add(Map<String, JsonNode> encoded, Duration timeout) {
    JsonNode response = apiClient.post(ADD_OPERATION, writeRequest(Map.of("values", encoded)), timeout);
    if (response == null || !response.isObject()) {
      throw invalid(ADD_OPERATION, "response confirmation was invalid");
    }
    JsonNode records = response.get("records");
    if (records == null || !records.isArray() || records.size() != 1) {
      throw invalid(ADD_OPERATION, "response confirmation was invalid");
    }
    JsonNode record = records.get(0);
    if (record == null || !record.isObject()) {
      throw invalid(ADD_OPERATION, "response confirmation was invalid");
    }
    JsonNode recordId = record.get("record_id");
    if (recordId == null || !recordId.isTextual() || recordId.textValue().trim().isEmpty()) {
      throw invalid(ADD_OPERATION, "response confirmation was invalid");
    }
    return recordId.textValue().trim();
  }

  private Map<String, Object> writeRequest(Map<String, ?> record) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("docid", config.documentId());
    request.put("sheet_id", config.sheetId());
    request.put("key_type", FIELD_ID_KEY_TYPE);
    request.put("records", List.of(record));
    return request;
  }

  private static void confirmUpdated(JsonNode response, String expectedRecordId) {
    if (response == null || !response.isObject()) {
      throw invalid(UPDATE_OPERATION, "response confirmation was invalid");
    }
    JsonNode records = response.get("records");
    if (records == null || !records.isArray()) {
      throw invalid(UPDATE_OPERATION, "response confirmation was invalid");
    }
    for (JsonNode record : records) {
      JsonNode recordId = record == null || !record.isObject() ? null : record.get("record_id");
      if (recordId != null && recordId.isTextual() && expectedRecordId.equals(recordId.textValue())) {
        return;
      }
    }
    throw invalid(UPDATE_OPERATION, "response confirmation was invalid");
  }

  private static String normalizedLockKey(String value) {
    StringBuilder normalized = new StringBuilder();
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      if (!Character.isWhitespace(character) && character != '-' && character != '(' && character != ')') {
        normalized.append(character);
      }
    }
    return normalized.toString();
  }

  private CreateLock acquireCreateLock(String lockKey) {
    return createLocks.compute(lockKey, (ignored, current) -> {
      CreateLock lock = current == null || current.retired ? new CreateLock() : current;
      lock.participants++;
      return lock;
    });
  }

  private void releaseCreateLock(String lockKey, CreateLock expected) {
    createLocks.compute(lockKey, (ignored, current) -> {
      if (current != expected || current.participants <= 0) {
        throw new IllegalStateException("Create lock state was invalid");
      }
      current.participants--;
      if (current.participants == 0) {
        current.retired = true;
      }
      return current;
    });
    if (expected.retired) {
      createLocks.remove(lockKey, expected);
    }
  }

  private TimestampedRow row(
      JsonNode record,
      Map<String, WecomSmartSheetField> fields,
      Set<String> recordIds,
      int recordIndex) {
    if (record == null || !record.isObject()) {
      throw invalid("record metadata was invalid");
    }
    String recordId = requiredText(record.get("record_id"), "record identifier");
    if (!recordIds.add(recordId)) {
      throw invalid("duplicate record identifier");
    }
    long updateMillis = timestamp(record.get("update_time"));
    JsonNode values = record.get("values");
    if (values == null || !values.isObject()) {
      throw invalid("record values metadata was invalid");
    }
    Map<String, String> decoded = new LinkedHashMap<>();
    for (Map.Entry<String, WecomSmartSheetField> entry : fields.entrySet()) {
      JsonNode value = values.get(entry.getKey());
      if (value == null) {
        continue;
      }
      try {
        decoded.put(entry.getKey(), valueCodec.decode(entry.getValue(), value));
      } catch (RuntimeException ex) {
        throw invalid("could not decode visible field " + entry.getKey() + " in record " + (recordIndex + 1));
      }
    }
    return new TimestampedRow(recordId, LocalDateTime.ofInstant(Instant.ofEpochMilli(updateMillis), config.zoneId()),
        new SheetRow(recordId, Map.copyOf(decoded)));
  }

  private static Page page(JsonNode response) {
    if (response == null || !response.isObject()) {
      throw invalid("response metadata was invalid");
    }
    JsonNode records = response.get("records");
    if (records == null || !records.isArray()) {
      throw invalid("records metadata was invalid");
    }
    JsonNode hasMore = response.get("has_more");
    boolean more;
    if (hasMore != null && hasMore.isBoolean()) {
      more = hasMore.booleanValue();
    } else {
      throw invalid("pagination metadata was invalid");
    }
    Long total = optionalNonnegativeLong(response.get("total"), "total metadata was invalid");
    JsonNode next = response.get("next");
    if (more && next == null) {
      throw invalid("pagination metadata was invalid");
    }
    return new Page(records, more, next, total);
  }

  private static Long expectedTotal(Long current, Long received) {
    if (received == null) {
      return current;
    }
    if (current != null && !current.equals(received)) {
      throw invalid("total metadata was inconsistent");
    }
    return received;
  }

  private static long nextOffset(JsonNode next, long currentOffset) {
    Long parsed = optionalNonnegativeLong(next, "pagination metadata was invalid");
    if (parsed == null || parsed <= currentOffset) {
      throw invalid("pagination metadata was invalid");
    }
    return parsed;
  }

  private static long timestamp(JsonNode value) {
    if (value == null) {
      throw invalid("update time metadata was invalid");
    }
    try {
      long parsed;
      if (value.isIntegralNumber() && value.canConvertToLong()) {
        parsed = value.longValue();
      } else if (value.isTextual()) {
        parsed = Long.parseLong(value.textValue());
      } else {
        throw new NumberFormatException();
      }
      if (parsed < 0) {
        throw new NumberFormatException();
      }
      return parsed;
    } catch (RuntimeException ex) {
      throw invalid("update time metadata was invalid");
    }
  }

  private static Long optionalNonnegativeLong(JsonNode value, String reason) {
    if (value == null) {
      return null;
    }
    if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
      throw invalid(reason);
    }
    return value.longValue();
  }

  private static String requiredText(JsonNode value, String reason) {
    if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
      throw invalid(reason + " was invalid");
    }
    return value.textValue().trim();
  }

  private static long add(long value, long addition) {
    try {
      return Math.addExact(value, addition);
    } catch (ArithmeticException ex) {
      throw invalid("records metadata was invalid");
    }
  }

  private static List<SheetRow> limitedSorted(List<TimestampedRow> included, int limit) {
    included.sort(Comparator.comparing(TimestampedRow::updatedAt).thenComparing(TimestampedRow::recordId));
    return included.stream().limit(limit).map(TimestampedRow::row).toList();
  }

  private static void validate(SheetSource source, LocalDateTime modifiedAfter, int limit, Duration timeout) {
    if (source == null || modifiedAfter == null || limit <= 0 || timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Source, modifiedAfter, positive limit, and positive timeout are required");
    }
  }

  private void validateWrite(String sourceTable, Map<String, Object> fields, Duration timeout) {
    if (fields == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Fields and a positive timeout are required");
    }
    config.requireTarget(config.documentId(), sourceTable);
  }

  private static WecomSmartSheetException invalid(String reason) {
    return invalid(GET_OPERATION, reason);
  }

  private static WecomSmartSheetException invalid(String operation, String reason) {
    return new WecomSmartSheetException(operation, reason, null);
  }

  private record Page(JsonNode records, boolean hasMore, JsonNode next, Long total) {}

  private record LookupRecord(String recordId, String uniqueValue) {}

  private record Match(int count, String recordId) {}

  private static final class CreateLock {
    private int participants;
    private volatile boolean retired;
  }

  private record TimestampedRow(String recordId, LocalDateTime updatedAt, SheetRow row) {}
}
