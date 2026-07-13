package com.privateflow.modules.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.privateflow.modules.api.ApiErrorCodes;
import com.privateflow.modules.api.ApiException;
import com.privateflow.modules.api.Role;
import com.privateflow.modules.api.config.SystemConfig;
import com.privateflow.modules.api.config.SystemConfigProvider;
import com.privateflow.modules.runtime.ProductionSafetyService;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class AuthServiceTest {

  @Mock
  private AccountRepository accountRepository;
  @Mock
  private JwtService jwtService;
  @Mock
  private RefreshTokenStore refreshTokenStore;
  @Mock
  private LoginRateLimiter rateLimiter;
  @Mock
  private SystemConfigProvider configProvider;
  @Mock
  private ProductionSafetyService productionSafetyService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    when(configProvider.get()).thenReturn(config());
    authService = new AuthService(accountRepository, jwtService, refreshTokenStore, rateLimiter, configProvider, productionSafetyService);
  }

  @Test
  void loginIssuesTokensForEnabledAdmin() {
    Account account = new Account(1L, "admin", "{plain}admin123", "Admin", Role.ADMIN, null, true);
    AuthUser expectedUser = new AuthUser("admin", "Admin", Role.ADMIN, null);
    when(accountRepository.findByPhone("admin")).thenReturn(Optional.of(account));
    when(jwtService.issue(expectedUser)).thenReturn("access-token");
    when(refreshTokenStore.issue(eq("admin"), any(Duration.class))).thenReturn("refresh-token");

    LoginResponse response = authService.login(new LoginRequest(null, "admin123", null, null, "admin"), "127.0.0.1", true);

    assertThat(response.accessToken()).isEqualTo("access-token");
    assertThat(response.refreshToken()).isEqualTo("refresh-token");
    assertThat(response.account()).isEqualTo(expectedUser);
    verify(accountRepository).updateLastLogin("admin");
    verify(rateLimiter).clear("127.0.0.1");
  }

  @Test
  void adminLoginRejectsNonAdminRole() {
    Account account = new Account(2L, "leader", "{plain}leader123", "Leader", Role.LEADER, null, true);
    when(accountRepository.findByPhone("leader")).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> authService.login(new LoginRequest("leader", "leader123", null, null, null), "127.0.0.1", true))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.FORBIDDEN);

    verify(jwtService, never()).issue(any());
  }

  @Test
  void loginRejectsDisabledAccount() {
    Account account = new Account(3L, "disabled", "{plain}pass123", "Disabled", Role.ADMIN, null, false);
    when(accountRepository.findByPhone("disabled")).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> authService.login(new LoginRequest("disabled", "pass123", null, null, null), "127.0.0.1", false))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.ACCOUNT_DISABLED);

    verify(rateLimiter, never()).recordFailure(anyString());
  }

  @Test
  void loginRecordsFailureForWrongPassword() {
    Account account = new Account(4L, "admin", "{plain}admin123", "Admin", Role.ADMIN, null, true);
    when(accountRepository.findByPhone("admin")).thenReturn(Optional.of(account));

    assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "wrong-pass", null, null, null), "127.0.0.1", false))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.AUTH_FAILED);

    verify(rateLimiter).recordFailure("127.0.0.1");
    verify(jwtService, never()).issue(any());
  }

  @Test
  void productionRejectsLegacyPlainPasswords() {
    Account account = new Account(5L, "admin", "{plain}admin123", "Admin", Role.ADMIN, null, true);
    when(accountRepository.findByPhone("admin")).thenReturn(Optional.of(account));
    when(productionSafetyService.isProduction()).thenReturn(true);

    assertThatThrownBy(() -> authService.login(new LoginRequest("admin", "admin123", null, null, null), "127.0.0.1", false))
        .isInstanceOf(ApiException.class)
        .extracting(ex -> ((ApiException) ex).getErrorCode())
        .isEqualTo(ApiErrorCodes.AUTH_FAILED);

    verify(jwtService, never()).issue(any());
  }

  private SystemConfig config() {
    return new SystemConfig(
        "test-secret",
        24,
        7,
        30,
        60,
        100,
        15000,
        90,
        10,
        300,
        false,
        "",
        "",
        "",
        300,
        7,
        30,
        "config:change",
        "ws:push");
  }
}
