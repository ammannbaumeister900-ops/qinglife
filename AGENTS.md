# qinglife 工程约束

## 核心原则

- 单文件职责清晰，禁止持续膨胀；按业务职责和依赖判断，不设机械行数门槛。
- 业务规则唯一来源，禁止重复实现。
- 跨层依赖必须收敛，禁止绕过边界。
- 修改必须补验证，禁止破坏既有行为。
- 先复用再新增，禁止复制粘贴式开发。

## 项目边界与复用

- 以真实代码、调用链和 schema 为准。仅做当前任务所需的最小改动；不引入无必要的依赖、框架或抽象，不顺带重构无关代码。
- `apps/miniapp` 是原生微信小程序及工作人员工作台；页面通过 `services/business-api.js`、`services/staff-api.js` 调用业务 API，复用 `utils`，不要另起请求、身份或业务规则实现。Demo/旧接口不得作为真实写操作失败后的成功兜底。
- `apps/admin` 是 Vue 2 后台；复用 `src/api/life` 和 `src/utils/request.js`。前端校验用于交互，价格、名额、状态流转、身份与权限最终由后端判定。
- 后端入口在 `server/api/box-web`，轻生活业务在 `box-system` 的 `com.yicai.life`。新增调用沿 Controller → Service/Policy → Mapper/数据访问；Controller 不直接查询数据库或拼装业务状态。通用模块不反向依赖业务模块。已有 Service 内联 SQL 是存量边界，不向新职责扩散。
- 报名/名额/状态流转复用 `QlRegistrationPolicy`、`QlSessionAdmissionPolicy`，定价复用 `QlSessionPricing`，卡次复用 `QlPassService`，客户身份复用 `QlCustomerIdentityService`。先查已有能力及调用者，再决定扩展；独立的新职责放入对应领域的现有模块，确有需要才增加小组件，不制造万能工具类。
- 修改接口时同时搜索后端、小程序和后台调用者，核对字段、错误码与历史兼容。`packages/shared` 当前只是契约规划，不假设已有运行时共享实现。
- schema 变更先核对 `server/migrations` 与 `box-system/src/main/resources/db/migration` 的现有机制；增量演进，不改写已发布迁移，也不为同一业务事实增加第二套状态来源。数据库验证只使用全新隔离库。
- 小程序员工使用 `appToken` / Redis / `ql_staff_access` 身份链，后台使用 `SysUser` 权限链，二者不能互相替代。新增敏感操作检查动作、目标资源和字段范围，缺省拒绝；不得仅凭登录或前端按钮放行，不擅自扩大历史授权或附件访问范围。

## 复杂度热点：原则上只减不增

以下文件均位于 `server/api/box-system/src/main/java/com/yicai/life/`。允许聚焦的缺陷修复与必要的兼容修改；新增业务职责、跨领域依赖或另一套规则实现不能继续塞入热点。

| 文件 | 必须守住的职责边界 |
| --- | --- |
| `service/impl/QlRegistrationServiceImpl.java` | 聚焦报名生命周期及既有收款/结算编排；复用 Policy、Pricing、PassService，不继续吸收客户档案、内容或入口专属逻辑。 |
| `service/QlStaffWorkspaceService.java` | 聚焦员工入口校验与工作台编排；报名、确认、付款继续委托 `IQlRegistrationService`，不新增复制规则的直接 SQL 写路径，不继续聚集新领域。 |
| `service/impl/QlMiniAppServiceImpl.java` | 聚焦小程序入口编排；复用身份、报名策略、定价、习惯等现有服务，不把新的领域流程或后台能力内嵌进来。 |
| `mapper/QlMiniAppMapper.java` | 聚焦数据访问；不承载鉴权、状态决策或工作流，不再作为所有新领域 SQL 的收纳处，优先使用对应领域 Mapper。 |

- 修改热点前说明本次涉及的职责、规则来源和新增依赖；若确需增长，在改动说明中给出复用/独立组件不适用的具体理由及验证证据，不以“文件还不够长”为理由。
- 小范围职责移出应保持调用方向、事务边界、锁顺序和幂等行为；不要只是把代码搬进新的大文件，也不要用更多布尔开关、通用 Map 字段或双向 Service 调用隐藏耦合。相关技术债仅在当前任务必要且可验证时处理。

## 最小验证

修改前查已有测试；行为改动补必要的小型回归用例，覆盖原行为和本次缺陷/边界。新增逻辑应可测试、可替换。文档与配置改动验证对应配置/命令，不为凑数量增加测试。不得通过跳过测试、放宽规则或忽略失败让检查变绿。

现有命令（除 Maven 外在仓库根目录执行）：

```sh
# 小程序统一发现入口：覆盖 apps/miniapp/tests/*.test.js；无额外依赖安装
npm run test:miniapp

# 后台只使用已提交的 pnpm 锁文件，不生成另一套锁文件
pnpm --dir apps/admin install --frozen-lockfile
# 全量 lint 仅用于历史债务盘点；CI 只检查本次变更文件
pnpm --dir apps/admin run lint
pnpm --dir apps/admin run build:prod

# JDK 8；由 reactor 编译依赖并运行 box-system / box-web 等模块单元测试
cd server/api
mvn --batch-mode --no-transfer-progress -pl box-web -am test
```

- 本地按影响范围执行检查；后端接口/依赖/编译改动还应运行 `mvn -pl box-web -am package`。SQL、事务或并发改动需按现有说明补隔离集成验证；`mysql-it` / `http-it` 是独立集成 profile，基础 CI 不开启它们，也不连接生产或已有验收库。
- `.github/workflows/ci.yml` 在 Pull Request 和 main push 执行小程序测试、Java 单元测试、后台生产构建及变更文件 lint，均为阻断检查。Admin 仅检查本次新增/修改的 `apps/admin/src` 下 JS/Vue 文件（含重命名，警告也视为失败）；历史 lint 债冻结，新债不允许进入，未改文件的历史错误不阻塞 CI。只验证，不部署；工具版本以工作流为准。
- 本地检查、GitHub CI、隔离集成、微信真机和业务验收分别报告；逻辑测试或构建通过不能代替真机/业务验收。历史失败说明原因，不扩大当前任务范围。

## 交付与 Git

- 保留用户已有未提交修改；不自动 commit、push、merge 或部署。安全、权限、数据库、登录、支付等修改优先独立分支和 PR；涉及生产数据、不可逆迁移、真实资金或重大架构调整，先明确授权与范围。
- 密钥、Token、密码、真实用户数据、本地工具和测试/构建产物不得入 Git。
- 完成前检查 `git status`、`git diff`（包括新增文件），确认无无关改动和调试残留；简述改动、原因、实际验证结果及未验证项。

## 小程序 UI 唯一基线

- 正式开发目录为 `F:/轻生活项目/轻生活/qinglife`；该目录当前工作树中的 `apps/miniapp` 是小程序 UI 唯一有效基线。其他工作树只用于历史核对或独立任务，不自动作为设计或实现基线。
- 每次 UI 修改前先确认当前 branch、HEAD 和 `git status`；不得在 detached HEAD 上继续设计修改。
- 修改页面前必须完整读取当前工作树中的实际 WXML / WXSS / JS，并核对所用公共组件。
- 禁止使用历史 commit、tag、archive branch、旧设计稿或 visualization 作为实现基线，除非用户明确要求。临时 HTML、截图和对比数据仅是辅助产物。
- 如果发现多个候选版本，停止页面修改并先报告，不自行选择旧版本或用旧文件覆盖当前源码。
- 整理前尚未适配新功能基线的 UI / 审计改动保存在 `archive/pre-baseline-consolidation-20261007`；归档不代表已迁移、已废弃或可直接覆盖当前页面。

## Qinglife Design Skill 版本源

- 正式采用 `qinglife-design` V2。仓库内 `docs/design/qinglife-design/`（含 `SKILL.md`、`references/`、`tools/`）是唯一版本化源（canonical source），设计规则与引用文件以该完整包为准。
- Codex 自动发现入口为 `.agents/skills/qinglife-design`；工作区上级目录的同名入口及外部 `F:/轻生活项目/轻生活/qinglife-design` 均为指向上述规范源的目录联接，不维护独立副本。新会话可用 `$qinglife-design` 显式调用，或按描述自动触发。
- 后续只修改仓库内完整 Skill 包；目录联接即时共享同一内容。备份、评审输入包及历史上下文资料包不是活动版本，不从中加载规则或覆盖当前规范源。
