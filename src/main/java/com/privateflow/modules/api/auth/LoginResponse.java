package com.privateflow.modules.api.auth;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    AuthUser account
) {
  public AuthUser userInfo() {
    return account;
  }
}
