package com.privateflow.modules.tablewrite;

public class TableWriteException extends RuntimeException {

  private final String errorCode;

  public TableWriteException(String errorCode, String message) {
    super(message);
    this.errorCode = errorCode;
  }

  public String getErrorCode() {
    return errorCode;
  }
}
