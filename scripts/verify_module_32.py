from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, tokens: list[str]) -> None:
    text = read(path)
    missing = [token for token in tokens if token not in text]
    if missing:
        raise AssertionError(f"{path} missing: {', '.join(missing)}")


def forbid(path: str, tokens: list[str]) -> None:
    text = read(path)
    found = [token for token in tokens if token in text]
    if found:
        raise AssertionError(f"{path} forbidden tokens: {', '.join(found)}")


def main() -> None:
    require("desktop/src/renderer/shared/config.ts", [
        "workbenchRefreshIntervalS: 300",
        "workbenchFollowupListLimit: 5",
        "workbenchNewLeadListLimit: 3",
        "workbenchMaxNotices: 3",
    ])
    require("desktop/src/renderer/App.vue", [
        "WorkbenchPanel",
    ])
    require("desktop/src/renderer/modules/workbench/workbenchStore.ts", [
        "/api/v1/followups/today",
        "NEW_LEAD",
        "OVERDUE",
        "DUE_TODAY",
        "APPOINTMENT",
        "TUAN_GOU",
        "XIAN_SUO",
        "PENDING",
        "sourceFrom: 'DASHBOARD'",
        "followupDataDirty",
        "dismissedNoticeIds",
        "workbenchRefreshIntervalS",
        "workbenchFollowupListLimit",
        "workbenchNewLeadListLimit",
        "workbenchMaxNotices",
        "newLeadToastState",
        "workbench:capture-chat",
        "quick-search:show",
        "followup:switch-tab",
    ])
    require("desktop/src/renderer/modules/workbench/WorkbenchPanel.vue", [
        "工作台",
        "今日跟进",
        "新客资",
        "识别聊天",
        "快线模板",
        "批量发模板",
        "stage:updated",
        "FOLLOWUP_REMIND",
        "SYSTEM_NOTICE",
        "NEW_LEAD_ALERT",
    ])
    require("desktop/src/renderer/modules/chat-recognition/ChatRecognitionPanel.vue", [
        "workbench:capture-chat",
        "captureFromWindow",
    ])
    require("desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue", [
        "quick-search:show",
        "showQuickSearch",
    ])
    require("dev-progress/32_progress.md", [
        "功能签收清单",
        "python scripts/verify_module_32.py",
    ])
    workbench_sources = "\n".join(
        path.read_text(encoding="utf-8")
        for path in (ROOT / "desktop/src/renderer/modules/workbench").rglob("*")
        if path.suffix in {".ts", ".vue"}
    )
    forbid_text = [
        "localStorage.setItem",
        "indexedDB.open",
        "setInterval",
        "console.log",
        "TODO",
        "FIXME",
        "alert(",
        "confirm(",
    ]
    for token in forbid_text:
        if token in workbench_sources:
            raise AssertionError(f"workbench forbidden token: {token}")
    print("module 32 verification passed")


if __name__ == "__main__":
    main()
