package com.privateflow.modules.tablewrite.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.sync.SheetRow;
import com.privateflow.modules.customer.sync.SheetSource;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HttpWecomTableClientTest {

  private final WecomSmartSheetRecordClient recordClient = mock(WecomSmartSheetRecordClient.class);
  private final TableConfigProvider configProvider = mock(TableConfigProvider.class);
  private final HttpWecomTableClient client = new HttpWecomTableClient(recordClient, configProvider);

  @Test
  void fetchesIncrementalRowsThroughOfficialClientUsingConfiguredWriteTimeout() {
    SheetSource source = new SheetSource(7L, "doc-1", "Customers");
    LocalDateTime modifiedAfter = LocalDateTime.of(2026, 7, 24, 9, 30);
    List<SheetRow> rows = List.of(new SheetRow("record-1", Map.of("Name", "Alice")));
    when(configProvider.get()).thenReturn(config(4321));
    when(recordClient.fetchIncrementalRows(source, modifiedAfter, 25, Duration.ofMillis(4321)))
        .thenReturn(rows);

    List<SheetRow> result = client.fetchIncrementalRows(source, modifiedAfter, 25);

    assertThat(result).isSameAs(rows);
    verify(configProvider).get();
    verify(recordClient).fetchIncrementalRows(source, modifiedAfter, 25, Duration.ofMillis(4321));
    verifyNoMoreInteractions(recordClient, configProvider);
  }

  @Test
  void createsRowThroughOfficialClientWithoutGatewayConfiguration() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("Phone", "13800000000");
    Duration timeout = Duration.ofSeconds(6);
    when(recordClient.createRow(eq("Customers"), same(fields), eq(timeout))).thenReturn("record-created");

    String rowId = client.createRow("Customers", fields, timeout);

    assertThat(rowId).isEqualTo("record-created");
    verify(recordClient).createRow(eq("Customers"), same(fields), eq(timeout));
    verifyNoMoreInteractions(recordClient);
    verifyNoInteractions(configProvider);
  }

  @Test
  void updatesRowThroughOfficialClientWithoutGatewayConfiguration() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("Name", "Updated");
    Duration timeout = Duration.ofSeconds(7);

    client.updateRow("Customers", "record-7", fields, timeout);

    verify(recordClient).updateRow(eq("Customers"), eq("record-7"), same(fields), eq(timeout));
    verifyNoMoreInteractions(recordClient);
    verifyNoInteractions(configProvider);
  }

  private static TableConfig config(int writeTimeoutMs) {
    return new TableConfig("", "", writeTimeoutMs, 5, 60, 1, "ADMIN", 100, 1000);
  }
}
