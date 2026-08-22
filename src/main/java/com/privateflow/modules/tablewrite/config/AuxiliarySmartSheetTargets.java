package com.privateflow.modules.tablewrite.config;

import com.privateflow.modules.customer.infra.SystemConfigRepository;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuxiliarySmartSheetTargets {

  private final AuxiliarySmartSheetTarget assignmentFallback;
  private final AuxiliarySmartSheetTarget arrivalFallback;
  private final SystemConfigRepository runtimeConfigRepository;

  @Autowired
  public AuxiliarySmartSheetTargets(
      @Value("${wecom.assignment-smartsheet.doc-id:}") String assignmentDocumentId,
      @Value("${wecom.assignment-smartsheet.sheet-id:}") String assignmentSheetId,
      @Value("${wecom.assignment-smartsheet.view-id:}") String assignmentViewId,
      @Value("${wecom.assignment-smartsheet.document-url:}") String assignmentDocumentUrl,
      @Value("${wecom.arrival-smartsheet.doc-id:}") String arrivalDocumentId,
      @Value("${wecom.arrival-smartsheet.sheet-id:}") String arrivalSheetId,
      @Value("${wecom.arrival-smartsheet.view-id:}") String arrivalViewId,
      @Value("${wecom.arrival-smartsheet.document-url:}") String arrivalDocumentUrl,
      SystemConfigRepository runtimeConfigRepository) {
    assignmentFallback = new AuxiliarySmartSheetTarget(
        "ASSIGNMENT", assignmentDocumentId, assignmentSheetId, assignmentViewId,
        "联系方式", assignmentDocumentUrl);
    arrivalFallback = new AuxiliarySmartSheetTarget(
        "ARRIVAL", arrivalDocumentId, arrivalSheetId, arrivalViewId,
        "手机号码", arrivalDocumentUrl);
    this.runtimeConfigRepository = runtimeConfigRepository;
  }

  public AuxiliarySmartSheetTargets() {
    this("", "", "", "", "", "", "", "", null);
  }

  public AuxiliarySmartSheetTargets(
      String assignmentDocumentId,
      String assignmentSheetId,
      String assignmentViewId,
      String assignmentDocumentUrl,
      String arrivalDocumentId,
      String arrivalSheetId,
      String arrivalViewId,
      String arrivalDocumentUrl) {
    this(assignmentDocumentId, assignmentSheetId, assignmentViewId, assignmentDocumentUrl,
        arrivalDocumentId, arrivalSheetId, arrivalViewId, arrivalDocumentUrl, null);
  }

  public Optional<AuxiliarySmartSheetTarget> assignment() {
    AuxiliarySmartSheetTarget target = current("assignment", assignmentFallback);
    return target.configured() ? Optional.of(target) : Optional.empty();
  }

  public Optional<AuxiliarySmartSheetTarget> arrival() {
    AuxiliarySmartSheetTarget target = current("arrival", arrivalFallback);
    return target.configured() ? Optional.of(target) : Optional.empty();
  }

  public Optional<AuxiliarySmartSheetTarget> forRole(String role) {
    return switch (role == null ? "" : role.trim().toUpperCase()) {
      case "ASSIGNMENT" -> assignment();
      case "ARRIVAL" -> arrival();
      default -> Optional.empty();
    };
  }

  private AuxiliarySmartSheetTarget current(String key, AuxiliarySmartSheetTarget fallback) {
    if (runtimeConfigRepository == null) {
      return fallback;
    }
    return new AuxiliarySmartSheetTarget(
        fallback.role(),
        value("table." + key + ".document_id", fallback.documentId()),
        value("table." + key + ".sheet_id", fallback.sheetId()),
        value("table." + key + ".view_id", fallback.viewId()),
        value("table." + key + ".unique_field_title", fallback.uniqueFieldTitle()),
        value("table." + key + "_document_url", fallback.documentUrl()));
  }

  private String value(String key, String fallback) {
    return runtimeConfigRepository.findValue(key)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .orElse(fallback);
  }
}
