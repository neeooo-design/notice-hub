# 本地开发与调试

本页供修改源码、调试和运行开发实例时使用。日常使用直接访问 [Notice Hub 云端系统](https://129.211.14.37)；服务器的部署、日志、更新与备份见 [云服务器部署与维护](云服务器部署与维护.md)。

## 本机已经配置的内容

项目目录：`C:\path\to\notice-hub`。后端在 `backend`，前端在 `frontend`。JDK为本机Temurin17，Maven复用本机已安装的3.10.0，Node为24.15.0。

MySQL新建 `notice_hub` 数据库和仅访问这个库的 `noticehub_app` 应用用户。密码写在 `.local/backend.properties`，已被 `.gitignore`排除。后端在其工作目录 `backend` 下通过 `spring.config.import`加载这个文件。

Redis使用现有WSL实例中的服务，数据库索引为2，Sa-Token前缀为 `noticehub-token`；登录失败计数前缀为 `noticehub:login-fail:`。启动脚本不会清空Redis数据库。

## 启动本地开发实例

| 任务 | 操作 |
|---|---|
| 启动 | 双击 Start-NoticeHub.cmd |
| 打开页面 | http://127.0.0.1:8088 |
| 查看初始账号 | .local/演示账号.md |
| 停止后端 | 双击 Stop-NoticeHub.cmd |
| 修改后重新构建 | 停止 → Build-NoticeHub.cmd → Start-NoticeHub.cmd |
| 查看诊断 | .local/backend.out.log、backend.err.log |
| 重复接口验收 | python scripts/verify.py |

启动脚本检查端口归属，启动WSL运行保持进程，确认Windows能访问Redis，启动后端，并等待健康检查通过再打开浏览器。已在运行时重复启动会复用现有服务。

停止脚本只对本项目保存的PID操作，并核对进程名和Jar名称；不会结束其他Java项目。它保留MySQL、Redis和WSL运行保持进程，便于随后重启及其他已有项目继续使用。运行保持进程PID保存在 `.local/wsl-keepalive.json`，是隐藏的 `wsl.exe -e sleep infinity`。如需结束它，可在确认不再需要WSL后按保存的PID核对并停止；不需要修改防火墙或WSL全局网络配置。

## 在IDEA中运行

1. 用IDEA打开 `backend/pom.xml` 作为Maven工程。
2. Project SDK和Maven Runner JRE选择17。
3. `NoticeHubApplication`运行配置的工作目录设为项目的 `backend` 目录，保证能读取 `../.local/backend.properties`。
4. 若一键启动的后端正在运行，先停止，避免8088端口冲突。
5. 启动入口类。已构建的前端继续由Spring Boot提供；如果要编辑前端，按下一节启用Vite。

## 前端开发模式

在 `frontend` 目录运行：

```powershell
npm ci
npm run dev
```

访问 <http://127.0.0.1:5173>。Vite代理 `/api` 到8088，后端保持运行；前端源码修改可即时更新。`npm run build`生成dist。一键构建脚本把dist复制到后端静态目录，再打包Jar。

## 本地配置模板

`.local/backend.properties`需要以下信息，密码由本机配置提供：

```properties
spring.datasource.username=noticehub_app
spring.datasource.password=填写应用数据库密码
app.seed-demo=false
```

首次初始化空数据库时，另设置 `app.seed-demo=true` 和 `app.seed-password=初始演示密码`。示例初始化要求密码至少10位，并只在用户表为空时运行。正常重启后不重新插入演示数据，也不会重设已修改的密码。当前本机已完成初始化。

需要迁移到另一台电脑时：先创建独立数据库和应用用户，安装JDK17、Maven、Node、MySQL8、Redis6或更高，按配置模板写入连接信息，再构建启动。`scripts/tools.ps1`包含当前本机的JDK与Maven路径，可以按新机器修改。Redis认证或地址变化可通过 `spring.data.redis.*`本地配置覆盖。

## WSL Redis检查

WSL内的 `redis-cli ping` 返回PONG，只能证明Linux内可用，还要检查Windows侧6379端口。微软文档说明，Windows通常可以通过localhost访问WSL服务，但systemd服务本身不保证WSL实例持续存活。因此本项目启动时保留一个WSL客户端并等待Windows侧端口可连接，再启动依赖Redis的后端。

依据：[WSL网络](https://learn.microsoft.com/en-us/windows/wsl/networking)、[WSL systemd说明](https://learn.microsoft.com/en-us/windows/wsl/systemd)。运行保持是本项目在当前机器上的适配实现。

## 数据与备份

用户、通知和审计记录保存在MySQL；构建与重启不会重建或清空数据。数据库结构由Flyway版本管理。演示数据初始化与数据库结构迁移相互独立。

开发数据库可使用 MySQL Workbench 的 Data Export 导出 `notice_hub`。本地开发实例绑定本机地址；云端实例已配置 HTTPS、独立凭据、每日备份与恢复演练，维护方法见 [云服务器部署与维护](云服务器部署与维护.md)。

## 常见现象

| 现象 | 检查位置 |
|---|---|
| 网页打不开 | 启动脚本输出、8088端口、后端日志 |
| 登录提示暂时不可用 | GET /api/health是否503；MySQL和Redis连接 |
| IDEA运行找不到密码配置 | 工作目录必须是backend |
| 新前端没有变化 | 停止服务、重新构建、重新启动并刷新浏览器 |
| 角色调整后按钮还在 | 页面导航或等待权限同步；后端已在下次请求执行新规则 |
| 演示账号密码不对 | 初始账号文件仅记录初始密码；确认是否已在个人设置修改 |
| 原来的账号还在，重启未恢复初始数据 | 正常行为，初始化不会覆盖已有用户 |
| 构建时提示Jar正被使用 | 先停止本项目后端再编译 |
