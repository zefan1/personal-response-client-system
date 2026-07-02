package com.privateflow.modules.api;

public class ApiException extends RuntimeException {

  private final String errorCode;

  public ApiException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
