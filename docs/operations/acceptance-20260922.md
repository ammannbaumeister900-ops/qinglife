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

卡次核销本次不开放，只验收余额展示。改期接口已关闭，不作为已交付能力。

## 已有验证及待验收

- 2026-09-21：31 项 Java 单元、25 项真实 MySQL、14 项真实 dev/prod HTTP 测试通过；微信换码为模拟，不代表微信真机验证。
- 2026-09-22 上传修复：32 项 Java 单元、编辑器行为测试、后台 staging 构建通过。该局部修复后未重跑全部 MySQL/HTTP，不能把前一日结果称为本标签全量重跑。
- 小程序测试与后台请求/下载/编辑器回归由 scripts 下统一脚本执行。
- 仍待处理：依赖审计剩余告警、旧库正式基线接管、网关切换/历史图片、微信正式域名和真机验证、完整私有附件产品。

用户已于 2026-09-22 明确要求合并 main，故该版本用于统一验收；合并不代表生产验收通过，不自动部署服务器。详细记录见 docs/audit/2026-09-21/integration-status.md 和 docs/audit/2026-09-22/public-media-upload.md。