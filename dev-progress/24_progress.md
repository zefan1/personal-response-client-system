# Module 24 Desktop E Followup List Progress

## Scope
- Module: 24 Desktop E Followup List And Reminders
- Responsibility: load today's followup list, group into four tabs, consume WS reminders, navigate to profile, emit batch template event.
- Strong dependencies: Backend A and Backend F through Backend H.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] Calls `GET /api/v1/followups/today`.
- [x] Consumes WS/event `FOLLOWUP_REMIND`.
- [x] Consumes WS/event `NEW_LEAD_ALERT`.
- [x] Groups reminder types into four tabs: `OVERDUE`, `DUE_TODAY`, `APPOINTMENT`, `NEW_LEAD`.
- [x] Emits `customer:selected` with `scene: "ACTIVE_REPLY"` and `sourceFrom: "FOLLOWUP_LIST"`.
- [x] Emits `batch:start` with `source: "FOLLOWUP_LIST"`.
- [x] Implements NewReminderBanner and row flash.
- [x] Implements multi-select, select all, invert, and batch action.
- [x] Keeps backend sort order; no second sort in frontend.
- [x] Handles load failure with retry and stale-data marker.
- [x] Registers `desktop.new_reminder_flash_ms` default `3000`.
- [x] Cleans event listeners on component unmount.
- [x] Does not modify customer data or call customer update APIs.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_24.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
