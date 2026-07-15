# 标签、Skill、LLM 闭环 Step 6 最终断点 059

时间：2026-07-15
对应方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`
对应任务清单：`dev-progress/tag_skill_llm_tasklist_056.md`
状态：Step 6 已完成；Step 7 尚未开始；两个 LLM 主开关保持关闭。

## Git 恢复点

- 当前开发分支：`feature/tag-step6-direct-llm-profile-analysis`。
- Step 6 核心代码恢复提交：`4597f4a feat: complete direct llm profile analysis`。
- Step 5 断点提交：`12e1775 docs: record skill profile analysis breakpoint`。
- `origin/main` 保持 `b1d3527`，Step 6 没有合并或覆盖主分支。
- Step 6 不包含数据库迁移，没有开启 LLM 主开关，也没有进入标签自动写入。

## 本断点完成内容

1. 新增共享 `ProfileAnalysisPromptBuilder`，Skill 与直接 LLM 复用相同固定任务、动态结构化输入和严格返回 Schema；LLM 配置 Prompt 只作为附加业务要求。
2. 直接 LLM 使用完整 `ProfileAnalysisContext`：最近聊天、脱敏档案、当前标签、锁定分类、动态候选、分类策略和目标档案字段全部与 Skill 路径一致。
3. `LlmProfileExtractionService` 返回完整 `ProfileAnalysisResult`，不再把旧 `ProfileUpdates` 临时包装成空标签决策。
4. 直接 LLM 严格复用 `SkillProfileAnalysisResponseParser` 和 `TagAnalysisDecisionValidator`；字典外、跨分类、非法动作、无证据、低把握度和旧 Schema 继续被统一拒绝。
5. `LlmService.generateValidated` 按候选逐个执行调用、解析和校验；HTTP 成功但业务 Schema 非法时记录 `30-20006` 并继续备用 LLM，只有校验成功才结束路由。
6. 所有候选失败或非法时返回空 Optional，`ProfileExtractionClient` 按配置回退 Skill；路由解析或请求构建抛异常时也不会绕过 Skill 回退。
7. 合法直接 LLM 结果原样保留档案字段和标签决策，阻止重复调用 Skill；两条路径都失败时不修改标签。
8. 选定 LLM 环境支持只读 `PROFILE_EXTRACTION` 在线测试，不受生产开关影响但复用生产 Prompt、解析和校验；不会触发 Step 7 标签写入。
9. 管理端 LLM 环境面板支持线索类型、真实测试消息、结构化字段/标签/证据展示；严格校验失败显示后端错误，不再误报空成功结果。

## 验证证据

- Java 全量：365 tests，0 failures，0 errors；1 条条件式 MariaDB 测试跳过。
- Step 6 定向：10 个测试类、45 tests，0 failures，0 errors。
- 前端全量：36 个测试文件、254 tests，0 failures；`AdminConsole.test.ts` 34 tests。
- `npm run typecheck`、`npm run build` 通过。
- 真实构建：`renderer_smoke=passed`、`electron_smoke=passed`，连接最终 Step 6 后端。
- `verify_module_46.py`、`verify_module_d.py` 通过。
- `verify_database_alignment.py`：42 tables、24 required、41 migration tables、1,382 Repository 列引用，0 violations。
- 真实数据库：`private_domain_assistant_smoke`，Flyway V69；全库当前有效 `customer_tag_assignments=0`，验收客户当前有效分配 0 条。
- 真实运行模式：DB、Redis、Skill 为 UP；总状态 DOWN 仅来自既有企微表格失败记录。
- 真实选定环境在线测试：PROFILE_EXTRACTION 使用生产档案契约调用环境 `codex-b-llm-224515`，不可达路径返回 `30-20004`，页面可展示该失败。
- 真实失败回退：临时设置 `llm.profile_extraction.enabled=true` 后，发送确认返回 `accepted=true`；LLM PROFILE_EXTRACTION 调用记录 0→1、成功率 0%，Skill PROFILE_EXTRACT 调用记录 2→3、成功率 0%，两条路径失败均未写入标签。
- 配置恢复核验：`llm.profile_extraction.enabled=false`、`llm.reply_generation.enabled=false`、`llm.profile_extraction.fallback_to_skill=true`。

## 当前运行状态

- 最终 Step 6 后端：WSL 内 `http://127.0.0.1:8082`，进程 PID 24464；当前 Windows 可访问地址为 `http://172.19.250.154:8082`。
- 数据库：`private_domain_assistant_smoke`，真实外部模式，Flyway V69。
- worktree 前端：`http://127.0.0.1:5175/`；生产构建产物已生成。
- Windows 到 WSL 的 `127.0.0.1:8082` 转发仍未生效，因此 smoke 使用 WSL IP；WSL IP 在 WSL 重启后可能变化。
- worktree 保留在 `C:\Users\85314\.config\superpowers\worktrees\private-domain-assistant\tag-step4-unified-access`，供 Step 7 继续开发。

## 下一步

严格按方案进入 Step 7：实现后端自动更新、人工修改、分类锁定、版本/冷却/权限检查、操作记录和 WebSocket 刷新。

继续保持：

- `llm.profile_extraction.enabled=false`
- `llm.reply_generation.enabled=false`

不得提前进入 Step 8 回复读取或 Step 9 搜索统计规则改造。
