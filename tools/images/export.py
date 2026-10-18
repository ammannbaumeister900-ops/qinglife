"""按 ART-DIRECTION §7 素材账本导出轻生活视觉改版照片。

原图只读；导出到仓库外 outputs/qing-images/ 供上传 CDN。
仅首页本地兜底图与认知页分享图写入小程序包。

用法：python tools/images/export.py [--src DIR] [--out DIR]
依赖：Pillow
"""

import argparse
import io
import json
import sys
from pathlib import Path

from PIL import Image, ImageOps

Image.MAX_IMAGE_PIXELS = None

REPO = Path(__file__).resolve().parents[2]
WORKSPACE = REPO.parent
DEFAULT_SRC = WORKSPACE / "图片"
DEFAULT_OUT = WORKSPACE / "outputs" / "qing-images"
MINIAPP_BRAND = REPO / "apps" / "miniapp" / "assets" / "brand"

QUALITY_START = 78
QUALITY_FLOOR = 70
KB = 1024

# 编号、源文件、用途、裁切框（相对原图 x0,y0,x1,y1）、导出尺寸、体积上限、releaseReady、
# 本地输出路径（None 表示只进 CDN 目录）。
LEDGER = [
    {
        "id": "guide-42", "source": "导游高清照片/微信图片_20261008143029_42_250.jpg",
        "use": "home-hero", "role": "首页首屏 · 氛围",
        "crop": (0, 0, 1, 1), "size": (1170, 780), "max_kb": 220, "releaseReady": True,
    },
    {
        "id": "guide-42", "source": "导游高清照片/微信图片_20261008143029_42_250.jpg",
        "use": "home-hero-local", "role": "首页首屏本地兜底",
        "crop": (0, 0, 1, 1), "size": (750, 500), "max_kb": 120, "releaseReady": True,
        "local": MINIAPP_BRAND / "home-guides.jpg",
    },
    {
        "id": "guide-42", "source": "导游高清照片/微信图片_20261008143029_42_250.jpg",
        "use": "share-about", "role": "认知页分享卡片",
        "crop": (0.083, 0, 0.917, 1), "size": (500, 400), "max_kb": 128, "releaseReady": True,
        "local": MINIAPP_BRAND / "share-about.jpg",
    },
    {
        "id": "scene-50", "source": "活动现场照片/微信图片_20261008160238_50_483.jpg",
        "use": "about-opening", "role": "认知页开场 · 慢下来",
        "crop": (0.204, 0, 0.7365, 1), "size": (1080, 1350), "max_kb": 220, "releaseReady": False,
        "pending": "待确认人物是否为导游公主",
    },
    {
        "id": "scene-46", "source": "活动现场照片/微信图片_20261008160208_46_483.jpg",
        "use": "about-return", "role": "认知页高点 · 被照顾",
        "crop": (0.17, 0.12, 0.99, 0.9396), "size": (1170, 780), "max_kb": 220, "releaseReady": False,
        "pending": "待导游军军确认公开使用",
    },
    {
        "id": "scene-43", "source": "活动现场照片/微信图片_20261008160022_43_483.jpg",
        "use": "home-about", "role": "首页「了解轻生活」 · 关系",
        "crop": (0.04, 0.02, 0.96, 0.94), "size": (1170, 780), "max_kb": 220, "releaseReady": False,
        "pending": "待导游大海确认公开使用",
    },
    {
        "id": "scene-67", "source": "活动现场照片/微信图片_20261008160710_67_483.jpg",
        "use": "about-juice", "role": "认知页饮食说明旁 · 细节（已裁掉标签手机号与二维码）",
        "crop": (0.04, 0.02, 0.74, 0.6763), "size": (600, 750), "max_kb": 120, "releaseReady": True,
    },
    {
        # 2026-10-18 用户提供并确认可用；目前只有聊天转存的 1280×1920 压缩版，原图到位后替换 source 重导。
        "id": "family-huahua", "source": "轻友高清照片（原图）/花花蕾蕾一家三人合影.jpg",
        "use": "about-story", "role": "认知页人物故事 · 花花一家",
        "crop": (0, 0.12, 1, 0.9533), "size": (1080, 1350), "max_kb": 220, "releaseReady": True,
        "pending": "文案已于 2026-10-18 审定；仍建议补原图后重导",
    },
    {
        "id": "scene-48", "source": "活动现场照片/微信图片_20261008160234_48_483.jpg",
        "use": "camp-header", "role": "清体营二级页页头 · 地方",
        "crop": (0, 0.12, 1, 0.921), "size": (1170, 624), "max_kb": 180, "releaseReady": True,
    },
]


def crop_to_ratio(image, box, size):
    """按相对裁切框裁切，再居中微调到目标宽高比（账本比例存在舍入误差）。"""
    width, height = image.size
    x0, y0, x1, y1 = box
    left, top = x0 * width, y0 * height
    right, bottom = x1 * width, y1 * height
    target = size[0] / size[1]
    box_w, box_h = right - left, bottom - top
    if box_w / box_h > target:
        trim = (box_w - box_h * target) / 2
        left, right = left + trim, right - trim
    else:
        trim = (box_h - box_w / target) / 2
        top, bottom = top + trim, bottom - trim
    pixel_box = tuple(round(v) for v in (left, top, right, bottom))
    return image.crop(pixel_box), pixel_box


def encode(image, max_bytes):
    """返回 (数据, quality, 是否在上限内)；降到 QUALITY_FLOOR 仍超限时返回最低质量版本供人工评审。"""
    for quality in range(QUALITY_START, QUALITY_FLOOR - 1, -2):
        buffer = io.BytesIO()
        # 不传 exif / icc 等元数据，导出图不含 GPS 与拍摄信息。
        image.save(buffer, "JPEG", quality=quality, progressive=True, optimize=True)
        if buffer.tell() <= max_bytes:
            return buffer.getvalue(), quality, True
    return buffer.getvalue(), QUALITY_FLOOR, False


def export(src_root, out_root):
    out_root.mkdir(parents=True, exist_ok=True)
    cache = {}
    manifest = []
    failures = []
    for item in LEDGER:
        source = src_root / item["source"]
        if source not in cache:
            with Image.open(source) as raw:
                cache[source] = ImageOps.exif_transpose(raw).convert("RGB")
        original = cache[source]
        cropped, pixel_box = crop_to_ratio(original, item["crop"], item["size"])
        resized = cropped.resize(item["size"], Image.LANCZOS)
        filename = f"qing-{item['use']}-{item['id'].split('-')[1]}.jpg"
        data, quality, within = encode(resized, item["max_kb"] * KB)
        if not within:
            failures.append(f"{filename}: quality {QUALITY_FLOOR} 仍为 {len(data) / KB:.1f}KB，"
                            f"超出 {item['max_kb']}KB 上限")
        (out_root / filename).write_bytes(data)
        local = item.get("local")
        if local:
            local.write_bytes(data)
        entry = {
            "id": item["id"],
            "use": item["use"],
            "role": item["role"],
            "source": item["source"],
            "sourceSize": list(original.size),
            "crop": list(item["crop"]),
            "cropPixels": list(pixel_box),
            "size": list(item["size"]),
            "bytes": len(data),
            "maxBytes": item["max_kb"] * KB,
            "quality": quality,
            "cdnFilename": filename,
            "local": local.relative_to(REPO).as_posix() if local else None,
            "releaseReady": item["releaseReady"],
        }
        if not within:
            entry["overBudget"] = True
        if item.get("pending"):
            entry["pending"] = item["pending"]
        manifest.append(entry)
        print(f"{filename:32} {item['size'][0]}x{item['size'][1]:<5} {len(data) / KB:6.1f}KB q{quality}"
              f"{'  -> ' + entry['local'] if local else ''}")

    (out_root / "manifest.json").write_text(
        json.dumps({"ledger": "docs/design/redesign-2026q4/ART-DIRECTION.md §7.1", "images": manifest},
                   ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    if failures:
        print("\n".join(["超出体积上限（已导出并在 manifest 标 overBudget，不可直接上线）："] + failures),
              file=sys.stderr)
        return 1
    print(f"manifest: {out_root / 'manifest.json'}")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--src", type=Path, default=DEFAULT_SRC)
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    args = parser.parse_args()
    return export(args.src, args.out)


if __name__ == "__main__":
    sys.exit(main())
