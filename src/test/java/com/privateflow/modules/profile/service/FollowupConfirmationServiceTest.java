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
    assertThat(fields).doesNotContainKey("followupNotes");
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

  @Test
  void recordsTheSharedStructuredAnalysisWhileKeepingRecentFollowupTimeLocal() {
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    FollowupConfirmationService service = new FollowupConfirmationService(profileWriter);
    Customer customer = customer();

    service.recordAnalysis(customer, Map.of(
        "internalNote", "先说明评估流程",
        "customerProfileSummary", "产后6个月，关注腹直肌",
        "followupNotes", "2026-08-01：约定周一联系",
        "secondTrackingCapture", "周一上午可联系"), true);

    Map<String, Object> fields = capturedFields(profileWriter, customer);
    assertThat(fields.get("lastFollowupAt")).isInstanceOf(LocalDateTime.class);
    assertThat(fields)
        .containsEntry("internalNote", "先说明评估流程")
        .containsEntry("customerProfileSummary", "产后6个月，关注腹直肌")
        .containsEntry("followupNotes", "2026-08-01：约定周一联系")
        .containsEntry("secondTrackingCapture", "周一上午可联系");
  }

  @Test
  void recordsStructuredAnalysisForPhoneLessRecognitionCustomerByCustomerId() {
    ProfileWriter profileWriter = mock(ProfileWriter.class);
    FollowupConfirmationService service = new FollowupConfirmationService(profileWriter);
    Customer customer = new Customer();
    customer.setId(42L);
    customer.setVersion(3);

    service.recordAnalysis(customer, Map.of("followupNotes", "首次咨询后已发送回复"), true);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
    verify(profileWriter).writeByCustomerId(eq(42L), fields.capture(), eq(3), eq(true));
    assertThat(fields.getValue()).containsEntry("followupNotes", "首次咨询后已发送回复");
    assertThat(fields.getValue().get("lastFollowupAt")).isInstanceOf(LocalDateTime.class);
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
