# 真实 HTTP / Redis 登录验收

## 验证范围

`LoginHttpIT` 启动随机端口的完整 Java HTTP 服务，使用实际 Spring 安全过滤链、业务服务、数据库事务、MySQL 和 Redis。仅 `QlWechatSessionClient` 使用测试替身，不访问微信服务器。

覆盖登录发放有期限的令牌、身份绑定、个人概览、令牌撤销/过期、账号停用、数据库异常不发令牌，以及文档/文件/管理接口的匿名访问拦截。停用账号重新登录的业务码为 403，失效令牌访问个人概览为 401；断言检查响应 JSON 的业务码，不能把它说成 HTTP 状态码。

## 运行条件

- JDK 8、Maven、独立 MySQL 8 测试实例。
- 每次创建一个空数据库，名称必须为 `qinglife_it_http_` 加小写字母、数字或下划线。迁移由测试自动执行。
- 独立、无密码、仅通过回环地址访问的临时 Redis。不能指向已有业务 Redis：测试会写入、枚举、过期和删除 `appToken:*` 测试键。
- 当前测试 MySQL 固定使用本机隔离实例的 root/空密码；不是生产数据库配置范例。

在仓库根目录设置：

```powershell
$env:QINGLIFE_HTTP_MYSQL_URL='jdbc:mysql://127.0.0.1:33317/qinglife_it_http_example?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai'
$env:QINGLIFE_TEST_REDIS_PORT='16379'
mvn -f server/api/pom.xml -Phttp-it -pl box-web -am verify
```

`http-it` 只选择 `*HttpIT`；原 `mysql-it` 排除这些用例。不要把两个 profile 同时启用。单元测试仍正常执行。

若 Redis 位于远端，使用仅绑定远端 `127.0.0.1` 的临时容器和 SSH 本地端口转发。隧道应开启 `ServerAliveInterval`，并在 Maven 完成后清理本轮创建的容器、网络、隧道和临时密钥副本。不要修改已有服务或原始密钥权限。

## 证据与边界

Maven Failsafe 报告位于 `server/api/box-web/target/failsafe-reports`；只发布去除环境属性和日志正文的结果摘要。

这组测试采用 `dev` profile。它不证明生产配置和部署正确，不覆盖真实微信登录、真机邀请链接、历史业务库迁移或全部业务页面。数据库失败用例主动制造 SQL 异常，因此日志中出现该异常并不自动代表用例失败，应以 Failsafe 结果为准。
