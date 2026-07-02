# Module 25 Desktop E+ New Lead Toast Progress

## Scope
- Module: 25 Desktop E+ New Lead Toast
- Responsibility: consume `NEW_LEAD_ALERT`, show non-blocking toasts, copy full phone, trigger opening reply flow, collapse overflow to followup tab.
- Strong dependencies: Backend F WS via H, Desktop D/B through `customer:selected`, Desktop E through `followup:switch-tab`.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] Consumes `NEW_LEAD_ALERT`.
- [x] Emits `toast:show`.
- [x] Emits `customer:selected` with `scene: "OPENING"` and `sourceFrom: "NEW_LEAD"`.
- [x] Emits `followup:switch-tab` with `{ tab: "NEW_LEAD" }`.
- [x] Uses `phoneFull` for clipboard copy and keeps UI on masked `phone`.
- [x] Uses existing `clipboard:write-text` bridge.
- [x] Keeps visible toast queue capped by `desktop.toast_max_count` default `3`.
- [x] Keeps auto dismiss default `desktop.toast_new_lead_dismiss_s` as `15`.
- [x] Keeps `desktop.new_reminder_flash_ms` shared default `3000`.
- [x] Does not call REST APIs directly.
- [x] Clears toast timers and event listeners on unmount.
- [x] Does not maintain an independent new-lead list.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_25.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
