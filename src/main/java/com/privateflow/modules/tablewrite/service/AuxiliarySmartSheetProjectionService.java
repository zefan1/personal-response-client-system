package com.privateflow.modules.tablewrite.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.tablewrite.PendingWritePayload;
import com.privateflow.modules.tablewrite.TableWriteActionType;
import com.privateflow.modules.tablewrite.client.AuxiliarySmartSheetWriter;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AuxiliarySmartSheetProjectionService {

  private static final Logger log = LoggerFactory.getLogger(AuxiliarySmartSheetProjectionService.class);
  private final AuxiliarySmartSheetWriter writer;
  private final WriteQueueManager queueManager;
  private final AuxiliarySmartSheetTargets targets;
  private final Optional<AuxiliarySmartSheetTarget> assignmentFallback;
  private final Optional<AuxiliarySmartSheetTarget> arrivalFallback;
  private final Duration timeout;
  private final TableFieldMappingResolver mappingResolver;
  private final AuditLogger auditLogger;
  private final ObjectMapper objectMapper;

  @Autowired
  public AuxiliarySmartSheetProjectionService(
      AuxiliarySmartSheetWriter writer,
      WriteQueueManager queueManager,
      AuxiliarySmartSheetTargets targets,
      TableFieldMappingResolver mappingResolver,
      AuditLogger auditLogger,
      ObjectMapper objectMapper) {
    this.writer = writer;
    this.queueManager = queueManager;
    this.targets = targets;
    this.assignmentFallback = Optional.empty();
    this.arrivalFallback = Optional.empty();
    this.timeout = Duration.ofSeconds(5);
    this.mappingResolver = mappingResolver;
    this.auditLogger = auditLogger;
    this.objectMapper = objectMapper;
  }

  AuxiliarySmartSheetProjectionService(
      AuxiliarySmartSheetWriter writer,
      WriteQueueManager queueManager,
      Optional<AuxiliarySmartSheetTarget> assignment,
      Optional<AuxiliarySmartSheetTarget> arrival,
      Duration timeout) {
    this.writer = writer;
    this.queueManager = queueManager;
    this.targets = null;
    this.assignmentFallback = assignment;
    this.arrivalFallback = arrival;
    this.timeout = timeout;
    this.mappingResolver = null;
    this.auditLogger = null;
    this.objectMapper = new ObjectMapper();
  }

  public void project(Customer customer) {
    if (customer == null || blank(customer.getPhone())) {
      return;
    }
    Optional<AuxiliarySmartSheetTarget> arrival = arrivalTarget();
    if (arrival.isPresent() && hasArrivalData(customer)) {
      write(arrival.get(), customer, arrivalFields(customer));
    }
  }

  private Optional<AuxiliarySmartSheetTarget> assignmentTarget() {
    return targets == null ? assignmentFallback : targets.assignment();
  }

  private Optional<AuxiliarySmartSheetTarget> arrivalTarget() {
    return targets == null ? arrivalFallback : targets.arrival();
  }

  private void write(AuxiliarySmartSheetTarget target, Customer customer, Map<String, Object> fields) {
    try {
      String sourceTable = target.role() + ":" + target.sheetId();
      Map<String, Object> mapped = mappedFields(target, fields);
      String identityField = mappingResolver == null
          ? target.uniqueFieldTitle() : mappingResolver.sourceFieldFor(sourceTable, "phone");
      writer.upsert(target, mapped, identityField, timeout);
      audit(customer, target, mapped, "SUCCESS", null);
    } catch (RuntimeException failure) {
      try {
        queueManager.enqueue(customer.getId(), customer.getPhone(), TableWriteActionType.UPDATE,
            new PendingWritePayload(target.role(), null, fields,
                target.documentId(), target.sheetId(), target.viewId()), message(failure));
        audit(customer, target, fields, "QUEUED", message(failure));
      } catch (RuntimeException queueFailure) {
        audit(customer, target, fields, "FAILED", message(queueFailure));
        log.warn("辅助表写入失败且无法排队, role={}, reason={}", target.role(), message(queueFailure));
      }
    }
  }

  private void audit(Customer customer, AuxiliarySmartSheetTarget target,
      Map<String, Object> fields, String status, String error) {
    if (auditLogger == null) {
      return;
    }
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("status", status);
    detail.put("role", target.role());
    detail.put("documentId", target.documentId());
    detail.put("sheetId", target.sheetId());
    detail.put("fields", fields == null ? Map.of() : fields.keySet());
    if (error != null) {
      detail.put("error", error);
    }
    try {
      auditLogger.log("SAVE_TO_TABLE", "SYSTEM", "customer",
          customer.getPhone(), objectMapper.writeValueAsString(detail));
    } catch (JsonProcessingException ex) {
      auditLogger.log("SAVE_TO_TABLE", "SYSTEM", "customer", customer.getPhone(),
          "role=" + target.role() + ", status=" + status);
    }
  }

  private static Map<String, Object> arrivalFields(Customer customer) {
    Map<String, Object> fields = new LinkedHashMap<>();
    put(fields, "phone", customer.getPhone());
    put(fields, "nickname", customer.getNickname());
    put(fields, "appointmentDate", customer.getAppointmentDate());
    put(fields, "appointmentStore", customer.getAppointmentStore());
    put(fields, "appointmentItem", customer.getAppointmentItem());
    put(fields, "arrived", customer.getArrived());
    put(fields, "assignedKeeper", customer.getAssignedKeeper());
    return fields;
  }

  private static Map<String, Object> commonFields(Customer customer) {
    Map<String, Object> fields = new LinkedHashMap<>();
    put(fields, "phone", customer.getPhone());
    put(fields, "nickname", customer.getNickname());
    return fields;
  }

  private static void put(Map<String, Object> fields, String title, Object value) {
    if (value != null) fields.put(title, String.valueOf(value));
  }

  private Map<String, Object> mappedFields(AuxiliarySmartSheetTarget target, Map<String, Object> fields) {
    if (mappingResolver == null) return fields;
    return mappingResolver.toSourceFields(target.role() + ":" + target.sheetId(), fields);
  }

  private static boolean hasArrivalData(Customer c) {
    return c.getAppointmentDate() != null || !blank(c.getAppointmentStore())
        || !blank(c.getAppointmentItem()) || !blank(c.getArrived());
  }

  private static boolean blank(String value) { return value == null || value.isBlank(); }

  private static String message(RuntimeException ex) {
    return blank(ex.getMessage()) ? "辅助表写入失败" : ex.getMessage();
  }
}
