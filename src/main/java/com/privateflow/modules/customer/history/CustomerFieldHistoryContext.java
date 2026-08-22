package com.privateflow.modules.customer.history;

import java.util.LinkedHashMap;
import java.util.Map;

/** Describes the business origin of a customer-field write. */
public record CustomerFieldHistoryContext(
    String source,
    String sourceFieldPrefix,
    Map<String, String> exactSourceFields,
    String operator) {

  public CustomerFieldHistoryContext {
    source = blank(source) ? "系统档案更新" : source;
    sourceFieldPrefix = blank(sourceFieldPrefix) ? "客户档案字段" : sourceFieldPrefix;
    exactSourceFields = exactSourceFields == null
        ? Map.of()
        : Map.copyOf(new LinkedHashMap<>(exactSourceFields));
    operator = blank(operator) ? "SYSTEM" : operator;
  }

  public static CustomerFieldHistoryContext system() {
    return new CustomerFieldHistoryContext("系统档案更新", "客户档案字段", Map.of(), "SYSTEM");
  }

  public static CustomerFieldHistoryContext of(String source, String sourceFieldPrefix, String operator) {
    return new CustomerFieldHistoryContext(source, sourceFieldPrefix, Map.of(), operator);
  }

  public static CustomerFieldHistoryContext external(
      String source, Map<String, String> sourceFields, String operator) {
    return new CustomerFieldHistoryContext(source, "外部数据字段", sourceFields, operator);
  }

  public String sourceField(String fieldName) {
    String exact = exactSourceFields.get(fieldName);
    return blank(exact) ? sourceFieldPrefix + " · " + fieldName : exact;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
