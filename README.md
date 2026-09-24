# 轻生活数字化运营系统

轻生活完整业务系统 Monorepo，统一维护微信小程序、运营管理后台、Java API、数据库迁移和产品文档。

当前唯一 P0 验收版为 **acceptance-p0-20260924-r2**，候选分支为 **release/p0-acceptance-20260924**。后台/API 验收环境和微信预览二维码都由这一版本产生，只是两个验收入口，不是两套版本。三端入口、账号说明和验收清单见 [P0 统一验收说明](docs/operations/p0-acceptance-20260924.md)。

GitHub `main` 仍是上一稳定主线；在本轮微信真机和业务验收通过前，不提前合并。通过后只把上述唯一候选版合入 `main`，不再从其他 P0 分支拼装。仓库不包含生产环境密钥、验收账号、数据库备份、用户数据或构建产物。

## 仓库结构

```text
qinglife/
├─ apps/
│  ├─ miniapp/          # 原生微信小程序与工作人员移动工作台
│  └─ admin/            # Vue 2 运营管理后台
├─ server/
│  ├─ api/              # Spring Boot / MyBatis Plus 后端 API
│  └─ migrations/       # V1.1—V1.3.12 数据库增量迁移
├─ packages/
│  └─ shared/           # 待逐步抽取的共享业务契约
└─ docs/
   ├─ product/          # 产品范围、档案与工作台设计
   └─ operations/       # 测试环境验收与版本记录
```

## 当前实际范围

### 微信小程序

- 首页、轻体营、轻读、我的四个主要频道。
- 微信登录、公开期次、报名与参与进度、个人历史和旧内容只读回看。
- 工作人员移动工作台：报名、付款、签到、服务记录等内部操作入口。
- 保留本地 Demo 与回归测试能力；真实能力由 `services/business-api.js` 和测试环境 API 配置启用。

### 运营管理后台

- 轻友统一档案、详情时间线和服务记录。
- 活动期次、报名与付款、活动签到；改期本次不开放。
- 工作人员配置及后台账号、角色和权限管理。
- 当前左侧导航采用一级菜单常驻、二级菜单原位折叠、蓝绿色选中态的新版样式。

### Java API 与数据库

- 后台登录鉴权、轻友档案、期次、报名、交易、签到、服务记录和工作人员工作台接口。
- MySQL、Redis 与对象存储通过环境变量配置。
- 数据库增量迁移保存在 `server/migrations/`，应用内迁移保存在 API 模块资源目录。

## 仍需明确的边界

- 隔离测试环境已覆盖主要业务闭环，但不等于正式生产发布。
- 小程序仍保留部分演示数据和旧系统只读接口；真机、微信授权和正式域名需要按发布流程验收。
- `packages/shared` 目前只记录共享契约规划，现有 Java/JavaScript 数据定义尚未完成统一抽取。
- 健康相关内容的正式发布仍需审核、版本、风险提示和下架机制。

## 开发与验证

小程序测试只有一个发现入口；在仓库根目录运行下列命令，或在 `apps/miniapp` 运行其 `test` 脚本，都会执行同一组 `*.test.js` 文件。小程序没有独立锁文件，不要为它额外生成 `package-lock.json` 或 `pnpm-lock.yaml`。

```bash
# 小程序逻辑、契约和工作人员图片下载测试
node scripts/test-miniapp.cjs
# 等价入口：npm --prefix apps/miniapp test

# 管理后台：此子包使用已提交的 pnpm-lock.yaml
pnpm --dir apps/admin install --frozen-lockfile
pnpm --dir apps/admin run build:stage

# Java 单元测试与可发布 jar；不执行 mysql-it 集成测试
cd server/api
mvn -pl box-web -am clean package

# 仅在全新、隔离的本机 qinglife_it_* 数据库上执行 MySQL 集成测试
# PowerShell 示例（不要指向生产、staging 或已有测试数据库）
$env:QINGLIFE_TEST_MYSQL_URL='jdbc:mysql://127.0.0.1:33317/qinglife_it_local?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:QINGLIFE_TEST_MYSQL_PASSWORD=''
mvn -pl box-web -am -Pmysql-it verify
```

`.env.example` 仅是变量名和安全默认值的样例文件；Spring Boot 不会自动读取它。请在实际启动进程中注入环境变量（PowerShell 使用 `$env:NAME='value'`，CI/服务管理器使用各自的受管密钥注入），再启动 Java 服务。生产环境必须显式设置 `SPRING_PROFILES_ACTIVE=prod`。

Windows 生产发布统一使用 `scripts/start-production.ps1`。该入口强制设置并复核 `prod` profile，且要求显式传入构建产物；生产所需密钥、数据库密码及 `QINGLIFE_PUBLIC_IMAGE_BASE_URL` 仍由服务管理器注入，不写入脚本或仓库。

仓库当前没有 `.github/workflows` 工作流。添加 CI 前，应使用合成测试数据、受管测试凭证与已锁定的依赖安装，不得把 `.env`、数据库备份或客户材料加入流水线。
## 安全约定

- `.private/`、`.env`、证书、日志、数据库备份和测试截图禁止提交。
- 仓库中的后端配置已改为环境变量引用；示例默认值仅用于本地开发。
- 正式环境发布、数据库迁移和历史数据处理必须单独备份并保留回退记录。
