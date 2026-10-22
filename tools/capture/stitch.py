"""把 capture.cjs 输出的滚动帧拼成长图。

用法：python tools/capture/stitch.py <frames.json> <out.png> [--top PX] [--bottom PX]
  --top     后续帧顶部要裁掉的固定区高度（逻辑 px，例如自定义导航栏）
  --bottom  除最后一帧外底部要裁掉的固定区高度（逻辑 px，例如 Tab 栏）
依赖：Pillow
"""

import argparse
import json

from PIL import Image


def stitch(spec_path, out_path, fixed_top=0, fixed_bottom=0):
    with open(spec_path, encoding="utf-8") as handle:
        spec = json.load(handle)
    frames = spec["frames"]
    images = [Image.open(frame["file"]).convert("RGB") for frame in frames]
    scale = images[0].height / spec["viewport"]
    width = images[0].width
    total = round(spec["pageHeight"] * scale)
    canvas = Image.new("RGB", (width, total), "white")

    covered = 0
    for index, (frame, image) in enumerate(zip(frames, images)):
        offset = round(frame["scrollTop"] * scale)
        top = max(covered - offset, round(fixed_top * scale) if index else 0)
        last = index == len(frames) - 1
        bottom = image.height - (0 if last else round(fixed_bottom * scale))
        if bottom <= top:
            continue
        canvas.paste(image.crop((0, top, width, bottom)), (0, offset + top))
        covered = offset + bottom
    canvas.save(out_path)
    print(f"{out_path}  {canvas.width}x{canvas.height}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("spec")
    parser.add_argument("out")
    parser.add_argument("--top", type=float, default=0)
    parser.add_argument("--bottom", type=float, default=0)
    args = parser.parse_args()
    stitch(args.spec, args.out, args.top, args.bottom)


if __name__ == "__main__":
    main()
