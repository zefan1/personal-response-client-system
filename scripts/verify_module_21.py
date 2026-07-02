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
    config_ts = read("desktop/src/renderer/shared/config.ts")
    panel_vue = read("desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue")
    store_ts = read("desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts")
    types_ts = read("desktop/src/renderer/modules/reply-suggestions/types.ts")
    progress = read("dev-progress/21_progress.md")

    assert_contains(app_vue, "ReplySuggestionPanel", "reply panel mounted in app")

    for event_name in [
        "recognize:start",
        "recognize:result",
        "recognize:multiple",
        "recognize:image-failed",
        "recognize:timeout",
        "customer:selected",
        "help:timeout",
        "PROFILE_SUGGESTIONS",
        "ABNORMAL_ALERT",
    ]:
        assert_contains(panel_vue + store_ts + progress, event_name, f"consumed event {event_name}")

    for event_name in ["reply:selected", "suggestion:show", "help:request"]:
        assert_contains(store_ts + progress, event_name, f"emitted event {event_name}")

    assert_contains(store_ts, "'/api/v1/chat/regenerate'", "regenerate API")
    assert_not_contains(store_ts, "'/api/v1/chat/recognize'", "direct recognize API call")
    assert_not_contains(store_ts + panel_vue, "clipboard", "direct clipboard usage")

    for enum_value in ["SYSTEM_FALLBACK", "CHAT_RECOGNIZE", "ACTIVE_REPLY", "REGENERATE", "OPENING"]:
        assert_contains(store_ts + types_ts + progress, enum_value, f"enum {enum_value}")

    assert_contains(config_ts, "fallbackRetryIntervalMs: 10000", "fallback retry interval default")
    assert_contains(config_ts, "fallbackMaxRetries: 3", "fallback retry max default")
    assert_contains(config_ts, "helpTimeoutS: 30", "help timeout default")
    assert_contains(config_ts, "requestTotalTimeoutMs: 15000", "request timeout default")

    for token in ["STAGE_DURATIONS = [5000, 2500, 7500]", "window.setTimeout", "window.clearTimeout", "cleanupReplySuggestionStore"]:
        assert_contains(store_ts + panel_vue, token, f"timer lifecycle {token}")

    for token in ["text:", "direction:", "reason:", "phone:", "isFallback:"]:
        assert_contains(store_ts + types_ts, token, f"reply selected payload field {token}")

    desktop_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "desktop/src/renderer/modules/reply-suggestions").rglob("*")
        if path.suffix in {".ts", ".vue"}
    )
    assert_not_contains(desktop_sources, "localStorage.setItem", "reply or phone localStorage persistence")
    for forbidden in ["console.log", "TODO", "FIXME", "待补充"]:
        assert_not_contains(desktop_sources + progress, forbidden, forbidden)

    print("module 21 verification passed")


if __name__ == "__main__":
    main()
