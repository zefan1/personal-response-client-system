package com.privateflow.modules.match;

public class CustomerMatchException extends RuntimeException {

  private final String errorCode;

  public CustomerMatchException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public CustomerMatchException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
