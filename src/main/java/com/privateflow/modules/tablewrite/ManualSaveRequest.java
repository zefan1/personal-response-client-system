package com.privateflow.modules.tablewrite;

import java.util.Map;

public record ManualSaveRequest(String sourceTable, String sourceRowId, Map<String, Object> fields) {
}
