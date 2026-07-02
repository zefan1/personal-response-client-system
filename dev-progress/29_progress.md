# 29 桌面I 求助模式进度卡

## 当前状态
- 状态：已实现并提交前验证
- 模块：桌面I 求助模式
- 入口：桌面B `help:request`

## 功能签收清单
- [x] B 面板发起 `help:request { phone, clientMessage, aiSuggestions }`，I 弹出求助窗。
- [x] 求助窗展示客户消息、AI 建议、500 字补充说明，调用 `POST /api/v1/help/request`。
- [x] 后端 help/request 支持 `clientMessage`、`aiSuggestions`、`keeperNote`，返回 `helpId/leaderOnline/forwarded/noFallbackAvailable`。
- [x] 后端通过 `WsPushService.isOnline` 判断直属组长/其他组长/管理员在线状态。
- [x] 全部组长不在线时发送 `help:timeout { reason: "NO_LEADER_ONLINE" }`，B 面板展示自救文案并恢复可求助。
- [x] 活跃求助期间 B 面板按钮显示「等待组长回复...」并禁用。
- [x] 组长端消费 `HELP_REQUEST` / `HELP_OFFLINE_REPLAY`，维护待处理求助队列。
- [x] 组长端支持确认采用、修改、自己写三种回复来源：`CONFIRMED` / `MODIFIED` / `ORIGINAL`。
- [x] 组长端调用 `POST /api/v1/help/resolve { helpId, helperReplies[] }`。
- [x] 新人端消费 `HELP_RESPONSE`，展示持续绿色回复条。
- [x] 新人复制组长回复时 emit `reply:selected`，`reason: "HELP_REPLY"`，交给 C 复制和 send-confirm。
- [x] 新增配置默认值：`desktop.help_offline_expire_hours=4`、`desktop.help_max_replies=3`，保留废弃 `desktop.help_timeout_s=30`。
- [x] 后端审计 action 使用 `ASK_FOR_HELP` / `RESOLVE_HELP`。

## 验证命令
```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_29.py
cd desktop; npm run typecheck
cd desktop; npm run build
```

## 实现备注
- 后端保留旧字段 `requestId/question/replyText` 兼容既有模块 H 验证，同时支持新字段 `helpId/clientMessage/helperReplies`。
- 真实离线队列由既有 `WsPushService.pushWsMessage` 统一落 `ws_offline_queue`；模块 I 不新增表。
