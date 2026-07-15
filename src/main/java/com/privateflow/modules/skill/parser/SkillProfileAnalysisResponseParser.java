package com.privateflow.modules.skill.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.skill.FieldUpdate;
import com.privateflow.modules.skill.ProfileAnalysisResult;
import com.privateflow.modules.skill.ProfileUpdates;
import com.privateflow.modules.skill.SkillErrorCodes;
import com.privateflow.modules.skill.SkillGatewayException;
import com.privateflow.modules.skill.TagAnalysisAction;
import com.privateflow.modules.skill.TagAnalysisDecision;
import com.privateflow.modules.skill.TagAnalysisResultType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SkillProfileAnalysisResponseParser {

  private final ObjectMapper objectMapper;

  public SkillProfileAnalysisResponseParser(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public ProfileAnalysisResult parse(String raw) {
    try {
      JsonNode root = objectMapper.readTree(raw);
      JsonNode profileUpdates = root.path("profile_updates");
      if (!profileUpdates.isObject()) {
        throw invalid("Skill 档案分析缺少 profile_updates", null);
      }
      JsonNode fieldsNode = profileUpdates.get("fields");
      if (fieldsNode == null || !fieldsNode.isObject()) {
        throw invalid("profile_updates.fields 必须是对象", null);
      }
      JsonNode decisionsNode = profileUpdates.get("tag_decisions");
      if (decisionsNode == null || !decisionsNode.isArray()) {
        throw invalid("Skill 档案分析缺少 tag_decisions 数组", null);
      }
      return new ProfileAnalysisResult(
          new ProfileUpdates(parseFields(fieldsNode)),
          parseDecisions(decisionsNode));
    } catch (SkillGatewayException ex) {
      throw ex;
    } catch (Exception ex) {
      throw invalid("Skill 档案分析返回格式异常", ex);
    }
  }

  private Map<String, FieldUpdate> parseFields(JsonNode fieldsNode) {
    if (fieldsNode == null || fieldsNode.isNull() || fieldsNode.isMissingNode()) {
      return Map.of();
    }
    if (!fieldsNode.isObject()) {
      throw invalid("profile_updates.fields 必须是对象", null);
    }
    Map<String, FieldUpdate> fields = new LinkedHashMap<>();
    fieldsNode.fields().forEachRemaining(entry -> {
      JsonNode update = entry.getValue();
      if (!update.isObject()) {
        throw invalid("档案字段更新格式异常：" + entry.getKey(), null);
      }
      fields.put(entry.getKey(), new FieldUpdate(
          objectMapper.convertValue(update.path("value"), Object.class),
          text(update.get("confidence"))));
    });
    return fields;
  }

  private List<TagAnalysisDecision> parseDecisions(JsonNode decisionsNode) {
    List<TagAnalysisDecision> decisions = new ArrayList<>();
    for (JsonNode node : decisionsNode) {
      if (!node.isObject()) {
        throw invalid("tag_decisions 项必须是对象", null);
      }
      String categoryCode = requiredText(node, "category_code");
      JsonNode tagCodesNode = node.get("tag_codes");
      if (tagCodesNode == null || !tagCodesNode.isArray()) {
        throw invalid("tag_codes 必须是数组", null);
      }
      List<String> tagCodes = new ArrayList<>();
      for (JsonNode codeNode : tagCodesNode) {
        if (!codeNode.isTextual() || codeNode.asText().isBlank()) {
          throw invalid("tag_codes 只能包含非空标签编码", null);
        }
        tagCodes.add(codeNode.asText().trim());
      }
      JsonNode confidenceNode = node.get("confidence");
      if (confidenceNode == null || !confidenceNode.isNumber()) {
        throw invalid("confidence 必须是数值", null);
      }
      decisions.add(new TagAnalysisDecision(
          categoryCode,
          tagCodes,
          confidenceNode.decimalValue(),
          requiredText(node, "evidence"),
          enumValue(TagAnalysisResultType.class, requiredText(node, "result_type"), "result_type"),
          enumValue(TagAnalysisAction.class, requiredText(node, "requested_action"), "requested_action")));
    }
    return List.copyOf(decisions);
  }

  private String requiredText(JsonNode node, String field) {
    String value = text(node.get(field));
    if (value == null) {
      throw invalid(field + " 必填", null);
    }
    return value;
  }

  private String text(JsonNode node) {
    if (node == null || !node.isTextual() || node.asText().isBlank()) {
      return null;
    }
    return node.asText().trim();
  }

  private <T extends Enum<T>> T enumValue(Class<T> type, String value, String field) {
    try {
      return Enum.valueOf(type, value);
    } catch (IllegalArgumentException ex) {
      throw invalid(field + " 参数非法：" + value, ex);
    }
  }

  private SkillGatewayException invalid(String message, Throwable cause) {
    return new SkillGatewayException(SkillErrorCodes.SKILL_RESPONSE_INVALID, message, true, cause);
  }
}
