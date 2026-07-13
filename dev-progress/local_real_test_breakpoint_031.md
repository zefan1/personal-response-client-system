# 断点 031：跟进建议接入 LLM 补位链路

时间：2026-07-09 21:18  
状态：已完成

本轮目标：

- 继续本地真实测试链路，不需要用户额外配合。
- 把 `FOLLOWUP_SUGGESTION` LLM 场景接入真实业务流。
- 不替换现有 Skill 返回的跟进建议，只在缺失时补位，降低对当前已可用流程的影响。

处理结果：

- 新增 LLM 跟进建议服务：
  - `LlmFollowupSuggestionService`
  - 使用 `FOLLOWUP_SUGGESTION` 场景路由调用统一 LLM runtime。
  - 输出结构为现有 `CustomerMessageSentEvent.FollowupSuggestPayload`。
- 新增输入模型：
  - `LlmFollowupSuggestionInput`
- 新增配置迁移：
  - `V63__llm_followup_suggestion_configs.sql`
- 新增配置键：
  - `llm.followup_suggestion.enabled`
  - `llm.followup_suggestion.temperature`
  - `llm.followup_suggestion.max_tokens`
  - `llm.followup_suggestion.system_prompt`
- 业务接入点：
  - `ChatOrchestrationService.sendConfirm`
  - 如果前端/Skill 已带 `followupSuggest`，后端不重复调用 LLM。
  - 如果没有 `followupSuggest`，且开关打开，则用 LLM 补充下次跟进时间和方向。
  - LLM 不可用或返回格式异常时，不阻断发送确认，继续沿用原 `selectedDirection` 写入逻辑。
- 隐私处理：
  - LLM prompt 不传完整手机号。
  - 只保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 跟进建议` 面板。

涉及文件：

- `src/main/java/com/privateflow/modules/llm/LlmFollowupSuggestionService.java`
- `src/main/java/com/privateflow/modules/llm/LlmFollowupSuggestionInput.java`
- `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- `src/main/resources/db/migration/V63__llm_followup_suggestion_configs.sql`
- `src/test/java/com/privateflow/modules/llm/LlmFollowupSuggestionServiceTest.java`
- `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`
- `desktop/src/renderer/modules/admin/AdminConsole.vue`
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

验证结果：

- 后端定向测试通过：
  - `LlmFollowupSuggestionServiceTest`：3 个用例，0 failure，0 error。
  - `ChatOrchestrationServiceTest`：7 个用例，0 failure，0 error。
- 后端编译验证通过：
  - `mvn -q test-compile`
- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 23 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 打开管理后台 `配置中心`。
- 应能看到 `LLM 跟进建议` 面板。
- 默认关闭时，发送确认仍按现有 Skill/回复方向写入跟进方向。
- 后续配置真实 LLM provider 后，可打开该开关，再在没有 Skill `followup_suggest` 的回复确认流程里观察是否补充下次跟进时间。

下一步：

- 继续不需要用户配合的 LLM 业务场景接入：
  - 异常识别接入 `ABNORMAL_DETECTION`。
  - 总结类能力接入 `SUMMARY`。
- 真实 LLM provider、企微表格接口仍需要用户后续提供后，才能做真实效果和真实写表联调。
