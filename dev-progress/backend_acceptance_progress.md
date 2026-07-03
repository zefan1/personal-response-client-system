# Backend API Acceptance Progress

## Position

This progress card tracks production-grade backend API acceptance work. It does not certify the SaaS as production-ready yet.

## Harness

- Added `scripts/acceptance_backend_api.py`.
- Purpose: start or reuse the WSL mock-runtime backend, login as seeded admin, exercise representative backend API flows, and write a JSON report under `.tools/acceptance/`.
- Runtime mode: local MariaDB + Redis with `MOCK_EXTERNALS=true`.
- Scope label: backend route behavior acceptance in mock external mode, not real-provider production acceptance.

## Covered Flow Groups

- Auth config, admin login, token refresh.
- Health and config reads.
- Admin account CRUD.
- Skill scene binding CRUD.
- Skill and image environment CRUD/activation.
- Datasource CRUD, mappings, restore, compare, columns, CSV import.
- Quick search CRUD and image upload.
- Followup rule CRUD.
- Tag list and value CRUD where an editable category exists.
- Notices immediate and scheduled lifecycle.
- Version create/update/upload/publish/report/revoke.
- Audit, analytics, desktop, customer, quick search, notices, followup read endpoints.

## Current Known Limits

- Harness intentionally does not claim real external-provider acceptance.
- Harness marks known placeholder endpoints as pass if HTTP/API envelope is valid; production gap audit still tracks placeholder behavior separately:
  - datasource mapping compare still returns manual placeholder text
  - datasource columns still returns fallback columns
  - datasource import logs still returns an empty list
- Java test suite still has no tests unless later added.
- Admin frontend is still absent unless later implemented.

## Latest Run

- Command:

```bash
wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && python3 scripts/acceptance_backend_api.py'
```

- Result: passed.
- Calls: 98 passed, 0 failed.
- Report: `.tools/acceptance/backend_api_acceptance_20260703104233.json`.
- Runtime: reused WSL backend at `http://127.0.0.1:8080` with `MOCK_EXTERNALS=true`.

## Validation Command

```bash
wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && python3 scripts/acceptance_backend_api.py'
```
