# 断点 023：LLM 多环境配置中心与本地 V59 迁移接入

时间：2026-07-09 18:10-18:50  
状态：已完成

## 本轮目标

- 继续本地真实测试准备，不需要用户额外配合。
- 把用户提出的“缺少思考 LLM 接口、最好可配置多个 LLM 测试”从 tasklist 落到可操作配置入口。
- 先完成 LLM provider 管理、激活同步和连通测试，为后续回复建议/档案提取/跟进策略接入 LLM 做准备。

## 处理结果

- 后端新增 LLM 环境类型：
  - `AiEnvironmentType.LLM`
  - 路由前缀：`/admin/api/v1/llm-environments`
- 新增数据库迁移：
  - `V59__llm_environment_configs.sql`
  - 新表：`llm_environments`
  - 新配置：`llm.api_base_url`、`llm.api_key`、`llm.model`、`llm.protocol`、`llm.timeout_ms`、`llm.temperature`、`llm.max_tokens`
- LLM 环境支持字段：
  - 环境名称、Base URL、API Key、模型名、协议、超时、温度、最大 tokens、启用状态、最近测试状态。
  - API Key 继续使用后端加密存储，前端只显示脱敏信息。
- 后端新增 LLM 环境能力：
  - 列表、创建、编辑、启用、删除。
  - 激活后同步当前运行配置到 `system_configs`。
  - OpenAI-compatible 连通测试：向 `/v1/chat/completions` 发最小测试请求，返回耗时和结果摘要。
- 管理后台配置中心新增：
  - `LLM 思考环境` 卡片。
  - 多环境列表、当前启用状态、模型/协议/超时/测试状态展示。
  - 新增/编辑 LLM 环境抽屉。
  - 启用、删除、测试连接按钮。
- 管理后台开发调试台新增：
  - LLM 环境读取。
  - 创建 LLM 环境。
  - 激活 LLM 环境。
  - 测试 LLM 环境。
- 本地 readiness 脚本更新：
  - `scripts/verify_local_runtime_readiness.py`
  - 真实外部配置强校验现在包含 `llm.api_base_url`、`llm.api_key`、`llm.model`。

## 本地运行时状态

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

## 验证结果

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

## 当前可手工验证

- 打开管理后台 `配置中心`。
- 查看是否出现 `LLM 思考环境`。
- 点击 `新增环境`，可填写：
  - 环境名称
  - 服务地址
  - API Key
  - 模型名称
  - 协议：OpenAI Compatible
  - 超时、温度、最大 Tokens
- 保存后可点击：
  - `启用`
  - `测试连接`
- 如果尚未填真实 provider，测试连接失败是预期状态；配置保存、列表显示和启用逻辑应可用。

## 下一步

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
