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
    require("desktop/src/renderer/modules/abnormal-alert/types.ts", [
        "CUSTOMER_COMPLAINT",
        "CHURN_RISK",
        "ERROR",
        "WARN",
        "INFO",
        "acknowledged",
    ])
    require("desktop/src/renderer/modules/abnormal-alert/alertStore.ts", [
        "ABNORMAL_ALERT",
        "abnormal:alert",
        "k_alert_",
        "alertStore",
        "acknowledgeAlert",
        "alertHistoryMaxCount",
        "alertHistoryRetentionDays",
        "alertBellRefreshIntervalS",
        "K_PAYLOAD_INVALID",
        "K_INDEXEDDB_WRITE_FAILED",
        "K_INDEXEDDB_READ_FAILED",
        "K_INDEXEDDB_CLEANUP_FAILED",
        "K_EVENT_EMIT_FAILED",
    ])
    require("desktop/src/renderer/modules/abnormal-alert/alertHistoryDb.ts", [
        "indexedDB.open",
        "siliang_desktop",
        "alert_history",
        "createObjectStore",
        "createIndex('phone'",
        "createIndex('occurredAt'",
        "IDBKeyRange.upperBound",
    ])
    require("desktop/src/renderer/modules/abnormal-alert/AlertBell.vue", [
        "AlertBell",
        "unconfirmedCount",
        "已知晓",
        "查看全部历史",
    ])
    require("desktop/src/renderer/shared/config.ts", [
        "alertHistoryMaxCount: 50",
        "alertHistoryRetentionDays: 7",
        "alertBellRefreshIntervalS: 86400",
    ])
    require("desktop/src/renderer/App.vue", [
        "<AlertBell />",
        "initializeAbnormalAlertRouter",
        "cleanupAbnormalAlertRouter",
    ])
    require("desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue", [
        "abnormal:alert",
    ])
    require("desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue", [
        "abnormal:alert",
        "profileAlert",
        "profile-alert-banner",
    ])
    forbid("desktop/src/renderer/modules/abnormal-alert/alertStore.ts", [
        "localStorage",
        "console.log",
        "TODO",
        "FIXME",
        "alert(",
        "confirm(",
    ])
    forbid("desktop/src/renderer/modules/abnormal-alert/alertHistoryDb.ts", [
        "localStorage",
        "console.log",
        "TODO",
        "FIXME",
        "alert(",
        "confirm(",
    ])
    print("module 31 verification passed")


if __name__ == "__main__":
    main()
