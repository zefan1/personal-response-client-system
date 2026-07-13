# 断点 029：侧边栏 LLM 健康提醒接入

时间：2026-07-09 20:55  
状态：已完成

本轮目标：

- 继续本地真实测试链路，不需要用户额外配合。
- 让侧边栏能直接看到 LLM 回复生成是否可用，而不是只在管理后台里看配置。
- 覆盖两类一线最容易误判的问题：
  - LLM 回复生成已打开但 API 地址、密钥或模型缺失。
  - LLM 回复生成配置完整但近 24 小时调用失败率较高。

处理结果：

- 后端 `/api/v1/desktop/status` 新增 `llmStatus`：
  - `OK`：LLM 未启用或已配置且近期调用正常。
  - `WARN`：LLM 回复生成已启用但配置缺失，或近期调用失败率过高。
  - `UNKNOWN`：兼容旧构造/未加载状态。
- LLM 回复生成未启用时，状态显示为 `LLM 回复生成未启用`，不会打扰侧边栏用户。
- LLM 回复生成启用但缺少 `llm.api_base_url`、`llm.api_key`、`llm.model` 任一项时，状态显示为 `LLM 配置不完整`。
- 配置完整后，会读取近 24 小时 `REPLY_GENERATION` 调用日志：
  - 调用次数达到 3 次。
  - 成功率低于 50%。
  - 侧边栏提醒中心显示 `LLM 近期调用失败率较高`。
- 前端 `desktopStatusStore` 已接收并保存 `llmStatus`。
- 侧边栏提醒中心新增 LLM 提醒：
  - 标题：`LLM 配置需处理`
  - 内容：后端返回的配置缺失或失败率详情。

涉及文件：

- `src/main/java/com/privateflow/modules/desktop/DesktopLlmStatus.java`
- `src/main/java/com/privateflow/modules/desktop/DesktopLlmStatusResponse.java`
- `src/main/java/com/privateflow/modules/desktop/DesktopStatusResponse.java`
- `src/main/java/com/privateflow/modules/desktop/DesktopStatusService.java`
- `src/test/java/com/privateflow/modules/desktop/DesktopStatusServiceTest.java`
- `src/test/java/com/privateflow/modules/desktop/DesktopStatusControllerTest.java`
- `src/test/java/com/privateflow/modules/api/web/WebCoreControllerTest.java`
- `desktop/src/renderer/shared/desktopStatusStore.ts`
- `desktop/src/renderer/modules/abnormal-alert/globalAlertCenter.ts`
- `desktop/src/renderer/modules/abnormal-alert/AlertBell.test.ts`

验证结果：

- 后端定向测试通过：
  - `mvn -q -Dtest=DesktopStatusServiceTest test`
  - `mvn -q -Dtest=DesktopStatusControllerTest,WebCoreControllerTest test`
- 后端编译验证通过：
  - `mvn -q test-compile`
- 前端提醒中心定向测试通过：
  - `npm test -- AlertBell.test.ts`
  - 1 个测试文件、5 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 默认状态下 `llm.reply_generation.enabled=false`，侧边栏提醒中心不应出现 LLM 警告。
- 在后台配置中心打开 `LLM 回复生成`，但不填写 LLM provider 时，侧边栏状态刷新后应出现 `LLM 配置需处理`。
- 后续填写真实 LLM provider 并打开回复生成后，若连续调用失败，侧边栏会提示近期失败率较高。

下一步：

- 继续不需要用户配合的 LLM 业务场景接入：
  - 客户档案提取接入 `PROFILE_EXTRACTION`。
  - 跟进建议接入 `FOLLOWUP_SUGGESTION`。
  - 异常识别接入 `ABNORMAL_DETECTION`。
- 真实 LLM provider、企微表格接口仍需要用户后续提供后，才能做真实效果和真实写表联调。
