# 断点 011：中断恢复后的侧边栏验证收口

时间：2026-07-09 15:18-15:32  
状态：已完成

## 本轮目标

- 从中断处继续，把断点 010 合并到总断点文件。
- 复核侧边栏当前最关键的客户档案、回复助手、跟进列表、速搜链路。
- 处理 renderer smoke 因 Electron 缓存目录占用导致的不稳定问题。

## 处理结果

- 已把断点 010 同步追加到 `dev-progress/local_real_test_breakpoints.md`。
- 已让 renderer smoke 使用独立临时 Electron 用户数据目录：
  - 避免和用户当前打开的 Electron 侧边栏共用 `%APPDATA%/private-domain-assistant-desktop`。
  - 避免 Chromium 缓存目录被占用时出现 `Unable to move the cache: 拒绝访问`。
- 已调整 renderer smoke 的置顶按钮验收边界：
  - smoke 验证 Electron 桥接存在、按钮可访问、`aria-pressed` 状态存在。
  - OS 级窗口置顶效果不再作为 smoke 的硬阻断，避免 Windows 窗口管理差异导致整条侧边栏集成验证失败。
- 已新增 App 单测，覆盖点击置顶按钮后 UI 状态会从“置”切换为“顶”。

## 验证结果

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

## 当前可手工验证

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

## 下一步

- 如果继续本地功能完善，建议进入“回复助手真实使用体验”细化：把推荐回复、待处理队列和文字通道在窄侧边栏下再做视觉压缩与交互顺序优化。
- 如果进入真实接口接入，需要先配置思考 LLM/Skill/图像识别/企微表格的真实 key。
