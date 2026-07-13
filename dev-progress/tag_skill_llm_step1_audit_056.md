# 标签、Skill、LLM 闭环 Step 1 审计报告 056

时间：2026-07-13  
对应方案：`dev-progress/tag_skill_llm_closed_loop_plan_055.md`  
执行范围：只读检查真实数据库、历史标签值、全部代码使用位置和上下游影响；未修改客户数据、数据库结构、当前标签或 LLM 开关。

## 1. 执行边界与结论

1. 接手前代码已保存到 GitHub：`2f9f7fd chore: checkpoint current production state`，已推送 `origin/main`。
2. 本机 `application.yml` 默认连接 `private_domain_assistant`，但该库当前不存在；本地历史运行记录和最新完整数据位于 `private_domain_assistant_smoke`。
3. 当前后端 `http://127.0.0.1:8080` 不可访问，PID 文件中的 `921` 已失效；前端开发服务 `http://127.0.0.1:5173` 可访问。
4. Step 1 以 `private_domain_assistant_smoke` 作为当前本地运行基准，同时横向核对了 `dev`、`real_acceptance`、`smoke_18080` 三个现存库。
5. 四个库的 `customers`、`tag_categories`、`tag_values` 列签名一致；只有迁移版本和客户数据量不同。
6. 当前 6 条客户数据中的四类旧标签字段全部是自由文本，和标签字典内部编码、中文展示名均为 0 条精确匹配，不能猜测迁移。
7. 当前至少存在三套相互独立的“标签”数据：`tag_categories/tag_values`、`personality_tags`、`system_tag_suggestions`；它们没有统一外键、统一校验或统一生命周期。
8. 当前回复生成链路没有把客户四类标签传给 Skill 或直接 LLM；Skill 仅拿到候选标签字典，直接 LLM 连候选字典也没有。
9. 当前档案提取仍把标签当普通字段：HIGH 直接写旧字段，MEDIUM 进入员工确认；这与 055 已确定的标签自动更新、人工锁定方案不一致。
10. 本报告只记录事实和后续修改边界，Step 2 数据库迁移尚未开始。

## 2. 真实数据库核验

### 2.1 现存数据库

| 数据库 | customers 精确数 | tag_categories | tag_values | 最新成功 Flyway | 说明 |
|---|---:|---:|---:|---:|---|
| `private_domain_assistant_smoke` | 6 | 4 | 27 | 67 | 当前本地完整运行基准 |
| `private_domain_assistant_real_acceptance` | 1 | 4 | 27 | 65 | 1 条客户四类旧字段均为空 |
| `private_domain_assistant_dev` | 0 | 4 | 27 | 57 | 旧迁移版本 |
| `private_domain_assistant_smoke_18080` | 0 | 4 | 27 | 57 | 旧迁移版本 |

`private_domain_assistant` 默认库不存在，因此后续启动必须显式使用真实部署环境的 `SPRING_DATASOURCE_URL`，不能把默认库名当成生产库事实。

### 2.2 `tag_categories` 真实结构

| 列 | 类型 | NULL | 默认值 | 额外属性 |
|---|---|---|---|---|
| `id` | `bigint(20)` | NO | 无 | `auto_increment` |
| `category_key` | `varchar(50)` | NO | 无 | 唯一索引 `uk_category_key` |
| `category_name` | `varchar(30)` | NO | 无 |  |
| `bound_field` | `varchar(50)` | NO | 无 | 唯一索引 `uk_bound_field` |
| `is_builtin` | `tinyint(4)` | NO | `0` |  |
| `is_enabled` | `tinyint(4)` | NO | `1` |  |
| `sort_order` | `int(11)` | NO | `0` |  |
| `created_at` | `datetime` | NO | `current_timestamp()` |  |
| `updated_at` | `datetime` | NO | `current_timestamp()` | `on update current_timestamp()` |

引擎与字符集：`InnoDB`、`utf8mb4_unicode_ci`。当前 4 行均为内置、启用状态。表的 `AUTO_INCREMENT=12`，说明本地历史测试曾创建并删除分类，但没有保留对应历史记录。

### 2.3 `tag_values` 真实结构

| 列 | 类型 | NULL | 默认值 | 额外属性 |
|---|---|---|---|---|
| `id` | `bigint(20)` | NO | 无 | `auto_increment` |
| `category_id` | `bigint(20)` | NO | 无 | 普通索引 `idx_category_id` |
| `tag_value` | `varchar(50)` | NO | 无 | 与 `category_id` 组成唯一索引 |
| `display_name` | `varchar(30)` | NO | 无 |  |
| `is_enabled` | `tinyint(4)` | NO | `1` |  |
| `sort_order` | `int(11)` | NO | `0` |  |
| `created_at` | `datetime` | NO | `current_timestamp()` |  |
| `updated_at` | `datetime` | NO | `current_timestamp()` | `on update current_timestamp()` |

引擎与字符集：`InnoDB`、`utf8mb4_unicode_ci`。当前 27 行全部启用。表的 `AUTO_INCREMENT=35`。真实数据库没有 `tag_values.category_id -> tag_categories.id` 外键，删除分类和值完全依赖业务代码。

### 2.4 `customers` 旧标签字段真实结构

| 列 | 类型 | NULL | 默认值 | 备注 |
|---|---|---|---|---|
| `personality_type` | `varchar(50)` | YES | `NULL` | 客户性格类型 |
| `body_concerns` | `varchar(500)` | YES | `NULL` | 身体关注点 |
| `intent_level` | `varchar(10)` | YES | `NULL` | 注释声明 HIGH/MEDIUM/LOW/PENDING，但真实数据存在中文值 |
| `worries` | `varchar(500)` | YES | `NULL` | 客户顾虑 |

`customers` 共 41 列，四个数据库列签名一致。Java 端不是 JPA ORM，而是 `Customer` Bean、`CustomerRowMapper` 和手写 JDBC SQL；后续数据库迁移必须同步所有读写 SQL、缓存映射和反射字段注册。

### 2.5 其他标签相关真实表

| 表 | 当前行数 | 作用 | 当前问题 |
|---|---:|---|---|
| `personality_tags` | 3 | 旧 Skill 性格标签字典 | 与 `tag_values` 重复；只有 LOYALIST/PEACEMAKER/PENDING，缺 DECISIVE；中文名也不同；Repository 当前无调用方 |
| `system_tag_suggestions` | 6 | 跟进规则生成的自由文本标签建议 | `tag_name` 不关联 `tag_values`；只有插入/去重代码，没有确认、忽略或写入客户标签的后端入口 |
| `profile_update_suggestions` | 标签字段相关 1 | MEDIUM 档案更新建议 | 标签仍走普通档案字段确认流程，不具备分类、标签 ID、证据、锁定和历史信息 |

### 2.6 数据库与 JDBC 对齐检查

执行：`SMOKE_DB_NAME=private_domain_assistant_smoke python scripts/verify_database_alignment.py`

结果：34 张表，33 张迁移定义表，933 个 Repository 列引用，缺失表 0、缺失配置 0、列引用违规 0、列属性违规 0。该检查只验证表/列存在性和已登记枚举，不验证四类旧标签值是否属于标签字典；旧值域问题必须按本报告单独处理。

## 3. 当前标签字典

| 分类 | 内部编码 | 绑定旧字段 | 值数量 |
|---|---|---|---:|
| 性格类型 | `personality_type` | `personalityType` | 4 |
| 身体关注 | `body_concerns` | `bodyConcerns` | 8 |
| 客户顾虑 | `worries` | `worries` | 9 |
| 意向等级 | `intent_level` | `intentLevel` | 6 |

当前 27 个值为：

- 性格类型：`LOYALIST`、`PEACEMAKER`、`DECISIVE`、`PENDING`。
- 身体关注：`DIASTASIS_RECTI`、`PELVIC_FLOOR`、`URINE_LEAKAGE`、`LUMBAGO`、`PUBIC_PAIN`、`STRETCH_MARKS`、`BELLY_SAG`、`WEIGHT_GAIN`。
- 客户顾虑：`FEAR_NO_EFFECT`、`FEAR_EXPENSIVE`、`FEAR_PAIN`、`FEAR_HARD_SELL`、`COMPARING`、`HUSBAND_DISAGREE`、`FAMILY_UNSUPPORT`、`NO_TIME`、`TOO_FAR`。
- 意向等级：`HIGH`、`MEDIUM`、`LOW`、`PENDING`、`CLOSED`、`LOST`。

## 4. 历史标签值统计

### 4.1 当前 6 条本地客户

四个字段均为 3 条空值、3 条非空值：

| 字段 | 历史原文 | 数量 |
|---|---|---:|
| `personality_type` | `主动咨询型` | 1 |
| `personality_type` | `务实型` | 1 |
| `personality_type` | `谨慎型` | 1 |
| `body_concerns` | `想确认腹直肌恢复情况` | 1 |
| `body_concerns` | `盆底肌松弛，担心漏尿` | 1 |
| `body_concerns` | `腹直肌分离和腰背酸痛` | 1 |
| `worries` | `想确认到店流程` | 1 |
| `worries` | `担心效果，需要案例说明` | 1 |
| `worries` | `担心时间安排` | 1 |
| `intent_level` | `高` | 2 |
| `intent_level` | `中` | 1 |

匹配结果：四个字段的非空值与对应 `tag_values.tag_value` 精确匹配均为 `0/3`，与 `display_name` 精确匹配也均为 `0/3`。`body_concerns` 和 `worries` 没有出现可证明为列表分隔符的格式，当前内容是自然语言句子，不能按逗号、顿号或关键词猜测拆分。

### 4.2 建议与规则标签历史

- `profile_update_suggestions`：1 条 `intentLevel=MEDIUM`，状态 `CONFLICT_SKIPPED`，说明旧档案建议已经发生过版本冲突。
- `system_tag_suggestions`：6 条、涉及 3 个客户，全部 `PENDING`；`可能流失` 3 条，`沉睡风险` 3 条。
- 上述两个名称不属于当前 27 个 `tag_values`，后续不能直接计入正式标签统计。

### 4.3 迁移判定

1. 当前客户旧值没有一条满足 055 的“内部编码完全一致”或“中文名称完全一致”自动转换条件。
2. Step 2 必须把这些原文写入历史未匹配记录，不能猜测映射，也不能因迁移丢失原字段内容。
3. 旧字段在所有读路径切换完成前继续同步维护；新增自定义分类不得再要求给 `customers` 增列。
4. `real_acceptance` 的 1 条客户四类字段均为空，`dev` 和 `smoke_18080` 无客户，不产生额外历史值。

## 5. 全部使用位置清单

### 5.1 数据库迁移与配置

| 文件 | 当前使用 |
|---|---|
| `src/main/resources/application.yml` | 默认数据源；`profile.extract-fields` 写死四个旧标签字段；`match.tag-removal-rules` 是昵称前缀清理，不是客户业务标签 |
| `V1__module_a_customer_cache.sql` | 创建 `customers` 四个旧字段和默认数据源字段映射 |
| `V3__module_b_skill_gateway.sql` | 创建重复字典 `personality_tags`；Skill Prompt 加入 `available_tags` |
| `V5__module_e_profile_update.sql` | 创建普通档案建议表；配置 AI 提取四个旧字段 |
| `V6__module_f_followup_rules.sql` | 创建 `system_tag_suggestions`；规则动作保存自由文本 `tagName` |
| `V21__module_41_ai_config_center.sql` | Skill Prompt 配置和昵称标签清理配置 |
| `V25__module_46_tag_management.sql` | 创建当前 `tag_categories/tag_values` 及 4 类 27 值 |
| `V61__llm_reply_generation_configs.sql` | 直接 LLM 回复 Prompt，只定义回复和 `personality_type_suggest`，没有动态标签字典 |
| `V62__llm_profile_extraction_configs.sql` | 直接 LLM 档案提取 Prompt，把标签当普通 `profile_updates` 字段 |
| `V67__localize_builtin_tag_display_names.sql` | 只本地化当前 4 类 27 值展示名 |

### 5.2 标签后台、缓存和权限

| 文件 | 当前使用/缺口 |
|---|---|
| `modules/tags/TagCategory.java`、`TagValue.java` | 与当前两张表列一一映射的 JDBC Record |
| `TagCategoryRequest.java`、`TagValueRequest.java`、`TagErrorCodes.java` | 仅支持当前简化 CRUD 字段；没有说明、判断策略、合并等生产字段 |
| `TagRepository.java` | 列表、详情查询、创建、编辑、启停、删除、旧字段占用计数、Prompt 候选值；占用计数依赖旧字段和 LIKE |
| `TagAdminService.java` | 强制分类绑定 `Customer` 现有 Bean 字段；前端必须提交标签英文编码；每类 50 个值写死；不支持合并/影响统计/动态分类 |
| `TagAdminController.java` | 分类列表/创建/编辑/删除和值创建/编辑/启停/删除；无详情、分页、后端搜索、导出、合并、影响预览 |
| `TagCacheService.java` | 缓存启用分类和值供 Skill Prompt；调度属性名与数据库配置键不一致，数据库 `tag.cache_refresh_interval_s` 当前未被此调度读取 |
| `ConfigAdminService.java` | 允许编辑 `tag.*` 配置，但 `tag.value_max_per_category` 未被 `TagAdminService` 使用 |
| `JwtAuthenticationFilter.java` | 所有 `/admin/api/v1/**` 仅 ADMIN 可访问；当前没有 LEADER 或细粒度标签管理权限 |
| `DatasourceAdminController/Service.java` | `/admin/api/v1/customer-fields` 给分类表单提供可绑定 Customer 字段，进一步固化“一分类一旧字段”模型 |

### 5.3 客户读写、缓存、导入和同步

| 文件 | 当前使用/缺口 |
|---|---|
| `Customer.java` | 四个旧字段 Bean 属性 |
| `CustomerRepository.java` | `SELECT *` 和 UPSERT 读写四个旧字段 |
| `CustomerRowMapper.java` | 数据库列到 Customer 属性映射 |
| `CustomerCacheManager.java` | Redis 序列化/反序列化四个旧字段 |
| `CustomerMergeEngine.java` | 外部同步时按非空值合并四个旧字段 |
| `CustomerProfileService.java`、`CustomerController.java` | 侧边栏客户详情、批量详情、手工更新和建议确认入口 |
| `ProfileFieldRegistry.java`、`ProfileWriter.java` | 反射读取并动态 SQL 写入四个旧字段；没有标签 ID、分类约束或值域校验 |
| `ManualEditHandler.java` | 员工可把任意字符串直接写入四个旧字段，没有标签字典检查和分类锁定 |
| `ProfileUpdateOrchestrator.java`、`ConfidenceRouter.java` | HIGH 自动写旧字段，MEDIUM 进入逐条确认；没有标签专用验证、证据、冷却、人工锁 |
| `SuggestionRepository.java`、`SuggestionQueueManager.java` | 标签建议与其他档案建议共用，确认后写旧字段 |
| `ProfileConfig.java`、`ProfileConfigProvider.java` | 提取字段列表包含四个旧字段 |
| `CustomerAdminSearchRepository.java`、`CustomerAdminListItem.java` | 管理后台客户列表只返回 `intentLevel`；只支持关键字和分页，无标签筛选/组合筛选/标签导出 |
| `DatasourceAdminService.java` | CSV 导入当前只处理 phone/nickname；字段字典包含四个旧字段，但无标签导入校验和未匹配记录 |
| `TableFieldMappingResolver.java`、`ManualSaveHandler`、`TableWriteOrchestrator`、桌面 `saveToTableService.ts` | 手工档案更新可继续同步外部表格；后续双写旧字段时必须保持同一事务/同一保存结果 |

### 5.4 Skill、直接 LLM 和回复链路

| 文件 | 当前使用/缺口 |
|---|---|
| `SkillRequestBuilder.java` | 从 `TagCacheService` 动态生成“中文名(编码)”候选字典；异常时静默返回“当前无可用标签” |
| `SkillConfigProvider.java`、`SkillGatewayServiceImpl.java` | 回复和档案提取共用 Skill Prompt/调用链 |
| `SkillResponseParser.java`、`CustomerAnalysis.java`、`ProfileUpdates.java` | 只解析普通字段更新和单个 `personality_type_suggest`，没有统一标签结果结构和严格字典校验 |
| `MockSkillHttpClient.java` | Mock 使用 snake_case 的 `body_concerns/intent_level`，与 `ProfileFieldRegistry` 的 camelCase 目标不一致，当前会被过滤 |
| `PersonalityTagRepository.java` | 读取 `personality_tags`，当前没有任何调用方，是第二套失效字典 |
| `ChatOrchestrationService.java` | 回复请求只传 phone、nickname、leadType、customerStage、followupNotes；没有传四类当前标签 |
| `LlmReplyGenerationService.java` | 直接 LLM 回复不读取 `TagCacheService`，没有候选字典和当前标签；当前开关为 false |
| `ProfileExtractionClient.java` | 先直接 LLM、失败后 Skill；当前直接 LLM 开关 false、回落 Skill true |
| `LlmProfileExtractionService.java` | 只传 existingProfile 和 targetFields，不传动态标签说明；返回值只做字段名过滤，不做标签值验证 |
| `RequestContextStore` 与换一组链路 | 保存并复用同一份缺少标签的 customer map，换一组也读不到最新标签 |

### 5.5 跟进规则、筛选、统计和导出

| 文件 | 当前使用/缺口 |
|---|---|
| `ConditionEvaluator.java` | 字段白名单写死，仅 `intentLevel` 与标签有关；规则不支持动态分类和值 |
| `RuleAdminService.java`、`FollowupRuleRepository.java`、`FollowupController.java` | 规则 CRUD/分页/启停；保存时不校验 `tagName` 是否存在、启用或属于分类 |
| `RuleMatcher.java`、`FullScanScheduler.java`、`LightweightScanScheduler.java` | 执行规则时读取旧 `Customer.intentLevel` |
| `ActionExecutor.java`、`TagSuggestionRepository.java` | TAG_CHANGE 把自由文本写入 `system_tag_suggestions`；无正式标签写入和锁定检查 |
| `FollowupTodayService.java`、`FollowupItem.java` | 返回自由文本标签建议 |
| 桌面 `followup-list/types.ts`、`followupListStore.ts`、`workbenchStore.ts` | 定义 TAG_SUGGESTION，但没有独立标签页；轮询结果会丢弃该类型，WebSocket 结果回落到 OVERDUE；没有确认/忽略动作 |
| `AnalyticsRepository.java` | 漏斗 SQL 写死 `intent_level IN ('HIGH','MEDIUM')`；无动态标签统计，且当前中文历史值不会命中 |
| `AnalyticsController/Service.java`、管理后台看板导出 | 只导出既有阶段/漏斗看板，没有标签数量、来源、趋势和权限范围统计 |
| `CustomerAdminSearchRepository.java` | 无标签筛选、任一/全部多选组合、标签分页导出 |

### 5.6 管理后台和侧边栏

| 文件 | 当前使用/缺口 |
|---|---|
| `desktop/.../admin/AdminConsole.vue` | 标签 CRUD、前端搜索、内置标签硬编码中文 fallback、LLM 档案开关、规则自由文本 tagName；不支持生产方案要求的详情/合并/影响/说明/例子/分页/导出 |
| `AdminDevConsole.vue` | 标签列表和创建值开发接口，创建值仍要求手工英文编码 |
| `customer-profile/types.ts` | 四个旧字段类型 |
| `CustomerProfilePanel.vue`、`customerProfileStore.ts` | 直接编辑 bodyConcerns/worries/intentLevel；AI 建议逐条确认；未显示 personalityType；没有按分类展示标签、来源、证据和锁定状态 |
| `replySuggestionStore.ts`、`ReplySuggestionPanel.vue` | 回复旁继续展示并确认普通档案建议，标签没有独立自动更新闭环 |
| `copyBackfillStore.ts`、`CopyBackfillAgent.vue` | 复制后档案建议确认入口，也会处理标签旧字段 |
| `stageSuggestionHandler.ts` | 客户阶段建议独立路径，后续不能因取消“标签确认”误删非标签确认流程 |
| `quick-search/templateVariables.ts` | 模板读取旧 `intentLevel`，切换统一标签后要定义兼容输出 |
| `save-to-table/saveToTableService.ts` | 手工档案保存和外部表格同步入口，后续需要兼容旧字段双写 |

### 5.7 自动化测试和验收脚本

当前直接覆盖：

- 后端：`TagAdminControllerTest`、`TagAdminServiceTest`、`SkillRequestBuilderTagLocalizationTest`、`ProfileFieldRegistryTest`、`ProfileExtractionClientTest`、`LlmProfileExtractionServiceTest`、`CustomerAdminSearchRepositoryTest`、跟进规则 Controller/Service 测试。
- 前端：`AdminConsole.test.ts`、`CustomerProfilePanel.test.ts`、`customerProfileStore.test.ts`、`ReplySuggestionPanel.test.ts`、`copyBackfillStore.test.ts`、`stageSuggestionHandler.test.ts`、`followupListStore.test.ts`。
- 脚本：`verify_module_46.py`、`verify_module_a.py`、`verify_module_e.py`、`acceptance_backend_api.py`、`acceptance_admin_batch_b.py`、`verify_database_alignment.py`。

当前缺失：统一客户标签表迁移、未匹配历史值、单选事务约束、多选去重、自动更新校验、人工锁、动态 Skill/LLM 一致性、回复读取最新标签、动态筛选/统计/规则/导入导出、合并与停用影响、权限隔离和重启幂等测试。

### 5.8 同名但不是客户业务标签的链路

`MatchConfig`、`MatchConfigProvider`、`TagRemovalProcessor` 以及 `match.tag_removal_rules` 用于去除昵称中的 `L1-`、`VIP-` 等前缀标记，不是本方案的客户业务标签。后续重构不能误删该匹配功能，但配置命名应避免与正式客户标签混淆。

## 6. 上下游修改清单

### 6.1 Step 2 数据库与迁移必须同步

- 新增分类策略字段、标签语义字段、统一客户标签记录、系统判断记录、未匹配历史值、合并别名/历史关系。
- 为 `tag_values.category_id`、客户标签关联等建立真实外键和唯一约束；单选分类的并发一致性必须由事务和数据库约束共同保证。
- 自动迁移四个旧字段；当前 12 个非空旧值全部进入未匹配记录，保留原文。
- 明确处理 `personality_tags`：迁入统一字典后停用旧读路径，不能继续成为第二真相源。
- 明确处理 `system_tag_suggestions`：迁为规则判断记录或废弃自由文本标签语义，不能直接混入正式标签。
- 同步测试数据库结构、`verify_database_alignment.py`、Repository SQL 和配置键。

### 6.2 后端统一入口必须同步

- 新建统一标签目录读取、候选构建、值校验、客户当前标签查询、人工修改、自动更新、锁定/解锁、停用/合并和历史查询服务。
- `TagAdminController/Service/Repository/Cache` 改用新结构；编码由后端生成且不可修改。
- `CustomerController`、`CustomerProfileService`、`ProfileWriter`、`ManualEditHandler` 改为调用统一服务，旧字段仅在迁移期同事务双写。
- 所有接口执行客户范围和角色权限检查；后台标签配置权限不能继续只靠“所有 admin 路径只有 ADMIN”的粗粒度规则。
- 审计和 WebSocket 必须记录/推送结构化分类 ID、标签 ID、来源、证据和版本。

### 6.3 Skill、LLM 和回复必须同步

- Skill 与直接 LLM 使用同一份后端动态标签定义、同一输出 Schema、同一校验器。
- 档案分析只处理允许系统判断且未人工锁定的分类；不确定时保持原值。
- 回复、换一组、在线测试和调用日志读取同一份当前有效标签。
- 标签更新失败不得阻断正常回复；失败原因必须可追查。
- `MockSkillHttpClient`、Prompt 版本和多 LLM 路由测试同步更新。

### 6.4 页面、规则、统计和数据交换必须同步

- 管理后台补齐分类和值的列表、详情、创建、编辑、启停、删除保护、合并、搜索、筛选、分页、排序、影响数和导出。
- 客户侧边栏按动态分类显示当前标签、来源、证据和锁定；员工可增删改和解锁，标签不再走逐条 AI 建议确认。
- 客户搜索、统计、跟进规则改读统一当前有效标签，并保持 ADMIN/LEADER/KEEPER 数据范围一致。
- 规则保存时校验分类和值；停用后暂停命中，合并后更新引用。
- CSV/外部同步/表格写回使用统一校验；无法识别值写入未匹配记录，不覆盖本地有效标签。
- 导出同时提供内部编码和中文名称。

## 7. Step 2 进入条件

Step 1 已满足 055 的完成标准：真实结构、每种历史值数量、全部使用位置和上下游影响已记录，且没有修改客户数据、数据库结构、当前标签或 LLM 开关。下一步只能从 Tasklist 056 的 Step 2 开始，先设计并实现可启动自动执行、幂等、可回滚验证的数据库迁移。
