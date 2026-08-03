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
import static org.mockito.Mockito.verifyNoMoreInteractions;
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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;

class ManualSaveHandlerTest {

  private WecomTableClient tableClient;
  private TableConfigProvider configProvider;
  private CustomerQueryService customerQueryService;
  private CustomerAccessService accessService;
  private TableFieldMappingResolver mappingResolver;
  private TagExchangeService exchangeService;
  private Customer customer;
  private ManualSaveHandler handler;

  private enum FailureStage {
    MAPPING,
    EXCHANGE,
    TABLE_CLIENT
  }

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
  void manualSaveRejectsMissingCustomerBeforeAccessOrDownstreamCalls() {
    when(customerQueryService.getByPhone("13800000000")).thenReturn(null);

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save(
            "13800000000",
            new ManualSaveRequest("table_a", "row-1", Map.of("ordinary", "keep"))));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("客户不存在")
        .doesNotContain("table_a", "row-1");
    verify(customerQueryService).getByPhone("13800000000");
    verifyNoMoreInteractions(customerQueryService);
    verifyNoInteractions(accessService, mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveRejectsInaccessibleCustomerBeforeDownstreamCalls() {
    when(accessService.canAccess(customer)).thenReturn(false);

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save(
            "13800000000",
            new ManualSaveRequest("table_a", "row-1", Map.of("ordinary", "keep"))));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("该客户不在你的负责范围内")
        .doesNotContain("table_a", "row-1");
    verify(customerQueryService).getByPhone("13800000000");
    verify(accessService).canAccess(customer);
    verifyNoMoreInteractions(customerQueryService, accessService);
    verifyNoInteractions(mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveAcceptsAsciiPaddingButUsesTrimmedServerCoordinates() {
    customer.setSourceTable("  table_a  ");
    customer.setSourceRowId("  row-1  ");
    Map<String, Object> requestFields = Map.of("ordinary", "keep");
    when(mappingResolver.toInternalFields("table_a", requestFields)).thenReturn(requestFields);
    TagExchangeResult exchange = new TagExchangeResult(requestFields, List.of(), List.of());
    when(exchangeService.prepareOutbound(
        TagExchangeSourceType.TABLE_WRITE, "row-1", requestFields)).thenReturn(exchange);
    when(mappingResolver.mergeSourceFields(
        "table_a", requestFields, exchange.acceptedFields(), exchange.filteredFields()))
        .thenReturn(requestFields);

    ManualSaveResult result = handler.save(
        "13800000000",
        new ManualSaveRequest(" table_a ", " row-1 ", requestFields));

    verify(mappingResolver).toInternalFields("table_a", requestFields);
    verify(exchangeService).prepareOutbound(
        TagExchangeSourceType.TABLE_WRITE, "row-1", requestFields);
    verify(mappingResolver).mergeSourceFields(
        "table_a", requestFields, exchange.acceptedFields(), exchange.filteredFields());
    verify(configProvider).get();
    verify(tableClient).updateRow(
        "table_a", "row-1", requestFields, Duration.ofMillis(5000));
    verifyNoMoreInteractions(mappingResolver, exchangeService, configProvider, tableClient);
    assertThat(result.written()).isTrue();
  }

  @Test
  void manualSaveRejectsCaseChangedCoordinates() {
    ManualSaveRequest request = new ManualSaveRequest(
        "TABLE_A", "ROW-1", Map.of("ordinary", "keep"));

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save("13800000000", request));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("source reference is invalid")
        .doesNotContain("TABLE_A", "ROW-1", "table_a", "row-1");
    verifyNoInteractions(mappingResolver, exchangeService, configProvider, tableClient);
  }

  @Test
  void manualSaveRejectsCoordinatesPaddedWithNonBreakingSpace() {
    String paddedTable = "table_a\u00a0";
    String paddedRowId = "row-1\u00a0";
    ManualSaveRequest request = new ManualSaveRequest(
        paddedTable, paddedRowId, Map.of("ordinary", "keep"));

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save("13800000000", request));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.BAD_REQUEST);
    assertThat(error.getMessage())
        .isEqualTo("source reference is invalid")
        .doesNotContain(paddedTable, paddedRowId, "table_a", "row-1");
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

  @ParameterizedTest
  @EnumSource(FailureStage.class)
  void manualSaveSanitizesDownstreamFailures(FailureStage failureStage) {
    String serverTable = "server-table-secret";
    String serverRowId = "server-row-secret";
    String privateFieldValue = "private-field-value";
    String originalFailure = "original downstream exception text";
    customer.setSourceTable(serverTable);
    customer.setSourceRowId(serverRowId);
    Map<String, Object> requestFields = Map.of("ordinary", privateFieldValue);
    Map<String, Object> internalFields = Map.of("ordinary", privateFieldValue);
    RuntimeException downstreamFailure = new RuntimeException(
        serverTable + " " + serverRowId + " " + privateFieldValue + " " + originalFailure);
    when(mappingResolver.toInternalFields(serverTable, requestFields)).thenReturn(internalFields);
    TagExchangeResult exchange = new TagExchangeResult(internalFields, List.of(), List.of());
    when(exchangeService.prepareOutbound(
        TagExchangeSourceType.TABLE_WRITE, serverRowId, internalFields)).thenReturn(exchange);
    when(mappingResolver.mergeSourceFields(
        serverTable, requestFields, exchange.acceptedFields(), exchange.filteredFields()))
        .thenReturn(requestFields);
    switch (failureStage) {
      case MAPPING -> when(mappingResolver.toInternalFields(serverTable, requestFields))
          .thenThrow(downstreamFailure);
      case EXCHANGE -> when(exchangeService.prepareOutbound(
          TagExchangeSourceType.TABLE_WRITE, serverRowId, internalFields))
          .thenThrow(downstreamFailure);
      case TABLE_CLIENT -> doThrow(downstreamFailure).when(tableClient).updateRow(
          serverTable, serverRowId, requestFields, Duration.ofMillis(5000));
    }

    TableWriteException error = assertThrows(
        TableWriteException.class,
        () -> handler.save(
            "13800000000",
            new ManualSaveRequest(serverTable, serverRowId, requestFields)));

    assertThat(error.getErrorCode()).isEqualTo(TableWriteErrorCodes.TABLE_WRITE_FAILED);
    assertThat(error.getMessage())
        .isEqualTo("table write failed")
        .doesNotContain(serverTable, serverRowId, privateFieldValue, originalFailure);
    assertThat(error.getCause()).isNull();
    assertThat(error.getSuppressed()).isEmpty();
  }

  @Test
  void manualSaveDiagnosticLogDoesNotExposeDownstreamExceptionDetails() {
    String privateValue = "private-customer-value";
    Map<String, Object> requestFields = Map.of("ordinary", privateValue);
    when(mappingResolver.toInternalFields("table_a", requestFields)).thenReturn(requestFields);
    TagExchangeResult exchange = new TagExchangeResult(requestFields, List.of(), List.of());
    when(exchangeService.prepareOutbound(
        TagExchangeSourceType.TABLE_WRITE, "row-1", requestFields)).thenReturn(exchange);
    when(mappingResolver.mergeSourceFields(
        "table_a", requestFields, exchange.acceptedFields(), exchange.filteredFields()))
        .thenReturn(requestFields);
    doThrow(new RuntimeException("row-1 " + privateValue)).when(tableClient).updateRow(
        "table_a", "row-1", requestFields, Duration.ofMillis(5000));
    ch.qos.logback.classic.Logger logger =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ManualSaveHandler.class);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      assertThrows(
          TableWriteException.class,
          () -> handler.save(
              "13800000000",
              new ManualSaveRequest("table_a", "row-1", requestFields)));
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);
    assertThat(event.getFormattedMessage())
        .contains("failureType=RuntimeException")
        .doesNotContain("row-1", privateValue);
    assertThat(event.getThrowableProxy()).isNull();
  }

  @Test
  void manualSaveUsesAuthorizedCustomerSourceForEntireWritePath() {
    String requestTable = new String("table_a");
    String requestRowId = new String("row-1");
    assertThat(requestTable).isNotSameAs(customer.getSourceTable());
    assertThat(requestRowId).isNotSameAs(customer.getSourceRowId());
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
        new ManualSaveRequest(requestTable, requestRowId, requestFields));

    verify(mappingResolver).toInternalFields(same(customer.getSourceTable()), same(requestFields));
    verify(exchangeService).prepareOutbound(
        eq(TagExchangeSourceType.TABLE_WRITE), same(customer.getSourceRowId()), same(internalFields));
    verify(mappingResolver).mergeSourceFields(
        same(customer.getSourceTable()),
        same(requestFields),
        same(exchange.acceptedFields()),
        same(exchange.filteredFields()));
    verify(configProvider).get();
    verify(tableClient).updateRow(
        same(customer.getSourceTable()),
        same(customer.getSourceRowId()),
        same(mergedFields),
        eq(Duration.ofMillis(5000)));
    verifyNoMoreInteractions(mappingResolver, exchangeService, configProvider, tableClient);
    assertThat(result.written()).isTrue();
    assertThat(result.updatedFields()).containsExactlyInAnyOrder("tag_column", "ordinary");
    assertThat(result.filteredFields()).containsExactly("bodyConcerns");
  }

  @Test
  void fourArgumentHandlerWritesOriginalFieldsWithoutMappingOrExchangeServices() {
    ManualSaveHandler compatibleHandler = new ManualSaveHandler(
        tableClient,
        configProvider,
        customerQueryService,
        accessService);
    Map<String, Object> requestFields = Map.of("ordinary", "keep");

    ManualSaveResult result = compatibleHandler.save(
        "13800000000",
        new ManualSaveRequest("table_a", "row-1", requestFields));

    verify(configProvider).get();
    verify(tableClient).updateRow(
        same(customer.getSourceTable()),
        same(customer.getSourceRowId()),
        same(requestFields),
        eq(Duration.ofMillis(5000)));
    verifyNoMoreInteractions(configProvider, tableClient);
    verifyNoInteractions(mappingResolver, exchangeService);
    assertThat(result.written()).isTrue();
    assertThat(result.updatedFields()).containsExactly("ordinary");
    assertThat(result.filteredFields()).isEmpty();
    assertThat(result.unmatchedCount()).isZero();
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
