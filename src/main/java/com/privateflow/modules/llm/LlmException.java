package com.privateflow.modules.llm;

public class LlmException extends RuntimeException {

  private final String errorCode;

  public LlmException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public LlmException(String errorCode, String message, Throwable cause) {
    super(message, cause);
    this.errorCode = errorCode;
  }

  public String errorCode() {
    return errorCode;
  }
}
