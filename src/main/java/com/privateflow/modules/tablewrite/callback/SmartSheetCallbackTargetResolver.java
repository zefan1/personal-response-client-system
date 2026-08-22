package com.privateflow.modules.tablewrite.callback;

import com.privateflow.modules.customer.admin.DatasourceAdminRepository;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTargets;
import com.privateflow.modules.tablewrite.config.WecomSmartSheetConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class SmartSheetCallbackTargetResolver {

  private final WecomSmartSheetConfig primaryConfig;
  private final AuxiliarySmartSheetTargets auxiliaryTargets;
  private final DatasourceAdminRepository datasourceRepository;

  SmartSheetCallbackTargetResolver(
      WecomSmartSheetConfig primaryConfig,
      AuxiliarySmartSheetTargets auxiliaryTargets,
      DatasourceAdminRepository datasourceRepository) {
    this.primaryConfig = primaryConfig;
    this.auxiliaryTargets = auxiliaryTargets;
    this.datasourceRepository = datasourceRepository;
  }

  Optional<ResolvedTarget> resolve(String documentId, String sheetId) {
    for (ResolvedTarget target : targets()) {
      if (target.target().documentId().equals(text(documentId))
          && target.target().sheetId().equals(text(sheetId))) {
        return Optional.of(target);
      }
    }
    return Optional.empty();
  }

  private List<ResolvedTarget> targets() {
    AuxiliarySmartSheetTarget primary = new AuxiliarySmartSheetTarget(
        "PRIMARY", primaryConfig.documentId(), primaryConfig.sheetId(), primaryConfig.viewId(),
        primaryConfig.uniqueFieldTitle(), "");
    List<ResolvedTarget> result = new ArrayList<>();
    resolved("PRIMARY", primary).ifPresent(result::add);
    auxiliaryTargets.assignment().flatMap(target -> resolved("ASSIGNMENT", target)).ifPresent(result::add);
    auxiliaryTargets.arrival().flatMap(target -> resolved("ARRIVAL", target)).ifPresent(result::add);
    return result.stream().filter(item -> item.target().configured()).toList();
  }

  private Optional<ResolvedTarget> resolved(String role, AuxiliarySmartSheetTarget target) {
    String sourceTable = datasourceRepository.managedSourceTable(role)
        .orElse("PRIMARY".equals(role) ? primaryConfig.sourceTable() : role + ":" + target.sheetId());
    return sourceTable == null || sourceTable.isBlank() ? Optional.empty()
        : Optional.of(new ResolvedTarget(role, sourceTable, target));
  }

  private static String text(String value) {
    return value == null ? "" : value.trim();
  }

  record ResolvedTarget(String role, String sourceTable, AuxiliarySmartSheetTarget target) {
  }
}
