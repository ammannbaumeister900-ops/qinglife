# 轻生活数字化运营系统

轻生活完整业务系统 Monorepo，统一维护微信小程序、运营管理后台、Java API、数据库迁移和产品文档。

当前小程序 UI 的唯一有效源码是正式开发目录 `F:/轻生活项目/轻生活/qinglife` 当前工作树中的 `apps/miniapp`，开发分支为 `dev/miniapp-current-20261007`。当前版本入口见 [小程序当前开发基线](docs/operations/miniapp-current-baseline.md)；[2026-10-05 阶段性说明](docs/operations/ui-baseline-20261005.md)保留为历史记录。修改前确认 branch、HEAD 和 git status，读取当前页面文件；旧工作树、归档分支与临时可视化稿不得替代当前源码。密钥、验收账号、数据库备份、用户资料或本地构建产物不得纳入版本归档。

## 首页 V2 与认知页阶段版本索引（2026-10-15）

| 工程成果 | 本地状态 |
| --- | --- |
| 首页 V2 | 品牌首屏、条件待办、真实营期原位切换、四卡心声和文章入口已实现 |
| 独立认知页 | 五段叙事、可替换人物与摄影、历史统计、健康风险、游客访问及分享入口已实现 |
| 配套只读状态修复 B | 人工配置、活动阶段及报名窗口分开表达；本地测试完成，尚未部署 |
| 阶段结论 | **工程开发完成，最终业务验收待确认**；不代表正式上线 |

归档候选分支为 `checkpoint/home-conversion-20261015`，源码取自当前本地工作树；拟复用已提交 UAT 快照 `76db662` 作为归档父版本（待审批）。尚未创建分支或提交，待审批后仅归档两页及必要依赖；其他未提交成果继续保护。

完整 V2 验收设计与内部候选仍是设计基线；公开内容配置仅接入已审核、获授权内容，不能以公开配置暂缺内容的状态代替完整设计。真实营期复用既有后台，集合时间不从日程开始时间推测；人物全文未配置时隐藏入口。

共享文件须按补丁拆分，不整文件纳入收款、重新报名、账号管理或数据库迁移改动。隔离候选的52个小程序测试文件、180项Java单元/打包及19项状态边界均通过；正式提交前仍复核实际暂存树。公开源码中的照片再分发权限、正式内容审核与微信真机验收待确认。详见 [当前开发基线](docs/operations/miniapp-current-baseline.md)。

## 仓库结构

```text
qinglife/
├─ apps/
│  ├─ miniapp/          # 原生微信小程序与工作人员移动工作台
│  └─ admin/            # Vue 2 运营管理后台
├─ server/
│  ├─ api/              # Spring Boot / MyBatis Plus 后端 API
│  └─ migrations/       # 数据库增量迁移（以目录及应用记录为准）
├─ packages/
│  └─ shared/           # 待逐步抽取的共享业务契约
└─ docs/
   ├─ product/          # 产品范围、档案与工作台设计
   └─ operations/       # 测试环境验收与版本记录
```

## 当前实际范围

### 微信小程序

- 首页、清体营、轻读、我的四个主要频道，另有独立认知转化页。
- 微信登录、公开期次、报名与参与进度、个人历史和旧内容只读回看。
- 原生四步轻友登记、敏感信息授权、本地草稿和本人历次登记记录。
- 工作人员移动工作台：报名、付款、签到、服务记录等内部操作入口。
- 保留本地 Demo 与回归测试能力；真实能力由 `services/business-api.js` 和测试环境 API 配置启用。

### 运营管理后台

- 轻友统一档案、详情时间线、历次登记、筛选统计和双表导出。
- 活动期次、报名、卡次/现金结算、结算撤销与重新确认、活动签到；改期本次不开放。
- 工作人员配置及后台账号、角色和权限管理。
- 当前左侧导航采用一级菜单常驻、二级菜单原位折叠、蓝绿色选中态的新版样式。

### Java API 与数据库

- 后台登录鉴权、轻友档案、历次登记、统计导出、期次、报名、交易、签到、服务记录和工作人员工作台接口。
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

当前工作树保留 .github/workflows/ci.yml，拟用UAT父版本已提交该工作流。CI使用合成测试数据、受管凭据与锁定依赖；环境变量私值、数据库备份、客户材料与本地验收产物不得加入流水线。CI通过不代表正式发布。
## 安全约定

- `.private/`、`.env`、证书、日志、数据库备份和测试截图禁止提交。
- 仓库中的后端配置已改为环境变量引用；示例默认值仅用于本地开发。
- 正式环境发布、数据库迁移和历史数据处理必须单独备份并保留回退记录。
