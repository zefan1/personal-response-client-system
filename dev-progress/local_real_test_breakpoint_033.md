# 断点 033：总结补位接入 SUMMARY LLM 场景

时间：2026-07-09 21:45  
状态：已完成

本轮目标：

- 继续本地真实测试链路，不需要用户额外配合。
- 把 `SUMMARY` LLM 场景接入发送确认后的会话摘要补位。
- 默认关闭，避免没有真实 LLM provider 时影响当前可操作流程。

处理结果：

- 新增 LLM 总结服务：
  - `LlmSummaryService`
  - 使用 `SUMMARY` 场景路由调用统一 LLM runtime。
  - 输出用于跟进备注、写表、档案更新上下文的短摘要。
- 新增输入结构：
  - `LlmSummaryInput`
  - 包含客户基础信息、聊天原文、已发送回复、选择方向和操作人。
- `ChatOrchestrationService.sendConfirm` 已接入总结补位：
  - 如果前端已传 `conversationSummary`，直接沿用，不调用 LLM。
  - 如果前端未传 `conversationSummary`，尝试调用 `SUMMARY` LLM。
  - 如果 LLM 未启用、失败或返回为空，回落到 `sentText`，不阻断发送确认。
- 新增配置迁移：
  - `V65__llm_summary_configs.sql`
- 新增配置键：
  - `llm.summary.enabled`
  - `llm.summary.temperature`
  - `llm.summary.max_tokens`
  - `llm.summary.system_prompt`
- 隐私处理：
  - LLM prompt 不传完整手机号。
  - 只保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 总结补位` 面板。

涉及文件：

- `src/main/java/com/privateflow/modules/llm/LlmSummaryService.java`
- `src/main/java/com/privateflow/modules/llm/LlmSummaryInput.java`
- `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- `src/main/resources/db/migration/V65__llm_summary_configs.sql`
- `src/test/java/com/privateflow/modules/llm/LlmSummaryServiceTest.java`
- `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`
- `desktop/src/renderer/modules/admin/AdminConsole.vue`
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

验证结果：

- 后端定向测试通过：
  - `LlmSummaryServiceTest`：3 个用例，0 failure，0 error。
  - `ChatOrchestrationServiceTest`：8 个用例，0 failure，0 error。
- 后端编译验证通过：
  - `mvn -q test-compile`
- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 23 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 打开管理后台 `配置中心`。
- 应能看到 `LLM 总结补位` 面板。
- 默认关闭时，发送确认仍沿用前端摘要；如果前端没有摘要，则使用已发送文本作为备注，不会卡住主流程。
- 后续配置真实 LLM provider 后，可打开该开关，再验证缺少摘要时是否由模型生成更适合作为跟进备注的短摘要。

下一步：

- 更新本地 readiness 脚本和检查项，让新 LLM 场景配置被纳入本地检测。
- 真实 LLM provider、企微表格接口仍需要用户后续提供后，才能做真实效果和真实写表联调。
