# 断点 034：本地 readiness 纳入 LLM 场景配置检测

时间：2026-07-09 22:05  
状态：已完成

本轮目标：

- 继续不依赖用户配合的本地收口。
- 让本地检测能区分：
  - 本地数据库迁移/配置行是否完整。
  - 真实外部 provider key 是否尚未填写。
- 把五个 LLM 业务场景纳入 readiness，而不是只检测全局 LLM key。

处理结果：

- 更新 `scripts/verify_local_runtime_readiness.py`：
  - 新增 `llm.reply_generation.*`、`llm.profile_extraction.*`、`llm.followup_suggestion.*`、`llm.abnormal_detection.*`、`llm.summary.*` 配置行检测。
  - 新增 LLM 场景开关检测。
  - 新增 `llm_scene_routes` 场景路由表检测。
  - 报告仍只输出 `<set>/<empty>/<missing>`，不泄露真实 key。
  - 普通本地测试不会因为真实 key 缺失失败。
  - 使用 `--require-real-externals` 时，真实 LLM/表格 key 缺失会变成失败项。
- 更新 `scripts/verify_real_external_readiness.py`：
  - 新增 LLM 真实 HTTP client 检查。
  - 新增 LLM 场景路由 runtime 检查。
  - 新增 LLM 场景枚举检查。
  - 新增 LLM 调用日志检查。
  - 新增全局 LLM key 和五个业务开关配置键检查。
- 重启本地后端，触发 Flyway 新迁移落库：
  - 当前本地后端已重新启动。
  - 本地数据库已补齐 V62-V65 的 LLM 场景配置行。

验证结果：

- Python 脚本编译通过：
  - `python -m py_compile scripts\verify_local_runtime_readiness.py scripts\verify_real_external_readiness.py`
- 真实外部代码 readiness 通过：
  - `python scripts\verify_real_external_readiness.py`
  - `mockExternalsFalseReady=true blockers=0`
- 普通本地 runtime readiness 通过：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=15/18`
- 强制真实外部配置 readiness 按预期失败：
  - `python scripts\verify_local_runtime_readiness.py --require-real-externals`
  - 当前缺少：
    - `llm.api_base_url`
    - `llm.api_key`
    - `llm.model`
    - `table.api_base_url`
    - `table.api_key`

当前结论：

- 本地数据库连接和迁移已恢复到最新 LLM 场景配置状态。
- 当前不用真实 key 也能继续做本地功能测试。
- 真正进入真实 LLM/企微表格联调前，需要补齐上述外部配置。

下一步：

- 继续做不依赖真实 key 的侧边栏/后台收口检查。
- 后续用户提供真实 LLM provider 和企微表格配置后，再打开对应业务开关做真实效果测试。
