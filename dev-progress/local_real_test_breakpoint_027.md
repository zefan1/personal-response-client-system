# 断点 027：管理后台 LLM 回复、路由与统计 UI 接入

时间：2026-07-09 20:15  
状态：已完成

本轮目标：

- 继续断点 026 后的管理后台补齐，不需要用户额外配合。
- 把后端已完成的 LLM 回复生成配置、场景路由和调用统计暴露到运营后台配置中心。
- 确保后续配置真实 LLM provider 后，可以在后台完成开关、路由、监控闭环。

处理结果：

- 管理后台配置中心新增 `LLM 回复生成` 面板：
  - 可配置 `llm.reply_generation.enabled`。
  - 可配置 `llm.reply_generation.fallback_to_skill`。
  - 可配置温度覆盖、最大 tokens、系统 Prompt。
  - 默认仍保持关闭，避免未配置真实 LLM key 时影响当前回复建议链路。
- 管理后台配置中心新增 `LLM 场景路由` 面板：
  - 可查看场景、线索类型、环境、模型、优先级、启用状态。
  - 支持新增、编辑、启停、删除路由。
  - 对接真实接口：`/admin/api/v1/llm-routes`。
- 管理后台配置中心新增 `LLM 调用统计` 面板：
  - 展示近 7/14/30 天调用次数、成功率、平均响应时间。
  - 展示按场景、线索类型、模型聚合的调用明细。
  - 对接真实接口：`/admin/api/v1/analytics/llm-calls`。
- 配置中心刷新时会同步加载：
  - LLM 环境。
  - LLM 路由。
  - LLM 场景字典。
  - LLM 调用统计。
  - `llm.reply_generation.*` 配置。
- 保存配置、路由启停和删除后会自动刷新页面数据，减少手动点刷新。

涉及文件：

- `desktop/src/renderer/modules/admin/AdminConsole.vue`
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- `desktop/src/renderer/styles.css`

验证结果：

- 管理后台定向测试通过：
  - `npm test -- AdminConsole.test.ts`
  - 1 个测试文件、23 个用例通过。
- 桌面端类型检查通过：
  - `npm run typecheck`

当前可手工验证：

- 打开管理后台 `配置中心`。
- 查看是否出现：
  - `LLM 回复生成`
  - `LLM 场景路由`
  - `LLM 调用统计`
- 在已有 LLM 环境后，可新增一条路由，例如：
  - 场景：`回复生成`
  - 线索类型：`待确认`
  - 环境：选择当前 LLM 环境
  - 优先级：`10`
- 保存后列表应自动刷新。
- 未配置真实 LLM key 前，不建议打开 `启用 LLM 回复生成`。

下一步：

- 侧边栏回复建议需要展示当前回复来源：`LLM / Skill / 兜底`。
- 真实 LLM provider 配置仍需用户后续提供：`base URL`、`API key`、`model`。
