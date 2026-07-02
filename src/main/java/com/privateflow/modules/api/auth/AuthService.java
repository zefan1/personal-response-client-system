package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.config.SystemConfigProvider;
import java.time.Duration;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final AccountRepository accountRepository;
  private final JwtService jwtService;
  private final RefreshTokenStore refreshTokenStore;
  private final LoginRateLimiter rateLimiter;
  private final SystemConfigProvider configProvider;

  public AuthService(
      AccountRepository accountRepository,
      JwtService jwtService,
      RefreshTokenStore refreshTokenStore,
      LoginRateLimiter rateLimiter,
      SystemConfigProvider configProvider) {
    this.accountRepository = accountRepository;
    this.jwtService = jwtService;
    this.refreshTokenStore = refreshTokenStore;
    this.rateLimiter = rateLimiter;
    this.configProvider = configProvider;
  }

  public LoginResponse login(LoginRequest request, String ip, boolean adminLogin) {
    if (request == null || blank(request.username()) || blank(request.password())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "username and password are required");
    }
    if (rateLimiter.locked(ip)) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "login failed too many times");
    }
    Account account = accountRepository.findEnabledByUsername(request.username())
        .orElseThrow(() -> authFailure(ip));
    if (adminLogin && account.role() == Role.KEEPER) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
    if (!passwordMatches(request.password(), account.passwordHash())) {
      throw authFailure(ip);
    }
    rateLimiter.clear(ip);
    AuthUser user = new AuthUser(account.username(), account.displayName(), account.role(), account.leaderId());
    String accessToken = jwtService.issue(user);
    String refreshToken = refreshTokenStore.issue(user.username(), Duration.ofDays(configProvider.get().jwtRefreshDays()));
    return new LoginResponse(accessToken, refreshToken, configProvider.get().jwtExpireHours() * 3600L, user);
  }

  public LoginResponse refresh(RefreshRequest request, AuthUser user) {
    if (request == null || blank(request.refreshToken()) || user == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "refresh token invalid");
    }
    String stored = refreshTokenStore.read(user.username())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.AUTH_FAILED, "refresh token expired"));
    if (!stored.equals(request.refreshToken())) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "refresh token invalid");
    }
    String accessToken = jwtService.issue(user);
    String refreshToken = refreshTokenStore.issue(user.username(), Duration.ofDays(configProvider.get().jwtRefreshDays()));
    return new LoginResponse(accessToken, refreshToken, configProvider.get().jwtExpireHours() * 3600L, user);
  }

  private ApiException authFailure(String ip) {
    rateLimiter.recordFailure(ip);
    return new ApiException(ApiErrorCodes.AUTH_FAILED, "username or password is invalid");
  }

  private boolean passwordMatches(String raw, String stored) {
    if (stored != null && stored.startsWith("{plain}")) {
      return raw.equals(stored.substring("{plain}".length()));
    }
    return stored != null && BCrypt.checkpw(raw, stored);
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
