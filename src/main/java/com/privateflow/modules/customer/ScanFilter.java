package com.privateflow.modules.customer;

public record ScanFilter(
    Integer lastFollowupBeforeHours,
    Boolean nextFollowupBeforeNow,
    Integer noMessageDays,
    Boolean assignedKeeperNotNull,
    Integer limit
) {
}
