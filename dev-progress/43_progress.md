# 43 运营D 速搜内容管理进度卡

## 基线
- 模块：运营D 速搜内容管理
- 依赖：独立配置模块；写 `quick_search_items`，通过后端H WS 通道刷新桌面F
- 共享契约：contentType=`TEMPLATE/KNOWLEDGE/LOCATION/IMAGE/MINI_PROGRAM`，leadType=`TUAN_GOU/XIAN_SUO/GENERAL`，WS `CONFIG_REFRESH`

## 功能签收清单
- [x] `GET /admin/api/v1/quick-search/items`：返回运营后台全量速搜内容，包含启用和禁用项。
- [x] `POST /admin/api/v1/quick-search/items`：新增内容，校验标题、类型、线索类型、快线码、正文。
- [x] `PUT /admin/api/v1/quick-search/items/{id}`：编辑已有内容。
- [x] `PUT /admin/api/v1/quick-search/items/{id}/toggle`：切换启用/禁用，成功后推送刷新。
- [x] `DELETE /admin/api/v1/quick-search/items/{id}`：物理删除内容；IMAGE 类型旧图写入 COS 清理队列。
- [x] `POST /admin/api/v1/upload/image`：图片上传入口，校验空文件、10MB 上限、JPG/PNG/WebP magic bytes。
- [x] `shortcutCode` 全局唯一且大小写不敏感，冲突返回 `80-10007`。
- [x] 保存/删除/切换后发布 `ConfigChangedEvent("quick_search")` 并广播 WS `CONFIG_REFRESH`。
- [x] 保持桌面端 `/api/v1/quick-search/items` 兼容；不删除旧 `scene` 字段，避免破坏桌面G批量模板排序。
- [x] 新增 `cos_cleanup_queue` 表。
- [x] `quick_search_items` 补充 `created_by` / `created_at` 字段。
- [x] 新增配置默认值：`quicksearch.admin.page_size=20`、`quicksearch.admin.image_max_size_mb=10`、`quicksearch.admin.cos_retention_days=30`。

## 验证命令
- `python scripts/verify_module_43.py`
- `python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py; python scripts/verify_module_g.py; python scripts/verify_module_h.py; python scripts/verify_module_20.py; python scripts/verify_module_21.py; python scripts/verify_module_22.py; python scripts/verify_module_23.py; python scripts/verify_module_24.py; python scripts/verify_module_25.py; python scripts/verify_module_26.py; python scripts/verify_module_27.py; python scripts/verify_module_28.py; python scripts/verify_module_29.py; python scripts/verify_module_30.py; python scripts/verify_module_31.py; python scripts/verify_module_32.py; python scripts/verify_module_33.py; python scripts/verify_module_40.py; python scripts/verify_module_41.py; python scripts/verify_module_42.py; python scripts/verify_module_43.py`
- `mvn test`
- `git diff --check`

## 假设与传播记录
- 当前无真实 COS SDK/凭证，本模块上传接口返回 `cos://quick-search/...` 形式的可替换对象地址，并保留 COS 清理队列表。
- 手册 43 要求移除 `scene` 字段，但桌面G当前仍消费 `scene`；本次保留旧字段兼容，运营Admin响应不暴露/不使用 scene。
- COS 清理定时任务未接入真实 COS API，先持久化 `cos_cleanup_queue`，待存储凭证确定后实现清理执行器。
