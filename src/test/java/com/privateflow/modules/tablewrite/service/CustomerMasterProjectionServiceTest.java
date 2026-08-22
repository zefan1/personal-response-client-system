package com.privateflow.modules.tablewrite.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.customer.Customer;
import com.privateflow.modules.tablewrite.client.WecomTableClient;
import com.privateflow.modules.tablewrite.config.TableConfig;
import com.privateflow.modules.tablewrite.config.TableConfigProvider;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import com.privateflow.modules.tablewrite.infra.TableFieldMappingResolver;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerMasterProjectionServiceTest {

  @Test
  void keepsProjectingWhenPrimarySheetRejectsOptionalFields() {
    WecomTableClient tableClient = mock(WecomTableClient.class);
    TableConfigProvider configProvider = mock(TableConfigProvider.class);
    TableFieldMappingResolver mappings = mock(TableFieldMappingResolver.class);
    WecomSmartSheetConfig smartSheet = new WecomSmartSheetConfig(
        "https://qyapi.weixin.qq.com", "corp", "secret", "document", "primary", "view",
        "PRIMARY", "联系方式");
    CustomerMasterProjectionService service = new CustomerMasterProjectionService(
        tableClient, smartSheet, configProvider, mappings);
    Customer customer = new Customer();
    customer.setPhone("13800000000");
    customer.setAssignedAt(LocalDateTime.of(2026, 8, 16, 10, 30));
    customer.setIntendedStore("不在主表选项中的门店");
    customer.setIntendedProject("不在主表选项中的项目");

    when(configProvider.get()).thenReturn(new TableConfig(
        "", "", 10_000, 5, 60, 1, "ADMIN", 100, 1_000));
    when(mappings.sourceFieldFor("PRIMARY", "phone")).thenReturn("联系方式");
    when(mappings.toSourceFields(eq("PRIMARY"), any())).thenAnswer(invocation -> new LinkedHashMap<>(Map.of(
        "联系方式", "13800000000",
        "分配日期", LocalDateTime.of(2026, 8, 16, 10, 30),
        "意向门店", "不在主表选项中的门店",
        "意向项目", "不在主表选项中的项目")));
    when(tableClient.createRow(eq("PRIMARY"), any(), any()))
        .thenThrow(new IllegalArgumentException("Invalid value for field: 意向项目"))
        .thenThrow(new IllegalArgumentException("Invalid value for field: 意向门店"))
        .thenThrow(new IllegalArgumentException("Invalid value for field: 分配日期"))
        .thenReturn("primary-row");

    service.projectAssignment(customer);

    verify(tableClient, times(4)).createRow(eq("PRIMARY"), any(), any());
    verify(tableClient).updateRow(eq("PRIMARY"), eq("primary-row"), any(), any());
  }
}
