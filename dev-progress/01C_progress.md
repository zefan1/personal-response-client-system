# 01C 后端C 图片识别通道进度卡

## 基线来源

- 手册：`C:\Users\85314\Desktop\私域工具\01C_后端_图片识别通道_开发实现手册.md`
- 公共契约：`C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- 依赖图：`C:\Users\85314\Desktop\私域工具\DEPENDENCIES.md`

## 当前生效基线

- 模块 C 不暴露 REST API，只提供 `recognize(byte[] image, Source source)` Java 服务接口。
- Source 枚举只允许 `BUTTON_CLICK` / `CLIPBOARD_SCREENSHOT`。
- RecognitionResult 字段为 `nickname`、`phone`、`messages[{role,text}]`、`timestamp`。
- messages role 只输出 `client` / `keeper`。
- 错误码只使用 `30-10001` 图片识别失败、`30-10002` 图片格式不支持。
- 图片零存储：不落盘、不入库、不缓存；识别完成后释放局部引用。
- `image.*` 8 项配置从 `system_configs` 读取，默认值按 SHARED_CONTRACTS 与 01C §5。
- 识图 LLM 调用 0 次自动重试。

## 功能签收清单

| 项 | 来源 | 状态 | 验证命令 |
|----|------|------|----------|
| SF-C01 Source 枚举 | 01C §2.2 | 已完成 | `python scripts/verify_module_c.py` |
| SF-C02 RecognitionResult / Message 契约 | 01C §3.2 | 已完成 | `python scripts/verify_module_c.py` |
| SF-C03 图片格式与体积校验 | 01C §4.1 | 已完成 | `python scripts/verify_module_c.py`; 正式：`mvn test` |
| SF-C04 图片预处理缩放与 JPEG 压缩 | 01C §4.2 | 已完成 | `python scripts/verify_module_c.py`; 正式：`mvn test` |
| SF-C05 识图 LLM HTTP 客户端 | 01C §4.3 | 已完成 | `python scripts/verify_module_c.py`; 正式：`mvn test` |
| SF-C06 响应 JSON 健壮解析与 role 归一化 | 01C §4.4 | 已完成 | `python scripts/verify_module_c.py`; 正式：`mvn test` |
| SF-C07 健康监控 UP/DOWN 事件 | 01C §4.5/§8 | 已完成 | `python scripts/verify_module_c.py`; 正式：`mvn test` |
| SF-C08 image.* 配置种子与热更新 | SHARED_CONTRACTS §image.* | 已完成 | `python scripts/verify_module_c.py` |

## 已做关键假设

- 当前机器仍无 Java/Maven，正式编译测试需补 JDK 17 + Maven 后执行。
- 为支持 WebP 预处理，工程加入 TwelveMonkeys ImageIO WebP 插件依赖。
- 真实识图供应商未确认时，`image.api_base_url` / `image.api_key` 保持空配置；开发期使用 `MOCK_EXTERNALS=true` 的 Mock 客户端。

## 下一步

补 JDK 17/Maven 环境后执行 `mvn test`，再进入后端 B 或 D 的实现。
