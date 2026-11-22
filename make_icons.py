#!/usr/bin/env python3
# Eyeline 图标方案：4 个 512x512 候选 + 一张对比图
import math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

S = 1024          # 超采样
OUT = 512
FONT_PATH = "/system/fonts/MiSansVF.ttf"
ACCENT = (77, 163, 255)
INK = (245, 247, 250)


def font(size, bold=True):
    f = ImageFont.truetype(FONT_PATH, size)
    if bold:
        try:
            f.set_variation_by_name("Bold")
        except Exception:
            pass
    return f


def gradient_bg(top=(19, 23, 32), bottom=(5, 7, 11)):
    img = Image.new("RGB", (S, S))
    d = ImageDraw.Draw(img)
    for y in range(S):
        t = y / (S - 1)
        c = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        d.line((0, y, S, y), fill=c)
    return img


def round_mask(r=232):
    m = Image.new("L", (S, S), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, S - 1, S - 1), radius=r, fill=255)
    return m


def finish(img, r=232):
    mask = round_mask(r)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out.resize((OUT, OUT), Image.LANCZOS)


def glow(img, pos, radius, color, alpha=90):
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.ellipse((pos[0] - radius, pos[1] - radius, pos[0] + radius, pos[1] + radius),
              fill=color + (alpha,))
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    return Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")


def a_eyeline():
    img = gradient_bg()
    img = glow(img, (640, 512), 240, ACCENT, 70)
    d = ImageDraw.Draw(img)
    d.line((150, 512, 760, 512), fill=INK, width=26)
    d.ellipse((560, 432, 720, 592), fill=ACCENT)
    d.ellipse((600, 472, 680, 552), fill=(250, 252, 255))
    return finish(img)


def b_letter():
    img = gradient_bg((16, 26, 48), (6, 10, 20))
    img = glow(img, (700, 540), 240, ACCENT, 50)
    d = ImageDraw.Draw(img)
    d.text((250, 300), "e", font=font(420), fill=INK)
    # e 的横杠向右延伸成「视线」，末端一个点
    d.line((640, 540, 800, 540), fill=ACCENT, width=22)
    d.ellipse((782, 500, 858, 576), fill=ACCENT)
    return finish(img)


def c_bars():
    img = gradient_bg((20, 22, 30), (6, 7, 10))
    d = ImageDraw.Draw(img)
    rows = ((240, 20), (300, 26), (372, 22))
    for y, w in rows:
        d.rounded_rectangle((180, y, 620, y + w), radius=w // 2, fill=(235, 238, 244))
    d.rounded_rectangle((180, 452, 540, 474), radius=11, fill=ACCENT)
    d.ellipse((596, 430, 700, 534), fill=ACCENT)
    return finish(img)


def d_aperture():
    img = gradient_bg((14, 18, 28), (4, 6, 10))
    img = glow(img, (512, 512), 260, ACCENT, 45)
    d = ImageDraw.Draw(img)
    d.ellipse((200, 200, 824, 824), outline=(236, 240, 247), width=26)
    d.line((160, 512, 864, 512), fill=(236, 240, 247), width=20)
    d.ellipse((470, 470, 620, 620), fill=ACCENT)
    return finish(img)


VARIANTS = [("A", a_eyeline), ("B", b_letter), ("C", c_bars), ("D", d_aperture)]

if __name__ == "__main__":
    imgs = []
    import os
    os.makedirs("/sdcard/Download/DSH-Go/eyeline-icons", exist_ok=True)
    for name, fn in VARIANTS:
        im = fn().convert("RGBA")
        im.save(f"/sdcard/Download/DSH-Go/eyeline-icons/{name}.png")
        imgs.append((name, im))

    # 对比图：2x2 + 标签
    sheet = Image.new("RGB", (1120, 1200), (250, 250, 252))
    d = ImageDraw.Draw(sheet)
    d.text((60, 40), "Eyeline · 图标方案", font=font(52), fill=(20, 22, 26))
    for i, (name, im) in enumerate(imgs):
        x = 60 + (i % 2) * 520
        y = 140 + (i // 2) * 530
        sheet.paste(im, (x, y), im)
        d.text((x + 8, y + 400), name, font=font(44), fill=(90, 96, 106))
    sheet.save("/sdcard/Download/DSH-Go/eyeline-icons/preview.png")
    print("ok")
