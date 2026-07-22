package com.privateflow.modules.api.chat;

public enum PendingReplyTaskStatus {
  WAITING_CUSTOMER,
  GENERATING,
  READY,
  FAILED,
  CANCELLED,
  EXPIRED
}
