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
    String phone = request == null ? null : request.loginPhone();
    if (blank(phone) || blank(request.password())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "phone and password are required");
    }
    if (rateLimiter.locked(ip)) {
      throw new ApiException(ApiErrorCodes.LOGIN_RATE_LIMITED, "login failed too many times");
    }
    validateCaptcha(request);
    Account account = accountRepository.findByPhone(phone.trim())
        .orElseThrow(() -> authFailure(ip));
    if (!account.enabled()) {
      throw new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "account disabled");
    }
    if (adminLogin && account.role() == Role.KEEPER) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "permission denied");
    }
    if (!passwordMatches(request.password(), account.passwordHash())) {
      throw authFailure(ip);
    }
    rateLimiter.clear(ip);
    accountRepository.updateLastLogin(account.username());
    AuthUser user = new AuthUser(account.username(), account.displayName(), account.role(), account.leaderId());
    String accessToken = jwtService.issue(user);
    String refreshToken = refreshTokenStore.issue(user.username(), Duration.ofSeconds(configProvider.get().jwtRefreshTokenTtlS()));
    return new LoginResponse(accessToken, refreshToken, configProvider.get().jwtAccessTokenTtlS(), user);
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
    Account account = accountRepository.findByPhone(user.username())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.AUTH_FAILED, "refresh token invalid"));
    if (!account.enabled()) {
      throw new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "account disabled");
    }
    AuthUser freshUser = new AuthUser(account.username(), account.displayName(), account.role(), account.leaderId());
    String accessToken = jwtService.issue(freshUser);
    return new LoginResponse(accessToken, request.refreshToken(), configProvider.get().jwtAccessTokenTtlS(), freshUser);
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

  private void validateCaptcha(LoginRequest request) {
    if (!configProvider.get().captchaEnabled()) {
      return;
    }
    if (blank(configProvider.get().captchaProvider()) || blank(configProvider.get().captchaAppId())
        || blank(configProvider.get().captchaSecret())) {
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "captcha service unavailable");
    }
    if (blank(request.captcha()) || blank(request.captchaTicket())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "captcha and captchaTicket are required");
    }
  }
}
