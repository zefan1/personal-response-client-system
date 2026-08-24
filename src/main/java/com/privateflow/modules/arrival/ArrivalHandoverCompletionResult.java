package com.privateflow.modules.arrival;

public record ArrivalHandoverCompletionResult(
    boolean databaseSaved,
    boolean synced,
    String wecomRowId,
    String syncError) {}
