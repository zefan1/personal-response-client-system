# 01A 后端A 客户数据缓存层进度卡

## 基线来源

- 手册：`C:\Users\85314\Desktop\私域工具\01A_后端_客户数据缓存层_开发实现手册.md`
- 公共契约：`C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- 依赖图：`C:\Users\85314\Desktop\私域工具\DEPENDENCIES.md`

## 当前生效基线

- 模块 A 不暴露 REST API，只提供 Java 服务层接口和 Spring Event。
- 强依赖：MySQL、Redis、企微智能表格 API；开发期企微通过 Mock Adapter。
- 配置 Key 使用 `cache.` 前缀，字段映射配置使用 `datasource.field_mappings`。
- leadType 只写入 `TUAN_GOU`、`XIAN_SUO`、`PENDING` 或 null。
- 高频查询：`getByPhone` 先 Redis，失败降级 MySQL；`searchByNickname` 和 `scanActiveCustomers` 直查 MySQL。

## 功能签收清单

| 项 | 来源 | 状态 | 验证命令 |
|----|------|------|----------|
| SF-A01 customers 主表 DDL 与索引 | 01A §6 + SHARED_CONTRACTS | 已完成 | `python scripts/verify_module_a.py` |
| SF-A02 datasource_field_mappings 表 | 01A §6.1.1 | 已完成 | `python scripts/verify_module_a.py` |
| SF-A03 sync_failure_log 表 | 01A §6.1.2 | 已完成 | `python scripts/verify_module_a.py` |
| SF-A04 Customer Java 模型字段 | SHARED_CONTRACTS §1 | 已完成 | `python scripts/verify_module_a.py`; 正式：`mvn test` |
| SF-A05 CustomerQueryService 四个接口 | 01A §3.1 | 已完成 | `python scripts/verify_module_a.py`; 正式：`mvn test` |
| SF-A06 Redis 缓存读写、TTL、击穿锁 | 01A §3.2.1/§4 | 已完成 | 正式：`mvn test` |
| SF-A07 字段映射加载与默认映射 | 01A §4.2 | 已完成 | `python scripts/verify_module_a.py`; 正式：`mvn test` |
| SF-A08 三表同步与合并顺序 | 01A §2.1/§4.1 | 已完成 | `python scripts/verify_module_a.py`; 正式：`mvn test` |
| SF-A09 NewLeadEvent 发布 | 01A §2.3/§8 | 已完成 | `python scripts/verify_module_a.py`; 正式：`mvn test` |
| SF-A10 ProfileUpdatedEvent/ConfigChangedEvent 消费 | 01A §2.3/§8 | 已完成 | `python scripts/verify_module_a.py`; 正式：`mvn test` |

## 已做关键假设

- 当前落地目录为空且非 git 仓库，先初始化单体 Spring Boot 后端工程。
- 本机未安装 Java/Maven，正式编译测试需安装 JDK 17 或补充 Maven Wrapper jar 后执行。
- 外部企微表格开发期只使用 Mock Adapter，不连接真实外部系统。

## 下一步

补 JDK 17/Maven 环境后执行 `mvn test`，再进入后端 C 或 B 的实现。
