# Module 20 Desktop A Chat Recognition Progress

## Scope
- Module: 20 Desktop A Chat Recognition
- Responsibility: capture chat input, trigger backend recognition, dispatch recognition events.
- Strong dependencies: Backend H API/WebSocket layer, Backend C image recognition channel through H, Backend D matching through H.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] IPC `screenshot:capture` from renderer to main.
- [x] IPC `clipboard:new-image` from main to renderer.
- [x] REST `POST /api/v1/chat/recognize` with 15000 ms timeout.
- [x] WS consumes `IMAGE_SERVICE_STATUS` and maps it to `image:status-changed`.
- [x] Source enum values: `BUTTON_CLICK`, `CLIPBOARD_SCREENSHOT`, `CLIPBOARD_TEXT`.
- [x] Emits `recognize:start`.
- [x] Emits `recognize:result`.
- [x] Emits `recognize:multiple`.
- [x] Emits `recognize:image-failed`.
- [x] Emits `recognize:timeout`.
- [x] Handles `CAPTURE_FAILED`, no screen source, `30-10001`, `30-10002`, `80-10002`, and timeout branches.
- [x] Clipboard poll interval default `500`.
- [x] Clipboard MD5 cache size default `5`.
- [x] Clipboard minimum image dimension default `200`.
- [x] Clipboard image-text cover window default `2000`.
- [x] Request total timeout default `15000`.
- [x] Does not persist screenshots to disk.
- [x] Does not log image base64, chat raw text, or plaintext phone.
- [x] Only disables screenshot on explicit `IMAGE_SERVICE_STATUS` `DOWN`.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_20.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
