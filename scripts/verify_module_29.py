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
    help_service = read("src/main/java/com/privateflow/modules/api/help/HelpService.java")
    request_payload = read("src/main/java/com/privateflow/modules/api/help/HelpRequestPayload.java")
    resolve_payload = read("src/main/java/com/privateflow/modules/api/help/HelpResolvePayload.java")
    reply_payload = read("src/main/java/com/privateflow/modules/api/help/HelpReplyPayload.java")
    suggestion_payload = read("src/main/java/com/privateflow/modules/api/help/HelpSuggestionPayload.java")
    ws_push = read("src/main/java/com/privateflow/modules/api/ws/WsPushService.java")
    account_repo = read("src/main/java/com/privateflow/modules/api/auth/AccountRepository.java")
    app = read("desktop/src/renderer/App.vue")
    config = read("desktop/src/renderer/shared/config.ts")
    panel = read("desktop/src/renderer/modules/help-mode/HelpModeAgent.vue")
    store = read("desktop/src/renderer/modules/help-mode/helpModeStore.ts")
    types = read("desktop/src/renderer/modules/help-mode/types.ts")
    reply_store = read("desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts")
    reply_panel = read("desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue")
    progress = read("dev-progress/29_progress.md")

    for token in [
        "clientMessage",
        "aiSuggestions",
        "keeperNote",
        "helperReplies",
        "HelpReplyPayload",
        "HelpSuggestionPayload",
        "HELP_REQUEST",
        "HELP_RESPONSE",
        "HELP_OFFLINE_REPLAY",
        "ASK_FOR_HELP",
        "RESOLVE_HELP",
        "leaderOnline",
        "forwarded",
        "noFallbackAvailable",
        "findEnabledByRole",
        "isOnline",
    ]:
        assert_contains(help_service + request_payload + resolve_payload + reply_payload + suggestion_payload + ws_push + account_repo + progress, token, token)

    for token in [
        "HelpModeAgent",
        "'help:request'",
        "'help:timeout'",
        "'help:pending'",
        "'help:resolved'",
        "'HELP_REQUEST'",
        "'HELP_RESPONSE'",
        "'HELP_OFFLINE_REPLAY'",
        "'/api/v1/help/request'",
        "'/api/v1/help/resolve'",
        "reason: 'HELP_REPLY'",
        "CONFIRMED",
        "MODIFIED",
        "ORIGINAL",
        "helpOfflineExpireHours: 4",
        "helpMaxReplies: 3",
        "activeHelpId",
        "等待组长回复...",
    ]:
        assert_contains(app + config + panel + store + types + reply_store + reply_panel + progress, token, token)

    for forbidden in ["TODO", "FIXME", "待补充", "console.log", "setTimeout(() => eventBus.emit('help:timeout'"]:
        assert_not_contains(panel + store + reply_store + progress, forbidden, forbidden)

    print("module 29 verification passed")


if __name__ == "__main__":
    main()
