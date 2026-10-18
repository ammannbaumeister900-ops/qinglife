# 视觉改版 2026Q4 · 验收记录（P6）

> 分支 `feat/visual-2026q4`（基线 dev/m2 @ 3560d5d），2026-10-18。未 commit、未 push、未部署。
> 结论分四级，互不替代：**工程完成 / DevTools 通过 / 真机待测 / 业务待验收**。

## 一、工程完成

| 阶段 | 内容 | 状态 |
|---|---|---|
| P0 | 截图通道 `tools/capture/`（单帧、长图、拼接、窗口摆放）；图片导出 `tools/images/export.py` + manifest | 完成；#50 导出 232.8KB，超 220KB 上限，manifest 标 `overBudget` |
| P1 | `styles/qing-tokens.wxss`、`styles/qing-fonts.wxss`（衬线 114 字 25.2KB + 数字 8.4KB，数字改等宽齐线）、`tools/fonts/subset.py` | 完成 |
| P2 | 第二 Tab 改为「了解轻生活」，清体营降为二级页，`navigation.returnTo / redirectTo` | 完成 |
| P3 | 首页深林首屏、数字日期、心声卡、浅苔色场（#43 于 2026-10-19 获确认）、1.5px Tab 图标 | 完成 |
| P4 | 认知页开场句 / GPS 句 / 食动歇 / 深林数字高点 / 透明说明 / 最近一期小卡 / 行内双按钮 / 分享卡 | 完成；创办时间线未做（founders 为空，无数据结构可依） |
| P5 | 清体营列表期号数字排版、#48 页头（CDN 地址空时不显示）、详情头部与报名栏令牌 | 完成 |
| 追加 | 人物故事「花花一家」：文案经用户 2026-10-18 审定，已合入 `about-content.js`（出处见 `story-candidates/`）；衬线子集随之更新为 114 字 25.2KB | 完成；合影待上传 CDN 后显示 |

护栏核对：模块顺序未变；59.9% 口径完整（标签 17px、口径 14px）；饮食限制与风险块在决策处直接可见；人物 / 照片全部走 `about-presentation` 既有授权校验，#50 / #46 / #43 默认关闭；琉璃光或任何人物故事显示时 #46 自动隐藏（有测试）；未改 `services/`、`utils/domain.js`、`utils/session-state.js`、`config/runtime.js`；报名、付款、登录流程未动。

## 二、DevTools 通过（微信开发者工具 iPhone 12 模拟器，390×844，截图 ≥1046px 宽）

| 项 | 方法 | 结果 |
|---|---|---|
| 单元测试 | `npm run test:miniapp` | 44 个文件，482 项通过，0 失败 |
| 对比度 | `node tools/audit/contrast.js` | 125 条设色规则全部达标（正文 ≥7:1，辅助 ≥4.5:1，汁绿只在深林底）。最低组合：`--q-ink-2` on `--q-field` 5.15:1（辅助），`--q-amber` on `--q-sand` 5.68:1。只靠继承取色、未单独设色的文字未逐条检查 |
| 触控尺寸 | `node tools/audit/touch.cjs` | 当前状态下 18 个可见可点元素全部 ≥44px（修复了首页「重试」30px、清体营「全部」28px）。未显示的状态（如心声控制栏、我的安排）未量到；Tab 栏按样式核算 61px 高 |
| 导航回归 | `node tools/capture/nav-paths.cjs` | P2 的 6 条路径 + 直达详情变体，7/7 通过，页面栈无重复 |
| 字号放大 1.4× | `home-states.cjs` / `about-states.cjs` 设 fontScale 1.4 | 首页、认知页首屏无溢出（截图 p3/、p4/）。清体营页用固定 px，不随该变量缩放 |
| 照片开 / 关两态 | 本机静态服务 + setData 模拟 | 首页 #43、认知页 #50 / #46 / #67、清体营 #48 开关两态版面都成立 |
| 包体 | 原始文件合计（未压缩、换行归一） | 1735.4KB → 1767.1KB（+31.6KB，含故事字体），低于 1.8MB。DevTools「代码依赖分析」的压缩后数字未取得，需在 IDE 里看一次 |
| 窄屏 320px | — | **未完成**：模拟器机型只能在 IDE 界面里切换，CLI / automator 无此能力；样式里 ≤350px 有 20px 边距规则，但未截图验证 |

截图目录：`test-artifacts/visual-2026q4/`（p0–p5、p6-paths、p7-story），对比长图 `p3/p3-compare-long.jpg`、`p4/p4-compare-long.jpg`。

## 三、真机待测（需要你扫码）

- iOS、Android 各看一次首页与认知页：首屏照片清晰度、深林色、衬线与数字字体是否显示。
- Android 若不显示 woff 字体，`subset.py` 改出 ttf；回退为系统字体时版面应仍成立（未验证）。
- 系统字号调到最大时三页是否溢出；320 宽机型（如 iPhone SE 1 代）首屏。

## 四、业务待验收

- 素材账本"需确认"的 3 张已于 2026-10-19 确认：#50 为导游公主、#46 军军同意（背景可用）、#43 为导游大海，均同意公开；配置已打开，填入 CDN 地址后显示。#46 在花花一家故事显示时仍自动隐藏。
- 照片上传 CDN（`outputs/qing-images/`），填入各 `url`，并在小程序后台加 downloadFile 合法域名。#50 先决定：放宽到 240KB，或降到 quality 66 / 1000×1250。
- 人物故事「花花一家」已上线到配置；合影 `qing-about-story-huahua.jpg` 上传 CDN 后填入 `family-huahua.url` 即显示，建议先补原图重导。《连续清体》建议本轮不用（医疗叙事密集，见 `story-candidates/huahua-family.md`）。
- 「带回每一天」食 / 动 / 歇三句为 Brief 初稿（`glyphsCopyState: draft`），需定稿。
- 高清 Logo 到货后放入 `ql-nav-space` brand 插槽；届时可评估移除 436KB 的 `logo-sage.png`。

## 已知限制与遗留

- 首页导航栏用固定深林色条而非 `immersive` 透明层：透明层下滚动到浅色区域时白色状态栏文字不可读。
- 首页结束日期 34px（起始日期 66px）：66px 的"10.01 — 10.03"在 375 宽放不下。
- 详情页导航栏底色仍是旧的 `--ql-nav-bg` 默认值（与正文亚麻底略有色差），改它会影响 camp-flow 其他视图，留待后续。
- 心声卡昵称用 14px（规范写 13px），以满足 ≥14px 的底线。
- `tools/` 不在 CI 中运行；自动化截图依赖本机开发者工具与竖屏显示器（`tools/capture/place-window.ps1`）。
