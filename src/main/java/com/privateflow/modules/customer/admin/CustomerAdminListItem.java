package com.privateflow.modules.customer.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CustomerAdminListItem(
    long id,
    String phone,
    String nickname,
    String sourceChannel,
    String leadType,
    String assignedKeeper,
    String intendedStore,
    String intendedProject,
    String customerStage,
    String intentLevel,
    LocalDateTime lastFollowupAt,
    LocalDateTime nextFollowupAt,
    LocalDate appointmentDate,
    String appointmentStore,
    String appointmentItem,
    String arrived,
    String sourceTable,
    LocalDateTime updatedAt) {
}
