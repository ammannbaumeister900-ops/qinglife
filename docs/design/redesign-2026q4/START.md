# 从这里开始

本目录有三份文件：

| 文件 | 给谁看 | 作用 |
|---|---|---|
| `START.md` | 你 | 怎么启动、怎么推进 |
| `PLAN.md` | Claude Code | 执行规划：护栏、7 个阶段、每阶段改哪些文件、验收、停点、启动指令 |
| `ART-DIRECTION.md` | Claude Code | 视觉规范：令牌、字体、组件、页面规格、素材账本 |

审计页（claude.ai 上的「轻生活小程序视觉审计」）给人看，不需要交给 Claude Code。

## 推进方式

Claude Code 不是一次跑完全部阶段。每个阶段做完都会停下来，交截图给你看：

1. 在仓库根目录 `F:/轻生活项目/轻生活/qinglife` 打开 Claude Code，粘贴下面的**第一条指令**。它会做准备工作和 P0，然后停下。
2. 你看完截图，满意就回复"继续 P1"（或粘贴 `PLAN.md` §8 中对应阶段的指令）；不满意就直接说哪里要改。
3. 依次推进到 P6。P3（首页）和 P4（认知页）是审美评审的关键两站。

## 第一条指令（粘贴给 Claude Code）

```
请先完整阅读 docs/design/redesign-2026q4/PLAN.md 和 ART-DIRECTION.md。

准备工作：
1. 报告当前 git branch / HEAD / status，以及 apps/miniapp 与基线 3560d5d 的差异；有未提交改动先停下告诉我。
2. 从当前分支新建 feat/visual-2026q4。
3. 把 PLAN.md §9 的片段追加到 apps/miniapp/CLAUDE.md（不存在就追加到仓库根目录 CLAUDE.md；都不存在就新建 apps/miniapp/CLAUDE.md）。
4. 把 PLAN.md 中"待定"各项按默认值执行，不再问我。

然后执行 PLAN.md §5 的 P0（§8 有 P0 指令）。完成后停下，给我：首页首屏截图（≥390px 宽）、manifest.json、导出照片的缩略拼图、本阶段的文件清单。
之后每个阶段都在停点停下，等我回复"继续"再进入下一阶段。不 push、不部署。
```
