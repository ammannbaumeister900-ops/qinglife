# 轻生活多人验收交接（2026-10-12）

## 固定入口与版本

- 小程序 AppID：`wxe21e6518a440d613`；已上传版本 `1.4.19-uat.1012`。管理员在本轮对话确认已设为体验版，完成成员与合法域名核对；尚未完成多人实体微信扫码验证。
- 后台：https://qinglife.guijianju.cn/uat-20261011-admin/ 。报名及收款入口：`活动运营 → 报名与付款`。
- API：https://qinglife.guijianju.cn/uat-20261011-api 。独立数据库：`qinglife_uat_20261011_01`。
- 版本、产物哈希、36项迁移及验证边界见同目录 `uat-longterm-20261012.json`。源代码基于未提交工作树；用户已同意仅推送审查范围至独立验收分支，正在同步，旧HEAD不代表完整验收版本。
- 使用已有微信身份；无需清除缓存身份、重新注册或重置已有报名。开发电脑关闭不影响远端API、MySQL、Redis与媒体服务。

## 体验成员与二维码

管理员在微信公众平台选择本AppID，在「成员管理 → 体验成员」按微信号添加业务人员，并由成员按平台要求接受邀请。在「管理 → 版本管理 → 体验版」下载体验二维码，分发同一二维码。体验版首页为 `pages/home/index`；不提审、不正式发布。

本轮公众平台浏览器访问被安全策略阻止，设体验版/成员/域名由管理员完成。体验二维码须取自该体验版，禁止以开发者工具临时预览码替代；本轮尚未取得平台二维码图片。

已上传包启用合法域名检查。`request` 与 `downloadFile` 使用 `https://qinglife.guijianju.cn`，配置不带API路径。开发工具真实微信登录和报价通过；平台域名配置由管理员确认。

## 测试账号获取

已有后台账号 `uat_admin`（管理员）、`uat_operator`（运营）。密码通过环境所有者受限本机文件 `.tools/uat-20261011/private-credentials.json` 或服务器目录内0600权限的 `private-credentials.json` 获取，由所有者私下交付。不得把该文件、密码、Token或业务导出放进GitHub、文档或日志。按需为不同运营人员配置独立账号与既有角色，不共享管理员身份开展日常操作。

## 运行与备份

独立目录 `/opt/qinglife-acceptance/20261011-uat`。API、DB、Redis容器分别为 `qinglife-uat-20261011-api`、`qinglife-uat-20261011-db`、`qinglife-uat-20261011-redis`，启用 `unless-stopped`，Docker开机启动。DB/Redis无公开主机端口，数据与媒体持久化。

每日北京时间04:20后随机5分钟内执行独立UAT备份，保留数据库、Redis、媒体及受限配置。备份路径 `backups/`，仅root可读，含私密数据，禁止下载到公开目录或提交Git。自动任务不删除旧备份；需按磁盘情况由运维归档保留。已有HTTPS证书自动续期任务保持启用。

SSH登录已有服务器后：

```sh
python3 /opt/qinglife-acceptance/20261011-uat/ops/uat_maintenance.py status
systemctl start qinglife-uat-20261011-backup.service
journalctl -u qinglife-uat-20261011-backup.service -n 50 --no-pager
```

状态命令只输出服务、版本、迁移、证书与备份元信息，不输出密钥或业务行。工具依赖服务器既有私有部署助手，不会初始化或清空数据库。

## 后续验收更新

1. 先确认隔离环境和状态，执行上述备份；保留现有微信身份、报名、取消与收款事实。
2. 对已审批的源码运行现有后端测试/构建、后台构建及迁移流程。只操作上述专属目录/容器/数据库；不运行初始化种子、不复制其他环境数据。更新前后记录JAR、后台index哈希及迁移记录。
3. 从待发布源码执行 `node scripts/package-uat-miniapp.cjs <新版本号>`，生成 `.wechat/uat-<版本号>/miniapp`。该副本固定连接UAT、开启域名检查；日常 `apps/miniapp` 的开发配置保持独立。脚本不覆盖已有版本副本。
4. 用微信开发者工具打开该副本并上传对应版本；管理员在公众平台将其设为体验版，不点提审或发布。只有后端更新且接口兼容时，无需重新上传小程序。
5. 更新服务器 `current-release.json` 和仓库版本记录，注明源码commit、前后端哈希和迁移；至少复验登录、报价、待缴费报名、取消重报与后台收款。Git提交/推送须单独获得同意。

## 现场排查

先记录微信体验版本、账号、期数、操作时间和错误截图，避免在公开Issue贴手机号、私人心声、附件或收款凭据。

在GitHub切换版本记录指定的验收分支/commit，先查 `docs/operations/uat-longterm-20261012.json`，再按链路看：`apps/miniapp/services/business-api.js` → `QlMiniAppController` → `QlMiniAppServiceImpl` / `QlMiniAppRegistrationQuoteService`；后台收款看 `OfflineReceiptDialog.vue` → `QlRegistrationController` → `QlOfflineReceiptService`。应以本轮独立验收分支为准，GitHub旧分支不能替代当前验收源码。

SSH执行状态命令，然后仅查看UAT API日志：

```sh
docker logs --since 15m --tail 200 qinglife-uat-20261011-api
journalctl -u qinglife-uat-20261011-backup.service --since today --no-pager
```

核对时间与接口错误，日志仅在受限终端查看，分享前去除身份/Token/业务内容。不要进入其他数据库排查，不通过清空Redis或删除报名解决故障。遇到证书、磁盘或备份故障联系环境管理员；不要自动采购资源。

## 本轮实际验证

- 开发者工具实际微信登录/服务端换码成功，读取原账号4条报名并核价3800；未修改原账号资料或报名。域名检查开启，UAT request请求通过；downloadFile在管理员确认配置后的既有IDE会话仍提示不在合法域名列表，正在重新获取配置，尚不能认定下载域名实际生效。开发者工具结果不替代体验版实体扫码。
- UAT真实HTTPS/MySQL/Redis，无业务Mock：2个新建、无openid且明确TEST的虚构身份，单人3800、两人7600；各自报名和日常记录隔离；取消后新单待缴费且取消历史保留；A待处理不影响B，两人整单仅一笔到账，A差额结清3700、B整单7600，重复确认拦截。原有身份、报名、取消与财务行按原值指纹核对未变。虚构身份已停用、TEST期次已归档，收款与审计保留；不代表真实到账。
- 测试脚本先后遇到昵称长度与VO字段别名错误，均已纠正并重跑通过，未修改业务实现，失败夹具仅在本轮TEST范围归档。
- 本轮小程序现有全量脚本通过；已有后端最新二进制及36项迁移保持一致，未重复部署或改写迁移。
- 每日备份已安装并立即执行成功；API/DB/Redis远端持久化、开机启动和HTTPS自动续期已检查。未实际关闭开发电脑或重启整台共享服务器。
