# 断点 030：客户档案提取接入 LLM 可选链路

时间：2026-07-09 21:08  
状态：已完成

本轮目标：

- 继续本地真实测试链路，不需要用户额外配合。
- 把第二个 LLM 业务场景接入现有资料更新流程。
- 保持默认关闭，避免未配置真实 LLM provider 时影响当前已能动的侧边栏。

处理结果：

- 新增 LLM 档案提取服务：
  - `LlmProfileExtractionService`
  - 使用 `PROFILE_EXTRACTION` 场景路由调用统一 LLM runtime。
  - 返回结构复用现有 `profile_updates`，继续进入高置信自动写入、中置信待确认建议流程。
- 新增配置迁移：
  - `V62__llm_profile_extraction_configs.sql`
- 新增配置键：
  - `llm.profile_extraction.enabled`
  - `llm.profile_extraction.fallback_to_skill`
  - `llm.profile_extraction.temperature`
  - `llm.profile_extraction.max_tokens`
  - `llm.profile_extraction.system_prompt`
- 默认策略：
  - `enabled=false`
  - `fallback_to_skill=true`
  - 所以当前本地没有真实 LLM key 时仍走原 Skill 档案提取链路。
- 隐私处理：
  - LLM prompt 不传完整手机号。
  - 只保留 `phoneLast4` 辅助定位日志和上下文。
- 管理后台配置中心新增 `LLM 档案提取` 面板：
  - 可开关 LLM 档案提取。
  - 可配置失败回落 Skill。
  - 可配置温度、最大 tokens、系统 Prompt。

涉及文件：

- `src/main/java/com/privateflow/modules/llm/LlmProfileExtractionService.java`
- `src/main/java/com/privateflow/modules/profile/service/ProfileExtractionClient.java`
- `src/main/resources/db/migration/V62__llm_profile_extraction_configs.sql`
- `src/test/java/com/privateflow/modules/llm/LlmProfileExtractionServiceTest.java`
- `src/test/java/com/privateflow/modules/profile/service/ProfileExtractionClientTest.java`
- `desktop/src/renderer/modules/admin/AdminConsole.vue`
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

验证结果：

- 后端新增定向测试通过：
  - `LlmProfileExtractionServiceTest`：3 个用例，0 failure，0 error。
  - `ProfileExtractionClientTest`：3 个用例，0 failure，0 error。
- 后端编译验证通过：
  - `mvn -q test-compile`
- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 23 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 打开管理后台 `配置中心`。
- 应能看到 `LLM 档案提取` 面板。
- 默认关闭时，侧边栏发送确认后的资料更新建议仍按原 Skill 链路运行。
- 后续配置真实 LLM provider 后，可开启该开关，再发送确认一条客户聊天，观察客户档案是否出现资料更新建议。

下一步：

- 继续不需要用户配合的 LLM 业务场景接入：
  - 跟进建议接入 `FOLLOWUP_SUGGESTION`。
  - 异常识别接入 `ABNORMAL_DETECTION`。
- 真实 LLM provider、企微表格接口仍需要用户后续提供后，才能做真实效果和真实写表联调。
