# Module 21 Desktop B Reply Suggestion Panel Progress

## Scope
- Module: 21 Desktop B Reply Suggestion Panel
- Responsibility: consume recognition/generate results, render reply suggestions, support regenerate, fallback retry, help entry, profile suggestions, abnormal alerts.
- Strong dependencies: Backend H API/WebSocket, Backend B Skill gateway, Desktop A recognition events.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] Consumes `recognize:start`.
- [x] Consumes `recognize:result`.
- [x] Consumes `recognize:multiple`.
- [x] Consumes `recognize:image-failed`.
- [x] Consumes `recognize:timeout`.
- [x] Consumes `customer:selected`.
- [x] Consumes `help:timeout`.
- [x] Consumes WS `PROFILE_SUGGESTIONS`.
- [x] Consumes WS `ABNORMAL_ALERT`.
- [x] Emits `reply:selected` with `{ text, direction, reason, phone, isFallback }`.
- [x] Emits `suggestion:show`.
- [x] Emits `help:request`.
- [x] Calls `POST /api/v1/chat/regenerate`.
- [x] Does not call `POST /api/v1/chat/recognize`.
- [x] Does not directly operate clipboard.
- [x] Uses `SYSTEM_FALLBACK` for fallback branch.
- [x] Uses scene values `CHAT_RECOGNIZE`, `ACTIVE_REPLY`, `REGENERATE`, `OPENING`.
- [x] Keeps fallback retry interval default `10000`.
- [x] Keeps fallback max retries default `3`.
- [x] Keeps help timeout default `30`.
- [x] Keeps request timeout aligned at `15000`.
- [x] Clears skeleton and fallback retry timers on component unmount.
- [x] Does not store reply text or customer phone in localStorage.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_21.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
