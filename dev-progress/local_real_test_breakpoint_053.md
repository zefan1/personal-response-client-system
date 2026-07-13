# 本地真机断点 053

时间：2026-07-13
状态：本批问题已完成，服务已重启
对应任务：`dev-progress/local_real_test_issue_tasklist_053.md`

## 本次完成

1. 账号安全变更通过在线 WebSocket 推送 `AUTH_INVALIDATED`，侧边栏立即清理登录状态并返回登录页；失效消息不写离线队列，避免新登录后误重放。
2. 账号表格使用独立 7 列网格，操作按钮固定在状态后的操作列；窄窗口使用横向滚动，不再掉到姓名下方。
3. 新增 `/admin/api/v1/customers/search` 管理员客户分页查询，支持姓名/昵称、手机号、门店、项目、来源、管家和来源行标识；后台“客户数据对接”新增客户查询与档案摘要。
4. 提醒弹层改为视口固定定位，修复主内容横向裁切；补全来源、等级、状态、历史收起和清空。
5. 速搜变量统一显示并插入中文占位符，侧边栏同时兼容中文双花括号、旧英文双花括号和旧单花括号变量。
6. 客户字段字典补齐中文业务名称，后端不再把未映射字段直接返回英文 key 作为界面名称。
7. renderer smoke 改为真实管理员登录，并增加桌面提醒弹层和管理后台关键流程检查。

## 验证结果

- Java 回归：29/29 通过。
- Vue/Vitest 全量：35 个文件、232/232 通过。
- `npm run typecheck`：通过。
- `npm run build`：通过。
- `npm run renderer:smoke`：桌面端通过。
- `PDA_RENDERER_SMOKE_TARGET=admin` renderer smoke：管理后台通过。
- `scripts/acceptance_admin_batch_b.py`：39/39 通过。
- `scripts/verify_database_alignment.py`：34 张表、933 个 Repository 列引用、0 违规。
- 真实客户查询：`1111` 返回 1 条；空关键词分页和中文参数错误通过。

## 当前运行

- 后端：`http://localhost:8080`，WSL 后端 PID 文件记录为 `6091`，`MOCK_EXTERNALS=false`。
- 管理后台：`http://127.0.0.1:5173/#/admin`，Vite PID `22792`。
- 侧边栏：Electron 主进程 PID `24368`，已加载最终构建。

## 剩余配置风险

- 当前真实外部表格接口尚未完整配置，历史测试数据源 `negative_20260709092920` 会周期性记录 `table.api_base_url is required when MOCK_EXTERNALS=false`。本批功能不依赖该数据源，但正式接企微表格前必须填写真实表格 API 地址和密钥，并停用或删除无效测试数据源。
- 当前识图环境仍指向示例地址，真实调用返回 HTTP 405；需要在“配置中心”填写实际识图 API 地址、模型和密钥。
- LLM 和表格真实供应商密钥仍按上线配置清单填写；本批代码已支持配置，但不会写死供应商凭据。

## 下次继续位置

本批没有未完成代码项。下一步可从真实外部服务配置与全链路验收继续，或由用户按 tasklist 重新点击确认界面结果。
