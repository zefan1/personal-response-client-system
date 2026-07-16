# 回复生成使用最新客户标签设计

日期：2026-07-16
范围：标签、Skill、LLM 闭环 Step 8
基线：`ae61402 docs: record tag step 7 breakpoint`

## 目标

回复生成前读取客户当前有效标签，并让 Skill 回复和直接 LLM 回复使用同一份结构化标签快照。人工或系统更新标签后，下一次生成和换一组回复必须立即读取最新标签。标签查询异常不得阻断普通回复生成。

## 非目标

- 不进入 Step 9，不修改客户搜索、统计、跟进规则、导入、同步或导出。
- 不新增数据库迁移或标签表字段。
- 不开启 `llm.reply_generation.enabled` 或 `llm.profile_extraction.enabled`。
- 不让模型自动修改标签；Step 8 只读取 Step 7 已保存的当前有效标签。
- 不在回复正文中直接暴露内部标签判断、证据、来源或锁定状态。

## 标签快照契约

新增 `ReplyTagSnapshot` record，并在 `SkillRequest` 增加不可变的 `List<ReplyTagSnapshot> currentTags` 字段，保留现有 9 参数构造器。每个标签项只包含回复生成所需的结构化信息：

- `categoryKey`：稳定分类编码。
- `categoryName`：分类中文名称。
- `tagValue`：稳定标签编码。
- `tagDisplayName`：标签中文名称。
- `meaning`：标签中文含义。
- `sourceType`：当前分配来源。
- `evidenceText`：已有依据，可为空。
- `manualLocked`：是否人工锁定。

快照不包含完整手机号、内部数据库主键、模型密钥或无关标签目录数据。快照使用 `List.copyOf` 固化，Skill 和 LLM 在同一次生成中只能读取同一个对象内容。

## 标签读取

`ChatOrchestrationService` 依赖统一的 `CustomerTagQueryService`。当客户存在且有有效编号时，调用 `current(customerId)` 读取当前有效标签，再与标签目录快照关联补充 `TagValue.meaning`。目录只读取 `useForReply=true`、启用且未合并的分类和值。

为避免聊天服务重复理解标签目录结构，新增专用 `ReplyTagSnapshotBuilder`：

- 输入：客户编号。
- 依赖：`CustomerTagQueryService` 和 `TagDirectoryService`。
- 输出：`List<ReplyTagSnapshot>`。
- 过滤：仅当前有效、分类和值启用、未合并、分类允许用于回复。
- 顺序：保持统一标签查询的分类和值排序。
- 异常：向调用方抛出，统一由聊天编排层降级处理。

## 回复数据流

### 首次生成

1. 查询客户并完成现有客户权限判断。
2. 读取一次最新标签快照。
3. 使用客户基础档案和该标签快照创建 `SkillRequest`。
4. 直接 LLM 优先时，将 `currentTags` 放入 LLM 输入 JSON。
5. 回退 Skill 时，将同一 `currentTags` 放入 Skill 请求 JSON 的 `current_tags`。
6. 保存请求上下文时保留本次标签快照。

### 截图识别后生成

匹配到现有客户时执行同样的标签读取。未匹配到客户或新客户没有客户编号时使用空标签快照，维持现有新客户回复行为。

### 换一组

换一组只复用上一轮的场景、客户消息、聊天上下文和旧建议。它必须重新查询客户并重新读取标签，不能复用 `RequestContext` 中保存的旧标签快照。新请求和新上下文保存最新快照。

## Skill 请求

`SkillRequestBuilder.build` 在顶层输出 `current_tags`，值直接来自 `SkillRequest.currentTags`。系统提示中追加明确约束：

- 标签只用于调整回复方向、优先级和语气。
- 不得向客户描述内部标签、系统判断、把握度、证据、来源或锁定状态。
- 标签与客户原话冲突时以当前客户消息和业务事实为准。

现有候选标签目录 `available_tags` 保留，它用于解释可用字典；`current_tags` 表示该客户当前真实有效标签，两者语义不能混用。

## 直接 LLM 请求

`LlmReplyGenerationService.userPrompt` 在输入 JSON 中加入 `currentTags`，使用与 Skill 相同的快照内容。默认系统提示增加同样的不暴露约束。自定义系统提示存在时，固定安全约束仍由用户提示附加，不能被配置完全替换。

## 异常降级与记录

标签快照构建失败时，只有数据库、目录或映射等运行时异常进入降级；`CustomerTagQueryService` 返回的 `ApiException`（特别是无客户权限）必须继续向上抛出，不能通过空标签快照绕过客户范围校验。可降级异常的处理为：

1. 记录 WARN 日志，包含手机号后四位和异常摘要，不记录完整手机号或标签证据。
2. 调用 `AuditLogger.log` 写入 `CUSTOMER_TAGS_READ_DEGRADED`，目标类型 `CUSTOMER`，目标为完整手机号，详情为裁剪后的失败原因。
3. 使用空标签快照继续直接 LLM 或 Skill 普通回复流程。
4. `ChatReplySource` 仍表示实际回复来源，不因标签读取降级错误标记为系统兜底。

审计失败沿用现有异步容错，不阻断回复。

## 在线测试和调用日志

现有侧边栏生成接口和换一组接口自动覆盖真实标签链路。管理端独立 LLM 连通性测试不绑定真实客户，不读取生产客户标签，继续使用空快照。

LLM/Skill 调用日志继续记录现有场景、调用者、请求摘要和成功状态。标签快照只进入请求载荷，不写入调用摘要，避免日志泄露客户画像。

## 测试设计

- `ReplyTagSnapshotBuilderTest`：补充中文名称、含义、来源和锁定状态；过滤不允许用于回复的分类。
- `ChatOrchestrationServiceTest`：首次生成读取标签并传给请求；标签查询失败仍生成回复并写降级审计；换一组重新读取最新快照。
- `SkillRequestBuilderTest`：Skill 请求包含 `current_tags` 和不暴露约束。
- `LlmReplyGenerationServiceTest`：LLM 输入包含同一标签中文含义，且不包含完整手机号。
- 现有回复来源、回退、发送确认和上下文测试继续通过。

## 完成标准

- 人工修改或系统自动更新标签后，下一次回复请求使用最新标签。
- 同一次生成中直接 LLM 和 Skill 回退使用相同标签快照契约。
- 换一组重新读取客户和标签，不复用旧快照。
- 标签读取异常时回复流程成功继续，并留下明确降级记录。
- 回复提示明确禁止暴露内部标签判断。
- Java 全量测试、前端全量测试、类型检查、构建和 Electron smoke 全部通过。
