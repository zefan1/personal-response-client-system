# Module 23 Desktop D Customer Search And Profile Card Progress

## Scope
- Module: 23 Desktop D Customer Search And Profile Card
- Responsibility: search customers, show candidate list, render profile card, generate replies from profile, edit profile, process AI suggestions.
- Strong dependencies: Backend A/D/E/B via H APIs, Desktop A recognition events, Desktop B customer selection, Desktop C send-confirm event.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] Calls `GET /api/v1/customers/search`.
- [x] Calls `GET /api/v1/customers/{phone}`.
- [x] Calls `POST /api/v1/chat/generate`.
- [x] Calls `PUT /api/v1/customers/{phone}`.
- [x] Calls `POST /api/v1/customers/{phone}/suggestions/batch-resolve`.
- [x] Consumes `recognize:multiple`.
- [x] Consumes `suggestion:show`.
- [x] Consumes `reply:send-confirmed`.
- [x] Consumes `stage:suggest`.
- [x] Consumes `stage:updated`.
- [x] Emits `customer:selected`.
- [x] Implements paste-immediate search.
- [x] Implements 300 ms debounced manual search.
- [x] Implements candidate list with up to 5 visible candidates.
- [x] Uses candidate display fields nickname, phone tail, leadType, assignedKeeper, lastFollowupAt, intendedStore.
- [x] Implements profile sections: intent, body, followup, AI suggestions, appointment.
- [x] Keeps edit conflict handling for `50-10002` without clearing edit fields.
- [x] Provides session-level section collapse state only.
- [x] Masks phone in UI.
- [x] Registers local cache defaults: search debounce `300`, result limit `10`, cache limit `50`, followup history visible `3`, offline banner `5`, edit log retention `7`.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_23.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
