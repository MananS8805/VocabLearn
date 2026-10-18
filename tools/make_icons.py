#!/usr/bin/env python3
"""Regenerate the legacy (pre-Android-8) launcher icons from the Glossa mark.

Android 8+ uses the vector adaptive icon (res/drawable/ic_launcher_foreground.xml
and ic_launcher_background.xml); this script only writes the bitmap fallbacks
(mipmap-*/ic_launcher.webp and ic_launcher_round.webp) that minSdk 24 still needs.
The artwork is the same three rounded bars on black: grey, peach, grey.

    pip install pillow
    python tools/make_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw

RES = Path(__file__).resolve().parent.parent / "app/src/main/res"
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
GREY, PEACH, BLACK = (138, 138, 138, 255), (255, 211, 160, 255), (0, 0, 0, 255)
SCALE = 8  # draw large, then downsample for smooth edges

# Bars as (x0, y0, x1, y1, radius, colour) on the adaptive icon's 108-unit canvas.
BARS = [
    (35, 34, 73, 40, 3, GREY),
    (34, 48, 74, 58, 5, PEACH),
    (35, 66, 62, 72, 3, GREY),
]


def render(size: int, shape: str) -> Image.Image:
    big = size * SCALE
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Legacy icons are drawn at full size, so the 108 canvas is cropped to its central
    # 72 units (the visible window of an adaptive icon) and scaled up.
    window = (108 - 72) / 2
    k = big / 72
    if shape == "round":
        d.ellipse((0, 0, big - 1, big - 1), fill=BLACK)
    else:
        d.rounded_rectangle((0, 0, big - 1, big - 1), radius=int(big * 0.22), fill=BLACK)
    for x0, y0, x1, y1, r, colour in BARS:
        d.rounded_rectangle(
            ((x0 - window) * k, (y0 - window) * k, (x1 - window) * k, (y1 - window) * k),
            radius=r * k, fill=colour)
    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    for density, size in DENSITIES.items():
        out = RES / f"mipmap-{density}"
        out.mkdir(parents=True, exist_ok=True)
        render(size, "square").save(out / "ic_launcher.webp", "WEBP", lossless=True)
        render(size, "round").save(out / "ic_launcher_round.webp", "WEBP", lossless=True)
        print(f"{density}: {size}px")


if __name__ == "__main__":
    main()
