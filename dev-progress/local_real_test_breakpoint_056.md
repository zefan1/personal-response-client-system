# 本地真机断点 056

时间：2026-07-14
状态：055 方案 Step 1、Step 2 已完成，Step 3 尚未开始
对应任务：`dev-progress/tag_skill_llm_tasklist_056.md`
执行方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`

## Git 和数据库恢复点

- 接手前完整代码：`2f9f7fd chore: checkpoint current production state`，已推送 `origin/main`。
- Step 1 审计代码：`a2e12b0 docs: complete tag closed-loop step 1 audit`，已推送 `origin/main`。
- Step 2 代码恢复点：包含本文件的后续提交，完成后推送 `origin/main`。
- V68 前数据库备份：`.tools/backups/private_domain_assistant_smoke_pre_v68_20260714_093354.sql.gz`。
- 备份大小：53,939 字节；SHA-256：`8cd611cf59b0329b9d828a92ce1fbe5b1cd3cc3fcf97572fa0c9e74da9dcefd9`。

## Step 2 已完成

1. 新增 Flyway `V68__unified_customer_tag_foundation.sql`，扩展分类策略和标签语义字段。
2. 新建统一客户标签分配、分类锁、分析运行、分析结果、历史未匹配和旧字典映射表。
3. 外键保证客户、分类和值关系真实存在；复合外键保证标签不能跨分类、分配模式不能偏离分类模式。
4. 生成列和唯一索引保证单选分类最多一个当前值，多选标签当前值不重复。
5. 27 个内置标签补齐含义、适用/禁止条件、正反例和同义词；`CLOSED/LOST` 禁止系统自动选择。
6. 历史迁移只接受唯一精确编码或中文名，多值支持逗号、顿号、分号、竖线、换行和制表符，不使用同义词猜测。
7. `personality_tags` 已停用并保留到统一字典的映射；旧 `system_tag_suggestions` 只进入未匹配历史，不进入正式标签。
8. 分类和值内部编码由后端生成；自定义分类不再强制绑定 `customers` 旧字段。
9. `CustomerRepository` 和 `ProfileWriter` 已接入事务内旧字段双写桥接，现有同步、手工档案和 AI 档案写入链路不断。
10. 标签缓存周期和每分类数量上限读取数据库配置，不再使用业务硬编码。

## 当前真实数据库

- 数据库：`private_domain_assistant_smoke`。
- Flyway：V68，V68 成功历史记录 1 条。
- 表数量：40；客户数量：6。
- 当前统一标签分配：0。当前 12 条非空旧字段无精确匹配，因此没有猜测生成正式标签。
- 历史未匹配：18，其中客户旧字段 12，旧规则自由文本建议 6。
- 6 条旧规则建议全部关联客户和未匹配记录；3 条旧性格字典全部关联统一标签后停用。
- 四个旧字段原文与升级前完全一致。
- `llm.profile_extraction.enabled=false`，`llm.reply_generation.enabled=false`，未开启 LLM。

## 验证结果

- Java 全量测试：237 条，0 失败，0 错误；条件式 MariaDB IT 在全量测试中跳过 1 条。
- MariaDB 空库集成测试单独实际执行：1 条，0 失败，0 跳过；V1-V68 共 36 个迁移文件执行成功，第二次迁移执行数为 0。
- 历史克隆迁移：`assignments=9 active=8 unmatched=18 constraints=7`。
- 数据库结构对齐：40 张表，22 张重点表，1,133 个 Repository 列引用，0 结构/列/默认值/枚举违规。
- 当前库连续启动两次：第二次日志为 `Schema private_domain_assistant_smoke is up to date. No migration necessary.`。
- 管理员登录和标签只读接口通过：4 个分类、27 个值，新增策略字段可完整返回。

## 当前服务

- 后端：`http://localhost:8080`，当前 PID 文件为 `.tools/runtime/backend.pid`。
- Windows 当前 WSL 端口转发通过 `localhost` 可访问；`127.0.0.1` 的 IPv4 转发本次未建立。
- 前端未在 Step 2 修改，Step 3 才开始完整管理功能改造。

## 下次继续位置

严格从 Tasklist 056 的 Step 3 开始：完成分类和值的列表、详情、创建、编辑、启停、删除保护、合并、搜索、分页、排序、影响统计、导出和权限。继续保持两个 LLM 主开关为 `false`，不得提前进入自动标签判断。
