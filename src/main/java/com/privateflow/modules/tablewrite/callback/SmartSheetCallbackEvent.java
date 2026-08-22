package com.privateflow.modules.tablewrite.callback;

import com.privateflow.modules.tablewrite.config.AuxiliarySmartSheetTarget;
import java.util.List;

record SmartSheetCallbackEvent(
    String eventKey,
    String role,
    String sourceTable,
    AuxiliarySmartSheetTarget target,
    String changeType,
    List<String> recordIds,
    String operator) {

  SmartSheetCallbackEvent {
    recordIds = recordIds == null ? List.of() : List.copyOf(recordIds);
  }
}
