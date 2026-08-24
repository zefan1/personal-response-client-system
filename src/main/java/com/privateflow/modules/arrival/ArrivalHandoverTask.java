package com.privateflow.modules.arrival;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ArrivalHandoverTask {
  private long id;
  private long customerId;
  private String phone;
  private String assignedKeeper;
  private LocalDate appointmentDate;
  private String appointmentTime;
  private String appointmentStore;
  private String appointmentItem;
  private String visitType;
  private String voucherRedeemed;
  private String experienceProject;
  private String projectType;
  private String historicalExperienceCount;
  private String customerReport;
  private String taskStatus;
  private String syncStatus;
  private String wecomRowId;
  private int syncRetryCount;
  private LocalDateTime nextSyncAt;
  private String syncError;
  private LocalDateTime remindedAt;
  private String completedBy;
  private LocalDateTime completedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  public long getId() { return id; } public void setId(long value) { id = value; }
  public long getCustomerId() { return customerId; } public void setCustomerId(long value) { customerId = value; }
  public String getPhone() { return phone; } public void setPhone(String value) { phone = value; }
  public String getAssignedKeeper() { return assignedKeeper; } public void setAssignedKeeper(String value) { assignedKeeper = value; }
  public LocalDate getAppointmentDate() { return appointmentDate; } public void setAppointmentDate(LocalDate value) { appointmentDate = value; }
  public String getAppointmentTime() { return appointmentTime; } public void setAppointmentTime(String value) { appointmentTime = value; }
  public String getAppointmentStore() { return appointmentStore; } public void setAppointmentStore(String value) { appointmentStore = value; }
  public String getAppointmentItem() { return appointmentItem; } public void setAppointmentItem(String value) { appointmentItem = value; }
  public String getVisitType() { return visitType; } public void setVisitType(String value) { visitType = value; }
  public String getVoucherRedeemed() { return voucherRedeemed; } public void setVoucherRedeemed(String value) { voucherRedeemed = value; }
  public String getExperienceProject() { return experienceProject; } public void setExperienceProject(String value) { experienceProject = value; }
  public String getProjectType() { return projectType; } public void setProjectType(String value) { projectType = value; }
  public String getHistoricalExperienceCount() { return historicalExperienceCount; } public void setHistoricalExperienceCount(String value) { historicalExperienceCount = value; }
  public String getCustomerReport() { return customerReport; } public void setCustomerReport(String value) { customerReport = value; }
  public String getTaskStatus() { return taskStatus; } public void setTaskStatus(String value) { taskStatus = value; }
  public String getSyncStatus() { return syncStatus; } public void setSyncStatus(String value) { syncStatus = value; }
  public String getWecomRowId() { return wecomRowId; } public void setWecomRowId(String value) { wecomRowId = value; }
  public int getSyncRetryCount() { return syncRetryCount; } public void setSyncRetryCount(int value) { syncRetryCount = value; }
  public LocalDateTime getNextSyncAt() { return nextSyncAt; } public void setNextSyncAt(LocalDateTime value) { nextSyncAt = value; }
  public String getSyncError() { return syncError; } public void setSyncError(String value) { syncError = value; }
  public LocalDateTime getRemindedAt() { return remindedAt; } public void setRemindedAt(LocalDateTime value) { remindedAt = value; }
  public String getCompletedBy() { return completedBy; } public void setCompletedBy(String value) { completedBy = value; }
  public LocalDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(LocalDateTime value) { completedAt = value; }
  public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime value) { createdAt = value; }
  public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
