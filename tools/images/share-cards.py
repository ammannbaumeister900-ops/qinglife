"""生成轻生活分享卡片（认知页分享图 + 邀请轻友三张邀请卡），统一一个模板。

模板（docs/design/redesign-2026q4/p9-invitation-rhythm.md）：5:4，上方实拍照片，下方亚麻色带，
左「轻生活」宋体，右一行小字。图上不印标题与营名，标题由分享标题承担。
原图只读；卡片直接写入小程序包（每张 ≤128KB）。

用法：python tools/images/share-cards.py [--src DIR]
依赖：Pillow；字体 tools/fonts/src/NotoSerifSC-SemiBold.otf（OFL）
"""

import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont, ImageOps

sys.path.insert(0, str(Path(__file__).resolve().parent))
from export import DEFAULT_SRC, KB, REPO, crop_to_ratio  # noqa: E402  复用账本裁切口径

Image.MAX_IMAGE_PIXELS = None

FONT = REPO / "tools" / "fonts" / "src" / "NotoSerifSC-SemiBold.otf"
MINIAPP = REPO / "apps" / "miniapp" / "assets"
SIZE = (1000, 800)          # 2× 500×400，页面内满宽预览也清晰
BAND = 160                  # 色带高度（2×80）
PAD = 48                    # 左右边距（2×24）
LINEN, INK, INK_2 = "#F3F2EC", "#1F251D", "#596253"   # 对应 --q-linen / --q-ink / --q-ink-2
MAX_KB = 128

# 照片与用途见 ART-DIRECTION §7.1（2026-10-19 用户确认分享卡片例外用途）。
CARDS = [
    {"out": MINIAPP / "brand" / "share-about.jpg", "id": "guide-42",
     "source": "导游高清照片/微信图片_20261008143029_42_250.jpg", "crop": (0, 0, 1, 1), "note": "为身体减负，为心灵减压"},
    {"out": MINIAPP / "invitations" / "invite-pause-48.jpg", "id": "scene-48",
     "source": "活动现场照片/微信图片_20261008160234_48_483.jpg", "crop": (0, 0, 1, 1), "note": "一封相聚的邀请"},
    {"out": MINIAPP / "invitations" / "invite-together-43.jpg", "id": "scene-43",
     "source": "活动现场照片/微信图片_20261008160022_43_483.jpg", "crop": (0.04, 0.02, 0.96, 0.94), "note": "一封相聚的邀请"},
    {"out": MINIAPP / "invitations" / "invite-bloom-50.jpg", "id": "scene-50",
     "source": "活动现场照片/微信图片_20261008160238_50_483.jpg", "crop": (0, 0, 1, 1), "note": "一封相聚的邀请"},
]


def compose(photo, note):
    card = Image.new("RGB", SIZE, LINEN)
    area = (SIZE[0], SIZE[1] - BAND)
    card.paste(photo.resize(area, Image.LANCZOS), (0, 0))
    draw = ImageDraw.Draw(card)
    brand, small = ImageFont.truetype(str(FONT), 40), ImageFont.truetype(str(FONT), 28)
    middle = SIZE[1] - BAND / 2
    draw.text((PAD, middle), "轻生活", font=brand, fill=INK, anchor="lm")
    draw.text((SIZE[0] - PAD, middle), note, font=small, fill=INK_2, anchor="rm")
    return card


def encode(image, path):
    for quality in range(84, 59, -2):
        image.save(path, "JPEG", quality=quality, progressive=True, optimize=True)
        if path.stat().st_size <= MAX_KB * KB:
            return quality
    raise SystemExit(f"{path.name}: quality 60 仍为 {path.stat().st_size / KB:.1f}KB，超出 {MAX_KB}KB")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC)
    args = parser.parse_args()
    for card in CARDS:
        with Image.open(args.src / card["source"]) as raw:
            original = ImageOps.exif_transpose(raw).convert("RGB")
        photo, box = crop_to_ratio(original, card["crop"], (SIZE[0], SIZE[1] - BAND))
        quality = encode(compose(photo, card["note"]), card["out"])
        print(f"{card['out'].relative_to(REPO).as_posix():52} {card['id']:9} crop {box} "
              f"{card['out'].stat().st_size / KB:6.1f}KB q{quality}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
