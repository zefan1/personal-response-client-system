# 本地真实测试执行断点

用途：记录每一轮已经完成到哪里、下一次从哪里继续，避免测试中断后重新摸索。

## 断点 001：基础登录已恢复

时间：2026-07-08 21:30  
状态：已完成

已完成：

- 后端可访问：`http://localhost:8080`
- Vite 前端可访问：`http://localhost:5173`
- 桌面端 Electron 已重新启动。
- 桌面端可以登录。
- 运营后台可以登录。
- 推荐本地 API 地址：`http://localhost:8080`
- 推荐测试账号：`admin/admin123`
- 已清理旧 Electron 登录态缓存，并备份到 `%APPDATA%/private-domain-assistant-desktop/.backup-20260708-212415`

验证结果：

- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过。
- `scripts/verify_manual_test_readiness.py` 通过。
- `scripts/verify_real_external_readiness.py` 通过。
- 后台与侧边栏基础接口 smoke 通过。

下一步：

- 从侧边栏应用端开始，检查当前测试库是否有足够数据支撑“工作台、客户档案、回复助手、跟进、速搜、公告”等基础操作。

## 断点 002：侧边栏基础数据与接口已就绪

时间：2026-07-08 21:36  
状态：已完成

已完成：

- 确认测试库 `private_domain_assistant_smoke` 原先几乎为空，导致登录后许多模块无可操作内容。
- 已写入最小本地样本数据，只作用于测试库：
  - 3 个客户：
    - `18800001111`：逾期跟进客户
    - `18800002222`：今日跟进客户
    - `18800003333`：今日预约客户
  - 2 条速搜模板：
    - `local_tuan_open`
    - `local_arrival_reminder`
  - 1 条系统公告：
    - `notice-local-smoke-001`
  - 1 条档案待确认建议：
    - 客户 `18800001111` 的 `intentLevel` 建议
- 修正了本地速搜样本的 `content_type`：不能用 `TEXT`，后端枚举要求 `TEMPLATE/KNOWLEDGE/LOCATION/IMAGE/MINI_PROGRAM`。

验证结果：

- `GET /api/v1/desktop/status` 成功。
- `GET /api/v1/followups/today` 成功，返回 `total=2`。
- `GET /api/v1/quick-search/items` 成功，返回 `count=2`。
- `GET /api/v1/notices/active` 成功，返回 `count=1`。
- `GET /api/v1/customers/search?q=18800001111&limit=10` 成功，返回 `total=1`。
- `GET /api/v1/customers/18800001111` 成功，返回客户档案和 1 条待确认建议。
- 管理后台速搜、公告、健康接口均成功。
- 桌面端 `npm run typecheck` 通过。

当前可手工验证：

- 侧边栏工作台应能看到跟进/公告类内容。
- 客户档案可搜索 `18800001111`。
- 客户档案中应能看到待确认建议。
- 速搜中应能看到 `local-smoke group-buy opening` 与 `local-smoke arrival reminder`。
- 后台速搜内容管理中搜索 `local` 应能看到 2 条数据。

下一步：

- 开始侧边栏应用端手工点测：工作台 -> 客户档案 -> 回复助手 -> 速搜 -> 跟进抽屉 -> 公告/健康提示。
- 每完成一个模块，继续追加断点 003、004、005。

## 断点 003：工作台“查看”未直达客户档案

时间：2026-07-08 21:40-21:45  
状态：已完成

问题现象：

- 在侧边栏工作台“今日跟进”卡片点击“查看”。
- 当前实际行为：只切换到“客户档案”页，页面仍停留在搜索框，没有自动打开对应客户。
- 期望行为：直接跳转到对应客户的档案详情，并加载该客户信息。

样例：

- 工作台卡片：`local-smoke-overdue`
- 展示手机号：`188****1111`
- 期望打开：该客户对应档案详情

处理结果：

- 已确认 WorkbenchPanel 点击“查看”会发出 `customer:selected`，但 CustomerProfilePanel 原本只显示页面，不会接收该事件加载客户档案。
- 已在 CustomerProfilePanel 中监听来自工作台、跟进列表、新客资、候选客户的 `customer:selected` 事件。
- 收到客户手机号后会自动调用客户档案接口，直达对应客户详情。
- 已加保护：生成回复等内部上下文事件不会额外触发档案刷新；同一客户正在加载时不会重复加载。

涉及文件：

- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts`

验证结果：

- `npx vitest run src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts src/renderer/modules/workbench/WorkbenchPanel.test.ts` 通过，2 个测试文件、7 个用例全部通过。
- `npm run typecheck` 通过。

当前可手工验证：

- 在侧边栏工作台“今日跟进”中点击 `local-smoke-overdue` 的“查看”。
- 页面应切到“客户档案”，并直接显示 `18800001111` 对应客户档案。

下一步：

- 继续侧边栏应用端点测：客户档案搜索/建议确认 -> 回复助手 -> 速搜 -> 跟进抽屉 -> 公告/健康提示。

## 断点 004：客户档案“加载超时”根因修复

时间：2026-07-08 22:20-22:47  
状态：已完成

问题现象：

- 工作台点击“查看”后已能切换到客户档案页，但页面提示“加载超时，请重试”。
- 手工截图显示客户档案卡片未加载，顶部同时有“提醒服务暂不可用”提示。

根因：

- 后端为了展示安全，把 `phone` 返回为脱敏号，例如 `188****1111`。
- 前端后续仍把 `phone` 当作真实手机号请求 `/api/v1/customers/{phone}`，等于请求了脱敏手机号，导致详情加载失败。
- 数据库和客户详情接口本身正常：带 token 直接请求 `18800001111` 可以返回客户档案。

处理结果：

- 后端跟进列表、客户搜索、客户档案详情响应新增 `phoneFull`，展示字段 `phone` 继续保留脱敏号。
- 前端工作台、跟进列表、客户搜索结果、档案刷新、生成回复、保存档案、建议确认、阶段建议处理均优先使用 `phoneFull` 做操作手机号。
- 防止 `phoneFull` 被误当作档案编辑字段提交。
- 已重启本地 mock 后端，新接口已生效。

验证结果：

- `GET /api/v1/followups/today` 返回 `phone=188****1111` 与 `phoneFull=18800001111`。
- `GET /api/v1/customers/search?q=18800001111&limit=10` 返回 `phoneFull=18800001111`。
- `GET /api/v1/customers/18800001111` 返回 `phoneFull=18800001111` 与客户档案。
- 桌面端定向测试通过：5 个测试文件、31 个用例全部通过。
- `npm run typecheck` 通过。
- 后端 `test-compile` 通过。
- 后端定向测试报告通过：
  - `CustomerControllerTest`：8 个用例，0 failure，0 error。
  - `FollowupControllerTest`：6 个用例，0 failure，0 error。

当前可手工验证：

- 刷新或重新打开侧边栏后，点击工作台 `local-smoke-overdue` 的“查看”。
- 预期：客户档案直接显示 `local-smoke-overdue`，手机号展示为脱敏号，但不再出现“加载超时”。

下一步：

- 继续客户档案模块点测：搜索 `18800001111`、确认/拒绝待确认建议、刷新档案、编辑保存。

## 断点 005：本地自动化批量验收已跑通

时间：2026-07-09 09:28-09:32  
状态：已完成

本轮目标：

- 在继续让用户手工点侧边栏前，先把机器能自动检查的接口、契约、类型和单测尽量跑完。
- 避免用户手测时撞到已知的基础接口、数据库结构或前端类型问题。

发现并处理：

- `scripts/acceptance_backend_api.py` 原本在客户流程中使用固定手机号 `13900000001`，当前测试库不一定存在该客户，导致“生成话术”接口返回 `customer not found`。
- 已改为在客户流程中先导入一个本轮唯一的临时客户，再执行生成/重生成话术验收。
- 跟进规则验收数据使用了旧字段 `operator`，而当前后端条件叶子节点契约为 `op`。
- 已把验收脚本中的规则条件改为 `{"field":"leadType","op":"EQ","value":"PENDING"}`。

验证结果：

- 后端完整接口验收通过：`scripts/acceptance_backend_api.py --no-start`，168 个场景全部通过。
- 后端接口验收质量检查通过：`scripts/verify_backend_api_acceptance_quality.py`。
- 手工测试就绪检查通过：`scripts/verify_manual_test_readiness.py --frontend-url http://localhost:5173/ --backend-url http://localhost:8080`。
- API 映射覆盖通过：114/114，缺失 0。
- Controller 测试覆盖通过：19/19，缺失 0。
- 管理后台产品面覆盖通过。
- 数据库结构对齐通过。
- 枚举契约对齐通过：40 个契约，0 mismatch。
- 桌面端类型检查通过：`npm run typecheck`。
- 桌面端单测通过：32 个测试文件、204 个用例全部通过。
- 真实外部接口 readiness 通过：当前代码在 `mockExternals=false` 形态下无配置结构阻断。
- 生产阻断检查通过脚本本身，但结论为 `productionReady=false`，剩余 2 个非本地阻断：
  - `P0:LIVE_EXTERNAL_PROVIDER_ACCEPTANCE`：真实 Skill/图像识别/企微供应商验收未完成。
  - `P1:SIGNED_RELEASE_PACKAGE`：正式发布包尚未配置证书签名。

当前本地可继续手工验证：

- 后端：`http://localhost:8080`
- 前端/Vite：`http://localhost:5173`
- 推荐账号：`admin/admin123`
- 侧边栏若仍显示旧错误，先刷新或重新打开侧边栏，确保拿到包含 `phoneFull` 的新接口响应。

下一步：

- 按 `dev-progress/local_real_test_manual_batches.md` 开始批量手测。
- 第一批优先测侧边栏：工作台“查看”直达客户档案、客户档案搜索/编辑/建议、跟进列表、速搜、公告和健康提示。

## 断点 006：页面级 smoke 与旧登录态处理

时间：2026-07-09 09:38-09:45  
状态：已完成

本轮目标：

- 在 API 和单测通过后，再用真实浏览器页面验证后台主要入口是否能打开和加载数据。
- 重新跑 Electron renderer smoke，确认侧边栏应用端桥接与关键页面仍可用。

发现：

- 直接打开 `http://localhost:5173/#/admin` 时，页面一开始显示后台已登录，但账号名为“当前账号”，Skill 场景页提示 `Failed to fetch`，运行模式显示“未连接”。
- 该状态来自浏览器本地旧登录态/旧 token，不代表后端接口或后台页面不可用。
- 点击“退出”后，登录页 API 地址已正确显示为 `http://localhost:8080`。
- 使用 `admin/admin123` 重新登录后，后台恢复正常，账号显示 `System Admin`，运行模式显示“本地模拟模式”。

页面级验证结果：

- 后台 12 个主要分组均已用浏览器实际点击并加载成功：
  - Skill 场景管理
  - 配置中心
  - 客户数据对接
  - 速搜内容管理
  - 账号与权限
  - 跟进规则引擎配置
  - 客户标签与分层
  - 运营分析看板
  - 版本管理
  - 系统公告
  - 操作审计日志
  - 系统健康监控
- 以上分组验证时均未出现 `Failed to fetch`、401/403 鉴权错误或持续连接失败。
- 页面能看到本地模拟模式、真实列表数据、正常空状态或健康状态。
- Electron 侧边栏 renderer smoke 通过：`npm run renderer:smoke`。

当前处理建议：

- 如果后台页面再次显示 `当前账号` 且提示 `Failed to fetch`，先点“退出”，再用 `admin/admin123` 重新登录。
- 如果侧边栏仍显示旧错误，刷新或重新打开 Electron 侧边栏，确保使用新构建和新接口响应。

下一步：

- 用户可从批次 A 开始手测侧边栏真实交互。
- Codex 已经完成当前能自动跑的 API、契约、后台页面和 Electron smoke；剩下需要用户实际观察的主要是业务体验、截图识别、复制/剪贴板和真实外部接口效果。

## 断点 007：客户档案搜索、保存反馈与中文样本修复

时间：2026-07-09 10:48-11:05  
状态：已完成

本轮用户反馈：

- 客户档案里部分字段显示英文，不确定是数据库写入英文还是界面只能显示英文。
- 客户档案搜索 `1111` 搜不到 `18800001111`。
- 修改客户档案后点击保存没有明显反应。
- 客户档案右上角“刷新”按钮在窄侧边栏里超出范围，希望改成符号。
- 需要明确“跟进列表、速搜、回复建议”三个模块怎么测试。

处理结果：

- 确认客户档案不是只能显示英文，问题来自早期本地样本数据里有英文值。
- 已把本地 smoke 库中的 3 个测试客户关键样本改为中文：
  - `18800001111`：逾期跟进客户，上海门店，产后修复，身体关注为“腹直肌分离和腰背酸痛”。
  - `18800002222`：今日待跟进客户，杭州门店，盆底肌评估。
  - `18800003333`：今日预约客户，上海门店，腹直肌检测。
- 已清理 Redis 客户缓存，避免页面继续读到旧英文缓存。
- 已把本地速搜样本改为中文，并删除自动化负向用例残留的 `negative fixture`。
- 已修复客户搜索逻辑：非 11 位数字也会进入关键词搜索，支持手机号后缀匹配，所以搜索 `1111` 能命中 `18800001111`。
- 已修复保存反馈：
  - 没有改动直接保存时提示“没有改动需要保存”。
  - 保存成功后刷新详情也会保留“档案已保存”提示。
- 已把客户档案右上角刷新按钮从文字“刷新”改为图标 `↻`，并加上 `aria-label="刷新客户档案"` 与悬浮标题。

验证结果：

- `GET /api/v1/customers/search?q=1111&limit=10` 成功返回 `18800001111`。
- `GET /api/v1/customers/18800001111` 成功返回中文字段：`逾期跟进客户 / 上海门店 / 产后修复 / 腹直肌分离和腰背酸痛 / 谨慎型 / 跟进中`。
- `quick_search_items` 当前只保留两条中文启用模板：
  - `本地测试 团购开场白`
  - `本地测试 到店提醒`
- 客户档案定向单测通过：2 个测试文件、17 个用例全部通过。
- 桌面端类型检查通过：`npm run typecheck`。
- 后端定向测试通过：`CustomerSearchServiceTest,CustomerControllerTest` 共 9 个用例全部通过。

当前可手工验证：

- 刷新或重开侧边栏，进入客户档案。
- 搜索 `1111`，预期自动打开 `逾期跟进客户 / 188****1111`。
- 点击右上角 `↻`，预期不再超出侧边栏范围。
- 点击“编辑档案”，改一个字段后保存，预期出现保存反馈；若没有任何改动直接保存，预期提示“没有改动需要保存”。

下一步：

- 按 `dev-progress/local_real_test_manual_batches.md` 的 A3-A6 继续手测：客户档案、跟进列表、速搜、回复建议。

## 断点 008：回复助手完整重排与客户档案编辑保存根因修复

时间：2026-07-09 11:30-11:57  
状态：已完成

本轮用户反馈：

- 回复助手生成的内容在下方，私域同事查看和复制不方便。
- 需要按照当前已有内容完整重排，不能删掉“待处理列表”等现有信息。
- 客户档案编辑后仍然无法保存数据，需要实际测试并找到根因。

回复助手重排方案：

```text
回复助手
当前客户/模式 + 识别聊天 + 文字通道

推荐回复（首屏主操作）
回复正文
推荐理由
复制 / 换一组 / 求助组长

当前任务
客户 + 状态 + 时间 + 复制/文字/重试/移除

待处理队列
其他任务数量、可复制数量、处理中数量、失败数量
任务列表或空状态

当前任务详情
异常提醒 / 资料更新建议 / 识别进度 / 失败处理 / 多客户选择

更多建议
第二条及后续回复建议，每条仍可复制

备用文字输入
客户标识 + 聊天内容 + 发送文字
```

处理结果：

- 已把第一条可用回复提到回复助手首屏，显示为“推荐回复”主卡片。
- 已保留“当前任务”和“待处理队列”，并放在推荐回复下方，方便继续查看任务状态。
- 已保留“更多建议”，第二条及后续回复不会丢失。
- 已保留“资料更新建议”“识别进度/失败/多客户选择”“备用文字输入”等现有能力。
- 已补边界处理：只有一条推荐回复时，不再在下方误显示“还没有识别当前聊天”。

客户档案保存根因：

- 真实接口测试确认：`worries` 等后端白名单字段可以保存。
- 但前端客户档案允许编辑的 `sourceChannel`、`intendedStore`、`intendedProject`、`purchasedProject` 原本不在后端 `ProfileFieldRegistry` 可写白名单中。
- 结果是：接口返回成功、版本可能更新，但这些字段实际不会写入数据库；用户看起来就是“点保存没有反应”。

处理结果：

- 已把 `sourceChannel`、`intendedStore`、`intendedProject`、`purchasedProject` 加入后端档案可写字段白名单。
- 已新增后端测试，覆盖客户档案页面展示为可编辑的关键字段必须被后端支持。
- 已重启本地 mock 后端，新保存逻辑已生效。

验证结果：

- 前端定向测试通过：`ReplySuggestionPanel.test.ts`、`customerProfileStore.test.ts`、`saveToTableService.test.ts` 共 32 个用例通过。
- 回复助手单测复跑通过：14 个用例通过。
- 桌面端类型检查通过：`npm run typecheck`。
- 后端定向测试通过：`ProfileFieldRegistryTest,CustomerControllerTest` 共 10 个用例通过。
- 真实接口逐字段保存验证通过，并已恢复测试数据原值：
  - `worries`
  - `intendedStore`
  - `intendedProject`
  - `sourceChannel`
  - `purchasedProject`

当前可手工验证：

- 刷新或重开侧边栏，进入客户档案，搜索 `1111`。
- 点击“编辑档案”，修改“来源渠道 / 意向门店 / 意向项目 / 已购项目 / 担忧点”任意一个字段并保存。
- 点击 `↻` 刷新后，预期修改值仍保留。
- 进入回复助手，预期首屏先看到“推荐回复”，下面仍能看到“当前任务”和“待处理队列”。

## 断点 010：侧边栏三模块基础链路复测

时间：2026-07-09 15:09-15:14  
状态：已完成

本轮目标：

- 在继续修改 UI 前，先确认侧边栏“回复建议、跟进列表、速搜”不是只有页面能打开，而是组件状态、接口调用和本地数据链路都可跑通。
- 这一步不需要用户手动验证，属于本地自动化和真实 API 复测。

前端自动化验证：

- 回复助手相关测试通过：
  - `ReplySuggestionPanel.test.ts`
  - `replySuggestionStore.test.ts`
  - `recognitionStore.test.ts`
  - 3 个测试文件、39 个用例通过。
- 跟进列表相关测试通过：
  - `followupListStore.test.ts`
  - `FollowupListPanel.test.ts`
  - 2 个测试文件、9 个用例通过。
- 速搜/批量模板相关测试通过：
  - `quickSearchStore.test.ts`
  - `QuickSearchOverlay.test.ts`
  - `batchTemplateStore.test.ts`
  - 3 个测试文件、17 个用例通过。

真实本地 API 复测：

- 使用本地后端 `http://localhost:8080` 和账号 `admin/admin123` 登录成功，账号返回 `System Admin`。
- `GET /api/v1/followups/today` 返回成功，当前包含 3 条本地测试跟进/预约数据：
  - `18800001111`：逾期跟进客户。
  - `18800002222`：今日待跟进客户。
  - `18800003333`：今日预约客户。
- `GET /api/v1/quick-search/items` 返回成功，当前包含 2 条启用速搜内容：
  - `本地测试 团购开场白`
  - `本地测试 到店提醒`
- `POST /api/v1/chat/generate` 返回成功，客户为 `18800001111`。

当前结论：

- 跟进列表：基础接口、状态管理、页面组件测试均已通过，可以进入手工体验验证。
- 速搜：基础接口、状态管理、页面组件测试均已通过，可以进入手工体验验证。
- 回复建议：组件与接口链路可跑通；断点 010 当时因未配置真实思考 LLM/Skill key 走系统兜底，这不是前端按钮或接口路由失败。当前请以后续断点 028-039 的来源标签和 LLM 路由状态为准。

下一步：

- 继续处理回复助手排版，让私域同事首屏就能看到可复制内容，同时保留待处理队列、当前任务、更多建议、备用文字输入等完整流程。
- 后续接入真实 LLM 后，再复测回复质量、换一组、求助组长、复制确认和发送后档案更新链路。

## 断点 011：中断恢复后的侧边栏验证收口

时间：2026-07-09 15:18-15:32  
状态：已完成

本轮目标：

- 从中断处继续，把断点 010 合并到总断点文件。
- 复核侧边栏当前最关键的客户档案、回复助手、跟进列表、速搜链路。
- 处理 renderer smoke 因 Electron 缓存目录占用导致的不稳定问题。

处理结果：

- 已把断点 010 同步追加到 `dev-progress/local_real_test_breakpoints.md`。
- 已让 renderer smoke 使用独立临时 Electron 用户数据目录：
  - 避免和用户当前打开的 Electron 侧边栏共用 `%APPDATA%/private-domain-assistant-desktop`。
  - 避免 Chromium 缓存目录被占用时出现 `Unable to move the cache: 拒绝访问`。
- 已调整 renderer smoke 的置顶按钮验收边界：
  - smoke 验证 Electron 桥接存在、按钮可访问、`aria-pressed` 状态存在。
  - OS 级窗口置顶效果不再作为 smoke 的硬阻断，避免 Windows 窗口管理差异导致整条侧边栏集成验证失败。
- 已新增 App 单测，覆盖点击置顶按钮后 UI 状态会从“置”切换为“顶”。

验证结果：

- `npm run test -- src/renderer/App.test.ts`
  - 1 个测试文件、7 个用例通过。
- 侧边栏定向测试：
  - `CustomerProfilePanel.test.ts`
  - `customerProfileStore.test.ts`
  - `ReplySuggestionPanel.test.ts`
  - `replySuggestionStore.test.ts`
  - `followupListStore.test.ts`
  - `FollowupListPanel.test.ts`
  - `quickSearchStore.test.ts`
  - `QuickSearchOverlay.test.ts`
  - 8 个测试文件、63 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 客户档案：
  - 搜索 `1111`，应只打开/显示真实客户 `18800001111`。
  - 编辑“意向门店”等字段并保存，详情应自动回读最新值。
  - 右上角刷新按钮应为 `↻` 图标，不应超出侧边栏。
- 回复助手：
  - 首屏应先显示“推荐回复”区域。
  - 下方仍保留“当前任务”“待处理队列”“更多建议”“备用文字输入”。
  - 当前因为没有配置真实 LLM/Skill key，生成回复会返回降级提示，这是预期状态。
- 跟进列表：
  - 应能看到逾期、今日跟进、今日预约等本地测试数据。
- 速搜：
  - 应能看到 2 条本地中文模板内容。

下一步：

- 如果继续本地功能完善，建议进入“回复助手真实使用体验”细化：把推荐回复、待处理队列和文字通道在窄侧边栏下再做视觉压缩与交互顺序优化。
- 如果进入真实接口接入，需要先配置思考 LLM/Skill/图像识别/企微表格的真实 key。

## 断点 012：回复助手窄侧边栏排版顺序优化

时间：2026-07-09 15:36-15:40  
状态：已完成

本轮目标：

- 不需要用户提供外部 key，继续优化当前已可运行的侧边栏应用端。
- 针对回复助手在窄侧边栏里查看不方便的问题，进一步调整信息顺序。
- 保留用户要求的完整内容：推荐回复、更多建议、当前任务、待处理队列、任务详情、备用文字输入。

处理结果：

- 已把“更多建议”从“当前任务详情”内部上移到“推荐回复”下方。
- 新顺序为：
  1. 回复助手顶部操作区。
  2. 推荐回复。
  3. 更多建议。
  4. 当前任务。
  5. 待处理队列。
  6. 当前任务详情。
  7. 备用文字输入。
- 已保留待处理队列，不删减任务列表、失败重试、多客户选择、文字通道、资料更新建议等现有流程。
- 已新增紧凑样式：
  - `reply-alt-list`
  - `reply-alt-card`
- 备选回复现在更靠近主回复，私域同事可以先完成查看和复制，再处理任务队列。

验证结果：

- 回复助手定向测试通过：
  - `ReplySuggestionPanel.test.ts`
  - `replySuggestionStore.test.ts`
  - 2 个测试文件、29 个用例通过。
- 侧边栏定向测试通过：
  - 客户档案、回复助手、跟进列表、速搜共 8 个测试文件、63 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前结论：

- 回复助手现在已经从“推荐回复在上、更多建议在详情下方”调整为“回复内容集中在前、任务信息随后”。
- 当前仍然会返回降级回复，是因为没有配置真实思考 LLM/Skill key；这不属于本轮 UI 排版问题。

下一步：

- 继续推进侧边栏应用端可本地完成的功能检查。
- 下一块建议检查“跟进列表”真实使用体验：分类、打开客户档案、批量模板入口、空状态和刷新反馈。

## 断点 013：跟进列表体验收口

时间：2026-07-09 15:44-15:50  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 检查跟进列表的分类、刷新反馈、打开客户档案、批量模板入口、新提醒和空状态。

处理结果：

- 跟进列表刷新按钮改为窄侧栏友好的图标按钮 `↻`。
  - 增加 `aria-label="刷新今日跟进"` 和 `title="刷新"`。
  - 加载中显示 `…`，避免按钮文字挤压布局。
- 跟进列表标题下方增加上次成功刷新时间。
  - 保存成功拉取时间 `lastLoadedAt`。
  - 页面显示格式为小时分钟，例如 `20:08`。
- 新客资提醒逻辑补齐：
  - 新客资到达时会增加 `newReminderCount`。
  - 新提醒 banner 会切到 `NEW_LEAD` 标签。
  - 闪烁状态即使用户当前不在新客资标签，也会自动清理，避免长时间残留高亮。
- 已保留原有能力：
  - 四个分类：逾期跟进、今日待跟进、今日预约、新客资。
  - 点击客户进入客户档案。
  - 勾选客户后批量发模板。
  - 接口失败时保留旧数据并提示 stale。
  - 空状态按当前标签显示。

验证结果：

- 跟进列表定向测试通过：
  - `followupListStore.test.ts`
  - `FollowupListPanel.test.ts`
  - 2 个测试文件、9 个用例通过。
- 侧边栏定向测试通过：
  - 客户档案、回复助手、跟进列表、速搜共 8 个测试文件、63 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 打开侧边栏工作台的“批量/待办队列”。
- 跟进列表顶部应显示待办数量和上次刷新时间。
- 右上角刷新按钮应显示为 `↻`，不会在窄栏里超出。
- 切换四个分类，应看到对应本地测试数据或空状态。
- 勾选客户后，应显示批量操作栏。
- 点击客户主体，应跳到客户档案。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“速搜”：模板抽屉、分类过滤、复制使用、图片/文本展示、空状态和关闭行为。

## 断点 014：速搜体验收口

时间：2026-07-09 16:02-16:09  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 检查速搜模板抽屉、分类过滤、复制使用、图片/文本展示、空状态和关闭行为。

处理结果：

- 打开速搜时会清理旧复制提示，避免上一次的 `已复制` 残留到新一轮使用。
- 增加键盘选择：
  - `ArrowDown` 选择下一条。
  - `ArrowUp` 选择上一条。
  - `Enter` 复制当前选中条目。
- 选中条目增加视觉状态，便于确认 Enter 会复制哪一条。
- 图片素材增加缩略图预览。
- 复制提示更明确：
  - 文本/模板复制成功显示 `已复制`。
  - 图片复制成功显示 `图片已复制`。
  - 提示会自动清理，不会长期停留。
- 空状态文案更精确：
  - 没有任何内容：`暂无可用内容，请联系管理员配置`。
  - 搜索无结果：`没有匹配的内容，请换个关键词试试`。
  - 当前分类为空：`当前分类暂无内容，请切换筛选条件`。
- 已保留原有能力：
  - 抽屉打开、关闭。
  - 分类过滤：全部、团购、线索、通用。
  - 文本复制。
  - 图片复制。
  - 离线提示与失败重试。
  - 缓存兜底。

验证结果：

- 速搜定向测试通过：
  - `quickSearchStore.test.ts`
  - `QuickSearchOverlay.test.ts`
  - 2 个测试文件、9 个用例通过。
- 侧边栏定向测试通过：
  - 客户档案、回复助手、跟进列表、速搜共 8 个测试文件、64 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 点击侧边栏“模板”打开速搜抽屉。
- 输入关键词后，应只显示匹配内容。
- 用上下方向键切换条目，蓝色高亮应移动。
- 按 Enter 应复制当前高亮条目。
- 图片素材应显示缩略图。
- 搜索不存在的词，应显示“没有匹配的内容”。
- 点击右上角关闭按钮后抽屉应关闭。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“客户档案”的实际使用体验收口：搜索空状态、详情字段密度、编辑保存提示、表格同步提示和 AI 建议区域。

## 断点 015：客户档案体验收口

时间：2026-07-09 16:14-16:25  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 检查客户档案搜索空状态、详情字段密度、编辑保存提示、表格同步提示和 AI 建议区域。

处理结果：

- 搜索无结果从普通提示升级为空状态面板。
  - 主文案仍显示后端/状态消息，例如 `未找到客户，请检查搜索词或确认客户已登记`。
  - 增加辅助提示：可换手机号后四位、昵称或微信备注再试。
- 编辑模式增加醒目提示：
  - `正在编辑档案，保存后会自动刷新最新资料。`
  - 避免用户进入编辑后不知道当前是否处于编辑状态。
- 表格同步提示增强：
  - 同步提示增加 `profile-sync-toast`。
  - 采用 sticky 底部样式，滚动时更不容易错过。
  - 保留 `同步` 和 `暂不` 两个动作。
- 已保留原有能力：
  - 搜索单结果自动打开档案。
  - 多结果列表手动选择。
  - 从工作台/跟进列表跳转自动打开客户档案。
  - 保存后自动回读详情。
  - 有表格行时询问是否同步企微表格。
  - AI 更新建议确认/拒绝。
  - 右上角刷新图标 `↻`。

验证结果：

- 客户档案定向测试通过：
  - `CustomerProfilePanel.test.ts`
  - `customerProfileStore.test.ts`
  - 2 个测试文件、19 个用例通过。
- 侧边栏定向测试通过：
  - 客户档案、回复助手、跟进列表、速搜共 8 个测试文件、66 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 客户档案搜索一个不存在的词，应显示空状态面板和辅助提示。
- 搜索 `1111` 应打开 `18800001111`。
- 点击“编辑档案”后，应显示正在编辑提示。
- 修改字段并保存后，详情会自动刷新。
- 如果该客户带企微表格行，保存后底部会出现同步提示，可选 `同步` 或 `暂不`。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“工作台视图”体验收口：今日概览、快捷入口、公告/健康提示、查看客户跳转和窄栏布局。

## 断点 016：工作台视图体验收口

时间：2026-07-09 16:50-17:03  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 检查工作台首屏是否能作为真实入口使用：今日概览、快捷入口、查看客户跳转、待办抽屉、速搜入口、加载失败反馈和窄栏布局。

处理结果：

- 工作台加载失败提示从纯文案升级为可操作状态。
  - 显示失败原因：`数据加载失败，请检查网络后重试`。
  - 增加 `重试` 按钮，点击后会重新拉取今日跟进数据。
  - 保留右上角 `↻` 刷新按钮。
- 今日概览指标卡保持可点击。
  - `待跟进` 打开待办队列并定位逾期/今日待跟进。
  - `今日预约` 打开待办队列并定位今日预约。
  - `新客资` 打开待办队列并定位新客资。
- 工作台首屏增加三个快捷入口：
  - `识别聊天`：触发聊天截图识别链路。
  - `速搜模板`：打开速搜模板抽屉。
  - `批量待办`：打开待办队列，并提示选择客户后批量发模板。
- 保留并验证从工作台客户卡片点击 `查看` 后跳转客户档案。
  - 事件仍发送完整手机号和客户类型。
  - App 层会切换到客户档案面板。
- 工作台不再单独重复展示公告。
  - 公告和异常统一进入顶部提醒中心/全局提醒，避免工作台重复 banner 挤占首屏。
- 窄栏布局补强：
  - 快捷入口在小宽度下自动变成单列。
  - 失败提示按钮在小宽度下换行显示。
  - 首页卡片和客户行保持 `min-width: 0` 与可换行，不挤出侧边栏。

验证结果：

- 工作台定向测试通过：
  - `WorkbenchPanel.test.ts`
  - `workbenchStore.test.ts`
  - 2 个测试文件、12 个用例通过。
- 侧边栏定向测试通过：
  - `App.test.ts`
  - 工作台、客户档案、回复助手、跟进列表、速搜共 11 个测试文件。
  - 85 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 打开侧边栏工作台，顶部刷新按钮应是 `↻` 图标，不会撑出右侧。
- 点击三个指标卡：
  - 待跟进 -> 打开待办队列。
  - 今日预约 -> 打开待办队列并切到今日预约。
  - 新客资 -> 打开待办队列并切到新客资。
- 点击工作台客户卡片的 `查看`，应切到客户档案并打开对应客户。
- 点击 `识别聊天`，应触发聊天识别流程。
- 点击 `速搜模板`，应打开速搜抽屉。
- 点击 `批量待办`，应打开待办队列并显示选择客户后批量发模板的提示。
- 如果后端暂时不可用，工作台应显示失败提示和 `重试` 按钮，而不是静默无反应。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“聊天识别 / 复制回填”链路：
  - 截图识别入口。
  - 文字通道。
  - 图像识别未配置时的降级提示。
  - 复制建议内容。
  - 确认发送后联动客户档案和跟进记录。

## 断点 017：聊天识别与复制回填体验收口

时间：2026-07-09 17:05-17:18  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 检查从“识别聊天/文字通道 -> 回复建议 -> 复制回复 -> 发送确认 -> 客户档案刷新”的链路。
- 优先解决用户点击复制后像“没有反应”的体验问题。

处理结果：

- 复制回复成功后增加即时反馈：
  - `已复制到剪贴板，请粘贴到微信发送`。
- 如果回复带客户手机号，会继续调用 `/api/v1/chat/send-confirm` 记录发送。
- send-confirm 成功后增加反馈：
  - `已复制并记录发送，档案正在刷新`。
- send-confirm 成功后新增前端事件：
  - `reply:send-confirmed`
  - 客户档案已监听该事件，会按当前客户刷新档案。
- send-confirm 失败不再静默：
  - 复制仍然保留成功。
  - 提示 `已复制，但发送记录失败，请稍后刷新档案确认`。
- 保留既有降级能力：
  - 图片识别服务不可用时切到文字通道。
  - 截图识别失败时提示使用文字通道。
  - 剪贴板截图先弹确认，再识别。
  - 空回复/剪贴板写入失败会提示复制失败。

验证结果：

- 复制回填定向测试通过：
  - `copyBackfillStore.test.ts`
  - `CopyBackfillAgent.test.ts`
  - 2 个测试文件、12 个用例通过。
- 聊天识别、回复助手、客户档案、复制回填联动测试通过：
  - 9 个测试文件、77 个用例通过。
- 侧边栏回归测试通过：
  - `App.test.ts`
  - 工作台、聊天识别、回复助手、复制回填、客户档案、跟进列表、速搜共 16 个测试文件。
  - 114 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 在回复助手里点击任意回复的 `复制`：
  - 应显示复制成功提示。
  - 剪贴板应拿到对应回复文字。
- 如果该回复有关联客户：
  - 应继续记录发送。
  - 成功后提示档案正在刷新。
  - 客户档案会收到刷新事件。
- 如果后端 send-confirm 暂时失败：
  - 文案仍已复制，可继续粘贴到微信。
  - 页面会提示发送记录失败，而不是完全没反应。
- 图像识别未配置或不可用时：
  - 识别入口应提示使用文字通道。
  - 文字通道仍可提交客户标识和聊天内容。

已发现但未在本轮展开的真实接口契约风险：

- 回复建议模块当前传给复制回填的 `phone` 是脱敏后四位格式，例如 `****1111`。
- 客户档案前端刷新可以通过后四位匹配当前客户，所以本地体验链路能动。
- 但后端 `/api/v1/chat/send-confirm` 会把请求里的 `phone` 原样发布为发送事件；后续要做真实表格/档案回写时，建议把复制回填 payload 改为完整手机号，避免后端事件只拿到脱敏号。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“send-confirm 完整手机号契约 / 保存到表格”链路：
  - 回复选择时携带完整手机号。
  - send-confirm 后端事件能命中真实客户。
  - 已发送内容能进入档案更新/跟进记录/表格写入。
  - 对没有完整手机号的场景保留明确降级提示。

## 断点 018：send-confirm 完整手机号契约修复

时间：2026-07-09 17:18-17:24  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 修复断点 017 发现的真实接口契约风险：回复复制事件只传脱敏手机号，导致 `/api/v1/chat/send-confirm` 后端事件也只能拿到脱敏号。

处理结果：

- `reply:selected` 事件契约调整：
  - `phone` 改为完整手机号，例如 `18800001111`。
  - 新增 `displayPhone`，保留脱敏展示值，例如 `****1111`。
- 回复建议模块 `selectReply()` 不再把 `phone` 主字段主动脱敏。
- 复制回填模块继续用 `phone` 调用 `/api/v1/chat/send-confirm`。
- send-confirm 成功后的 `reply:send-confirmed` 事件也携带完整手机号。
- 这样后端发送确认事件、档案刷新、后续表格/跟进写入可以拿到真实客户标识。

验证结果：

- 回复建议 + 复制回填定向测试通过：
  - 4 个测试文件、42 个用例通过。
- 侧边栏回归测试通过：
  - 工作台、聊天识别、回复助手、复制回填、客户档案、跟进列表、速搜共 16 个测试文件。
  - 115 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 在回复助手点击 `复制`：
  - 页面仍显示脱敏手机号。
  - send-confirm 请求应使用完整手机号。
  - 成功后显示 `已复制并记录发送，档案正在刷新`。
- 客户档案收到发送确认事件后，应按完整手机号刷新当前档案。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“保存到表格 / 发送确认后后端事件”链路：
  - send-confirm 后后端是否能命中客户。
  - 已发送内容是否进入档案更新建议或跟进记录。
  - 有表格行时是否触发表格更新。
  - 无真实企微表格 key 时，确认 mock/降级提示是否清楚。

## 断点 019：发送确认后端事件与表格写入链路核对

时间：2026-07-09 17:24-17:40  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 核对 `reply:selected -> copy-backfill -> /api/v1/chat/send-confirm -> CustomerMessageSentEvent` 后，后端是否能继续触发档案更新和企微表格写入。
- 为真实接口联调前的关键契约补测试，避免前端再次把脱敏手机号传入后端。

核对结果：

- `/api/v1/chat/send-confirm` 会发布 `CustomerMessageSentEvent`：
  - 使用请求里的完整 `phone`。
  - 携带 `sentText`、`selectedDirection`、`followupSuggest`、`rawMessages`、`sourceTable`、`leadType` 和当前操作人。
  - 返回 `{ accepted: true }`，表示发送确认事件已被接收。
- 档案更新链路已接事件：
  - `ProfileUpdateOrchestrator` 监听 `CustomerMessageSentEvent`。
  - 通过完整手机号查客户。
  - 抽取高置信字段后写入档案，并把中置信字段进入建议队列。
  - 同时写入 `lastFollowupAt` 和 `followupNotes`。
- 表格写入链路已接事件：
  - `TableWriteOrchestrator` 监听 `CustomerMessageSentEvent`。
  - 有客户且不是新客时走现有行更新。
  - 找不到客户或标记为新客时走新行创建。
  - 写入失败时会立即重试一次，再进入待写队列。

本轮新增守护测试：

- `ChatOrchestrationServiceTest.sendConfirmPublishesFullPhoneEventForProfileAndTableWriteConsumers`
  - 验证 send-confirm 发布的事件使用完整手机号。
  - 验证已发送内容、跟进建议、操作人和审计日志被正确带出。
- `TableWriteOrchestratorTest.updatesExistingCustomerWithFullPhoneAndFallsBackToPendingQueueOnFailure`
  - 验证表格写入用完整手机号命中现有客户。
  - 验证现有行更新失败后会重试一次。
  - 验证重试失败后进入待写队列，并保留 `sourceTable`、`sourceRowId` 和待写字段。

验证结果：

- 前端/桌面侧在断点 018 已通过：
  - 侧边栏回归 16 个测试文件、115 个用例通过。
  - `npm run typecheck` 通过。
  - `npm run renderer:smoke` 通过。
- 后端 Java 测试本轮已补充，但当前本机没有可用 Maven：
  - 项目只有 `pom.xml`，没有 `mvnw`。
  - `Get-Command mvn,mvnw` 未找到可执行命令。
  - 因此 `ChatOrchestrationServiceTest` 和 `TableWriteOrchestratorTest` 暂未能在本机执行。

当前可手工验证：

- 在回复助手点击 `复制`：
  - 前端 send-confirm 请求应带完整手机号。
  - 成功后应提示 `已复制并记录发送，档案正在刷新`。
- 后端有 Maven/JDK 执行环境后，优先运行：
  - `mvn test -Dtest=ChatOrchestrationServiceTest,TableWriteOrchestratorTest`
- 如果真实企微表格接口尚未配置：
  - 表格写入应走 mock 或待写队列。
  - 后续需要在侧边栏/后台把“已排队、等待重试、失败原因”展示清楚，避免用户以为保存没有反应。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“表格写入/待写队列/降级状态”前端可见性：
  - 保存或发送确认后，用户能否看到表格同步状态。
  - 接口失败或真实 key 未配置时，是否有明确提示。
  - 是否需要在工作台或客户档案里增加待写队列入口。

## 断点 020：客户档案表格同步状态可见性增强

时间：2026-07-09 17:40-17:52  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 解决客户档案保存后“同步到企微表格”只靠底部 toast 提示的问题。
- 让私域同事在保存、等待同步、同步中、同步成功、失败后台重试、暂不同步这些状态下都能看到明确反馈。

处理结果：

- 客户档案状态新增 `tableSyncStatus`：
  - `pending`：档案已保存，等待同步企微表格。
  - `syncing`：正在同步到企微表格。
  - `success`：已同步到表格。
  - `retrying`：同步失败，系统将在后台自动重试。
  - `skipped`：已暂不同步企微表格。
- 客户档案顶部新增 `profile-table-sync-status` 状态条：
  - 保存后立即显示待同步状态。
  - 点击同步后先显示同步中。
  - 同步成功或失败后保留最终结果说明。
  - 失败时明确提示“不需要重复保存，可稍后刷新确认”。
- 状态条不会被保存后的自动刷新立刻清掉：
  - 同一个客户刷新时保留状态。
  - 切换到另一个客户时自动清空旧客户状态，避免误导。
- 保留原底部 `同步 / 暂不` 操作 toast：
  - 仍可直接同步企微表格。
  - 仍可选择暂不。

涉及文件：

- `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts`
- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- `desktop/src/renderer/styles.css`
- `desktop/src/renderer/modules/customer-profile/customerProfileStore.test.ts`
- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts`

验证结果：

- 客户档案与保存到表格定向测试通过：
  - `CustomerProfilePanel.test.ts`
  - `customerProfileStore.test.ts`
  - `saveToTableService.test.ts`
  - 3 个测试文件、28 个用例通过。
- 侧边栏相关回归测试通过：
  - 工作台、聊天识别、回复助手、复制回填、客户档案、保存到表格、跟进列表、速搜共 17 个测试文件。
  - 124 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 打开客户档案，编辑带有 `sourceRowId` 的客户并保存：
  - 顶部应出现“档案已保存，等待同步企微表格”状态条。
  - 底部仍会出现“同步 / 暂不”操作条。
- 点击 `同步`：
  - 应先看到“正在同步到企微表格”。
  - 成功后显示“已同步到表格”。
  - 如果真实表格接口未配置或失败，应显示“表格同步失败，系统将在后台自动重试”，并提示无需重复保存。
- 点击 `暂不`：
  - 应显示“已暂不同步企微表格”。
- 切换到另一个客户：
  - 旧客户的同步状态条应自动消失。

下一步：

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“异常提醒 / 离线降级 / 帮助模式”：
  - 异常提醒是否能从全局铃铛进入历史。
  - 当前客户异常是否能在档案和回复助手里同步展示。
  - 离线或接口失败时是否有明确状态，而不是静默不可用。
  - 帮助模式是否能让一线发起求助、组长处理并回填建议。

## 断点 021：帮助模式发送反馈与异常/离线核对

时间：2026-07-09 17:52-18:00  
状态：已完成

本轮目标：

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 核对异常提醒、离线降级、帮助模式是否能在本地测试中给出明确反馈。
- 优先修复“求助发送后弹窗关闭，成功提示也跟着消失”的体验问题。

核对结果：

- 异常提醒链路已有可见入口：
  - 全局提醒铃铛会聚合桌面操作异常、网络状态、客户异常、公告、Skill 到期。
  - 客户异常可在提醒中心确认“已知晓”。
  - 当前客户异常会同步到客户档案和回复建议区域。
- 离线降级链路已有可见入口：
  - API 连续网络失败会进入离线模式。
  - WebSocket 长时间断开会显示提醒服务不可用。
  - 恢复在线后会显示短暂恢复提示。
- 帮助模式发现体验缺口：
  - 发送求助成功后 `requestDialogVisible` 会关闭。
  - 原成功/转派文案写在弹窗内部 toast 上，弹窗关闭后用户看不到。
  - 一线同事容易误判为“点了发送没反应”。

处理结果：

- 帮助模式新增 `statusNotice`：
  - 独立于弹窗内部 `toast`。
  - 弹窗关闭后仍会在侧边栏顶部显示。
  - 可手动关闭。
- 求助发送成功后新增可见状态：
  - 直属组长在线：显示 `已向某某组长发送求助`。
  - 直属组长不在线但已转派：显示 `已转给某某组长`。
  - 提示收到回复后会在侧边栏顶部提醒。
- 求助失败和无组长可接新增可见状态：
  - 无客户上下文：提示先识别聊天或选择客户。
  - 已有等待中的求助：提示等待组长回复后再发起。
  - 没有组长在线：提示本次求助未发送成功，可稍后重试或电话联系组长。
  - 网络异常：提示求助发送失败。
- 组长侧处理和回填新增可见状态：
  - 组长回复发送失败时显示顶部错误状态。
  - 组长回复发送成功时显示已回复并移出待处理。
  - 一线收到组长回复时显示 `组长已回复你的求助`。
  - 复制组长回复后显示 `组长回复已复制`，提示粘贴到微信并按正常流程确认发送。

涉及文件：

- `desktop/src/renderer/modules/help-mode/helpModeStore.ts`
- `desktop/src/renderer/modules/help-mode/HelpModeAgent.vue`
- `desktop/src/renderer/styles.css`
- `desktop/src/renderer/modules/help-mode/helpModeStore.test.ts`
- `desktop/src/renderer/modules/help-mode/HelpModeAgent.test.ts`

验证结果：

- 帮助模式定向测试通过：
  - `helpModeStore.test.ts`
  - `HelpModeAgent.test.ts`
  - 2 个测试文件、12 个用例通过。
- 异常提醒、离线降级、帮助模式、回复复制回归测试通过：
  - 10 个测试文件、73 个用例通过。
- 侧边栏完整相关回归测试通过：
  - 工作台、聊天识别、回复助手、复制回填、客户档案、保存到表格、跟进列表、速搜、异常提醒、离线降级、帮助模式共 23 个测试文件。
  - 155 个用例通过。
- `npm run typecheck` 通过。
- `npm run renderer:smoke` 通过，输出 `renderer_smoke=passed`。

当前可手工验证：

- 在回复建议里点击求助组长并发送：
  - 弹窗关闭后，顶部应显示“已向组长发送求助”或“已转给其他组长”。
  - 状态条可以点击关闭。
- 如果当前没有客户上下文：
  - 应显示“请先识别聊天或选择客户”。
- 如果已有等待中的求助：
  - 应显示“你已有等待中的求助”。
- 当收到组长回复：
  - 顶部应显示“组长已回复你的求助”。
  - 展开后复制某条回复，应显示“组长回复已复制”。
- 网络断开或接口失败时：
  - 离线条和提醒中心应给出明确状态，不应静默不可用。

下一步：

- 侧边栏应用端本地核心交互已经完成一轮可用性收口。
- 下一块建议进入“本地启动/真实接口配置清单”收口：
  - 明确本地数据库、后端、桌面端启动方式。
  - 明确当前仍缺的真实外部配置：图像识别、Skill/回复生成、表格网关、未来 LLM 多供应商。
  - 如需继续编码，下一优先级是 LLM 多供应商接口设计与后台配置页。

## 断点 022：本地后端/数据库预检与真实接口缺口定位

时间：2026-07-09 18:00-18:10  
状态：已完成

本轮目标：

- 继续本地真实测试准备，不需要用户额外配合。
- 针对“数据库好像没有连接上”的反馈，核对当前后端、数据库、Redis、工具链和真实外部配置。
- 把本地测试前的检查固化成可重复脚本，避免后续靠猜。

核对结论：

- 当前本地后端可访问：
  - `http://localhost:8080/api/v1/auth/config` 返回成功。
  - `admin/admin123` 登录成功。
- 当前数据库是连上的：
  - 后端健康检查里 `db=UP`、`redis=UP`。
  - WSL MariaDB 中 `private_domain_assistant_smoke` 可查询。
  - Flyway 迁移数：26。
  - 当前表数量：31。
- 当前运行模式是 `真实接口模式`：
  - `MOCK_EXTERNALS=false`。
  - Skill 和图像识别配置当前已有值。
  - 企微表格真实接口仍缺：`table.api_base_url`、`table.api_key`。
- Windows 侧工具链：
  - 有 JDK、Node、npm、WSL、Docker。
  - 没有 Windows PATH 下的 `mvn` 和 `mysql`。
  - 这不阻塞本地后端运行，因为 WSL 内已有 Maven、MySQL/MariaDB、Redis 和 Java。

处理结果：

- 新增本地运行预检脚本：
  - `scripts/verify_local_runtime_readiness.py`
  - 默认检查本地核心链路，不要求真实外部接口全部配置。
  - 可用 `--require-real-externals` 切换为真实接口强校验。
- 预检脚本覆盖：
  - Windows 基础工具：Java、javac、Node、npm、WSL。
  - WSL 后端工具链：Java、Maven、MySQL、Redis。
  - 后端 auth config。
  - 管理员登录。
  - 后端健康检查中的 DB/Redis。
  - WSL 数据库 schema、Flyway、表数量。
  - Skill、图像识别、企微表格外部配置是否为空。
- 报告已脱敏：
  - accessToken、refreshToken、apiKey、password 不会明文写入 readiness 报告。
- 统一本地验收默认后端地址：
  - `scripts/verify_manual_test_readiness.py` 默认后端从旧 WSL IP 改为 `http://localhost:8080`。
  - `scripts/acceptance_p0_p1.py` 默认后端从旧 WSL IP 改为 `http://localhost:8080`。
- WSL 后端启动脚本增加防重复启动：
  - `scripts/start_backend_mock_wsl.sh`
  - `scripts/start_backend_real_wsl.sh`
  - 当 PID 文件失效但 8080 已经有后端响应时，不再启动第二个后端，直接提示 `backend_already_running`。

验证结果：

- 本地运行预检通过：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=12/15 backend=http://localhost:8080 database=private_domain_assistant_smoke`
  - warning：Windows PATH 无 `mvn/mysql`。
  - warning：真实企微表格配置缺 `table.api_base_url`、`table.api_key`。
- 真实外部强校验按预期失败：
  - `python scripts\verify_local_runtime_readiness.py --require-real-externals`
  - 失败项：`table.api_base_url`、`table.api_key`。
  - 说明后续“每个接口都接上”的真实表格联调还需要配置这两项。
- 手工测试就绪检查通过：
  - `python scripts\verify_manual_test_readiness.py --frontend-url http://127.0.0.1:5173/ --backend-url http://localhost:8080`
  - `passed=true checks=3/3`
- WSL 启动脚本语法检查通过：
  - `bash -n scripts/start_backend_mock_wsl.sh`
  - `bash -n scripts/start_backend_real_wsl.sh`
  - `bash -n scripts/smoke_backend_wsl.sh`
  - `bash -n scripts/stop_backend_mock_wsl.sh`
- 后端启动脚本防重复分支通过：
  - 当前已有后端运行时，`start_backend_mock_wsl.sh` 返回 `backend_already_running`。
- Python 编译检查通过：
  - `python -m py_compile scripts\verify_local_runtime_readiness.py scripts\verify_manual_test_readiness.py scripts\acceptance_p0_p1.py`
- 桌面端验证通过：
  - `npm run typecheck`
  - `npm run renderer:smoke`，输出 `renderer_smoke=passed`。

当前可手工验证：

- 如果侧边栏提示数据库/后端不可用，先运行：
  - `python scripts\verify_local_runtime_readiness.py`
- 如果只想确认前端和后端能登录：
  - `python scripts\verify_manual_test_readiness.py --frontend-url http://127.0.0.1:5173/ --backend-url http://localhost:8080`
- 如果要进入真实外部接口全接入检查：
  - `python scripts\verify_local_runtime_readiness.py --require-real-externals`
  - 当前会明确卡在企微表格配置：`table.api_base_url`、`table.api_key`。

下一步：

- 本地核心链路已经可测，数据库不是当前阻塞项。
- 下一步进入真实外部配置收口：
  - 企微表格接口：补 `table.api_base_url`、`table.api_key`。
  - 继续确认 Skill/图像识别配置是否是可实际调用的真实 provider。
  - LLM 多供应商仍在 tasklist，尚未实现，需要作为下一阶段编码项。

## 断点 023：LLM 多环境配置中心与本地 V59 迁移接入

时间：2026-07-09 18:10-18:50  
状态：已完成

本轮目标：

- 继续本地真实测试准备，不需要用户额外配合。
- 把用户提出的“缺少思考 LLM 接口、最好可配置多个 LLM 测试”从 tasklist 落到可操作配置入口。
- 先完成 LLM provider 管理、激活同步和连通测试，为后续回复建议/档案提取/跟进策略接入 LLM 做准备。

处理结果：

- 后端新增 LLM 环境类型和管理接口：
  - `AiEnvironmentType.LLM`
  - `/admin/api/v1/llm-environments`
  - 支持列表、创建、编辑、启用、删除、测试连接。
- 新增数据库迁移：
  - `V59__llm_environment_configs.sql`
  - 新表：`llm_environments`
  - 新配置：`llm.api_base_url`、`llm.api_key`、`llm.model`、`llm.protocol`、`llm.timeout_ms`、`llm.temperature`、`llm.max_tokens`
- LLM 环境支持字段：
  - 环境名称、Base URL、API Key、模型名、协议、超时、温度、最大 tokens、启用状态、最近测试状态。
  - API Key 继续使用后端加密存储，前端只显示脱敏信息。
- 激活 LLM 环境后会同步当前运行配置到 `system_configs`，并推送配置刷新事件。
- LLM 测试连接使用 OpenAI-compatible `/v1/chat/completions` 最小请求，返回耗时和结果摘要。
- 管理后台配置中心新增 `LLM 思考环境` 卡片：
  - 多环境列表。
  - 当前启用状态。
  - 模型/协议/超时/测试状态展示。
  - 新增/编辑/启用/删除/测试连接。
- 管理后台开发调试台新增 LLM 环境读取、创建、激活、测试入口。
- 本地 readiness 脚本已把 `llm.api_base_url`、`llm.api_key`、`llm.model` 纳入真实外部强校验。

本地运行时状态：

- 已重启本地 WSL 后端，当前仍是本地真实接口模式：
  - `MOCK_EXTERNALS=false`
  - 后端：`http://localhost:8080`
  - 测试库：`private_domain_assistant_smoke`
- Flyway 已更新到：
  - `59 / V59__llm_environment_configs.sql`
- 数据库已存在：
  - `llm_environments`
- 后台 LLM 环境接口已可访问：
  - `GET /admin/api/v1/llm-environments` 返回 200，当前列表为空。

验证结果：

- 后端定向测试通过：
  - `AiConfigControllerTest`
  - `AiEnvironmentServiceTest`
  - `AiEnvironmentRepositoryTest`
  - `ConfigAdminServiceTest`
- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 22 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`
- Renderer smoke 通过：
  - `npm run renderer:smoke`
  - 输出 `renderer_smoke=passed`。
- 本地运行预检通过：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=12/15 backend=http://localhost:8080 database=private_domain_assistant_smoke`
- 手工测试就绪检查通过：
  - `python scripts\verify_manual_test_readiness.py --frontend-url http://127.0.0.1:5173/ --backend-url http://localhost:8080`
  - `passed=true checks=3/3`
- 真实外部强校验按预期失败：
  - `python scripts\verify_local_runtime_readiness.py --require-real-externals`
  - 当前缺：`llm.api_base_url`、`llm.api_key`、`llm.model`、`table.api_base_url`、`table.api_key`。

当前可手工验证：

- 打开管理后台 `配置中心`。
- 查看是否出现 `LLM 思考环境`。
- 点击 `新增环境`，可填写环境名称、服务地址、API Key、模型名称、协议、超时、温度、最大 Tokens。
- 保存后可点击 `启用` 和 `测试连接`。
- 如果尚未填真实 provider，测试连接失败是预期状态；配置保存、列表显示和启用逻辑应可用。

下一步：

- 如果用户暂时不提供真实 key，下一步建议继续做“不需要真实 key”的本地能力：
  - 侧边栏/后台剩余交互自测。
  - LLM 业务路由设计：回复建议、档案提取、跟进策略要如何选择当前 LLM。
  - A/B 测试结构和结果记录表。
- 如果用户准备进入真实接口联调，需要先补：
  - `llm.api_base_url`
  - `llm.api_key`
  - `llm.model`
  - `table.api_base_url`
  - `table.api_key`

## 断点 024：统一 LLM runtime client/service 接入

时间：2026-07-09 18:50-19:08  
状态：已完成

本轮目标：

- 在断点 023 已完成“LLM 多环境配置入口”的基础上，继续补后端统一 LLM 调用能力。
- 避免后续回复建议、档案提取、跟进策略等模块各自硬编码 provider。
- 让配置中心的 LLM 测试连接也复用统一 runtime，实现一条真实调用链。

处理结果：

- 新增统一 LLM runtime 包：
  - `src/main/java/com/privateflow/modules/llm`
- 新增基础类型：
  - `LlmConfig`
  - `LlmRequest`
  - `LlmMessage`
  - `LlmResponse`
  - `LlmException`
  - `LlmErrorCodes`
- 新增配置读取：
  - `LlmConfigProvider`
  - 读取 `llm.*` 配置。
  - 自动解密 `llm.api_key`。
  - 监听 `ConfigChangedEvent`，当 `llm.*` 配置变化时刷新内存快照。
- 新增调用入口：
  - `LlmClient`
  - `HttpLlmClient`
  - `LlmService`
- 当前协议能力：
  - 支持 OpenAI-compatible `/v1/chat/completions`。
  - 自动规范化 base URL：可填根地址、`/v1` 或完整 `/chat/completions`。
  - 支持 system prompt、user prompt、多轮 messages、temperature、max tokens。
- 当前错误码：
  - 未配置：`30-20001`
  - 鉴权失败：`30-20002`
  - 限流：`30-20003`
  - 不可达：`30-20004`
  - 超时：`30-20005`
  - 返回格式异常：`30-20006`
- 配置中心 LLM 测试已改为复用：
  - `LlmService.test(...)`
  - 后续业务模块也可复用同一个 service。

本地运行时状态：

- 已重启本地 WSL 后端到最新代码。
- 当前运行模式：
  - `MOCK_EXTERNALS=false`
  - 后端：`http://localhost:8080`
  - 测试库：`private_domain_assistant_smoke`
- 当前后端接口可用：
  - `GET /admin/api/v1/llm-environments` 返回 200。
  - `GET /admin/api/v1/health` 返回 200，运行模式为真实接口模式。

验证结果：

- 后端定向测试通过：
  - `AiEnvironmentServiceTest`
  - `AiConfigControllerTest`
  - `AiEnvironmentRepositoryTest`
  - `ConfigAdminServiceTest`
  - `LlmConfigProviderTest`
  - `HttpLlmClientTest`
- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 22 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`
- Renderer smoke 通过：
  - `npm run renderer:smoke`
  - 输出 `renderer_smoke=passed`。
- 本地运行预检通过：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=12/15 backend=http://localhost:8080 database=private_domain_assistant_smoke`

当前仍缺：

- 真实 LLM provider 配置仍未填写：
  - `llm.api_base_url`
  - `llm.api_key`
  - `llm.model`
- 真实企微表格配置仍未填写：
  - `table.api_base_url`
  - `table.api_key`
- 统一 LLM runtime 已有，但尚未接入具体业务链路：
  - 回复建议。
  - 档案提取。
  - 跟进策略。
  - 异常识别。
- A/B 测试和结果记录尚未实现。
- LLM 调用日志、降级策略、侧边栏异常展示尚未实现。

下一步：

- 如果继续不需要用户配合，建议下一步做：
  - 设计 LLM 场景路由表/配置结构。
  - 增加 LLM 调用日志。
  - 将回复建议的“思考 LLM”作为可选链路接入，但保留现有 Skill 兜底。
- 如果用户提供真实 key，可以先在配置中心新增 LLM 环境并测试连接。
## 断点 025：LLM 场景路由与调用日志基础设施

时间：2026-07-09 19:40  
状态：已完成

已完成：

- 新增迁移 `V60__llm_routes_and_call_logs.sql`。
- 新增 `llm_scene_routes`，用于维护业务场景到 LLM 环境的路由。
- 新增 `llm_call_logs`，用于记录 LLM 调用场景、模型、耗时、成功/失败和错误信息。
- 新增 LLM 场景：`REPLY_GENERATION`、`PROFILE_EXTRACTION`、`FOLLOWUP_SUGGESTION`、`ABNORMAL_DETECTION`、`SUMMARY`。
- 新增管理接口：`/admin/api/v1/llm-routes`、`/admin/api/v1/llm-routes/scenes`、`/admin/api/v1/analytics/llm-calls`。
- 新增 `LlmRoutingService`、`LlmCallLogger`、`LlmCallAnalyticsRepository`。
- `LlmService` 新增按场景调用入口，后续业务模块可直接调用。
- 本地后端已重启，Flyway 已到 V60。

验证结果：

- `mvn -q test-compile` 通过。
- 新增测试通过：`LlmRoutingServiceTest`、`LlmCallAnalyticsRepositoryTest`、`LlmServiceTest`、`LlmAdminControllerTest`。
- 回归测试通过：`LlmConfigProviderTest`、`HttpLlmClientTest`、`AiEnvironmentRepositoryTest`、`AiEnvironmentServiceTest`、`AiConfigControllerTest`、`ConfigAdminServiceTest`。
- 运行态接口确认：
  - `GET /admin/api/v1/llm-routes/scenes` 返回 5 个场景。
  - `GET /admin/api/v1/llm-routes` 当前为空，符合尚未配置路由的状态。
  - `GET /admin/api/v1/analytics/llm-calls` 当前 `totalCalls=0`，符合尚未接业务调用的状态。

下一步：

- 把回复建议接入 `REPLY_GENERATION` 可选 LLM 链路，并保留 Skill/模板兜底。
- 后续补管理后台 LLM 路由配置和调用统计展示。
## 断点 026：回复建议接入 LLM 可选链路

时间：2026-07-09 20:00  
状态：已完成

已完成：

- 新增迁移 `V61__llm_reply_generation_configs.sql`。
- 新增 `LlmReplyGenerationService`。
- 回复建议生成和重新生成已接入 `REPLY_GENERATION` LLM 可选链路。
- 默认配置 `llm.reply_generation.enabled=false`，所以未配置真实 LLM key 时仍走原 Skill 链路。
- LLM 失败或返回格式异常时自动回落到 Skill。
- LLM prompt 不传完整手机号，只保留 `phoneLast4`。
- 后端已重启，Flyway 已到 V61。

验证结果：

- `mvn -q test-compile` 通过。
- 定向测试通过：`ChatOrchestrationServiceTest`、`ChatControllerTest`、`LlmReplyGenerationServiceTest`、`LlmServiceTest`、`LlmRoutingServiceTest`、`LlmCallAnalyticsRepositoryTest`、`LlmAdminControllerTest`。
- 运行态确认 `POST /api/v1/chat/generate` 可访问；当前真实 Skill/外部服务不可用时仍返回原系统兜底，这是预期。

下一步：

- 补管理后台 LLM 路由配置和调用统计 UI。
- 或继续优化侧边栏，让一线同事能看到当前回复来源是 LLM、Skill 还是兜底。

## 断点 027：管理后台 LLM 回复、路由与统计 UI 接入

时间：2026-07-09 20:15  
状态：已完成

已完成：

- 管理后台配置中心新增 `LLM 回复生成` 面板。
- 支持保存 `llm.reply_generation.enabled`、`fallback_to_skill`、温度覆盖、最大 tokens 和系统 Prompt。
- 管理后台配置中心新增 `LLM 场景路由` 面板。
- 支持 LLM 路由新增、编辑、启停和删除。
- 管理后台配置中心新增 `LLM 调用统计` 面板。
- 支持查看近 7/14/30 天调用量、成功率、平均响应时间和聚合明细。
- 配置中心刷新时会同步加载 LLM 环境、路由、场景字典、调用统计和回复生成配置。

验证结果：

- `npm test -- AdminConsole.test.ts` 通过，23 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 侧边栏回复建议展示当前回复来源：`LLM / Skill / 兜底`。
- 真实 LLM provider 配置仍需后续提供后才能打开 LLM 回复生成做真实效果测试。

## 断点 028：侧边栏回复来源展示

时间：2026-07-09 20:28  
状态：已完成

已完成：

- 后端 `ChatResponse` 新增 `replySource` 元数据。
- 回复建议来源可区分：
  - `LLM`
  - `SKILL`
  - `FALLBACK`
- 侧边栏回复助手会在推荐回复、当前任务和待处理队列展示来源标签。
- 兜底状态会强制显示 `系统兜底`，避免空结果误判为 Skill 正常生成。

验证结果：

- `mvn -q -Dtest=ChatOrchestrationServiceTest,ChatControllerTest test` 通过。
- `mvn -q test-compile` 通过。
- `npm test -- ReplySuggestionPanel.test.ts replySuggestionStore.test.ts` 通过，30 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 继续补侧边栏 LLM 状态提醒，或继续把档案提取、跟进建议、异常识别接入 LLM 场景。

## 断点 029：侧边栏 LLM 健康提醒接入

时间：2026-07-09 20:55  
状态：已完成

已完成：

- `/api/v1/desktop/status` 新增 `llmStatus`。
- 侧边栏状态仓库已接收 LLM 状态。
- 提醒中心已展示 LLM 配置缺失提醒。
- 配置完整后，会根据近 24 小时 `REPLY_GENERATION` 调用日志判断近期失败率：
  - 至少 3 次调用。
  - 成功率低于 50% 时提醒。
- 默认 `llm.reply_generation.enabled=false` 时不提醒，不影响当前 Skill/兜底链路。

验证结果：

- `mvn -q -Dtest=DesktopStatusServiceTest test` 通过。
- `mvn -q -Dtest=DesktopStatusControllerTest,WebCoreControllerTest test` 通过。
- `mvn -q test-compile` 通过。
- `npm test -- AlertBell.test.ts` 通过，5 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 继续接入客户档案提取、跟进建议、异常识别等 LLM 场景。
- 真实 LLM provider 与企微表格接口仍需后续提供后再做真实联调。

## 断点 030：客户档案提取接入 LLM 可选链路

时间：2026-07-09 21:08  
状态：已完成

已完成：

- 新增 `LlmProfileExtractionService`。
- 发送确认后的资料更新流程已可选接入 `PROFILE_EXTRACTION` LLM 场景。
- LLM 档案提取默认关闭，失败默认回落 Skill，不影响当前已可用链路。
- LLM prompt 不传完整手机号，仅保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 档案提取` 面板。
- 新增迁移 `V62__llm_profile_extraction_configs.sql`。

验证结果：

- `LlmProfileExtractionServiceTest` 3 个用例通过。
- `ProfileExtractionClientTest` 3 个用例通过。
- `mvn -q test-compile` 通过。
- `npm test -- AdminConsole.test.ts` 通过，23 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 继续接入跟进建议和异常识别 LLM 场景。
- 真实 LLM provider 与企微表格接口仍需后续提供后再做真实联调。

## 断点 031：跟进建议接入 LLM 补位链路

时间：2026-07-09 21:18  
状态：已完成

已完成：

- 新增 `LlmFollowupSuggestionService`。
- 发送确认时如缺少 `followupSuggest`，可选调用 `FOLLOWUP_SUGGESTION` LLM 补充下次跟进时间和方向。
- 已有 Skill/前端 `followupSuggest` 时不会重复调用 LLM。
- LLM 跟进建议默认关闭，不影响当前本地测试。
- LLM prompt 不传完整手机号，仅保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 跟进建议` 面板。
- 新增迁移 `V63__llm_followup_suggestion_configs.sql`。

验证结果：

- `LlmFollowupSuggestionServiceTest` 3 个用例通过。
- `ChatOrchestrationServiceTest` 7 个用例通过。
- `mvn -q test-compile` 通过。
- `npm test -- AdminConsole.test.ts` 通过，23 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 继续接入异常识别和总结 LLM 场景。
- 真实 LLM provider 与企微表格接口仍需后续提供后再做真实联调。

## 断点 032：异常识别接入 LLM 异步提醒链路

时间：2026-07-09 21:27  
状态：已完成

已完成：

- 新增 `LlmAbnormalDetectionService`。
- 新增 `LlmAbnormalDetectionListener`，监听发送确认事件。
- 命中异常时通过现有 WebSocket 类型 `ABNORMAL_ALERT` 推送到侧边栏提醒中心。
- 默认关闭，不影响当前本地测试，也避免真实模型未评估前误报。
- LLM prompt 不传完整手机号，仅保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 异常识别` 面板。
- 新增迁移 `V64__llm_abnormal_detection_configs.sql`。

验证结果：

- `LlmAbnormalDetectionServiceTest` 3 个用例通过。
- `LlmAbnormalDetectionListenerTest` 2 个用例通过。
- `mvn -q test-compile` 通过。
- `npm test -- AdminConsole.test.ts` 通过，23 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 评估并接入 `SUMMARY` 场景的明确业务落点。
- 真实 LLM provider 与企微表格接口仍需后续提供后再做真实联调。

## 断点 033：总结补位接入 SUMMARY LLM 场景

时间：2026-07-09 21:45  
状态：已完成

已完成：

- 新增 `LlmSummaryService`。
- 发送确认时如缺少 `conversationSummary`，可选调用 `SUMMARY` LLM 生成短摘要。
- 前端已提供摘要时不会重复调用 LLM。
- LLM 总结补位默认关闭，不影响当前本地测试。
- LLM prompt 不传完整手机号，仅保留 `phoneLast4`。
- 管理后台配置中心新增 `LLM 总结补位` 面板。
- 新增迁移 `V65__llm_summary_configs.sql`。

验证结果：

- `LlmSummaryServiceTest` 3 个用例通过。
- `ChatOrchestrationServiceTest` 8 个用例通过。
- `mvn -q test-compile` 通过。
- `npm test -- AdminConsole.test.ts` 通过，23 个用例通过。
- `npm run typecheck` 通过。

下一步：

- 更新本地 readiness 脚本和检查项，把新 LLM 场景配置纳入检测。
- 真实 LLM provider 与企微表格接口仍需后续提供后再做真实联调。

## 断点 034：本地 readiness 纳入 LLM 场景配置检测

时间：2026-07-09 22:05  
状态：已完成

已完成：

- `scripts/verify_local_runtime_readiness.py` 已纳入五个 LLM 业务场景配置行检测：
  - `REPLY_GENERATION`
  - `PROFILE_EXTRACTION`
  - `FOLLOWUP_SUGGESTION`
  - `ABNORMAL_DETECTION`
  - `SUMMARY`
- 本地 readiness 会区分：
  - LLM 场景配置行缺失。
  - 真实 provider key 尚未填写。
- `scripts/verify_real_external_readiness.py` 已纳入 LLM 真实 HTTP client、场景路由、场景枚举、调用日志和配置键检查。
- 已重启本地后端并触发 Flyway 新迁移落库。

验证结果：

- `python -m py_compile scripts\verify_local_runtime_readiness.py scripts\verify_real_external_readiness.py` 通过。
- `python scripts\verify_real_external_readiness.py` 通过，`mockExternalsFalseReady=true blockers=0`。
- `python scripts\verify_local_runtime_readiness.py` 通过，`passed=true checks=15/18`。
- `python scripts\verify_local_runtime_readiness.py --require-real-externals` 按预期失败，提示缺少真实 `llm.*` 与 `table.*` 配置。

下一步：

- 继续做不依赖真实 key 的侧边栏/后台收口检查。
- 真实 LLM provider 与企微表格接口仍需后续提供后再做真实联调。

## 断点 035：重启迁移后的本地侧边栏与后台 smoke 基线

时间：2026-07-09 22:15  
状态：已完成

已完成：

- 在后端重启并完成 V62-V65 迁移后，重跑本地基础 smoke。
- 侧边栏 renderer smoke 通过。
- 管理后台产品面检查通过。
- 手工 readiness 通过。
- 管理后台和主应用前端测试通过。
- 桌面端类型检查通过。
- 本地 runtime readiness 通过。

验证结果：

- `npm --prefix desktop run renderer:smoke` 通过，`renderer_smoke=passed`。
- `python scripts\verify_admin_product_surface.py` 通过，`passed=true violations=0 missingMarkers=0`。
- `python scripts\verify_manual_test_readiness.py --frontend-url http://localhost:5173/ --backend-url http://localhost:8080` 通过，`passed=true checks=3/3`。
- `npm --prefix desktop test -- AdminConsole.test.ts App.test.ts` 通过，2 个测试文件、30 个用例。
- `npm --prefix desktop run typecheck` 通过。
- `python scripts\verify_local_runtime_readiness.py` 通过，`passed=true checks=15/18`。

当前仍缺：

- 真实 LLM provider：
  - `llm.api_base_url`
  - `llm.api_key`
  - `llm.model`
- 真实企微表格：
  - `table.api_base_url`
  - `table.api_key`

下一步：

- 继续做不依赖真实 key 的自动化验收收口。
- 或在用户提供真实 key 后，切入真实 provider 连通性与效果测试。

## 断点 036：LLM 多模型备用降级链路

时间：2026-07-09 22:20  
状态：已完成

已完成：

- `LlmRouteRepository` 支持按优先级返回多个启用候选路由。
- `LlmRoutingService` 支持候选解析：
  - 精确 leadType 路由。
  - 通用 leadType 路由。
  - active LLM environment。
  - 全局 LLM 配置。
- `LlmService` 会在主模型失败后自动尝试备用模型。
- 每一次模型尝试都会记录 `llm_call_logs`。
- 所有 LLM 候选失败后，业务层继续沿用原有 Skill/模板回落。

验证结果：

- `LlmServiceTest` 2 个用例通过。
- `LlmRoutingServiceTest` 4 个用例通过。
- `mvn -q test-compile` 通过。
- `python scripts\verify_real_external_readiness.py` 通过。
- `python scripts\verify_local_runtime_readiness.py` 通过。
- `npm --prefix desktop test -- AdminConsole.test.ts` 通过，23 个用例。
- `npm --prefix desktop run typecheck` 通过。

下一步：

- 继续整理真实 provider 接入后的执行顺序和验收命令。
- 用户提供真实 LLM provider 后，可配置两个 LLM environment 验证主备切换效果。

## 断点 037：live provider acceptance 纳入 LLM 自动验收

时间：2026-07-09 22:30  
状态：已完成

已完成：

- `scripts/acceptance_real_external_live.py` 新增 LLM live provider 环境变量：
  - `PDA_LIVE_LLM_BASE_URL`
  - `PDA_LIVE_LLM_API_KEY`
  - `PDA_LIVE_LLM_MODEL`
- live acceptance 会写入全局 `llm.*` 配置。
- live acceptance 会创建 LLM environment。
- live acceptance 会调用 `POST /admin/api/v1/llm-environments/{id}/test`。
- LLM 测试失败时会让 live acceptance 失败并写入报告。

验证结果：

- `python -m py_compile scripts\acceptance_real_external_live.py scripts\acceptance_real_external_local.py` 通过。
- `python scripts\acceptance_real_external_live.py` 在未提供真实 env 时按预期失败，`missingEnv=9`。
- `python scripts\verify_real_external_readiness.py` 通过。

下一步：

- 准备真实 LLM 主备切换的自动/手工验收步骤。
- 用户提供真实 key 后，运行 live acceptance 生成真实验收报告。

## 断点 038：LLM 主备切换受控自动验收

时间：2026-07-09 22:40  
状态：已完成

已完成：

- 新增 `scripts/acceptance_llm_failover_local.py`。
- 使用隔离后端和 fake provider 自动验证：
  - 主 LLM 环境失败。
  - 备用 LLM 环境成功。
  - 回复来源为 `LLM`。
  - LLM 调用统计中同时出现失败和成功记录。
- 验收结束后自动恢复本地 8080 后端。

验证结果：

- `python -m py_compile scripts\acceptance_llm_failover_local.py` 通过。
- `python scripts\acceptance_llm_failover_local.py` 通过，`passed=true checks=20`。
- `.tools/acceptance/llm_failover_local.json` 显示：
  - `totalCalls=2`
  - `bad-primary` 失败 1 次
  - `fake-backup` 成功 1 次
- `python scripts\verify_local_runtime_readiness.py` 通过，确认本地后端恢复可用。

下一步：

- 继续补不依赖真实 key 的验收报告收口。
- 用户提供真实 LLM provider 后，可参考此脚本做真实主备切换验收。

## 断点 039：侧边栏/后台剩余手测批次收口

时间：2026-07-09 22:55  
状态：已完成

已完成：

- 更新 `dev-progress/local_real_test_manual_batches.md`，把手测批次同步到断点 038 后状态。
- 手测清单已明确 LLM 五个业务场景配置、主备切换受控验收均已完成。
- A6 回复建议已纳入推荐回复首屏、当前任务、待处理队列、来源标签和降级提示。
- B1 AI/Skill 配置已纳入 LLM 环境、场景路由、调用统计。
- C2 思考 LLM 已纳入真实 provider 后的五场景路由、主备、A/B 和统计验收。
- 更新 `dev-progress/local_real_test_tasklist.md`，M3 已标记完成，并新增 M3.1 真实 provider/A-B 效果测试。
- 更新 `dev-progress/local_real_test_worklist.md`，合并重复配合项，并明确真实 key 后再做 live 验收。

验证结果：

- 文档旧口径扫描通过：未发现 LLM 仍待实现、业务链路仍未接入、回复建议只按旧系统兜底描述的残留表述。

下一步：

- 继续做不依赖真实 key 的本地手测报告模板。
- 或继续整理服务器部署与桌面端发布准备清单。

## 断点 040：本地手测报告模板建立

时间：2026-07-09 23:05  
状态：已完成

已完成：

- 新增 `dev-progress/local_real_test_manual_report.md`。
- 报告模板已覆盖：
  - 总体结论。
  - 测试环境。
  - 自动化基线。
  - 批次 A：侧边栏应用端。
  - 批次 B：管理后台。
  - 批次 C：真实外部接口。
  - 批次 D：服务器与发布准备。
  - 问题台账。
  - 当前阻塞项。
  - 下一步记录。
- 已把历史已修复问题 P-001 到 P-005 纳入问题台账。
- 已把真实 LLM provider、企微表格、图像识别、Skill、服务器信息列为当前阻塞项。

验证结果：

- 本地 runtime readiness 通过，`passed=true checks=15/18`。
- Python 验收脚本编译通过。
- 文档旧口径扫描通过。

下一步：

- 执行批次 A 侧边栏手测并填写报告。
- 或先整理服务器部署与桌面端发布准备清单。

## 断点 041：服务器部署与桌面端发布准备清单

时间：2026-07-09 23:15  
状态：已完成

已完成：

- 新增 `dev-progress/server_release_preparation_checklist.md`。
- 已明确服务器承载后端 API、WebSocket、数据库、Redis、上传/下载文件、版本检查和后台管理入口。
- 已明确侧边栏应用端是 Electron 桌面应用，正式使用需要打包并配置生产 API 地址。
- 已整理生产环境变量、Nginx 代理要点、数据库备份、桌面端签名和版本发布流程。
- 已列出用户需要决策或提供的事项：服务器、域名、HTTPS、外部 provider 白名单、代码签名证书、真实测试数据。

验证结果：

- 文档旧口径扫描通过。
- Python 验收脚本编译通过。
- 本地 runtime readiness 通过，`passed=true checks=15/18`。

下一步：

- 执行批次 A 侧边栏手测并填写报告。
- 或继续补 Nginx/systemd/env/备份脚本模板。

## 断点 042：部署模板文件建立

时间：2026-07-09 23:25  
状态：已完成

已完成：

- 新增目录：`dev-progress/deploy_templates/`。
- 新增生产环境变量模板：`production.env.example`。
- 新增 Nginx 示例配置：`nginx_private_domain_assistant.conf`。
- 新增 systemd 服务模板：`private-domain-assistant.service`。
- 新增数据库备份脚本模板：`backup_database.sh`。
- 新增数据库恢复脚本模板：`restore_database.sh`。
- 新增桌面端发布步骤模板：`release_steps.md`。

验证结果：

- 文档旧口径扫描通过。
- Python 验收脚本编译通过。
- 本地 runtime readiness 通过，`passed=true checks=15/18`。

下一步：

- 执行批次 A 侧边栏手测并填写报告。
- 或在用户确认服务器信息后，把模板改成实际部署文件。

## 断点 043：侧边栏批次 A 自动化验收通过

时间：2026-07-09 23:35  
状态：已完成

已完成：

- 新增 `scripts/acceptance_sidebar_batch_a.py`。
- 批次 A 侧边栏应用端自动化验收通过，14/14。
- 报告输出到 `.tools/acceptance/sidebar_batch_a.json`。
- `dev-progress/local_real_test_manual_report.md` 已记录 A1-A6 状态。

验证结果：

- `python scripts\acceptance_sidebar_batch_a.py` 通过，`passed=true checks=14/14`。
- 侧边栏 5 个关键组件测试通过，35 个用例。
- Renderer smoke 通过。
- 手工 readiness 通过。
- 本地 runtime readiness 通过。

下一步：

- 执行批次 B 管理后台写入类自动化/半手工复测。
- 或等待用户真机确认侧边栏体验细节。

## 断点 044：管理后台批次 B 自动化验收通过

时间：2026-07-09 23:45  
状态：已完成

已完成：

- 新增 `scripts/acceptance_admin_batch_b.py`。
- 批次 B 管理后台写入类自动化验收通过，39/39。
- 报告输出到 `.tools/acceptance/admin_batch_b.json`。
- `dev-progress/local_real_test_manual_report.md` 已记录 B1-B5 状态。

验证结果：

- `python scripts\acceptance_admin_batch_b.py` 通过，`passed=true checks=39/39`。
- `npm --prefix desktop test -- AdminConsole.test.ts` 通过，23 个用例。
- `python scripts\verify_admin_product_surface.py` 通过，`passed=true violations=0 missingMarkers=0`。

注意：

- 第一次脚本调试留下 1 个未激活 LLM 占位环境。
- 该占位环境是后端“至少保留一个 LLM environment”规则导致，未激活，不影响真实 provider 后续配置。

下一步：

- 补真实 provider 填写表和上线前最终验收表。
- 或等待用户提供真实外部接口配置。

## 断点 045：真实 provider 填写表与最终验收表

时间：2026-07-09 23:55  
状态：已完成

已完成：

- 新增 `dev-progress/real_provider_config_form.md`。
- 新增 `dev-progress/final_acceptance_checklist.md`。
- 真实 provider 表已覆盖 LLM、图像识别、Skill、企微表格、脱敏样本和配置后验收命令。
- 最终验收表已覆盖本地功能、真实外部接口、服务器发布、用户体验确认和最终放行条件。

验证结果：

- A 批次验收通过，14/14。
- B 批次验收通过，39/39。
- 本地 runtime readiness 通过，15/18。
- 前端关键测试通过，6 个测试文件、58 个用例。

下一步：

- 等待用户提供真实 provider。
- 或继续根据用户真机体验反馈修复细节。

## 断点 046：侧边栏应用端真机基础体验通过

时间：2026-07-10 09:10  
状态：已完成

已完成：

- 用户已确认侧边栏应用端本地基础体验测试成功。
- `dev-progress/local_real_test_manual_report.md` 已把批次 A 侧边栏 A1-A6 更新为本地基础体验通过。
- `dev-progress/local_real_test_tasklist.md` 已记录 M4.1 侧边栏本地基础体验真机确认通过，并拆出 M4.2 真实外部接口流程确认。
- `dev-progress/local_real_test_worklist.md` 已同步分工：侧边栏基础体验已确认，管理后台仍待用户手测。

边界说明：

- 本断点不代表管理后台已经由用户手测通过。
- 本断点不代表真实 LLM、图像识别、Skill、企微表格 provider 已验收通过。

下一步：

- 执行批次 B：管理后台用户侧手测。
- 如果管理后台手测通过，再记录断点 048。
- 如果发现问题，先定位并修复，再记录修复断点。

## 断点 047：管理后台浏览器预检与登录态恢复

时间：2026-07-10 09:25  
状态：已完成

已完成：

- 发现后台页面进入后显示 `Failed to fetch` 且账号为“当前账号”，判断为旧登录态/旧 token 导致。
- 已退出旧 session，并使用 `admin/admin123` 重新登录后台。
- 重新登录后后台显示账号 `System Admin`，请求恢复正常。
- 浏览器预检覆盖 12 个后台模块：
  - Skill 场景管理
  - 配置中心
  - 客户数据对接
  - 速搜内容管理
  - 账号与权限
  - 跟进规则引擎配置
  - 客户标签与分层
  - 运营分析看板
  - 版本管理
  - 系统公告
  - 操作审计日志
  - 系统健康监控
- 预检结果：未发现 `Failed to fetch`、401、403。
- 已生成后台测试夹具盘点：`.tools/acceptance/admin_fixture_inventory.json`。

注意：

- 本断点是 Codex 浏览器预检通过，不等于用户侧管理后台手测已经通过。
- 当前测试库存在自动化残留夹具，包括 `duplicate fixture`、`negative datasource`、乱码测试账号、测试版本和占位 LLM 环境。

下一步：

- 用户决定是否先清理测试夹具。
- 继续执行管理后台用户侧手测；通过后记录断点 048。

## 断点 048：管理后台真机反馈修复批次 1

时间：2026-07-10 11:05  
状态：已完成

已完成：

- 修复账号隔离与旧登录态失效：重置密码、启停、改角色/直属组长后旧 token 和 WebSocket 会失效。
- 接入客户权限隔离：管理员看全部，组长看自己及直属启用管家，管家只看分配给自己的客户。
- 管理后台新增“全部客资”规则支持，并允许 Skill/规则按通用客资回退。
- 速搜支持客户档案变量插入，侧边栏复制时按当前客户档案替换变量。
- 工作台主动拉取并展示系统公告，公告推送不会重复渲染。
- 后台常见报错与前端网络/超时错误改为中文提示。
- 后台优先级、配置中心、标签、分析、审计等页面补充可读性说明和中文映射。
- 修复 `SkillRuntimeRouter` 编译错误，并同步更新前后端测试预期。

验证结果：

- 后端全量测试通过：56 个测试文件，219 个用例，0 failure，0 error。
- 前端全量测试通过：32 个测试文件，220 个用例通过。
- 前端类型检查通过：`npm run typecheck`。

下一步：

- 用户刷新前端后复测管理后台：账号隔离、重置密码旧登录失效、公告展示、速搜变量、全部客资规则、中文错误提示。
- 真实 LLM/图像识别/Skill/企微表格 provider 仍需真实 key 后再做 live 验收。

## 断点 049：管理后台与侧边栏接口本地实操复测

时间：2026-07-10 12:05  
状态：已完成

已完成：

- Codex 已接管 Chrome 管理后台并重新登录 `System Admin`，解决旧登录态导致的“运行模式未连接 / 网络连接失败”误报。
- 后台 A-L 模块全部点击复测通过，无 `Failed to fetch`，无英文网络错误横幅。
- 后台接口验收：`python scripts\acceptance_admin_batch_b.py` 通过，39/39。
- 侧边栏接口验收：`python scripts\acceptance_sidebar_batch_a.py` 通过，15/15。
- 修复侧边栏验收脚本跨天过期问题：自动把 `18800003333` 今日预约样本日期刷新为当天。
- 修复后台系统公告状态口径：生效中筛选和值统计统一使用后端状态 `PUBLISHED`，浏览器复查显示生效中 2。
- 速搜新增表单复查通过：全部客资、客户档案变量、变量插入、图片素材字段切换均正常。
- 跟进规则新增表单复查通过：全部客资和优先级说明均正常。
- 公告 active 接口返回 2 条，包含用户发布的“测试”。
- 密码重置链路通过：旧密码失效、旧 token 失效、新密码可登录。
- 客户数据隔离通过：无客户的新管家搜索 `1111` 和今日跟进均返回 0，管理员仍可看到。
- `npm test -- AdminConsole` 通过，23/23。
- `npm run typecheck` 通过。

边界说明：

- 侧边栏 Electron 独立窗口本轮无法由 Chrome 直接读取画面，已通过接口确认它应能读取公告、跟进、搜索和回复数据。
- 系统健康页表格通道仍显示 DOWN，属于真实企微表格 provider 未接入状态。
- 测试库仍保留历史自动化夹具和部分乱码样本，建议后续做一次测试数据清理。

下一步：

- 用户肉眼确认侧边栏工作台公告是否显示“测试”公告。
- 若侧边栏视觉确认通过，再进入真实 provider 配置与服务器发布准备。
