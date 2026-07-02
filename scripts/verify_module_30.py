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
    handler = read("desktop/src/renderer/modules/stage-suggestion/stageSuggestionHandler.ts")
    store = read("desktop/src/renderer/modules/customer-profile/customerProfileStore.ts")
    panel = read("desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue")
    types = read("desktop/src/renderer/modules/customer-profile/types.ts")
    app = read("desktop/src/renderer/App.vue")
    config = read("desktop/src/renderer/shared/config.ts")
    styles = read("desktop/src/renderer/styles.css")
    progress = read("dev-progress/30_progress.md")

    for token in [
        "initializeStageSuggestionHandler",
        "cleanupStageSuggestionHandler",
        "'PROFILE_SUGGESTIONS'",
        "'customer:selected'",
        "'stage:suggest'",
        "'stage:updated'",
        "pendingStageSuggestions",
        "emittedSuggestionIds",
        "fieldName === 'customerStage'",
        "stageOptionMatch: suggestion.stageOptionMatch !== false",
        "validOptions",
        "suggestionType: 'STAGE_CHANGE'",
        "confirmStageSuggestion",
        "ignoreStageSuggestion",
        "`/api/v1/customers/${encodeURIComponent(phone)}`",
        "fields: { customerStage: newStage }",
        "stageSuggestPendingTtlS: 300",
    ]:
        assert_contains(handler + store + panel + types + app + config + progress, token, token)

    for token in [
        "stage-change",
        "stage-label",
        "阶段建议",
        "stage-warning",
        "此阶段值不在表格当前可选范围内",
        "handleCustomerProfileLoaded(response.data)",
        "action === 'CONFIRM' ? await confirmStageSuggestion(item) : await ignoreStageSuggestion(item)",
    ]:
        assert_contains(store + panel + styles + progress, token, token)

    for forbidden in ["TODO", "FIXME", "待补充", "console.log"]:
        assert_not_contains(handler + store + panel + progress, forbidden, forbidden)
    for forbidden in ["localStorage", "indexedDB"]:
        assert_not_contains(handler, forbidden, forbidden)

    print("module 30 verification passed")


if __name__ == "__main__":
    main()
