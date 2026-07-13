# 断点 037：live provider acceptance 纳入 LLM 自动验收

时间：2026-07-09 22:30  
状态：已完成

本轮目标：

- 补齐真实 provider 自动验收脚本中的 LLM 缺口。
- 后续用户提供真实 LLM key 后，可以通过脚本自动创建 LLM 环境并测试连接。

处理结果：

- 更新 `scripts/acceptance_real_external_live.py`：
  - 新增必填环境变量：
    - `PDA_LIVE_LLM_BASE_URL`
    - `PDA_LIVE_LLM_API_KEY`
    - `PDA_LIVE_LLM_MODEL`
  - 新增可选环境变量：
    - `PDA_LIVE_LLM_PROTOCOL`
    - `PDA_LIVE_LLM_TIMEOUT_MS`
    - `PDA_LIVE_LLM_TEMPERATURE`
    - `PDA_LIVE_LLM_MAX_TOKENS`
  - live 验收会写入全局 LLM 配置：
    - `llm.api_base_url`
    - `llm.api_key`
    - `llm.model`
  - live 验收会创建 `llm` environment。
  - live 验收会调用：
    - `POST /admin/api/v1/llm-environments/{id}/test`
  - LLM 测试失败时，报告会标记 live acceptance 失败。
- 更新 `dev-progress/real_provider_acceptance_runbook.md`：
  - 配置真实 key 后的自动验收已包含 LLM。

验证结果：

- Python 脚本编译通过：
  - `python -m py_compile scripts\acceptance_real_external_live.py scripts\acceptance_real_external_local.py`
- 无真实环境变量时，live acceptance 按预期失败并列出缺失项：
  - `python scripts\acceptance_real_external_live.py`
  - `missingEnv=9`
  - 缺少 Skill、Image、LLM、Table 的真实配置。
- 真实外部代码 readiness 仍通过：
  - `python scripts\verify_real_external_readiness.py`
  - `mockExternalsFalseReady=true blockers=0`

当前结论：

- 后续进入真实 provider 联调时，LLM 已经纳入自动验收路径。
- 当前没有真实 key，因此 live acceptance 失败是预期结果，不影响本地基础测试。

下一步：

- 准备真实 LLM 主备切换的自动/手工验收步骤。
- 用户提供真实 key 后，运行 live acceptance 生成真实验收报告。
