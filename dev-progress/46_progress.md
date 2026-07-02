# Module 46 Progress - 运营G 客户标签与分层管理

## Sources
- Manual: `C:\Users\85314\Desktop\私域工具\46_运营G_客户标签与分层管理_开发实现手册.md`
- Shared contracts: `/admin/api/v1/`, `ConfigChangedEvent`, `UPDATE_TAG`, customer camelCase fields.
- Existing consumer: backend B `SkillRequestBuilder` prompt placeholder `{{available_tags}}`.

## Implementation
- [x] Added `tag_categories` and `tag_values` migration.
- [x] Seeded builtin categories: `personality_type`, `body_concerns`, `worries`, `intent_level`.
- [x] Seeded builtin values including `LOYALIST`, `PEACEMAKER`, `DIASTASIS_RECTI`, `HIGH`, `MEDIUM`, `LOW`, `PENDING`.
- [x] Added `GET /admin/api/v1/tags/categories`.
- [x] Added category create/update/delete APIs.
- [x] Added tag value create/update/delete/toggle APIs.
- [x] `boundField` is validated against `Customer` bean properties by reflection.
- [x] One category per `boundField`.
- [x] `tagValue` format is `[A-Z0-9_]{1,50}` and is ignored on update.
- [x] Tag value deletion checks customer usage across `personality_type`, `body_concerns`, `worries`, and the bound field.
- [x] Builtin categories cannot be deleted.
- [x] Tag changes write audit action `UPDATE_TAG`.
- [x] Tag changes publish `ConfigChangedEvent("tag_config")` and WS `CONFIG_REFRESH`.
- [x] Added `TagCacheService.getAllEnabledTags()`, `getTagsByCategory()`, and `refresh()`.
- [x] Skill prompt now reads available tags from `TagCacheService` instead of only legacy `personality_tags`.

## Downstream Consumer Check
- `SkillRequestBuilder` still fills `{{available_tags}}`, but now groups enabled values from `tag_categories/tag_values`.
- Legacy `PersonalityTagRepository` remains present for module B historical verifier compatibility.
- Customer data is not modified when tags change or are disabled.

## Validation Commands
- `python scripts\verify_module_46.py`
- `python scripts\verify_module_b.py`
- Full verifier chain through 46.
- `git diff --check`

## Limits
- Java compiler/Maven are unavailable on this machine, so Java build validation cannot be run here.
- Seed display names are English ASCII to avoid source/migration encoding drift; operations can update display names later.
