# 标签、Skill、LLM 闭环 Tasklist 056

时间：2026-07-13  
对应方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`  
当前状态：Step 1、Step 2、Step 3、Step 4、Step 5 已完成，Step 6 尚未开始。

## 生产级约束

- [x] 接手前版本已提交并推送 GitHub，恢复点为 `2f9f7fd`。
- [x] Step 4 独立分支恢复点为 `1a17b8a`；`main`/`origin/main` 保留 `b1d3527`，未被覆盖；`uploads/` 未跟踪且未提交。
- [x] Step 5 独立分支代码恢复点为 `ddcf1cc`；当前分支为 `feature/tag-step5-skill-profile-analysis`，Step 4 远程恢复点未被覆盖。
- [x] Step 1 只读检查，没有修改客户数据、数据库结构、当前标签和 LLM 开关。
- [x] 每次修改同时追踪数据库、Repository、Service、API、前端、Skill、LLM、规则、统计、导入导出和测试。
- [x] 真实数据库为唯一结构事实，任何迁移前后都核对列、类型、NULL、默认值、索引和外键。
- [x] 后端返回完整正确，前端 fallback 不承担掩盖后端缺陷的职责。
- [x] 所有迁移通过 Flyway 启动自动执行，重复启动幂等，不要求手工补数据。
- [x] 新增分类和值使用统一结构，无需增 `customers` 字段；内部编码由后端生成。

## Step 1：真实数据库和全部使用位置

- [x] 核对四个现存数据库和当前本地运行基准。
- [x] 核对 `tag_categories`、`tag_values` 全部真实列、类型、NULL、默认值、索引和外键。
- [x] 核对 `customers` 四个旧字段真实结构。
- [x] 核对 `personality_tags`、`system_tag_suggestions`、`profile_update_suggestions`。
- [x] 统计 4 类 27 个当前标签值。
- [x] 统计所有历史客户旧标签原文、空值和精确匹配率。
- [x] 搜索数据库、后端、管理后台、侧边栏、Skill、LLM、规则、统计、导入导出和测试使用位置。
- [x] 生成上下游修改清单。
- [x] 生成审计报告 `tag_skill_llm_step1_audit_056.md`。
- [x] 生成本 Tasklist 和断点 056。

## Step 2：数据库结构和自动迁移

- [x] 设计分类表新增字段：用途、选择模式、自动判断、员工修改、自动动作、最低把握度、最低消息数、冷却期、不确定策略、回复/筛选/统计/规则开关、启用和排序。
- [x] 设计标签值新增字段：含义、适用条件、禁止条件、正确例子、错误例子、同义表达、系统/员工可选、启用、排序。
- [x] 后端生成稳定分类编码和标签编码，创建后不可修改。
- [x] 新建统一客户标签当前/历史记录，保存客户、分类、标签、有效状态、来源、置信度、证据、Skill/LLM/Prompt 版本、操作者、锁定和失效原因。
- [x] 新建系统判断记录，保存通过/拒绝原因和完整可审计上下文。
- [x] 新建历史未匹配标签记录，原文不可丢失。
- [x] 新建标签合并关系和旧编码/旧字典历史映射。
- [x] 为真实关联建立外键、唯一约束和必要索引；修复当前 `tag_values.category_id` 无外键问题。
- [x] 单选分类保证同一客户同一时刻只有一条有效记录，多选分类保证去重，且分配模式必须与分类模式一致。
- [x] 自动迁移内部编码完全一致值和中文名完全一致值，多值字段支持项目现有中英文分隔符。
- [x] 当前 12 个非空旧值因 0 精确匹配全部保留到未匹配记录，不做关键词猜测。
- [x] 迁移期 `CustomerRepository` 和 `ProfileWriter` 在同一事务双写统一记录与四个旧字段；自定义分类不写旧字段。
- [x] `personality_tags` 第二字典已停用并映射到统一字典，旧行和映射关系继续保留。
- [x] `system_tag_suggestions` 已增加客户、正式标签、判断结果和未匹配引用；旧自由文本只进入未匹配记录，不进入正式标签。
- [x] 更新测试建表、MariaDB 集成测试、迁移验证脚本和 `verify_database_alignment.py`。
- [x] 空库启动、现有库升级、连续重启两次和隔离失败恢复验证通过；升级前已生成完整数据库备份。

## Step 3：分类和值完整管理

- [x] 分类列表、详情、创建、编辑、启用、停用、删除保护、合并。
- [x] 标签值列表、详情、创建、编辑、启用、停用、删除保护、合并。
- [x] 搜索、筛选、分页、排序、影响客户/规则/历史数量和导出。
- [x] 删除只允许无任何引用的记录；已使用记录只能停用或合并。
- [x] 合并预览和执行在事务中更新客户、规则、统计引用并保留旧编码映射。
- [x] 管理后台不要求运营填写英文编码。
- [x] 分类和值所有用户可见错误为中文。
- [x] 配置缓存即时刷新和定时刷新使用同一真实配置；修复当前配置键未生效和数量上限写死问题。
- [x] ADMIN/LEADER/KEEPER 权限按产品规则细分，不能只依赖 admin 路径总开关。
- [x] 更新、启停、合并预览和执行均强制提交版本号；数据库更新 SQL 不允许空版本绕过乐观锁。
- [x] 内置分类合并到无绑定字段分类时，事务内转移旧字段绑定并重新加载分类，再迁移客户、规则和历史引用。
- [x] 停用分类或标签值保留历史分配和四个旧字段；当前有效标签读取按启用状态排除，Step 4 前旧字段继续作为兼容读取来源。
- [x] 可选说明字段支持显式清空；锁合并审计、后台登录校验顺序和 CSV 公式注入防护已完成。

## Step 4：统一标签读取和校验

- [x] 建立唯一标签目录查询服务和缓存快照。
- [x] 建立统一候选构建器，供 Skill、直接 LLM、人工修改、导入、规则和测试复用。
- [x] 建立统一结果校验器：分类存在/启用、标签存在/启用、归属正确、来源允许、数量正确、证据完整。
- [x] 建立统一客户当前有效标签查询、历史查询和权限过滤。
- [x] 替换 `TagRepository.usageCount` 的旧字段 LIKE 判断。
- [x] 清除业务代码中的第二套标签列表和前端内置标签字典依赖。
- [x] 保留昵称前缀 `match.tag_removal_rules` 功能，但与客户业务标签明确隔离。

### Step 4 验收记录（2026-07-15）

- Java 全量：`mvn test`，326 tests，0 failures，0 errors，1 条条件式 MariaDB 测试跳过。
- 前端全量：`npm test`，36 个测试文件、252 tests，0 failures；`npm run typecheck` 通过；`npm run build` 通过。
- 真实构建 smoke：`npm run renderer:smoke`、`npm run electron:smoke` 均连接最新 8082 后端并通过。
- Step 4 定向：`AdminDevConsole.test.ts` 9/9；`TagAdminServiceTest` 15/15；`TagRepositoryTest` 7/7。
- 静态核验：`python scripts/verify_module_46.py`、`python scripts/verify_module_d.py` 通过。
- 数据库对齐：42 张表、24 张必需表、41 张迁移表，1,382 个 Repository 列引用，0 列/属性/枚举违规。
- 真实数据库：`private_domain_assistant_smoke`，Flyway V69；4 个分类、27 个标签值、当前统一分配 0 条；`llm.profile_extraction.enabled=false`、`llm.reply_generation.enabled=false`。
- 真实接口：管理员登录、健康接口、动态分类分页、标签值分页、非法分类创建拒绝均已验证；非法 `categoryId` 返回 404 / `90-10007`，没有写入。
- 当前运行地址：后端 `http://127.0.0.1:8082`（真实数据库、真实外部模式）；前端 `http://127.0.0.1:5174/`。健康总状态受既有 WeCom/image 外部告警影响为 DOWN，但 DB/Redis UP，Flyway 无待迁移。

## Step 5：Skill 档案分析

- [x] Skill 动态读取允许系统判断的分类和值及全部说明。
- [x] 请求包含最近有效聊天、当前档案、当前有效标签、人工锁定分类和分类更新策略。
- [x] 返回统一结构：分类编码、标签编码、把握度、证据、结果类型和多选动作。
- [x] 支持无法判断、保持当前值和多选仅新增。
- [x] Skill 返回字典外、停用、跨分类或无证据值时拒绝更新。
- [x] 在线测试、调用日志、超时、熔断和失败回退同步验证。

### Step 5 验收记录（2026-07-15）

- Step 5 代码恢复提交：`ddcf1cc feat: complete skill profile analysis`。
- Java 全量：358 tests，0 failures，0 errors，1 条条件式 MariaDB 测试跳过。
- Step 5 后端定向：14 个测试类、62 tests，0 failures，0 errors。
- 前端全量：36 个测试文件、253 tests，0 failures；`npm run typecheck` 和 `npm run build` 通过。
- 真实构建 smoke：`renderer_smoke=passed`、`electron_smoke=passed`，均连接最新 Step 5 后端。
- 静态核验：`verify_module_46.py`、`verify_module_d.py` 通过。
- 数据库对齐：42 张表、24 张必需表、41 张迁移表，1,382 个 Repository 列引用，0 列/属性/枚举违规。
- 真实数据库：`private_domain_assistant_smoke`，Flyway V69，无待迁移；当前有效统一标签分配 0 条。
- 真实运行：PROFILE_EXTRACT 在线测试使用临时绑定进入真实外部失败路径后返回错误，临时绑定已删除；发送确认在 Skill 失败时仍返回 accepted，PROFILE_EXTRACT 失败调用记录从 0 增至 1，未写入标签。
- `llm.profile_extraction.enabled=false`、`llm.reply_generation.enabled=false`，未提前进入 Step 6/7/8。

## Step 6：直接 LLM 档案分析

- [x] 直接 LLM 使用与 Skill 完全相同的动态内容和返回结构。
- [x] 多 LLM 路由切换不改变标签 Schema。
- [x] 直接 LLM 和 Skill 经过同一后端校验器。
- [x] LLM 失败按配置回退 Skill；两条路径都失败时不修改标签。
- [x] 在完整闭环上线前保持 `llm.profile_extraction.enabled=false`。

### Step 6 验收记录（2026-07-15）

- Step 6 核心代码恢复提交：`4597f4a feat: complete direct llm profile analysis`。
- 新增共享 `ProfileAnalysisPromptBuilder`，Skill 与直接 LLM 使用相同固定任务、动态 `ProfileAnalysisContext` 输入和严格 `profile_updates.fields + tag_decisions` Schema；LLM 自定义 Prompt 只能追加要求，不能替换固定契约。
- 直接 LLM 返回完整 `ProfileAnalysisResult`，严格复用 `SkillProfileAnalysisResponseParser` 和 `TagAnalysisDecisionValidator`；主 LLM HTTP 成功但 Schema/校验非法时按失败记录并继续备用候选。
- 所有 LLM 候选失败、非法或路由异常时按 `fallback_to_skill` 进入 Skill；合法的直接 LLM 结果保留标签决策并阻止重复 Skill 调用。
- 管理端支持对选定 LLM 环境执行只读 `PROFILE_EXTRACTION` 在线测试，使用生产动态候选和档案字段配置，结构化展示字段、标签动作、把握度和证据；失败时展示后端错误，不生成空结果卡。
- Java 全量：365 tests，0 failures，0 errors，1 条条件式 MariaDB 测试跳过；Step 6 定向 10 个测试类、45 tests 全部通过。
- 前端全量：36 个测试文件、254 tests，0 failures；`AdminConsole.test.ts` 34 tests；`npm run typecheck` 和 `npm run build` 通过。
- 真实构建 smoke：`renderer_smoke=passed`、`electron_smoke=passed`，连接最终 Step 6 后端。
- 静态核验：`verify_module_46.py`、`verify_module_d.py` 通过。
- 数据库对齐：42 张表、24 张必需表、41 张迁移表，1,382 个 Repository 列引用，0 列/属性/枚举违规；Flyway V69，无新增迁移。
- 真实 LLM 环境在线测试使用选定环境调用生产档案契约，真实不可达路径返回 `30-20004`，没有开启生产主开关。
- 真实失败回退：临时开启档案 LLM 后发送确认返回 `accepted=true`；LLM PROFILE_EXTRACTION 调用记录 0→1、Skill PROFILE_EXTRACT 调用记录 2→3，两条路径均失败但全库和验收客户当前有效标签仍为 0；配置随后恢复。
- 最终配置：`llm.profile_extraction.enabled=false`、`llm.reply_generation.enabled=false`、`llm.profile_extraction.fallback_to_skill=true`，未进入 Step 7/8/9。

## Step 7：自动更新、人工修改和锁定

- [x] 后端按 055 的 13 项顺序检查权限、分类、标签、消息数、把握度、证据、锁定、冷却和版本。
- [x] 单选按配置替换，整个操作单事务完成。
- [x] 多选第一版只自动新增，不自动删除。
- [x] 员工可按权限手工新增、替换、移除、锁定和解锁。
- [x] 人工修改优先并默认锁定分类，自动分析不能覆盖。
- [x] 手工和自动更新同事务维护旧字段兼容值。
- [x] 写入完整操作记录和系统判断记录。
- [x] WebSocket 推送结构化标签变化，所有已登录页面即时刷新。
- [x] 自动标签更新失败不能影响回复生成。

### Step 7 验收记录（2026-07-16）

- 后端完整测试：`mvn -q test`，379 tests，0 failures，0 errors，1 conditional skip。
- 新增缓存一致性回归：标签事件发布后刷新客户 Redis 缓存，档案读取立即返回最新版本。
- 前端完整测试：`npm run test`，36 files，255 tests；`npm run typecheck` 和 `npm run build` 通过。
- Electron 运行时：`renderer_smoke=passed`、`electron_smoke=passed`；前端地址 `http://127.0.0.1:5174/`。
- 真实接口：管理员登录、档案标签目录、人工保存、过期版本 409、锁定/解锁和清理均通过；测试客户 `13900000001` 最终标签为空、分类解锁。
- 后端运行地址：`http://127.0.0.1:8082`，数据库 `private_domain_assistant_smoke`，Flyway V69；主服务 `8080` 保持运行。

## Step 8：回复生成读取最新标签

- [ ] `ChatOrchestrationService` 不再只传 5 个基础字段，加入当前有效标签快照。
- [ ] Skill 回复和直接 LLM 回复读取同一标签快照及中文含义。
- [ ] 换一组重新读取最新标签，不复用过期标签上下文。
- [ ] 回复只受标签调整方向和语气，不暴露内部判断。
- [ ] 标签读取异常时正常生成普通回复并记录降级原因。
- [ ] 在线测试、回复来源、调用日志和自动化测试同步更新。

## Step 9：搜索、统计、规则和数据交换

- [ ] 客户搜索支持动态分类、单/多标签、任一/全部组合及现有条件组合。
- [ ] 分页、导出和数据权限与列表查询使用同一查询条件。
- [ ] 统计当前有效标签数量、门店/团队/员工/时间范围、来源、未更新原因和趋势。
- [ ] `AnalyticsRepository` 移除写死 `intent_level IN ('HIGH','MEDIUM')` 的标签语义依赖。
- [ ] 跟进规则创建/编辑从动态标签目录选择分类和值。
- [ ] 规则保存和执行都校验标签状态；停用暂停命中，合并更新引用。
- [ ] 处理现有 `system_tag_suggestions` 6 条 PENDING 记录，不丢原文且不计正式统计。
- [ ] CSV 导入、外部表格同步和写回使用统一标签校验。
- [ ] 无法识别值写入未匹配记录；外部失败不覆盖本地有效标签。
- [ ] 导出提供中文名称和内部编码。

## Step 10：全量测试和真实运行验收

- [ ] 后端标签、客户、Skill、LLM、规则、统计、导入导出自动化测试全量通过。
- [ ] 管理后台和侧边栏相关组件、Store、类型检查、构建全量通过。
- [ ] 数据库结构对齐、迁移幂等和真实历史数据守恒检查通过。
- [ ] 四类默认标签和新增自定义分类都完成全场景测试。
- [ ] 列表、详情、创建、编辑、删除、启停、合并、筛选、分页、排序、导出、统计全部可用。
- [ ] Skill 和多个直接 LLM 路径、失败回退、超时和非法返回测试通过。
- [ ] ADMIN、LEADER、KEEPER 权限和客户范围隔离通过。
- [ ] 后端、前端重启后无需手工脚本即可立即使用。
- [ ] 真实部署环境再次执行只读结构与历史值核验后再放量。
- [ ] 更新 Tasklist、最终断点、测试命令、通过数量、服务地址和运行状态。
