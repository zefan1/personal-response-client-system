package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.common.events.CustomerMessageSentEvent;
import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExistingCustomerUpdaterTest {

  @Test
  void followupFieldsIncludeConfirmedSummaryAndFollowupTime() {
    ExistingCustomerUpdater updater = new ExistingCustomerUpdater(
        mock(WecomTableClient.class),
        mock(TableConfigProvider.class),
        mock(TableFieldMappingResolver.class));

    Map<String, Object> fields = updater.followupFields(event(
        "发送模板《到店提醒》：明天见",
        null,
        false));

    assertThat(fields).containsEntry("followupNotes", "发送模板《到店提醒》：明天见");
    assertThat(fields.get("lastFollowupAt")).isInstanceOf(String.class);
    assertThat(fields).doesNotContainKeys("nextFollowupAt", "nextFollowupDir");
  }

  @Test
  void followupFieldsExplicitlyClearTheCompletedTaskWithoutANewSuggestion() {
    ExistingCustomerUpdater updater = new ExistingCustomerUpdater(
        mock(WecomTableClient.class),
        mock(TableConfigProvider.class),
        mock(TableFieldMappingResolver.class));

    Map<String, Object> fields = updater.followupFields(event("已完成今日跟进", null, true));

    assertThat(fields).containsEntry("nextFollowupAt", "");
    assertThat(fields).containsEntry("nextFollowupDir", "");
  }

  @Test
  void followupFieldsUseANewSuggestionInsteadOfCompletionClearMarkers() {
    ExistingCustomerUpdater updater = new ExistingCustomerUpdater(
        mock(WecomTableClient.class),
        mock(TableConfigProvider.class),
        mock(TableFieldMappingResolver.class));
    CustomerMessageSentEvent.FollowupSuggestPayload suggestion =
        new CustomerMessageSentEvent.FollowupSuggestPayload("2026-07-22T10:00:00", "再次确认到店");

    Map<String, Object> fields = updater.followupFields(event("已安排下次跟进", suggestion, true));

    assertThat(fields).containsEntry("nextFollowupAt", "2026-07-22T10:00:00");
    assertThat(fields).containsEntry("nextFollowupDir", "再次确认到店");
  }

  @Test
  void filteredOutboundFieldsDoNotCallRemoteClient() {
    WecomTableClient client = mock(WecomTableClient.class);
    TableConfigProvider config = mock(TableConfigProvider.class);
    TableFieldMappingResolver mapping = mock(TableFieldMappingResolver.class);
    TagExchangeService exchangeService = mock(TagExchangeService.class);
    when(config.get()).thenReturn(new TableConfig("", "", 5000, 3, 30, 1, "ADMIN", 50, 500));
    when(exchangeService.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), eq("row-1"), any(Map.class)))
        .thenReturn(new TagExchangeResult(Map.of(), List.of("bodyConcerns"), List.of()));
    when(mapping.toSourceFields("table_a", Map.of())).thenReturn(Map.of());
    ExistingCustomerUpdater updater = new ExistingCustomerUpdater(client, config, mapping, exchangeService);
    Customer customer = new Customer();
    customer.setSourceTable("table_a");
    customer.setSourceRowId("row-1");

    updater.update(customer, event());

    verify(client, never()).updateRow(any(), any(), any(), any(Duration.class));
  }

  private CustomerMessageSentEvent event() {
    return event("summary", null, false);
  }

  private CustomerMessageSentEvent event(
      String summary,
      CustomerMessageSentEvent.FollowupSuggestPayload suggestion,
      boolean completeCurrentFollowup) {
    return new CustomerMessageSentEvent(
        "13800000000",
        "Alice",
        false,
        "table_a",
        "LEAD",
        summary,
        List.of(),
        summary,
        "NEXT",
        suggestion,
        completeCurrentFollowup,
        "keeper");
  }
}
