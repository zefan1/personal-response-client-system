# Electron 侧边栏一线工作台产品化改造进度

## 范围

- 主导航收敛为 `工作台 / 客户 / 回复`。
- `聊天识别` 改为全局底部操作栏按钮，不再作为主菜单页。
- `跟进列表` 改为待办队列抽屉，仍复用原跟进列表组件和批量选择能力。
- `话术建议` 产品化为 `回复助手`，并承接备用文字通道。
- 侧栏品牌区只显示账号名和 Skill 有效期状态。
- `后台` 入口只对 `ADMIN/LEADER` 显示，仍通过外部浏览器打开。
- 告警入口默认隐藏，并聚合异常客户、离线/WS、桌面桥接错误、系统公告、Skill 到期风险。
- 新增 `/api/v1/desktop/status`，Skill 有效期来自 `system_configs.skill.subscription_expire_at`。

## 已完成文件

- `desktop/src/renderer/App.vue`
- `desktop/src/renderer/styles.css`
- `desktop/src/renderer/shared/desktopStatusStore.ts`
- `desktop/src/renderer/shared/desktopNoticeStore.ts`
- `desktop/src/renderer/modules/abnormal-alert/globalAlertCenter.ts`
- `desktop/src/renderer/modules/abnormal-alert/AlertBell.vue`
- `desktop/src/renderer/modules/workbench/WorkbenchPanel.vue`
- `desktop/src/renderer/modules/reply-suggestions/ReplySuggestionPanel.vue`
- `desktop/src/renderer/modules/chat-recognition/recognitionStore.ts`
- `desktop/src/main/main.ts`
- `src/main/java/com/privateflow/modules/desktop/*`
- `src/main/resources/db/migration/V54__desktop_status_skill_subscription.sql`
- 相关前后端测试文件。

## 验证

- `cd desktop && npm run typecheck`：通过。
- `cd desktop && npm run test`：通过，30 个测试文件，159 个用例。
- `cd desktop && npm run renderer:smoke`：通过。
- `python scripts\verify_admin_product_surface.py`：通过，0 violations。
- `python scripts\verify_manual_test_readiness.py --frontend-url http://127.0.0.1:5173/ --backend-url http://127.0.0.1:8080`：通过，3/3。
- `mvn test`：未运行成功，当前环境缺少 `mvn`，仓库无 Maven Wrapper；尝试下载临时 Maven 超时且只得到不完整 zip。

## 待人工验收重点

- 420px Electron 窗口无横向滚动，底部 `识别 / 快线 / 批量` 不遮挡主要内容。
- 任意主页面点击 `识别` 后切到 `回复` 并触发现有截图识别链路。
- `批量` 打开待办队列抽屉，并能选择客户进入批量模板流程。
- 无告警时右上角不显示提醒入口；制造异常/离线/公告/Skill 到期时提醒入口出现且内容同步。
- KEEPER 看不到 `后台`，ADMIN/LEADER 可见并外部浏览器打开后台。

## UI 纠偏记录（2026-07-05）

- 原问题：上一版过度依赖短文案和入口重命名，`客户 / 回复 / 识别 / 快线 / 批量` 在窄侧栏里缺少视觉上下文，用户无法判断每个区域的用途。
- 已修正：侧边栏改为账号卡 + Skill 状态卡 + 图形锚点导航，主导航显示 `工作台 / 客户档案 / 回复助手`，不再把核心入口压缩成难懂短词。
- 已修正：底部全局操作栏改为三枚固定工具键，显示 `识别聊天 / 快捷话术 / 批量跟进`，保留任意页面触发识别、快线、待办队列的行为。
- 已修正：工作台指标卡增加图形标识和卡片层级，待办/新客资空状态改成可见的虚线空状态。
- 已修正：回复助手空状态增加“识别聊天”入口，复用全局识别链路，不新增后端契约。
- 已修正：离线/桌面提醒在顶部只保留一条最高优先级提示，避免离线提示和告警横幅重复。
- 已修正：420px 断点不再把侧栏压回 88px；侧栏宽度、底部操作栏、待办抽屉统一使用 CSS 变量，避免错位和文字溢出。

## UI 纠偏验证

- `cd desktop && npm run typecheck`：通过。
- `cd desktop && npm run test -- App.test.ts WorkbenchPanel.test.ts ReplySuggestionPanel.test.ts`：通过，3 个测试文件，13 个用例。
- `cd desktop && npm run test`：通过，30 个测试文件，159 个用例。
- `cd desktop && npm run renderer:smoke`：通过，包含新导航、新全局操作、待办抽屉、识别聚焦回复助手、横向溢出检查。
- `git diff --check`：通过，仅提示 Windows CRLF 行尾提醒，无 whitespace error。

## UI 微调记录（2026-07-05）

- 原问题：工作台顶部三张指标卡在 420px 窄窗口下纵向堆叠过高，数据仪表盘挤占业务内容，用户需要频繁下滑才能看到今日跟进和新客资。
- 已修正：窄窗口下 `待跟进 / 今日预约 / 新客资` 改为一行紧凑三列指标条，保留点击进入待办队列的能力。
- 验证：`cd desktop && npm run typecheck` 通过；`cd desktop && npm run test -- WorkbenchPanel.test.ts App.test.ts` 通过；420x760 实际截图确认首屏可直接看到今日跟进区域且无横向滚动。

## 置顶与可视区域修正记录（2026-07-05）

- 原问题：侧边栏容易被其他窗口覆盖，用户边看聊天边操作不方便；底部三按钮固定遮挡主内容；账号区、页面标题、工作台标题和仪表盘占用过多首屏空间。
- 已修正：Electron 主进程新增受控 IPC：`window:get-always-on-top`、`window:toggle-always-on-top`，preload 暴露 `getAlwaysOnTop()`、`toggleAlwaysOnTop()`，renderer 只控制当前窗口，不接受任意窗口参数。
- 已修正：账号区去掉头像，只显示账号名、Skill 短状态和 Electron 环境下的 `置顶/已置顶` 小按钮；Web 预览隐藏置顶按钮。
- 已修正：侧边栏宽度收敛为 `104px`，主导航保持图标和文字并行；`识别 / 快线 / 批量` 从底部固定栏移动到侧边栏紧凑快捷区，主内容底部不再被遮挡。
- 已修正：工作台内部移除重复 `工作台` 标题，刷新按钮改为图标按钮；今日跟进、新客资、空状态和指标卡全部压缩留白，420px 下无横向滚动。
- 已修正：全局 `识别` 点击后立即走现有截图识别链路；截图失败时停留当前页面并显示失败提示，识别成功后进入回复助手。
- 已修正：`renderer:smoke` 默认加载构建产物，不再默认依赖 5173 开发服务器；preload 改为 `.cts -> .cjs` 输出，在 `contextIsolation: true`、`nodeIntegration: false`、`sandbox: true` 下验证 bridge 注入、置顶按钮、侧栏快捷区、无旧底部栏、识别失败留当前页/成功进回复助手。

## 置顶与可视区域验证

- `cd desktop && npm run typecheck`：通过。
- `cd desktop && npm run test -- App.test.ts WorkbenchPanel.test.ts desktopBridge.test.ts`：通过，3 个测试文件，15 个用例。
- `cd desktop && npm run test`：通过，30 个测试文件，161 个用例。
- `cd desktop && npm run renderer:smoke`：通过，构建产物 Electron smoke 通过，包含 preload bridge、置顶按钮、侧栏快捷区、待办抽屉、快捷话术、管理后台外部入口、无横向溢出检查。
- `git diff --check`：通过，仅 Windows CRLF 行尾提醒，无 whitespace error。

## 屏幕识别修正记录（2026-07-06）

- 原问题：Electron 截图链路通过窗口标题筛选微信/企业微信窗口，存在平台识别不稳定和窗口监听语义风险；旧 Electron 进程未重启时还会触发 `应用桥接未更新`。
- 已修正：`screenshot:capture` 改为 `desktopCapturer.getSources({ types: ['screen'] })` 截取当前屏幕，不再读取或匹配微信/企微窗口标题。
- 已修正：前端识别失败文案改为屏幕截图/系统录屏权限语义，后续是否存在聊天窗口交给识图 LLM 判断。
- 已验证：`cd desktop && npm run typecheck` 通过；`cd desktop && npm run test` 通过，30 个测试文件，161 个用例；`cd desktop && npm run renderer:smoke` 通过；`git diff --check` 通过，仅 CRLF 提醒。
- 已操作：关闭/确认旧 Electron 进程不再存在后，重新启动 Electron，新进程启动时间为 2026-07-06 09:25。

## 侧边栏入口与建议展示修正记录（2026-07-06）

- 原问题：`置顶/已置顶` 在账号名称同行，占用窄侧栏宽度；右下角固定 `AI 更新建议` 会遮挡回复内容；`快线` 命名和居中弹层不适合一线同事快速扫读。
- 已修正：账号区只展示账号名和 Skill 状态，Electron 置顶控制移动到主内容标题栏右侧 28px 图标按钮，长文案只保留在 tooltip/aria-label。
- 已修正：侧边栏入口从 `快线` 改为 `模板`，仍复用现有 `quick-search` 内部事件和 `/api/v1/quick-search/items` 契约。
- 已修正：模板入口从居中弹层改为右侧抽屉，背景点击、Esc、关闭按钮均可关闭；列表项优先显示中文标题、内容类型、线索类型、场景/模板码、内容预览和 `复制`按钮，不再把 `TEMPLATE/KNOWLEDGE` 等枚举作为主要视觉信息。
- 已修正：全局 `CopyBackfillAgent` 不再渲染右下角建议浮层；资料更新建议复用 `copyBackfillStore`，在回复助手顶部内联展示，默认折叠为一行摘要，展开后支持单条/批量确认拒绝。
- 已验证：`cd desktop && npm run typecheck` 通过；`cd desktop && npm run test -- App.test.ts QuickSearchOverlay.test.ts CopyBackfillAgent.test.ts ReplySuggestionPanel.test.ts` 通过，4 个测试文件，17 个用例；`cd desktop && npm run test` 通过，30 个测试文件，162 个用例；`cd desktop && npm run renderer:smoke` 通过；`git diff --check` 通过，仅 CRLF 行尾提醒。

## 模板、提醒与多客户回复队列修正记录（2026-07-06）

- 原问题：模板抽屉仍受 `quicksearchAutoCloseS=3` 影响，打开后几秒自动消失；复制模板后也会自动关闭。
- 已修正：模板抽屉不再读取自动关闭计时，搜索/筛选/复制后保持打开，只能通过 X 图标、Esc 或点击背景关闭。
- 原问题：提醒中心固定在右上角，与标题栏置顶按钮重叠，导致置顶不可用。
- 已修正：提醒中心移入标题栏右侧工具区，与置顶按钮并排展示；提醒、模板、待办队列、候选客户、求助弹层的关闭入口统一为 X 图标按钮，并保留 aria-label/title。
- 原问题：回复助手只有一份当前结果，连续识别多个客户时，旧客户回复和未完成结果会被覆盖。
- 已修正：识别请求生成 `sessionId`，回复助手维护客户会话队列；每个客户的识别中/已生成/失败/已复制状态和回复结果独立保存，可切换回旧客户继续复制，未完成结果返回后写回对应会话。
- 已验证：`cd desktop && npm run typecheck` 通过；`cd desktop && npm run test` 通过，30 个测试文件，164 个用例；`cd desktop && npm run renderer:smoke` 通过。
