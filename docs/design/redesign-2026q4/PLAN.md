# 轻生活小程序 · 视觉改版执行规划（给 Claude Code）

> 版本 V1.1 · 2026-10-18（V1.1：接入本地图片库与素材账本；认知页开场句、最近一期、Logo 留空已定） · 适用工程 `apps/miniapp`（基线 dev/m2 @ 3560d5d，冻结版 1.4.22-uat.1017）
> 配套文件：`ART-DIRECTION.md`（视觉规范、令牌、组件与页面规格）
> 放置位置：`docs/design/redesign-2026q4/PLAN.md`

---

## 0. 怎么使用这份规划

1. 把 `PLAN.md` 和 `ART-DIRECTION.md` 放进仓库的 `docs/design/redesign-2026q4/`。
2. 把第 9 节的片段追加到 `apps/miniapp/CLAUDE.md`（或仓库根目录的 `CLAUDE.md`）。
3. 每次只启动一个阶段：把第 8 节对应的"启动指令"粘贴给 Claude Code。
4. 每个阶段结束时，Claude Code 必须停下，交出截图和变更说明。你确认后再进入下一阶段。
5. 全程在 `feat/visual-2026q4` 分支上进行，每个阶段一个 commit，不 push、不部署。

---

## 1. 目标与"惊艳"的验收定义

**目标**：首页和「了解轻生活」认知页要做到第一眼像一个高端疗愈目的地品牌，同时保留现有全部事实边界、业务逻辑和可读性底线。

"惊艳"不靠感觉判断，下面每条都要过：

| # | 验收项 | 判定方式 |
|---|---|---|
| A1 | 首屏遮名测试：遮住 Logo 和"轻生活"字样，仍能认出是这个品牌（深林绿首屏、真实照片、题字） | 首屏截图评审 |
| A2 | 每页恰好一个视觉高点：首页 = 深林首屏；认知页 = 深林色场 + 59.9% 巨型数字 | 长图眯眼测试 |
| A3 | 三个签名全部到位：一次深色色场 / 数字即画面 / 满幅真实影像或单字题眼 | 对照 ART-DIRECTION §3 |
| A4 | 相邻模块不同构：连续两个模块不得都是"标题 + 正文 + 细线 + 文字链" | 长图逐段检查 |
| A5 | 尺度对比：同一屏内最大字号 ≥ 正文的 3 倍 | WXSS 检查 |
| A6 | 和 6.1-sol 版并排比较，你认为有明显提升 | 你的主观评审，一票否决 |

---

## 2. 不可改动的护栏（违反任何一条即退回）

**业务与事实**
- 模块顺序不变。首页：品牌首屏 → 条件待办 → 近期清体营 → 亲友心声 → 了解轻生活。认知页：共鸣 → 创办 → 带回每一天 → 一次次回来 → 透明说明与行动。
- 首页营期切换只原位更新（`chooseCamp`），心声阅读时暂停轮换，公开内容游客可读。
- 59.9% 必须写作「2025年老轻友参营人次占比」，旁边配 18期 / 676 / 405 的口径，字号 ≥14px，可直接读到。
- 2007 年、500 多期照原样使用，不改写成"服务人数"之类。
- 饮食限制（纯蔬菜榨汁代替日常餐食）和风险说明要在决策处直接可见，不折叠，不缩成小字。
- 人物、肖像、原话、创办史实只有在 `about-presentation.js` 的授权校验通过后才显示。不编造，不用导游照冒充其他人物。
- 不改 `services/`、`utils/domain.js`、`utils/session-state.js` 等业务与状态规则，不改报名、付款、登录流程。

**照片**
- 只使用 ART-DIRECTION §7 素材账本里列出的照片，每张只放在指定位置。
- 账本中标"需确认"的照片：代码和样式照常实施，但在 `config/` 中用 `releaseReady: false` 关闭，确认后再打开。
- 榨汁瓶照片只用裁切版，不得出现标签上的手机号和二维码。
- 不用宴会厅场景（现场 #37 #38 #41 #58）、未成年人（轻友 #142）、轻友拼图。
- 原图不进小程序包，也不提交进仓库。

**可读性底线**
- 叙事正文 ≥16px，功能正文 ≥15px，日期 / 地点 / 价格 / 状态 / 风险 ≥14px，行高 1.75–1.9。
- 正文对比度 ≥7:1，辅助文字 ≥4.5:1；状态不能只靠颜色区分。
- 可点区域 ≥44px；固定栏不遮挡最后一项和安全区；字号放大 1.4 倍时不溢出。

**工程**
- 最小改动，不做无关重构。优先复用 `ql-nav-space`、`custom-tab-bar`、`utils/navigation.js`。
- 主包 ≤ 1.8MB（给 2MB 上限留余量）。高清照片走 https CDN，不进包。
- 不 push、不部署、不修改 `config/runtime.js`。

---

## 3. 用户画像（覆盖旧文档里的"中年及以上"）

| 维度 | 描述 |
|---|---|
| 核心人群 | 35–45 岁，一二线城市，中高收入；企业中层、创业者、专业人士 |
| 审美参照 | 安缦 / 松赞（目的地质感）、Kinfolk / Cereal（编辑排版）、观夏（东方克制）、lululemon（身体与生活方式） |
| 进入方式 | 朋友转发，带着"这是什么、靠不靠谱、适不适合我"的问题进来 |
| 决策关注 | 谁带、在哪、哪几天、多少钱、会经历什么、有没有风险、同去的是什么样的人 |
| 反感 | 养生广告腔、疗效承诺、廉价促销感、说教、"适老化"的大字说明页 |

可读性底线照样保留，因为这个人群同样常在通勤路上单手阅读。改变的是气质：从"说明清楚"变成"值得去"。

---

## 4. 已核实的现状事实（以活动工程为准，开工前重新核对）

| 事实 | 位置 |
|---|---|
| Tab 列表有两份，需同步修改 | `app.json` → `tabBar.list`；`custom-tab-bar/index.js` → `data.list` |
| Tab 选中由各页面设置 | home `selected:0`（`pages/home/index.js:29`），camp `selected:1`（`pages/camp/index.js:28`），content `2`，mine `3` |
| Tab 栏只给首页用了新纸色 | `custom-tab-bar/index.wxml` 的 `tabbar-home` 类，仅在 `selected === 0` 时生效 |
| 首页进认知页用的是 navigateTo | `pages/home/index.js:205` `about()`。about 变成 Tab 页后，navigateTo 会失败，必须改成 switchTab |
| 跳清体营用的是 switchTab（共 6 处） | `pages/home/index.js:196`、`pages/about/index.js:24`、`pages/mine/index.js:99`、`pages/personal/index.js:41`、`pages/camp-flow/index.js:522`、`pages/camp-reflection/index.js:301` |
| 认知页有返回键和固定双按钮栏 | `pages/about/index.wxml` 第 1 行 `back`；`.about-actions { position:fixed; bottom:0 }` |
| 清体营页预留了 Tab 栏高度 | `.camp-page { padding-bottom: calc(110px + safe-area) }` |
| 首页首屏照片 | `assets/brand/home-guides.jpg` 1000×667；`.home-hero-image{height:220px}`；`.home-hero{margin-right:22px}` |
| 首页题字字体 | `pages/home/index.wxss` 第 1 行内联 Noto Serif SC 子集（仅含"来坐坐"等少数几个字） |
| 全局衬线 | `app.wxss` 中 `--ql-serif: "Songti SC","STSong","SimSun",serif` |
| Logo 体积 | `assets/brand/logo-sage.png` 1448×1086，446KB |
| 认知页内容配置 | `config/about-content.js`：`founders: null`，`storySlot.item: null`，`assets: []` |
| 现有测试 | `apps/miniapp/tests/`（如 `home-v2-contract.test.js`、`about-presentation.test.js`），运行 `npm test` |
| 本地图片库 | `F:/轻生活项目/轻生活/图片/`：`活动现场照片/`（14 张，9520×6336 等，单张 3–30MB）、`导游高清照片/`（3 张，含首页合影原图 4023×2682）、`轻生活工作人员/`（4 张）、`轻友高清照片（原图）/`（13 张）、`轻友高清照片（拼图）/`（15 张）、`logo/`（与现有 logo 相同，高清版制作中） |
| 照片编号 | 用文件名第三段数字指代，例如 `微信图片_20261008160208_46_483.jpg` 记作"现场 #46" |

---

## 5. 分阶段执行

### P0 · 素材与取证就绪（你 / 业务方为主，Claude Code 辅助）

**目标**：拿到能评审审美的截图，并按素材账本把照片导出成可上线的尺寸。

**Claude Code 做的事**
1. 修复截图通道。现在 DevTools 导出只有 149×321px。用 `miniprogram-automator` 的 `miniProgram.screenshot()`，或者把 DevTools 模拟器缩放设成 100% 后截图，目标是每帧 ≥ 390px 宽（最好 1170px）。把脚本写在 `tools/capture/`，后续每个阶段复用。
2. 写图片导出脚本 `tools/images/export.py`（规格见 ART-DIRECTION §7.2）：
   - 读取 `F:/轻生活项目/轻生活/图片/` 的原图，按素材账本的裁切框和尺寸，导出到仓库外的 `F:/轻生活项目/轻生活/outputs/qing-images/`，供上传 CDN。
   - 同时为首页首屏生成一张 ≤120KB 的本地兜底图，覆盖 `assets/brand/home-guides.jpg`，文件名不变。
   - 榨汁瓶 #67 必须按账本裁切，导出后人工核对一次，确认看不到手机号和二维码。
   - 输出 `manifest.json`：每张图的编号、用途、尺寸、字节数、建议 CDN 文件名。
3. 不做 Logo：高清 Logo 在制作中。首页顶栏的 Logo 位置先留空，`ql-nav-space` 的 `brand` 插槽保留。

**你 / 业务方做的事**
- 把 `outputs/qing-images/` 上传到 CDN（业务域名下的 https 路径），并把该域名加入小程序后台 downloadFile 合法域名。
- 确认素材账本中标"需确认"的 3 张：现场 #50 中的人是否为导游公主；导游军军、大海是否同意 #46、#43 公开使用。

**停点**：交出一张 ≥390px 宽的首页首屏截图（证明截图通道已修好）、`manifest.json`，以及 6 张导出图的缩略拼图。

---

### P1 · 设计令牌 + 字体管线（不改任何页面）

**目标**：搭好新视觉的基础设施，页面暂时不动。

**新增文件**
- `styles/qing-tokens.wxss`：颜色、字阶、间距令牌，内容照抄 ART-DIRECTION §2。只定义变量，不写选择器样式。
- `styles/qing-fonts.wxss`：由脚本生成的 `@font-face`（base64 内联）。
- `tools/fonts/subset.py`：字体子集脚本（规格见 ART-DIRECTION §4）。
- `tools/fonts/README.md`：说明何时需要重新生成（改了任何固定标题文案之后）。

**步骤**
1. 下载 Noto Serif SC SemiBold 和 Cormorant Garamond SemiBold（均为 OFL 许可），放在 `tools/fonts/src/`。源字体不进小程序包，加进 `.gitignore` 或 `project.config.json` 的 `packOptions.ignore`。
2. `subset.py` 从 `config/home-brand.js`、`config/brand-content.js`、`config/about-content.js`，以及 WXML 里的固定标题中抽取汉字，生成衬线子集；数字子集只含 `0-9 . % + – — s`。
3. 输出 woff 格式的 base64，写入 `qing-fonts.wxss`。如果 Android 真机不显示，改用 ttf。
4. 在 `app.wxss` 顶部 `@import` 这两个文件。保留旧变量，避免影响其他页面。

**不做**：不改任何页面的 WXML 或 WXSS。

**验收**
- 两个子集合计 ≤ 60KB，主包总量 ≤ 1.8MB（在 DevTools 的"代码依赖分析"中查看）。
- 在 DevTools 里写一个临时测试 view 验证两款字体都能渲染，验证完删除。
- `npm test` 通过。

**停点**：报告子集大小、包体前后对比、字体渲染截图。

---

### P2 · 第二 Tab 改为「了解轻生活」

**目标**：完成已确定的导航调整。清体营降为二级页，路径完整可回退。

**改动清单**

| 文件 | 改动 |
|---|---|
| `app.json` | `tabBar.list[1]` 改为 `{"pagePath":"pages/about/index","text":"了解轻生活"}`；`pages` 数组保留 `pages/camp/index` |
| `custom-tab-bar/index.js` | `list[1]` 改为 about；图标暂时沿用 sprout，P3 再换 |
| `custom-tab-bar/index.wxml/.wxss` | 把 `tabbar-home` 的样式提升为默认样式，四个 Tab 统一用新纸色（令牌见 ART-DIRECTION） |
| `pages/about/index.js` | 新增 `onShow` 中 `getTabBar().setData({selected:1})`；`learnRegistration` 改为 `navigation.navigateTo({url:'/pages/camp/index'})` |
| `pages/about/index.wxml` | `ql-nav-space` 去掉 `back`；固定栏 `.about-actions` 暂时保留，P4 再改成行内按钮，但要加上 Tab 栏高度的偏移，避免两栏重叠 |
| `pages/home/index.js` | `about()` 改为 `navigation.switchTab({url:'/pages/about/index'})`；`openCamp()` 改为 navigateTo；新增 `openCampList()`，供 P3 的"全部营期 →"使用 |
| `pages/camp/index.js` | 删除第 27–28 行的 Tab 选中设置 |
| `pages/camp/index.wxml` | `ql-nav-space` 加上 `back`。分享直达时，现有 `goBack()` 会回退到首页 Tab，已满足需要 |
| `pages/camp/index.wxss` | 底部 padding 改为 `calc(32px + env(safe-area-inset-bottom))`（不再有 Tab 栏） |
| `pages/mine/index.js:99`、`pages/personal/index.js:41` | switchTab 改为 navigateTo |
| `pages/camp-flow/index.js:522` `backToCamp` | 上一页是 camp 就 navigateBack，否则 redirectTo camp |
| `pages/camp-reflection/index.js:301` | 回退失败时的兜底从 switchTab 改为 redirectTo camp |
| `tests/` | grep `pages/camp/index` 和 `switchTab`，更新受影响的断言 |

**完成后再 grep 一次**：`switchTab.*camp/index` 结果应为 0；`navigateTo.*about/index` 结果应为 0。

**手动走查路径（每条都要截图）**
1. 首页 → 了解轻生活 Tab → 了解报名 → 清体营 → 详情 → 返回 → 返回到认知页
2. 首页 → 全部营期（P2 阶段先用现有空态入口验证）→ 清体营 → 返回首页
3. 我的 → 我的安排（camp）→ 返回我的
4. 分享直达 `/pages/camp/index` → 返回键 → 首页
5. 分享直达 `/pages/about/index` → Tab 栏选中第 2 项
6. camp-flow 详情 → "回到清体营" → 页面栈不重复堆叠

**验收**：6 条路径全部通过；`npm test` 通过；Tab 选中状态在每个页面都正确。

**停点**：交出 6 条路径的截图和 diff 摘要。

---

### P3 · 首页视觉重构

**目标**：做出深林首屏，并按 ART-DIRECTION §6.1 重做各模块。

**改动范围**：`pages/home/index.wxml`、`pages/home/index.wxss`、`config/home-brand.js`（加 CDN 图片地址字段，失败时回退到本地图；新增"了解轻生活"配图字段，带 `releaseReady` 开关）、`custom-tab-bar`（新图标）。
**JS 只允许**：新增 `openCampList()`；为数字日期做的纯展示格式化写在 `utils/home-presentation.js`，例如把 dateLabel 拆成起止日期；可选加一个日期淡入动效的 class 切换。业务逻辑不动。

**步骤**
1. 首屏：`ql-nav-space` 用 `immersive`，Logo 位置先留空（不显示旧 Logo，也不用文字替代）；深林底；题字"有空，/来坐坐"按新字阶；照片换成导游合影原图导出版（CDN 1170×780，本地兜底），满幅 375×250，图注保持原文。
2. 近期清体营：区头加"全部营期 →"；期次改为下划线标签（期号 + 起始日期数字）；数字日期大字；状态 chip 带文字；事实行；唯一的实心主按钮"查看课程介绍"。
3. 亲友心声：卡片加开引号，两种底色交替；控制栏保持现有逻辑。真实数据为空或请求失败时，沿用现有错误态，只换样式。
4. 了解轻生活：整块苔色色场，顶部满宽放现场 #43（导游大海与轻友交谈，`releaseReady` 打开后才显示，关闭时色场直接从文字开始）；"认识轻生活"做成次按钮；精选文章和更多入口收为两行。
5. 首页底部 padding 与新 Tab 栏高度对齐。

**截图要求（≥390px 宽）**：首屏 / 整页长图 / 切换到第二期 / 心声失败态 / 1.4 倍字号首屏 / 320px 宽首屏。

**停点**：截图与 6.1 版并排对比，等你按 A1–A6 评审。

---

### P4 · 认知页视觉重构

**目标**：按 ART-DIRECTION §6.2 把认知页做成有高点的短刊。

**改动范围**：`pages/about/index.wxml`、`pages/about/index.wxss`、`pages/about/index.js`（读取最近一期、分享配置）、`config/about-content.js`、`utils/about-presentation.js`（只加展示字段，不放宽授权校验）。

**已定的内容改动（写进 `config/about-content.js`）**
- `entry.title`：`为身体减负，\n为心灵减压`
- `entry.intro`：`忙着生活，有多久没好好关心自己了？`（Brief 候选稿；如你另有决定以你为准）。原句"轻生活的故事，要从两个忙着创业的人说起"删除，因为创办段未授权，这句会预告一段看不到的内容。创办段获批后，可在时间线上方恢复这句。
- `everyday.motto`（新增）：`轻生活的 GPS：让身体来带路`，显示在"把轻生活，带回每一天"段题下方。
- `everyday.glyphs`（新增）：食 / 动 / 歇三条短句，取自 Brief 初稿，标注 `copyState: 'draft'`。
- 分享标题：`一起来轻生活吧！`（替换"认识轻生活"）；分享图用首页合影的 5:4 导出版。
- `assets`：按素材账本加入现场 #50、#46、#67 三张，带身份、用途、`releaseReady`。#67 可直接设为 true；#50、#46 在你确认人物前为 false。

**步骤**
1. 开场：衬线 40px 两行题字 + 16px 副句；下方满宽放现场 #50（4:5，园林中慢慢走的导游），图注"轻生活导游 · 清体营场地园林"。未确认时不显示照片，副句后直接接下一段。
2. 创办段：仍按现有授权逻辑，`founders` 为 null 时整段隐藏；获批后用排版时间线（1990s / 三十岁 / 2007）。
3. 带回每一天：段题 + GPS 一句 + 导语 + 三列单字题眼 + 现有正文。这一段不配营内照片，因为讲的是回到日常。
4. 一次次回来：满宽深林色场。段题下放现场 #46（导游军军协助轻友放松），然后是人物槽位（琉璃光，现有授权逻辑）、59.9%（数字字体 ≥104px，汁绿）、口径、2007 / 500 多期、定义说明。**琉璃光故事获批显示时，#46 自动隐藏**，避免读者把照片里的人当成她。
5. 透明说明：三行事实 → 榨汁瓶 #67 裁切版（150×188）+ 说明"清体营期间的蔬菜榨汁" → 砂色风险块 → "最近一期"小卡 → 收尾句 → 行内双按钮。
6. "最近一期"小卡（已获批）：在 `onShow` 中通过 `services/business-api.js` 现有的公开营期读取方式拿数据，复用 `utils/home-presentation.js` 的 camps 展示函数取第一期。展示期号、日期（数字字体）、状态、地点和"全部营期 →"。读取失败或没有营期时整卡隐藏，不报错。不新增接口，不改报名规则。
7. 删除固定栏 `.about-actions`。可选：滚动到透明说明段后浮出一个"了解报名"小按钮，位置在 Tab 栏上方。

**截图要求**：首屏 / 长图（照片全部打开）/ 长图（#50、#46 关闭、无最近一期）/ 模拟琉璃光获批的状态（用 about-presentation 测试里已有的 fixture，不写进正式配置，确认 #46 已隐藏）/ 1.4 倍字号 / 320px 宽。

**停点**：等你评审。

---

### P5 · 清体营二级页与详情头部对齐

**目标**：从首页或认知页点进去时，视觉不断档。

**改动范围**：`pages/camp/index.wxss`（换用新令牌）、`pages/camp/index.wxml`（页头加现场 #48 园林步道满宽图 375×200，标题"近期活动"放在图下；期号改为数字排版，去掉 `.camp-template-cover` 渐变海报）、`pages/camp-flow/detail.wxss`（只改头部和底部报名栏的令牌）。有本人安排时，"我的安排"仍在页头图之前，任务优先。
**不做**：不动报名、筛选、我的安排、心声的逻辑，不改报名表的 register 和 journey 视图。

**停点**：列表、筛选、详情头部、报名栏截图。

---

### P6 · 验收

| 项 | 方法 |
|---|---|
| 对比度 | 写 `tools/audit/contrast.js`，扫描三页 WXSS 中的前景 / 背景组合，输出不达标项 |
| 触控尺寸 | 在 DevTools 中用 automator 查询所有 button / bindtap 元素的尺寸，必须 ≥44px |
| 字号放大 | 系统字号设为最大（1.4 倍封顶）时，三页截图无溢出 |
| 窄屏 | 320px 宽截图 |
| 字体回退 | Android 真机截图，衬线回退为黑体后版面仍然成立 |
| 包体 | 主包 ≤ 1.8MB |
| 回归 | `npm test` 全部通过；P2 的 6 条路径重新走一遍 |
| 真机 | 你在 iOS 和 Android 各扫码看一次，重点看首屏照片清晰度和字体 |

结论分四级写：工程完成 / DevTools 通过 / 真机通过 / 业务验收。不混用。

---

## 6. 风险与回滚

| 风险 | 对策 |
|---|---|
| 字体子集在 Android 上不渲染 | 改用 ttf 格式；最终兜底是系统字体，版面必须在回退后仍然成立 |
| CDN 图片加载慢或失败 | 本地保留一张 ≤120KB 的压缩图作为兜底；`binderror` 时回退 |
| about 改成 Tab 后，旧分享卡片路径 | 路径不变，仍可直达，只是变成 Tab 页 |
| 页面栈超过 10 层 | camp-flow 回清体营时优先 navigateBack 或 redirectTo |
| 审美评审不通过 | 每个阶段一个 commit，可以单独 revert；P3、P4 互相独立 |
| 照片人物确认迟迟不到 | `releaseReady: false` 时版面必须照样成立（已列入截图要求），不阻塞上线 |
| 高清 Logo 到货 | 只需在 `ql-nav-space` 的 brand 插槽放反白版（深色首屏用），浅底页面用原色版；不影响其他阶段 |

---

## 7. 决定事项

**已定（2026-10-18）**
- 认知页开场句：「为身体减负，为心灵减压」。「轻生活的 GPS：让身体来带路」用在"带回每一天"段；「一起来轻生活吧！」用作认知页分享标题。
- 认知页显示"最近一期"小卡。
- Logo：高清版制作中，本轮顶栏 Logo 位置留空。
- 素材：使用本地图片库，按 ART-DIRECTION §7 素材账本执行。

**待定（2026-10-18 用户确认：以下各项先按括号内默认值执行）**
1. **方向**：是否采用「林下午后」（默认采用）。
2. **3 张照片的人物确认**：现场 #50 是单人照（园林中行走的一位女士），待确认是否为导游公主；导游军军、大海是否同意 #46、#43 公开使用（默认 `releaseReady: false`）。注意：首页三人合影（导游 #42，从左至右军军、公主、大海）是另一张，已确认可用。
3. **开场副句**："忙着生活，有多久没好好关心自己了？"（默认采用 Brief 候选稿）。
4. **转化栏**：行内双按钮 + 可选浮动小按钮，还是保留固定双按钮栏（默认行内双按钮，不做浮动按钮）。
5. **CDN 地址**（默认先用本地兜底图开发，CDN 字段留空）。

---

## 8. 每个阶段的启动指令（直接粘贴给 Claude Code）

**P0**
```
读 docs/design/redesign-2026q4/PLAN.md 的 §2、§4、§5-P0。先核对 git branch / HEAD / status 并报告。
在 feat/visual-2026q4 分支上：1) 在 tools/capture/ 写一个基于 miniprogram-automator 的截图脚本，输出 ≥390px 宽的单帧和长图；
2) 按 ART-DIRECTION.md §7 写 tools/images/export.py，从 F:/轻生活项目/轻生活/图片/ 导出账本里的 6 张照片到 F:/轻生活项目/轻生活/outputs/qing-images/，
并生成 manifest.json 和首页本地兜底图。导出后打开 #67 的裁切图，确认看不到手机号和二维码。不做 Logo，不改任何页面。
完成后停下，给我首页首屏截图、manifest.json 和导出图的缩略拼图。
```

**P1**
```
读 PLAN.md §2、§5-P1 和 ART-DIRECTION.md §2、§4。按规格创建 styles/qing-tokens.wxss、tools/fonts/subset.py、styles/qing-fonts.wxss，
并在 app.wxss 顶部 @import。不改任何页面。报告子集大小、主包大小前后对比，以及字体渲染截图，然后停下。
```

**P2**
```
读 PLAN.md §2、§4、§5-P2。按改动清单逐项实施第二 Tab 调整，完成后 grep 确认 switchTab.*camp/index 和 navigateTo.*about/index 都为 0，
运行 npm test，按 6 条走查路径截图（用 tools/capture）。只做导航改动，不改视觉。完成后停下并给我 diff 摘要。
```

**P3**
```
读 PLAN.md §1、§2、§5-P3、§7 和 ART-DIRECTION.md 全文，重点是 §6.1 和 §7 素材账本。重构首页 WXML/WXSS，JS 只按 P3 允许的范围改。
Logo 位置留空；#43 用 releaseReady 开关控制，打开和关闭两种状态都要截图。
先用 ASCII 线框描述你的节奏谱（每个模块：回答什么问题、构图语法、强弱），然后实施。
完成后按截图要求出图，并与 6.1 版长图并排，按 A1–A6 自评，然后停下。
```

**P4**
```
读 PLAN.md §1、§2、§5-P4、§7，以及 ART-DIRECTION.md §6.2、§7。先写节奏谱再实施。
开场句、GPS 句、分享标题、最近一期小卡按 §5-P4"已定的内容改动"落实；最近一期只复用现有公开营期读取，失败时整卡隐藏。
授权校验一律不放宽；#50、#46 默认 releaseReady:false；琉璃光获批时 #46 必须自动隐藏，写一条测试覆盖这条规则。
完成后按截图要求出图，按 A1–A6 自评，然后停下。
```

**P5**
```
读 PLAN.md §5-P5 和 ART-DIRECTION.md §6.3。只换令牌和期号排版，不动任何业务逻辑。完成后出图并停下。
```

**P6**
```
读 PLAN.md §5-P6。写对比度和触控尺寸检查脚本，跑完全部验收项，输出 docs/design/redesign-2026q4/acceptance.md，
把"工程完成 / DevTools 通过 / 真机待测 / 业务待验收"分开写。
```

---

## 9. 追加到 `CLAUDE.md` 的片段

```md
## 视觉改版 2026Q4（进行中）
- 规划与规范：docs/design/redesign-2026q4/PLAN.md、ART-DIRECTION.md。二者与 qinglife-design Skill 冲突时，
  可读性底线与事实边界以 Skill 为准，视觉方向以 ART-DIRECTION 为准。
- 新样式只用 styles/qing-tokens.wxss 中的令牌，不在页面里写新的十六进制色值。
- 固定标题文案改动后，运行 python tools/fonts/subset.py 重新生成字体子集。
- 每个阶段结束必须停下，交 ≥390px 宽截图；不 push、不部署。
- 第二 Tab 是 pages/about/index；pages/camp/index 是二级页，一律用 navigateTo / redirectTo，不用 switchTab。
- 照片只用 ART-DIRECTION §7 素材账本里的；原图在 F:/轻生活项目/轻生活/图片/，不进包、不进仓库；人物照默认 releaseReady:false。
- 高清 Logo 未到货前，顶栏 Logo 位置留空。
```
