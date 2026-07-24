package com.privateflow.modules.tablewrite.client;

public final class WecomSmartSheetException extends IllegalStateException {

  private static final int UNKNOWN_ERRCODE = -1;

  private final String operation;
  private final int errcode;

  public WecomSmartSheetException(String operation, int errcode, String errmsg) {
    super("WeCom " + safeOperation(operation) + " failed (errcode=" + errcode + "): "
        + oneLine(errmsg));
    this.operation = safeOperation(operation);
    this.errcode = errcode;
  }

  public WecomSmartSheetException(String operation, String message, Throwable cause) {
    super("WeCom " + safeOperation(operation) + " failed: " + oneLine(message));
    this.operation = safeOperation(operation);
    this.errcode = UNKNOWN_ERRCODE;
  }

  public String operation() {
    return operation;
  }

  public int errcode() {
    return errcode;
  }

  private static String safeOperation(String value) {
    return oneLine(value).isBlank() ? "request" : oneLine(value);
  }

  private static String oneLine(String value) {
    return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
  }
}
