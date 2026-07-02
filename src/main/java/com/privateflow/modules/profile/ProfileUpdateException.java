package com.privateflow.modules.profile;

public class ProfileUpdateException extends RuntimeException {

  private final String errorCode;

  public ProfileUpdateException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public ProfileUpdateException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
