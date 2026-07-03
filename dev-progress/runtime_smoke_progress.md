# Runtime Smoke Progress

## Scope

- Backend runtime startup against real MariaDB and Redis in WSL Ubuntu.
- Flyway migration execution from an empty database.
- Public auth config endpoint.
- Admin login with seeded `admin/admin123` account.
- Authenticated health endpoint.

## Verification Command

```bash
bash scripts/smoke_backend_wsl.sh
```

## Latest Result

```text
auth_config={"success":true,"data":{"captchaProvider":"","captchaEnabled":false},"errorCode":null,"message":null}
login_success=true
health={"success":true,"status":"UP","components":["db","imageRecognition","redis","skill","wecomTable"]}
flyway_migrations=19
table_count=31
```

## Fixes Made During Runtime Verification

- Renamed the profile audit repository bean to avoid a Spring default bean-name conflict with the admin audit repository.
- Moved notice scheduled-scan delay resolution to `NoticeScheduleProperties` to avoid a self-referencing scheduled expression cycle.
- Added `scripts/smoke_backend_wsl.sh` as a repeatable runtime smoke verifier.

## Remaining Production Acceptance Boundary

- External providers are still smoke-tested with `MOCK_EXTERNALS=true`.
- Real Skill, image recognition, and WeCom table credentials still require environment-level acceptance.
