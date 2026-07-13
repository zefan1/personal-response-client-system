# 本地真实测试断点 025：LLM 场景路由与调用日志基础设施

时间：2026-07-09 19:40  
状态：已完成

## 本轮目标

- 在已有 LLM 多环境配置和统一 runtime 的基础上，补齐“按业务场景选择 LLM”的后端基础结构。
- 先不强行接入真实业务链路，避免回复建议、客户档案、跟进等功能同时变化。
- 为后续真实测试保留可观测性：知道哪个场景用了哪个 LLM、耗时多少、成功还是失败。

## 已完成

- 新增数据库迁移：`V60__llm_routes_and_call_logs.sql`。
- 新增数据表：
  - `llm_scene_routes`：维护 `scene + leadType -> llm_environment_id` 的路由。
  - `llm_call_logs`：记录 LLM 调用场景、模型、耗时、成功/失败、错误码和摘要。
- 新增运行配置：
  - `llm.routing.enabled=true`
  - `llm.admin.monitor_default_days=7`
- 新增 LLM 场景枚举：
  - `REPLY_GENERATION`
  - `PROFILE_EXTRACTION`
  - `FOLLOWUP_SUGGESTION`
  - `ABNORMAL_DETECTION`
  - `SUMMARY`
- 新增管理接口：
  - `GET /admin/api/v1/llm-routes/scenes`
  - `GET /admin/api/v1/llm-routes`
  - `POST /admin/api/v1/llm-routes`
  - `PUT /admin/api/v1/llm-routes/{id}`
  - `PUT /admin/api/v1/llm-routes/{id}/toggle`
  - `DELETE /admin/api/v1/llm-routes/{id}`
  - `GET /admin/api/v1/analytics/llm-calls`
- 新增后端能力：
  - `LlmRoutingService`：按场景和 leadType 解析 LLM 配置。
  - `LlmRouteRepository`：维护路由配置。
  - `LlmCallLogger`：异步写入调用日志。
  - `LlmCallAnalyticsRepository`：查询 LLM 调用统计。
  - `LlmService.generate(scene, leadType, caller, requestSummary, request)`：业务侧后续可直接按场景调用。
- 路由兜底策略：
  - 优先使用 `scene + leadType` 精确路由。
  - 没有精确路由时使用该 `scene` 的空 leadType 默认路由。
  - 仍未命中时回退到当前启用的全局 LLM 环境。
  - 没有启用环境时回退到 `llm.*` runtime 配置。

## 本地运行状态

- 后端已重启到最新代码：
  - `http://localhost:8080`
  - `MOCK_EXTERNALS=false`
  - 数据库：`private_domain_assistant_smoke`
- Flyway 已执行到：
  - `60 / V60__llm_routes_and_call_logs.sql`
- 运行态接口确认：
  - `GET /admin/api/v1/llm-routes/scenes` 返回 5 个场景。
  - `GET /admin/api/v1/llm-routes` 返回空列表，符合当前尚未配置路由的状态。
  - `GET /admin/api/v1/analytics/llm-calls` 返回 `totalCalls=0`，符合尚未接入业务调用的状态。

## 验证结果

- 编译：`mvn -q test-compile` 通过。
- 新增模块定向测试通过：
  - `LlmRoutingServiceTest`
  - `LlmCallAnalyticsRepositoryTest`
  - `LlmServiceTest`
  - `LlmAdminControllerTest`
- 受影响旧功能回归通过：
  - `LlmConfigProviderTest`
  - `HttpLlmClientTest`
  - `AiEnvironmentRepositoryTest`
  - `AiEnvironmentServiceTest`
  - `AiConfigControllerTest`
  - `ConfigAdminServiceTest`

## 当前仍缺

- 管理后台 UI 还没有单独展示 LLM 场景路由和调用统计。
- 具体业务链路还没有改为使用 LLM 场景路由：
  - 回复建议
  - 客户档案提取
  - 跟进建议
  - 异常识别
  - 总结
- 真实 LLM provider 仍未配置：
  - `llm.api_base_url`
  - `llm.api_key`
  - `llm.model`
- 企业微信表格真实接口仍未配置：
  - `table.api_base_url`
  - `table.api_key`

## 下一步

- 优先把“回复建议”接入 LLM 可选链路：
  - 默认仍保留现有 Skill。
  - 当 LLM 路由可用时，可走 `REPLY_GENERATION`。
  - LLM 失败时记录日志，并回退到 Skill 或模板兜底。
- 然后补管理后台 LLM 路由/统计 UI，便于本地测试时配置多个模型做对比。
