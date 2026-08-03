package com.privateflow.modules.profile.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.llm.FollowupAnalysisPayload;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FollowupAnalysisFieldMerger {

  public Map<String, Object> merge(Customer customer, FollowupAnalysisPayload payload) {
    Map<String, Object> fields = new LinkedHashMap<>();
    if (payload == null) {
      return fields;
    }
    putIfPresent(fields, "internalNote", payload.internalNote());
    putIfPresent(fields, "bodyConcerns", payload.bodyConcerns());
    putIfPresent(fields, "customerProfileSummary", payload.customerProfileSummary());
    putIfPresent(fields, "customerStage", payload.customerStage());
    putIfPresent(fields, "nextFollowupDir", payload.nextFollowupDirection());
    putIfPresent(fields, "nextFollowupAt", payload.nextFollowupAt());
    if (!blank(payload.followupRecord())) {
      fields.put("followupNotes", appendUnique(customer == null ? null : customer.getFollowupNotes(), payload.followupRecord()));
    }
    addTrackingCapture(fields, customer, payload.trackingCapture());
    return fields;
  }

  private void addTrackingCapture(Map<String, Object> fields, Customer customer, String capture) {
    if (blank(capture) || alreadyCaptured(customer, capture)) {
      return;
    }
    if (customer == null || blank(customer.getFirstTrackingCapture())) {
      fields.put("firstTrackingCapture", capture.trim());
    } else if (blank(customer.getSecondTrackingCapture())) {
      fields.put("secondTrackingCapture", capture.trim());
    } else if (blank(customer.getThirdTrackingCapture())) {
      fields.put("thirdTrackingCapture", capture.trim());
    }
  }

  private boolean alreadyCaptured(Customer customer, String capture) {
    if (customer == null) {
      return false;
    }
    String value = capture.trim();
    return value.equals(trim(customer.getFirstTrackingCapture()))
        || value.equals(trim(customer.getSecondTrackingCapture()))
        || value.equals(trim(customer.getThirdTrackingCapture()));
  }

  private String appendUnique(String existing, String addition) {
    String next = addition.trim();
    if (blank(existing)) {
      return next;
    }
    String current = existing.trim();
    for (String line : current.split("\\R")) {
      if (next.equals(line.trim())) {
        return current;
      }
    }
    return current + "\n" + next;
  }

  private void putIfPresent(Map<String, Object> fields, String field, String value) {
    if (!blank(value)) {
      fields.put(field, value.trim());
    }
  }

  private String trim(String value) {
    return value == null ? null : value.trim();
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
