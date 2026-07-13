# 运营后台产品覆盖审计

审计日期：2026-07-04

角色：子代理 E，验证与手册差异审计。

约束：本次只审计并写入本文档，不修改任何源码。

## 一、正式端形态边界

| 资料范围 | 正式端形态 | 说明 | 验收入口 |
|---|---|---|---|
| 02 桌面端侧边栏 | Electron 侧边栏 | 面向管家/组长的窄侧边栏工作端，负责聊天识别、回复建议、复制回填、客户档案、跟进、速搜、求助、异常提醒、工作台、离线缓存等。依赖 Electron 的剪贴板、窗口截图、全局快捷键、IndexedDB、WebSocket 能力。 | Electron 应用本体。Vite/浏览器页面只能作为渲染预览和自动化 smoke target。 |
| 40-51 运营 A-L | 独立全屏运营后台 | 面向 ADMIN/LEADER 的全屏 Web 管理产品，负责配置、内容、账号、规则、标签、分析、版本、公告、审计、健康监控。必须是业务页面，不是 API 控制台。 | 浏览器中的运营后台路由与菜单。 |
| 开发/联调辅助 | 开发调试台 | 仅用于开发、联调、接口可达性验证。可以展示 method/path、JSON、target id 等技术信息。 | 只能在开发调试入口或显式 debug 页面，不能混入生产后台菜单。 |

结论：当前计划必须继续把三类入口硬隔离。生产运营后台的验收对象是 A-L 业务页面完整流程，不是 `/admin/api/v1/*` 接口调用覆盖率。

## 二、运营后台 A-L 最小用户流程覆盖

| 模块 | 手册定位 | 生产后台至少应覆盖的流程 |
|---|---|---|
| A Skill 场景管理 | Skill 绑定、在线测试、调用监控 | 绑定分组列表；按 scene/leadType/日期筛选；新增/编辑绑定表单；删除绑定二次确认；启用/停用开关，停用最后一个绑定需 warning 确认；在线测试区含输入、加载、结果、失败提示；监控看板含空状态和加载失败重试。 |
| B AI 配置中心 | Skill/识图环境、Prompt、辅助规则 | Skill 环境列表、识图环境列表；新增/编辑环境；激活环境二次确认；删除非激活环境确认，禁止删除激活/最后环境；识图测试成功/失败详情；Prompt/红线编辑；版本历史、预览、恢复确认；配置保存失败提示与表单保留；不得展示完整 API Key。 |
| C 客户数据对接 | 数据源、字段映射、同步、CSV 导入 | 数据源列表；筛选/状态查看；新增/编辑数据源；启用/禁用；换表；删除确认；字段映射编辑、增删行、启停映射、自动识别、手动列名降级；映射版本列表/对比/恢复确认；同步状态看板、失败详情；手动同步；CSV 上传、预览、导入结果、失败明细；空状态与加载失败重试。 |
| D 速搜内容管理 | 五类速搜内容、图片上传、排序 | Tab 列表、搜索、分页；新增/编辑内容 Drawer；实时预览；启用/禁用；批量启用/禁用/删除；删除确认；图片上传/预览/替换/删除；排序管理；空状态、网络断开缓存态、保存失败、上传失败、COS 不可用提示。 |
| E 账号与权限 | 账号生命周期、角色、组长关系、安全策略 | 账号分页列表；角色/启用状态筛选和搜索；创建/编辑账号；启用/停用即时开关且防自停；密码重置；删除账号二次确认且防自删、防删除有下属组长；组长下拉；登录失败/账号停用提示；空状态、保存失败与权限失败提示。 |
| F 跟进规则引擎配置 | 规则列表、条件编辑、动作配置 | 规则列表；按动作类型筛选和关键词搜索；新增/编辑规则 Drawer；条件构建、动作配置、优先级；启用/停用，停用需确认；批量启用/停用/删除；删除自定义规则确认，内置规则不可删；空状态、校验失败、保存失败、toggle 回弹。 |
| G 客户标签与分层管理 | 标签分类和值管理 | 分类/标签树或列表；按分类/状态搜索筛选；新增/编辑分类；新增/编辑标签值；启用/禁用；删除分类/标签值确认；内置分类保护；在用标签禁止删除并给出失败提示；空状态与保存失败提示；标签变更应触发配置刷新。 |
| H 运营分析看板 | 漏斗、转化、效率、内容、风险分析 | 全屏看板；日期、线索类型、同事/组长等筛选；至少覆盖 overview、漏斗、效率、内容排行、风险等数据区；API 并行加载、自动刷新、CSV 导出；各数据区独立空状态/失败重试；权限范围区分 ADMIN/LEADER/KEEPER；筛选状态 URL 保留。 |
| I 版本管理 | 桌面端版本发布、撤回、安装包 | 版本列表和状态筛选；新增草稿；安装包上传或手动下载链接；编辑草稿；发布二次确认；撤回需填写原因并二次确认；仅未发布草稿可删除并确认；上传失败可重试或改手动链接；版本检测/上报由桌面端消费，后台需有失败提示。 |
| J 系统公告 | 手动公告、定时公告、自动告警公告 | 公告分页列表；按状态/级别/来源筛选；创建立即发布/定时发布；停止公告确认；删除仅已停止公告且两步确认；自动告警只读展示；定时发布可编辑/取消直到发布前；空状态、发布失败、广播失败降级、权限失败提示。 |
| K 操作审计日志 | 只读追溯、筛选、详情、导出 | 筛选栏：action 多选、operator、时间范围、targetType、关键词；分页列表；行内详情展开，JSON 解析失败降级纯文本；CSV 异步导出、上限确认、轮询、失败重试；严格只读，无编辑/删除；空状态、加载失败、导出失败提示。 |
| L 系统健康监控 | 5 个核心组件状态与告警历史 | 健康状态卡片看板；最近刷新时间、手动刷新、30 秒自动刷新；5 组件状态；未恢复告警置顶展开、已恢复折叠；告警类型/状态/时间前端筛选；detail JSON/纯文本展示；403 空状态；连续失败暂停自动刷新并提示。 |

## 三、生产后台禁止出现的 API 调试态

以下文案/交互只能存在于开发调试台，禁止出现在正式运营后台 A-L 页面：

- 可见 HTTP method/path，例如 `GET /admin/api/v1/...`、`POST /admin/api/v1/...`、`PUT /admin/api/v1/...`、`DELETE /admin/api/v1/...`。
- 面向用户的“请求体 JSON”“响应 JSON”“原始请求”“执行接口”“接口路径”等调试文案。
- 需要管理员手动填写“目标 ID”“target id”“resource id”后才能操作的表单。
- 要求用户从响应结果中复制 id，再粘贴到另一个输入框执行删除、停用、恢复、发布等操作。
- 通用 API 执行面板、method 下拉、path 输入框、raw body textarea、curl 风格说明。
- 将生产操作命名为“调用接口”“执行请求”“发送 JSON”的按钮。

允许的例外：运营A 的 Skill 测试结果、运营K 的审计详情、运营L 的告警 detail 可折叠展示结构化 JSON，但它们必须作为“查看详情/原始返回/技术排障信息”的次级区域，不得成为完成业务操作的必要步骤。

## 四、本轮第三点补齐记录

更新时间：2026-07-04。

执行范围：按用户要求先做第三点，即 A-L 运营后台逐模块手册级补齐；真实 API key 联调暂不执行，签名发版暂不执行但继续作为未完成生产事项记录。

本轮已补齐：

- A Skill：增加 scene/leadType/时间窗口筛选、可用 Skill 下拉、在线测试、调用监控摘要。
- B AI 配置：修正后端 `active` 字段识别；增加环境编辑/删除、Prompt 版本历史与恢复；配置列表兼容后端 Map 返回。
- C 数据源：增加数据源编辑、换表、删除、列名识别、映射对比、映射版本恢复；CSV 导入改为后端真实要求的 multipart `file` 上传，并展示导入结果/最近记录。
- D 速搜内容：增加图片上传/替换/清除、批量启用/停用/删除、卡片选择。
- E 账号权限：增加角色/状态/关键词筛选、停用/删除确认、防自停/防自删。
- F 跟进规则：列表加载接后端筛选参数，停用加确认，编辑时从 `conditionJson` 回填业务字段。
- G 标签分层：增加标签分类筛选、标签值编辑/启停/删除、分类删除与内置保护。
- H 分析看板：增加日期/线索类型筛选，补 analytics health/lifecycle 数据块，支持当前看板 CSV 导出。
- I 版本管理：增加状态/平台筛选、编辑、发布确认、删除确认；签名安装包仍属于上线前未完成事项。
- J 系统公告：增加状态/级别筛选、编辑、停止确认、删除确认。
- K 审计日志：增加对象类型/日期筛选、详情展开、导出任务状态刷新。
- L 系统健康：改为后端真实 `components` 结构，展示 db/redis/skill/imageRecognition/wecomTable 五组件、最近刷新时间、告警筛选/详情展开、连续失败暂停自动刷新。

本轮关键修正原因：

- 运营后台不能只做到接口可调；用户需要从业务按钮和表单完成动作。
- 前端必须与后端真实契约对齐：AI 环境激活字段是 `active`，配置列表是 Map，CSV/图片上传是 multipart。
- 调试信息仍只允许存在于开发调试台，生产后台质量门继续拦截 API 控制台式文案。

本轮验证命令：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- AdminConsole.test.ts` -> passed，5 tests。
- `cd desktop && npm run test` -> passed，29 files / 150 tests。
- `python scripts\verify_admin_product_surface.py` -> passed，0 violations。
- `python scripts\verify_desktop_component_test_coverage.py` -> passed，14/14 components。

仍需保留的生产未完成事项：

- 真实外部 API key 未准备，本轮未跑 live provider acceptance；后续需设置 `PDA_LIVE_SKILL_BASE_URL`、`PDA_LIVE_SKILL_API_KEY`、`PDA_LIVE_IMAGE_BASE_URL`、`PDA_LIVE_IMAGE_API_KEY`、`PDA_LIVE_TABLE_BASE_URL`、`PDA_LIVE_TABLE_API_KEY` 并运行 `python scripts\acceptance_real_external_live.py`。
- 正式上线前仍需签名安装包验证；当前未配置生产证书，不执行 `--require-signed-package` 强门。

## 五、待主控确认/补做

## 七、2026-07-07 运营后台生产化补齐记录

触发背景：用户确认“AI 与 Skill 配置”改名为“配置中心”，并要求每个模块继续对开发大纲和开发手册检查遗漏，使用子代理 agent，所有改动按生产级交付约束追溯前端/API/service/ORM/数据库链路。

子代理审计结果：

- 已相对闭环：A Skill 场景管理、B 配置中心、D 速搜内容管理、G 客户标签与分层。
- 高优先级缺口：C 数据对接诊断信息、E 账号契约与组长选择、F 跟进规则构建器、H 分析看板明细化、I 版本状态动作约束、J 公告 source 与动作约束、K 审计字典/分页/摘要、L 健康 refreshInterval/detail/告警筛选。

本轮已落地：

- B 命名：正式后台与开发调试台显示统一为“配置中心”，移除“AI 与 Skill 配置 / Skill 配置”残留文案。
- E 账号：编辑账号 payload 与后端 `AccountUpdateRequest(displayName, role, leaderId, isEnabled)` 对齐；新增/编辑 payload 分开；直属组长从数字输入改成启用 LEADER 下拉；账号列表展示 `leaderName` 与 `lastLoginAt`。
- I 版本：版本列表展示 `publishedAt/revokedAt/revokeReason/alternativeVersion`；按后端 `DesktopVersionService` 约束收口动作，DRAFT 可编辑/发布/删除，PUBLISHED 可撤回，REVOKED 只读。
- J 公告：新增 `source` 筛选；展示人工/自动来源、发布时间/过期时间；自动公告只读；只有 SCHEDULED 且未停止公告可编辑；只有未停止公告可停止；只有已停止公告可删除。
- K 审计：接入 `/admin/api/v1/audit-logs/actions` 的 `actions(label/group)` 与 `targetTypes`；列表展示 `actionLabel/actionGroup/targetTypeLabel/detailSummary/detailParsed`；接入 `total/page/size/totalPages/retentionDays/earliestCreatedAt`；导出下载改为带 Bearer token 的 fetch blob 下载，避免 window.open 丢鉴权。
- L 健康：使用后端 `/admin/api/v1/health.refreshIntervalS` 调整自动刷新间隔；展示后端告警 `alertType/level/status/occurredAt/resolvedAt/detail`，支持状态与级别筛选，显示持续时长。

本轮验证命令：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，8 tests。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts src/renderer/modules/admin/AdminDevConsole.test.ts src/renderer/App.test.ts` -> passed，16 tests。
- `cd desktop && npm run test -- --run` -> passed，30 files / 170 tests。
- `python scripts/verify_admin_product_surface.py` -> passed，0 violations。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q test'` -> passed。
- `git diff --check` -> no whitespace errors; only existing CRLF conversion warnings.

仍需后续继续补齐：

- C 数据对接：把 `/datasources/{id}/columns` 的 `fetchStatus/externalFetchAvailable/fallback/fetchError`、`/mappings/compare` diff 明细、CSV 行级 errors 做成用户可读诊断区。
- F 跟进规则：条件构建器仍偏窄，后续要承接多条件 AND/OR、动作差异化配置、业务预览和批量启停/删除。
- H 分析看板：目前仍是摘要块，后续需员工/组长筛选、明细表、排行表、风险客户列表、独立失败/空状态、自动刷新和基于真实加载数据的 CSV。
- K 审计：本轮先做单 action 筛选与分页，手册中的 action 多选可继续深化。

## 八、2026-07-07 继续补齐 C/F/H 记录

触发背景：目标仍是“完成上面这个计划”，不能只停在上轮配置中心/账号/版本/公告/审计/健康。本轮继续按子代理审计和后端真实契约补齐 C、F、H。

本轮子代理与本地核查：

- C 数据对接由主线程直接核查 `DatasourceAdminController/Service/Repository`，确认列名、映射对比、CSV 导入均已有后端字段。
- F 跟进规则由子代理 Curie 只读审计，确认真实 actionType 为 `ALERT/TAG_CHANGE/NOTIFY_LEADER`，运行时条件叶子必须使用 `op`，字段为 `lastFollowupHours`，后端返回内置字段是 `builtin`。
- H 分析看板由子代理 Hubble 只读审计，确认 analytics 接口真实列表 key 和字段，并指出 `GENERAL` 会触发后端 `leadType invalid`。

本轮已落地：

- C 客户数据对接：列名识别展示 `fetchStatus/source/fallback/fetchError`，明确告诉运营同事真实表格取样是否可用；映射差异展示 `added/removed/changed/unchanged` 明细；CSV 导入展示总行数、新增、更新、跳过和行级 errors；最近导入记录展示文件名、数量和错误摘要；映射保存保留后端 `FieldMappingDto.enabled`，不再一律写成 `enabled:true`；`listFrom` 增加 `mappings` key，修复保存映射会提交空数组的链路 bug。
- F 跟进规则：修正前端 actionType 从错误的 `TAG_SUGGESTION` 改为后端真实 `TAG_CHANGE`；规则条件 JSON 改为 `{ operator:"AND", conditions:[{ field:"leadType", op:"EQ" }, { field:"lastFollowupHours", op:"GT" }] }`，保证运行时 `ConditionEvaluator` 能消费；`actionConfig` 改用 `alertLevel/reminderType/tagName`；内置规则删除保护兼容 `builtin/isBuiltin`；开发调试台 actionType 示例同步为 `ALERT/TAG_CHANGE/NOTIFY_LEADER`。
- H 运营分析看板：移除会导致后端 400 的 `GENERAL` 线索类型选项；增加 caller 筛选；按真实响应 key 渲染使用趋势、同事效能、客户来源、阶段分布、生命周期估算、风险客户、内容排行明细表；CSV 导出改为多段数据，包含摘要和各明细区行数据。

本轮验证命令：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，9 tests。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts src/renderer/modules/admin/AdminDevConsole.test.ts src/renderer/App.test.ts` -> passed，17 tests。
- `python scripts/verify_admin_product_surface.py` -> passed，0 violations。
- `cd desktop && npm run test -- --run` -> passed，30 files / 171 tests。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q test'` -> passed。
- `git diff --check` -> no whitespace errors; only existing CRLF conversion warnings.

仍需后续深化：

- H 看板的独立失败态/区块级 `Promise.allSettled` 和 5 分钟自动刷新尚未做，本轮先完成真实字段展示与导出。
- F 规则构建器仍未暴露复杂 AND/OR；由于后端 `orGroups` 保存可过但运行时不消费，本轮刻意不做，以免生成“看起来高级但不生效”的配置。
- K 审计 action 多选仍可继续深化。

高风险遗漏与后续继续深化：

1. 待主控确认/补做：若当前运营后台仍是 API 控制台形态，需要补成 A-L 业务页面。接口可达不等于产品验收通过。
2. 待主控确认/补做：需要逐模块检查是否有完整的列表/看板、筛选、创建/编辑、删除/停用或恢复、确认、空状态、失败提示。尤其 A/B/C/D/E/F/G/I/J 属于高频配置变更页，缺任一环节都会让后台不可交付。
3. 待主控确认/补做：运营G 标签与分层、运营H 分析看板、运营I 版本管理、运营J 公告、运营K 审计、运营L 健康监控容易被做成只读或调试面板，需按手册补足筛选、状态、确认、失败态。
4. 待主控确认/补做：桌面端正式入口必须是 Electron 侧边栏；`http://127.0.0.1:5173` 只能标注为 Vite 渲染预览/烟测入口，不能作为最终同事使用入口。
5. 待主控确认/补做：生产后台菜单中如存在“API 测试”“调试台”“接口控制台”类入口，应移出生产后台或加开发环境隔离。
6. 待主控确认/补做：所有危险操作必须有业务化确认文案，包括删除、停用最后一条路由、激活环境、恢复版本、发布/撤回版本、停止/删除公告、导出超限等。
7. 待主控确认/补做：所有失败态必须是业务语言和可恢复动作，不应只吐错误码或后端异常；错误码可作为次级排障信息。
8. 待主控确认/补做：账号、客户、审计、公告、版本等列表需要脱敏和权限边界，避免正式后台暴露完整手机号、API Key、Token、明文密钥。

## 六、读取资料与写入路径

## 九、2026-07-07 H/K 与配置中心链路补齐记录

触发背景：用户继续强调生产级交付约束，要求使用子代理 agent，并在每个模块开发时回对开发大纲/开发手册。子代理 Huygens 针对“配置中心、H 运营分析、K 审计日志”做了前端/API/service/DB 链路审计，指出多处保存成功但运行时不生效、筛选入口存在但数据源未加载、搜索文案与后端 SQL 不一致的问题。

本轮已落地：

- B 配置中心运行时链路：`SkillConfigProvider` 运行时优先读取配置中心新 key `skill.system_prompt_format` / `skill.system_prompt_red_lines`，并保留旧 key `skill.system_prompt_template` / `skill.red_lines` 兜底；红线 JSON 数组转换为逐行文本，避免后台保存成功但 Skill 请求仍使用旧 Prompt。
- B 环境编辑链路：`AiEnvironmentService` 保持创建环境时 API Key 必填；编辑已有 Skill/识图环境时允许不传 `apiKey` 并由 `AiEnvironmentRepository` 保留数据库 `api_key/api_key_last4`，解决脱敏密钥无法回填导致只能改名/地址也保存失败的问题。
- A/B 线索类型枚举：Skill 场景筛选和 Skill 表单移除后端不支持的 `GENERAL`，保存只使用 `TUAN_GOU/XIAN_SUO/PENDING`，避免请求 `leadType=GENERAL` 查空或误判配置丢失。
- H 运营分析：`loadInsightOps` 不再用一个大 `Promise.all` 让任一 analytics 接口拖垮整页；新增区块级 `Promise.allSettled`、失败摘要、区块失败提示和重试入口，保留已成功/旧数据；新增 5 分钟自动刷新，页面不可见或不在 H 看板时跳过；进入 H 看板时并行拉 `/admin/api/v1/accounts?page_size=100`，caller 下拉不再依赖用户先打开“账号与权限”。
- H 内容排行：前端不再向 `/analytics/content-ranking` 传 `leadType`，避免后端当前 SQL 无 lead_type 支撑时出现“看起来筛了但实际没筛”的假筛选；其他 analytics 接口继续传真实 leadType/caller。
- K 审计日志：动作筛选从单选改为后端已支持的多选逗号串，列表查询和导出请求共用同一组 `action=CREATE_NOTICE,UPDATE_NOTICE` 条件；筛选摘要展示已选动作中文名。
- K 审计搜索：前端统一搜索框只传 `keyword`，后端 keyword 扩展到 `operator/detail/target_id/target_type/action`，保证“搜索操作人或对象”实际可命中对象 ID、对象类型、动作和详情；保留独立 `operator` 参数给未来单独操作人筛选使用。

本轮新增/更新测试：

- 前端 `AdminConsole.test.ts`：覆盖 H analytics 单接口失败时其它区块仍可用并显示失败摘要；覆盖 K 多动作筛选列表查询和导出 payload 都使用同一逗号串；覆盖 H 看板直接加载 caller 账号列表。
- 后端 `SkillConfigProviderTest`：覆盖配置中心新 Prompt key 优先级、红线 JSON 数组转换和旧 key 兜底。
- 后端 `AiEnvironmentRepositoryTest`：覆盖编辑环境不传 API Key 时保留原密钥与 last4。
- 后端 `AuditLogRepositoryTest`：覆盖 keyword 能命中对象 ID、详情和操作人。

已执行验证：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，11 tests。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q "-Dtest=SkillConfigProviderTest" test'` -> passed。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q "-Dtest=AiEnvironmentRepositoryTest" test'` -> passed。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q "-Dtest=AuditLogRepositoryTest" test'` -> passed。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q "-Dtest=AuditLogControllerTest" test'` -> passed。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q "-Dtest=AiConfigControllerTest" test'` -> passed。

仍需继续深化：

- H 内容排行若未来必须按线索类型筛选，需要给审计/内容使用记录补稳定 leadType 来源字段或可索引 JSON 字段，再恢复后端 SQL 过滤；当前前端已避免假筛选。
- K 若要完全符合手册，可继续拆出“操作人”独立筛选、`targetId` 独立输入、导出 count 预估和导出完成自动下载。
- A/B 配置中心仍可继续补“换一组最大次数”“识图 Prompt”独立编辑、Prompt diff/稳定版本预览等高阶配置，但本轮已修复会影响生产生效的主链路。

## 十、2026-07-07 配置中心与审计筛选继续补齐记录

触发背景：继续向“每个模块都要对开发大纲和开发手册补齐”的目标推进，优先选择已经有后端配置项/数据库字段/API 参数支撑、但正式运营后台还没有呈现给用户的能力，避免做不生效的假功能。

本轮已落地：

- B 配置中心：在“Prompt 与规则”中新增 `image.recognition_prompt` 识图提示词编辑，和 `skill.regenerate_max_count` 换一组次数上限编辑；保存时与 Skill Prompt、红线、昵称前缀规则、降级回复一起写入 `/admin/api/v1/configs/{key}`。这两个 key 已由 DB 迁移和后端配置校验支持。
- K 审计日志：新增对象 ID 独立筛选输入，列表查询和导出 payload 都传 `targetId`；筛选摘要明确展示对象类型和对象 ID。统一搜索框继续走 `keyword`，对象 ID 精确定位走 `targetId`，避免把两种搜索语义混在一起。

本轮新增/更新测试：

- 前端 `AdminConsole.test.ts` 新增配置中心保存测试，覆盖 `skill.system_prompt_format`、`skill.system_prompt_red_lines`、`image.recognition_prompt`、`skill.regenerate_max_count`。
- 前端 `AdminConsole.test.ts` 更新审计多动作筛选测试，覆盖 `targetId=notice-1` 同时进入 GET 查询和导出 POST payload。

已执行验证：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，12 tests。

已读取资料：

- `C:\Users\85314\Desktop\私域工具\02_桌面端侧边栏_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\40_运营A_Skill场景管理_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\41_运营B_AI配置中心_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\42_运营C_客户数据对接_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\43_运营D_速搜内容管理_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\44_运营E_账号与权限_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\45_运营F_跟进规则引擎配置_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\46_运营G_客户标签与分层管理_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\47_运营H_运营分析看板_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\48_运营I_版本管理_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\49_运营J_系统公告_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\50_运营K_操作审计日志_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\51_运营L_系统健康监控_开发实现手册.md`
- `C:\Users\85314\Desktop\私域工具\私域聊天辅助系统_开发大纲.md`
- `C:\Users\85314\Desktop\私域工具\decisions.md`
- `C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- `C:\Users\85314\Desktop\私域工具\私域辅助系统\dev-progress\product_direction_gap.md`

写入文件：

- `C:\Users\85314\Desktop\私域工具\私域辅助系统\dev-progress\admin_product_coverage_audit.md`

## 十一、2026-07-07 G 标签与分层生产链路补齐记录

触发背景：继续按用户的生产级交付约束核对运营 G。手册要求分类绑定客户档案字段、`tagValue` 建完锁定、标签变更进入配置刷新链路；本轮从前端页面、API、service、repository、数据库迁移和 Skill Prompt 消费链路重新追溯。

本轮链路核查：

- 数据库真实结构：`V25__module_46_tag_management.sql` 中 `tag_categories.bound_field` / `category_key` 唯一，`tag_values(category_id, tag_value)` 唯一，`tag_value` 是代码值。
- 后端 API：`TagAdminController` 暴露 `/admin/api/v1/tags/categories` 与 `/admin/api/v1/tags/values` CRUD；`TagAdminService` 创建分类校验 boundField、创建标签校验代码格式，更新标签值只改 `displayName/isEnabled/sortOrder`。
- 删除保护：`TagAdminService.deleteValue` 通过 `TagRepository.usageCount` 检查 `customers` 的绑定字段、`personality_type/body_concerns/worries`，被使用时返回标签域错误。
- 生效链路：标签保存后写 `audit_logs` action=`UPDATE_TAG`，发布 `ConfigChangedEvent("tag_config")`；`TagCacheService` 收到事件清缓存；`SkillRequestBuilder` 调用时读取启用标签并注入 `{{available_tags}}`。

本轮已落地：

- G 前端加载组织/规则/标签时同时获取 `/admin/api/v1/customer-fields`，字段字典失败不阻断账号/规则/标签主数据展示，使用已有缓存或默认字段兜底。
- 标签分类卡片显示客户字段中文名 + 字段 key，例如 `意向度字段（intentLevel）`，避免运营只看到后端字段名。
- 新增分类表单的 `boundField` 从文本输入改为客户字段下拉，并过滤已被其他分类绑定的字段，前端尊重数据库唯一约束。
- 编辑分类不再提交 `categoryKey/boundField`，避免运营误以为能修改后端稳定绑定字段。
- 新增标签值表单的分类从数字输入改为分类下拉；编辑标签值时分类和 `tagValue` 显示为禁用，保存 payload 只提交 `displayName/sortOrder/isEnabled`，与后端“代码值建完锁定”的真实行为一致。
- 内置分类删除保护兼容后端可能返回的 `isBuiltin` 与历史 `builtin` 字段。

本轮新增/更新测试：

- 前端 `AdminConsole.test.ts` 覆盖进入“客户标签与分层”会加载客户字段字典，分类卡片显示字段中文名，内置分类删除按钮置灰。
- 覆盖新增标签分类时字段下拉来自后端字段字典，且已绑定字段不再可选。
- 覆盖新增标签值时分类下拉展示业务名称；覆盖编辑标签值时分类和代码值锁定，保存请求不提交 `tagValue`。

已执行验证：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，13 tests。

仍需继续深化：

- F 跟进规则仍只有单组 AND 条件构建入口；后续要在不超出后端 `ConditionEvaluator` 实际支持范围的前提下，补客户阶段/意向度/最近跟进等更多可执行条件。
- G 若要进一步贴合手册，可把后端标签错误码转成更具体的前端业务提示，例如“该标签正被 N 个客户使用，建议停用”。

## 十二、2026-07-07 A-H 子代理审计后的安全闭环补齐记录

触发背景：A-D 与 E-H 子代理审计均指出生产 P0：`LEADER` 可直接调用大量 `/admin/api/v1/**` 写接口，配置中心 API Key 明文存储，G 标签启停未走手册专用接口。按用户“生产级交付约束”，本轮优先修复不依赖外部凭据且会直接影响上线安全的链路。

本轮已落地：

- 后端 RBAC：`JwtAuthenticationFilter` 对 `/admin/api/v1/**` 增加角色门禁。`ADMIN` 可读写；`LEADER` 仅允许 GET 只读接口；`KEEPER` 继续只允许既有的 `GET /admin/api/v1/analytics/overview` 特例。这样即使前端菜单或路由暴露，组长也不能直接 POST/PUT/DELETE 修改账号、配置、规则、标签、数据源等生产配置。
- 密钥加密：新增 `SecretCipher`，新写入的环境密钥统一保存为 `{aes-gcm}` AES-GCM 密文，保留 `{plain}` 与裸文本兼容解密，避免旧数据立即失效。
- 环境激活链路：`AiEnvironmentRepository` 环境表不再存 `{plain}`；`AiEnvironmentService.activate` 写入 `system_configs.skill.api_key/image.api_key` 时保存密文，不再把明文复制到系统配置表。
- 运行时解密：`SkillConfigProvider` 与 `ImageConfigProvider` 读取 `*.api_key` 时解密，保证数据库不存明文但 Skill/识图 HTTP 客户端仍能拿到可用 API Key。
- 配置中心通用保存：`ConfigAdminService` 对 `*.api_key` 保存时加密，读取/list 时只返回 `****last4` 脱敏值，堵住绕过“环境管理”直接改配置写明文的路径。
- G 标签启停：前端标签值启停改调用手册专用 `PUT /admin/api/v1/tags/values/{id}/toggle`；后端 toggle 请求缺少 `isEnabled` 时返回 `BAD_REQUEST`，不再默认禁用。

本轮新增/更新测试：

- `JwtAuthenticationFilterTest` 覆盖 `LEADER` 可 GET 但不能 POST admin API、`ADMIN` 可写、`KEEPER` 仅保留 analytics overview。
- `AiEnvironmentRepositoryTest` 覆盖环境 API Key 新写入为 `{aes-gcm}` 且不包含明文，更新环境不传 API Key 时保留原密钥。
- `ConfigAdminServiceTest` 覆盖 `skill.api_key` 通用配置保存为密文，读取/list 返回脱敏值。
- `SkillConfigProviderTest` 与 `ImageConfigProviderTest` 覆盖运行时 provider 能从密文配置解出明文 API Key。
- `TagAdminControllerTest` 覆盖 toggle 缺 `isEnabled` 返回 400；前端 `AdminConsole.test.ts` 覆盖标签停用调用 `/toggle`。

已执行验证：

- `cd desktop && npm run typecheck` -> passed。
- `cd desktop && npm run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，13 tests。
- `wsl -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && mvn -q "-Dtest=JwtAuthenticationFilterTest,AiEnvironmentRepositoryTest,SkillConfigProviderTest,ImageConfigProviderTest,TagAdminControllerTest,ConfigAdminServiceTest" test'` -> passed。

仍需继续深化：

- A-D 子代理提出的数据源唯一真相、真实 COS 上传、Skill 可用列表外部拉取、采纳率埋点等仍未全部处理。
- E-H 子代理提出账号 phone 唯一约束、F 条件构建器、G 配置读取 system_configs、H 缓存/埋点等仍需继续推进。

## 十三、2026-07-08 子代理复审后的分页、配置迁移与误导入口修复

触发背景：用户要求“AI 与 Skill 配置”统一为“配置中心”，并要求每个模块继续对开发大纲和开发手册查漏补缺。本轮使用两个子代理做只读复审：前端运营后台复审指出账号编辑误导字段、配置中心最后环境删除保护、F 规则分页/批量操作等缺口；后端链路复审指出账号分页缺 `totalPages`、V21 迁移可能覆盖生产配置、既有 `system_configs` 没有回填 active 环境。

本轮已落地：

- E 账号分页契约：`AccountAdminService.list()` 返回结构补齐 `totalPages`，与前端 `{ list,total,page,pageSize,totalPages }` 对齐；`AccountAdminControllerTest` 增加 `totalPages` 断言。
- E 账号编辑体验：编辑账号抽屉不再展示手机号和初始密码输入，避免用户以为保存会修改但后端不会接收；手机号只在新增时设置，密码继续走列表里的“重置密码”动作。
- B 配置迁移安全：`V21__module_41_ai_config_center.sql` 改为 `ON DUPLICATE KEY UPDATE description`，不再覆盖已有生产 `system_configs.config_value`；`skill.regenerate_max_count` 默认与 provider 统一为 `3`。
- B 环境回填：V21 在环境表为空且已有 `skill/image.api_base_url + api_key` 时，从 `system_configs` 回填一个 active 环境，避免升级后后台环境列表为空但运行时仍有旧配置。
- B 删除保护：配置中心环境删除按钮同时拦截“当前启用环境”和“最后一个环境”；用户误点时显示业务提示，而不是沉默失败。
- F 跟进规则：前端规则列表改为真实后端分页，筛选 keyword/actionType/enabled 会重置到第 1 页并请求 `/admin/api/v1/rules?page&size`；新增分页 UI；新增批量启用、批量停用、批量删除自定义规则，批量操作复用现有真实单条 toggle/delete API，完成后刷新当前页。

本轮测试/验证覆盖：

- 前端 `AdminConsole.test.ts` 覆盖配置中心环境删除保护、账号编辑不出现不可保存字段、F 规则后端分页/状态筛选/批量启停/批量删除。
- 后端 `AccountAdminControllerTest` 覆盖账号分页响应中的 `totalPages`。

已执行验证：

- `cd desktop && npm.cmd run typecheck` -> passed。
- `cd desktop && npm.cmd run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，17 tests。
- `C:\windows\System32\WindowsPowerShell\v1.0\powershell.exe -Command "& .\.tools\runtime\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/runtime/m2' -DskipTests compile"` -> passed。
- `C:\windows\System32\WindowsPowerShell\v1.0\powershell.exe -Command "& .\.tools\runtime\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/runtime/m2' -Dtest=AccountAdminControllerTest test"` -> passed，8 tests。

仍需继续深化：

- D 速搜内容后端当前只有全量 list，本轮不做假分页；后续需要先补后端分页/排序契约，再让前端消费。
- I 版本和 J 公告后端已有分页返回，但前端仍固定拉较大页；后续可按账号/规则同样方式补分页 UI、页码状态和强确认。
- K 审计仍缺独立 operator 筛选；L 健康仍缺告警类型/时间筛选和 403 空状态。
- V24/V56 等迁移仍存在 MySQL `IF NOT EXISTS` DDL 兼容性和唯一索引预检风险，需要单独做 Flyway/MySQL 兼容验证与迁移修订。
- 真实外部 provider acceptance、正式签名安装包仍是上线前阻塞项。

## 十四、2026-07-08 D/I/J 真实分页契约闭环

触发背景：Gauss 子代理复审确认 D 速搜内容后端缺少真实分页/筛选/排序契约，I 版本管理和 J 系统公告后端已有分页但前端仍固定拉 `page=1,size=100`。本轮按生产级交付约束补齐前端/API/service/repository/测试链路，避免继续用假前端分页或大页绕过后端契约。

本轮已落地：

- D 速搜内容：`GET /admin/api/v1/quick-search/items` 增加 `contentType/leadType/enabled/keyword/page/size/sortBy/sortDir` 可选参数，返回 `{ items,total,page,size,totalPages }`；service 层统一校验 `leadType`、分页上下限和排序白名单；repository 增加真实 `WHERE + COUNT + LIMIT/OFFSET`，默认排序保持原来的 `content_type,sort_order,shortcut_code`。
- D 前端页面：速搜内容筛选改为后端筛选，新增启用状态筛选和分页条；批量启停、批量删除、上传图片、清除图片、单条启停/删除后刷新当前真实页，删除当前页最后一条时自动退页。
- I 版本管理：后端列表补 `totalPages`；前端不再固定 `size=100`，改用 `versionPageInfo`、真实 `page/size`、筛选重置第一页和分页 UI；发布/撤回/删除后保留当前筛选上下文并处理空页。
- J 系统公告：后端列表补 `totalPages`；前端不再固定 `size=100`，改用 `noticePageInfo`、真实 `page/size`、筛选重置第一页和分页 UI；停止/删除后处理当前筛选下的空页。
- 测试覆盖：后端补 `QuickSearchAdminListQuery` controller 绑定、service 参数规范化和非法参数拒绝；I/J controller 测试补 `totalPages`；前端 `AdminConsole.test.ts` 覆盖 D/I/J 后端分页请求、筛选重置和翻页路径。

本轮已执行验证：

- `cd desktop && npm.cmd run typecheck` -> passed。
- `cd desktop && npm.cmd run test -- --run src/renderer/modules/admin/AdminConsole.test.ts` -> passed，20 tests。
- `cd desktop && npm.cmd run test -- --run` -> passed，31 files / 187 tests。
- `C:\windows\System32\WindowsPowerShell\v1.0\powershell.exe -Command "& .\.tools\runtime\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/runtime/m2' '-Dtest=QuickSearchAdminControllerTest,QuickSearchAdminServiceTest,DesktopVersionControllerTest,NoticeControllerTest' test"` -> passed，25 tests。
- `C:\windows\System32\WindowsPowerShell\v1.0\powershell.exe -Command "& .\.tools\runtime\apache-maven-3.9.9\bin\mvn.cmd '-Dmaven.repo.local=.tools/runtime/m2' -DskipTests compile"` -> passed。

仍需继续深挖：

- Flyway/MySQL 迁移兼容性仍在子代理 Confucius 只读复审中，重点是 `ADD COLUMN IF NOT EXISTS`、`ADD UNIQUE INDEX IF NOT EXISTS` 和配置值覆盖风险。
- `python scripts\verify_admin_product_surface.py`、`python scripts\verify_production_blockers.py`、`git diff --check` 仍需在迁移复审处理后统一跑。
- 真实外部 provider acceptance 与签名安装包仍是上线前阻塞项，不能因本轮 D/I/J 通过测试而视为整体生产完成。

## 十五、2026-07-08 迁移脚本生产安全复扫

触发背景：上一轮后端复审指出迁移脚本仍有 MySQL/Flyway 兼容和生产配置覆盖风险；Confucius 子代理因 503 中断，本轮由主线程继续只读扫描并修复。

本轮已落地：

- 配置默认值保护：所有 `system_configs` 初始化迁移中的 `ON DUPLICATE KEY UPDATE config_value = VALUES(config_value)` 已改为只更新 `description`，避免上线升级时把运营已维护的 API、JWT、Prompt、分页、导出、健康等配置重置成默认值。空库初始化仍正常插入默认值。
- DDL 兼容：`V20/V23/V24` 中的 `ADD COLUMN IF NOT EXISTS` 改为普通 `ADD COLUMN`，匹配 Flyway 单次顺序迁移语义，避免依赖不同 MySQL/MariaDB 版本对 `ALTER TABLE ... IF NOT EXISTS` 的兼容差异。
- 唯一索引上线安全：`V56__production_chain_integrity.sql` 不再使用 `ADD UNIQUE INDEX IF NOT EXISTS`；加索引前先按业务语义去重，再用普通 `ADD UNIQUE INDEX` 增加唯一约束：Skill 绑定保留启用优先、优先级高、更新时间新的一条；数据源映射版本和 Prompt 版本保留同一版本最新/稳定记录。
- 风险扫描：`rg "ON DUPLICATE KEY UPDATE config_value|ADD COLUMN IF NOT EXISTS|ADD UNIQUE INDEX IF NOT EXISTS|CREATE INDEX IF NOT EXISTS|DROP INDEX IF EXISTS" src/main/resources/db/migration` 无残留命中。

本轮已执行验证：

- `SMOKE_PORT=18080 SMOKE_DB_NAME=private_domain_assistant_smoke_18080 bash scripts/smoke_backend_wsl.sh` -> passed，`flyway_migrations=24`，`table_count=31`，登录与健康接口通过。
- `SMOKE_DB_NAME=private_domain_assistant_smoke_18080 python scripts\verify_database_alignment.py` -> passed，31 tables，0 missing config keys，824 repository columns checked，0 violations。
- `python scripts\verify_admin_product_surface.py` -> passed。
- `python scripts\verify_production_blockers.py` -> productionReady=false，仅剩 `P0:LIVE_EXTERNAL_PROVIDER_ACCEPTANCE` 和 `P1:SIGNED_RELEASE_PACKAGE`。
- `git diff --check` -> no whitespace errors；仅 Git 的 LF/CRLF 转换 warning。

仍需继续：

- 整体目标仍未完成，因为真实 Skill/识图/企微表格 provider live acceptance 和正式签名安装包尚未具备证据。
- 若将来要升级已经跑过旧 checksum 的真实生产库，需按 Flyway 规范新增补丁迁移或执行受控 repair；本轮修复以当前上传新服务器/空库及本地 smoke 证据为准。

## 十六、2026-07-08 发布签名链路补强

触发背景：生产门禁仍剩 `SIGNED_RELEASE_PACKAGE`。上一轮已有签名校验脚本，但打包命令本身没有把签名材料接入 `@electron/packager`，导致 CI 即使配置证书也只能“检查未签名”，不能产出已签名包。

本轮已落地：

- `desktop/scripts/package-dir.mjs` 在 `PDA_REQUIRE_SIGNED_PACKAGE=1` 时会在打包前检查平台与签名材料，缺失时提前失败，不再等到打完包才发现未签。
- Windows 打包支持 `WINDOWS_CERTIFICATE_FILE + WINDOWS_CERTIFICATE_PASSWORD`、`WINDOWS_SIGN_WITH_PARAMS`、`WINDOWS_SIGN_HOOK_MODULE_PATH` 三种实际由 `@electron/windows-sign` 消费的签名入口。
- macOS 打包支持 `PDA_MAC_CODESIGN_IDENTITY` / `MAC_CODESIGN_IDENTITY`，并预留 entitlements 输入；正式 notarization 仍需 Apple 凭据与 macOS CI 环境。
- `desktop/scripts/package-verify.mjs` 的 `signingConfiguration` 改为按当前实际打包工具可消费的变量判断，避免把未接入 packager 的变量误报为“已配置”。
- `desktop/package.json` 的 `package:verify:signed` 改为在打包阶段就设置 `PDA_REQUIRE_SIGNED_PACKAGE=1`，确保正式发布命令会尝试签名并最终校验签名。
- 新增 `scripts/verify_release_signing_readiness.py`，只报告签名准备度和缺失输入，不输出密钥值；`scripts/acceptance_p0_p1.py` 已纳入该检查。

仍需外部证据：

- 本机没有生产代码签名证书，因此不能把 `SIGNED_RELEASE_PACKAGE` 标为通过。
- 正式上线前需要在 Windows/macOS 发布 CI 配置上述签名材料，并运行 `cd desktop && npm run package:verify:signed` 产出签名校验报告。

## 十七、2026-07-08 子代理复审后的 P0 权限与版本包闭环

触发背景：子代理 Wegener 按 40-51 运营手册只读复审，指出两个 P0：`/admin/api/v1/**` 对 LEADER 的 GET 放行过宽，以及版本安装包上传只返回伪 `cos://desktop-releases/...` 地址。

本轮已落地：

- 后端 `JwtAuthenticationFilter` 将 `/admin/api/v1/**` 收紧为 ADMIN-only。LEADER/KEEPER 不再能读取账号、配置、数据源、标签、审计、版本、健康等后台 GET 接口。
- `AuthService` 同步收紧后台登录，`/admin/api/v1/auth/login` 仅 ADMIN 可登录；LEADER/KEEPER 继续走桌面端登录和前台 `/api/v1/**` 能力。
- 桌面侧边栏“后台”入口只对 ADMIN 显示，避免前端继续引导组长进入运营后台。
- Analytics、Notice、Health service 层也收紧到 ADMIN-only，不只依赖 Filter，避免未来换路由或内部复用时权限漂移。
- 新增真实版本包存储链路：`DesktopVersionPackageStorage` 将安装包写入 `version.storage.root`，返回 `/downloads/desktop-releases/...` 下载 URL；`DesktopVersionDownloadResourceConfig` 映射静态下载资源。
- 新增 `V57__desktop_release_storage_configs.sql`，补齐 `version.storage.root` 与 `version.storage.public_base_url` 配置键；配置中心会校验存储根路径和公开下载 base URL。

验证与测试补强：

- `JwtAuthenticationFilterTest` 覆盖 LEADER/KEEPER 访问后台接口均为 403。
- `AuthServiceTest` 覆盖后台登录拒绝非 ADMIN。
- `App.test.ts` 覆盖 LEADER 缓存 token 不显示后台入口。
- `DesktopVersionPackageStorageTest` 覆盖上传文件真实落盘并返回下载 URL。
- `ConfigAdminServiceTest` 覆盖版本存储配置校验。
- `scripts/verify_module_47.py` / `48.py` / `49.py` / `51.py` 已更新为 ADMIN-only 与真实版本存储口径。

仍需继续处理的子代理 P1：

- 速搜图片上传仍是本地文件存储，不是 COS SDK 直传；但当前路径至少是真实可访问文件，不再影响模板图片可用性。后续如必须直连 COS，需要抽象共享对象存储客户端并统一版本包/速搜图片。
- Skill 可用列表仍来自已有绑定反推，不是外部 Skill 系统实时列表。
- QuickSearch、Skill、Datasource 等后台写操作审计覆盖仍需继续补齐。
- 客户数据对接缺同步策略配置页；这属于功能完整度 P2，可继续按手册补。
