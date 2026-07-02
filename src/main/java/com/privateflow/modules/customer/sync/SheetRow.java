package com.privateflow.modules.customer.sync;

import java.util.Map;

public record SheetRow(String rowId, Map<String, String> values) {
}
