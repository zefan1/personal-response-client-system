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
    service = read("desktop/src/renderer/modules/save-to-table/saveToTableService.ts")
    types = read("desktop/src/renderer/modules/save-to-table/types.ts")
    store = read("desktop/src/renderer/modules/customer-profile/customerProfileStore.ts")
    panel = read("desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue")
    config = read("desktop/src/renderer/shared/config.ts")
    progress = read("dev-progress/28_progress.md")

    for token in [
        "saveProfile",
        "syncProfileToTable",
        "recoverPendingSave",
        "cleanupExpiredPendingSaves",
        "`/api/v1/customers/${encodeURIComponent(input.phone)}`",
        "`/api/v1/customers/${encodeURIComponent(input.phone)}/save-to-table`",
        "PENDING_SAVE_PREFIX",
        "pending_saves:",
        "activeSaves",
        "status: 'BUSY'",
        "status: 'CONFLICT'",
        "status: 'GIVE_UP'",
        "saveMaxRetries",
        "saveRetryIntervalMs",
        "saveToTableTimeoutMs",
        "savePendingExpireHours",
    ]:
        assert_contains(service + types + store + config + progress, token, token)

    for token in [
        "saveToTableTimeoutMs: 15000",
        "saveRetryIntervalMs: 5000",
        "saveMaxRetries: 3",
        "savePendingExpireHours: 24",
        "tableSyncPrompt",
        "confirmTableSync",
        "skipTableSync",
        "上次编辑内容未保存成功",
        "是否同步到企微表格",
        "同步",
        "暂不",
    ]:
        assert_contains(service + config + store + panel + progress, token, token)

    assert_contains(store, "collectChangedFields", "changed fields only")
    assert_contains(store, "editingSnapshot", "conflict keeps edit fields")
    assert_contains(store, "sourceRowId", "has table row")
    assert_contains(store, "getPendingSave(profile.customer.phone)", "pending banner")
    assert_not_contains(service, "alert(", "blocking alert")
    assert_not_contains(service, "confirm(", "blocking confirm")
    for forbidden in ["TODO", "FIXME", "待补充", "console.log"]:
      assert_not_contains(service + store + panel + progress, forbidden, forbidden)

    print("module 28 verification passed")


if __name__ == "__main__":
    main()
