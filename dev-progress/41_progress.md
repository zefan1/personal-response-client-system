# 41 运营B AI配置中心进度卡

## 基线
- 模块：运营B AI 配置中心
- 依赖：后端B Skill 网关、后端C 图片识别通道、后端H API 层
- 共享契约：`/admin/api/v1/`、`ConfigChangedEvent`、WS `CONFIG_REFRESH`、`UPDATE_CONFIG`、`system_configs`

## 功能签收清单
- [x] 扩展 `GET /admin/api/v1/configs/{key}` 查询单条配置。
- [x] 扩展 `PUT /admin/api/v1/configs/{key}`：配置范围/类型校验、`80-10007` 配置值错误、`UPDATE_CONFIG` 审计日志、保存后 `ConfigChangedEvent` + `CONFIG_REFRESH`。
- [x] Prompt 配置保存时自动写入 `skill_prompt_versions`：`skill.system_prompt_format`、`skill.system_prompt_red_lines`、`image.recognition_prompt`。
- [x] `GET/POST/PUT/DELETE /admin/api/v1/skill-environments`。
- [x] `PUT /admin/api/v1/skill-environments/{id}/activate`：激活环境后同步 `skill.api_base_url` 与 `skill.api_key`。
- [x] `GET/POST/PUT/DELETE /admin/api/v1/image-environments`。
- [x] `PUT /admin/api/v1/image-environments/{id}/activate`：激活环境后同步 `image.api_base_url` 与 `image.api_key`。
- [x] 环境列表只返回 `apiKeyLast4`，不返回完整 API Key。
- [x] 禁止删除激活环境，禁止删除最后一个环境。
- [x] `POST /admin/api/v1/image-environments/{id}/test` 使用预置测试图片走 `ImageRecognitionService`，返回成功/失败详情，并写 `last_test_at` / `last_test_ok`。
- [x] `GET /admin/api/v1/skill-prompt/{type}/versions` 返回最多 50 个版本。
- [x] `POST /admin/api/v1/skill-prompt/{type}/restore`：写回 `system_configs`，产生新版本，发布配置刷新。
- [x] 新增表：`skill_environments`、`image_environments`、`skill_prompt_versions`。
- [x] 新增配置默认值：`skill.system_prompt_format`、`skill.system_prompt_red_lines=[]`、`skill.regenerate_max_count=3`、`skill.prompt_version_max=50`、`image.*`、`match.tag_removal_rules=[]`。

## 验证命令
- `python scripts/verify_module_41.py`
- `python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py; python scripts/verify_module_g.py; python scripts/verify_module_h.py; python scripts/verify_module_20.py; python scripts/verify_module_21.py; python scripts/verify_module_22.py; python scripts/verify_module_23.py; python scripts/verify_module_24.py; python scripts/verify_module_25.py; python scripts/verify_module_26.py; python scripts/verify_module_27.py; python scripts/verify_module_28.py; python scripts/verify_module_29.py; python scripts/verify_module_30.py; python scripts/verify_module_31.py; python scripts/verify_module_32.py; python scripts/verify_module_33.py; python scripts/verify_module_40.py; python scripts/verify_module_41.py`
- `mvn test`
- `git diff --check`

## 假设与传播记录
- 当前仓库没有真实 AES-256-GCM 密钥管理，本模块以 `{plain}` 前缀保留可替换加密边界，接口仍只暴露 `apiKeyLast4`。
- 识图测试使用最小内置 PNG 触发现有 `ImageRecognitionService`；测试结果不写生产统计日志。
- Prompt 类型在接口中支持 `format`、`red-lines` 和 `image`，其中 `image` 对应 `image.recognition_prompt`，便于复用同一版本机制。
