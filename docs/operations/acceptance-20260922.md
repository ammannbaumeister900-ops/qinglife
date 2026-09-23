# 统一验收候选版：acceptance-20260922

## 唯一版本

GitHub main 是三端唯一验收主线；标签 acceptance-20260922 固定本次候选源码。轻友端、工作人员移动工作台和运营后台必须从同一标签检出，不混用历史预览包或 collab/release 分支。

| 组成 | 唯一源码入口 |
| --- | --- |
| 轻友端（四 Tab） | apps/miniapp，微信开发者工具导入该目录 |
| 工作人员移动工作台 | apps/miniapp/staff/workspace，与轻友端属于同一个小程序，按工作人员权限进入 |
| 运营后台 | apps/admin |
| 三端公共 API | server/api/box-web |
| 数据库初始化和增量 | server/bootstrap、server/migrations；通过 scripts/migrate-database.ps1 执行，另含工具自动纳入的 V1_3_10 |

本次包含 release/readiness-20260915 全部历史、整合修复及指定 qinglife-four-tab-staging-preview-20260916 预览包 UI 增量。旧本地工作树及其未提交修改是待归档资料，不是另一套正式验收版本，不能删除其中尚未保存的独有内容。

## 启动与配置

- 后台：在 apps/admin 执行 pnpm install --frozen-lockfile，再执行 pnpm run build:stage。产物 dist；staging 使用 /test-admin/ 与 /test-api。
- 小程序：默认 runtime.js 是 demo，空接口地址不能作为真实业务验收。真实联调须将 environment 配置为 staging，businessBaseUrl 指向部署了本标签 API 的 HTTPS 入口；legacy 接口仅在明确需要时配置。不要将密钥写入小程序或仓库。
- API：Java 8、MySQL 8、Redis；环境变量和启动方法见 README、scripts/start-production.ps1 及 docs/operations/database-migration-20260916.md。
- 全新隔离库可以使用迁移工具初始化；已有旧库不得直接当作空库执行，仍需完成经过验证的基线接管。
- 网关参考 server/operations/nginx/qinglife-test.locations.example。API context 和公共图片地址需配套；本标签尚未部署到现有测试服务器。

## 三端验收清单

1. 轻友端：四 Tab、微信登录、报名、参与进度、每日记录、14/21 天计划暂停/恢复与到期；漏记不延长，暂停当天不计入。
2. 移动工作台：工作人员权限、报名/付款/签到、轻友详情、访谈图片、预览包保留的界面及每期次汇总。
3. 后台：登录与权限、档案、期次、报名/付款/取消、签到、两个编辑器图片上传与展示、本人导出下载。
4. 跨端：同一报名的状态/名额/付款一致，重复请求与无权访问被拒绝。

卡次本轮按最新 P0 口径验收：电脑后台可开户、增加或更正并保留不可变流水；工作人员可在已确认报名上选择归属正确的可用账户完成扣次结算；多人报名只能扣报名发起人的账户，不能扣同行参与者账户，单人后台报名扣实际参与者本人账户；轻友端只展示本次消耗记录与当前剩余次数。余额不足、账户归属错误、重复确认均必须失败且不能留下部分流水。改期接口已关闭，不作为已交付能力。

## 已有验证及待验收

- 2026-09-21：31 项 Java 单元、25 项真实 MySQL、14 项真实 dev/prod HTTP 测试通过；微信换码为模拟，不代表微信真机验证。
- 2026-09-22 上传修复：32 项 Java 单元、编辑器行为测试、后台 staging 构建通过。该局部修复后未重跑全部 MySQL/HTTP，不能把前一日结果称为本标签全量重跑。
- 2026-09-23 P0 本地候选：卡次迁移在全新隔离 MySQL 8 数据库成功执行；24 项 Web/API 单元、21 项 system 单元、30 项真实 MySQL 集成测试、小程序全套测试（含 10 组真实 HTTP 生命周期）通过；后台 staging 构建通过，仅有既有体积告警。以上仍是本地候选证据，尚未提交到标签、部署测试环境或完成微信真机验收。
- 小程序测试与后台请求/下载/编辑器回归由 scripts 下统一脚本执行。
### 2026-09-23 P0 旧库演练与本轮复核

- 在独立 MySQL 8.4 临时实例中，从 `legacy-schema.sql` 和 V1.3.12 以前全部迁移重建旧结构；填入 5 个旧轻友、独立单人和两人报名、两笔真实收款、旧免付记录、旧卡次发放与消耗、共用手机号及无手机号档案，再从干净旧结构副本执行 V1.4.0、V1.4.1，均成功。旧单人实收 70/报价 80、旧整单实收 120/报价 150 被正确分开；免付记录未伪装成现金结算。5 条报名、2 笔收款和原有 3 次卡余额保留；两名同行参与者仍是独立档案，历史联系人手机号仍为空，共用手机号未合并。只使用本轮创建的隔离库，结束后删除。
- 并发数据库演练证实：事务已建立旧快照时，普通余额聚合仍读到 3 次，锁定读取看到另一事务提交后的 2 次；卡次服务已改用账户锁之后的流水锁定读取。故障注入时，卡次流水和订单结算一起回滚，余额仍为 2 次、订单仍待确认。此项先由 SQL 级演练发现；随后新增 Java 服务层故障注入集成测试，证实结算日志写入失败时卡次流水和结算状态同时回滚，撤销故障后可成功重试。
- `node scripts/test-miniapp.cjs` 通过，失败 0；其中 10 组本地真实 HTTP 生命周期场景通过。`node scripts/test-admin-auth.cjs`、`node scripts/test-admin-editor-upload.cjs`、`node scripts/test-admin-download.cjs`、`node scripts/test-admin-request.cjs` 均通过。`node node_modules/@vue/cli-service/bin/vue-cli-service.js build --mode staging` 在 `apps/admin` 执行成功，仅 2 个资源体积告警。
- 已尝试 `mvn -pl box-web -am test -Dmaven.repo.local=.tools\m2` 和 `mvn -Pmysql-it -pl box-web -am verify -Dmaven.repo.local=.tools\m2`；补装前本机无可用 Maven/JDK，命令在启动前失败；补装后的 Java 实测结果见下条。后台 `pnpm --dir apps/admin run build:stage` 因自动依赖安装脚本策略退出，改用已安装构建器直接构建成功。
- 2026-09-23 在本工作区被忽略的 `.tools/` 安装 Azul Zulu JDK 8.0.504、Maven 3.9.9、MySQL 8.4.11；JDK SHA-256 与 Azul 认证文件一致，Maven SHA-512 与 Maven Central 一致，MySQL 官方 GPG 签名通过。使用工作区内的 Maven 与 `.tools/m2` 缓存，在 `server/api` 执行 `mvn -B -ntp -pl box-web -am test`：最终重跑 system 21/21、web/API 26/26，合计 47/47，失败 0；执行 `mvn -B -ntp -Pmysql-it -pl box-web -am verify`：LoginConcurrencyIT 9/9、RegistrationReadinessIT 22/22，合计 31/31，失败 0。MySQL 仅监听 127.0.0.1:33317；两轮测试分别创建 `qinglife_it_p0_20260923_jdk*` 和 `qinglife_it_p0_20260923_rollback*`，共四个隔离测试库均已删除，实例已关闭。完整日志在 `.tools/java-unit-final.log` 和 `.tools/mysql-it-rollback.log`。
- 新增 Spring 方法权限代理回归 2/2：无卡次/结算权限者读取、开户、调整和确认结算均被拒绝，服务层未被调用；仅持有卡次查询权限者也不能开户。定向测试日志 `.tools/permission-test.log`；该测试验证控制器方法权限，未替代测试环境真实 HTTP 403 与角色菜单验收。
- 本轮重新执行 `node scripts/test-miniapp.cjs`：13 个测试文件均通过，其中 TAP 用例 14/14、真实本地 HTTP 生命周期 10/10 组，失败 0；`node scripts/test-admin-auth.cjs`、`node scripts/test-admin-editor-upload.cjs`、`node scripts/test-admin-download.cjs`、`node scripts/test-admin-request.cjs` 均退出 0；在 `apps/admin` 执行 `node node_modules/@vue/cli-service/bin/vue-cli-service.js build --mode staging` 成功，仍有 2 个既有资源体积告警。对应日志保存在 `.tools/miniapp-regression.log`、`.tools/admin-*.log` 和 `.tools/admin-build.log`。
- 以上仍为本地合成数据和构建复核；真实旧库备份恢复、微信真机、测试环境联调、生产验收仍待完成。
- 仍待处理：依赖审计剩余告警、旧库正式基线接管、网关切换/历史图片、微信正式域名和真机验证、完整私有附件产品。

用户已于 2026-09-22 明确要求合并 main，故该版本用于统一验收；合并不代表生产验收通过，不自动部署服务器。详细记录见 docs/audit/2026-09-21/integration-status.md 和 docs/audit/2026-09-22/public-media-upload.md。