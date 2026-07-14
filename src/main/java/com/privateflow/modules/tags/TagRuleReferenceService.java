package com.privateflow.modules.tags;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TagRuleReferenceService {

  private static final Set<String> VALUE_ID_KEYS = Set.of("tagvalueid", "tagid", "canonicaltagvalueid");
  private static final Set<String> VALUE_CODE_KEYS = Set.of("tagvalue", "tagcode", "tagname");
  private static final Set<String> CATEGORY_ID_KEYS = Set.of("tagcategoryid", "categoryid");
  private static final Set<String> CATEGORY_CODE_KEYS = Set.of("tagcategorykey", "categorykey", "tagcategory");
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public TagRuleReferenceService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  public ReferenceCounts countReferences(List<TagCategory> categories, List<TagValue> values) {
    Map<Long, Long> categoryCounts = new LinkedHashMap<>();
    Map<Long, Long> valueCounts = new LinkedHashMap<>();
    categories.forEach(category -> categoryCounts.put(category.id(), 0L));
    values.forEach(value -> valueCounts.put(value.id(), 0L));
    Map<Long, TagCategory> categoryById = new LinkedHashMap<>();
    categories.forEach(category -> categoryById.put(category.id(), category));
    for (RuleDocument rule : rules()) {
      for (TagValue value : values) {
        TagCategory category = categoryById.get(value.categoryId());
        if (category == null) {
          category = categories.stream().filter(item -> item.id() == value.categoryId()).findFirst().orElse(null);
        }
        if (referencesValue(rule.condition(), value, category) || referencesValue(rule.action(), value, category)) {
          valueCounts.computeIfPresent(value.id(), (ignored, count) -> count + 1);
        }
      }
      for (TagCategory category : categories) {
        boolean direct = referencesCategory(rule.condition(), category) || referencesCategory(rule.action(), category);
        boolean viaValue = category.values().stream().anyMatch(value ->
            referencesValue(rule.condition(), value, category) || referencesValue(rule.action(), value, category));
        if (direct || viaValue) {
          categoryCounts.computeIfPresent(category.id(), (ignored, count) -> count + 1);
        }
      }
    }
    return new ReferenceCounts(Map.copyOf(categoryCounts), Map.copyOf(valueCounts));
  }

  public int rewriteValue(TagValue source, TagCategory sourceCategory, TagValue target, TagCategory targetCategory) {
    ValueReplacement replacement = new ValueReplacement(source, sourceCategory, target, targetCategory);
    return rewriteRules(List.of(replacement), null);
  }

  public int rewriteCategory(
      TagCategory source,
      TagCategory target,
      Map<Long, TagValue> targetBySourceValueId) {
    List<ValueReplacement> replacements = new ArrayList<>();
    for (TagValue sourceValue : source.values()) {
      TagValue targetValue = targetBySourceValueId.get(sourceValue.id());
      if (targetValue != null) {
        replacements.add(new ValueReplacement(sourceValue, source, targetValue, target));
      }
    }
    return rewriteRules(replacements, new CategoryReplacement(source, target));
  }

  private int rewriteRules(List<ValueReplacement> values, CategoryReplacement category) {
    int changedRules = 0;
    for (RuleDocument rule : rules()) {
      JsonNode condition = rule.condition().deepCopy();
      JsonNode action = rule.action().deepCopy();
      boolean changed = rewrite(condition, values, category) | rewrite(action, values, category);
      if (changed) {
        jdbcTemplate.update("""
            UPDATE followup_rules
            SET condition_json = ?, action_config = ?, updated_at = NOW()
            WHERE id = ?
            """, json(condition), json(action), rule.id());
        changedRules++;
      }
    }
    return changedRules;
  }

  private boolean rewrite(JsonNode node, List<ValueReplacement> values, CategoryReplacement category) {
    if (node instanceof ObjectNode object) {
      boolean changed = false;
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      List<Map.Entry<String, JsonNode>> snapshot = new ArrayList<>();
      fields.forEachRemaining(snapshot::add);
      for (Map.Entry<String, JsonNode> field : snapshot) {
        String key = normalizeKey(field.getKey());
        JsonNode value = field.getValue();
        if (category != null) {
          changed |= replaceCategoryField(object, field.getKey(), key, value, category);
        }
        for (ValueReplacement replacement : values) {
          changed |= replaceValueField(object, field.getKey(), key, value, replacement);
        }
        changed |= rewrite(object.get(field.getKey()), values, category);
      }
      return changed;
    }
    if (node instanceof ArrayNode array) {
      boolean changed = false;
      for (JsonNode child : array) {
        changed |= rewrite(child, values, category);
      }
      return changed;
    }
    return false;
  }

  private boolean replaceCategoryField(
      ObjectNode object,
      String fieldName,
      String key,
      JsonNode current,
      CategoryReplacement replacement) {
    if (CATEGORY_ID_KEYS.contains(key) && matchesId(current, replacement.source().id())) {
      object.put(fieldName, replacement.target().id());
      return true;
    }
    if (CATEGORY_CODE_KEYS.contains(key) && textEquals(current, replacement.source().categoryKey())) {
      object.put(fieldName, replacement.target().categoryKey());
      return true;
    }
    if ("field".equals(key) && replacement.source().boundField() != null
        && replacement.target().boundField() != null
        && textEquals(current, replacement.source().boundField())) {
      object.put(fieldName, replacement.target().boundField());
      return true;
    }
    return false;
  }

  private boolean replaceValueField(
      ObjectNode object,
      String fieldName,
      String key,
      JsonNode current,
      ValueReplacement replacement) {
    if (VALUE_ID_KEYS.contains(key) && matchesId(current, replacement.source().id())) {
      object.put(fieldName, replacement.target().id());
      return true;
    }
    if (VALUE_CODE_KEYS.contains(key)) {
      String target = replacementText(current, replacement.source(), replacement.target());
      if (target != null) {
        object.put(fieldName, target);
        return true;
      }
    }
    if ("value".equals(key)
        && (isTagCondition(object, replacement.sourceCategory()) || isTagCondition(object, replacement.targetCategory()))) {
      String target = replacementText(current, replacement.source(), replacement.target());
      if (target != null) {
        object.put(fieldName, target);
        return true;
      }
    }
    return false;
  }

  private boolean referencesCategory(JsonNode node, TagCategory category) {
    if (node instanceof ObjectNode object) {
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String key = normalizeKey(field.getKey());
        JsonNode value = field.getValue();
        if (CATEGORY_ID_KEYS.contains(key) && matchesId(value, category.id())) {
          return true;
        }
        if (CATEGORY_CODE_KEYS.contains(key) && textEquals(value, category.categoryKey())) {
          return true;
        }
        if ("field".equals(key) && category.boundField() != null && textEquals(value, category.boundField())) {
          return true;
        }
        if (referencesCategory(value, category)) {
          return true;
        }
      }
    } else if (node instanceof ArrayNode array) {
      for (JsonNode child : array) {
        if (referencesCategory(child, category)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean referencesValue(JsonNode node, TagValue value, TagCategory category) {
    if (node instanceof ObjectNode object) {
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> field = fields.next();
        String key = normalizeKey(field.getKey());
        JsonNode current = field.getValue();
        if (VALUE_ID_KEYS.contains(key) && matchesId(current, value.id())) {
          return true;
        }
        if (VALUE_CODE_KEYS.contains(key) && matchesValueText(current, value)) {
          return true;
        }
        if ("value".equals(key) && isTagCondition(object, category) && matchesValueText(current, value)) {
          return true;
        }
        if (referencesValue(current, value, category)) {
          return true;
        }
      }
    } else if (node instanceof ArrayNode array) {
      for (JsonNode child : array) {
        if (referencesValue(child, value, category)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isTagCondition(ObjectNode object, TagCategory category) {
    JsonNode field = object.get("field");
    if (field == null || !field.isTextual()) {
      return false;
    }
    String raw = field.asText();
    return normalizeKey(raw).contains("tag")
        || category != null && (raw.equals(category.categoryKey()) || raw.equals(category.boundField()));
  }

  private boolean matchesValueText(JsonNode current, TagValue source) {
    if (current == null || !current.isTextual()) {
      return false;
    }
    String raw = current.asText();
    return raw.equals(source.tagValue()) || raw.equals(source.displayName()) || source.synonyms().contains(raw);
  }

  private String replacementText(JsonNode current, TagValue source, TagValue target) {
    if (current == null || !current.isTextual()) {
      return null;
    }
    String raw = current.asText();
    if (raw.equals(source.tagValue())) {
      return target.tagValue();
    }
    if (raw.equals(source.displayName()) || source.synonyms().contains(raw)) {
      return target.displayName();
    }
    return null;
  }

  private boolean matchesId(JsonNode value, long id) {
    return value != null && (value.isIntegralNumber() && value.asLong() == id
        || value.isTextual() && value.asText().equals(String.valueOf(id)));
  }

  private boolean textEquals(JsonNode value, String expected) {
    return expected != null && value != null && value.isTextual() && expected.equals(value.asText());
  }

  private String normalizeKey(String value) {
    return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
  }

  private List<RuleDocument> rules() {
    return jdbcTemplate.query("SELECT id, name, condition_json, action_config FROM followup_rules ORDER BY id", (rs, rowNum) -> {
      long id = rs.getLong("id");
      String name = rs.getString("name");
      try {
        return new RuleDocument(
            id,
            objectMapper.readTree(rs.getString("condition_json")),
            objectMapper.readTree(rs.getString("action_config")));
      } catch (Exception ex) {
        throw new ApiException(
            ApiErrorCodes.BAD_REQUEST,
            "跟进规则「" + (name == null || name.isBlank() ? id : name) + "」配置格式损坏，请先修正规则");
      }
    });
  }

  private String json(JsonNode value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception ex) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "更新跟进规则标签引用失败，请稍后重试");
    }
  }

  public record ReferenceCounts(Map<Long, Long> categoryCounts, Map<Long, Long> valueCounts) {
    public long category(long id) {
      return categoryCounts.getOrDefault(id, 0L);
    }

    public long value(long id) {
      return valueCounts.getOrDefault(id, 0L);
    }
  }

  private record RuleDocument(long id, JsonNode condition, JsonNode action) {
  }

  private record ValueReplacement(
      TagValue source,
      TagCategory sourceCategory,
      TagValue target,
      TagCategory targetCategory) {
  }

  private record CategoryReplacement(TagCategory source, TagCategory target) {
  }
}
