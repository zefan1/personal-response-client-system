# 断点 036：LLM 多模型备用降级链路

时间：2026-07-09 22:20  
状态：已完成

本轮目标：

- 完成 tasklist 中的 LLM 降级策略。
- 让管理后台配置的多个 LLM 路由真正参与运行时容灾。
- 保持默认关闭业务开关，不影响当前无真实 key 的本地测试。

处理结果：

- `LlmRouteRepository` 新增候选路由查询：
  - 同一 `scene + leadType` 下按 `priority ASC, id ASC` 返回所有启用路由。
- `LlmRoutingService` 新增候选解析：
  - 先取精确 leadType 路由。
  - 再取通用 leadType 路由。
  - 再取 active LLM environment。
  - 最后回落全局 `llm.api_base_url/api_key/model` 配置。
  - 同一个 LLM environment 不会重复进入候选列表。
- `LlmService.generate(scene, ...)` 改为逐个候选调用：
  - 主模型成功时立即返回。
  - 主模型失败时自动尝试备用模型。
  - 每一次模型尝试都会写入 `llm_call_logs`。
  - 所有候选都失败时，返回最后一次失败结果，让业务层继续执行原有 Skill/模板回落。

涉及文件：

- `src/main/java/com/privateflow/modules/llm/LlmRouteRepository.java`
- `src/main/java/com/privateflow/modules/llm/LlmRoutingService.java`
- `src/main/java/com/privateflow/modules/llm/LlmService.java`
- `src/test/java/com/privateflow/modules/llm/LlmRoutingServiceTest.java`
- `src/test/java/com/privateflow/modules/llm/LlmServiceTest.java`

验证结果：

- 后端定向测试通过：
  - `LlmServiceTest`：2 个用例，0 failure，0 error。
  - `LlmRoutingServiceTest`：4 个用例，0 failure，0 error。
- 后端编译验证通过：
  - `mvn -q test-compile`
- 真实外部代码 readiness 通过：
  - `python scripts\verify_real_external_readiness.py`
  - `mockExternalsFalseReady=true blockers=0`
- 本地 runtime readiness 通过：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=15/18`
- 管理后台定向测试通过：
  - `npm --prefix desktop test -- AdminConsole.test.ts`
  - 23 个用例通过。
- 桌面端类型检查通过：
  - `npm --prefix desktop run typecheck`

当前结论：

- 多 LLM 配置现在不只是能保存，也能在运行时按优先级自动切备用模型。
- 当前真实 LLM key 未配置，所以该能力仍处于待真实 provider 验收状态。
- 业务层原有回落逻辑仍保留：备用 LLM 全失败后继续回落 Skill/模板/人工处理。

下一步：

- 继续整理真实 provider 接入后的执行顺序和验收命令。
- 用户提供真实 LLM provider 后，可配置两个 LLM environment 验证主备切换效果。
