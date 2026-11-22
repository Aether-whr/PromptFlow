#!/usr/bin/env python3
# 把方案 A（视线 + 瞳孔）做成自适应图标 + 各密度位图
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = "/root/workspace/tp/res"
HIGH = 1024
ACCENT = (77, 163, 255)
LINE = (245, 247, 250)


def draw_art(size, with_bg=True, mono=False):
    """在透明画布上画图标；with_bg=False 用于自适应前景。"""
    s = size
    ss = 4
    S = s * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if with_bg:
        # 垂直渐变底
        bg = Image.new("RGB", (S, S))
        bd = ImageDraw.Draw(bg)
        top, bottom = (19, 23, 32), (5, 7, 11)
        for y in range(S):
            t = y / (S - 1)
            bd.line((0, y, S, y), fill=tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3)))
        mask = Image.new("L", (S, S), 0)
        ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.226), fill=255)
        img.paste(bg, (0, 0), mask)

    cy = S // 2
    if with_bg:
        x0, x1 = int(S * 0.147), int(S * 0.743)   # 150..760 @1024
        lw = int(S * 0.026)
        r = int(S * 0.078)                        # 80 @1024
        cx = int(S * 0.625)                       # 640
    else:
        x0, x1 = int(S * 0.21), int(S * 0.79)
        lw = int(S * 0.036)
        r = int(S * 0.105)
        cx = int(S * 0.60)

    line_color = (255, 255, 255, 255) if mono else LINE + (255,)
    dot_color = (255, 255, 255, 255) if mono else ACCENT + (255,)

    d.line((x0, cy, x1, cy), fill=line_color, width=lw)
    if not mono:
        # 先画一圈柔光
        glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        gd = ImageDraw.Draw(glow)
        gd.ellipse((cx - r * 2.6, cy - r * 2.6, cx + r * 2.6, cy + r * 2.6),
                   fill=ACCENT + (70,))
        glow = glow.filter(ImageFilter.GaussianBlur(r * 1.2))
        img = Image.alpha_composite(img, glow)
        d = ImageDraw.Draw(img)
        d.line((x0, cy, x1, cy), fill=line_color, width=lw)

    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=dot_color)
    if not mono:
        pr = int(r * 0.42)
        d.ellipse((cx - pr, cy - pr, cx + pr, cy + pr), fill=(250, 252, 255, 255))
    return img.resize((s, s), Image.LANCZOS)


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


# 旧式方图标（各密度）
for density, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    save(draw_art(px), f"{ROOT}/mipmap-{density}/ic_launcher.png")

# 自适应图标前景 / 单色（432）
save(draw_art(432, with_bg=False), f"{ROOT}/drawable/ic_launcher_foreground.png")
save(draw_art(432, with_bg=False, mono=True), f"{ROOT}/drawable/ic_launcher_monochrome.png")

os.makedirs(f"{ROOT}/mipmap-anydpi-v26", exist_ok=True)
with open(f"{ROOT}/mipmap-anydpi-v26/ic_launcher.xml", "w") as f:
    f.write("""<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_bg" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
""")

os.makedirs(f"{ROOT}/drawable", exist_ok=True)
with open(f"{ROOT}/drawable/ic_launcher_bg.xml", "w") as f:
    f.write("""<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient
        android:angle="270"
        android:startColor="#131720"
        android:endColor="#05070B"
        android:type="linear" />
</shape>
""")
print("ok")
