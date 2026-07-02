package com.privateflow.modules.tablewrite;

import java.time.LocalDateTime;

public class PendingTableWrite {

  private Long id;
  private String phone;
  private TableWriteActionType actionType;
  private String payload;
  private int retryCount;
  private TableWriteStatus status;
  private LocalDateTime nextRetryAt;
  private String errorMsg;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public String getPhone() { return phone; }
  public void setPhone(String phone) { this.phone = phone; }
  public TableWriteActionType getActionType() { return actionType; }
  public void setActionType(TableWriteActionType actionType) { this.actionType = actionType; }
  public String getPayload() { return payload; }
  public void setPayload(String payload) { this.payload = payload; }
  public int getRetryCount() { return retryCount; }
  public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
  public TableWriteStatus getStatus() { return status; }
  public void setStatus(TableWriteStatus status) { this.status = status; }
  public LocalDateTime getNextRetryAt() { return nextRetryAt; }
  public void setNextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; }
  public String getErrorMsg() { return errorMsg; }
  public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
}
