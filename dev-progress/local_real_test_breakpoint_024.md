# 断点 024：统一 LLM runtime client/service 接入

时间：2026-07-09 18:50-19:08  
状态：已完成

## 本轮目标

- 在断点 023 已完成“LLM 多环境配置入口”的基础上，继续补后端统一 LLM 调用能力。
- 避免后续回复建议、档案提取、跟进策略等模块各自硬编码 provider。
- 让配置中心的 LLM 测试连接也复用统一 runtime，实现一条真实调用链。

## 处理结果

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

## 本地运行时状态

- 已重启本地 WSL 后端到最新代码。
- 当前运行模式：
  - `MOCK_EXTERNALS=false`
  - 后端：`http://localhost:8080`
  - 测试库：`private_domain_assistant_smoke`
- 当前后端接口可用：
  - `GET /admin/api/v1/llm-environments` 返回 200。
  - `GET /admin/api/v1/health` 返回 200，运行模式为真实接口模式。

## 验证结果

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

## 当前仍缺

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

## 下一步

- 如果继续不需要用户配合，建议下一步做：
  - 设计 LLM 场景路由表/配置结构。
  - 增加 LLM 调用日志。
  - 将回复建议的“思考 LLM”作为可选链路接入，但保留现有 Skill 兜底。
- 如果用户提供真实 key，可以先在配置中心新增 LLM 环境并测试连接。
