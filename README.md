# Notice Hub · 内部通知与权限管理系统

Notice Hub 是一个已经接通 MySQL 和 Redis 的本地完整应用，用于学习 Spring Boot 后端、登录认证、角色权限和数据归属控制。后端、Vue 前端、数据库迁移、验收脚本和学习文档都保存在本项目中。

## 立即使用

1. 双击项目根目录的 `Start-NoticeHub.cmd`。
2. 浏览器打开 <http://127.0.0.1:8088>。
3. 打开 `.local/演示账号.md` 查看本机生成的初始密码。
4. 用 `admin`、`editor`、`employee`、`editor2` 分别体验管理员、编辑、员工、另一位编辑。

停止服务：双击 `Stop-NoticeHub.cmd`。修改代码后：停止服务 → 双击 `Build-NoticeHub.cmd` → 再启动。编译成功后会生成 `backend/target/notice-hub-1.0.0.jar`。

服务只监听本机 `127.0.0.1:8088`。新数据库为 `notice_hub`，应用使用独立数据库账号 `noticehub_app`；Redis 使用 DB 2 和 `noticehub-token` 前缀。原来的 demo 和数据库保持独立。

## 已实现功能

| 模块 | 功能 |
|---|---|
| 工作台 | 实时通知统计、本人发布数量、最新通知；管理员查看团队数量与近期活动 |
| 登录认证 | 用户名密码登录、两小时有效期、多会话、退出、连续失败限流 |
| 通知中心 | 发布、详情、编辑、逻辑删除、关键词搜索、分类、我的发布、分页、管理员置顶 |
| 用户管理 | 创建成员、编辑姓名、启停账号、分配一个或多个角色 |
| 角色权限 | 三个固定角色、11项权限、角色成员数和权限矩阵 |
| 审计日志 | 登录结果、通知变更、账号启停、角色分配、资料和密码变更；搜索、筛选、分页 |
| 个人设置 | 显示姓名、验证旧密码后修改密码、修改后注销全部会话 |

密码使用带独立随机盐的 Argon2id 哈希保存。接口传参使用 DTO，拒绝未知字段，客户端不能注入创建人或角色。通知更新带版本号，阻止并发覆盖；停用和权限撤销在下一个请求生效。

## 从哪里学习

- [一步一步学习这个系统](docs/一步一步学习.md)：操作、代码位置、设计原因、观察方法。
- [需求与设计](docs/需求与设计.md)：边界、角色矩阵、ER图、验收目标。
- [接口说明](docs/API.md)：请求、权限、错误状态和示例。
- [运行与维护](docs/SETUP.md)：IDEA、开发模式、配置、数据库和 WSL Redis。
- [真实验收结果](docs/验收结果.md)：本机接口验收记录。
- [浏览器验收](docs/浏览器验收.md)：实际页面交互和检查范围。

## 技术与目录

| 技术 | 采用方式 |
|---|---|
| JDK 17 / Spring Boot 3.5.16 / Maven | 单个后端服务，按职责分包 |
| MyBatis-Plus 3.5.15 / MySQL 8 | 基础实体CRUD + 显式参数化SQL |
| Sa-Token 1.46.0 / Redis 8 | 服务端会话与权限检查 |
| Flyway | 按版本自动执行数据库迁移 |
| Spring Security Crypto / Bouncy Castle | Argon2id 密码哈希；没有同时启用第二套登录框架 |
| Vue 3 / Vue Router / Vite / Lucide | 前端界面；构建后由后端同源提供 |

```text
notice-hub/
├─ backend/                  Spring Boot / Maven 工程
│  └─ src/main/
│     ├─ java/com/noticehub/
│     │  ├─ controller/      接口和参数接收
│     │  ├─ service/         业务规则和权限判断
│     │  ├─ mapper/          数据访问和SQL
│     │  ├─ domain/          数据库实体
│     │  ├─ dto/             输入字段和校验规则
│     │  ├─ config/          登录拦截、密码编码器、响应头、初始化
│     │  └─ common/          统一响应和异常处理
│     └─ resources/
│        ├─ application.yml
│        ├─ db/migration/    Flyway SQL
│        └─ static/          构建时生成的前端
├─ frontend/src/
│  ├─ pages/                六个业务页面和登录页
│  ├─ components/           图标、弹窗、分页
│  ├─ state.js              请求封装、会话和界面状态
│  └─ main.js               路由和页面入口
├─ scripts/                 构建、启动、停止、真实接口验收
├─ docs/                    设计、教学、API和验收材料
└─ .local/                  本机密码、运行PID、日志；不进入Git
```

需要核查的技术依据：[Spring Boot 3.5运行要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Sa-Token路由拦截](https://sa-token.com/use/route-check.html)、[Sa-Token权限认证](https://sa-token.com/use/jur-auth.html)、[Sa-Token Redis集成](https://sa-token.com/up/integ-redis.html)、[MyBatis-Plus](https://baomidou.com/getting-started/)、[Vue](https://vuejs.org/guide/introduction.html)、[OWASP鉴权](https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html)、[OWASP密码存储](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)。这些资料用于理解框架和安全原则；项目业务边界和参数是本学习项目的设计约定。
