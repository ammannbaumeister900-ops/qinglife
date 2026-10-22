# 字体子集

`subset.py` 把固定标题用到的字裁成两个子集，以 base64 写入 `apps/miniapp/styles/qing-fonts.wxss`：

| 字体族 | 源字体（SIL OFL 1.1） | 字符 | 预算 |
|---|---|---|---|
| `Qing Serif` | Noto Serif SC SemiBold | `config/` 中的固定标题字段 + `WXML_TITLES` 中的区头 + 常用标点 | ≤ 45KB |
| `Qing Num` | Cormorant Garamond SemiBold | `0-9 . % + – — s` 与空格 | ≤ 12KB |

## 何时重新生成

改了以下任意文案后都要重跑，否则新字会回退为系统字体：

- `config/home-brand.js`、`config/brand-content.js`、`config/about-content.js` 中的 `title`、`heading`、`openingLine`、`openingInvitation`、`discoverTitle`、`endnote`、`motto`、`glyph(s)`、`name`、`quote`、`closingQuote` 字段
- `subset.py` 中 `WXML_TITLES` 列出的 WXML 区头（脚本会核对它们仍在 WXML 中）

接口返回的动态文本（营期名、文章标题）不进子集，版面须在系统字体下成立。

## 运行

```sh
pip install fonttools brotli
python tools/fonts/subset.py            # 生成并写入 qing-fonts.wxss
python tools/fonts/subset.py --dry-run  # 只看字符数与体积
```

源字体放在 `tools/fonts/src/`（已在 `.gitignore` 中忽略，不进仓库也不进小程序包）：

- `NotoSerifSC-SemiBold.otf`：https://github.com/notofonts/noto-cjk （`Serif/SubsetOTF/SC/`）
- `CormorantGaramond-SemiBold.otf`：https://github.com/CatharsisFonts/Cormorant （`fonts/otf/`）

超出预算时脚本报错退出。Android 真机若不显示 woff，再改为输出 ttf。
