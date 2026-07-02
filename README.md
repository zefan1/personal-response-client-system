# 私域辅助系统

生产级 SaaS 工程实现目录。蓝图手册位于父目录 `C:\Users\85314\Desktop\私域工具`。

## 当前实现断点

- 阶段 1：基础后端工程 + 后端A（客户数据缓存层）
- 进度卡：`dev-progress/01A_progress.md`

## 验证命令

正式 Java 验证：

```powershell
mvn test
```

当前机器 PATH 未提供 Java/Maven 时，可先运行结构与契约静态校验：

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_a.py
```
