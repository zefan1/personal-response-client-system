package com.privateflow.modules.api.auth;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    AuthUser userInfo
) {
}
