# 01D 后端D 客户匹配服务进度卡

## 权威输入

- 手册：`C:\Users\85314\Desktop\私域工具\01D_后端_客户匹配服务_开发实现手册.md`
- 共享契约：`C:\Users\85314\Desktop\私域工具\SHARED_CONTRACTS.md`
- 依赖拓扑：`C:\Users\85314\Desktop\私域工具\DEPENDENCIES.md`

## 依赖检查

- 强依赖：后端A 客户数据缓存层
- 当前状态：后端A 已实现并已有静态验证脚本 `scripts/verify_module_a.py`
- D 不直接访问外部系统，不新增业务表；所有客户查询通过 `CustomerQueryService`

## 功能签收清单

- [x] SF-D01 三级匹配链：手机号 EXACT -> 昵称 FUZZY/MULTIPLE -> NONE
- [x] SF-D02 有 phone 但精确未命中时降级走昵称匹配，不直接 NONE
- [x] SF-D03 标记去除规则读取 `match.tag_removal_rules`，按长度降序、大小写不敏感匹配
- [x] SF-D04 模糊匹配通过 A `searchByNickname(cleanedNickname, maxCandidates)`，不直接访问 MySQL
- [x] SF-D05 置信度枚举只使用 HIGH / MEDIUM，HIGH 条件为子串关系 + 覆盖比阈值 + 最小长度
- [x] SF-D06 1 条 HIGH 返回 FUZZY；1 条 MEDIUM 返回 MULTIPLE；多条返回 MULTIPLE
- [x] SF-D07 候选排序：自己的客户优先、最近跟进优先、HIGH 优先于 MEDIUM，null 时间排最后
- [x] SF-D08 REST `GET /api/v1/customers/search`：手机号精确优先，昵称模糊兜底，limit 范围 1-50
- [x] SF-D09 REST `GET /api/v1/customers/{phone}`：清洗并校验 11 位手机号，返回全量 Customer 且响应 phone 脱敏
- [x] SF-D10 错误码：40-10001、40-10002、80-10001
- [x] SF-D11 `match.*` 5 个配置项写入 Flyway 并支持 ConfigChangedEvent 热更新
- [x] SF-D12 匹配结果不落盘、不发 Spring Event、不调用 B/C

## 新增/修改文件

- 新增：`src/main/java/com/privateflow/modules/match/**`
- 新增：`src/main/resources/db/migration/V4__module_d_customer_match.sql`
- 新增：`scripts/verify_module_d.py`
- 修改：`CustomerQueryService` 增加 `searchByNickname(String nickname, int limit)` 重载
- 修改：`SystemConfigRepository` 增加 `findByPrefix`
- 修改：`pom.xml` 增加 `spring-boot-starter-web`
- 修改：`application.yml` 增加 `match.*` 默认配置

## 实现假设

- D 的 Java 服务入口增加了可选 `currentUser` 字段，用于候选排序中的“自己的客户优先”；H 后续可不传，排序会自然退化为最近跟进优先。
- 模糊搜索超时用 `CompletableFuture.supplyAsync` + `get(timeout)` 实现，不创建 D 自有线程池，避免新增生命周期资源。
- 手册第 4.8 写“不订阅事件”，但第 1/5/6 章要求 `ConfigChangedEvent` 热更新 `match.*`；本实现只订阅配置变更事件，不发布业务事件。

## 验证命令

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_d.py
```

## 验证结果

- 已通过：

```powershell
$env:PYTHONUTF8='1'; python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py
```

- 输出摘要：A/B/C/D 静态验证全部 passed。

## Maven/JDK 状态

- 阻塞：

```powershell
mvn test
```

- 当前输出：`mvn : The term 'mvn' is not recognized as the name of a cmdlet...`
- 结论：不能声明 Java 编译测试已通过。
