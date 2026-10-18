"""Draws the Play icon (512x512) and feature graphics (1024x500) from the launcher icon's geometry."""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

OUT = Path(__file__).parent
BERRY, PINK, DEEP = (194, 24, 91), (248, 187, 208), (120, 14, 58)
FONTS = Path("C:/Windows/Fonts")
SS = 4


def cubic(p0, p1, p2, p3, steps=48):
    return [
        tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * d for a, b, c, d in zip(p0, p1, p2, p3))
        for t in (i / steps for i in range(1, steps + 1))
    ]


def drop(cx, top, scale):
    """The launcher drop: its 108-unit path, centered on cx with its tip at top."""
    pts = [(54, 28)]
    pts += cubic((54, 28), (41.6, 38.6), (35.4, 47.7), (35.4, 55.4))
    pts += cubic((35.4, 55.4), (35.4, 67.0), (44.2, 74.5), (54, 74.5))
    pts += cubic((54, 74.5), (63.8, 74.5), (72.6, 67.0), (72.6, 55.4))
    pts += cubic((72.6, 55.4), (72.6, 47.7), (66.4, 38.6), (54, 28))
    return [(cx + (x - 54) * scale, top + (y - 28) * scale) for x, y in pts]


def draw_mark(d, cx, top, scale, fill=(255, 255, 255), pill=PINK, line=BERRY):
    d.polygon(drop(cx, top, scale), fill=fill)
    pc = (cx, top + (57.5 - 28) * scale)
    r = 7.5 * scale
    d.ellipse([pc[0] - r, pc[1] - r, pc[0] + r, pc[1] + r], fill=pill)
    d.rectangle([pc[0] - r, pc[1], pc[0] + r, pc[1] + 1.6 * scale], fill=line)


def icon():
    size = 512 * SS
    im = Image.new("RGB", (size, size), BERRY)
    scale = size / 108
    draw_mark(ImageDraw.Draw(im), size / 2, 28 * scale, scale)
    im.resize((512, 512), Image.Resampling.LANCZOS).save(OUT / "icon-512.png")


def pack(d, x0, y0, cell, popped):
    """A 3x7 blister pack with the first `popped` pills taken."""
    w, h = cell * 7 + cell * 0.6, cell * 3 + cell * 0.6
    d.rounded_rectangle([x0, y0, x0 + w, y0 + h], radius=cell * 0.45, fill=(255, 255, 255, 46))
    for i in range(21):
        cx = x0 + cell * 0.8 + (i % 7) * cell
        cy = y0 + cell * 0.8 + (i // 7) * cell
        r = cell * 0.36
        if i < popped:
            d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=(255, 255, 255, 120), width=int(cell * 0.06))
        else:
            d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 255, 255, 235))
            d.line([cx - r * 0.55, cy, cx + r * 0.55, cy], fill=PINK + (255,), width=int(cell * 0.05))
        if i == popped:
            R = cell * 0.47
            d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=(255, 255, 255, 255), width=int(cell * 0.08))


def feature(tagline, name):
    w, h = 1024 * SS, 500 * SS
    bg = Image.new("RGB", (w, h))
    px = bg.load()
    for x in range(w):
        t = x / w
        c = tuple(int(DEEP[k] + (BERRY[k] - DEEP[k]) * (0.35 + 0.65 * t)) for k in range(3))
        for y in range(h):
            px[x, y] = c
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    draw_mark(d, 140 * SS, 178 * SS, 3.2 * SS, fill=(255, 255, 255, 255), pill=PINK + (255,), line=BERRY + (255,))
    title = ImageFont.truetype(str(FONTS / "segoeuib.ttf"), 92 * SS)
    sub = ImageFont.truetype(str(FONTS / "segoeui.ttf"), 28 * SS)
    d.text((245 * SS, 128 * SS), "Perixd", font=title, fill=(255, 255, 255, 255))
    y = 262 * SS
    for line in tagline:
        d.text((250 * SS, y), line, font=sub, fill=(255, 235, 242, 255))
        y += 40 * SS
    pack(d, 676 * SS, 178 * SS, 40 * SS, popped=12)
    out = Image.alpha_composite(bg.convert("RGBA"), layer).convert("RGB")
    out.resize((1024, 500), Image.Resampling.LANCZOS).save(OUT / name)


if __name__ == "__main__":
    icon()
    feature(["Period, symptoms and pill.", "Private by design."], "feature-en-US.png")
    feature(["Menstruação, sintomas e pílula.", "Privado de raiz."], "feature-pt-PT.png")
