# 本地真机测试断点 050

时间：2026-07-10 16:55

## 本轮处理

- 复现管理后台 `E 账号与权限` 新增组长失败现场。
- 根因 1：本地后端是旧进程，未带最新 CORS 兜底头，浏览器把 401 误显示为“网络连接失败”。
- 根因 2：认证过滤器错误响应未强制 UTF-8，未登录提示会显示成 `????`。
- 已重启本地 WSL 后端到最新代码。
- 已确认截图监听代码没有切换输入法逻辑，只读取剪贴板图片和屏幕图像；后续需记录具体截图快捷键来判断是否是 Windows/聊天软件快捷键导致。

## 已修改文件

- `src/main/java/com/privateflow/modules/api/auth/JwtAuthenticationFilter.java`
  - 认证错误响应强制 `UTF-8`。
- `src/test/java/com/privateflow/modules/api/auth/JwtAuthenticationFilterTest.java`
  - 增加中文“请先登录”不乱码断言。
- `dev-progress/local_real_test_user_checklist_050.md`
  - 补充后端重启要求和截图输入法观察项。

## 已验证

- `mvn -q -Dtest=JwtAuthenticationFilterTest test`
  - 4 个用例通过，0 失败。
- `curl -i -H "Origin: http://127.0.0.1:5173" http://localhost:8080/admin/api/v1/accounts`
  - 返回 `Access-Control-Allow-Origin: http://127.0.0.1:5173`
  - 返回 `Content-Type: application/json;charset=UTF-8`
  - 返回中文 `请先登录`
- Chrome 管理后台重新登录成功。
  - 页面显示 `真实接口模式`
  - 不再显示“网络连接失败”
- UI 新增组长测试成功。
  - 创建临时账号 `TestLeader / 15600004011 / 组长`
  - 已删除该临时账号并刷新列表确认不存在。

## 当前状态

- 后端：已运行，`http://localhost:8080`
- 前端：已运行，`http://127.0.0.1:5173/#/admin`
- 管理后台 Chrome 标签：已登录 `System Admin`，停在 `E 账号与权限`

## 用户下一步

- 打开 `dev-progress/local_real_test_user_checklist_050.md`
- 按里面 6 项逐项勾选测试。
- 如果截图后输入法仍变英文，在 checklist 的“额外观察”里记录使用的是 `Win+Shift+S`、微信截图快捷键，还是侧边栏“识别”按钮。
