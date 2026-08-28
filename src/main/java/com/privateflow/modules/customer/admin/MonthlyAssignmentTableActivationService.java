package com.privateflow.modules.customer.admin;

import com.privateflow.modules.api.config.ConfigAdminService;
import com.privateflow.modules.tablewrite.client.WecomSmartSheetProvisioningService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Atomically activates a provisioned assignment table in local MariaDB state. */
@Service
public class MonthlyAssignmentTableActivationService {

  private final MonthlyAssignmentTableRepository repository;
  private final ConfigAdminService configAdminService;
  private final DatasourceAdminRepository datasourceRepository;

  public MonthlyAssignmentTableActivationService(
      MonthlyAssignmentTableRepository repository,
      ConfigAdminService configAdminService,
      DatasourceAdminRepository datasourceRepository) {
    this.repository = repository;
    this.configAdminService = configAdminService;
    this.datasourceRepository = datasourceRepository;
  }

  @Transactional
  public void activate(long id, WecomSmartSheetProvisioningService.ProvisionedSheet created) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("table.assignment.document_id", created.documentId());
    values.put("table.assignment.sheet_id", created.sheetId());
    values.put("table.assignment.view_id", created.viewId());
    values.put("table.assignment.unique_field_title", created.uniqueFieldTitle());
    values.put("table.assignment_document_url", created.documentUrl());
    configAdminService.updateAll(values);
    datasourceRepository.ensureManagedSmartSheetDatasource("ASSIGNMENT", created.documentId(), created.sheetId());
    repository.activate(id);
  }

  @Transactional
  public void activateExisting(MonthlyAssignmentTable table) {
    activate(table.id(), new WecomSmartSheetProvisioningService.ProvisionedSheet(
        table.documentId(), table.documentUrl(), table.sheetId(), table.viewId(),
        table.tableName(), table.uniqueFieldTitle()));
  }
}
