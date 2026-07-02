package com.privateflow.modules.api;

public final class ApiErrorCodes {

  public static final String BAD_REQUEST = "80-10001";
  public static final String AUTH_FAILED = "80-10002";
  public static final String FORBIDDEN = "80-10003";
  public static final String INTERNAL_ERROR = "80-10004";
  public static final String WS_CONNECT_FAILED = "80-10005";
  public static final String CONFLICT = "80-10006";
  public static final String CONFIG_INVALID = "80-10007";
  public static final String ACCOUNT_DISABLED = "80-10008";
  public static final String LOGIN_RATE_LIMITED = "80-10009";
  public static final String VERSION_EXISTS = "80-10010";
  public static final String VERSION_STATUS_INVALID = "80-10011";
  public static final String VERSION_PACKAGE_MISSING = "80-10012";
  public static final String VERSION_UPLOAD_FAILED = "80-10013";

  private ApiErrorCodes() {
  }
}
