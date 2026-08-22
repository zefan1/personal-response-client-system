package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetRecordClient {

  private static final Logger log = LoggerFactory.getLogger(WecomSmartSheetRecordClient.class);

  private static final String GET_OPERATION = "get_records";
  private static final String ADD_OPERATION = "add_records";
  private static final String UPDATE_OPERATION = "update_records";
  private static final int PAGE_SIZE = 1000;
  private static final int MAX_PAGES = 100;
  private static final Duration RECENT_SUCCESS_TTL = Duration.ofMinutes(5);
  private static final int RECENT_SUCCESS_LIMIT = 1024;
  private static final String FIELD_TITLE_KEY_TYPE = "CELL_VALUE_KEY_TYPE_FIELD_TITLE";
  private static final String FIELD_ID_KEY_TYPE = "CELL_VALUE_KEY_TYPE_FIELD_ID";

  private final WecomSmartSheetConfig config;
  private final WecomSmartSheetApiClient apiClient;
  private final WecomSmartSheetFieldCatalog fieldCatalog;
  private final WecomSmartSheetValueCodec valueCodec;
  private final AuxiliarySmartSheetTargets auxiliaryTargets;
  private final Clock clock;
  private final Duration recentSuccessTtl;
  private final int recentSuccessLimit;
  private final ConcurrentHashMap<String, CreateLock> createLocks = new ConcurrentHashMap<>();
  private final LinkedHashMap<String, RecentSuccess> recentSuccesses = new LinkedHashMap<>(16, 0.75f, true);

  @Autowired
  public WecomSmartSheetRecordClient(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetValueCodec valueCodec,
      AuxiliarySmartSheetTargets auxiliaryTargets) {
    this(config, apiClient, fieldCatalog, valueCodec, auxiliaryTargets,
        Clock.systemUTC(), RECENT_SUCCESS_TTL, RECENT_SUCCESS_LIMIT);
  }

  public WecomSmartSheetRecordClient(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetValueCodec valueCodec) {
    this(config, apiClient, fieldCatalog, valueCodec, null,
        Clock.systemUTC(), RECENT_SUCCESS_TTL, RECENT_SUCCESS_LIMIT);
  }

  WecomSmartSheetRecordClient(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetValueCodec valueCodec,
      AuxiliarySmartSheetTargets auxiliaryTargets,
      Clock clock,
      Duration recentSuccessTtl,
      int recentSuccessLimit) {
    this.config = Objects.requireNonNull(config, "config is required");
    this.apiClient = Objects.requireNonNull(apiClient, "apiClient is required");
    this.fieldCatalog = Objects.requireNonNull(fieldCatalog, "fieldCatalog is required");
    this.valueCodec = Objects.requireNonNull(valueCodec, "valueCodec is required");
    this.auxiliaryTargets = auxiliaryTargets;
    this.clock = Objects.requireNonNull(clock, "clock is required");
    if (recentSuccessTtl == null || recentSuccessTtl.isZero() || recentSuccessTtl.isNegative()
        || recentSuccessLimit <= 0) {
      throw new IllegalArgumentException("Recent success cache settings must be positive");
    }
    this.recentSuccessTtl = recentSuccessTtl;
    this.recentSuccessLimit = recentSuccessLimit;
  }

  WecomSmartSheetRecordClient(
      WecomSmartSheetConfig config,
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetFieldCatalog fieldCatalog,
      WecomSmartSheetValueCodec valueCodec,
      Clock clock,
      Duration recentSuccessTtl,
      int recentSuccessLimit) {
    this(config, apiClient, fieldCatalog, valueCodec, null,
        clock, recentSuccessTtl, recentSuccessLimit);
  }

  public List<SheetRow> fetchIncrementalRows(
      SheetSource source, LocalDateTime modifiedAfter, int limit, Duration timeout) {
    validate(source, modifiedAfter, limit, timeout);
    AuxiliarySmartSheetTarget target = targetFor(source);
    Map<String, WecomSmartSheetField> fields = fieldCatalog.visibleFields(target, timeout);
    List<TimestampedRow> included = new ArrayList<>();
    Set<String> recordIds = new HashSet<>();
    Long expectedTotal = null;
    long loadedCount = 0;
    long offset = 0;

    for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
      JsonNode response = apiClient.postForTarget(GET_OPERATION, request(target, offset), timeout,
          "PRIMARY".equals(target.role()));
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
          log.info("WeCom Smart Sheet records read, role={}, returned={}, matchedSince={}, modifiedAfter={}",
              target.role(), loadedCount, included.size(), modifiedAfter);
          return limitedSorted(included, limit);
      }
      if (pageNumber == MAX_PAGES - 1) {
        throw invalid("pagination exceeded maximum pages");
      }
      offset = nextOffset(page.next(), offset);
    }
    throw invalid("pagination exceeded maximum pages");
  }

  /** Reads only the records named by a verified WeCom callback. */
  public List<SheetRow> fetchRecords(
      AuxiliarySmartSheetTarget target, List<String> recordIds, Duration timeout) {
    if (target == null || !target.configured() || recordIds == null || recordIds.isEmpty()
        || timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Configured target, record identifiers, and positive timeout are required");
    }
    List<String> ids = recordIds.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(id -> !id.isEmpty())
        .distinct()
        .toList();
    if (ids.isEmpty() || ids.size() > PAGE_SIZE) {
      throw new IllegalArgumentException("Between 1 and " + PAGE_SIZE + " record identifiers are required");
    }
    Map<String, WecomSmartSheetField> fields = fieldCatalog.visibleFields(target, timeout);
    Map<String, Object> request = request(target, 0);
    request.put("record_ids", ids);
    JsonNode response = apiClient.postForTarget(GET_OPERATION, request, timeout,
        "PRIMARY".equals(target.role()));
    Page page = page(response);
    if (page.hasMore()) {
      throw invalid("callback record response was paginated");
    }
    List<SheetRow> rows = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int index = 0; index < page.records().size(); index++) {
      rows.add(row(page.records().get(index), fields, seen, index).row());
    }
    if (!seen.equals(new HashSet<>(ids))) {
      throw invalid("callback record response did not contain every requested record");
    }
    return List.copyOf(rows);
  }

  public String createRow(String sourceTable, Map<String, Object> fields, Duration timeout) {
    AuxiliarySmartSheetTarget target = targetForSourceTable(sourceTable);
    validateWrite(target, sourceTable, fields, timeout);
    long deadline = deadline(timeout);
    String uniqueTitle = target.uniqueFieldTitle();
    Object uniqueValue = fields.get(uniqueTitle);
    if (!(uniqueValue instanceof String exactValue) || exactValue.isBlank()) {
      throw new IllegalArgumentException("A nonblank value is required for unique field: " + uniqueTitle);
    }
    String lockKey = cacheKey(target, sourceTable, exactValue);
    CreateLock lock = acquireCreateLock(lockKey);
    boolean locked = false;
    try {
      locked = tryCreateLock(lock, deadline);
      Map<String, JsonNode> encoded = encodeFieldsUntil(target, fields, deadline);
      WecomSmartSheetField uniqueField = fieldCatalog.requireWritable(
          target, uniqueTitle, remaining(deadline, ADD_OPERATION));
      Match match = exactMatch(target, uniqueField, exactValue, deadline);
      if (match.count() > 1) {
        throw invalid(GET_OPERATION, "multiple exact unique-field matches were found");
      }
      if (match.count() == 1) {
        lock.confirmedRecordId = match.recordId();
        rememberRecentSuccess(lockKey, match.recordId());
        return match.recordId();
      }
      if (lock.confirmedRecordId != null) {
        return lock.confirmedRecordId;
      }
      String recentRecordId = recentSuccess(lockKey);
      if (recentRecordId != null) {
        lock.confirmedRecordId = recentRecordId;
        return recentRecordId;
      }
      String createdRecordId = add(target, encoded, remaining(deadline, ADD_OPERATION));
      lock.confirmedRecordId = createdRecordId;
      rememberRecentSuccess(lockKey, createdRecordId);
      return createdRecordId;
    } finally {
      try {
        if (locked) {
          lock.coordination.unlock();
        }
      } finally {
        releaseCreateLock(lockKey, lock);
      }
    }
  }

  public void updateRow(
      String sourceTable, String sourceRowId, Map<String, Object> fields, Duration timeout) {
    AuxiliarySmartSheetTarget target = targetForSourceTable(sourceTable);
    validateWrite(target, sourceTable, fields, timeout);
    String recordId = sourceRowId == null ? "" : sourceRowId.trim();
    if (recordId.isEmpty()) {
      throw new IllegalArgumentException("Record identifier is required");
    }
    if (fields.values().stream().allMatch(Objects::isNull)) {
      throw new IllegalArgumentException("At least one non-null field is required");
    }
    Map<String, JsonNode> encoded = encodeFields(target, fields, timeout);
    if (encoded.isEmpty()) {
      throw new IllegalArgumentException("At least one non-null field is required");
    }
    Map<String, Object> record = new LinkedHashMap<>();
    record.put("record_id", recordId);
    record.put("values", encoded);
    JsonNode response = apiClient.postForTarget(UPDATE_OPERATION, writeRequest(target, record), timeout,
        "PRIMARY".equals(target.role()));
    confirmUpdated(response, recordId);
  }

  private Map<String, Object> request(AuxiliarySmartSheetTarget target, long offset) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("docid", target.documentId());
    request.put("sheet_id", target.sheetId());
    request.put("view_id", target.viewId());
    request.put("key_type", FIELD_TITLE_KEY_TYPE);
    request.put("offset", offset);
    request.put("limit", PAGE_SIZE);
    return request;
  }

  private Match exactMatch(
      AuxiliarySmartSheetTarget target,
      WecomSmartSheetField uniqueField,
      String exactValue,
      long deadline) {
    Set<String> recordIds = new HashSet<>();
    Long expectedTotal = null;
    long loadedCount = 0;
    long offset = 0;
    int matchCount = 0;
    String matchedRecordId = null;

    for (int pageNumber = 0; pageNumber < MAX_PAGES; pageNumber++) {
      JsonNode response = apiClient.post(
          GET_OPERATION, lookupRequest(target, uniqueField.fieldId(), offset), remaining(deadline, GET_OPERATION));
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

  private Map<String, Object> lookupRequest(
      AuxiliarySmartSheetTarget target, String uniqueFieldId, long offset) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("docid", target.documentId());
    request.put("sheet_id", target.sheetId());
    request.put("view_id", target.viewId());
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

  private Map<String, JsonNode> encodeFields(
      AuxiliarySmartSheetTarget target, Map<String, Object> fields, Duration timeout) {
    Map<String, JsonNode> encoded = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      WecomSmartSheetField field = fieldCatalog.requireWritable(target, entry.getKey(), timeout);
      encoded.put(field.fieldId(), valueCodec.encode(field, entry.getValue()));
    }
    return encoded;
  }

  private Map<String, JsonNode> encodeFieldsUntil(
      AuxiliarySmartSheetTarget target, Map<String, Object> fields, long deadline) {
    Map<String, JsonNode> encoded = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      if (entry.getValue() == null) {
        continue;
      }
      WecomSmartSheetField field =
          fieldCatalog.requireWritable(target, entry.getKey(), remaining(deadline, ADD_OPERATION));
      encoded.put(field.fieldId(), valueCodec.encode(field, entry.getValue()));
    }
    return encoded;
  }

  private String add(
      AuxiliarySmartSheetTarget target, Map<String, JsonNode> encoded, Duration timeout) {
    JsonNode response = apiClient.postForTarget(
        ADD_OPERATION, writeRequest(target, Map.of("values", encoded)), timeout,
        "PRIMARY".equals(target.role()));
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

  private Map<String, Object> writeRequest(
      AuxiliarySmartSheetTarget target, Map<String, ?> record) {
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("docid", target.documentId());
    request.put("sheet_id", target.sheetId());
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

  private static String cacheKey(
      AuxiliarySmartSheetTarget target, String sourceTable, String uniqueValue) {
    return String.join("\u0000",
        target.role(),
        target.documentId(),
        target.sheetId(),
        target.viewId(),
        sourceTable == null ? "" : sourceTable.trim(),
        normalizedLockKey(uniqueValue));
  }

  private static long deadline(Duration timeout) {
    long timeoutNanos;
    try {
      timeoutNanos = timeout.toNanos();
    } catch (ArithmeticException ex) {
      timeoutNanos = Long.MAX_VALUE;
    }
    return System.nanoTime() + timeoutNanos;
  }

  private static Duration remaining(long deadline, String operation) {
    long remainingNanos = deadline - System.nanoTime();
    if (remainingNanos <= 0) {
      throw invalid(operation, "request timeout expired");
    }
    return Duration.ofNanos(remainingNanos);
  }

  private static boolean tryCreateLock(CreateLock lock, long deadline) {
    try {
      long remainingNanos = remaining(deadline, ADD_OPERATION).toNanos();
      if (!lock.coordination.tryLock(remainingNanos, TimeUnit.NANOSECONDS)) {
        throw invalid(ADD_OPERATION, "duplicate-write coordination timed out");
      }
      return true;
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw invalid(ADD_OPERATION, "duplicate-write coordination was interrupted");
    }
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

  private String recentSuccess(String lockKey) {
    synchronized (recentSuccesses) {
      removeExpiredRecentSuccesses(clock.instant());
      RecentSuccess success = recentSuccesses.get(lockKey);
      return success == null ? null : success.recordId();
    }
  }

  private void rememberRecentSuccess(String lockKey, String recordId) {
    synchronized (recentSuccesses) {
      Instant now = clock.instant();
      removeExpiredRecentSuccesses(now);
      recentSuccesses.put(lockKey, new RecentSuccess(recordId, now.plus(recentSuccessTtl)));
      while (recentSuccesses.size() > recentSuccessLimit) {
        Iterator<String> oldest = recentSuccesses.keySet().iterator();
        oldest.next();
        oldest.remove();
      }
    }
  }

  private void removeExpiredRecentSuccesses(Instant now) {
    recentSuccesses.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
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

  private AuxiliarySmartSheetTarget targetFor(SheetSource source) {
    return targetForSourceTable(source.sourceTable(), source.sheetId());
  }

  private AuxiliarySmartSheetTarget targetForSourceTable(String sourceTable) {
    return targetForSourceTable(sourceTable, null);
  }

  private AuxiliarySmartSheetTarget targetForSourceTable(String sourceTable, String requestedDocumentId) {
    String normalized = sourceTable == null ? "" : sourceTable.trim();
    String primarySource = config.sourceTable();
    if (normalized.equals(primarySource)) {
      if (requestedDocumentId != null && !requestedDocumentId.isBlank()
          && !config.documentId().equals(requestedDocumentId.trim())) {
        throw new IllegalArgumentException("Requested document does not match configured document");
      }
      return new AuxiliarySmartSheetTarget(
          "PRIMARY", config.documentId(), config.sheetId(), config.viewId(),
          config.uniqueFieldTitle(), "");
    }
    int separator = normalized.indexOf(':');
    String role = separator > 0 ? normalized.substring(0, separator).trim().toUpperCase() : "";
    String childSheetId = separator > 0 ? normalized.substring(separator + 1).trim() : "";
    if (auxiliaryTargets != null) {
      AuxiliarySmartSheetTarget target = auxiliaryTargets.forRole(role).orElse(null);
      if (target != null && (childSheetId.isBlank() || target.sheetId().equals(childSheetId))) {
        if (requestedDocumentId == null || requestedDocumentId.isBlank()
            || target.documentId().equals(requestedDocumentId.trim())) {
          return target;
        }
      }
    }
    throw new IllegalArgumentException("No Smart Sheet target configured for source table: " + normalized);
  }

  private void validateWrite(
      AuxiliarySmartSheetTarget target,
      String sourceTable,
      Map<String, Object> fields,
      Duration timeout) {
    if (fields == null || timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Fields and a positive timeout are required");
    }
    if (target == null || !target.configured() || sourceTable == null || sourceTable.isBlank()) {
      throw new IllegalArgumentException("Smart Sheet target is not configured");
    }
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

  private record RecentSuccess(String recordId, Instant expiresAt) {}

  private static final class CreateLock {
    private final ReentrantLock coordination = new ReentrantLock();
    private int participants;
    private volatile boolean retired;
    private String confirmedRecordId;
  }

  private record TimestampedRow(String recordId, LocalDateTime updatedAt, SheetRow row) {}
}
