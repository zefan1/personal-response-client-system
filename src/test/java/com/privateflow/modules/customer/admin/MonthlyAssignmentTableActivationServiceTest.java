package com.privateflow.modules.customer.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetProvisioningService;
import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.annotation.Transactional;

class MonthlyAssignmentTableActivationServiceTest {

  @Test
  void updatesConfigurationMappingAndHistoryInsideOneTransactionalOperation() throws Exception {
    MonthlyAssignmentTableRepository repository = mock(MonthlyAssignmentTableRepository.class);
    ConfigAdminService config = mock(ConfigAdminService.class);
    DatasourceAdminRepository datasource = mock(DatasourceAdminRepository.class);
    MonthlyAssignmentTableActivationService service =
        new MonthlyAssignmentTableActivationService(repository, config, datasource);
    WecomSmartSheetProvisioningService.ProvisionedSheet created =
        new WecomSmartSheetProvisioningService.ProvisionedSheet(
            "doc-9", "https://doc.weixin.qq.com/9", "sheet-9", "view-9", "sheet-9", "联系方式");

    service.activate(9L, created);

    InOrder ordered = inOrder(config, datasource, repository);
    ordered.verify(config).updateAll(Map.of(
        "table.assignment.document_id", "doc-9",
        "table.assignment.sheet_id", "sheet-9",
        "table.assignment.view_id", "view-9",
        "table.assignment.unique_field_title", "联系方式",
        "table.assignment_document_url", "https://doc.weixin.qq.com/9"));
    ordered.verify(datasource).ensureManagedSmartSheetDatasource("ASSIGNMENT", "doc-9", "sheet-9");
    ordered.verify(repository).activate(9L);

    Method method = MonthlyAssignmentTableActivationService.class.getMethod(
        "activate", long.class, WecomSmartSheetProvisioningService.ProvisionedSheet.class);
    assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
  }
}
