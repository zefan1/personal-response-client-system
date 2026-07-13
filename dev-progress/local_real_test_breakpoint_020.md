# 断点 020：客户档案表格同步状态可见性增强

时间：2026-07-09 17:40-17:52  
状态：已完成

## 本轮目标

- 继续侧边栏应用端本地真实测试，不需要用户额外配合。
- 解决客户档案保存后“同步到企微表格”只靠底部 toast 提示的问题。
- 让私域同事在保存、等待同步、同步中、同步成功、失败后台重试、暂不同步这些状态下都能看到明确反馈。

## 处理结果

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

## 涉及文件

- `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts`
- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- `desktop/src/renderer/styles.css`
- `desktop/src/renderer/modules/customer-profile/customerProfileStore.test.ts`
- `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts`

## 验证结果

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

## 当前可手工验证

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

## 下一步

- 继续侧边栏应用端本地可完成检查。
- 下一块建议检查“异常提醒 / 离线降级 / 帮助模式”：
  - 异常提醒是否能从全局铃铛进入历史。
  - 当前客户异常是否能在档案和回复助手里同步展示。
  - 离线或接口失败时是否有明确状态，而不是静默不可用。
  - 帮助模式是否能让一线发起求助、组长处理并回填建议。
