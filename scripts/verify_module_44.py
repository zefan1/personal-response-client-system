from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/api/account/AccountAdminController.java",
    "src/main/java/com/privateflow/modules/api/account/AccountAdminService.java",
    "src/main/java/com/privateflow/modules/api/account/AccountAdminRepository.java",
    "src/main/java/com/privateflow/modules/api/account/AccountAdminItem.java",
    "src/main/java/com/privateflow/modules/api/account/AccountCreateRequest.java",
    "src/main/java/com/privateflow/modules/api/account/AccountUpdateRequest.java",
    "src/main/java/com/privateflow/modules/api/account/AccountToggleRequest.java",
    "src/main/java/com/privateflow/modules/api/account/PasswordResetRequest.java",
    "src/main/resources/db/migration/V24__module_44_account_permissions.sql",
    "dev-progress/44_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/api/account/AccountAdminController.java")
for token in [
    "/admin/api/v1/accounts",
    "page_size",
    "is_enabled",
    "/admin/api/v1/accounts/{id}/toggle",
    "/admin/api/v1/accounts/{id}/reset-password",
    "@DeleteMapping",
]:
    if token not in controller:
        errors.append(f"AccountAdminController missing {token}")

service = read("src/main/java/com/privateflow/modules/api/account/AccountAdminService.java")
for token in [
    "BCrypt.hashpw",
    "^1[3-9]\\\\d{9}$",
    "Role.KEEPER",
    "enabledLeaderExists",
    "cannot modify own role or status",
    "enabledKeeperCount",
    "refreshTokenStore.revoke",
    "password length must be at least 6",
]:
    if token not in service:
        errors.append(f"AccountAdminService missing {token}")

repo = read("src/main/java/com/privateflow/modules/api/account/AccountAdminRepository.java")
for token in [
    "COALESCE(a.phone, a.username)",
    "LEFT JOIN accounts l",
    "LIMIT ? OFFSET ?",
    "phone = ? OR username = ?",
    "last_login_at",
    "INSERT INTO accounts (phone, username, password_hash",
]:
    if token not in repo:
        errors.append(f"AccountAdminRepository missing {token}")

auth_service = read("src/main/java/com/privateflow/modules/api/auth/AuthService.java")
for token in [
    "request.loginPhone()",
    "ACCOUNT_DISABLED",
    "LOGIN_RATE_LIMITED",
    "validateCaptcha",
    "updateLastLogin",
    "jwtRefreshTokenTtlS",
    "jwtAccessTokenTtlS",
    "return new LoginResponse(accessToken, request.refreshToken()",
]:
    if token not in auth_service:
        errors.append(f"AuthService missing {token}")

auth_filter = read("src/main/java/com/privateflow/modules/api/auth/JwtAuthenticationFilter.java")
for token in [
    "AccountRepository accountRepository",
    "accountRepository.findByPhone",
    "ACCOUNT_DISABLED",
    "new AuthUser(account.username()",
    "/api/v1/auth/config",
]:
    if token not in auth_filter:
        errors.append(f"JwtAuthenticationFilter missing {token}")

login_request = read("src/main/java/com/privateflow/modules/api/auth/LoginRequest.java")
for token in ["String phone", "String captcha", "String captchaTicket", "loginPhone()"]:
    if token not in login_request:
        errors.append(f"LoginRequest missing {token}")

auth_controller = read("src/main/java/com/privateflow/modules/api/web/AuthController.java")
for token in ["/api/v1/auth/config", "captchaEnabled", "captchaProvider"]:
    if token not in auth_controller:
        errors.append(f"AuthController missing {token}")

config = read("src/main/java/com/privateflow/modules/api/config/SystemConfig.java")
for token in ["loginFailWindowS", "captchaEnabled", "captchaProvider", "captchaAppId", "captchaSecret", "jwtAccessTokenTtlS", "jwtRefreshTokenTtlS"]:
    if token not in config:
        errors.append(f"SystemConfig missing {token}")

config_admin = read("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java")
for token in ["system.login_fail_window_s", "system.jwt_access_token_ttl_s", "system.jwt_refresh_token_ttl_s"]:
    if token not in config_admin:
        errors.append(f"ConfigAdminService missing {token}")

migration = read("src/main/resources/db/migration/V24__module_44_account_permissions.sql")
for token in [
    "ADD COLUMN IF NOT EXISTS phone",
    "ADD COLUMN IF NOT EXISTS last_login_at",
    "UPDATE accounts SET phone = username",
    "system.jwt_access_token_ttl_s",
    "system.jwt_refresh_token_ttl_s",
    "system.login_fail_window_s",
    "system.captcha_enabled",
]:
    if token not in migration:
        errors.append(f"migration missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 44 verification passed")
