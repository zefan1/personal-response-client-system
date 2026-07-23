# 私域辅助系统服务器上线 Tasklist

> 创建日期：2026-07-20  
> 适用服务器：Ubuntu 24.04，2 vCPU，4 GiB RAM，公网带宽 1 Mbps   
> 目标：让后端 API、WebSocket、数据库、Redis、管理入口和桌面端生产 API 具备可回滚、可备份、可验收的上线条件。

## 当前判断

- [x] 已确认服务器 SSH 登录方式、登录用户和公网访问规则，并已执行远程部署。
- [x] 已启用正式 HTTPS 域名 `sy.xn--15tq51d.top`；桌面端生产 API 地址仍需在正式打包前切换到该域名。
- [ ] 这台 2C4G/1Mbps 服务器可以先承载 Spring Boot、MariaDB/MySQL、Redis、Nginx 和管理入口。
- [ ] 不建议长期用 1 Mbps 服务器分发 Windows 安装包；安装包优先迁移到对象存储/CDN，服务器保留版本元数据和下载鉴权。
- [ ] 当前代码基线仍有两个生产阻塞：真实 Skill/图片识别/企业微信 provider live 验收，以及 Windows 正式签名包。

### 2026-07-20 实测状态

- [x] DNS 查询确认 `sy.xn--15tq51d.top` 已通过 1.1.1.1 和 8.8.8.8 解析到 `39.108.221.95`。
- [x] 已在云控制台确认实例公网 IP 为 `39.108.221.95`，并把域名 A 记录切换到该 IP。
- [x] 已生成并绑定专用 ED25519 公钥，已通过 SSH 登录 `39.108.221.95`。
- [x] 已安装 Java 17、MariaDB 10.11、Redis 7、Nginx、Certbot、Fail2ban、UFW，并创建 2 GiB swap。
- [x] MariaDB 和 Redis 仅监听 `127.0.0.1`；公网只放行 22/80/443。
- [x] 已创建独立生产库 `private_domain_assistant_prod` 和本机最小范围账号 `pda_prod@127.0.0.1`。
- [x] 已生成服务器端生产数据库密码、JWT 密钥和 BCrypt 管理员密码；敏感值未写入本地仓库或聊天。
- [x] Flyway 41 个迁移全部成功，当前版本 V73，生产库共 42 张表。
- [x] Maven 完整测试 120 个套件、474 项测试、0 失败、0 错误、2 个条件跳过。
- [x] 已修复 Maven 标准打包只生成薄 JAR 的问题；可执行 JAR 约 39 MiB，包含 71 个运行时依赖。
- [x] systemd 后端已启用并运行；公网 `http://39.108.221.95/api/v1/auth/config` 返回 200。
- [x] 生产管理员登录和健康检查通过；数据库、Redis 和当前组件健康状态均为 `UP`。
- [x] 每日数据库备份定时器已启用；首次备份通过 gzip 校验和临时库恢复演练，恢复出 42 张表、Flyway V73。
- [x] 整机重启后 SSH、后端、MariaDB、Redis、Nginx、Fail2ban、备份定时器和 swap 自动恢复。
- [x] Let's Encrypt 证书已签发并部署到 Nginx，域名为 `sy.xn--15tq51d.top`，当前证书到期时间为 2026-10-18。
- [x] HTTP 已 301 跳转到 HTTPS，HTTPS API 返回 200，WSS 握手返回 101；8080、3306、6379 均未对公网开放。
- [x] `certbot.timer` 已启用并运行，`certbot renew --dry-run` 模拟续期成功。
- [ ] 真实 Skill/图片识别/企业微信/LLM provider live acceptance 和 Windows 签名包仍是正式发布门槛。

## P0：今天先完成服务器安全基线

### 1. 云平台控制台

- [ ] 记录实例 ID、地域、系统盘/数据盘容量、弹性公网 IP、续费方式和快照策略。
- [ ] 配置安全组：`22/tcp` 只允许办公出口 IP；`80/tcp`、`443/tcp` 允许公网；禁止公网访问 `3306/tcp`、`6379/tcp`、`8080/tcp`。
- [ ] 创建服务器快照；确认快照可以回滚到当前干净系统。
- [ ] 确认 provider 控制台可以把服务器公网出口 IP 加入白名单。

### 2. SSH 与系统加固

- [ ] 创建非 root 部署用户 `pda`，使用 SSH key 登录，并保留一个仅用于应急的管理员账号。
- [ ] 禁止 root 远程登录和 SSH 密码登录；确认新 key 登录成功后再关闭旧方式。
- [ ] 更新系统并设置时区、主机名和时间同步：

```bash
sudo apt update
sudo apt full-upgrade -y
sudo timedatectl set-timezone Asia/Shanghai
sudo timedatectl set-ntp true
```

- [ ] 启用 UFW：只放行 SSH、HTTP、HTTPS；数据库和 Redis 只监听 `127.0.0.1`。
- [ ] 安装并启用 Fail2ban 或同等 SSH 暴力破解防护。
- [ ] 配置自动安全更新，但不要让系统自动重启影响业务窗口。
- [ ] 检查磁盘、内存和 inode；4 GiB 服务器预留 1-2 GiB swap，避免 Java、数据库和构建任务同时触发 OOM。

完成标准：新用户可以登录；root/密码登录按预期被拒绝；公网端口扫描只看到 80/443（以及受限的 22）；服务器重启后网络和时间正常。

## P0：部署运行环境

### 3. 安装依赖

- [ ] Workbench 登录后可先运行 `dev-progress/deploy_templates/bootstrap_server_ubuntu2404.sh`；脚本只安装基础依赖、创建目录、配置 swap/UFW，不写入任何真实密钥。
- [ ] 安装 JDK 17，确认 `java -version` 为 17 或更高。
- [ ] 安装 MariaDB/MySQL，与项目的 MySQL JDBC 驱动和 Flyway 兼容。
- [ ] 安装 Redis，并限制为本机访问；当前代码没有生产 Redis 密码环境变量，不能只在 Redis 侧开启密码后期待应用自动可用。
- [ ] 安装 Nginx、Certbot/正式证书工具、`curl`、`rsync`、`unzip`、日志轮转工具。
- [ ] 不在生产服务器上安装 Node.js、Maven 并进行桌面端构建；桌面包在开发/CI 机器构建后上传。

### 4. 建立目录和权限

- [ ] 创建并设置属主为 `pda`：

```text
/opt/private-domain-assistant/app
/opt/private-domain-assistant/config
/data/private-domain-assistant/uploads
/data/private-domain-assistant/logs
/data/private-domain-assistant/backups
```

- [ ] 仅 `/opt/private-domain-assistant/app` 放 JAR；生产配置放 `/opt/private-domain-assistant/config/production.env`，不提交 Git、不写入日志。
- [ ] 上传目录、日志目录和备份目录使用独立数据盘或明确的容量上限，防止磁盘写满拖垮数据库。

### 5. 创建生产数据库

- [ ] 创建独立数据库 `private_domain_assistant_prod`，禁止复用 `private_domain_assistant_smoke`、`dev` 或本地验收库。
- [ ] 创建最小权限账号 `pda_prod`，只允许本机连接；不要用 root 连接应用。
- [ ] 配置 `utf8mb4`、`Asia/Shanghai` 和合理连接池上限。
- [ ] 首次启动前做空库备份；让 Flyway 自动执行全部迁移，并保存迁移结果。
- [ ] 先导入一份脱敏测试数据跑通，再决定是否导入真实业务数据。

### 6. 生产环境变量

- [ ] 从 `dev-progress/deploy_templates/production.env.example` 复制模板到服务器。
- [ ] 必须替换：`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`SYSTEM_JWT_SECRET`。
- [ ] 设置 `APP_ENV=production`、`MOCK_EXTERNALS=false`、`SERVER_PORT=8080`。
- [ ] 设置版本包目录：`VERSION_STORAGE_ROOT=/data/private-domain-assistant/uploads/desktop-releases`。
- [ ] `SYSTEM_JWT_SECRET` 使用至少 32 字节随机值；生产禁止使用 `change-me`、本地测试值或聊天记录中的密钥。
- [ ] 外部 provider key 通过管理后台配置中心写入数据库；任何日志、截图、备份和工单只显示 `<set>/<empty>`。

完成标准：应用配置文件权限为 `600`；生产库不是 smoke 库；`MOCK_EXTERNALS=false`；重启后配置仍然存在且未出现在公开仓库。

## P0：第一次部署后端

### 7. 构建、传输和启动

- [ ] 本地先完成后端测试、桌面端 typecheck/build、renderer smoke 和 `verify_production_blockers.py` 记录。
- [ ] 构建后端 JAR，生成 SHA-256；通过 SSH/SCP/rsync 上传到 `/opt/private-domain-assistant/app/`，保留上一个版本。
- [ ] 安装 `dev-progress/deploy_templates/private-domain-assistant.service` 为 systemd 服务。
- [ ] 使用非 root 用户运行 Java；JVM 初始值先按 4 GiB 服务器保守配置，观察一小时内存后再调大。
- [ ] 启用服务并设置开机自启：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now private-domain-assistant
sudo systemctl status private-domain-assistant --no-pager
```

- [ ] 检查 `journalctl -u private-domain-assistant`、应用日志、Flyway 迁移和端口 `127.0.0.1:8080`。

### 8. Nginx、域名和 HTTPS

- [x] DNS 已将正式域名 A 记录指向服务器公网 IP，解析生效后完成证书申请。
- [x] Nginx 已配置反向代理：`/api/`、`/admin/api/`、`/ws/`、`/downloads/`、`/uploads/quick-search/`。
- [x] WebSocket 已保留 `Upgrade`、`Connection: upgrade` 和 HTTP/1.1 代理头，并通过 WSS 101 握手验收。
- [x] HTTP 全部 301 到 HTTPS；证书续期定时器已启用，模拟续期和 Nginx 配置校验通过。
- [ ] 后台入口和桌面端生产 API 都使用同一个 HTTPS 域名，禁止正式客户端继续使用 `http://localhost:8080`。

完成标准：`curl -I https://<正式域名>/api/v1/auth/config` 返回预期状态；浏览器证书有效；WebSocket 可以建立连接；8080、3306、6379 不对公网暴露。

## P0：真实 provider 和业务验收

### 9. 配置真实外部接口

- [ ] 准备真实 LLM provider：base URL、API key、model；如需主备，准备第二个 provider 或模型。
- [ ] 准备图片识别 provider：base URL、API key、model，以及 5-10 张脱敏截图。
- [ ] 准备 Skill/场景 provider：base URL、API key 和至少一个测试场景。
- [ ] 准备企业微信表格 provider：base URL、API key、测试表格/空间和字段说明。
- [ ] 先在管理后台逐个做环境测试，再开启对应业务开关；不要一次性打开所有 LLM 场景。
- [ ] 确认 provider 白名单、超时、失败重试、主备切换和调用统计均能记录。

验收命令（在能访问生产 API 的验收机执行，真实 key 不写进命令历史）：

```bash
python scripts/verify_local_runtime_readiness.py --require-real-externals
python scripts/acceptance_real_external_live.py
```

### 10. 业务冒烟

- [ ] 管理员登录、刷新 token、退出登录。
- [ ] 客户搜索、客户档案读取和更新；确认数据权限隔离。
- [ ] 文本识别、图片识别、回复建议、重新生成、复制回填和确认发送。
- [ ] Skill 调用、LLM 失败回退、异常提醒、跟进建议和工作台刷新。
- [ ] 客户标签、跟进规则、表格字段映射、同步/写回和失败重试。
- [ ] 版本检查、安装包下载、审计日志、健康诊断和后台操作记录。
- [ ] 用两个普通员工账号验证权限边界，确认不能读取其他员工不应看到的客户。

完成标准：生产 API 接受脚本通过；真实 provider live acceptance 通过；关键链路没有 mock 响应；日志中无 key、token、客户敏感内容泄漏。

## P1：数据、备份和回滚

### 11. 备份

- [ ] 安装并审查 `dev-progress/deploy_templates/backup_database.sh`，每天至少一次全量备份。
- [ ] 备份数据库、`production.env`（加密或严格权限）、上传目录和当前 JAR 版本信息。
- [ ] 备份保留 7-30 天；至少一份复制到服务器之外的对象存储或另一台机器。
- [ ] 每次上线前手动备份；每次迁移前记录数据库版本、行数和关键表计数。
- [ ] 每月做一次恢复演练，确认能在新目录恢复数据库和配置，而不是只看到备份文件存在。

### 12. 发布与回滚

- [ ] 每次发布生成版本号、Git commit、JAR SHA-256、数据库迁移版本和发布时间记录。
- [ ] 发布顺序固定为：备份 -> 上传新 JAR -> 停服务/切换 -> Flyway -> 启动 -> 健康检查 -> 冒烟 -> 观察。
- [ ] 保留上一版 JAR 和配置；启动失败或冒烟失败时可在 10 分钟内回滚。
- [ ] 数据库迁移必须向后兼容；不能把需要旧客户端的字段直接删除。
- [ ] 发布窗口内记录操作者、命令、结果和回滚决定，写入运维记录。

## P1：桌面端正式发布

### 13. Windows 包

- [ ] 本地执行 `npm --prefix desktop run package:verify`，确认包结构和 `app.asar` SHA-256。
- [ ] 准备 Windows 代码签名证书或签名服务；执行 `python scripts/verify_release_signing_readiness.py --target win32`。
- [ ] 正式发布执行 `npm --prefix desktop run package:verify:signed`；禁止把 `NotSigned` 包作为正式分发包。
- [ ] 在干净 Windows 机器安装并验证：登录、生产 API 地址、WebSocket、更新检查、下载链接和卸载。
- [ ] 在后台版本管理上传已验证包，先以 `OPTIONAL` 小范围灰度，确认回滚和下载带宽。
- [ ] 1 Mbps 服务器不承担大规模安装包下载；上线前确认对象存储/CDN 或外部 `downloadUrl` 方案。

### 14. 客户端配置

- [ ] 桌面端默认生产 API 为 `https://<正式域名>`，不包含本地地址、WSL IP 或公网 IP。
- [ ] 确认 HTTPS 证书更新不会导致旧客户端无法连接。
- [ ] 记录当前正式客户端版本、API 兼容范围和最低支持版本。

## P1：上线后的监控与运营

- [ ] 建立健康检查：API、数据库、Redis、磁盘、内存、JVM、外部 provider 成功率和延迟。
- [ ] 配置日志轮转和保留周期，禁止无限增长 `app.log`、Nginx 日志和上传目录。
- [ ] 配置告警：服务停止、健康检查失败、磁盘 >80%、内存压力、备份失败、provider 连续失败、证书 14 天内到期。
- [ ] 每周检查审计日志、失败队列、表格写回失败重试和异常提醒积压。
- [ ] 每月做一次权限复核、密钥轮换演练和恢复演练。
- [ ] 明确应急联系人、回滚负责人和 provider 故障时的人工兜底流程。

## 最终放行条件

- [ ] 服务器安全组、UFW、SSH 加固完成。
- [x] HTTPS 域名可访问，WebSocket 正常，数据库和 Redis 未暴露公网。
- [x] 生产数据库独立，Flyway 迁移成功，备份和恢复演练通过。
- [x] 后端 systemd 重启和服务器重启后自动恢复。
- [ ] `MOCK_EXTERNALS=false`，真实 Skill/图片识别/企业微信/LLM 验收通过。
- [ ] `python scripts/verify_production_blockers.py` 输出 `productionReady=true`。
- [ ] Windows 正式包已签名，干净机器安装验收通过。
- [ ] 发布、回滚、告警和日常备份均有可执行记录。

## 需要你补齐的输入

- [ ] 服务器 SSH 登录方式：公网 IP/域名、端口、用户名、是否已配置 SSH key。
- [ ] 正式域名和 DNS 管理权限。
- [ ] 是否先在同一台服务器运行 MariaDB 和 Redis，还是使用云数据库/云 Redis。
- [ ] Skill、图片识别、LLM、企业微信表格的真实 endpoint、key、model 和白名单权限。
- [ ] 是否现在就准备 Windows 代码签名证书。
- [ ] 是否已有真实业务数据需要迁移，以及数据脱敏和停机窗口。
