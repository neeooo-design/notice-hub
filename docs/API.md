# API说明

基础地址：`http://127.0.0.1:8088/api`。请求和响应使用UTF-8 JSON；受保护接口携带 `noticehub-token: Bearer <token>`。所有列表最大每页100条。

成功响应为 `{"code":0,"message":"操作成功","data":...}`。失败使用实际HTTP错误状态，响应体中 `code`为同一状态、`message`为可读说明、`data`为null。列表data包含 `items,total,page,size`。

| 方法 | 路径 | 用途 | 访问条件 |
|---|---|---|---|
| GET | /health | 检查服务、MySQL和Redis就绪 | 公开 |
| POST | /auth/login | 登录 | 公开 |
| GET | /auth/me | 本人资料、角色和权限 | 登录 |
| POST | /auth/logout | 注销当前会话 | 登录 |
| PUT | /auth/profile | 修改本人显示名 | 登录 |
| PUT | /auth/password | 修改本人密码并注销全部会话 | 登录且旧密码正确 |
| GET | /dashboard | 工作台数据 | notice:read；管理统计另检查权限 |
| GET | /notices | 查询通知 | notice:read |
| GET | /notices/{id} | 通知详情 | notice:read |
| POST | /notices | 创建通知 | notice:create |
| PUT | /notices/{id} | 修改通知 | notice:manage，或本人修改权限且属于本人 |
| DELETE | /notices/{id} | 逻辑删除通知 | notice:manage，或本人删除权限且属于本人 |
| GET | /users | 查询用户 | user:read |
| POST | /users | 创建用户并分配初始角色 | user:create + role:assign |
| PUT | /users/{id} | 修改用户显示姓名 | user:update |
| PUT | /users/{id}/enabled | 启停账号 | user:update；保护规则见需求文档 |
| PUT | /users/{id}/roles | 分配用户角色 | role:assign；保护规则见需求文档 |
| GET | /roles | 角色、权限和人数 | role:read |
| GET | /audit-logs | 查询审计记录 | audit:read |

## 请求示例

登录：

```json
{"username":"editor","password":"填写本机演示密码"}
```

data包含 `token,tokenName,expiresIn,user`。user包含 `id,username,displayName,enabled,protectedAccount,createdAt,roles,permissions`，不会包含密码或密码哈希。

创建通知：

```json
{"title":"本周技术分享","content":"周五下午三点，欢迎参加。","category":"技术分享","pinned":false,"version":null}
```

更新通知使用相同字段，并带详情接口返回的最新 `version`。分类为 `公司动态、行政通知、技术分享、活动资讯` 之一。置顶需要 `notice:manage`。创建成功data为新通知ID。

通知筛选：`GET /notices?page=1&size=9&q=技术&category=技术分享&mine=false`。`mine=true`由后端根据当前身份筛选本人发布。

创建用户：

```json
{"username":"zhangsan","displayName":"张三","password":"ExamplePassword2026!","roles":["EMPLOYEE","EDITOR"]}
```

修改用户或本人显示名：`{"displayName":"新的姓名"}`。

启停：`{"enabled":false}`。分配角色：`{"roles":["EDITOR"]}`。角色列表至少一个，角色码为 `EMPLOYEE、EDITOR、ADMIN`。

修改密码：

```json
{"oldPassword":"当前密码","newPassword":"新的至少10位字母数字密码"}
```

用户筛选：`GET /users?q=zhang&page=1&size=10`。

日志筛选：`GET /audit-logs?action=NOTICE_CREATE&q=林舟&page=1&size=12`。action为空表示全部；q查询操作人显示名或对象。

## 状态解释

| HTTP | 情况 | 前端处理 |
|---|---|---|
| 400 | 字段缺失、格式错误、注入未声明字段 | 提示修改输入 |
| 401 | 无登录凭证、失效、已退出 | 清除本地Token，返回登录页 |
| 403 | 没有操作权限、数据不属于本人、受保护账号限制 | 提示权限不足，刷新权限信息 |
| 404 | 通知、用户或接口不存在 | 提示不存在 |
| 409 | 用户名重复、通知版本冲突 | 修改用户名或重新读取通知 |
| 429 | 登录失败次数过多 | 等待计数窗口结束 |
| 500 | 未预期服务异常 | 显示通用错误，具体诊断见本机后端日志 |
| 503 | 初始化中或MySQL/Redis未就绪 | 启动脚本继续等待或报告依赖异常 |

请求DTO与数据库实体分别定义，额外注入 `createdBy、passwordHash、protectedAccount` 等字段会被拒绝。接口正文不得使用实体对象直接替代请求示例。
