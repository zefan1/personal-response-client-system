# 私域辅助系统

生产级 SaaS 工程实现目录。蓝图手册位于父目录 `C:\Users\85314\Desktop\私域工具`。

## 当前实现断点

- 已覆盖 34 个实际实施模块：
  - 后端基础模块：`01A-01H`
  - 桌面端模块：`20-33`
  - 运营后台模块：`40-51`
- 最新完成模块：`51_运营L_系统健康监控`
- 最新进度卡：`dev-progress/51_progress.md`
- 最新验证脚本：`scripts/verify_module_51.py`
- 最新提交请以 `git log --oneline -1` 为准。

## 验证命令

后端正式 Java 验证：

```powershell
mvn test
```

当前机器 PATH 未提供 Java/Maven 时，运行结构与契约静态验证：

```powershell
$scripts = @('verify_module_a.py','verify_module_b.py','verify_module_c.py','verify_module_d.py','verify_module_e.py','verify_module_f.py','verify_module_g.py','verify_module_h.py') + (20..33 | ForEach-Object { "verify_module_$_.py" }) + (40..51 | ForEach-Object { "verify_module_$_.py" })
foreach ($script in $scripts) { python "scripts\$script"; if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE } }
```

桌面端类型检查：

```powershell
Set-Location desktop
npm run typecheck
```

空白与换行检查：

```powershell
git diff --check
```
