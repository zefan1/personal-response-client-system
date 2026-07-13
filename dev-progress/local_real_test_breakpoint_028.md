# 断点 028：侧边栏回复来源展示

时间：2026-07-09 20:28  
状态：已完成

本轮目标：

- 继续本地真实测试链路，不需要用户额外配合。
- 让侧边栏回复建议明确显示当前回复来自 `LLM`、`Skill` 还是 `系统兜底`。
- 方便后续打开真实 LLM 后直接判断接口是否真正参与生成。

处理结果：

- 后端 `ChatResponse` 新增 `replySource` 元数据：
  - `LLM`：回复建议来自 LLM 场景路由。
  - `SKILL`：回复建议来自 Skill 场景接口。
  - `FALLBACK`：AI/Skill 不可用时使用系统兜底。
- 回复生成流程会自动标记来源：
  - LLM 生成成功 -> `LLM`。
  - LLM 未启用或失败后回落 Skill，且 Skill 正常返回 -> `SKILL`。
  - Skill 返回 `SYSTEM_FALLBACK` 或空结果 -> `FALLBACK`。
- 侧边栏回复助手新增来源标签：
  - 推荐回复标题旁展示来源。
  - 当前任务操作区展示来源。
  - 待处理队列任务也展示来源。
- 兜底状态会强制显示 `系统兜底`，避免空回复误显示为 Skill 生成。

涉及文件：

- `src/main/java/com/privateflow/modules/api/chat/ChatReplySource.java`
- `src/main/java/com/privateflow/modules/api/chat/ChatResponse.java`
- `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`
- `src/test/java/com/privateflow/modules/api/web/ChatControllerTest.java`
- `desktop/src/renderer/modules/reply-suggestions/types.ts`
- `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.ts`
- `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue`
- `desktop/src/renderer/modules/reply-suggestions/replySuggestionStore.test.ts`
- `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.test.ts`
- `desktop/src/renderer/styles.css`

验证结果：

- 后端定向测试通过：
  - `mvn -q -Dtest=ChatOrchestrationServiceTest,ChatControllerTest test`
- 后端编译验证通过：
  - `mvn -q test-compile`
- 回复建议前端定向测试通过：
  - `npm test -- ReplySuggestionPanel.test.ts replySuggestionStore.test.ts`
  - 2 个测试文件、30 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 打开侧边栏 `回复助手`。
- 使用文字通道或客户档案生成回复。
- 推荐回复标题旁应显示：
  - `Skill 生成`：当前默认链路。
  - `LLM 生成`：后续打开 LLM 回复生成且真实 LLM 成功时。
  - `系统兜底`：外部服务不可用或返回空结果时。

下一步：

- 如果继续不需要用户配合，建议继续做后台/侧边栏 LLM 状态联动：
  - 侧边栏健康/提醒中心展示 LLM 配置缺失或调用失败。
  - 或继续把客户档案提取、跟进建议、异常识别逐步接入 LLM 场景。
