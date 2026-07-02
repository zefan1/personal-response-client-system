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
    api_client_ts = read("desktop/src/renderer/shared/apiClient.ts")
    panel_vue = read("desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue")
    store_ts = read("desktop/src/renderer/modules/customer-profile/customerProfileStore.ts")
    types_ts = read("desktop/src/renderer/modules/customer-profile/types.ts")
    progress = read("dev-progress/23_progress.md")

    assert_contains(app_vue, "CustomerProfilePanel", "customer profile panel mounted")
    assert_contains(api_client_ts, "getJson", "GET client helper")
    assert_contains(api_client_ts, "putJson", "PUT client helper")

    for api in [
        "/api/v1/customers/search",
        "/api/v1/customers/${encodeURIComponent(phone)}",
        "/api/v1/chat/generate",
        "/api/v1/customers/${encodeURIComponent(customer.phone)}",
        "/suggestions/batch-resolve",
    ]:
        assert_contains(store_ts, api, f"API {api}")

    for event in [
        "recognize:multiple",
        "suggestion:show",
        "reply:send-confirmed",
        "stage:suggest",
        "stage:updated",
        "customer:selected",
    ]:
        assert_contains(panel_vue + store_ts + progress, event, f"event {event}")

    for enum_value in ["SEARCH", "CANDIDATE_LIST", "PROFILE_CARD", "CANDIDATE_DISMISSED", "ACTIVE_REPLY", "CHAT_RECOGNIZE"]:
        assert_contains(store_ts + types_ts, enum_value, f"enum {enum_value}")

    for token in [
        "searchDebounceMs: 300",
        "searchResultLimit: 10",
        "customerCacheLimit: 50",
        "followupHistoryVisible: 3",
        "profileCacheOfflineBannerS: 5",
        "editLogRetentionDays: 7",
    ]:
        assert_contains(config_ts, token, f"config {token}")

    for field in ["nickname", "phone", "leadType", "assignedKeeper", "lastFollowupAt", "intendedStore"]:
        assert_contains(panel_vue + types_ts, field, f"candidate field {field}")

    for section in ["意向与购买", "身体情况", "跟进历史", "AI 更新建议", "预约信息"]:
        assert_contains(panel_vue, section, f"profile section {section}")

    assert_contains(panel_vue, "@paste", "paste immediate search")
    assert_contains(store_ts, "window.setTimeout", "debounce timer")
    assert_contains(store_ts, "response.errorCode === '50-10002'", "edit conflict handling")
    assert_contains(panel_vue, "maskPhone", "phone masking")

    for forbidden in ["TODO", "FIXME", "待补充", "console.log"]:
        assert_not_contains(panel_vue + store_ts + progress, forbidden, forbidden)

    print("module 23 verification passed")


if __name__ == "__main__":
    main()
