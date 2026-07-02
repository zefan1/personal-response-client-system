package com.privateflow.modules.skill;

public class SkillGatewayException extends RuntimeException {
  private final String errorCode;
  private final boolean circuitFailure;

  public SkillGatewayException(String errorCode, String message, boolean circuitFailure) {
    super(message);
    this.errorCode = errorCode;
    this.circuitFailure = circuitFailure;
  }

  public SkillGatewayException(String errorCode, String message, boolean circuitFailure, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
    this.circuitFailure = circuitFailure;
  }

  public String getErrorCode() {
    return errorCode;
  }

  public boolean isCircuitFailure() {
    return circuitFailure;
  }
}
