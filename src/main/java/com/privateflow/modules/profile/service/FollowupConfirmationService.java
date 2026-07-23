package com.privateflow.modules.profile.service;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
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
    fields.put("followupNotes", firstNonBlank(conversationSummary, sentText));
    if (suggestion != null && !blank(suggestion.nextFollowupAt())) {
      fields.put("nextFollowupAt", suggestion.nextFollowupAt());
      fields.put("nextFollowupDir", suggestion.nextFollowupDir());
    } else if (completeCurrentFollowup) {
      fields.put("nextFollowupAt", null);
      fields.put("nextFollowupDir", null);
    }
    profileWriter.write(customer.getPhone(), fields, customer.getVersion(), true);
  }

  private String firstNonBlank(String first, String second) {
    return blank(first) ? (second == null ? "" : second) : first;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
