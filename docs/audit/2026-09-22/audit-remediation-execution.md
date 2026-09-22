# 代码审计补充修复执行记录

基线：`main@5e7c10e`（`acceptance-20260922`）。本次仅处理新增的四个检查点，不重开已关闭问题，也不包含大版本迁移或服务拆分。

## 已实现

- 小程序安全边界：仅登录、期次列表/详情和邀请码详情匿名开放；其余 `/app/qinglife/**` 默认经过统一 Token Filter。Filter 同时校验 Redis 会话、用户存在性和启用状态，新增接口不会再因遗漏 Service 内手工校验而自动公开。
- Token 收紧：管理后台绝对有效期默认和生产上限改为 8 小时，小程序 Token 改为 24 小时并可配置；管理端 Token 从持久 Cookie 改为标签页级 `sessionStorage`，删除记住密码 Cookie；小程序增加退出接口并立即删除 Redis Token。原后台退出仍删除服务端 Token。
- 报名时间：邀请入口和最终报名统一调用 `QlSessionAdmissionPolicy`，使用 Asia/Shanghai 精确时间；截止时刻本身允许，之后拒绝；未配置截止时刻时才回退到活动结束日末。
- Production Guard：生产启动拒绝空 Redis 密码、无 TLS 且无显式私网例外、超长 Token、有模板值或非 HTTPS 的已启用 OSS 配置；启动后继续核对数据库选择的 OSS provider 与环境声明一致。

## 生产配置要求

环境变量清单已补入 `.env.example`。`QINGLIFE_OSS_PROVIDER` 必须与数据库 `sys.oss.cloudStorageService` 一致；未使用 OSS 时两者均应为 `disabled`/空。Redis 默认要求 TLS；仅对已核实的回环或 RFC1918 地址，才可显式设置 `QINGLIFE_REDIS_ALLOW_INSECURE_INTERNAL=true`，且密码仍不能为空。

## 已验证

- Maven `mysql-it`：21 项系统单测、24 项 Web 单测、25 项隔离 MySQL 集成测试通过。
- 小程序根测试通过，包括 10 组本地真实 HTTP 生命周期用例。
- 管理端请求安全测试和新增鉴权存储契约测试通过。
- 管理后台 staging 构建成功；仅保留既有 bundle size 警告。
- `git diff --check` 通过。

## 仍需上线环境验收

- 未连接生产或 staging，也未执行真实 OSS、加密 Redis、微信真机和生产构建包回归。
- `sessionStorage` 缩短了 Token 暴露窗口，但仍可被同源 XSS 读取。彻底改为 `HttpOnly + Secure + SameSite` 服务端 Cookie 需要同时设计 CSRF、跨域/代理和富文本净化，不能只改 Cookie 标志；该迁移应作为独立安全变更验证后上线。
