package com.privateflow.modules.tablewrite.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class WecomSmartSheetFieldCatalog {

  private static final Duration CACHE_TTL = Duration.ofMinutes(5);
  private static final int PAGE_SIZE = 1000;
  private static final String LOAD_OPERATION = "get_fields";

  private final WecomSmartSheetApiClient apiClient;
  private final WecomSmartSheetConfig config;
  private final Clock clock;
  private final LongSupplier ticker;
  private volatile Snapshot snapshot;
  private InFlight inFlight;
  private final Map<String, Snapshot> targetSnapshots = new ConcurrentHashMap<>();

  @Autowired
  public WecomSmartSheetFieldCatalog(WecomSmartSheetApiClient apiClient, WecomSmartSheetConfig config) {
    this(apiClient, config, Clock.systemUTC(), System::nanoTime);
  }

  WecomSmartSheetFieldCatalog(WecomSmartSheetApiClient apiClient, WecomSmartSheetConfig config, Clock clock) {
    this(apiClient, config, clock, System::nanoTime);
  }

  WecomSmartSheetFieldCatalog(
      WecomSmartSheetApiClient apiClient,
      WecomSmartSheetConfig config,
      Clock clock,
      LongSupplier ticker) {
    this.apiClient = apiClient;
    this.config = config;
    this.clock = clock;
    this.ticker = ticker;
  }

  public Map<String, WecomSmartSheetField> visibleFields(Duration timeout) {
    WecomRequestDeadline deadline = WecomRequestDeadline.start(timeout, LOAD_OPERATION, ticker);
    config.requireConfigured();
    Snapshot current = snapshot;
    Instant now = clock.instant();
    if (isFresh(current, now)) {
      return current.fields();
    }
    InFlight active;
    boolean loader = false;
    synchronized (this) {
      current = snapshot;
      now = clock.instant();
      if (isFresh(current, now)) {
        return current.fields();
      }
      if (inFlight == null) {
        active = new InFlight();
        inFlight = active;
        loader = true;
      } else {
        active = inFlight;
      }
      active.participants++;
    }
    try {
      if (loader) {
        try {
          Map<String, WecomSmartSheetField> fields = load(deadline);
          deadline.remaining();
          Snapshot loaded = new Snapshot(Map.copyOf(fields), clock.instant());
          snapshot = loaded;
          detach(active);
          active.result.complete(loaded);
          return loaded.fields();
        } catch (RuntimeException ex) {
          RuntimeException failure = loadFailure(ex);
          detach(active);
          active.result.completeExceptionally(failure);
          throw failure;
        }
      }
      return await(active.result, deadline).fields();
    } finally {
      releaseParticipant(active);
    }
  }

  private synchronized void detach(InFlight active) {
    if (inFlight == active) {
      inFlight = null;
    }
  }

  private synchronized void releaseParticipant(InFlight active) {
    if (active.participants <= 0) {
      throw new IllegalStateException("WeCom visible field catalog coordination was invalid");
    }
    active.participants--;
  }

  public WecomSmartSheetField requireWritable(String title, Duration timeout) {
    String requested = title == null ? "" : title.trim();
    if (requested.isEmpty()) {
      throw new IllegalArgumentException("Field title is required");
    }
    WecomSmartSheetField field = visibleFields(timeout).get(requested);
    if (field == null) {
      throw new IllegalArgumentException("Unknown visible field: " + requested);
    }
    if (!field.writable()) {
      throw new IllegalArgumentException("Field is visible but read-only: " + requested);
    }
    return field;
  }

  public Map<String, WecomSmartSheetField> visibleFields(
      AuxiliarySmartSheetTarget target, Duration timeout) {
    if (target == null || !target.configured()) {
      throw new IllegalArgumentException("Smart Sheet target is not configured");
    }
    String key = target.documentId() + "\u0000" + target.sheetId() + "\u0000" + target.viewId();
    Snapshot current = targetSnapshots.get(key);
    Instant now = clock.instant();
    if (isFresh(current, now)) {
      return current.fields();
    }
    WecomRequestDeadline deadline = WecomRequestDeadline.start(timeout, LOAD_OPERATION, ticker);
    Map<String, WecomSmartSheetField> fields = load(target, deadline);
    deadline.remaining();
    Snapshot loaded = new Snapshot(Map.copyOf(fields), clock.instant());
    targetSnapshots.put(key, loaded);
    return loaded.fields();
  }

  public WecomSmartSheetField requireWritable(
      AuxiliarySmartSheetTarget target, String title, Duration timeout) {
    String requested = title == null ? "" : title.trim();
    if (requested.isEmpty()) {
      throw new IllegalArgumentException("Field title is required");
    }
    WecomSmartSheetField field = visibleFields(target, timeout).get(requested);
    if (field == null) {
      throw new IllegalArgumentException("Unknown visible field: " + requested);
    }
    if (!field.writable()) {
      throw new IllegalArgumentException("Field is visible but read-only: " + requested);
    }
    return field;
  }

  /** Clears all cached field IDs after a Smart Sheet target configuration changes. */
  public synchronized void invalidate() {
    snapshot = null;
    targetSnapshots.clear();
  }

  private Map<String, WecomSmartSheetField> load(WecomRequestDeadline deadline) {
    return load(new AuxiliarySmartSheetTarget(
        "PRIMARY", config.documentId(), config.sheetId(), config.viewId(),
        config.uniqueFieldTitle(), ""), deadline);
  }

  private Map<String, WecomSmartSheetField> load(
      AuxiliarySmartSheetTarget target, WecomRequestDeadline deadline) {
    Map<String, WecomSmartSheetField> fieldsByTitle = new LinkedHashMap<>();
    Set<String> fieldIds = new HashSet<>();
    int offset = 0;
    Integer expectedTotal = null;
    while (expectedTotal == null || offset < expectedTotal) {
      Map<String, Object> request = new LinkedHashMap<>();
      request.put("docid", target.documentId());
      request.put("sheet_id", target.sheetId());
      request.put("view_id", target.viewId());
      request.put("offset", offset);
      request.put("limit", PAGE_SIZE);
      JsonNode response = apiClient.postForTarget(LOAD_OPERATION, request, deadline.remaining(),
          "PRIMARY".equals(target.role()));
      Page page = page(response);
      if (expectedTotal == null) {
        expectedTotal = page.total();
      } else if (expectedTotal != page.total()) {
        throw invalidCatalog();
      }
      if (page.fields().isEmpty() && offset < expectedTotal) {
        throw invalidCatalog();
      }
      for (WecomSmartSheetField field : page.fields()) {
        if (fieldsByTitle.putIfAbsent(field.title(), field) != null) {
          throw invalidCatalog();
        }
        if (!fieldIds.add(field.fieldId())) {
          throw invalidCatalog();
        }
      }
      int nextOffset = offset + page.fields().size();
      if (nextOffset <= offset && offset < expectedTotal) {
        throw invalidCatalog();
      }
      offset = nextOffset;
      if (offset > expectedTotal) {
        throw invalidCatalog();
      }
    }
    return fieldsByTitle;
  }

  private static Page page(JsonNode response) {
    if (response == null || !response.isObject()) {
      throw invalidCatalog();
    }
    JsonNode total = response.get("total");
    JsonNode fields = response.get("fields");
    if (total == null || !total.isIntegralNumber() || !total.canConvertToInt() || total.intValue() < 0
        || fields == null || !fields.isArray()) {
      throw invalidCatalog();
    }
    Map<String, WecomSmartSheetField> parsed = new LinkedHashMap<>();
    for (JsonNode node : fields) {
      WecomSmartSheetField field = field(node);
      if (parsed.putIfAbsent(field.title(), field) != null) {
        throw invalidCatalog();
      }
    }
    return new Page(total.intValue(), parsed.values().stream().toList());
  }

  private static WecomSmartSheetField field(JsonNode node) {
    if (node == null || !node.isObject()) {
      throw invalidCatalog();
    }
    String fieldId = requiredText(node.get("field_id"));
    String title = WecomSmartSheetFieldMetadata.requireTitle(node);
    String type = requiredText(node.get("field_type"));
    boolean includesTime = "FIELD_TYPE_DATE_TIME".equals(type) && dateTimeIncludesTime(node.get("property_date_time"));
    Map<String, String> options = options(node, type);
    return new WecomSmartSheetField(fieldId, title, type, options, includesTime);
  }

  private static boolean dateTimeIncludesTime(JsonNode property) {
    if (property == null || !property.isObject() || !property.path("format").isTextual()) {
      throw invalidCatalog();
    }
    String format = property.path("format").textValue().trim();
    if (format.isEmpty()) {
      throw invalidCatalog();
    }
    return format.toLowerCase(java.util.Locale.ROOT).contains("h");
  }

  private static Map<String, String> options(JsonNode field, String type) {
    String propertyName = switch (type) {
      case "FIELD_TYPE_SELECT" -> "property_select";
      case "FIELD_TYPE_SINGLE_SELECT" -> "property_single_select";
      default -> null;
    };
    if (propertyName == null) {
      return Map.of();
    }
    JsonNode property = field.get(propertyName);
    JsonNode options = property == null ? null : property.get("options");
    if (property == null || !property.isObject() || options == null || !options.isArray()) {
      throw invalidCatalog();
    }
    Map<String, String> idsByText = new LinkedHashMap<>();
    Set<String> optionIds = new HashSet<>();
    Set<String> ambiguousTexts = new HashSet<>();
    for (JsonNode option : options) {
      if (option == null || !option.isObject()) {
        throw invalidCatalog();
      }
      String id = requiredText(option.get("id"));
      String text = requiredText(option.get("text"));
      if (ambiguousTexts.contains(text)) {
        // Already known to be ambiguous; do not re-introduce it.
      } else if (idsByText.containsKey(text)) {
        // A duplicate label cannot be encoded safely; keep the field visible but
        // remove the ambiguous label so a write fails closed for that value.
        idsByText.remove(text);
        ambiguousTexts.add(text);
      } else {
        idsByText.put(text, id);
      }
      if (!optionIds.add(id)) {
        throw invalidCatalog();
      }
    }
    return idsByText;
  }

  private static String requiredText(JsonNode value) {
    if (value == null || !value.isTextual() || value.textValue().trim().isEmpty()) {
      throw invalidCatalog();
    }
    return value.textValue().trim();
  }

  private static boolean isFresh(Snapshot current, Instant now) {
    return current != null && now.isBefore(current.loadedAt().plus(CACHE_TTL));
  }

  private static IllegalStateException invalidCatalog() {
    return new IllegalStateException("WeCom visible field catalog was invalid");
  }

  private static IllegalStateException loadFailure() {
    return new IllegalStateException("WeCom visible field catalog could not be loaded");
  }

  private static RuntimeException loadFailure(RuntimeException failure) {
    return failure instanceof WecomSmartSheetException ? failure : loadFailure();
  }

  static <T> T await(CompletableFuture<T> result, WecomRequestDeadline deadline) {
    T completed;
    try {
      completed = result.get(deadline.remaining().toNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException ex) {
      throw new WecomSmartSheetException(LOAD_OPERATION, "catalog load wait timed out", null);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new WecomSmartSheetException(LOAD_OPERATION, "catalog load wait was interrupted", null);
    } catch (ExecutionException ex) {
      deadline.remaining();
      throw completedFailure(ex.getCause());
    }
    deadline.remaining();
    return completed;
  }

  private static RuntimeException completedFailure(Throwable cause) {
    return cause instanceof RuntimeException failure ? failure : loadFailure();
  }

  private record Page(int total, java.util.List<WecomSmartSheetField> fields) {}
  private record Snapshot(Map<String, WecomSmartSheetField> fields, Instant loadedAt) {}
  private static final class InFlight {
    private final CompletableFuture<Snapshot> result = new CompletableFuture<>();
    private int participants;
  }
}
