package com.privateflow.common.events;

public record NewLeadEvent(String phone, String leadType, String sourceTable) {
}
