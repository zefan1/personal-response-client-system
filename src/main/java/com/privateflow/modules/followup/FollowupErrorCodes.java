package com.privateflow.modules.followup;

public final class FollowupErrorCodes {

  public static final String SCAN_TIMEOUT = "60-10001";
  public static final String CONDITION_PARSE_FAILED = "60-10002";
  public static final String PUSH_QUEUE_OVERFLOW = "60-10003";
  public static final String BAD_REQUEST = "80-10001";
  public static final String FORBIDDEN = "80-10003";
  public static final String INTERNAL_ERROR = "80-10004";

  private FollowupErrorCodes() {
  }
}
