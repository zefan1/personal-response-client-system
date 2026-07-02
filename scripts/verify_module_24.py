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
    panel_vue = read("desktop/src/renderer/modules/followup-list/FollowupListPanel.vue")
    store_ts = read("desktop/src/renderer/modules/followup-list/followupListStore.ts")
    types_ts = read("desktop/src/renderer/modules/followup-list/types.ts")
    progress = read("dev-progress/24_progress.md")

    assert_contains(app_vue, "FollowupListPanel", "followup panel mounted")
    assert_contains(store_ts, "'/api/v1/followups/today'", "today followup API")

    for event in ["FOLLOWUP_REMIND", "NEW_LEAD_ALERT", "customer:selected", "batch:start"]:
        assert_contains(panel_vue + store_ts + progress, event, f"event {event}")

    for reminder_type in ["OVERDUE", "DUE_TODAY", "APPOINTMENT", "NEW_LEAD"]:
        assert_contains(panel_vue + store_ts + types_ts, reminder_type, f"reminder type {reminder_type}")

    assert_contains(store_ts, "scene: 'ACTIVE_REPLY'", "customer selected scene")
    assert_contains(store_ts, "sourceFrom: 'FOLLOWUP_LIST'", "customer selected source")
    assert_contains(store_ts, "source: 'FOLLOWUP_LIST'", "batch source")
    assert_contains(config_ts, "newReminderFlashMs: 3000", "new reminder flash default")
    assert_contains(panel_vue, "onBeforeUnmount", "listener lifecycle cleanup")
    assert_contains(panel_vue, "new-reminder-banner", "new reminder banner")
    assert_contains(panel_vue, "selectAllActiveFollowups", "select all")
    assert_contains(panel_vue, "invertActiveFollowupSelection", "invert selection")
    assert_contains(panel_vue, "startBatchTemplate", "batch action")
    assert_contains(store_ts, "stale = followupListState.loaded", "stale data marker")

    for forbidden in ["/api/v1/customers/", "putJson", "POST /api/v1/chat/generate", "setInterval", "TODO", "FIXME", "待补充", "console.log"]:
        assert_not_contains(store_ts + panel_vue + progress, forbidden, forbidden)

    print("module 24 verification passed")


if __name__ == "__main__":
    main()
