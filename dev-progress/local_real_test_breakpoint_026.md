# 本地真实测试断点 026：回复建议接入 LLM 可选链路

时间：2026-07-09 20:00  
状态：已完成

## 本轮目标

- 把侧边栏“回复建议”的后端生成链路接入 `REPLY_GENERATION` LLM 场景。
- 保留现有 Skill 生成链路作为默认和兜底。
- 在真实 LLM key 尚未配置时，不影响当前本地可用流程。

## 已完成

- 新增迁移：`V61__llm_reply_generation_configs.sql`。
- 新增配置：
  - `llm.reply_generation.enabled=false`
  - `llm.reply_generation.fallback_to_skill=true`
  - `llm.reply_generation.temperature=`
  - `llm.reply_generation.max_tokens=900`
  - `llm.reply_generation.system_prompt=...`
- 新增后端服务：
  - `LlmReplyGenerationService`
- `ChatOrchestrationService` 已改为：
  - 生成回复时先调用 `LlmReplyGenerationService.tryGenerate(...)`。
  - LLM 未启用、失败或返回格式异常时，自动回落到原 `SkillGatewayService.generateReplies(...)`。
  - 重新生成回复也走同一套 LLM 可选链路。
- LLM 输出格式复用现有 `SkillResponseParser`：
  - 只要 LLM 返回包含 `suggestions` 的 JSON，就能变成前端现有回复卡片。
  - 前端当前不需要改动。
- 隐私处理：
  - LLM prompt 中不会传完整手机号。
  - 客户字段里的 `phone` 会被过滤，只保留 `phoneLast4`。
  - 请求摘要会脱敏 11 位手机号。

## 本地运行状态

- 后端已重启到最新代码：
  - `http://localhost:8080`
  - `MOCK_EXTERNALS=false`
  - 数据库：`private_domain_assistant_smoke`
- Flyway 已执行到：
  - `61 / V61__llm_reply_generation_configs.sql`
- 默认状态：
  - `llm.reply_generation.enabled=false`
  - 所以当前回复建议仍默认走 Skill，不会因为 LLM provider 未配置而阻断。

## 验证结果

- 编译：`mvn -q test-compile` 通过。
- 定向测试通过：
  - `ChatOrchestrationServiceTest`
  - `ChatControllerTest`
  - `LlmReplyGenerationServiceTest`
  - `LlmServiceTest`
  - `LlmRoutingServiceTest`
  - `LlmCallAnalyticsRepositoryTest`
  - `LlmAdminControllerTest`
- 运行态确认：
  - `flyway_schema_history` 最新为 V61。
  - `system_configs` 已写入 `llm.reply_generation.*`。
  - `POST /api/v1/chat/generate` 可访问。
  - 当前因为真实 Skill/外部服务不可用，仍按原逻辑返回系统兜底回复，这是预期状态。

## 当前仍缺

- 需要在管理后台暴露 `llm.reply_generation.enabled` 和相关 prompt/参数配置。
- 需要配置真实 LLM provider 后，才能测试 LLM 回复质量。
- 需要配置 LLM 路由后，才能测试多个模型按场景切换。
- 需要后续把 LLM 调用状态展示到侧边栏，避免一线同事只看到“生成失败”但不知道原因。

## 下一步

- 补管理后台 LLM 路由配置和调用统计 UI。
- 或继续把侧边栏回复建议面板显示优化为“当前使用 Skill / LLM / 兜底”的可见状态。
