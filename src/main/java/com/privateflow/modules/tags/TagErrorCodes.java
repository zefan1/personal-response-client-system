package com.privateflow.modules.tags;

public final class TagErrorCodes {

  public static final String CATEGORY_EXISTS = "90-10001";
  public static final String VALUE_EXISTS = "90-10002";
  public static final String BUILTIN_CATEGORY_DELETE_FORBIDDEN = "90-10003";
  public static final String VALUE_IN_USE = "90-10004";
  public static final String CATEGORY_HAS_VALUES = "90-10005";
  public static final String VALUE_LIMIT_EXCEEDED = "90-10006";
  public static final String CATEGORY_NOT_FOUND = "90-10007";
  public static final String VALUE_NOT_FOUND = "90-10008";

  private TagErrorCodes() {
  }
}
