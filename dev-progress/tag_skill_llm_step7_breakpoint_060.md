# 标签、Skill、LLM 闭环 Step 7 最终断点 060

时间：2026-07-16
对应方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`
对应任务清单：`dev-progress/tag_skill_llm_tasklist_056.md`
状态：Step 7 已完成；严格停在 Step 7，未进入 Step 8 或 Step 9。

## Git 恢复点

- 当前开发分支：`feature/tag-step7-auto-profile-update-locking`。
- Step 6 基线：`6f6259e docs: record direct llm profile analysis`。
- Step 7 代码与验收提交：`22bc0da feat: complete tag auto update and locking`。
- 主分支 `main` 和 `origin/main` 保持不变；主服务仍使用独立工作树运行。
- 本断点文档已随 Step 7 分支提交，供后续继续接手。

## 本断点完成内容

1. 新增统一客户标签自动更新服务和事务仓储，按权限、分类/标签状态、系统自动策略、动作、数量、证据、消息数、置信度、人工锁定、冷却期和客户版本顺序检查。
2. 单选分类按配置替换，多选分类只自动新增，不自动删除；所有自动更新保存分析运行、分析结果和审计记录。
3. 自动更新使用客户版本乐观锁，失败或竞争冲突不写入标签；自动标签异常不会阻断普通档案更新、建议入队和审计。
4. 人工标签接口支持新增、替换、移除，人工修改默认锁定分类；新增分类锁定/解锁接口，操作者从 `AuthContext` 获取，不信任请求体操作者。
5. 人工修改和解锁校验客户权限与版本，竞争冲突返回标准 409 并保留审计记录。
6. `CustomerProfileView` 返回当前标签、分类锁和可人工维护的标签目录；保留旧三参数构造器兼容现有调用。
7. 新增 `CustomerTagsUpdatedEvent` 和 `CUSTOMER_TAGS_UPDATED` WebSocket 推送；客户缓存服务监听该事件并刷新 Redis，保证标签事务提交后档案读取立即返回最新客户版本。
8. 客户档案前端新增标签编辑区域，支持单选/多选、移除、保存、锁定/解锁、版本提交和 WebSocket 刷新。

## API 验证

- `PUT /api/v1/customers/{phone}/tags/{categoryId}`：人工保存、替换和移除通过。
- `PUT /api/v1/customers/{phone}/tags/{categoryId}/lock`：锁定、解锁和过期版本冲突通过。
- `GET /api/v1/customers/{phone}`：返回 `currentTags`、`tagLocks`、`editableTagCategories`；保存后版本和标签缓存即时刷新。
- 真实验收客户：`13900000001`。测试后已清理当前标签并解除分类锁，最终标签为空。

## 验证证据

- Java 全量：`mvn -q test`，379 tests，0 failures，0 errors，1 条条件式 MariaDB 测试跳过。
- 缓存回归：`CustomerQueryServiceImplTest` 通过，验证标签事件触发数据库重读和 Redis 写入。
- 前端全量：`npm run test`，36 个测试文件、255 tests，0 failures。
- `npm run typecheck`、`npm run build` 通过。
- 运行时：`renderer_smoke=passed`、`electron_smoke=passed`。
- 后端真实启动：数据库 `private_domain_assistant_smoke`，Flyway V69，8082 启动并完成管理员登录。
- 主服务 `8080` 未停止；当前工作树前端地址：`http://127.0.0.1:5174/`。

## 当前运行状态

- Step 7 后端：已用真实数据库、`MOCK_EXTERNALS=false` 在 WSL 内 `http://127.0.0.1:8082` 完成启动和接口验收；验收结束后已停止临时进程。
- 主服务：WSL 内 `http://127.0.0.1:8080`，保持运行。
- 前端开发服务：`http://127.0.0.1:5174/`。
- worktree：`C:\Users\85314\.config\superpowers\worktrees\private-domain-assistant\tag-step4-unified-access`。

## 下一步

按用户指示再进入 Step 8：让回复生成读取统一当前有效标签及中文含义。不得提前进入 Step 9 搜索、统计、规则和数据交换改造。

继续保持：

- `llm.profile_extraction.enabled=false`
- `llm.reply_generation.enabled=false`
