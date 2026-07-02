package com.privateflow.modules.api.auth;

public record LoginRequest(String phone, String password, String captcha, String captchaTicket, String username) {
  public String loginPhone() {
    return phone == null || phone.isBlank() ? username : phone;
  }
}
