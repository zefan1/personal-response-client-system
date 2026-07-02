# 27 桌面G 批量模板进度卡

## 当前状态
- 状态：已实现并提交前验证
- 模块：桌面G 批量模板
- 入口事件：`batch:start { phones, source: "FOLLOWUP_LIST" }`

## 功能签收清单
- [x] 接收桌面E `batch:start`，去重并按 `desktop.batch_max_customers=100` 校验。
- [x] 拉取 `GET /api/v1/quick-search/items?contentType=TEMPLATE&enabled=true`，失败时读取本地模板缓存。
- [x] 新增 `POST /api/v1/customers/batch`，请求 `{ phones[] }`，响应 `{ customers[] }`，不存在客户跳过。
- [x] 批量客户接口失败时降级逐个 `GET /api/v1/customers/{phone}`。
- [x] 支持模板场景筛选、智能预选和中途切换模板。
- [x] 支持变量替换：`{客户昵称}`、`{预约时间}`、`{预约门店}`、`{预约项目}`、`{管家名}`、`{意向门店}`、`{手机后4位}`。
- [x] 支持客户昵称/手机号复制，手机号复制完整 phone。
- [x] 点击复制写入系统剪贴板，并异步调用 `POST /api/v1/chat/send-confirm`，`selectedDirection/source=BATCH_TEMPLATE`。
- [x] send-confirm 失败仅写本地日志，不打断当前批量流程。
- [x] 支持上一个、下一个、暂停、继续、退出和完成汇总。
- [x] 新增桌面配置默认值：`desktop.batch_max_customers=100`、`desktop.batch_customer_batch_timeout_ms=3000`。

## 验证命令
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_27.py
cd desktop; npm run typecheck
cd desktop; npm run build
```

## 实现备注
- 当前桌面端沿用既有单窗口顶层遮罩架构实现全屏批量流程；未拆独立 BrowserWindow，以保持与模块F当前实现一致。
- 模块M尚未实现，因此 IndexedDB 兜底暂以 `localStorage` 模板缓存替代，后续由模块M统一迁移。
