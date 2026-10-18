## 视觉改版 2026Q4（进行中）
- 规划与规范：docs/design/redesign-2026q4/PLAN.md、ART-DIRECTION.md。二者与 qinglife-design Skill 冲突时，
  可读性底线与事实边界以 Skill 为准，视觉方向以 ART-DIRECTION 为准。
- 新样式只用 styles/qing-tokens.wxss 中的令牌，不在页面里写新的十六进制色值。
- 固定标题文案改动后，运行 python tools/fonts/subset.py 重新生成字体子集。
- 每个阶段结束必须停下，交 ≥390px 宽截图；不 push、不部署。
- 第二 Tab 是 pages/about/index；pages/camp/index 是二级页，一律用 navigateTo / redirectTo，不用 switchTab。
- 照片只用 ART-DIRECTION §7 素材账本里的；原图在 F:/轻生活项目/轻生活/图片/，不进包、不进仓库；人物照默认 releaseReady:false。
- 高清 Logo 未到货前，顶栏 Logo 位置留空。
