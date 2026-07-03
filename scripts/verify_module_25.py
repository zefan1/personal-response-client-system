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
    followup_panel = read("desktop/src/renderer/modules/followup-list/FollowupListPanel.vue")
    panel_vue = read("desktop/src/renderer/modules/new-lead-toast/NewLeadToastAgent.vue")
    store_ts = read("desktop/src/renderer/modules/new-lead-toast/newLeadToastStore.ts")
    bridge_ts = read("desktop/src/renderer/shared/desktopBridge.ts")
    types_ts = read("desktop/src/renderer/modules/new-lead-toast/types.ts")
    progress = read("dev-progress/25_progress.md")

    assert_contains(app_vue, "NewLeadToastAgent", "new lead toast agent mounted")
    assert_contains(panel_vue + store_ts, "NEW_LEAD_ALERT", "NEW_LEAD_ALERT consumption")
    assert_contains(store_ts, "toast:show", "toast show event")
    assert_contains(store_ts, "customer:selected", "customer selected event")
    assert_contains(store_ts, "scene: 'OPENING'", "OPENING scene")
    assert_contains(store_ts, "sourceFrom: 'NEW_LEAD'", "NEW_LEAD source")
    assert_contains(store_ts, "followup:switch-tab", "followup switch event")
    assert_contains(store_ts, "tab: 'NEW_LEAD'", "new lead tab")
    assert_contains(followup_panel, "followup:switch-tab", "followup panel listens switch event")
    assert_contains(store_ts + types_ts, "phoneFull", "phoneFull copy field")
    assert_contains(store_ts, "writeClipboardText(phone)", "clipboard bridge use")
    assert_contains(bridge_ts, "window.desktopBridge.writeClipboardText", "electron clipboard bridge use")
    assert_contains(config_ts, "toastMaxCount: 3", "toast max default")
    assert_contains(config_ts, "toastNewLeadDismissS: 15", "toast dismiss default")
    assert_contains(config_ts, "newReminderFlashMs: 3000", "flash default")
    assert_contains(store_ts, "window.setTimeout", "toast timer")
    assert_contains(store_ts, "window.clearTimeout", "toast timer cleanup")
    assert_contains(panel_vue, "onBeforeUnmount", "listener cleanup")

    for forbidden in ["/api/v1/", "postJson", "getJson", "putJson", "localStorage.setItem", "TODO", "FIXME", "待补充", "console.log"]:
        assert_not_contains(store_ts + panel_vue + progress, forbidden, forbidden)

    print("module 25 verification passed")


if __name__ == "__main__":
    main()
