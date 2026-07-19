package com.privateflow.modules.skill.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.skill.CustomerAnalysis;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.FollowupSuggest;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.SkillResponse;
import com.privateflow.modules.skill.Suggestion;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SkillResponseParser {

  private static final Logger log = LoggerFactory.getLogger(SkillResponseParser.class);
  private final ObjectMapper objectMapper;

  public SkillResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public SkillResponse parseReplies(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      SkillResponse structured = parseStructured(root);
      if (structured != null) {
        return structured;
      }
      String result = text(root.path("result"));
      if (result != null) {
        SkillResponse embedded = parseEmbeddedStructured(result);
        return embedded == null ? SkillResponse.guidanceOnly(result) : embedded;
      }
      String guidance = text(root.path("guidance"));
      if (guidance != null) {
        return SkillResponse.guidanceOnly(guidance);
      }
      throw invalid("Skill response contains no suggestions or guidance", null);
    } catch (SkillGatewayException ex) {
      throw ex;
    } catch (Exception ex) {
      throw invalid("Skill 返回格式异常", ex);
    }
  }

  private SkillResponse parseStructured(JsonNode root) {
    JsonNode suggestions = root.path("suggestions");
    if (!suggestions.isArray() || suggestions.isEmpty()) {
      return null;
    }
    return new SkillResponse(
        normalizeSuggestions(suggestions),
        parseCustomerAnalysis(root.path("customer_analysis")),
        parseFollowupSuggest(root.path("followup_suggest")),
        parseProfileUpdates(root.path("profile_updates")),
        text(root.path("guidance")));
  }

  private SkillResponse parseEmbeddedStructured(String result) {
    try {
      JsonNode embedded = objectMapper.readTree(cleanJson(result));
      return parseStructured(embedded);
    } catch (Exception ignored) {
      return null;
    }
  }

  private String cleanJson(String content) {
    String trimmed = content == null ? "" : content.trim();
    if (!trimmed.startsWith("```")) {
      return trimmed;
    }
    String withoutOpening = trimmed.replaceFirst("^```[A-Za-z0-9_-]*\\s*", "");
    return withoutOpening.replaceFirst("\\s*```$", "").trim();
  }

  public ProfileUpdates parseProfileUpdatesOnly(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      ProfileUpdates updates = parseProfileUpdates(root.path("profile_updates"));
      return updates == null ? ProfileUpdates.empty() : updates;
    } catch (Exception ex) {
      log.warn("profile_updates parse failed: {}", ex.getMessage());
      return ProfileUpdates.empty();
    }
  }

  private List<Suggestion> normalizeSuggestions(JsonNode node) {
    if (!node.isArray() || node.isEmpty()) {
      throw invalid("Skill suggestions 为空或格式异常", null);
    }
    List<Suggestion> parsed = new ArrayList<>();
    for (JsonNode item : node) {
      String text = text(item.path("text"));
      String direction = text(item.path("direction"));
      String reason = text(item.path("reason"));
      parsed.add(new Suggestion(text, direction == null ? "" : direction, reason == null ? "" : reason));
    }
    String fallbackText = parsed.stream().map(Suggestion::text).filter(value -> value != null && !value.isBlank()).findFirst()
        .orElseThrow(() -> invalid("Skill suggestions 文本为空", null));
    List<Suggestion> normalized = new ArrayList<>();
    for (int i = 0; i < parsed.size() && normalized.size() < 3; i++) {
      Suggestion suggestion = parsed.get(i);
      normalized.add(new Suggestion(
          suggestion.text() == null || suggestion.text().isBlank() ? fallbackText : suggestion.text(),
          suggestion.direction(),
          suggestion.reason()));
    }
    int repeated = 1;
    while (normalized.size() < 3) {
      normalized.add(new Suggestion(fallbackText, "REPEATED_" + repeated, ""));
      repeated++;
    }
    return List.copyOf(normalized);
  }

  private CustomerAnalysis parseCustomerAnalysis(JsonNode node) {
    if (!node.isObject()) {
      return null;
    }
    return new CustomerAnalysis(text(node.path("intent")), text(node.path("emotion")),
        text(node.path("personality_type_suggest")), text(node.path("confidence")));
  }

  private FollowupSuggest parseFollowupSuggest(JsonNode node) {
    if (!node.isObject()) {
      return null;
    }
    return new FollowupSuggest(text(node.path("next_contact_at")), text(node.path("next_contact_direction")));
  }

  private ProfileUpdates parseProfileUpdates(JsonNode node) {
    if (!node.isObject() || !node.path("fields").isObject()) {
      return null;
    }
    Map<String, FieldUpdate> fields = new LinkedHashMap<>();
    node.path("fields").fields().forEachRemaining(entry -> {
      JsonNode update = entry.getValue();
      fields.put(entry.getKey(), new FieldUpdate(objectMapper.convertValue(update.path("value"), Object.class), text(update.path("confidence"))));
    });
    return new ProfileUpdates(fields);
  }

  private String text(JsonNode node) {
    if (node == null || node.isMissingNode() || node.isNull()) {
      return null;
    }
    String value = node.asText();
    return value == null || value.isBlank() || "null".equalsIgnoreCase(value) ? null : value;
  }

  private SkillGatewayException invalid(String message, Throwable cause) {
    return new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, message, true, cause);
  }
}
