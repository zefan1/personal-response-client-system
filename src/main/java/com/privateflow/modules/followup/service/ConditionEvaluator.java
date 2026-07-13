package com.privateflow.modules.followup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.followup.FollowupErrorCodes;
import com.privateflow.modules.followup.FollowupException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ConditionEvaluator {

  private static final Set<String> FIELD_WHITELIST = Set.of(
      "leadType", "intentLevel", "customerStage", "lastFollowupHours",
      "noMessageDays", "messageKeywords", "appointmentDate", "sourceTable");
  private static final Set<String> OP_WHITELIST = Set.of("EQ", "NEQ", "GT", "LT", "GTE", "LTE", "CONTAINS", "BETWEEN");
  private final ObjectMapper objectMapper;

  public ConditionEvaluator(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public boolean matches(Customer customer, String conditionJson) {
    try {
      JsonNode root = objectMapper.readTree(conditionJson);
      return evalNode(customer, root, 0);
    } catch (FollowupException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件解析失败", ex);
    }
  }

  public void validateDefinition(JsonNode root) {
    validateNode(root, 0);
  }

  private boolean evalNode(Customer customer, JsonNode node, int depth) {
    if (depth > 2) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "条件组合过于复杂，请拆分为多条规则");
    }
    String operator = text(node.path("operator"));
    JsonNode conditions = node.path("conditions");
    JsonNode orGroups = node.path("orGroups");
    if (orGroups.isArray()) {
      boolean mainMatched = conditions.isArray() && evalAndGroup(customer, conditions, depth);
      if (mainMatched) {
        return true;
      }
      for (JsonNode group : orGroups) {
        JsonNode groupConditions = group.path("conditions");
        if (groupConditions.isArray() && evalAndGroup(customer, groupConditions, depth + 1)) {
          return true;
        }
      }
      return false;
    }
    if (!conditions.isArray()) {
      return evalLeaf(customer, node);
    }
    if ("OR".equalsIgnoreCase(operator)) {
      for (JsonNode child : conditions) {
        if (evalNode(customer, child, depth + 1)) {
          return true;
        }
      }
      return false;
    }
    if (!operator.isBlank() && !"AND".equalsIgnoreCase(operator)) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则组合操作符不合法");
    }
    return evalAndGroup(customer, conditions, depth);
  }

  private boolean evalAndGroup(Customer customer, JsonNode conditions, int depth) {
    for (JsonNode child : conditions) {
      if (!evalNode(customer, child, depth + 1)) {
        return false;
      }
    }
    return true;
  }

  private void validateNode(JsonNode node, int depth) {
    if (node == null || !node.isObject()) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则条件必须是对象格式");
    }
    if (depth > 2) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "条件组合过于复杂，请拆分为多条规则");
    }
    JsonNode conditions = node.path("conditions");
    JsonNode orGroups = node.path("orGroups");
    if (conditions.isArray()) {
      String operator = text(node.path("operator"));
      if (!operator.isBlank() && !"AND".equalsIgnoreCase(operator) && !"OR".equalsIgnoreCase(operator)) {
        throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则组合操作符不合法");
      }
      for (JsonNode child : conditions) {
        validateNode(child, depth + 1);
      }
      if (orGroups.isArray()) {
        for (JsonNode group : orGroups) {
          validateNode(group, depth + 1);
        }
      }
      return;
    }
    validateLeaf(node);
  }

  private void validateLeaf(JsonNode node) {
    String field = text(node.path("field"));
    String op = text(node.path("op"));
    if (!FIELD_WHITELIST.contains(field) || !OP_WHITELIST.contains(op)) {
      throw new FollowupException(FollowupErrorCodes.CONDITION_PARSE_FAILED, "规则字段或操作符不合法");
    }
  }

  private boolean evalLeaf(Customer customer, JsonNode node) {
    String field = text(node.path("field"));
    String op = text(node.path("op"));
    validateLeaf(node);
    Object actual = value(customer, field);
    JsonNode expected = node.path("value");
    return switch (op) {
      case "EQ" -> string(actual).equalsIgnoreCase(expected.asText(""));
      case "NEQ" -> !string(actual).equalsIgnoreCase(expected.asText(""));
      case "GT" -> number(actual) > expected.asDouble();
      case "LT" -> number(actual) < expected.asDouble();
      case "GTE" -> number(actual) >= expected.asDouble();
      case "LTE" -> number(actual) <= expected.asDouble();
      case "CONTAINS" -> containsAny(string(actual), expected.asText(""));
      case "BETWEEN" -> appointmentBetween(customer.getAppointmentDate(), expected);
      default -> false;
    };
  }

  private Object value(Customer customer, String field) {
    return switch (field) {
      case "leadType" -> customer.getLeadType();
      case "intentLevel" -> customer.getIntentLevel();
      case "customerStage" -> customer.getCustomerStage();
      case "lastFollowupHours", "noMessageDays" -> hoursSince(customer.getLastFollowupAt());
      case "messageKeywords" -> customer.getFollowupNotes();
      case "appointmentDate" -> customer.getAppointmentDate();
      case "sourceTable" -> customer.getSourceTable();
      default -> null;
    };
  }

  private long hoursSince(LocalDateTime time) {
    if (time == null) {
      return Long.MAX_VALUE / 24;
    }
    return Duration.between(time, LocalDateTime.now()).toHours();
  }

  private boolean containsAny(String actual, String keywords) {
    for (String keyword : keywords.split(",")) {
      if (!keyword.isBlank() && actual.contains(keyword.trim())) {
        return true;
      }
    }
    return false;
  }

  private boolean appointmentBetween(LocalDate appointmentDate, JsonNode expected) {
    if (appointmentDate == null || !expected.isArray() || expected.size() < 2) {
      return false;
    }
    LocalDate today = LocalDate.now();
    return !appointmentDate.isBefore(today) && !appointmentDate.isAfter(today.plusDays(1));
  }

  private String text(JsonNode node) {
    return node == null || node.isMissingNode() || node.isNull() ? "" : node.asText("");
  }

  private String string(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private double number(Object value) {
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    try {
      return Double.parseDouble(string(value));
    } catch (NumberFormatException ex) {
      return 0d;
    }
  }
}
