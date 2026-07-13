# 桌面端发布步骤模板

适用阶段：本地测试和真实 provider 验收通过后。

## 1. 发布前检查

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

## 2. 打包 Windows 桌面端

未签名本地验证包：

```powershell
npm --prefix desktop run package:verify
```

正式签名包：

```powershell
$env:WINDOWS_CERTIFICATE_FILE="C:\path\to\certificate.pfx"
$env:WINDOWS_CERTIFICATE_PASSWORD="***"
$env:WINDOWS_TIMESTAMP_SERVER="http://timestamp.digicert.com"
npm --prefix desktop run package:verify:signed
```

签名前先检查：

```powershell
python scripts\verify_release_signing_readiness.py --target win32
```

## 3. 上传安装包

后台路径：

- 登录管理后台。
- 打开版本管理。
- 新增版本，平台选择 `WINDOWS`。
- 上传安装包或填写下载地址。
- 填写更新说明。
- 先保存草稿。
- 发布前在测试电脑下载安装。

## 4. 发布策略

推荐顺序：

1. 内部测试：`OPTIONAL`。
2. 小范围灰度：`GRADUAL`，例如 10%。
3. 全量可选更新：`OPTIONAL`。
4. 必须更新只用于严重问题修复：`FORCED`。

## 5. 发布后检查

```powershell
python scripts\verify_production_blockers.py
```

在一台干净 Windows 机器上验证：

- 可以打开应用。
- API 地址为生产 HTTPS 地址。
- 可以登录。
- 工作台、客户档案、回复助手可用。
- 版本检查能收到最新版本。
- 下载链接可用。
