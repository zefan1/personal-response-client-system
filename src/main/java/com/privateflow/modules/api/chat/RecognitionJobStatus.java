package com.privateflow.modules.api.chat;

public enum RecognitionJobStatus {
  QUEUED,
  RECOGNIZING,
  READY,
  WAITING_CUSTOMER,
  FAILED,
  CANCELLED,
  EXPIRED
}
