# 标签、Skill、LLM 闭环 Step 4 最终断点 057

时间：2026-07-15
对应方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`
对应任务清单：`dev-progress/tag_skill_llm_tasklist_056.md`
状态：Step 4 已完成；Step 5 尚未开始；两个 LLM 主开关保持关闭。

## Git 恢复点

- 当前开发分支：`feature/tag-step4-unified-access`。
- 当前 Step 4 恢复提交：`1a17b8a fix: serialize tag category refresh and creation checks`。
- 主分支和 `origin/main` 仍为：`b1d3527 docs: record tag management step 3 recovery point`。
- `uploads/` 保持未跟踪，未进入任何提交。
- Step 4 代码尚未合并 `main`；恢复时可直接检出该分支或提交 `1a17b8a`。

## 本断点完成内容

1. 统一标签目录快照、候选构建和结果校验；目录从数据库读取，动态支持新增分类和值。
2. Skill 使用统一系统判断候选；非法、停用、合并、跨分类或无证据输入被后端拒绝；LLM 开关未改变。
3. 客户当前有效标签和历史标签使用统一查询服务，当前查询按启用状态、合并状态和权限过滤。
4. `TagRepository` 的客户旧字段占用统计已移除；客户旧字段只保留合并和兼容同步路径。
5. 管理后台和开发调试台移除固定标签字典、固定分类 ID 和固定数量假设；开发调试台按 `enabled=true&merged=false` 分页读取所有分类，并只提交当前候选分类。
6. 创建标签值的后端从数据库校验分类存在、启用和未合并；最终 `INSERT ... SELECT` 再次用数据库状态条件保护，避免停用/合并竞态写入。
7. 昵称前缀清洗保留 `match.tag_removal_rules` 行为，处理器已明确命名为 `NicknamePrefixRemovalProcessor`，没有接入业务标签目录。

## 验证证据

- Java 全量：326 tests，0 failures，0 errors；1 条条件式 MariaDB 测试跳过。
- 前端全量：36 个测试文件、252 tests，0 failures；类型检查和生产构建通过。
- Electron 真实构建：`renderer_smoke=passed`、`electron_smoke=passed`，均连接当前 Step 4 后端。
- Step 4 定向：前端调试台 9/9；`TagAdminServiceTest` 15/15；`TagRepositoryTest` 7/7。
- `verify_module_46.py` 和 `verify_module_d.py` 通过。
- `verify_database_alignment.py`：42 tables、24 required、41 migration tables、1,382 Repository 列引用，0 violations。
- 真实数据库 `private_domain_assistant_smoke`：Flyway V69；4 categories、27 values、0 active unified assignments；LLM profile/reply 两个 enabled 配置均为 `false`。
- 真实后端启动日志：Successfully validated 37 migrations；schema version 69；No migration necessary；Tomcat 8082 可访问。
- 真实 API：管理员登录成功；动态分类和标签值列表成功；`categoryId=999999999` 创建请求返回 404 / `90-10007`。

## 当前运行状态

- 后端：`http://127.0.0.1:8082`，连接 `private_domain_assistant_smoke`，`MOCK_EXTERNALS=false`。
- 前端：`http://127.0.0.1:5174/`，当前分支生产构建产物已生成。
- 健康接口总状态为 DOWN 的原因是数据库中的既有 WeCom/image 外部状态告警；DB、Redis 和 Skill 状态为 UP。本断点没有伪造健康状态，也没有修改外部配置或业务数据。

## 下一步

严格按方案进入 Step 5：Skill 档案分析动态读取允许判断的分类和值、当前标签、人工锁定和更新策略；继续保持两个 LLM 主开关为 `false`，不得跳过 Step 5 的上下游检查和测试。
