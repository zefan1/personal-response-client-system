# 标签、Skill、LLM 闭环 Step 5 最终断点 058

时间：2026-07-15
对应方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`
对应任务清单：`dev-progress/tag_skill_llm_tasklist_056.md`
状态：Step 5 已完成；Step 6 尚未开始；两个 LLM 主开关保持关闭。

## Git 恢复点

- 当前开发分支：`feature/tag-step5-skill-profile-analysis`。
- Step 5 核心代码恢复提交：`ddcf1cc feat: complete skill profile analysis`。
- Step 4 远程恢复点：`origin/feature/tag-step4-unified-access` at `361cada`。
- `origin/main` 保持 `b1d3527`，Step 5 没有合并或覆盖主分支。
- Step 5 代码不包含数据库迁移，没有开启 LLM，也没有进入标签自动写入。

## 本断点完成内容

1. 新增统一 `ProfileAnalysisContext` 请求链路：最近结构化聊天、脱敏档案、当前有效标签、人工锁定分类、完整动态候选和分类策略全部来自数据库目录快照。
2. 服务端识别上下文优先于请求体；档案分析只在存在有效客户消息时调用，扁平 `client_message` 只包含 `role=client` 原话，员工回复不能成为客户证据。
3. PROFILE_EXTRACT 使用独立档案分析 Prompt，不再继承“生成 3 条回复”的通用任务；严格输出包含 `fields` 和 `tag_decisions`。
4. 新增 `UPDATE`、`UNABLE_TO_DETERMINE`、`KEEP_CURRENT` 和 `ADD`、`REPLACE`、`NONE` 类型化结果。
5. 严格解析和统一校验会拒绝缺失/旧 Schema、非法枚举、重复分类、字典外、停用、跨分类、重复现值、无证据、消息不足、低把握度和动作策略不匹配。
6. 旧 `profile_updates.fields` 中所有数据库 `boundField` 标签字段被动态阻断，不能绕过统一标签结果；标签决策在 Step 5 只返回，不落库，自动写入留到 Step 7。
7. Skill 熔断按场景隔离；PROFILE_EXTRACT 失败不会阻断聊天回复，开路短路、超时和非法响应都会写健康计数和调用日志。
8. 管理端 PROFILE_EXTRACT 在线测试复用生产结构化上下文、专用请求构建、严格解析和统一校验，并展示档案字段、标签动作、把握度和证据。
9. 直接 LLM 档案分析仍只临时包装旧字段结果，统一 Schema 和共用校验的正式实现留到 Step 6。

## 验证证据

- Java 全量：358 tests，0 failures，0 errors；1 条条件式 MariaDB 测试跳过。
- Step 5 定向：14 个测试类、62 tests，0 failures，0 errors。
- 前端全量：36 个测试文件、253 tests，0 failures；类型检查和生产构建通过。
- 真实构建：`renderer_smoke=passed`、`electron_smoke=passed`，连接最新 8082 后端。
- `verify_module_46.py`、`verify_module_d.py` 通过。
- `verify_database_alignment.py`：42 tables、24 required、41 migration tables、1,382 Repository 列引用，0 violations。
- 真实后端：Flyway 成功验证 37 个迁移，schema version 69，无待迁移；Tomcat 8082 启动完成。
- 真实健康：DB、Redis、Skill 为 UP，Skill 熔断为 CLOSED；总状态 DOWN 来自既有企微表格失败记录。
- 真实在线测试：临时创建 PROFILE_EXTRACT 绑定后调用真实外部地址，接口返回失败；绑定随后删除，没有留下测试配置。
- 真实失败回退：发送确认返回 `accepted=true`；PROFILE_EXTRACT 调用记录从 0 增至 1、成功率 0%，业务未被外部 Skill 失败阻断。
- 真实数据守恒：全库当前有效 `customer_tag_assignments=0`，验收客户当前有效分配 0 条，Step 5 没有提前写标签。
- 配置核验：`llm.profile_extraction.enabled=false`、`llm.reply_generation.enabled=false`。

## 当前运行状态

- 后端：WSL 内 `http://127.0.0.1:8082`；当前 Windows 可访问地址为 `http://172.19.250.154:8082`。
- 数据库：`private_domain_assistant_smoke`，真实外部模式，Flyway V69。
- worktree 前端：`http://127.0.0.1:5175/`；生产构建产物已生成。
- Windows 到 WSL 的 `127.0.0.1:8082` 转发当前未生效，因此 smoke 使用 WSL IP；WSL IP 在 WSL 重启后可能变化。
- 当前会话未提供内置浏览器控制接口；管理端结构展示由组件测试覆盖，真实后端接口、renderer smoke 和 Electron smoke 均已通过。

## 下一步

严格按方案进入 Step 6：让直接 LLM 档案分析使用与 Skill 相同的动态上下文、严格返回 Schema 和统一后端校验，并验证多 LLM 路由与 Skill 回退。

继续保持：

- `llm.profile_extraction.enabled=false`
- `llm.reply_generation.enabled=false`

不得提前进入 Step 7 自动写标签、Step 8 回复读取或 Step 9 搜索统计规则改造。
