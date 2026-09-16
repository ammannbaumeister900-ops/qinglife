# Terra 预览包 UI 复核

## 保留内容

- `apps/miniapp/pages/mine/index.wxml` 和 `index.wxss`：保留“我的”页工作人员入口的预览样式；入口仍受既有 `staffAccess` 控制。
- `apps/miniapp/staff/workspace/index.js`、`index.wxml` 和 `index.wxss`：保留工作人员移动工作台的视觉层级、档案头像、附件汇总标签、到场汇总及卡次只展示的文案和样式。
- 附件标签仅汇总既有回访记录中的图片，使用既有图片预览能力；未增加后台附件接口或数据模型。
- 卡次界面仍明确为只展示，未提供移动端核销或扣次操作。

## 环境处理

- 未带入预览包的 `config/runtime.js`：源码继续使用 `environment: 'demo'` 和空业务地址。
- 未带入预览包的 `project.config.json` 名称/描述改动；未处理或提交预览包独有的 `project.private.config.json`。
- 未覆盖仓库独有的 `test-artifacts`；未修改 server 或 admin。

## 兼容修复与验证

- 工作台期次卡片曾对所有期次复用第一期报名统计。现在每个当前期次分别请求既有报名读取接口，并将本期的全部、待确认、待收款数量放在对应卡片上；期次页默认名单仍使用首期，保持既有导航表现。
- 新增 `staff-workspace.test.js` 覆盖两个期次各自统计的行为回归。
- 已运行 `node scripts/test-miniapp.cjs`，全部通过：生命周期 10 组、工作人员工作台、静态契约及其余 miniapp 回归均通过。

## 限制

- 本次为 Node 静态/行为回归验证，不包含微信开发者工具、真机或浏览器验收。
- 工作人员入口仍依赖现有身份和 `staffAccess` 判定；本次没有变更权限或 API 契约。
