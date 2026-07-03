package com.privateflow.modules.customer;

public final class LeadTypes {
  public static final String TUAN_GOU = "TUAN_GOU";
  public static final String XIAN_SUO = "XIAN_SUO";
  public static final String PENDING = "PENDING";

  private LeadTypes() {
  }

  public static String normalize(String raw) {
    if (raw == null || raw.isBlank()) {
      return null;
    }
    return switch (raw.trim()) {
      case TUAN_GOU, XIAN_SUO, PENDING -> raw.trim();
      case "团购客资", "团购", "体验券" -> TUAN_GOU;
      case "线索客资", "线索" -> XIAN_SUO;
      case "待确认" -> PENDING;
      default -> PENDING;
    };
  }
}
