# 真实 Provider 配置填写表

创建日期：2026-07-09  
用途：准备真实外部接口联调。请不要把真实 key 写入代码仓库；本表只记录需要提供的字段和验收状态。

## 0. 当前状态

| 模块 | 本地 mock/基础链路 | 真实 provider | 当前状态 |
| --- | --- | --- | --- |
| 侧边栏批次 A | 通过 | 部分依赖 | A1-A6 自动化 14/14 |
| 管理后台批次 B | 通过 | 部分依赖 | B1-B5 自动化 39/39 |
| LLM 主备 | 通过 | 待提供 | fake provider 主备验收已通过 |
| 图像识别 | 配置入口可用 | 待提供 | 需截图样本 |
| Skill/场景 | 兜底链路可用 | 待提供 | 需真实 Skill 地址 |
| 企微表格 | 本地读写可用 | 待提供 | 需测试表 |

## 1. LLM Provider

### Provider 1：主模型

| 字段 | 填写值 | 备注 |
| --- | --- | --- |
| 环境名称 |  | 例如 `LLM 主模型` |
| Base URL |  | 例如 `https://api.example.com`，不要写到前端 |
| API Key | 不在文档记录 | 通过安全方式提供或由你在后台填写 |
| Model |  | 例如 `gpt-4.1-mini`、`qwen-plus` |
| Protocol | `OPENAI_COMPATIBLE` | 当前已支持 |
| Timeout | `10000` | 毫秒 |
| Temperature | `0.2` | 可按场景调整 |
| Max Tokens | `1024` | 可按场景调整 |
| 是否设为激活 | 是 | 主模型建议激活 |

### Provider 2：备用模型，可选但推荐

| 字段 | 填写值 | 备注 |
| --- | --- | --- |
| 环境名称 |  | 例如 `LLM 备用模型` |
| Base URL |  |  |
| API Key | 不在文档记录 |  |
| Model |  |  |
| Protocol | `OPENAI_COMPATIBLE` |  |
| Timeout | `15000` | 毫秒 |
| Temperature | `0.2` |  |
| Max Tokens | `1024` |  |
| 是否设为备用路由 | 是 | 用于主备切换和 A/B |

LLM 场景路由建议：

| 场景 | 主模型 | 备用模型 | 是否先启用 |
| --- | --- | --- | --- |
| `REPLY_GENERATION` | Provider 1 | Provider 2 | 先启用 |
| `PROFILE_EXTRACTION` | Provider 1 | Provider 2 | 回复稳定后启用 |
| `FOLLOWUP_SUGGESTION` | Provider 1 | Provider 2 | 回复稳定后启用 |
| `ABNORMAL_DETECTION` | Provider 1 | Provider 2 | 评估误报后启用 |
| `SUMMARY` | Provider 1 | Provider 2 | 可较早启用 |

## 2. 图像识别 Provider

| 字段 | 填写值 | 备注 |
| --- | --- | --- |
| 环境名称 |  | 例如 `图像识别生产` |
| Base URL |  |  |
| API Key | 不在文档记录 | 通过后台或安全方式提供 |
| Model |  | 例如视觉模型名 |
| Timeout | `5000` | 毫秒 |
| 最大图片大小 |  | 默认按系统配置 |
| 测试截图 |  | 5-10 张脱敏聊天截图 |

验收重点：

- 能识别昵称、手机号、聊天文本、时间顺序。
- 识别失败不泄露 key。
- 图片过大或格式不支持时提示清楚。

## 3. Skill/场景 Provider

| 字段 | 填写值 | 备注 |
| --- | --- | --- |
| 环境名称 |  | 例如 `Skill 生产` |
| Base URL |  |  |
| API Key | 不在文档记录 |  |
| 测试场景 |  | 回复建议、重新生成、阶段建议 |
| 超时时间 | `10000` | 毫秒 |

验收重点：

- LLM 关闭时 Skill 仍可生成回复或给出明确错误。
- Skill 不可用时能回到系统兜底。
- 调用日志、错误码、耗时可查。

## 4. 企微表格 Provider

| 字段 | 填写值 | 备注 |
| --- | --- | --- |
| Base URL |  |  |
| API Key | 不在文档记录 |  |
| 测试表/测试空间 |  | 必须使用测试表，避免污染正式表 |
| 字段说明 |  | 手机号、昵称、来源、阶段等列名 |
| 写入权限 |  | 确认可以新增和更新 |

验收重点：

- 新客保存到测试表成功。
- 老客更新字段成功。
- 写入失败进入重试或保留失败状态。
- 字段映射错误时后台提示清楚。

## 5. 脱敏样本

| 样本类型 | 数量 | 当前状态 |
| --- | --- | --- |
| 聊天截图 | 5-10 张 | 待提供 |
| 聊天文本 | 10 条 | 待提供 |
| 客户样例 | 10 条 | 待提供 |
| 禁用话术 | 1 份 | 待提供 |
| 期望回复风格 | 1 份 | 待提供 |

## 6. 配置后验收命令

```powershell
python scripts\verify_local_runtime_readiness.py --require-real-externals
python scripts\acceptance_real_external_live.py
python scripts\acceptance_sidebar_batch_a.py
python scripts\acceptance_admin_batch_b.py
```

LLM 主备受控验收：

```powershell
python scripts\acceptance_llm_failover_local.py
```

生产阻塞检查：

```powershell
python scripts\verify_production_blockers.py
```

## 7. 安全备注

- 真实 API key 不要写入此 Markdown。
- 后台配置接口会对 `*.api_key` 做敏感处理和展示脱敏。
- 日志、截图和报告里只记录 `<set>`、尾号或脱敏状态。
- 测试表必须和正式业务表隔离。
