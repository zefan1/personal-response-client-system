# Module 22 Desktop C Copy Backfill Progress

## Scope
- Module: 22 Desktop C Copy Backfill
- Responsibility: consume copy events, write reply text to system clipboard, fire send-confirm, show profile suggestion toast.
- Strong dependencies: Desktop B `reply:selected` and `suggestion:show`, Backend H `send-confirm`, Backend E batch resolve endpoint.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] Consumes `reply:selected`.
- [x] Consumes `suggestion:show`.
- [x] Adds IPC `clipboard:write-text`.
- [x] Uses Electron `clipboard.writeText`.
- [x] Falls back to `navigator.clipboard.writeText`.
- [x] Falls back to `document.execCommand('copy')`.
- [x] Calls `POST /api/v1/chat/send-confirm`.
- [x] Calls `POST /api/v1/customers/{phone}/suggestions/batch-resolve`.
- [x] Does not call recognize/generate/regenerate APIs.
- [x] Does not implement countdown or cancel button.
- [x] Treats send-confirm as fire-and-forget and silent on failure.
- [x] Aborts previous pending send-confirm when a newer copy event arrives.
- [x] Suggestion toast auto-collapses after `15000` ms.
- [x] Clears toast timer and aborts pending send-confirm on unmount.
- [x] Does not store reply text or phone in localStorage.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_22.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
