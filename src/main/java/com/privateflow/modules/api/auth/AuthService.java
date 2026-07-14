package com.privateflow.modules.api.auth;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.config.SystemConfigProvider;
import com.privateflow.modules.runtime.ProductionSafetyService;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private final AccountRepository accountRepository;
  private final JwtService jwtService;
  private final RefreshTokenStore refreshTokenStore;
  private final LoginRateLimiter rateLimiter;
  private final SystemConfigProvider configProvider;
  private final ProductionSafetyService productionSafetyService;
  private final AccountPermissionRepository permissionRepository;

  public AuthService(
      AccountRepository accountRepository,
      JwtService jwtService,
      RefreshTokenStore refreshTokenStore,
      LoginRateLimiter rateLimiter,
      SystemConfigProvider configProvider,
      ProductionSafetyService productionSafetyService,
      AccountPermissionRepository permissionRepository) {
    this.accountRepository = accountRepository;
    this.jwtService = jwtService;
    this.refreshTokenStore = refreshTokenStore;
    this.rateLimiter = rateLimiter;
    this.configProvider = configProvider;
    this.productionSafetyService = productionSafetyService;
    this.permissionRepository = permissionRepository;
  }

  public LoginResponse login(LoginRequest request, String ip, boolean adminLogin) {
    String phone = request == null ? null : request.loginPhone();
    if (blank(phone) || request == null || blank(request.password())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请输入手机号和密码");
    }
    if (rateLimiter.locked(ip)) {
      throw new ApiException(ApiErrorCodes.LOGIN_RATE_LIMITED, "登录失败次数过多，请稍后再试");
    }
    validateCaptcha(request);
    Account account = accountRepository.findByPhone(phone.trim())
        .orElseThrow(() -> authFailure(ip));
    if (!account.enabled()) {
      throw new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "账号已停用，请联系管理员");
    }
    if (!passwordMatches(request.password(), account.passwordHash())) {
      throw authFailure(ip);
    }
    Set<String> permissions = permissions(account);
    if (adminLogin && account.role() != Role.ADMIN && !permissions.contains(PermissionCodes.TAG_MANAGEMENT)) {
      throw new ApiException(ApiErrorCodes.FORBIDDEN, "当前账号没有后台权限");
    }
    rateLimiter.clear(ip);
    accountRepository.updateLastLogin(account.username());
    AuthUser user = authUser(account, permissions);
    String accessToken = jwtService.issue(user);
    String refreshToken = refreshTokenStore.issue(user.username(), Duration.ofSeconds(configProvider.get().jwtRefreshTokenTtlS()));
    return new LoginResponse(accessToken, refreshToken, configProvider.get().jwtAccessTokenTtlS(), user);
  }

  public LoginResponse refresh(RefreshRequest request, AuthUser user) {
    if (request == null || blank(request.refreshToken()) || user == null) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "登录状态无效，请重新登录");
    }
    String stored = refreshTokenStore.read(user.username())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.AUTH_FAILED, "登录已过期，请重新登录"));
    if (!stored.equals(request.refreshToken())) {
      throw new ApiException(ApiErrorCodes.AUTH_FAILED, "登录状态无效，请重新登录");
    }
    Account account = accountRepository.findByPhone(user.username())
        .orElseThrow(() -> new ApiException(ApiErrorCodes.AUTH_FAILED, "登录状态无效，请重新登录"));
    if (!account.enabled()) {
      throw new ApiException(ApiErrorCodes.ACCOUNT_DISABLED, "账号已停用，请联系管理员");
    }
    AuthUser freshUser = authUser(account, permissions(account));
    String accessToken = jwtService.issue(freshUser);
    return new LoginResponse(accessToken, request.refreshToken(), configProvider.get().jwtAccessTokenTtlS(), freshUser);
  }

  private ApiException authFailure(String ip) {
    rateLimiter.recordFailure(ip);
    return new ApiException(ApiErrorCodes.AUTH_FAILED, "手机号或密码不正确");
  }

  private AuthUser authUser(Account account, Set<String> permissions) {
    return new AuthUser(
        account.username(),
        account.displayName(),
        account.role(),
        account.leaderId(),
        account.tokenVersion(),
        permissions);
  }

  private Set<String> permissions(Account account) {
    Set<String> permissions = new LinkedHashSet<>(permissionRepository.findEnabledByAccountId(account.id()));
    if (account.role() == Role.ADMIN) {
      permissions.add(PermissionCodes.TAG_MANAGEMENT);
    }
    return Set.copyOf(permissions);
  }

  private boolean passwordMatches(String raw, String stored) {
    if (stored != null && stored.startsWith("{plain}")) {
      if (productionSafetyService.isProduction()) {
        throw new ApiException(ApiErrorCodes.AUTH_FAILED, "生产环境不允许使用明文密码");
      }
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
      throw new ApiException(ApiErrorCodes.INTERNAL_ERROR, "验证码服务暂不可用");
    }
    if (blank(request.captcha()) || blank(request.captchaTicket())) {
      throw new ApiException(ApiErrorCodes.BAD_REQUEST, "请先完成验证码校验");
    }
  }
}
