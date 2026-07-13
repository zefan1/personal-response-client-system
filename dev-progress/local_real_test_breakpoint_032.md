# 断点 032：异常识别接入 LLM 异步提醒链路

时间：2026-07-09 21:27  
状态：已完成

本轮目标：

- 继续本地真实测试链路，不需要用户额外配合。
- 把 `ABNORMAL_DETECTION` LLM 场景接入侧边栏已有异常提醒中心。
- 默认关闭，避免没有真实模型评估前产生误报。

处理结果：

- 新增 LLM 异常识别服务：
  - `LlmAbnormalDetectionService`
  - 使用 `ABNORMAL_DETECTION` 场景路由调用统一 LLM runtime。
  - 输出 `CUSTOMER_COMPLAINT` 或 `CHURN_RISK`，级别为 `ERROR/WARN/INFO`。
- 新增异步监听器：
  - `LlmAbnormalDetectionListener`
  - 监听 `CustomerMessageSentEvent`。
  - 发送确认主流程不等待异常识别结果。
  - 命中后发布 `FollowupWsMessageReadyEvent(userId, "ABNORMAL_ALERT", payload)`。
  - 前端已有异常提醒中心会直接接收并展示。
- 新增配置迁移：
  - `V64__llm_abnormal_detection_configs.sql`
- 新增配置键：
  - `llm.abnormal_detection.enabled`
  - `llm.abnormal_detection.temperature`
  - `llm.abnormal_detection.max_tokens`
  - `llm.abnormal_detection.system_prompt`
- 隐私处理：
  - LLM prompt 不传完整手机号。
  - 只保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 异常识别` 面板。

涉及文件：

- `src/main/java/com/privateflow/modules/llm/LlmAbnormalDetectionService.java`
- `src/main/java/com/privateflow/modules/llm/LlmAbnormalDetectionListener.java`
- `src/main/java/com/privateflow/modules/llm/LlmAbnormalDetectionInput.java`
- `src/main/java/com/privateflow/modules/llm/LlmAbnormalAlert.java`
- `src/main/resources/db/migration/V64__llm_abnormal_detection_configs.sql`
- `src/test/java/com/privateflow/modules/llm/LlmAbnormalDetectionServiceTest.java`
- `src/test/java/com/privateflow/modules/llm/LlmAbnormalDetectionListenerTest.java`
- `desktop/src/renderer/modules/admin/AdminConsole.vue`
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

验证结果：

- 后端定向测试通过：
  - `LlmAbnormalDetectionServiceTest`：3 个用例，0 failure，0 error。
  - `LlmAbnormalDetectionListenerTest`：2 个用例，0 failure，0 error。
- 后端编译验证通过：
  - `mvn -q test-compile`
- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 23 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 打开管理后台 `配置中心`。
- 应能看到 `LLM 异常识别` 面板。
- 默认关闭时，不会产生新的 LLM 异常提醒。
- 后续配置真实 LLM provider 后，可打开该开关，再发送确认包含客户不满/流失风险的聊天，侧边栏提醒中心应出现异常提醒。

下一步：

- 继续评估 `SUMMARY` 场景的明确落点。
- 真实 LLM provider、企微表格接口仍需要用户后续提供后，才能做真实效果和真实写表联调。
