package com.privateflow.modules.customer.admin;

public record CustomerMasterFieldValue(
    String fieldName,
    String label,
    Object value,
    String source,
    String sourceField) {

  public CustomerMasterFieldValue(String fieldName, String label, Object value) {
    this(fieldName, label, value, "", "");
  }
}
