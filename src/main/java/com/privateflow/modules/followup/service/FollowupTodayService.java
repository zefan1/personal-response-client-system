package com.privateflow.modules.followup.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.ScanFilter;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.auth.AuthContext;
import com.privateflow.modules.api.auth.AuthUser;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.followup.AlertLevel;
import com.privateflow.modules.followup.FollowupItem;
import com.privateflow.modules.followup.FollowupTodayResponse;
import com.privateflow.modules.followup.ReminderType;
import com.privateflow.modules.followup.infra.ReminderLogRepository;
import com.privateflow.modules.match.util.PhoneUtils;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class FollowupTodayService {

  private final CustomerQueryService customerQueryService;
  private final ReminderLogRepository reminderLogRepository;
  private final CustomerAccessService customerAccessService;

  public FollowupTodayService(
      CustomerQueryService customerQueryService,
      ReminderLogRepository reminderLogRepository,
      CustomerAccessService customerAccessService) {
    this.customerQueryService = customerQueryService;
    this.reminderLogRepository = reminderLogRepository;
    this.customerAccessService = customerAccessService;
  }

  public FollowupTodayResponse today(String keeperId) {
    String requestedKeeperId = keeperId == null ? "" : keeperId.trim();
    List<FollowupItem> items = new ArrayList<>();
    Set<String> newLeadPhones = new HashSet<>();
    LocalDateTime now = LocalDateTime.now();
    int pendingNewLeadCount = 0;
    int retainedNewLeadCount = 0;
    for (Customer customer : customerQueryService.scanActiveCustomers(new ScanFilter(null, null, null, true, 5000))) {
      if (!canInclude(customer, requestedKeeperId)) {
        continue;
      }
      FollowupItem item = classify(customer);
      if (item != null) {
        items.add(item);
      }
      // Assignment is the authoritative new-lead signal. The reminder log is
      // only a compatibility fallback for rows created before this rule existed.
      if (isNewLeadVisible(customer, requestedKeeperId, now)) {
        boolean processed = isProcessedForKeeper(customer, requestedKeeperId);
        items.add(toItem(customer, ReminderType.NEW_LEAD, null, AlertLevel.NORMAL, customer.getAssignedAt(), null, processed));
        newLeadPhones.add(customer.getPhone());
        if (processed) {
          retainedNewLeadCount++;
        } else {
          pendingNewLeadCount++;
        }
      }
    }
    for (String phone : reminderLogRepository.findTodayPhones(ReminderType.NEW_LEAD)) {
      if (newLeadPhones.contains(phone)) {
        continue;
      }
      Customer customer = customerQueryService.getByPhone(phone);
      if (canInclude(customer, requestedKeeperId) && isNewLeadVisible(customer, requestedKeeperId, now)
          && !newLeadPhones.contains(phone)) {
        boolean processed = isProcessedForKeeper(customer, requestedKeeperId);
        items.add(toItem(customer, ReminderType.NEW_LEAD, null, AlertLevel.NORMAL, customer.getAssignedAt(), null, processed));
        if (processed) {
          retainedNewLeadCount++;
        } else {
          pendingNewLeadCount++;
        }
      }
    }
    List<FollowupItem> sorted = items.stream().sorted(this::compare).toList();
    return new FollowupTodayResponse(effectiveKeeperId(requestedKeeperId), sorted.size(), sorted,
        pendingNewLeadCount, retainedNewLeadCount);
  }

  private boolean isNewLeadVisible(Customer customer, String requestedKeeperId, LocalDateTime now) {
    if (customer == null || customer.getAssignedAt() == null || customer.getAssignedKeeper() == null
        || customer.getAssignedKeeper().isBlank()) {
      return false;
    }
    boolean processed = isProcessedForKeeper(customer, requestedKeeperId);
    return !processed || (customer.getLeadRetainedUntil() != null && customer.getLeadRetainedUntil().isAfter(now));
  }

  private boolean isProcessedForKeeper(Customer customer, String requestedKeeperId) {
    String keeper = requestedKeeperId.isBlank() ? customer.getAssignedKeeper() : requestedKeeperId;
    if (customer.getLeadInitialProcessedAt() != null) {
      return customer.getLeadInitialProcessedBy() == null || customer.getLeadInitialProcessedBy().isBlank()
          || customer.getLeadInitialProcessedBy().equals(keeper)
          || "desktop".equalsIgnoreCase(customer.getLeadInitialProcessedBy());
    }
    return false;
  }

  private boolean canInclude(Customer customer, String requestedKeeperId) {
    if (customer == null || !customerAccessService.canAccess(customer)) {
      return false;
    }
    if (requestedKeeperId.isBlank()) {
      return true;
    }
    return requestedKeeperId.equals(customer.getAssignedKeeper());
  }

  private String effectiveKeeperId(String requestedKeeperId) {
    AuthUser user = AuthContext.current();
    if (user != null && user.role() == Role.KEEPER) {
      return user.username();
    }
    return requestedKeeperId.isBlank() ? null : requestedKeeperId;
  }

  private FollowupItem classify(Customer customer) {
    LocalDate today = LocalDate.now();
    if (customer.getNextFollowupAt() != null && customer.getNextFollowupAt().toLocalDate().isBefore(today)) {
      return toItem(customer, ReminderType.OVERDUE, overdueHours(customer), AlertLevel.HIGH, null, null);
    }
    if (customer.getNextFollowupAt() != null && customer.getNextFollowupAt().toLocalDate().equals(today)) {
      return toItem(customer, ReminderType.DUE_TODAY, null, AlertLevel.NORMAL, null, null);
    }
    if (customer.getAppointmentDate() != null && customer.getAppointmentDate().equals(today) && !"是".equals(customer.getArrived())) {
      return toItem(customer, ReminderType.APPOINTMENT, null, AlertLevel.HIGH, null, null);
    }
    return null;
  }

  private FollowupItem toItem(
      Customer customer,
      ReminderType reminderType,
      Long overdueHours,
      AlertLevel alertLevel,
      LocalDateTime arrivedAt,
      FollowupItem.TagSuggestionPayload tagSuggestion) {
    return toItem(customer, reminderType, overdueHours, alertLevel, arrivedAt, tagSuggestion, false);
  }

  private FollowupItem toItem(
      Customer customer,
      ReminderType reminderType,
      Long overdueHours,
      AlertLevel alertLevel,
      LocalDateTime arrivedAt,
      FollowupItem.TagSuggestionPayload tagSuggestion,
      boolean leadProcessed) {
    return new FollowupItem(
        PhoneUtils.mask(customer.getPhone()),
        customer.getPhone(),
        customer.getNickname(),
        customer.getLeadType(),
        customer.getLastFollowupAt(),
        customer.getNextFollowupAt(),
        customer.getNextFollowupDir(),
        customer.getAppointmentDate(),
        customer.getAppointmentStore(),
        customer.getSourceTable(),
        reminderType,
        overdueHours,
        alertLevel,
        tagSuggestion,
        arrivedAt,
        contactValue(customer),
        isPhone(customer.getPhone()) ? "PHONE" : "WECHAT",
        leadProcessed,
        customer.isLeadInvalid(),
        leadProcessed ? customer.getLeadRetainedUntil() : null,
        customer.getVersion());
  }

  private String contactValue(Customer customer) {
    if (isPhone(customer.getPhone())) {
      return customer.getPhone();
    }
    if (customer.getWechatId() != null && !customer.getWechatId().isBlank()) {
      return customer.getWechatId();
    }
    return customer.getPhone();
  }

  private boolean isPhone(String value) {
    return value != null && value.matches("\\d{11}");
  }

  private Long overdueHours(Customer customer) {
    if (customer.getLastFollowupAt() == null) {
      return null;
    }
    return Duration.between(customer.getLastFollowupAt(), LocalDateTime.now()).toHours();
  }

  private int compare(FollowupItem left, FollowupItem right) {
    int type = Integer.compare(weight(left.reminderType()), weight(right.reminderType()));
    if (type != 0) {
      return type;
    }
    int lead = Integer.compare(leadWeight(left.leadType()), leadWeight(right.leadType()));
    if (lead != 0) {
      return lead;
    }
    if (left.reminderType() == ReminderType.NEW_LEAD && right.reminderType() == ReminderType.NEW_LEAD) {
      int processed = Boolean.compare(left.leadProcessed(), right.leadProcessed());
      if (processed != 0) {
        return processed;
      }
    }
    return Comparator.nullsLast(Comparator.<Long>reverseOrder()).compare(left.overdueHours(), right.overdueHours());
  }

  private int weight(ReminderType type) {
    return type == ReminderType.DUE_TODAY ? 0 : type == ReminderType.OVERDUE ? 1 : type == ReminderType.APPOINTMENT ? 2 : 3;
  }

  private int leadWeight(String leadType) {
    return "TUAN_GOU".equals(leadType) ? 0 : 1;
  }
}
