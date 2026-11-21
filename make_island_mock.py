#!/usr/bin/env python3
# 生成超级岛样式示意图（给小米焦点通知申请当附件）
from PIL import Image, ImageDraw, ImageFont

W, H = 1600, 1560
BG = (245, 246, 248)
INK = (20, 22, 26)
SUB = (107, 114, 128)
STRIP = (17, 19, 24)
CARD = (255, 255, 255)
BORDER = (225, 228, 233)
ISLAND = (0, 0, 0)
ICONBG = (42, 47, 54)
TXT = (236, 237, 242)
MUTED = (154, 160, 170)
ACCENT = (77, 163, 255)
GREEN = (74, 222, 128)

FONT = "/system/fonts/MiSansVF.ttf"


def font(size, bold=False):
    f = ImageFont.truetype(FONT, size)
    if bold:
        try:
            f.set_variation_by_name("Bold")
        except Exception:
            pass
    return f


def rrect(d, box, r, fill, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def center_text(d, xy, text, f, fill):
    w = d.textlength(text, font=f)
    d.text((xy[0] - w / 2, xy[1]), text, font=f, fill=fill)


img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((80, 70), "提词器 · 超级岛样式示意", font=font(56, True), fill=INK)
d.text((80, 150), "Xiaomi HyperOS 3 · Android 16 · 焦点通知（客户端实现，不走 MiPush）",
       font=font(30), fill=SUB)

# ── Card A：小岛 ─────────────────────────────────────────────
rrect(d, (80, 240, 1520, 580), 28, CARD, BORDER, 2)
d.text((120, 276), "小岛（摘要态）", font=font(34, True), fill=INK)

rrect(d, (120, 340, 1480, 480), 24, STRIP)
d.text((160, 392), "20:24", font=font(28), fill=TXT)
# 状态栏右侧：信号/WiFi/电池
for i, x in enumerate((1300, 1340, 1380)):
    rrect(d, (x, 402 - i * 6, x + 22, 416), 4, TXT)
rrect(d, (1412, 396, 1452, 416), 5, TXT)

# 岛本体
ix0, ix1, iy0, iy1 = 560, 1040, 366, 454
rrect(d, (ix0, iy0, ix1, iy1), (iy1 - iy0) // 2, ISLAND, (40, 44, 52), 2)
d.text((ix0 + 40, iy0 + 30), "一代用行动接过", font=font(32, True), fill=TXT)
d.ellipse((ix1 - 56, iy0 + 36, ix1 - 40, iy0 + 52), fill=GREEN)

d.text((120, 505), "只显示当前行前 8 个字（左右各 4 字），不打断用户当前应用；点击回到 App。",
       font=font(24), fill=SUB)

# ── Card B：大岛 ─────────────────────────────────────────────
rrect(d, (80, 620, 1520, 1040), 28, CARD, BORDER, 2)
d.text((120, 656), "大岛（展开态 · 播放中）", font=font(34, True), fill=INK)

rrect(d, (120, 720, 1480, 960), 24, STRIP)
rrect(d, (200, 750, 1320, 930), 40, ISLAND, (40, 44, 52), 2)
d.ellipse((236, 806, 300, 870), fill=ICONBG)
d.polygon([(258, 822), (258, 854), (284, 838)], fill=TXT)

d.text((330, 792), "一代用行动接过传承", font=font(40, True), fill=TXT)
d.text((330, 848), "第 12 / 40 行 · 提词器", font=font(26), fill=MUTED)

# 进度条
rrect(d, (330, 900, 1230, 910), 5, ICONBG)
rrect(d, (330, 900, 330 + int(900 * 0.3), 910), 5, ACCENT)

d.text((120, 985), "展开显示当前行 + 进度；播完 / 关闭后立即消失，不常驻。",
       font=font(24), fill=SUB)

# ── Card C：ticker ──────────────────────────────────────────
rrect(d, (80, 1080, 1520, 1400), 28, CARD, BORDER, 2)
d.text((120, 1116), "状态栏 ticker（OS1 / OS2 兼容）", font=font(34, True), fill=INK)

rrect(d, (120, 1180, 1480, 1300), 24, STRIP)
d.text((160, 1222), "20:24", font=font(24), fill=TXT)
rrect(d, (420, 1196, 980, 1268), 36, ISLAND, (40, 44, 52), 2)
d.ellipse((444, 1216, 476, 1248), fill=ICONBG)
d.text((492, 1218), "一代用行动接过", font=font(28), fill=TXT)
for i, x in enumerate((1300, 1340, 1380)):
    rrect(d, (x, 1242 - i * 6, x + 22, 1256), 4, TXT)

d.text((120, 1330), "老系统上退化为一行动态文字，内容和岛保持一致。", font=font(24), fill=SUB)

d.text((80, 1460), "提词器 v1.20 · 包名 com.dsh.tp · package Aether-whr/PromptFlow",
       font=font(24), fill=(150, 156, 166))

img.save("/sdcard/Download/DSH-Go/岛样式示意.png")
img.save("/root/workspace/tp/island-style.png")
print("saved", img.size)
