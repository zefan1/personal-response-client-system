# 识图失败自动聚焦回复助手设计

## 背景

桌面端侧边栏的蓝色“识别”入口可以发起整屏截图识别。真实识图服务可用，但当模型无法从整屏截图中提取聊天消息时，后端会返回 `30-10001` 及具体失败原因。当前页面只在成功事件 `recognize:result` 上自动切换到“回复助手”，失败事件会留在原页面；同时识别状态层只使用固定文案，丢失后端返回的具体诊断信息。

## 目标

- 所有识别终态都自动聚焦“回复助手”：成功、识图失败、格式失败、超时，以及截图采集失败。
- 回复助手优先显示后端返回的具体失败原因，缺少具体原因时继续使用现有通用文案。
- 不改变截图采集方式、后端 API 路径、错误码契约或文字通道行为。

## 方案

### 页面导航

在 `App.vue` 的全局事件订阅中，为 `recognize:image-failed`、`recognize:failed`、`recognize:timeout` 和 `recognize:multiple` 增加与成功结果一致的 `selectDesktopPanel('reply')` 行为。蓝色全局入口的截图采集失败分支也先切换到“回复助手”，再显示截图失败通知。

保留 `recognizeFromAnywhere` 在请求完成后切换页面的逻辑，作为全局入口的兜底；事件订阅负责覆盖剪贴板、工作台和回复助手内部发起的识别。

### 错误信息传递

`recognitionStore` 的错误处理函数接收 API 响应中的 `message`，并将其写入识别失败事件。`30-10001` 仍使用文字通道提示作为无 message 时的兜底；其他错误码沿用现有默认文案。回复助手现有失败状态无需改模板，只需消费事件中的 message。

### 数据流

```text
识别入口
  -> triggerRecognize
  -> API 返回终态
     -> recognitionStore 发出终态事件(message)
        -> ReplySuggestionPanel 保存失败会话
        -> App 选择“回复助手”
```

## 边界与不变项

- 不在本次修改中隐藏辅助系统窗口、选择特定聊天窗口或裁剪屏幕图像。
- 不把后端错误详情写入 localStorage、日志或 URL。
- 不修改 `30-10001`、`30-10002`、`80-10002` 错误码含义。
- 识别成功和多客户匹配的现有会话队列行为保持不变。

## 测试策略

- `App.test.ts`：新增截图采集失败自动切换回复助手的回归测试；新增终态失败事件触发页面切换的回归测试。
- `recognitionStore.test.ts`：新增 API 返回具体 message 时，失败事件携带该 message；无 message 时验证通用兜底文案。
- 运行上述定向 Vitest，再运行桌面端 typecheck、全量 Vitest、构建和 Electron renderer smoke。

