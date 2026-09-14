# 轻生活 Java API

Spring Boot 2.5 / MyBatis Plus 多模块服务，为运营管理后台、微信小程序和工作人员移动工作台提供鉴权与业务接口。

```bash
mvn -pl box-web -am clean package
```

数据库、Redis、JWT、监控和对象存储凭据必须通过环境变量注入。示例变量见仓库根目录 `.env.example`。
