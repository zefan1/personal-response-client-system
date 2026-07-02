# 42 运营C 客户数据对接进度卡

## 基线
- 模块：运营C 客户数据对接
- 依赖：后端A 客户数据缓存层、后端H API 层
- 共享契约：`/admin/api/v1/`、`datasource.field_mappings`、`datasource.connections`、`cache.*`、`datasource.*`

## 功能签收清单
- [x] `GET /admin/api/v1/datasources`：返回数据源列表、映射数量、最近同步时间和同步状态。
- [x] `POST /admin/api/v1/datasources`：新增数据源，校验 name/sheetId/sourceTable。
- [x] `PUT /admin/api/v1/datasources/{id}`：编辑数据源连接信息。
- [x] `DELETE /admin/api/v1/datasources/{id}`：删除数据源并清理当前字段映射。
- [x] `PUT /admin/api/v1/datasources/{id}/toggle`：启用/禁用数据源。
- [x] `PUT /admin/api/v1/datasources/{id}/replace`：换表，仅更新 sheetId，保留映射。
- [x] `GET /admin/api/v1/datasources/{id}/mappings`：读取当前字段映射和当前版本号。
- [x] `PUT /admin/api/v1/datasources/{id}/mappings`：整体替换映射，校验同一数据源启用 targetField 唯一，保存版本快照。
- [x] `GET /admin/api/v1/datasources/{id}/mappings/versions`：返回最近 20 条映射版本。
- [x] `POST /admin/api/v1/datasources/{id}/mappings/restore`：恢复历史版本并生成新版本。
- [x] `GET /admin/api/v1/datasources/{id}/columns`：当前 SheetClient 无列名能力，按手册降级返回空列名和 fallback 提示。
- [x] `GET /admin/api/v1/customer-fields`：反射 Customer 模型业务字段，排除系统字段。
- [x] `GET /admin/api/v1/datasources/sync-status`：展示每个数据源同步状态与未解决失败原因。
- [x] `POST /admin/api/v1/datasources/{id}/sync`：触发后端A同步器执行一轮同步。
- [x] `POST /admin/api/v1/datasources/import`：CSV 导入，校验 phone，单次最多 5000 行，同文件重复手机号跳过。
- [x] CSV 导入已存在客户时只补空字段，不覆盖已有非空字段。
- [x] 新增表：`datasources`、`datasource_mapping_versions`、`customer_import_log`。
- [x] 新增配置默认值：`datasource.mapping_version_max=50`、`datasource.import_max_rows=5000`、`datasource.manual_sync_timeout_s=60`、`datasource.sync_status_refresh_s=30`。
- [x] 保存数据源/映射后发布 `ConfigChangedEvent` 和 WS `CONFIG_REFRESH`。

## 验证命令
- `python scripts/verify_module_42.py`
- `python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py; python scripts/verify_module_g.py; python scripts/verify_module_h.py; python scripts/verify_module_20.py; python scripts/verify_module_21.py; python scripts/verify_module_22.py; python scripts/verify_module_23.py; python scripts/verify_module_24.py; python scripts/verify_module_25.py; python scripts/verify_module_26.py; python scripts/verify_module_27.py; python scripts/verify_module_28.py; python scripts/verify_module_29.py; python scripts/verify_module_30.py; python scripts/verify_module_31.py; python scripts/verify_module_32.py; python scripts/verify_module_33.py; python scripts/verify_module_40.py; python scripts/verify_module_41.py; python scripts/verify_module_42.py`
- `mvn test`
- `git diff --check`

## 假设与传播记录
- 当前 `SheetClient` 没有列名读取方法，`columns` 接口按手册降级为手动输入模式。
- 当前手动同步触发已有 `CustomerSyncScheduler.runOnce()`，不是只同步单张表；响应中带 datasourceId/sourceTable 供前端展示。
- 当前 CSV 导入实现最小生产闭环：phone/nickname 基础导入和不覆盖已有非空字段，后续可扩展到所有 Customer 字段。
