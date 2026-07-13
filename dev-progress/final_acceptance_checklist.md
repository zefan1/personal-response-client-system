# 上线前最终验收表

创建日期：2026-07-09  
用途：本地测试、真实 provider 联调和服务器发布前的最终确认。

## 1. 本地功能

| 项目 | 状态 | 证据 |
| --- | --- | --- |
| 侧边栏批次 A | 通过 | `.tools/acceptance/sidebar_batch_a.json` |
| 管理后台批次 B | 通过 | `.tools/acceptance/admin_batch_b.json` |
| 本地 runtime readiness | 通过 | `.tools/acceptance/local_runtime_readiness.json` |
| Renderer smoke | 通过 | `renderer_smoke=passed` |
| 管理后台产品面 | 通过 | `.tools/contracts/admin_product_surface.json` |

## 2. 真实外部接口

| 项目 | 状态 | 通过标准 |
| --- | --- | --- |
| LLM provider | 待提供 | `acceptance_real_external_live.py` 通过，回复来源可显示 `LLM` |
| LLM 主备 | 本地受控通过，live 待测 | 主失败后备成功，统计记录两次调用 |
| 图像识别 | 待提供 | 脱敏截图识别可用 |
| Skill/场景 | 待提供 | 回复建议、重新生成、阶段建议可用 |
| 企微表格 | 待提供 | 测试表新增和更新成功 |

## 3. 服务器发布

| 项目 | 状态 | 通过标准 |
| --- | --- | --- |
| 服务器 | 未决策 | 确认系统、访问方式、规格 |
| 域名/HTTPS | 未准备 | 生产 API 使用 HTTPS |
| 数据库/Redis | 未部署 | 生产库独立，Redis 内网可用 |
| Nginx/WebSocket | 模板已准备 | `/ws/` 可连接 |
| 文件目录 | 模板已准备 | 上传、下载、日志、备份目录存在 |
| 备份恢复 | 模板已准备 | 至少做一次恢复演练 |
| Windows 签名 | 未准备 | `verify_release_signing_readiness.py --target win32` 通过 |
| 生产阻塞检查 | 当前不通过 | `verify_production_blockers.py` 无 blocker |

## 4. 用户最终确认

| 项目 | 状态 | 备注 |
| --- | --- | --- |
| 回复话术风格 | 待确认 | 专业、亲切、转化强度、禁用话术 |
| 客户档案字段 | 待确认 | 一线是否够用 |
| 跟进提醒逻辑 | 待确认 | 逾期、今日、预约是否符合实际 |
| 后台权限边界 | 待确认 | 管理员、组长、管家 |
| 桌面端安装体验 | 待确认 | 未签名提示、杀毒误报、快捷方式 |

## 5. 最终放行条件

- 本地 A/B 批次通过。
- 真实外部接口 live acceptance 通过。
- 生产服务器 HTTPS、DB、Redis、Nginx、WebSocket 通过。
- 桌面端安装包可下载、可安装、可登录。
- 生产阻塞检查为 `productionReady=true`。
