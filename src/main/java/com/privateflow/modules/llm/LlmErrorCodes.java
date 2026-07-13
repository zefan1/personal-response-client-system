package com.privateflow.modules.llm;

public final class LlmErrorCodes {

  public static final String CONFIG_MISSING = "30-20001";
  public static final String AUTH_FAILED = "30-20002";
  public static final String RATE_LIMITED = "30-20003";
  public static final String UNREACHABLE = "30-20004";
  public static final String TIMEOUT = "30-20005";
  public static final String RESPONSE_INVALID = "30-20006";

  private LlmErrorCodes() {
  }
}
