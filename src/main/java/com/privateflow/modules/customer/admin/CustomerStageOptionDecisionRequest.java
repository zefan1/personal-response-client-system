package com.privateflow.modules.customer.admin;

public record CustomerStageOptionDecisionRequest(
    String oldOptionId,
    String newOptionId,
    String decision) {
}
