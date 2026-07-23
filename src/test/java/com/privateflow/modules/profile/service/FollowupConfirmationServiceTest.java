package com.privateflow.modules.profile.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.profile.infra.ProfileWriter;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FollowupConfirmationServiceTest {

  @Test
  void recordsConfirmedFollowupTimeAndSummary() {
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    FollowupConfirmationService service = new FollowupConfirmationService(profileWriter);
    Customer customer = customer();

    service.record(customer, "发送模板《到店提醒》：明天见", "明天见", null, false);

    Map<String, Object> fields = capturedFields(profileWriter, customer);
    assertThat(fields.get("lastFollowupAt")).isInstanceOf(LocalDateTime.class);
    assertThat(fields).containsEntry("followupNotes", "发送模板《到店提醒》：明天见");
    assertThat(fields).doesNotContainKeys("nextFollowupAt", "nextFollowupDir");
  }

  @Test
  void clearsCurrentFollowupWhenCompletionIsExplicitAndThereIsNoNewSuggestion() {
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    FollowupConfirmationService service = new FollowupConfirmationService(profileWriter);
    Customer customer = customer();

    service.record(customer, "", "已发送模板", null, true);

    Map<String, Object> fields = capturedFields(profileWriter, customer);
    assertThat(fields).containsEntry("followupNotes", "已发送模板");
    assertThat(fields).containsEntry("nextFollowupAt", null);
    assertThat(fields).containsEntry("nextFollowupDir", null);
  }

  @Test
  void replacesCurrentFollowupWithTheNewSuggestionWhenOneExists() {
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    FollowupConfirmationService service = new FollowupConfirmationService(profileWriter);
    Customer customer = customer();
    CustomerMessageSentEvent.FollowupSuggestPayload suggestion =
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-07-22T10:00:00", "再次确认到店");

    service.record(customer, "已确认明天再联系", "明天联系", suggestion, true);

    Map<String, Object> fields = capturedFields(profileWriter, customer);
    assertThat(fields).containsEntry("nextFollowupAt", "2026-07-22T10:00:00");
    assertThat(fields).containsEntry("nextFollowupDir", "再次确认到店");
  }

  private Map<String, Object> capturedFields(ProfileWriter profileWriter, Customer customer) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
    verify(profileWriter).write(eq(customer.getPhone()), fields.capture(), eq(customer.getVersion()), eq(true));
    return fields.getValue();
  }

  private Customer customer() {
    Customer customer = new Customer();
    customer.setPhone("18800001111");
    customer.setVersion(7);
    return customer;
  }
}
