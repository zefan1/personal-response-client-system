from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(relative: str) -> str:
    path = ROOT / relative
    if not path.exists():
        raise AssertionError(f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def assert_contains(text: str, needle: str, label: str) -> None:
    if needle not in text:
        raise AssertionError(f"Missing {label}: {needle}")


def assert_not_contains(text: str, needle: str, label: str) -> None:
    if needle in text:
        raise AssertionError(f"Forbidden {label}: {needle}")


def main() -> None:
    app_vue = read("desktop/src/renderer/App.vue")
    main_ts = read("desktop/src/main/main.ts")
    preload_ts = read("desktop/src/preload/preload.ts")
    api_client_ts = read("desktop/src/renderer/shared/apiClient.ts")
    agent_vue = read("desktop/src/renderer/modules/copy-backfill/CopyBackfillAgent.vue")
    store_ts = read("desktop/src/renderer/modules/copy-backfill/copyBackfillStore.ts")
    types_ts = read("desktop/src/renderer/modules/copy-backfill/types.ts")
    progress = read("dev-progress/22_progress.md")

    assert_contains(app_vue, "CopyBackfillAgent", "copy backfill agent mounted")
    assert_contains(main_ts, "ipcMain.handle('clipboard:write-text'", "clipboard text IPC handler")
    assert_contains(main_ts, "clipboard.writeText(text)", "Electron clipboard write")
    assert_contains(preload_ts, "writeClipboardText", "preload clipboard bridge")
    assert_contains(preload_ts, "ipcRenderer.invoke('clipboard:write-text'", "clipboard bridge invoke")

    for event_name in ["reply:selected", "suggestion:show"]:
        assert_contains(agent_vue + store_ts + progress, event_name, f"consumed event {event_name}")

    assert_contains(store_ts, "window.desktopBridge.writeClipboardText", "Electron clipboard write path")
    assert_contains(store_ts, "navigator.clipboard.writeText", "navigator clipboard fallback")
    assert_contains(store_ts, "document.execCommand('copy')", "execCommand clipboard fallback")
    assert_contains(store_ts, "'/api/v1/chat/send-confirm'", "send-confirm API")
    assert_contains(store_ts, "/suggestions/batch-resolve", "batch resolve API")
    assert_contains(store_ts, "AbortController", "send-confirm abort controller")
    assert_contains(api_client_ts, "signal?: AbortSignal", "api client external abort support")
    assert_contains(store_ts, "SUGGESTION_TOAST_AUTO_COLLAPSE_MS = 15000", "suggestion toast collapse default")
    assert_contains(store_ts, "selectedDirection: payload.isFallback ? 'SYSTEM_FALLBACK' : payload.direction", "fallback direction propagation")

    for field in ["text:", "direction:", "reason:", "phone:", "isFallback:"]:
        assert_contains(types_ts, field, f"reply selected payload field {field}")

    forbidden_api_text = store_ts.replace("send-confirm", "")
    for forbidden in ["/api/v1/chat/recognize", "/api/v1/chat/generate", "/api/v1/chat/regenerate"]:
        assert_not_contains(forbidden_api_text, forbidden, f"forbidden API {forbidden}")

    for forbidden in ["倒计时", "取消", "setInterval", "localStorage.setItem", "console.log", "TODO", "FIXME", "待补充"]:
        assert_not_contains(store_ts + agent_vue + progress, forbidden, forbidden)

    print("module 22 verification passed")


if __name__ == "__main__":
    main()
