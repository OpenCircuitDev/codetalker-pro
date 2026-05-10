"""Programmatic placeholder generator for v0.1.0 store assets.

Renders the feature graphic (1024x500) + high-res icon (512x512) per the
spec at store-assets/google-play/feature-graphic-spec.md. Uses PIL only —
no external tooling needed. Replace with a designed hero before production
track per the spec.

Run from anywhere:
    python store-assets/generate-placeholder-assets.py
"""
from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


# Spec colours
BG_DARK_TOP = (0x0A, 0x0B, 0x10)
BG_DARK_BOT = (0x11, 0x14, 0x1C)
ORB_CYAN = (0x22, 0xD3, 0xEE)
ORB_VIOLET = (0xA8, 0x55, 0xF7)
WORDMARK = (0xE6, 0xE8, 0xEE)
TAGLINE = (0xAA, 0xB1, 0xC0)
ICON_BG = (0x0A, 0x0B, 0x10)


def _gradient_diagonal(size: tuple[int, int], top_left: tuple[int, int, int], bottom_right: tuple[int, int, int]) -> Image.Image:
    """Render a 45-degree diagonal gradient (top-left → bottom-right)."""
    w, h = size
    img = Image.new("RGB", size, top_left)
    pixels = img.load()
    max_dist = w + h
    for y in range(h):
        for x in range(w):
            t = (x + y) / max_dist
            r = int(top_left[0] * (1 - t) + bottom_right[0] * t)
            g = int(top_left[1] * (1 - t) + bottom_right[1] * t)
            b = int(top_left[2] * (1 - t) + bottom_right[2] * t)
            pixels[x, y] = (r, g, b)
    return img


def _draw_orb(img: Image.Image, cx: int, cy: int, r: int) -> None:
    """Draw a cyan→violet radial-ish orb centred at (cx, cy) with radius r."""
    draw = ImageDraw.Draw(img, "RGBA")
    for ring in range(r, 0, -1):
        t = 1 - (ring / r)
        rr = int(ORB_CYAN[0] * (1 - t) + ORB_VIOLET[0] * t)
        gg = int(ORB_CYAN[1] * (1 - t) + ORB_VIOLET[1] * t)
        bb = int(ORB_CYAN[2] * (1 - t) + ORB_VIOLET[2] * t)
        alpha = int(255 * min(1.0, 0.4 + t * 0.6))
        draw.ellipse(
            (cx - ring, cy - ring, cx + ring, cy + ring),
            fill=(rr, gg, bb, alpha),
        )
    # Soft white waveform glyph: 3 vertical bars centred in the orb
    bar_w = 6
    bar_gap = 14
    bar_heights = [r * 0.6, r * 0.9, r * 0.5]
    bar_x_start = cx - (bar_w + bar_gap)
    for i, h in enumerate(bar_heights):
        x = bar_x_start + i * (bar_w + bar_gap)
        draw.rounded_rectangle(
            (x, cy - h / 2, x + bar_w, cy + h / 2),
            radius=bar_w // 2,
            fill=(255, 255, 255, 235),
        )


def _load_font(size: int, bold: bool = False) -> ImageFont.ImageFont:
    """Best-effort font loader — falls back to PIL default if Inter isn't installed."""
    candidates = [
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
        "/System/Library/Fonts/Helvetica.ttc",
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ]
    for path in candidates:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def render_feature_graphic(out: Path) -> None:
    """1024x500 hero per spec."""
    img = _gradient_diagonal((1024, 500), BG_DARK_TOP, BG_DARK_BOT)

    _draw_orb(img, cx=160, cy=250, r=90)

    draw = ImageDraw.Draw(img, "RGBA")
    title_font = _load_font(64, bold=True)
    tag_font = _load_font(24, bold=False)
    draw.text((280, 195), "codetalker companion", font=title_font, fill=WORDMARK)
    draw.text((280, 285), "speak to your code, listen to your code", font=tag_font, fill=TAGLINE)

    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG", optimize=True)
    print(f"wrote {out} ({out.stat().st_size:,} bytes)")


def render_icon_512(out: Path) -> None:
    """512x512 high-res icon — solid background + centred orb glyph."""
    img = Image.new("RGB", (512, 512), ICON_BG)
    _draw_orb(img, cx=256, cy=256, r=180)
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, "PNG", optimize=True)
    print(f"wrote {out} ({out.stat().st_size:,} bytes)")


def main() -> None:
    here = Path(__file__).resolve().parent
    feature = here / "google-play" / "feature-graphic.png"
    icon = here / "google-play" / "icon-512.png"
    xreal_banner = here / "xreal-store" / "banner.png"

    render_feature_graphic(feature)
    render_icon_512(icon)
    # XREAL banner = same artwork as feature graphic (per spec)
    xreal_banner.parent.mkdir(parents=True, exist_ok=True)
    Image.open(feature).save(xreal_banner, "PNG", optimize=True)
    print(f"wrote {xreal_banner} ({xreal_banner.stat().st_size:,} bytes)")


if __name__ == "__main__":
    main()
