package com.privateflow.modules.customer.admin;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    LocalDateTime updatedAt,
    List<CustomerTagSummary> tags) {

  public CustomerAdminListItem(
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
    this(
        id, phone, nickname, sourceChannel, leadType, assignedKeeper, intendedStore,
        intendedProject, customerStage, intentLevel, lastFollowupAt, nextFollowupAt,
        appointmentDate, appointmentStore, appointmentItem, arrived, sourceTable,
        updatedAt, List.of());
  }

  public CustomerAdminListItem {
    tags = tags == null ? List.of() : List.copyOf(tags);
  }

  public CustomerAdminListItem withTags(List<CustomerTagSummary> nextTags) {
    return new CustomerAdminListItem(
        id, phone, nickname, sourceChannel, leadType, assignedKeeper, intendedStore,
        intendedProject, customerStage, intentLevel, lastFollowupAt, nextFollowupAt,
        appointmentDate, appointmentStore, appointmentItem, arrived, sourceTable,
        updatedAt, nextTags);
  }
}
