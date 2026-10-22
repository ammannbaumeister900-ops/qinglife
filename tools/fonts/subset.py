"""生成「林下午后」固定标题字体子集，写入 apps/miniapp/styles/qing-fonts.wxss。

规格见 docs/design/redesign-2026q4/ART-DIRECTION.md §4。
  Qing Serif  Noto Serif SC SemiBold      固定标题汉字 + 常用标点   ≤ 45KB
  Qing Num    Cormorant Garamond SemiBold 0-9 . % + – — s 与空格    ≤ 12KB

用法：python tools/fonts/subset.py [--dry-run]
依赖：pip install fonttools brotli；源字体放在 tools/fonts/src/（不进仓库、不进小程序包）。
"""

import argparse
import base64
import io
import json
import re
import subprocess
import sys
from pathlib import Path

from fontTools import subset

REPO = Path(__file__).resolve().parents[2]
MINIAPP = REPO / "apps" / "miniapp"
SRC = Path(__file__).resolve().parent / "src"
OUTPUT = MINIAPP / "styles" / "qing-fonts.wxss"
KB = 1024

CONFIGS = ["config/home-brand.js", "config/brand-content.js", "config/about-content.js"]
# 配置中作为固定标题渲染的字段（衬线）。风险标题等用黑体的字段不在此列。
TITLE_KEYS = {"title", "heading", "openingLine", "openingInvitation", "discoverTitle",
              "endnote", "motto", "glyph", "glyphs",
              # 人物故事的名字与两句原话也用衬线（about-person-name / about-person-quote / about-closing-quote）。
              "name", "quote", "closingQuote"}
# WXML 中写死、按规范用衬线的区头；脚本会核对它们仍在对应文件中。
WXML_TITLES = {
    "pages/home/index.wxml": ["近期清体营", "亲友心声"],
    "pages/camp/index.wxml": ["近期活动", "我的安排"],
    "pages/phone-auth/index.wxml": ["轻一点，先认识你"],
    "pages/camp-flow/index.wxml": ["填写轻友登记"],
}
PUNCTUATION = "，。？、：；“”（）·—"
NUM_CHARS = "0123456789.%+–—s "

FONTS = [
    {"family": "Qing Serif", "source": "NotoSerifSC-SemiBold.otf", "budget": 45 * KB},
    # Cormorant 默认是旧式数字（下沉、像字母），日期与统计数字改用等宽齐线数字 .tf 字形。
    {"family": "Qing Num", "source": "CormorantGaramond-SemiBold.otf", "budget": 12 * KB,
     "default_glyphs": {c: f"{name}.tf" for c, name in zip("0123456789", ["zero", "one", "two", "three", "four",
                                                                         "five", "six", "seven", "eight", "nine"])}},
]


def load_config(relative):
    script = f"process.stdout.write(JSON.stringify(require({json.dumps(str(MINIAPP / relative))})))"
    return json.loads(subprocess.run(["node", "-e", script], check=True, capture_output=True,
                                     encoding="utf-8").stdout)


def title_strings(node, key=None):
    if isinstance(node, dict):
        for child_key, value in node.items():
            yield from title_strings(value, child_key)
    elif isinstance(node, list):
        for value in node:
            yield from title_strings(value, key)
    elif isinstance(node, str) and key in TITLE_KEYS:
        yield node


def serif_text():
    strings = []
    for relative in CONFIGS:
        strings.extend(title_strings(load_config(relative)))
    for relative, titles in WXML_TITLES.items():
        markup = (MINIAPP / relative).read_text(encoding="utf-8")
        missing = [title for title in titles if title not in markup]
        if missing:
            raise SystemExit(f"{relative} 中找不到固定标题 {missing}，请更新 WXML_TITLES")
        strings.extend(titles)
    chars = set(re.sub(r"\s", "", "".join(strings))) | set(PUNCTUATION)
    return "".join(sorted(chars))


def build(source, text, default_glyphs=None):
    options = subset.Options()
    options.flavor = "woff"
    options.layout_features = ["*"]
    options.hinting = False
    options.desubroutinize = True
    options.name_IDs = ["*"]
    font = subset.load_font(str(source), options)
    if default_glyphs:
        # 直接改 cmap，让字符默认映射到替代字形，不依赖渲染端支持 font-feature-settings。
        glyph_names = set(font.getGlyphOrder())
        missing = [glyph for glyph in default_glyphs.values() if glyph not in glyph_names]
        if missing:
            raise SystemExit(f"{source.name} 缺少字形 {missing}")
        for table in font["cmap"].tables:
            if table.isUnicode():
                for char, glyph in default_glyphs.items():
                    table.cmap[ord(char)] = glyph
    subsetter = subset.Subsetter(options)
    subsetter.populate(text=text)
    subsetter.subset(font)
    buffer = io.BytesIO()
    subset.save_font(font, buffer, options)
    return buffer.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--dry-run", action="store_true", help="只打印结果，不写 qing-fonts.wxss")
    args = parser.parse_args()

    texts = {"Qing Serif": serif_text(), "Qing Num": NUM_CHARS}
    rules, over = [], []
    for spec in FONTS:
        source = SRC / spec["source"]
        if not source.exists():
            raise SystemExit(f"缺少源字体 {source}（见 tools/fonts/README.md）")
        text = texts[spec["family"]]
        data = build(source, text, spec.get("default_glyphs"))
        print(f"{spec['family']:10} {len(text):4} 字符  {len(data) / KB:6.1f}KB / 预算 {spec['budget'] // KB}KB")
        if len(data) > spec["budget"]:
            over.append(spec["family"])
        encoded = base64.b64encode(data).decode("ascii")
        rules.append(f'@font-face {{ font-family: "{spec["family"]}"; font-weight: 600; font-style: normal; '
                     f'font-display: swap; src: url("data:font/woff;base64,{encoded}") format("woff"); }}')
    print("Qing Serif 字符：" + texts["Qing Serif"])
    if over:
        raise SystemExit(f"超出预算：{', '.join(over)}")
    if args.dry_run:
        return 0
    header = ("/* 由 tools/fonts/subset.py 生成，请勿手改。固定标题文案改动后重新运行。\n"
              " * Noto Serif SC / Cormorant Garamond，SIL Open Font License 1.1。 */\n")
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(header + "\n".join(rules) + "\n", encoding="utf-8")
    print(f"写入 {OUTPUT.relative_to(REPO).as_posix()}  {OUTPUT.stat().st_size / KB:.1f}KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
