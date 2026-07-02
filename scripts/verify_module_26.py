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
    migration = read("src/main/resources/db/migration/V9__module_quick_search_items.sql")
    controller = read("src/main/java/com/privateflow/modules/quicksearch/QuickSearchController.java")
    repository = read("src/main/java/com/privateflow/modules/quicksearch/QuickSearchRepository.java")
    enum_java = read("src/main/java/com/privateflow/modules/quicksearch/ContentType.java")
    main_ts = read("desktop/src/main/main.ts")
    preload_ts = read("desktop/src/preload/preload.ts")
    app_vue = read("desktop/src/renderer/App.vue")
    config_ts = read("desktop/src/renderer/shared/config.ts")
    overlay_vue = read("desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue")
    store_ts = read("desktop/src/renderer/modules/quick-search/quickSearchStore.ts")
    types_ts = read("desktop/src/renderer/modules/quick-search/types.ts")
    progress = read("dev-progress/26_progress.md")

    assert_contains(migration, "CREATE TABLE IF NOT EXISTS quick_search_items", "quick search table")
    assert_contains(controller, '@GetMapping("/api/v1/quick-search/items")', "quick search API")
    assert_contains(repository, "WHERE is_enabled = 1", "enabled-only query")

    for value in ["TEMPLATE", "KNOWLEDGE", "LOCATION", "IMAGE", "MINI_PROGRAM"]:
        assert_contains(enum_java + types_ts + progress, value, f"content type {value}")

    assert_contains(main_ts, "globalShortcut.register", "global shortcut registration")
    assert_contains(main_ts, "CommandOrControl+Shift+F", "default shortcut")
    assert_contains(main_ts, "quicksearch:show", "quicksearch show IPC")
    assert_contains(main_ts, "quicksearch:hide", "quicksearch hide IPC")
    assert_contains(main_ts, "clipboard:write-image", "image clipboard IPC")
    assert_contains(main_ts, "net.fetch", "main-process image download")
    assert_contains(preload_ts, "writeClipboardImage", "preload image bridge")
    assert_contains(preload_ts, "onQuickSearchShow", "preload quicksearch show listener")
    assert_contains(app_vue, "QuickSearchOverlay", "quick search overlay mounted")
    assert_contains(store_ts, "'/api/v1/quick-search/items'", "frontend quick search API")

    for event in ["CONFIG_REFRESH", "network:offline", "network:online"]:
        assert_contains(overlay_vue + store_ts, event, f"event {event}")

    for token in [
        "quicksearchShortcut: 'CommandOrControl+Shift+F'",
        "quicksearchResultLimit: 10",
        "quicksearchAutoCloseS: 3",
        "quicksearchCacheRefreshOnStartup: true",
        "searchInputDebounceMs: 100",
    ]:
        assert_contains(config_ts, token, f"config {token}")

    for forbidden in ["search_history", "recent", "TODO", "FIXME", "待补充", "console.log"]:
        assert_not_contains(overlay_vue + store_ts + progress, forbidden, forbidden)
    assert_not_contains(store_ts, "fetch(item.imageUrl", "renderer image download")

    print("module 26 verification passed")


if __name__ == "__main__":
    main()
