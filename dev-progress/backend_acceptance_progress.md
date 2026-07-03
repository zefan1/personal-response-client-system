# Backend API Acceptance Progress

## Position

This progress card tracks backend API acceptance work. It does not certify the SaaS as production-ready yet.

## Harness

- Script: `scripts/acceptance_backend_api.py`.
- Purpose: start or reuse the WSL mock-runtime backend, login as seeded admin, exercise representative backend API flows, and write a JSON report under `.tools/acceptance/`.
- Runtime mode: local MariaDB + Redis with `MOCK_EXTERNALS=true`.
- Scope label: backend route behavior acceptance in mock external mode, not real-provider production acceptance.

## Covered Flow Groups

- Auth config, admin login, token refresh.
- Health and config reads.
- Admin account CRUD.
- Skill scene binding CRUD.
- Skill and image environment CRUD/activation.
- Datasource CRUD, mappings, restore, structured compare, inferred/sample columns, CSV import, persisted import logs.
- Quick search CRUD and image upload.
- Followup rule CRUD.
- Tag category/value CRUD with duplicate binding guard.
- Notices immediate and scheduled lifecycle.
- Version create/update/upload/publish/report/revoke.
- Audit, analytics, desktop, customer, quick search, notices, followup read endpoints.

## Current Known Limits

- Harness intentionally does not claim real external-provider acceptance.
- Java test suite now has focused coverage, but not full controller/repository integration coverage.
- Admin frontend now exists in the desktop/Vite renderer, but the bespoke workflow UI and full browser click coverage are still incomplete.
- Real WeCom table metadata still depends on a production `SheetClient`; datasource columns now expose `fetchStatus` and fallback source instead of returning an empty placeholder.

## Latest Passing Run

- Command:

```bash
wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && python3 scripts/acceptance_backend_api.py'
```

- Result after admin frontend/CORS work: 99 passed, 0 failed.
- Report: `.tools/acceptance/backend_api_acceptance_20260703121721.json`.
- Browser smoke: login at `http://127.0.0.1:5173/` using `admin/admin123`, then checked health/config, skill bindings, AI/external environments, datasource mappings, accounts, notices/versions/audit sections.

## Validation Command

```bash
wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && python3 scripts/acceptance_backend_api.py'
```

## Java And DB Evidence

- `mvn test`: 11 tests run, 0 failures.
- `python scripts/verify_database_alignment.py`: 31 live MariaDB tables inspected, 14 key tables checked, 0 missing required columns.
