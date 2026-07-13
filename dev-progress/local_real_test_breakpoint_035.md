# 断点 035：重启迁移后的本地侧边栏与后台 smoke 基线

时间：2026-07-09 22:15  
状态：已完成

本轮目标：

- 在后端重启并完成 V62-V65 迁移后，确认本地侧边栏与管理后台仍可操作。
- 继续执行不依赖真实 LLM/企微表格 key 的本地收口检查。

处理结果：

- 本地后端保持运行：
  - `http://localhost:8080`
  - 测试库：`private_domain_assistant_smoke`
  - 当前真实外部配置仍缺 LLM 和企微表格 key，但不阻塞本地基础测试。
- 侧边栏 renderer smoke 通过：
  - 覆盖桌面端 build、Electron renderer smoke、基础桥接和主要入口。
- 管理后台产品面检查通过：
  - 配置中心、Skill、客户数据、速搜、账号权限、规则、标签、看板、公告、审计、健康监控等标记均存在。
- 手工 readiness 通过：
  - 前端地址可访问。
  - 后端 auth config 可访问。
  - `admin/admin123` 可登录。
- 前端定向测试和类型检查通过：
  - 管理后台和主应用基础测试通过。
  - Vue/TS 类型检查通过。

验证结果：

- `npm --prefix desktop run renderer:smoke`
  - `renderer_smoke=passed`
- `python scripts\verify_admin_product_surface.py`
  - `passed=true violations=0 missingMarkers=0`
- `python scripts\verify_manual_test_readiness.py --frontend-url http://localhost:5173/ --backend-url http://localhost:8080`
  - `passed=true checks=3/3`
- `npm --prefix desktop test -- AdminConsole.test.ts App.test.ts`
  - 2 个测试文件通过，30 个用例通过。
- `npm --prefix desktop run typecheck`
  - 通过。
- `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=15/18`
  - 警告项仍为真实外部配置缺失：
    - `llm.api_base_url`
    - `llm.api_key`
    - `llm.model`
    - `table.api_base_url`
    - `table.api_key`

当前结论：

- 现在本地基础链路是可测状态。
- 如果继续不需要用户配合，可以进入更细的侧边栏/后台自动化与文档收口。
- 如果进入真实接口联调，需要先补齐真实 LLM provider 和企微表格配置。

下一步：

- 优先继续做不依赖真实 key 的自动化验收收口。
- 或在用户提供真实 key 后，切入真实 provider 连通性与效果测试。
