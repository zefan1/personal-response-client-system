# 断点 043：侧边栏批次 A 自动化验收通过

时间：2026-07-09 23:35  
状态：已完成

本轮目标：

- 对批次 A 侧边栏应用端做不依赖真实 key 的自动化/半手工复测。
- 把 A1-A6 的结果写入 `dev-progress/local_real_test_manual_report.md`。

处理结果：

- 新增脚本：`scripts/acceptance_sidebar_batch_a.py`。
- 脚本覆盖：
  - A1 登录与首页状态：后端可访问、管理员登录、桌面状态接口。
  - A2 工作台到客户档案：跟进数据包含 `18800001111`，详情可加载，`phoneFull` 存在。
  - A3 客户档案搜索与编辑基础：搜索 `1111` 命中客户，详情包含中文样本字段。
  - A4 跟进列表：今日跟进接口返回 3 条样本。
  - A5 速搜：返回两条中文模板和 `local_*` 快捷码。
  - A6 回复建议基础链路：回复生成接口返回可用内容，`replySource` 明确。
- 更新 `dev-progress/local_real_test_manual_report.md`，A1-A6 均记录为自动化通过或自动化通过但待真机体验确认。

验证结果：

- `python -m py_compile scripts\acceptance_sidebar_batch_a.py` 通过。
- `python scripts\acceptance_sidebar_batch_a.py` 通过：
  - `passed=true checks=14/14`
  - 报告：`.tools/acceptance/sidebar_batch_a.json`
- 侧边栏定向组件测试通过：
  - `WorkbenchPanel.test.ts`
  - `CustomerProfilePanel.test.ts`
  - `FollowupListPanel.test.ts`
  - `QuickSearchOverlay.test.ts`
  - `ReplySuggestionPanel.test.ts`
  - 5 个测试文件、35 个用例通过。
- Renderer smoke 通过：
  - `renderer_smoke=passed`
- 手工 readiness 通过：
  - `passed=true checks=3/3`
- 本地 runtime readiness 通过：
  - `passed=true checks=15/18`

当前结论：

- 批次 A 的基础可操作性已由自动化证明，不再是“死的不能操作”。
- A6 当前回复来源为系统兜底，这是本地真实 LLM/Skill provider 未配置时的预期状态，不是页面无反应。
- 客户档案编辑保存的真机体验仍建议用户后续在界面上点一次确认，但历史根因已在断点 008 修复并做过逐字段接口验证。

下一步：

- 执行批次 B 管理后台写入类自动化/半手工复测。
- 或等待用户真机确认侧边栏体验细节。
