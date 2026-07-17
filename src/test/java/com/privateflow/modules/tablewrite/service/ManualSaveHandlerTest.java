package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tablewrite.ManualSaveRequest;
import com.privateflow.modules.tablewrite.ManualSaveResult;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualSaveHandlerTest {

  private WecomTableClient tableClient;
  private TableConfigProvider configProvider;
  private CustomerQueryService customerQueryService;
  private CustomerAccessService accessService;
  private TableFieldMappingResolver mappingResolver;
  private TagExchangeService exchangeService;
  private ManualSaveHandler handler;

  @BeforeEach
  void setUp() {
    tableClient = mock(WecomTableClient.class);
    configProvider = mock(TableConfigProvider.class);
    customerQueryService = mock(CustomerQueryService.class);
    accessService = mock(CustomerAccessService.class);
    mappingResolver = mock(TableFieldMappingResolver.class);
    exchangeService = mock(TagExchangeService.class);
    when(configProvider.get()).thenReturn(new TableConfig("", "", 5000, 3, 30, 1, "ADMIN", 50, 500));
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    when(customerQueryService.getByPhone("13800000000")).thenReturn(customer);
    when(accessService.canAccess(customer)).thenReturn(true);
    handler = new ManualSaveHandler(
        tableClient,
        configProvider,
        customerQueryService,
        accessService,
        mappingResolver,
        exchangeService);
  }

  @Test
  void manualSaveWritesOrdinaryFieldsAndAcceptedTagsButDropsUnknownTag() {
    Map<String, Object> requestFields = Map.of("tag_column", "漏尿,未知", "ordinary", "keep");
    when(mappingResolver.toInternalFields("table_a", requestFields))
        .thenReturn(Map.of("bodyConcerns", "漏尿", "ordinary", "keep"));
    TagExchangeResult exchange = new TagExchangeResult(
        Map.of("bodyConcerns", "URINE_LEAKAGE", "ordinary", "keep"),
        List.of("bodyConcerns"),
        List.of());
    when(exchangeService.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), eq("row-1"), any(Map.class)))
        .thenReturn(exchange);
    when(mappingResolver.mergeSourceFields(
        "table_a", requestFields, exchange.acceptedFields(), exchange.filteredFields()))
        .thenReturn(Map.of("tag_column", "URINE_LEAKAGE", "ordinary", "keep"));

    ManualSaveResult result = handler.save(
        "13800000000",
        new ManualSaveRequest("table_a", "row-1", requestFields));

    verify(tableClient).updateRow(
        eq("table_a"),
        eq("row-1"),
        eq(Map.of("tag_column", "URINE_LEAKAGE", "ordinary", "keep")),
        any(Duration.class));
    assertThat(result.written()).isTrue();
    assertThat(result.updatedFields()).containsExactlyInAnyOrder("tag_column", "ordinary");
    assertThat(result.filteredFields()).containsExactly("bodyConcerns");
  }

  @Test
  void manualSaveWithOnlyUnknownTagDoesNotCallRemoteClient() {
    Map<String, Object> requestFields = Map.of("tag_column", "未知");
    when(mappingResolver.toInternalFields("table_a", requestFields))
        .thenReturn(Map.of("bodyConcerns", "未知"));
    when(exchangeService.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), eq("row-2"), any(Map.class)))
        .thenReturn(new TagExchangeResult(Map.of(), List.of("bodyConcerns"), List.of()));
    when(mappingResolver.mergeSourceFields("table_a", requestFields, Map.of(), List.of("bodyConcerns")))
        .thenReturn(Map.of());

    ManualSaveResult result = handler.save(
        "13800000000",
        new ManualSaveRequest("table_a", "row-2", requestFields));

    verify(tableClient, never()).updateRow(any(), any(), any(), any());
    assertThat(result.written()).isFalse();
    assertThat(result.updatedFields()).isEmpty();
    assertThat(result.filteredFields()).containsExactly("bodyConcerns");
  }
}
