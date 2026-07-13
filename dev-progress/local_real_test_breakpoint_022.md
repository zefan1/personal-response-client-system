# 断点 022：本地后端/数据库预检与真实接口缺口定位

时间：2026-07-09 18:00-18:10  
状态：已完成

## 本轮目标

- 继续本地真实测试准备，不需要用户额外配合。
- 针对“数据库好像没有连接上”的反馈，核对当前后端、数据库、Redis、工具链和真实外部配置。
- 把本地测试前的检查固化成可重复脚本，避免后续靠猜。

## 核对结论

- 当前本地后端可访问：
  - `http://localhost:8080/api/v1/auth/config` 返回成功。
  - `admin/admin123` 登录成功。
- 当前数据库是连上的：
  - 后端健康检查里 `db=UP`、`redis=UP`。
  - WSL MariaDB 中 `private_domain_assistant_smoke` 可查询。
  - Flyway 迁移数：26。
  - 当前表数量：31。
- 当前运行模式是 `真实接口模式`：
  - `MOCK_EXTERNALS=false`。
  - Skill 和图像识别配置当前已有值。
  - 企微表格真实接口仍缺：`table.api_base_url`、`table.api_key`。
- Windows 侧工具链：
  - 有 JDK、Node、npm、WSL、Docker。
  - 没有 Windows PATH 下的 `mvn` 和 `mysql`。
  - 这不阻塞本地后端运行，因为 WSL 内已有 Maven、MySQL/MariaDB、Redis 和 Java。

## 处理结果

- 新增本地运行预检脚本：
  - `scripts/verify_local_runtime_readiness.py`
  - 默认检查本地核心链路，不要求真实外部接口全部配置。
  - 可用 `--require-real-externals` 切换为真实接口强校验。
- 预检脚本覆盖：
  - Windows 基础工具：Java、javac、Node、npm、WSL。
  - WSL 后端工具链：Java、Maven、MySQL、Redis。
  - 后端 auth config。
  - 管理员登录。
  - 后端健康检查中的 DB/Redis。
  - WSL 数据库 schema、Flyway、表数量。
  - Skill、图像识别、企微表格外部配置是否为空。
- 报告已脱敏：
  - accessToken、refreshToken、apiKey、password 不会明文写入 readiness 报告。
- 统一本地验收默认后端地址：
  - `scripts/verify_manual_test_readiness.py` 默认后端从旧 WSL IP 改为 `http://localhost:8080`。
  - `scripts/acceptance_p0_p1.py` 默认后端从旧 WSL IP 改为 `http://localhost:8080`。
- WSL 后端启动脚本增加防重复启动：
  - `scripts/start_backend_mock_wsl.sh`
  - `scripts/start_backend_real_wsl.sh`
  - 当 PID 文件失效但 8080 已经有后端响应时，不再启动第二个后端，直接提示 `backend_already_running`。

## 验证结果

- 本地运行预检通过：
  - `python scripts\verify_local_runtime_readiness.py`
  - `passed=true checks=12/15 backend=http://localhost:8080 database=private_domain_assistant_smoke`
  - warning：Windows PATH 无 `mvn/mysql`。
  - warning：真实企微表格配置缺 `table.api_base_url`、`table.api_key`。
- 真实外部强校验按预期失败：
  - `python scripts\verify_local_runtime_readiness.py --require-real-externals`
  - 失败项：`table.api_base_url`、`table.api_key`。
  - 说明后续“每个接口都接上”的真实表格联调还需要配置这两项。
- 手工测试就绪检查通过：
  - `python scripts\verify_manual_test_readiness.py --frontend-url http://127.0.0.1:5173/ --backend-url http://localhost:8080`
  - `passed=true checks=3/3`
- WSL 启动脚本语法检查通过：
  - `bash -n scripts/start_backend_mock_wsl.sh`
  - `bash -n scripts/start_backend_real_wsl.sh`
  - `bash -n scripts/smoke_backend_wsl.sh`
  - `bash -n scripts/stop_backend_mock_wsl.sh`
- 后端启动脚本防重复分支通过：
  - 当前已有后端运行时，`start_backend_mock_wsl.sh` 返回 `backend_already_running`。
- Python 编译检查通过：
  - `python -m py_compile scripts\verify_local_runtime_readiness.py scripts\verify_manual_test_readiness.py scripts\acceptance_p0_p1.py`
- 桌面端验证通过：
  - `npm run typecheck`
  - `npm run renderer:smoke`，输出 `renderer_smoke=passed`。

## 当前可手工验证

- 如果侧边栏提示数据库/后端不可用，先运行：
  - `python scripts\verify_local_runtime_readiness.py`
- 如果只想确认前端和后端能登录：
  - `python scripts\verify_manual_test_readiness.py --frontend-url http://127.0.0.1:5173/ --backend-url http://localhost:8080`
- 如果要进入真实外部接口全接入检查：
  - `python scripts\verify_local_runtime_readiness.py --require-real-externals`
  - 当前会明确卡在企微表格配置：`table.api_base_url`、`table.api_key`。

## 下一步

- 本地核心链路已经可测，数据库不是当前阻塞项。
- 下一步进入真实外部配置收口：
  - 企微表格接口：补 `table.api_base_url`、`table.api_key`。
  - 继续确认 Skill/图像识别配置是否是可实际调用的真实 provider。
  - LLM 多供应商仍在 tasklist，尚未实现，需要作为下一阶段编码项。
