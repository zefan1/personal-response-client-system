package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.audit.AuditLogger;
import com.privateflow.modules.customer.infra.CustomerRepository;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetFieldCatalog;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerStageOptionServiceTest {

  private DatasourceAdminRepository datasourceRepository;
  private CustomerStageOptionRepository optionRepository;
  private CustomerStageOptionService service;

  @BeforeEach
  void setUp() {
    datasourceRepository = mock(DatasourceAdminRepository.class);
    optionRepository = mock(CustomerStageOptionRepository.class);
    service = new CustomerStageOptionService(
        datasourceRepository,
        optionRepository,
        mock(CustomerRepository.class),
        mock(WecomSmartSheetFieldCatalog.class),
        null,
        mock(AuxiliarySmartSheetTargets.class),
        mock(AuditLogger.class));
  }

  @Test
  void rejectsStageOptionsForAssignmentDatasource() {
    Datasource assignment = datasource(45L, "ASSIGNMENT:q979lj", "SYSTEM_MANAGED_SMART_SHEET:ASSIGNMENT");
    when(datasourceRepository.find(45L)).thenReturn(Optional.of(assignment));

    assertThatThrownBy(() -> service.current(45L))
        .isInstanceOf(ApiException.class)
        .hasMessage("客户阶段选项只适用于客户主表，请先选择客户主表");
  }

  @Test
  void readsStageOptionsForPrimaryDatasourceWhenMappingExists() {
    Datasource primary = datasource(14L, "th1zyU", "SYSTEM_MANAGED_SMART_SHEET:PRIMARY");
    when(datasourceRepository.find(14L)).thenReturn(Optional.of(primary));
    when(datasourceRepository.mappings("th1zyU")).thenReturn(List.of(
        new FieldMappingDto(1L, "客户阶段", "customerStage", true)));
    when(optionRepository.list("th1zyU", "客户阶段")).thenReturn(List.of(
        Map.of("optionText", "跟进中", "status", "ACTIVE")));

    Map<String, Object> result = service.current(14L);

    assertThat(result).containsEntry("fieldName", "客户阶段");
    assertThat(result.get("options")).asList().hasSize(1);
  }

  private Datasource datasource(long id, String sourceTable, String description) {
    return new Datasource(
        id,
        description.endsWith("PRIMARY") ? "客户主表" : "分配表",
        "sheet-" + id,
        sourceTable,
        description,
        true,
        1,
        null,
        "OK",
        "admin",
        null,
        null);
  }
}
