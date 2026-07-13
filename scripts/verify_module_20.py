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
    package_json = read("desktop/package.json")
    main_ts = read("desktop/src/main/main.ts")
    preload_ts = read("desktop/src/preload/preload.cts")
    config_ts = read("desktop/src/renderer/shared/config.ts")
    api_client_ts = read("desktop/src/renderer/shared/apiClient.ts")
    ws_bus_ts = read("desktop/src/renderer/shared/wsMessageBus.ts")
    event_bus_ts = read("desktop/src/renderer/shared/eventBus.ts")
    types_ts = read("desktop/src/renderer/modules/chat-recognition/types.ts")
    store_ts = read("desktop/src/renderer/modules/chat-recognition/recognitionStore.ts")
    panel_vue = read("desktop/src/renderer/modules/chat-recognition/ChatRecognitionPanel.vue")
    progress = read("dev-progress/20_progress.md")

    for file_name in [
        "desktop/index.html",
        "desktop/vite.config.ts",
        "desktop/tsconfig.json",
        "desktop/tsconfig.main.json",
        "desktop/src/renderer/main.ts",
        "desktop/src/renderer/App.vue",
    ]:
        read(file_name)

    assert_contains(package_json, '"electron"', "Electron dependency")
    assert_contains(package_json, '"vue"', "Vue dependency")
    assert_contains(package_json, '"vue-tsc"', "Vue typecheck dependency")

    assert_contains(main_ts, "ipcMain.handle('screenshot:capture'", "screenshot capture IPC handler")
    assert_contains(main_ts, "desktopCapturer.getSources", "Electron desktop capturer")
    assert_contains(main_ts, "types: ['screen']", "screen capture source")
    assert_contains(main_ts, "thumbnailSize: { width: 1920, height: 1080 }", "capture thumbnail size")
    assert_contains(main_ts, "CAPTURE_FAILED", "capture failed error")
    assert_contains(main_ts, "No screen source detected", "no screen source error")
    assert_contains(main_ts, "screenTitle: selected.name", "captured screen title")
    assert_contains(main_ts, "clipboard.readImage()", "clipboard polling")
    assert_contains(main_ts, "clipboardPollIntervalMs: 500", "clipboard poll default")
    assert_contains(main_ts, "clipboardMd5CacheSize: 5", "clipboard MD5 cache default")
    assert_contains(main_ts, "clipboardMinImageDimension: 200", "clipboard min image default")
    assert_contains(main_ts, "clipboardImageTextCoverMs: 2000", "image text cover default")
    assert_contains(main_ts, "requestTotalTimeoutMs: 15000", "request timeout default")
    assert_contains(main_ts, "mainWindow?.webContents.send('clipboard:new-image'", "clipboard image IPC emit")
    assert_contains(main_ts, "crypto.createHash('md5')", "clipboard MD5 de-duplication")

    assert_contains(preload_ts, "captureScreenshot", "preload screenshot bridge")
    assert_contains(preload_ts, "onClipboardImage", "preload clipboard bridge")
    assert_contains(preload_ts, "ipcRenderer.invoke('screenshot:capture')", "renderer screenshot invoke")
    assert_contains(preload_ts, "ipcRenderer.on('clipboard:new-image'", "renderer clipboard listener")

    for source in ["BUTTON_CLICK", "CLIPBOARD_SCREENSHOT", "CLIPBOARD_TEXT"]:
        assert_contains(types_ts, source, f"source enum {source}")

    assert_contains(config_ts, "requestTotalTimeoutMs: 15000", "renderer request timeout default")
    assert_contains(store_ts, "'/api/v1/chat/recognize'", "chat recognize REST path")
    assert_contains(api_client_ts, "AbortController", "request timeout controller")
    assert_contains(ws_bus_ts, "IMAGE_SERVICE_STATUS", "WS service status message")
    assert_contains(ws_bus_ts, "image:status-changed", "status event mapping")

    for event_name in [
        "recognize:start",
        "recognize:result",
        "recognize:multiple",
        "recognize:image-failed",
        "recognize:timeout",
    ]:
        assert_contains(store_ts + event_bus_ts + progress, event_name, f"event {event_name}")

    for error_code in ["30-10001", "30-10002", "80-10002"]:
        assert_contains(store_ts, error_code, f"backend error branch {error_code}")
    for error_code in ["CAPTURE_FAILED"]:
        assert_contains(main_ts + panel_vue, error_code, f"capture error branch {error_code}")

    assert_contains(store_ts, "imageServiceStatus === 'DOWN'", "explicit DOWN handling")
    assert_contains(panel_vue, "state.imageServiceStatus === 'DOWN'", "capture disable only on DOWN")

    desktop_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "desktop/src").rglob("*")
        if path.suffix in {".ts", ".vue"}
    )
    for forbidden in ["writeFile", "appendFile", "createWriteStream", "console.log(imageBase64", "console.log(content.textMessage"]:
        assert_not_contains(desktop_sources, forbidden, "screenshot/raw data persistence or logging")
    for forbidden in ["TODO", "FIXME"]:
        assert_not_contains(desktop_sources + progress, forbidden, "unfinished marker")

    print("module 20 verification passed")


if __name__ == "__main__":
    main()
