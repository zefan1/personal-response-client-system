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
        "offlineApiFailCount: 3",
        "offlineWsDisconnectWaitS: 15",
        "onlineToastDurationMs: 2000",
        "recoverSyncTimeoutS: 30",
        "customerCacheLimit: 50",
    ])
    require("desktop/src/renderer/shared/offlineManager.ts", [
        "isOnline",
        "isWsConnected",
        "lastOnlineAt",
        "offlineReason",
        "OS_OFFLINE",
        "WS_AND_API_FAILED",
        "API_CONSECUTIVE_FAIL",
        "network:offline",
        "network:online",
        "ws:disconnected",
        "ws:reconnected",
        "ws:status-change",
        "registerOfflineCapability",
        "recordApiSuccess",
        "recordApiNetworkFailure",
        "offlineWsDisconnectWaitS",
        "offlineApiFailCount",
    ])
    require("desktop/src/renderer/shared/offlineDb.ts", [
        "cowork-desktop",
        "cowork_db_version",
        "customers_cache",
        "quick_search_cache",
        "followups_cache",
        "pending_saves",
        "alert_history",
        "edit_logs",
        "workbench_cache",
        "lastViewedAt",
        "nickname",
        "contentType",
        "shortcutCode",
        "leadType",
        "reminderType",
        "nextFollowupAt",
        "createdAt",
        "occurredAt",
        "timestamp",
        "autoIncrement: true",
    ])
    require("desktop/src/main/main.ts", [
        "app:get-online-status",
        "app:online-status",
        "net.isOnline()",
        "startOnlineStatusPolling",
    ])
    require("desktop/src/preload/preload.ts", [
        "getOnlineStatus",
        "onOnlineStatusChange",
        "app:get-online-status",
        "app:online-status",
    ])
    require("desktop/src/renderer/main.ts", [
        "initializeOfflineManager",
        ".finally(() =>",
        "createApp(App).mount('#app')",
    ])
    require("desktop/src/renderer/shared/apiClient.ts", [
        "recordApiSuccess",
        "recordApiNetworkFailure",
    ])
    require("desktop/src/renderer/shared/wsMessageBus.ts", [
        "ws:status-change",
        "RECONNECT",
        "lastMessageId",
    ])
    require("desktop/src/renderer/App.vue", [
        "OfflineStatusBar",
    ])
    require("desktop/src/renderer/modules/offline/OfflineStatusBar.vue", [
        "离线模式",
        "提醒服务暂不可用",
        "已恢复在线",
        "onlineToastDurationMs",
    ])
    require("dev-progress/33_progress.md", [
        "功能签收清单",
        "python scripts/verify_module_33.py",
        "npm run typecheck",
        "npm run build",
    ])
    forbid("desktop/src/renderer/shared/offlineManager.ts", [
        "navigator.onLine",
        "TODO",
        "FIXME",
        "console.log",
        "alert(",
        "confirm(",
    ])
    print("module 33 verification passed")


if __name__ == "__main__":
    main()
