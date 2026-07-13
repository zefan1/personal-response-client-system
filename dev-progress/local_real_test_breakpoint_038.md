# 断点 038：LLM 主备切换受控自动验收

时间：2026-07-09 22:40  
状态：已完成

本轮目标：

- 准备并验证真实 LLM 主备切换前的受控自动验收步骤。
- 不依赖真实 key，先证明代码链路可以做到主模型失败、备用模型成功。

处理结果：

- 新增脚本：
  - `scripts/acceptance_llm_failover_local.py`
- 脚本使用隔离测试库和本地 fake provider：
  - 启动隔离后端：`http://127.0.0.1:8081`
  - 启动 fake provider：`http://127.0.0.1:18080`
  - 测试库：`private_domain_assistant_real_acceptance`
- 脚本自动完成：
  - 创建一个故意失败的 LLM 主环境。
  - 创建一个指向 fake provider 的 LLM 备用环境。
  - 配置 `REPLY_GENERATION + PENDING` 两条 LLM 场景路由。
  - 启用 `llm.reply_generation.enabled=true`。
  - 写入一个测试客户。
  - 调用 `/api/v1/chat/generate`。
  - 验证回复来源为 `LLM`。
  - 查询 LLM 调用统计，验证至少 1 次失败和 1 次成功。
  - 结束后停止隔离后端和 fake provider，并恢复当前本地后端。

验证结果：

- Python 脚本编译通过：
  - `python -m py_compile scripts\acceptance_llm_failover_local.py`
- 受控主备切换验收通过：
  - `python scripts\acceptance_llm_failover_local.py`
  - `passed=true checks=20`
- 报告路径：
  - `.tools/acceptance/llm_failover_local.json`
- 报告核心结果：
  - `replySource.source = LLM`
  - `totalCalls = 2`
  - 主模型 `bad-primary`：1 次失败
  - 备用模型 `fake-backup`：1 次成功
- 验收后本地后端恢复可用：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=15/18`

当前结论：

- LLM 主备切换链路已被自动化证明。
- 当前真实 provider 未配置，所以 live 主备验收仍等待用户提供真实 LLM 配置。

下一步：

- 继续补不依赖真实 key 的验收报告收口。
- 用户提供真实 LLM provider 后，可参考此脚本做真实主备切换验收。
