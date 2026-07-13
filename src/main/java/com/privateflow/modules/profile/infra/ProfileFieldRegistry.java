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
    register("sourceChannel", "source_channel", String.class);
    register("intendedStore", "intended_store", String.class);
    register("intendedProject", "intended_project", String.class);
    register("purchasedProject", "purchased_project", String.class);
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
    register("worries", "worries", String.class);
    register("customerStage", "customer_stage", String.class);
    register("lastFollowupAt", "last_followup_at", LocalDateTime.class);
    register("followupNotes", "followup_notes", String.class);
    register("nextFollowupAt", "next_followup_at", LocalDateTime.class);
    register("nextFollowupDir", "next_followup_dir", String.class);
    register("appointmentDate", "appointment_date", LocalDate.class);
    register("appointmentStore", "appointment_store", String.class);
    register("appointmentItem", "appointment_item", String.class);
    register("arrived", "arrived", String.class);
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
