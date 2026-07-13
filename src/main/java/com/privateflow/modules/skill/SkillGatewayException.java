package com.privateflow.modules.skill;

public class SkillGatewayException extends RuntimeException {
  private final String errorCode;
  private final boolean circuitFailure;
  private final boolean fallbackAllowed;

  public SkillGatewayException(String errorCode, String message, boolean circuitFailure) {
    this(errorCode, message, circuitFailure, true);
  }

  public SkillGatewayException(String errorCode, String message, boolean circuitFailure, boolean fallbackAllowed) {
    super(message);
    this.errorCode = errorCode;
    this.circuitFailure = circuitFailure;
    this.fallbackAllowed = fallbackAllowed;
  }

  public SkillGatewayException(String errorCode, String message, boolean circuitFailure, Throwable cause) {
    this(errorCode, message, circuitFailure, true, cause);
  }

  public SkillGatewayException(String errorCode, String message, boolean circuitFailure, boolean fallbackAllowed, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.circuitFailure = circuitFailure;
    this.fallbackAllowed = fallbackAllowed;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public boolean isCircuitFailure() {
    return circuitFailure;
  }

  public boolean isFallbackAllowed() {
    return fallbackAllowed;
  }
}
