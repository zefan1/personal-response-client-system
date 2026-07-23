package com.privateflow.modules.followup.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.followup.FollowupTodayResponse;
import com.privateflow.modules.followup.ReminderType;
import com.privateflow.modules.followup.infra.ReminderLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FollowupTodayServiceTest {

  @Test
  void keepsEarlierTimesTodayDueTodayAndSortsThemBeforePreviousDayOverdueItems() {
    LocalDateTime now = LocalDateTime.now();
    Customer dueToday = customer("18800002222", "今日待跟进客户", now.toLocalDate().atStartOfDay());
    Customer overdue = customer("18800001111", "逾期跟进客户", now.minusDays(1));

    CustomerQueryService queryService = mock(CustomerQueryService.class);
    ReminderLogRepository reminderLogs = mock(ReminderLogRepository.class);
    CustomerAccessService accessService = mock(CustomerAccessService.class);
    when(queryService.scanActiveCustomers(any())).thenReturn(List.of(overdue, dueToday));
    when(reminderLogs.findTodayPhones(ReminderType.NEW_LEAD)).thenReturn(List.of());
    when(accessService.canAccess(any())).thenReturn(true);

    FollowupTodayResponse response = new FollowupTodayService(queryService, reminderLogs, accessService).today("");

    assertEquals(List.of("今日待跟进客户", "逾期跟进客户"),
        response.items().stream().map(item -> item.nickname()).toList());
    assertEquals(List.of(ReminderType.DUE_TODAY, ReminderType.OVERDUE),
        response.items().stream().map(item -> item.reminderType()).toList());
  }

  private Customer customer(String phone, String nickname, LocalDateTime nextFollowupAt) {
    Customer customer = new Customer();
    customer.setPhone(phone);
    customer.setNickname(nickname);
    customer.setLeadType("XIAN_SUO");
    customer.setAssignedKeeper("keeper-a");
    customer.setLastFollowupAt(nextFollowupAt.minusDays(1));
    customer.setNextFollowupAt(nextFollowupAt);
    customer.setNextFollowupDir("电话回访");
    return customer;
  }
}
