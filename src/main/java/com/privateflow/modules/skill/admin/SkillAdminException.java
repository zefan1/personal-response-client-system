package com.privateflow.modules.skill.admin;

public class SkillAdminException extends RuntimeException {
  private final String errorCode;

  public SkillAdminException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
