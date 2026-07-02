package com.privateflow.modules.match.util;

public final class PhoneUtils {

  private PhoneUtils() {
  }

  public static String clean(String phone) {
    return phone == null ? "" : phone.replaceAll("[^\\d]", "");
  }

  public static boolean isValid(String phone) {
    return phone != null && phone.matches("\\d{11}");
  }

  public static String mask(String phone) {
    if (!isValid(phone)) {
      return phone;
    }
    return phone.replaceAll("(?<=\\d{3})\\d{4}(?=\\d{4})", "****");
  }
}
