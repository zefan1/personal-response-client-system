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
    controller = read("src/main/java/com/privateflow/modules/match/web/CustomerController.java")
    batch_request = read("src/main/java/com/privateflow/modules/match/CustomerBatchRequest.java")
    batch_response = read("src/main/java/com/privateflow/modules/match/CustomerBatchResponse.java")
    config = read("desktop/src/renderer/shared/config.ts")
    app = read("desktop/src/renderer/App.vue")
    overlay = read("desktop/src/renderer/modules/batch-template/BatchTemplateOverlay.vue")
    store = read("desktop/src/renderer/modules/batch-template/batchTemplateStore.ts")
    types = read("desktop/src/renderer/modules/batch-template/types.ts")
    followup = read("desktop/src/renderer/modules/followup-list/followupListStore.ts")
    styles = read("desktop/src/renderer/styles.css")
    progress = read("dev-progress/27_progress.md")

    assert_contains(controller, '@PostMapping("/batch")', "customers batch endpoint")
    assert_contains(controller, "phones.size() > 100", "backend batch limit")
    assert_contains(controller, "CUSTOMER_NOT_FOUND", "missing customer skip")
    assert_contains(batch_request, "List<String> phones", "batch request phones")
    assert_contains(batch_response, "List<CustomerProfileView> customers", "batch response profiles")

    for token in [
        "batchMaxCustomers: 100",
        "batchCustomerBatchTimeoutMs: 3000",
        "BatchTemplateOverlay",
        "batch:start",
        "'/api/v1/customers/batch'",
        "'/api/v1/quick-search/items?contentType=TEMPLATE&enabled=true'",
        "'/api/v1/chat/send-confirm'",
        "selectedDirection: 'BATCH_TEMPLATE'",
        "source: 'BATCH_TEMPLATE'",
        "window.desktopBridge.writeClipboardText",
        "pauseBatchTemplate",
        "resumeBatchTemplate",
        "fillTemplate",
        "localLogs",
    ]:
        assert_contains(config + app + overlay + store + followup + styles + progress, token, token)

    for variable in ["客户昵称", "预约时间", "预约门店", "预约项目", "管家名", "意向门店", "手机后4位"]:
        assert_contains(store + progress, variable, f"template variable {variable}")

    for forbidden in ["TODO", "FIXME", "待补充", "console.log", "setTimeout(() => nextBatchCustomer"]:
        assert_not_contains(overlay + store + progress, forbidden, forbidden)

    print("module 27 verification passed")


if __name__ == "__main__":
    main()
