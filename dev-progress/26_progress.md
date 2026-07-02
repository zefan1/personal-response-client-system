# Module 26 Desktop F Quick Search Progress

## Scope
- Module: 26 Desktop F Quick Search
- Responsibility: fetch quick search items, show searchable overlay, copy text/image content, refresh on config/network events.
- Strong dependencies: Backend H API route `/api/v1/quick-search/items`; content managed by future operation module D.
- Status: implemented, pending repeatable validation and checkpoint commit.

## Contract Checklist
- [x] Backend table `quick_search_items`.
- [x] Backend `GET /api/v1/quick-search/items`.
- [x] Content type enum values `TEMPLATE`, `KNOWLEDGE`, `LOCATION`, `IMAGE`, `MINI_PROGRAM`.
- [x] Frontend consumes `GET /api/v1/quick-search/items`.
- [x] Main process registers `CommandOrControl+Shift+F`.
- [x] Main process emits `quicksearch:show`.
- [x] Renderer invokes `quicksearch:hide`.
- [x] IPC `clipboard:write-image`.
- [x] Reuses text clipboard bridge.
- [x] Consumes `CONFIG_REFRESH`, `network:offline`, `network:online`.
- [x] Filters by `ALL`, `TUAN_GOU`, `XIAN_SUO`, `GENERAL`.
- [x] Uses defaults: shortcut `CommandOrControl+Shift+F`, result limit `10`, auto close `3`, startup refresh `true`, input debounce `100`.
- [x] Does not store search history.
- [x] Does not download image in renderer.

## Validation Commands
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_26.py
```

```powershell
cd "C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop"; npm run typecheck; npm run build
```
