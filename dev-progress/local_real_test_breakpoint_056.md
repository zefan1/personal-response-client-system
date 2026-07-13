# 本地真机断点 056

时间：2026-07-13  
状态：055 方案 Step 1 已完成，Step 2 尚未开始  
对应任务：`dev-progress/tag_skill_llm_tasklist_056.md`  
审计报告：`dev-progress/tag_skill_llm_step1_audit_056.md`

## Git 恢复点

- 接手前完整代码：`2f9f7fd chore: checkpoint current production state`。
- 已推送：`origin/main`，远程仓库 `https://github.com/zefan1/personal-response-client-system.git`。
- 未纳入版本库：运行时 `uploads/` 和缓存 `scripts/__pycache__/`。

## 本次只读完成

1. 核对本机所有现存数据库，确定 `private_domain_assistant_smoke` 是当前本地最新完整基准，Flyway V67、34 张表、6 条客户。
2. 核对 `tag_categories`、`tag_values` 和 `customers` 四个旧字段真实结构、默认值、索引和外键。
3. 核对 `personality_tags`、`system_tag_suggestions`、`profile_update_suggestions` 三条旁路。
4. 统计 4 类 27 个当前标签和全部客户历史旧值。
5. 确认 12 个非空旧字段值与内部编码、中文展示名均无精确匹配，后续必须保留为未匹配原文，禁止猜测。
6. 完成数据库、后端、API、管理后台、侧边栏、Skill、直接 LLM、规则、统计、导入导出和自动化测试使用位置清单。
7. 完成后续每个模块的上下游修改清单和 Tasklist 056。
8. 未修改客户数据、数据库结构、当前标签、业务代码或 LLM 开关。

## 关键现状

- 当前存在三套标签数据：正式字典 `tag_categories/tag_values`、旧性格字典 `personality_tags`、规则自由文本建议 `system_tag_suggestions`。
- `tag_values.category_id` 没有数据库外键。
- 分类必须绑定 `Customer` 现有字段，无法真正支持动态新增分类。
- 标签编码由前端运营输入，未由后端自动生成。
- 标签值数量上限在 Service 写死为 50，数据库配置键未生效。
- Skill Prompt 有动态候选字典，但回复请求没有当前客户标签。
- 直接 LLM 回复和档案提取都没有动态标签字典。
- 标签 HIGH 结果当前作为普通档案字段自动写入，MEDIUM 继续要求员工逐条确认。
- 客户侧边栏没有显示 `personalityType`，其他三类仍是自由文本输入框。
- 客户搜索、统计、规则、导入和导出没有接入统一动态标签。
- 跟进规则的 TAG_CHANGE 使用自由文本，6 条现有建议全部 PENDING，且没有后端确认/忽略闭环。

## 验证结果

- 数据库结构对齐：34 张表，933 个 Repository 列引用，0 结构违规。
- 当前标签字典：4 个分类、27 个值，全部启用。
- 客户旧值：每个字段 3 空、3 非空；四类精确匹配均 0/3。
- 直接 LLM 档案提取开关：`false`；失败回落 Skill：`true`。
- 直接 LLM 回复开关：`false`；失败回落 Skill：`true`。
- 前端：`http://127.0.0.1:5173` 可访问。
- 后端：`http://127.0.0.1:8080` 当前不可访问；本次为避免触发后台任务，没有启动服务。

## 下次继续位置

严格从 Tasklist 056 的 Step 2 开始：先设计数据库结构和 Flyway 自动迁移，覆盖统一客户标签记录、系统判断记录、未匹配历史值、人工锁定、合并映射、外键和唯一约束。完成迁移设计前，不进入管理后台、Skill 或 LLM 业务代码修改。
