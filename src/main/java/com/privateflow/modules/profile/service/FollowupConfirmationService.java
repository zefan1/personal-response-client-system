package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.history.CustomerFieldHistoryContext;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class FollowupConfirmationService {

  private final ProfileWriter profileWriter;

  public FollowupConfirmationService(ProfileWriter profileWriter) {
    this.profileWriter = profileWriter;
  }

  public void record(
      Customer customer,
      String conversationSummary,
      String sentText,
      CustomerMessageSentEvent.FollowupSuggestPayload suggestion,
      boolean completeCurrentFollowup) {
    if (customer == null) {
      return;
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("lastFollowupAt", LocalDateTime.now());
    if (!blank(conversationSummary)) {
      fields.put("followupNotes", conversationSummary);
    }
    if (suggestion != null && !blank(suggestion.nextFollowupAt())) {
      fields.put("nextFollowupAt", suggestion.nextFollowupAt());
      fields.put("nextFollowupDir", suggestion.nextFollowupDir());
    } else if (completeCurrentFollowup) {
      fields.put("nextFollowupAt", null);
      fields.put("nextFollowupDir", null);
    }
    writeCustomer(customer, fields);
  }

  public void recordAnalysis(Customer customer, Map<String, ?> analysisFields, boolean touchFollowupTime) {
    if (customer == null) {
      return;
    }
    Map<String, Object> fields = new LinkedHashMap<>();
    if (analysisFields != null) {
      analysisFields.forEach(fields::put);
    }
    fields.remove("lastFollowupAt");
    if (touchFollowupTime) {
      fields.put("lastFollowupAt", LocalDateTime.now());
    }
    writeCustomer(customer, fields);
  }

  private void writeCustomer(Customer customer, Map<String, Object> fields) {
    if (!blank(customer.getPhone())) {
      profileWriter.write(
          customer.getPhone(),
          fields,
          customer.getVersion(),
          true,
          CustomerFieldHistoryContext.of("跟进流程", "跟进记录", "SYSTEM"));
      return;
    }
    profileWriter.writeByCustomerId(
        customer.getId(),
        fields,
        customer.getVersion(),
        true,
        CustomerFieldHistoryContext.of("跟进流程", "跟进记录", "SYSTEM"));
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
