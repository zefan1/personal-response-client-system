# 管理后台客户筛选 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在管理后台客户查询中提供可用的分组筛选面板，并将全部条件提交到真实客户搜索和导出接口。

**Architecture:** 后端扩展客户筛选请求和 SQL 构造器，支持预约、跟进和到店条件；新增一个受账号数据范围约束的筛选选项接口。前端在 `AdminConsole.vue` 加载该接口，按基本条件、业务归属、时间与到店、标签条件渲染，并复用现有搜索与导出请求路径。

**Tech Stack:** Spring Boot、JdbcTemplate、JUnit 5、Vue 3、Vitest、现有 Vite 桌面端。

---

### Task 1: 扩展筛选契约与 SQL

**Files:**
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerFilter.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerSearchRequest.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerFilterValidator.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerFilterQueryBuilder.java`
- Test: `src/test/java/com/privateflow/modules/customer/admin/CustomerFilterQueryBuilderTest.java`

- [ ] **Step 1: 写失败测试**

验证预约日期范围、最近和下次跟进范围、是否到店值会生成参数化 SQL 条件，并保持既有关键词、标签和数据权限条件不变。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=CustomerFilterQueryBuilderTest test`

Expected: FAIL，因为当前筛选契约不存在上述字段。

- [ ] **Step 3: 实现最小筛选字段扩展**

在请求、领域筛选对象、校验器和查询构造器中一致传递并校验日期范围；所有值继续使用占位参数，禁止拼接用户输入。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=CustomerFilterQueryBuilderTest,CustomerFilterValidatorTest test`

Expected: PASS。

### Task 2: 提供动态筛选选项

**Files:**
- Create: `src/main/java/com/privateflow/modules/customer/admin/CustomerFilterOptions.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepository.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchService.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchController.java`
- Test: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryTest.java`
- Test: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchControllerTest.java`

- [ ] **Step 1: 写失败测试**

验证筛选选项接口返回来源、线索类型、管家、意向门店、意向项目、客户阶段和到店状态的去重非空值，并受当前账号可见管家范围约束。

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -Dtest=CustomerAdminSearchRepositoryTest,CustomerAdminSearchControllerTest test`

Expected: FAIL，因为当前没有筛选选项契约和接口。

- [ ] **Step 3: 实现最小选项查询和接口**

新增 `GET /admin/api/v1/customers/filter-options`；数据库从 `customers` 的真实列读取去重值，空值不返回，非管理员只看到被授权管家范围内的记录。

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -Dtest=CustomerAdminSearchRepositoryTest,CustomerAdminSearchControllerTest test`

Expected: PASS。

### Task 3: 接入管理后台筛选面板

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/styles.css`
- Test: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: 写失败测试**

验证客户查询展示分组筛选字段，加载动态选项，并将客户阶段、来源、管家、门店、项目、到店和时间范围提交到搜索、导出请求。

- [ ] **Step 2: 运行测试确认失败**

Run: `npm test -- AdminConsole.test.ts`

Expected: FAIL，因为现有页面只提交关键词和标签组。

- [ ] **Step 3: 实现最小 Vue 状态与界面**

使用现有 `getJson`、`postJson` 和 `postBlob`；将高频字段排在同一行，标签使用现有 chips，时间使用原生日期输入；不要改动工作台路由或工作台组件。

- [ ] **Step 4: 运行测试确认通过**

Run: `npm test -- AdminConsole.test.ts`

Expected: PASS。

### Task 4: 回归验证与浏览器验收

**Files:**
- Verify: `src/test/java/com/privateflow/modules/customer/admin/*`
- Verify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: 运行定向测试和构建**

Run: `mvn -Dtest=CustomerFilterQueryBuilderTest,CustomerFilterValidatorTest,CustomerAdminSearchRepositoryTest,CustomerAdminSearchControllerTest test`

Run: `npm run typecheck && npm run build`

- [ ] **Step 2: 浏览器验收**

打开 `http://127.0.0.1:5173/#/admin`，在客户查询中确认高级筛选的默认可见性、移动换行、条件重置和搜索请求；只保留该交付页面标签。
