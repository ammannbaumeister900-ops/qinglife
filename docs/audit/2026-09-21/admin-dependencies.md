# 后台依赖与请求边界审计（2026-09-21）

## 结论

本轮依赖审计未通过，不能将当前后台标为依赖安全验收完成。`pnpm audit --json` 实际执行返回 1，汇总为 critical 4、high 40、moderate 45、low 6。原始条目、命中版本、路径与修复范围见 `admin-dependency-results.json`。这些是扫描条目数，不代表 95 个已证实可远程利用的产品漏洞。

## 已修复：带凭证的请求目标缺少约束

- 问题：`src/utils/request.js` 给调用方提供的 URL 自动附加 Bearer token；Axios 的绝对 URL 可以覆盖 baseURL。
- 原因：请求封装信任 URL/baseURL，且请求错误回调漏写 `return`，错误可能被吞掉。
- 修复：在附加 token 前，限制 URL 为以单个斜杠开头、无反斜杠/空白/控制字符的相对路径，禁止覆盖配置的 baseURL；返回拒绝 Promise。
- 验证：`node scripts/test-admin-request.cjs` 使用实际 Axios 拦截链和替换后的网络适配器，检查合法请求、免 token 登录、绝对/协议相对/反斜杠/换行地址、baseURL 覆盖，以及失败传播。全部通过，非法请求没有进入适配器。
- 同时执行 `node scripts/test-admin-download.cjs` 通过。下载 URL 来自固定环境配置，文件名作为 params 传递。
- 可利用性边界：已检查 API 模块的当前请求定义，未证明外部攻击者能控制完整请求 URL；本修复收紧凭证边界，不声称已经发现实际泄露，也不能替代依赖升级。
- 上游依据：[Axios 官方安全公告](https://github.com/axios/axios/security/advisories/GHSA-jr5f-v2jv-69x6)。

## 仍需处理

| 范围 | 真实代码情况 | 原因及修复路径 | 建议执行者 |
| --- | --- | --- | --- |
| Axios 0.21.0 → 0.34.0（本轮已升级） | 浏览器请求与下载实际使用；扫描命中 25 条公告 | 部分是仅 Node HTTP 适配器问题，部分涉及浏览器/原型污染。已保留地址限制并完成隔离升级、构建及拦截链回归；仍需真实后台浏览器登录、分页、导出、上传验收 | Luna MAX：边界明确但兼容性较多 |
| Quill 1.3.7 | 全局注册 EditorLocal，使用 pasteHTML 载入保存的 HTML | 追踪富文本保存、读取及最终展示的清洗边界；依据实际支持的标签建立清洗与恶意内容测试，再选择编辑器升级/替换 | Sol High：跨前后端内容信任边界 |
| highlight.js 9.18.5 | 代码生成页直接高亮并 v-html 展示结果 | 升级时处理旧 import 路径、API，验证生成代码预览与超长输入；确认生产是否开放生成器 | Luna High |
| ECharts / zrender | 仪表盘和缓存监控使用 | 检查服务端返回值是否进入 HTML formatter；升级并回归图表 API、主题和交互 | Luna MAX |
| js-cookie 2.2.1 | 登录凭证存取依赖 | 升级后回归 set/get/remove、路径及退出清理；审查调用方是否传入不可信属性 | Luna High |
| Vue 2、Webpack 4、Vue CLI 4 及构建链 | 已有老项目依赖；部分构建工具被放在 dependencies，审计 dev=false 不能直接当作浏览器运行风险 | 区分实际打包与构建执行路径，在隔离安装中升级。Vue 主版本迁移是独立工程任务，不能直接覆盖版本号后宣称完成 | Sol High 制定兼容路径，明确改动再交 Luna |

已在独立目录将 Axios 从 0.21.0 升级到 0.34.0，更新 package.json 与 pnpm-lock.yaml；未修改共享 node_modules。升级候选实际 staging 构建及同一请求拦截回归通过。升级后扫描 Axios 告警为 0，剩余 critical 4、high 29、moderate 32、low 5（共 70 条）；见 admin-dependency-after.json。其他依赖仍未完成修复。需避免多个工作树的 junction 相互污染。正式发布静态构建产物，不能把开发服务器当作生产服务器；这也不等于构建依赖风险已消除。

## 验收范围

本报告是新增发现与已完成局部修复的记录。后台依赖治理、真实浏览器/微信联调及最终上线验收尚未完成，主分支合并和生产部署不能据此判为就绪。