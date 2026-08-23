package com.privateflow.modules.profile.infra;

import com.privateflow.modules.customer.Customer;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProfileFieldRegistry {

  private static final Map<String, FieldSpec> FIELDS = new LinkedHashMap<>();

  static {
    register("nickname", "nickname", String.class);
    register("customerName", "customer_name", String.class);
    register("sourceChannel", "source_channel", String.class);
    register("leadType", "lead_type", String.class);
    register("wechatId", "wechat_id", String.class);
    register("leadCaptureMethod", "lead_capture_method", String.class);
    register("platformLeadAt", "platform_lead_at", LocalDateTime.class);
    register("advertisingType", "advertising_type", String.class);
    register("globalAdvertisementId", "global_advertisement_id", String.class);
    register("standardAdvertisementId", "standard_advertisement_id", String.class);
    register("contentId", "content_id", String.class);
    register("videoId", "video_id", String.class);
    register("orderNumber", "order_number", String.class);
    register("conversionTrace", "conversion_trace", String.class);
    register("previousAssignedKeeper", "previous_assigned_keeper", String.class);
    register("previousPlatformLeadAt", "previous_platform_lead_at", LocalDateTime.class);
    register("assignedKeeper", "assigned_keeper", String.class);
    register("assignedAt", "assigned_at", LocalDateTime.class);
    register("leadInitialProcessedAt", "lead_initial_processed_at", LocalDateTime.class);
    register("leadInitialProcessedBy", "lead_initial_processed_by", String.class);
    register("leadRetainedUntil", "lead_retained_until", LocalDateTime.class);
    register("leadInvalid", "lead_invalid", Boolean.class);
    register("assignmentMonth", "assignment_month", String.class);
    register("intendedStore", "intended_store", String.class);
    register("intendedProject", "intended_project", String.class);
    register("purchasedProject", "purchased_project", String.class);
    register("experienceCardType", "experience_card_type", String.class);
    register("pendingOrderStatus", "pending_order_status", String.class);
    register("purchaseDate", "purchase_date", LocalDate.class);
    register("customerLevel", "customer_level", String.class);
    register("personalityType", "personality_type", String.class);
    register("postpartumMonths", "postpartum_months", BigDecimal.class);
    register("parity", "parity", String.class);
    register("deliveryMethod", "delivery_method", String.class);
    register("breastfeeding", "breastfeeding", String.class);
    register("lochiaPeriod", "lochia_period", String.class);
    register("bodyConcerns", "body_concerns", String.class);
    register("diastasisRecti", "diastasis_recti", String.class);
    register("urineLeakage", "urine_leakage", String.class);
    register("pubicLumbago", "pubic_lumbago", String.class);
    register("prevRepairExp", "prev_repair_exp", String.class);
    register("postpartumCheck", "postpartum_check", String.class);
    register("exerciseHabits", "exercise_habits", String.class);
    register("intentLevel", "intent_level", String.class);
    register("customerStage", "customer_stage", String.class);
    register("internalNote", "internal_note", String.class);
    register("customerProfileSummary", "customer_profile_summary", String.class);
    register("firstTrackingCapture", "first_tracking_capture", String.class);
    register("secondTrackingCapture", "second_tracking_capture", String.class);
    register("thirdTrackingCapture", "third_tracking_capture", String.class);
    register("lastFollowupAt", "last_followup_at", LocalDateTime.class);
    register("followupNotes", "followup_notes", String.class);
    register("nextFollowupAt", "next_followup_at", LocalDateTime.class);
    register("nextFollowupDir", "next_followup_dir", String.class);
    register("appointmentDate", "appointment_date", LocalDate.class);
    register("appointmentStore", "appointment_store", String.class);
    register("appointmentItem", "appointment_item", String.class);
    register("arrived", "arrived", String.class);
    register("appointmentStatus", "appointment_status", String.class);
    register("appointmentTime", "appointment_time", String.class);
    register("arrivalSourceRowId", "arrival_source_row_id", String.class);
    register("arrivalHandoverRecord", "arrival_handover_record", String.class);
    register("arrivalProjectType", "arrival_project_type", String.class);
    register("arrivalExperienceProject", "arrival_experience_project", String.class);
    register("historicalExperienceCount", "historical_experience_count", String.class);
    register("customerReport", "customer_report", String.class);
    register("receptionTeacher", "reception_teacher", String.class);
    register("receptionConsultant", "reception_consultant", String.class);
    register("voucherRedeemed", "voucher_redeemed", String.class);
    register("transactionAmount", "transaction_amount", BigDecimal.class);
    register("transactionAt", "transaction_at", LocalDateTime.class);
    register("transactionPrimaryReason", "transaction_primary_reason", String.class);
  }

  public Set<String> supportedFields() {
    return FIELDS.keySet();
  }

  public boolean supports(String fieldName) {
    return FIELDS.containsKey(fieldName);
  }

  public FieldSpec spec(String fieldName) {
    return FIELDS.get(fieldName);
  }

  public Object normalizeValue(String fieldName, Object value) {
    FieldSpec spec = spec(fieldName);
    if (spec == null || value == null) {
      return null;
    }
    if (spec.type() == BigDecimal.class) {
      return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }
    if (spec.type() == LocalDateTime.class) {
      if (value instanceof LocalDateTime time) {
        return Timestamp.valueOf(time);
      }
      return Timestamp.valueOf(String.valueOf(value).replace("T", " "));
    }
    if (spec.type() == LocalDate.class) {
      if (value instanceof LocalDate date) {
        return Date.valueOf(date);
      }
      return Date.valueOf(String.valueOf(value).substring(0, 10));
    }
    if (spec.type() == Boolean.class) {
      return value instanceof Boolean bool ? bool : Boolean.parseBoolean(String.valueOf(value));
    }
    String text = String.valueOf(value);
    return text.length() > 500 ? text.substring(0, 500) : text;
  }

  public Object readValue(Customer customer, String fieldName) {
    if (customer == null || !supports(fieldName)) {
      return null;
    }
    String methodName = "get" + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    try {
      Method method = Customer.class.getMethod(methodName);
      return method.invoke(customer);
    } catch (ReflectiveOperationException ex) {
      return null;
    }
  }

  public Map<String, Object> toProfileMap(Customer customer) {
    Map<String, Object> profile = new LinkedHashMap<>();
    if (customer == null) {
      return profile;
    }
    profile.put("phone", customer.getPhone());
    profile.put("nickname", customer.getNickname());
    profile.put("sourceChannel", customer.getSourceChannel());
    profile.put("leadType", customer.getLeadType());
    profile.put("assignedKeeper", customer.getAssignedKeeper());
    profile.put("intendedStore", customer.getIntendedStore());
    profile.put("intendedProject", customer.getIntendedProject());
    profile.put("purchasedProject", customer.getPurchasedProject());
    for (String field : FIELDS.keySet()) {
      profile.put(field, readValue(customer, field));
    }
    profile.put("version", customer.getVersion());
    return profile;
  }

  private static void register(String fieldName, String columnName, Class<?> type) {
    FIELDS.put(fieldName, new FieldSpec(fieldName, columnName, type));
  }

  public record FieldSpec(String fieldName, String columnName, Class<?> type) {
  }
}
