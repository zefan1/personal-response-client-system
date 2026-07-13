# 断点 044：管理后台批次 B 自动化验收通过

时间：2026-07-09 23:45  
状态：已完成

本轮目标：

- 对批次 B 管理后台写入类交互做不依赖真实 key 的自动化/半手工复测。
- 覆盖新增、编辑、启停、删除、导出、健康读取等后台核心操作。
- 将 B1-B5 结果写入 `dev-progress/local_real_test_manual_report.md`。

处理结果：

- 新增脚本：`scripts/acceptance_admin_batch_b.py`。
- 脚本使用 `codex-b-*` 临时数据，测试结束后自动清理。
- 覆盖范围：
  - B1：LLM 环境列表、环境新增、场景路由新增/启停、LLM 统计。
  - B2：数据源新增、字段映射保存、映射版本读取、启停、删除。
  - B3：速搜模板新增、编辑、启停、删除。
  - B4：组长账号、管家账号、组长绑定、停用、重置密码、删除。
  - B5：规则、标签、公告、审计导出、健康、版本草稿写入/读取/清理。
- 更新 `dev-progress/local_real_test_manual_report.md`，B1-B5 均记录为自动化通过或自动化通过但待业务体验确认。

验证结果：

- `python -m py_compile scripts\acceptance_admin_batch_b.py` 通过。
- `python scripts\acceptance_admin_batch_b.py` 通过：
  - `passed=true checks=39/39`
  - 报告：`.tools/acceptance/admin_batch_b.json`
- 管理后台组件测试通过：
  - `npm --prefix desktop test -- AdminConsole.test.ts`
  - 23 个用例通过。
- 管理后台产品面检查通过：
  - `python scripts\verify_admin_product_surface.py`
  - `passed=true violations=0 missingMarkers=0`

注意：

- 第一次 B 批次脚本调试时留下 1 个未激活 LLM 占位环境：`codex-b-llm-224515`。
- 原因：后端规则不允许删除最后一个 LLM environment。
- 当前它未激活，不影响真实 provider 后续配置；后续新增真实 LLM provider 后可以再删除该占位环境。

当前结论：

- 管理后台不是静态壳，主要写入类交互已自动化证明可用。
- 真实外部效果仍需 provider key 后验收。

下一步：

- 执行真实外部接口配置前的收口，或等待用户提供 provider。
- 如果继续不需要用户配合，可以补“真实 provider 填写表”和“上线前最终验收表”。
