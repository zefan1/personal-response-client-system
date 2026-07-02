package com.privateflow.modules.followup.service;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.ScanFilter;
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
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class FollowupTodayService {

  private final CustomerQueryService customerQueryService;
  private final ReminderLogRepository reminderLogRepository;

  public FollowupTodayService(CustomerQueryService customerQueryService, ReminderLogRepository reminderLogRepository) {
    this.customerQueryService = customerQueryService;
    this.reminderLogRepository = reminderLogRepository;
  }

  public FollowupTodayResponse today(String keeperId) {
    List<FollowupItem> items = new ArrayList<>();
    for (Customer customer : customerQueryService.scanActiveCustomers(new ScanFilter(null, null, null, true, 5000))) {
      if (keeperId != null && !keeperId.isBlank() && !keeperId.equals(customer.getAssignedKeeper())) {
        continue;
      }
      FollowupItem item = classify(customer);
      if (item != null) {
        items.add(item);
      }
    }
    for (String phone : reminderLogRepository.findTodayPhones(ReminderType.NEW_LEAD)) {
      Customer customer = customerQueryService.getByPhone(phone);
      if (customer != null && (keeperId == null || keeperId.isBlank() || keeperId.equals(customer.getAssignedKeeper()))) {
        items.add(toItem(customer, ReminderType.NEW_LEAD, null, AlertLevel.NORMAL, LocalDateTime.now(), null));
      }
    }
    List<FollowupItem> sorted = items.stream().sorted(this::compare).toList();
    return new FollowupTodayResponse(keeperId, sorted.size(), sorted);
  }

  private FollowupItem classify(Customer customer) {
    LocalDate today = LocalDate.now();
    if (customer.getNextFollowupAt() != null && customer.getNextFollowupAt().isBefore(LocalDateTime.now())) {
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
    return new FollowupItem(
        PhoneUtils.mask(customer.getPhone()),
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
        arrivedAt);
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
    return Comparator.nullsLast(Comparator.<Long>reverseOrder()).compare(left.overdueHours(), right.overdueHours());
  }

  private int weight(ReminderType type) {
    return type == ReminderType.OVERDUE ? 0 : type == ReminderType.NEW_LEAD ? 1 : type == ReminderType.APPOINTMENT ? 2 : 3;
  }

  private int leadWeight(String leadType) {
    return "TUAN_GOU".equals(leadType) ? 0 : 1;
  }
}
