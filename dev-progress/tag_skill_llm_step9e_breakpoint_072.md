# Step 9E 断点：客户分页、导出和数据权限查询统一

日期：2026-07-17  
分支：`feature/tag-step8-reply-tag-context`  
设计：`docs/superpowers/specs/2026-07-17-customer-search-export-design.md`  
实施计划：`docs/superpowers/plans/2026-07-17-customer-search-export.md`  
实现代码 HEAD：`e00f890`

状态：Step 9E 已完成，未 push、未 merge、未创建 PR。

## 本轮结果

- `POST /admin/api/v1/customers/search`、旧 `GET /admin/api/v1/customers/search` 和新 `POST /admin/api/v1/customers/export` 全部经过统一筛选校验和当前账号权限解析。
- 客户 COUNT、分页行、导出行、标签 ANY/ALL、分类 AND/OR、排序和当前标签摘要统一使用 `CustomerFilterQueryBuilder` 与 `CustomerQuerySpec`。
- 旧 GET 不再固定使用 `CustomerAccessScope.all()`，ADMIN、LEADER、KEEPER 均按当前账号范围查询。
- 新客户 CSV 导出忽略页码限制，保留完整筛选和排序；超过 10,000 行返回中文错误，不生成部分文件。
- CSV 使用 UTF-8 BOM，包含客户资料、跟进/预约信息、来源、更新时间，以及当前标签中文名称和内部编码。
- CSV 所有数据单元格执行引号转义和公式注入防护。
- AdminConsole 客户查询区域新增“导出当前查询”；API client 新增带认证、超时和错误处理的 `postBlob`。
- 真实启动首次发现 Repository 多构造器缺少 Spring 注入标记；新增装配契约测试并给生产构造器增加 `@Autowired`，重启后通过。

## 主要提交

- `ad85027` `docs: design customer search export alignment`
- `614eae2` `docs: plan customer search export alignment`
- `d8e53ae` `feat: add permission-aware customer csv export`
- `d7fb1fb` `feat: add customer search csv download`
- `e00f890` `fix: mark customer search repository constructor`

## 验证记录

后端相关递归测试：

```text
mvn -q -Dtest=com.privateflow.modules.customer.admin.**.*Test,com.privateflow.modules.analytics.**.*Test,com.privateflow.modules.followup.**.*Test,com.privateflow.modules.tags.**.*Test,com.privateflow.modules.tablewrite.**.*Test test
```

结果：50 个测试套件，197 tests，0 failures，0 errors，1 conditional skip。

前端：

```text
npm test -- --run
npm run typecheck
npm run build
```

结果：37 个 Vitest 文件、264 tests 全部通过；typecheck 通过；生产构建通过。第一次完整 Vitest 运行中既有 `QuickSearchOverlay` 测试因 5 秒超时失败，单文件 4/4 通过，完整重跑 264/264 通过，确认是并发资源偶发超时。

真实运行：

- 后端：`http://127.0.0.1:8082`，当前 worktree 代码，数据库 `private_domain_assistant_smoke`，`MOCK_EXTERNALS=true`。
- 前端：`http://127.0.0.1:5175/`。
- 管理员登录后调用 `POST /admin/api/v1/customers/export` 返回 HTTP 200、`text/csv;charset=UTF-8`、附件名 `customers.csv`，响应 1,350 字节。

## 保护项核对

- `36d9b81..e00f890` 没有新增或修改 Flyway migration。
- `system_tag_suggestions.status=PENDING` 仍为 6 条：ID 1-6，原文依次为“沉睡风险/可能流失”。
- `llm.profile_extraction.enabled=false`。
- `llm.reply_generation.enabled=false`。
- 未修改 Step 8 回复标签上下文、客户标签数据或现有建议状态。
- 未执行 merge、push 或创建 PR。

## 下一步

Step 9 仍有一项单独任务：处理现有 6 条 `system_tag_suggestions.status=PENDING` 历史记录，要求不丢原文且不计入正式统计。之后进入 Step 10 全量测试和真实运行验收。本断点之后不自动修改这 6 条记录。
