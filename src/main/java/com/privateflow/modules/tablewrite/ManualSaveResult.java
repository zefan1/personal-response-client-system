package com.privateflow.modules.tablewrite;

import java.util.List;

public record ManualSaveResult(boolean written, List<String> updatedFields) {
}
