package com.privateflow.modules.followup;

public class FollowupException extends RuntimeException {

  private final String errorCode;

  public FollowupException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public FollowupException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
