# 真实 Provider 接入与验收运行手册

更新日期：2026-07-09  
适用阶段：本地真实测试，暂不上服务器

## 当前基线

- 本地后端：`http://localhost:8080`
- 本地测试库：`private_domain_assistant_smoke`
- 登录账号：`admin/admin123`
- 普通本地 readiness 已通过：
  - `python scripts\verify_local_runtime_readiness.py`
- 当前强制真实外部检查仍缺：
  - `llm.api_base_url`
  - `llm.api_key`
  - `llm.model`
  - `table.api_base_url`
  - `table.api_key`

## 接入顺序

### 1. 先确认本地状态

运行：

```powershell
python scripts\verify_local_runtime_readiness.py
python scripts\verify_real_external_readiness.py
```

通过标准：

- `local_runtime_readiness`：`passed=true`
- `real_external_readiness`：`mockExternalsFalseReady=true blockers=0`

如果失败，先修本地后端、数据库迁移、Redis 或代码结构问题，不要先填真实 key。

### 2. 配置 LLM provider

需要用户提供：

- `base URL`
- `API key`
- `model`
- 如果要验证主备切换，建议至少 2 套 provider 或 2 个模型。

推荐在管理后台操作：

- 打开 `配置中心`
- 新增或编辑 `LLM 环境`
- 点击环境测试
- 激活主模型
- 在 `LLM 场景路由` 中给以下场景配置路由：
  - `REPLY_GENERATION`
  - `PROFILE_EXTRACTION`
  - `FOLLOWUP_SUGGESTION`
  - `ABNORMAL_DETECTION`
  - `SUMMARY`

验收顺序：

1. 只打开 `LLM 回复生成`
2. 验证回复建议质量和来源标签
3. 再逐个打开档案提取、跟进建议、异常识别、总结补位
4. 查看 `LLM 调用统计` 是否记录调用、成功率、耗时和失败原因
5. 如果配置了主备模型，临时禁用或填错主模型，确认备用模型被调用

### 3. 配置图像识别 provider

需要用户提供：

- `base URL`
- `API key`
- `model`
- 5-10 张真实或脱敏聊天截图

验收顺序：

1. 管理后台 `图像识别环境` 新增环境
2. 点击环境测试
3. 在侧边栏用截图/剪贴板图片触发识别
4. 验证识别结果中的客户消息、销售消息、时间顺序是否可用
5. 失败时查看系统健康监控和后端日志

### 4. 配置 Skill/场景接口

需要用户提供：

- `base URL`
- `API key`
- 测试场景说明

验收顺序：

1. 管理后台 `Skill 场景管理` 绑定场景
2. 运行测试调用
3. 侧边栏生成回复建议
4. 验证重新生成、选择建议、复制回填、确认发送
5. 验证 LLM 关闭时 Skill 仍能兜底

### 5. 配置企微表格接口

需要用户提供：

- `base URL`
- `API key`
- 测试表格或测试空间
- 测试表字段说明

验收顺序：

1. 管理后台 `客户数据对接` 新建测试数据源
2. 拉取字段/样例行
3. 配置字段映射
4. 执行同步或保存到表格
5. 验证新增客户、更新客户、失败重试、队列状态
6. 确认不会污染真实业务表

## 推荐验收命令

配置真实 key 前：

```powershell
python scripts\verify_local_runtime_readiness.py
python scripts\verify_real_external_readiness.py
npm --prefix desktop run renderer:smoke
```

配置真实 key 后：

```powershell
python scripts\verify_local_runtime_readiness.py --require-real-externals
python scripts\acceptance_real_external_live.py
```

运行 `acceptance_real_external_live.py` 前需要设置：

```powershell
$env:PDA_LIVE_SKILL_BASE_URL="..."
$env:PDA_LIVE_SKILL_API_KEY="..."
$env:PDA_LIVE_IMAGE_BASE_URL="..."
$env:PDA_LIVE_IMAGE_API_KEY="..."
$env:PDA_LIVE_LLM_BASE_URL="..."
$env:PDA_LIVE_LLM_API_KEY="..."
$env:PDA_LIVE_LLM_MODEL="..."
$env:PDA_LIVE_TABLE_BASE_URL="..."
$env:PDA_LIVE_TABLE_API_KEY="..."
```

当前覆盖：

- Skill provider 测试。
- 图像识别 provider 测试。
- LLM provider 测试。
- 企微表格字段读取和写入测试。

LLM 主备切换受控验收：

```powershell
python scripts\acceptance_llm_failover_local.py
```

该脚本不需要真实 key，会用本地 fake provider 验证：

- 主 LLM 路由失败。
- 备用 LLM 路由成功。
- 回复来源为 `LLM`。
- 调用统计中同时有失败和成功记录。

## 失败排查顺序

1. 后端是否可访问：`GET http://localhost:8080/api/v1/auth/config`
2. 数据库是否迁移完成：`python scripts\verify_local_runtime_readiness.py`
3. key 是否写入配置：只看 `<set>/<empty>`，不要在日志里打印 key
4. 管理后台环境测试是否成功
5. 业务场景是否启用对应开关
6. `LLM 场景路由` 是否指向已激活或可用环境
7. `LLM 调用统计`、系统健康监控、后端日志是否记录错误码
8. 如果真实表格写入失败，先查看待写队列和失败重试记录

## 用户需要准备

- 真实 LLM provider 1：`base URL`、`API key`、`model`
- 真实 LLM provider 2：可选，但建议用于主备/A-B
- 图像识别接口：`base URL`、`API key`、`model`
- Skill/场景接口：`base URL`、`API key`
- 企微表格接口：`base URL`、`API key`、测试表格
- 脱敏聊天截图或聊天文本样例
- 期望回复风格和禁用话术
