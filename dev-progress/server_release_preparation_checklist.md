# 服务器部署与桌面端发布准备清单

创建日期：2026-07-09  
适用阶段：本地测试完成后，上服务器前准备。当前仍优先本地测试，暂不执行部署。

## 0. 当前结论

- 侧边栏应用端是 Electron 桌面应用，不是把侧边栏页面直接部署到服务器给员工打开。
- 服务器主要承载：后端 API、WebSocket、数据库、Redis、上传/下载文件、版本检查、后台管理入口。
- 桌面端需要打包成 Windows 目录包或安装包，并配置生产 API 地址。
- 正式发布建议做 Windows 代码签名；未签名包不阻塞本地测试，但会影响正式分发体验。
- 当前生产阻塞仍是：
  - 真实外部 provider live 验收未完成。
  - 正式发布包签名未完成。

## 1. 推荐服务器组成

| 组件 | 是否必需 | 作用 | 建议 |
| --- | --- | --- | --- |
| Linux 服务器 | 必需 | 承载后端、Nginx、日志、任务 | 推荐云服务器或公司内网 Linux 服务器 |
| JDK 17+ | 必需 | 运行 Spring Boot | 与 Maven/Spring Boot 3.x 兼容 |
| MySQL/MariaDB | 必需 | 业务数据、配置、审计、版本 | 生产库与本地测试库严格分开 |
| Redis | 必需 | token、配置刷新、WebSocket 推送辅助 | 配置密码和内网访问 |
| Nginx | 必需 | HTTPS、反向代理、静态下载 | 代理 API/WebSocket/downloads |
| HTTPS 证书 | 必需 | 桌面端生产 API 和后台访问 | 使用正式域名证书 |
| 文件目录 | 必需 | 速搜图片、桌面安装包、日志、备份 | 放在固定数据盘并做备份 |
| 备份任务 | 必需 | 数据库和配置回滚 | 至少每日备份，保留 7-30 天 |
| 监控告警 | 建议 | 后端、DB、Redis、外部接口失败率 | 先用基础日志和健康页，后续接告警 |

## 2. 推荐目录规划

| 目录 | 用途 |
| --- | --- |
| `/opt/private-domain-assistant/app` | 后端 jar 和启动脚本 |
| `/opt/private-domain-assistant/config` | 生产环境变量文件，不提交代码 |
| `/data/private-domain-assistant/uploads` | 速搜图片、桌面安装包等上传文件 |
| `/data/private-domain-assistant/logs` | 后端运行日志 |
| `/data/private-domain-assistant/backups` | 数据库和配置备份 |
| `/etc/nginx/conf.d/private-domain-assistant.conf` | Nginx 站点配置 |

## 3. 生产环境变量

| 变量 | 必需 | 说明 |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | 是 | 生产 MySQL/MariaDB JDBC 地址 |
| `SPRING_DATASOURCE_USERNAME` | 是 | 生产数据库账号 |
| `SPRING_DATASOURCE_PASSWORD` | 是 | 生产数据库密码 |
| `REDIS_HOST` | 是 | Redis 地址 |
| `REDIS_PORT` | 是 | Redis 端口 |
| `SYSTEM_JWT_SECRET` | 是 | 必须替换默认值，生产安全检查会拦截默认密钥 |
| `MOCK_EXTERNALS` | 是 | 生产应为 `false` |
| `APP_ENV` 或 `ENVIRONMENT` | 建议 | 建议填 `production` |
| `SERVER_PORT` | 建议 | 后端内部端口，例如 `8080` |
| `VERSION_STORAGE_ROOT` | 建议 | 桌面安装包存储目录，例如 `/data/private-domain-assistant/uploads/desktop-releases` |
| `VERSION_PUBLIC_BASE_URL` | 建议 | 下载公开路径，例如 `/downloads/desktop-releases` |

外部接口 key 推荐通过管理后台配置中心写入数据库，不直接写入前端：

| 配置键 | 说明 |
| --- | --- |
| `skill.api_base_url` / `skill.api_key` | Skill/场景接口 |
| `image.api_base_url` / `image.api_key` / `image.model` | 图像识别接口 |
| `llm.api_base_url` / `llm.api_key` / `llm.model` | 默认 LLM provider |
| `table.api_base_url` / `table.api_key` | 企微表格接口 |

## 4. Nginx 反向代理要点

生产域名示例：

- API 和后台：`https://pda.example.com`
- 桌面端 API 地址：`https://pda.example.com`

需要代理：

| 路径 | 目标 |
| --- | --- |
| `/api/` | Spring Boot 后端 |
| `/admin/api/` | Spring Boot 后端 |
| `/ws/` | Spring Boot WebSocket，需支持 Upgrade |
| `/downloads/desktop-releases/` | 桌面安装包下载目录 |
| `/uploads/quick-search/` | 速搜图片目录 |

WebSocket 代理必须包含：

```nginx
proxy_http_version 1.1;
proxy_set_header Upgrade $http_upgrade;
proxy_set_header Connection "upgrade";
```

## 5. 数据库与备份

上线前：

- 创建独立生产库，禁止复用 `private_domain_assistant_smoke`。
- 创建最小权限数据库账号。
- 首次启动由 Flyway 自动迁移。
- 初始化管理员账号后立即修改默认密码。
- 真实业务数据导入前，先在测试表/测试空间跑通。

备份建议：

- 每日全量备份数据库。
- 每次上线前手动备份数据库。
- 备份 `system_configs` 中外部接口配置，但注意 key 属于敏感信息。
- 备份上传目录：速搜图片、桌面安装包。
- 定期做恢复演练，不只保存备份文件。

## 6. 桌面端发布方式

本地已有脚本：

```powershell
npm --prefix desktop run package:verify
```

签名发布脚本：

```powershell
npm --prefix desktop run package:verify:signed
```

Windows 签名支持的环境变量：

| 变量 | 说明 |
| --- | --- |
| `WINDOWS_CERTIFICATE_FILE` | PFX 证书路径 |
| `WINDOWS_CERTIFICATE_PASSWORD` | PFX 密码 |
| `WINDOWS_SIGN_WITH_PARAMS` | 自定义 signtool 参数 |
| `WINDOWS_SIGN_HOOK_MODULE_PATH` | 自定义签名 hook |
| `WINDOWS_TIMESTAMP_SERVER` | 时间戳服务器 |
| `WINDOWS_SIGN_DESCRIPTION` | 签名描述 |
| `WINDOWS_SIGN_WEBSITE` | 签名网站 |

签名 readiness 检查：

```powershell
python scripts\verify_release_signing_readiness.py --target win32
```

生产阻塞检查：

```powershell
python scripts\verify_production_blockers.py
```

## 7. 桌面端生产 API 地址

当前桌面端默认 API 地址为：

```text
http://localhost:8080
```

本地测试可以继续使用该地址。正式发给员工前，需要确保桌面端登录页或配置中使用：

```text
https://pda.example.com
```

WebSocket 会由 API 地址自动推导：

- `https://pda.example.com` -> `wss://pda.example.com/ws/v1/desktop`
- `http://localhost:8080` -> `ws://localhost:8080/ws/v1/desktop`

## 8. 版本管理与安装包下载

后端已支持：

- 后台版本管理。
- 安装包上传。
- 下载地址记录。
- 发布、撤回、灰度比例。
- 桌面端版本检查接口。

需要准备：

- `version.storage.root`：安装包存储目录。
- `version.storage.public_base_url`：公开下载路径。
- Nginx 下载路径映射。
- 至少一个已验证的 Windows 包。
- 正式发布前建议签名。

## 9. 上线前验收命令

本地或预生产基础检查：

```powershell
python scripts\verify_local_runtime_readiness.py
python scripts\verify_real_external_readiness.py
npm --prefix desktop run renderer:smoke
```

真实 provider 配置后：

```powershell
python scripts\verify_local_runtime_readiness.py --require-real-externals
python scripts\acceptance_real_external_live.py
```

桌面端包：

```powershell
npm --prefix desktop run package:verify
python scripts\verify_release_signing_readiness.py --target win32
python scripts\verify_production_blockers.py
```

## 10. 需要用户决策或提供

| 事项 | 推荐决策 | 需要用户提供 |
| --- | --- | --- |
| 服务器类型 | 云服务器优先，便于公网 HTTPS 和外部 provider 白名单 | 服务器系统、登录方式、规格 |
| 域名 | 使用一个独立二级域名 | 域名和 DNS 管理权限 |
| HTTPS 证书 | 正式证书，不使用自签 | 证书申请或 DNS 验证 |
| 外部 provider 白名单 | 使用服务器出口 IP | provider 控制台权限 |
| Windows 签名 | 正式发布建议购买/准备代码签名证书 | PFX 或签名服务 |
| 真实测试数据 | 使用脱敏样本和测试表 | 截图、聊天文本、测试表 |

## 11. Codex 可以继续完成

- 输出 Nginx 示例配置。
- 输出 Linux systemd 启动服务模板。
- 输出生产 `.env` 模板。
- 输出数据库备份/恢复脚本模板。
- 输出桌面端发布操作步骤。
- 在拿到真实 provider 后写入本地配置并跑 live acceptance。
