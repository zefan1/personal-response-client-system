package com.privateflow.modules.tablewrite.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.customer.CustomerQueryService;
import com.privateflow.modules.customer.service.CustomerAccessService;
import com.privateflow.modules.tags.TagExchangeResult;
import com.privateflow.modules.tags.TagExchangeService;
import com.privateflow.modules.tags.TagExchangeSourceType;
import com.privateflow.modules.tablewrite.ManualSaveRequest;
import com.privateflow.modules.tablewrite.ManualSaveResult;
import com.privateflow.modules.tablewrite.TableWriteErrorCodes;
import com.privateflow.modules.tablewrite.TableWriteException;
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
  private Customer customer;
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
    customer = new Customer();
    customer.setPhone("13800000000");
    customer.setSourceTable(new String("table_a"));
    customer.setSourceRowId(new String("row-1"));
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
  void manualSaveRejectsTamperedSourceRowIdBeforePreparingOrWritingFields() {
    ManualSaveRequest request = new ManualSaveRequest(
        "table_a", "row-other", Map.of("ordinary", "keep"));

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save("13800000000", request));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("source reference is invalid")
        .doesNotContain("row-other", "row-1", "table_a");
    verifyNoInteractions(mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveRejectsTamperedSourceTableBeforePreparingOrWritingFields() {
    ManualSaveRequest request = new ManualSaveRequest(
        "table_other", "row-1", Map.of("ordinary", "keep"));

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save("13800000000", request));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("source reference is invalid")
        .doesNotContain("table_other", "table_a", "row-1");
    verifyNoInteractions(mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveRejectsCustomerWithMissingSourceTable() {
    customer.setSourceTable("   ");

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save(
            "13800000000",
            new ManualSaveRequest("table_a", "row-1", Map.of("ordinary", "keep"))));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("source reference is invalid")
        .doesNotContain("table_a", "row-1");
    verifyNoInteractions(mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveRejectsCustomerWithMissingSourceRowId() {
    customer.setSourceRowId(null);

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save(
            "13800000000",
            new ManualSaveRequest("table_a", "row-1", Map.of("ordinary", "keep"))));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("source reference is invalid")
        .doesNotContain("table_a", "row-1");
    verifyNoInteractions(mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveUsesAuthorizedCustomerSourceForEntireWritePath() {
    Map<String, Object> requestFields = Map.of("tag_column", "漏尿,未知", "ordinary", "keep");
    Map<String, Object> internalFields = Map.of("bodyConcerns", "漏尿", "ordinary", "keep");
    when(mappingResolver.toInternalFields(same(customer.getSourceTable()), same(requestFields)))
        .thenReturn(internalFields);
    TagExchangeResult exchange = new TagExchangeResult(
        Map.of("bodyConcerns", "URINE_LEAKAGE", "ordinary", "keep"),
        List.of("bodyConcerns"),
        List.of());
    when(exchangeService.prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), same(customer.getSourceRowId()), same(internalFields)))
        .thenReturn(exchange);
    Map<String, Object> mergedFields = Map.of(
        "tag_column", "URINE_LEAKAGE", "ordinary", "keep");
    when(mappingResolver.mergeSourceFields(
        same(customer.getSourceTable()),
        same(requestFields),
        same(exchange.acceptedFields()),
        same(exchange.filteredFields())))
        .thenReturn(mergedFields);

    ManualSaveResult result = handler.save(
        "13800000000",
        new ManualSaveRequest("table_a", "row-1", requestFields));

    verify(mappingResolver).toInternalFields(same(customer.getSourceTable()), same(requestFields));
    verify(exchangeService).prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), same(customer.getSourceRowId()), same(internalFields));
    verify(mappingResolver).mergeSourceFields(
        same(customer.getSourceTable()),
        same(requestFields),
        same(exchange.acceptedFields()),
        same(exchange.filteredFields()));
    verify(tableClient).updateRow(
        same(customer.getSourceTable()),
        same(customer.getSourceRowId()),
        same(mergedFields),
        eq(Duration.ofMillis(5000)));
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
        eq(TagExchangeSourceType.TABLE_WRITE), eq("row-1"), any(Map.class)))
        .thenReturn(new TagExchangeResult(Map.of(), List.of("bodyConcerns"), List.of()));
    when(mappingResolver.mergeSourceFields("table_a", requestFields, Map.of(), List.of("bodyConcerns")))
        .thenReturn(Map.of());

    ManualSaveResult result = handler.save(
        "13800000000",
        new ManualSaveRequest("table_a", "row-1", requestFields));

    verify(tableClient, never()).updateRow(any(), any(), any(), any());
    assertThat(result.written()).isFalse();
    assertThat(result.updatedFields()).isEmpty();
    assertThat(result.filteredFields()).containsExactly("bodyConcerns");
  }
}
