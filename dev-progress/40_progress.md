# 40 运营A Skill 场景管理进度卡

## 基线
- 模块：运营A Skill 场景管理
- 依赖：后端B Skill 网关、后端H API/WS 通道、`skill_scene_bindings`、`skill_call_logs`
- 共享契约：`/admin/api/v1/`、`CONFIG_REFRESH`、`ConfigChangedEvent`、scene 枚举、leadType 枚举

## 功能签收清单
- [x] `GET /admin/api/v1/skills`：按 `scene` / `leadType` 可选筛选，返回全部 Skill 绑定，按 scene + leadType + priority 排序。
- [x] `POST /admin/api/v1/skills`：新增绑定，校验 skillId、skillName、scene、leadType、同组合 skillId 不重复。
- [x] `PUT /admin/api/v1/skills/{id}`：编辑绑定，校验规则同新增。
- [x] `DELETE /admin/api/v1/skills/{id}`：物理删除绑定。
- [x] `PUT /admin/api/v1/skills/{id}/toggle`：启用/停用绑定，停用最后一个启用绑定时返回 warning。
- [x] `GET /admin/api/v1/skills/available`：提供可选 Skill 列表；当前从已绑定 Skill 去重返回，外部 Skill 列表接口接入后可替换数据源。
- [x] `POST /admin/api/v1/skills/{id}/test`：已保存绑定在线测试，复用 Skill payload 构造与响应解析。
- [x] 测试调用不经过 `SkillGatewayService`，不写入 `skill_call_logs`，不计入生产统计。
- [x] `GET /admin/api/v1/analytics/skill-calls`：按最近 1-90 天、scene、leadType 聚合调用量、成功率、平均耗时、采纳率字段。
- [x] 绑定增删改/开关后发布 `ConfigChangedEvent("skill_scene_bindings")` 并通过 WS 广播 `CONFIG_REFRESH`。
- [x] 增加 `last_tested_at` 字段，测试成功后更新。
- [x] 增加运营A配置默认值：`skill.admin.monitor_refresh_interval_s=30`、`skill.admin.monitor_default_days=7`、`skill.admin.test_timeout_ms=10000`、`skill.admin.test_message_max_chars=2000`。
- [x] Admin API 使用既有 JWT 过滤器保护，`KEEPER` 角色禁止访问 `/admin/api/v1/**`。

## 验证命令
- `python scripts/verify_module_40.py`
- `python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py; python scripts/verify_module_g.py; python scripts/verify_module_h.py; python scripts/verify_module_20.py; python scripts/verify_module_21.py; python scripts/verify_module_22.py; python scripts/verify_module_23.py; python scripts/verify_module_24.py; python scripts/verify_module_25.py; python scripts/verify_module_26.py; python scripts/verify_module_27.py; python scripts/verify_module_28.py; python scripts/verify_module_29.py; python scripts/verify_module_30.py; python scripts/verify_module_31.py; python scripts/verify_module_32.py; python scripts/verify_module_33.py; python scripts/verify_module_40.py`
- `mvn test`
- `git diff --check`

## 假设与传播记录
- 当前仓库没有独立运营后台前端工程；本次落地运营A所需后端 Admin API，使后续运营前端可直接调用。
- 外部 Skill 系统列表接口当前未在后端B暴露独立 client 方法，`skills/available` 先从已配置绑定去重返回，并保留手动输入 Skill ID 能力。
- 手册要求采纳率 v1 允许近似；当前审计日志和 skill_call_logs 尚无逐调用关联字段，本模块返回 `adoptionRate` 字段但先以 `0.0` 占位，不伪造统计。
