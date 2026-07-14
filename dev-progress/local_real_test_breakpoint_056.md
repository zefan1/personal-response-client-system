# 本地真机断点 056

时间：2026-07-14
状态：055 方案 Step 1、Step 2、Step 3 已完成，Step 4 尚未开始
对应任务：`dev-progress/tag_skill_llm_tasklist_056.md`
执行方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`

## Git 和数据库恢复点

- 接手前完整代码：`2f9f7fd chore: checkpoint current production state`，已推送 `origin/main`。
- Step 1 审计代码：`a2e12b0 docs: complete tag closed-loop step 1 audit`，已推送 `origin/main`。
- Step 2 代码恢复点：`1fa6ab1 feat: complete tag foundation migration step 2`，已推送 `origin/main`。
- Step 3 代码恢复点：`9176546a1338451cfcc55c305abf938f01d3d94a feat: complete tag management step 3`。
- V69 前数据库备份：`.tools/backups/private_domain_assistant_smoke_pre_v69_20260714_114738.sql.gz`。
- V69 前备份 SHA-256：`2377569548EE5227019527A1C73181E5F5DAF68E5AA7BA851FBBCD2C4CF28FC8`。

## Step 3 已完成

1. 新增 Flyway `V69__tag_management_permissions_and_merge_history.sql`，创建账号细粒度权限和不可变标签合并历史表。
2. `tag_values.merged_into_id` 改为单列自引用外键，允许不同分类中同编号冲突值归并到目标分类标签。
3. 分类和值完成列表、详情、创建、编辑、启停、删除保护、搜索、筛选、分页、排序、影响统计和 CSV 导出。
4. 分类和值合并支持预览、乐观版本检查和单事务执行；迁移客户当前/历史标签、分析结果、建议、旧映射、未匹配记录和规则 JSON 引用，并重算旧字段、递增客户版本、提交后刷新客户缓存。
5. 规则引用通过 Jackson 结构化解析和重写，不使用字符串替换；合并后保留旧编号映射及 `tag_merge_operations` 审计历史。
6. 新分类不再绑定 `customers` 旧字段；后端拒绝非空 `boundField`，管理后台不展示旧字段绑定控件，内部编号只读。
7. ADMIN 始终拥有 `TAG_MANAGEMENT`；LEADER/KEEPER 仅在显式授权后可登录标签后台和访问 `/admin/api/v1/tags/**`，其他后台 API 继续返回 403。
8. 账号创建、编辑、登录、刷新令牌和桌面状态均返回当前权限；权限变化递增 `token_version` 并撤销现有刷新令牌。
9. 管理后台完成分类和值独立筛选分页、详情和表单抽屉、启停、删除、合并预览/执行、CSV 下载和 WebSocket `tag_config` 刷新。
10. 委派账号只显示“客户标签与分层”；标准 1440 桌面视口显示全部行操作，窄屏表格使用容器内横向滚动，详情抽屉不溢出视口。
11. 分类和值更新、启停、合并预览和执行强制版本号；Controller、Service 和 Repository 三层均阻止空版本绕过乐观锁。
12. 内置分类合并到无绑定字段目标时，锁定源和目标后原子转移 `bound_field`、重新加载对象，再迁移客户和规则；分类锁审计按最终状态同步锁定人、时间和解锁字段。
13. 分类用途及标签含义、条件、例子和同义词支持显式清空；CSV 对 `= + - @` 开头单元格增加公式注入防护；后台登录先校验密码再判断委派权限。
14. 停用兼容策略固定为保留历史分配及四个旧字段，统一当前标签查询按启用状态排除；旧模块在 Step 4 完成统一读取切换前继续读取兼容字段，不做破坏性清空。

## 当前真实数据库

- 数据库：`private_domain_assistant_smoke`。
- Flyway：V69；37 个迁移文件校验成功，重复启动无需迁移。
- 表数量：42；客户数量：6；标签分类：4；标签值：27。
- 当前统一标签分配：0；历史未匹配：18；主库合并操作历史：0。
- 当前有效 `TAG_MANAGEMENT` 权限：1 条 ADMIN；验收临时委派账号及权限已删除。
- Step 3 临时数据库 `private_domain_assistant_tag_step3_verify`、`private_domain_assistant_tag_step3_flyway`、`private_domain_assistant_tag_step3_review` 已删除。
- `llm.profile_extraction.enabled=false`，`llm.reply_generation.enabled=false`，未开启 LLM。

## 验证结果

- Java 全量测试：248 条，0 失败，0 错误；条件式 MariaDB IT 在全量中跳过 1 条。
- MariaDB 空库集成测试单独执行：V1-V69 共 37 个迁移成功，第二次迁移执行数 0；V69 权限种子、新表和单列合并外键断言通过；单选标签值合并、分类和值停用兼容、可选字段清空、严格版本 SQL、绑定字段转移、分类锁审计、规则 JSON 和客户旧字段同步全部通过。
- 标签真实 API 生命周期在隔离克隆库通过：分类和值 CRUD、启停、删除保护、值合并、分类合并、单选冲突处理、规则 JSON 重写、旧编号映射、合并历史和 CSV 导出。
- 前端测试：36 个测试文件、244 条测试全部通过；`npm run typecheck` 和 `npm run build` 通过。
- 数据库结构对齐：42 张表，24 张重点表，41 张迁移表，1,355 个 Repository 列引用，0 结构/列/默认值/枚举违规。
- 主库连续启动验证：V69、`Successfully validated 37 migrations`、`No migration necessary`；最新编译产物重启后同样通过。
- Electron 管理后台冒烟通过：管理员桌面/窄屏分类和值、详情、新建表单、合并预览；委派账号只显示标签后台且加载 4 个分类和 27 个标签值。
- 权限真机验证：委派 LEADER 后台登录和刷新均返回 `TAG_MANAGEMENT`，标签 API 200，账号 API 403；临时账号删除后权限行级联清理。
- 验收截图位于 `.tools/screenshots/`，该目录已由 `.gitignore` 排除。

## 当前服务

- 后端：`http://localhost:8080`，PID 文件 `.tools/runtime/backend.pid`，连接真实本地 MariaDB。
- 前端开发服务：`http://127.0.0.1:5174/#/admin`。
- `uploads/` 继续保持未跟踪，不纳入 Step 3 提交。

## 下次继续位置

严格从 Tasklist 056 的 Step 4 开始：建立统一标签目录查询、候选构建和结果校验，切换统一客户当前/历史标签读取，并替换旧字段 LIKE 使用计数。继续保持两个 LLM 主开关为 `false`，不得提前进入 Step 5 或自动标签判断。
