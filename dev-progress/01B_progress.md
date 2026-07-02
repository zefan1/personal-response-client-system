# 01B 后端B Skill 网关进度卡

## 基线来源

- 手册：`C:\Users\85314\Desktop\私域工具\01B_后端_Skill网关_开发实现手册.md`
- 公共契约：`C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- 依赖图：`C:\Users\85314\Desktop\私域工具\DEPENDENCIES.md`

## 当前生效基线

- 模块 B 不暴露 REST API，只提供 `generateReplies(SkillRequest)` 与 `extractProfile(ProfileExtractRequest)` Java 方法。
- scene 枚举只允许 `CHAT_RECOGNIZE / ACTIVE_REPLY / REGENERATE / PROFILE_EXTRACT / OPENING`。
- leadType 只使用 `TUAN_GOU / XIAN_SUO / PENDING / null`。
- 降级方向固定为 `SYSTEM_FALLBACK`，补足方向使用 `REPEATED_1 / REPEATED_2 / REPEATED_3`。
- 正常回复返回 3 条 suggestions；降级回复返回 1 条 suggestions。
- Skill 调用 0 次自动重试。
- `skill_call_logs.request_summary` 只保存脱敏摘要，不保存完整聊天记录。
- B 不发布 Spring Event，只订阅 `ConfigChangedEvent` 的 `skill.*` 变更。

## 功能签收清单

| 项 | 来源 | 状态 | 验证命令 |
|----|------|------|----------|
| SF-B01 scene 枚举 | SHARED_CONTRACTS §Skill 调用场景 | 已完成 | `python scripts/verify_module_b.py` |
| SF-B02 SkillRequest / SkillResponse 契约 | 01B §3 | 已完成 | `python scripts/verify_module_b.py` |
| SF-B03 ProfileExtractRequest / ProfileUpdates 契约 | 01B §3.2.2 | 已完成 | `python scripts/verify_module_b.py` |
| SF-B04 skill_call_logs / skill_scene_bindings / personality_tags DDL | 01B §6 | 已完成 | `python scripts/verify_module_b.py` |
| SF-B05 skill.* 17 项配置种子与热更新 | 01B §5 | 已完成 | `python scripts/verify_module_b.py` |
| SF-B06 请求构建：路由、Prompt、手机号后四位、A.getByPhone 补全 | 01B §4.1 | 已完成 | `python scripts/verify_module_b.py`; 正式：`mvn test` |
| SF-B07 Skill HTTP 客户端与 Mock 客户端 | 01B §4.2 | 已完成 | `python scripts/verify_module_b.py`; 正式：`mvn test` |
| SF-B08 响应解析：3 条补足/截断、profile_updates 透传 | 01B §4.3 | 已完成 | `python scripts/verify_module_b.py`; 正式：`mvn test` |
| SF-B09 滑动窗口熔断 CLOSED/OPEN/HALF_OPEN | 01B §4.4 | 已完成 | `python scripts/verify_module_b.py`; 正式：`mvn test` |
| SF-B10 降级与异步日志 | 01B §4.5/4.6 | 已完成 | `python scripts/verify_module_b.py`; 正式：`mvn test` |

## 已做关键假设

- 当前机器仍无 Java/Maven，正式编译测试需补 JDK 17 + Maven 后执行。
- 真实 Skill 系统未配置时，开发环境使用 `MOCK_EXTERNALS=true` 的 Mock Skill 客户端。
- `skill.system_prompt_format` / `skill.system_prompt_red_lines` 是 SHARED_CONTRACTS 后续运营 B 的命名；为兼容 01B 手册，本模块同时种子并读取 `skill.system_prompt_template` / `skill.red_lines`。

## 下一步

补 JDK 17/Maven 环境后执行 `mvn test`，再进入后端 D 客户匹配服务。
