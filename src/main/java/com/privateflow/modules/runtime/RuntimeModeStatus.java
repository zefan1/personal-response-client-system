package com.privateflow.modules.runtime;

public record RuntimeModeStatus(
    boolean mockExternals,
    String label,
    String description
) {
}
