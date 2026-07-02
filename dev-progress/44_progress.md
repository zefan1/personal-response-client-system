# Module 44 Progress - 运营E 账号与权限

## Sources
- Manual: `C:\Users\85314\Desktop\私域工具\44_运营E_账号与权限_开发实现手册.md`
- Shared contracts: `C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- Existing auth baseline: module H implementation under `src/main/java/com/privateflow/modules/api/auth`

## Implementation
- [x] Admin account list API: `GET /admin/api/v1/accounts`
- [x] Admin account create API: `POST /admin/api/v1/accounts`
- [x] Admin account update API: `PUT /admin/api/v1/accounts/{id}`
- [x] Admin account toggle API: `PUT /admin/api/v1/accounts/{id}/toggle`
- [x] Admin password reset API: `PUT /admin/api/v1/accounts/{id}/reset-password`
- [x] Admin account delete API: `DELETE /admin/api/v1/accounts/{id}`
- [x] Auth config API: `GET /api/v1/auth/config`
- [x] Phone credential support with legacy `username` compatibility.
- [x] BCrypt password hashing for created/reset passwords.
- [x] Role enum constrained to `ADMIN / LEADER / KEEPER`.
- [x] Keeper requires enabled leader.
- [x] Self-protection for role/status/delete operations.
- [x] Leader delete blocks when enabled keepers exist.
- [x] Toggle/reset/delete revoke `refresh:{phone}`.
- [x] JWT filter checks current account enabled state on every request.
- [x] Login rate limit uses configurable `system.login_fail_window_s`.
- [x] Captcha fields/config are reserved and enforced when enabled.
- [x] Migration `V24__module_44_account_permissions.sql` adds `phone`, `last_login_at`, and system config defaults.

## Downstream Consumer Check
- `AuthContext.username()` remains available and now resolves to phone for chat, WS, audit, datasource, skill, and quick-search callers.
- `AuthUser.username()` remains available for existing WS/offline queue and help routing code.
- `accounts.username` column is retained; new `phone` column is populated from it and both are written for new accounts.

## Validation Commands
- `python scripts\verify_module_44.py`
- `python scripts\verify_module_h.py`
- `git diff --check`

## Limits
- Java compiler/Maven are unavailable on this machine, so Java build validation cannot be run here.
- Real captcha provider integration is reserved behind config; default remains disabled.
