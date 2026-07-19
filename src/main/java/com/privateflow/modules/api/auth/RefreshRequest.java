package com.privateflow.modules.api.auth;

public record RefreshRequest(String refreshToken, String username) {

  public RefreshRequest(String refreshToken) {
    this(refreshToken, null);
  }
}
