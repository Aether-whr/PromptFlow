#!/usr/bin/env python3
# 生成「提词器」宣传视频：720x1280 / 30fps，直接管道送给 ffmpeg
import math
import subprocess
from PIL import Image, ImageChops, ImageDraw, ImageFont
import imageio_ffmpeg

W, H = 720, 1280
FPS = 30
FONT_PATH = "/system/fonts/MiSansVF.ttf"

INK = (20, 22, 26)
SUB = (120, 126, 136)
PAGE = (245, 246, 248)
CARD = (255, 255, 255)
ACCENT = (47, 111, 237)
GREEN = (74, 222, 128)
BLACK = (0, 0, 0)
WHITE = (255, 255, 255)


def font(size, bold=False):
    f = ImageFont.truetype(FONT_PATH, size)
    if bold:
        try:
            f.set_variation_by_name("Bold")
        except Exception:
            pass
    return f


def tw(d, text, f):
    return d.textlength(text, font=f)


def center(d, cx, y, text, f, fill):
    d.text((cx - tw(d, text, f) / 2, y), text, font=f, fill=fill)


def ease(t, a=0.0, b=1.0):
    t = max(0.0, min(1.0, (t - a) / (b - a)))
    return 1 - (1 - t) ** 3


def pill(d, box, r, fill, outline=None, width=2):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def status_bar(d, dark=False):
    fg = (255, 255, 255) if dark else (28, 30, 34)
    d.text((36, 26), "20:24", font=font(26, True), fill=fg)
    for i, x in enumerate((560, 592, 624)):
        d.rounded_rectangle((x, 34 - i * 5, x + 18, 46), radius=3, fill=fg)
    d.rounded_rectangle((654, 30, 684, 46), radius=5, fill=fg)


SCRIPT = [
    "大家好，今天聊聊一个特别小的提词器。",
    "它只有六十多 KB，原生安卓，没有广告。",
    "把台词粘进来，点一下开始播放，",
    "字号、速度、背景颜色，都能自己调。",
    "横屏竖屏随你，还能开悬浮窗，",
    "或者缩成顶上一个小胶囊。",
    "念到哪里，它就跟到哪里。",
]


def wrap(d, text, f, maxw):
    out, line = [], ""
    for ch in text:
        if tw(d, line + ch, f) > maxw:
            out.append(line)
            line = ch
        else:
            line += ch
    if line:
        out.append(line)
    return out


# ---------------------------------------------------------------- 开场
def s_intro(t):
    img = Image.new("RGB", (W, H), (11, 12, 16))
    d = ImageDraw.Draw(img)
    a = ease(t, 0.0, 0.8)
    y = 470 + (1 - a) * 30
    col = tuple(int(c * a) for c in (243, 244, 246))
    center(d, W / 2, y, "提词器", font(150, True), col)
    center(d, W / 2, y + 190, "轻量 · 原生 · 不用背稿", font(38), (150, 156, 168))
    if t > 1.2:
        a2 = ease(t, 1.2, 2.0)
        c2 = tuple(int(c * a2) for c in (110, 116, 128))
        center(d, W / 2, 940, "github.com/Aether-whr/PromptFlow", font(28), c2)
    center(d, W / 2, 1160, "v1.32", font(24), (70, 74, 84))
    return img


# ---------------------------------------------------------------- 编辑页
EDITOR_TEXT = "今天想跟你聊聊，一个特别小的提词器。\n它只有六十多 KB，原生安卓，没有广告，也不用联网。\n把台词粘进来，点一下开始播放，字号、速度、背景颜色都能自己调。"


def s_editor(t):
    img = Image.new("RGB", (W, H), PAGE)
    d = ImageDraw.Draw(img)
    status_bar(d, dark=False)

    # 顶栏
    d.text((36, 84), "提词器", font=font(44, True), fill=INK)
    chips = (("导入", (230, 245, 236), (31, 122, 77)),
             ("保存", (233, 240, 254), ACCENT),
             ("清空", (253, 243, 220), (138, 106, 34)),
             ("设置", (255, 255, 255), (74, 81, 96)))
    widths = [tw(d, label, font(28)) + 44 for label, _, _ in chips]
    x = W - 36 - (sum(widths) + 14 * (len(chips) - 1))
    for (label, bg, fg), w in zip(chips, widths):
        pill(d, (x, 92, x + w, 140), 24, bg)
        center(d, x + w / 2, 102, label, font(28), fg)
        x += w + 14

    # 输入卡片
    pill(d, (36, 180, 684, 800), 36, CARD, (225, 228, 234), 2)
    f = font(30)
    reveal = int(len(EDITOR_TEXT) * ease(t, 0.4, 2.6))
    shown = EDITOR_TEXT[:reveal]
    y = 214
    for para in shown.split("\n"):
        for line in wrap(d, para, f, 600):
            d.text((66, y), line, font=f, fill=(30, 32, 38))
            y += 46
        y += 18

    # 字数
    n = len(shown)
    f24 = font(24)
    d.text((40, 830), f"共 {n} 字 · 每行约 4 字", font=f24, fill=SUB)
    right = "从光标处播放"
    xr = W - 36 - tw(d, right, f24)
    d.text((xr, 830), right, font=f24, fill=ACCENT)
    left = "保存的文案"
    xl = xr - 26 - tw(d, left, f24)
    d.text((xl, 830), left, font=f24, fill=ACCENT)

    # 开始按钮
    press = 1.0 - 0.06 * math.sin(min(1.0, max(0.0, (t - 4.6) / 0.18)) * math.pi)
    bh = 96 * press
    y0 = 1000 + (96 - bh) / 2
    pill(d, (36, y0, 684, y0 + bh), 34, ACCENT)
    center(d, W / 2, y0 + 26, "开始播放", font(36, True), WHITE)

    # 点按波纹
    if 4.6 < t < 5.4:
        r = 30 + (t - 4.6) * 260
        a = max(0.0, 1 - (t - 4.6) / 0.8)
        d.ellipse((W / 2 - r, 1048 - r, W / 2 + r, 1048 + r),
                  outline=(255, 255, 255), width=3)
    return img


# ---------------------------------------------------------------- 全屏播放
LINES = None


def s_play(t):
    global LINES
    img = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(img)
    if LINES is None:
        LINES = []
        f = font(96, True)
        for para in SCRIPT:
            LINES.extend(wrap(d, para, f, 620))

    f = font(96, True)
    lh = 132
    off = max(0.0, (t - 0.3)) * 58
    y = 300 - off
    for i, line in enumerate(LINES):
        yy = y + i * lh
        if -100 < yy < H + 60:
            d.text((46, yy), line, font=f, fill=(246, 246, 248))

    # 上下渐隐
    for i in range(60):
        a = int(255 * (1 - i / 60) * 0.9)
        d.line((0, i, W, i), fill=(0, 0, 0))
        d.line((0, H - i, W, H - i), fill=(0, 0, 0))

    # 控制条
    if t > 2.6:
        k = ease(t, 2.6, 3.1)
        y0 = H - 30 - 210 * k
        d.rounded_rectangle((20, y0, W - 20, y0 + 190), radius=34, fill=(20, 20, 20))
        pill(d, (44, y0 + 24, 148, y0 + 76), 20, (58, 58, 58))
        center(d, 96, y0 + 34, "从头", font(26), WHITE)
        pill(d, (160, y0 + 24, 264, y0 + 76), 20, (86, 86, 86))
        center(d, 212, y0 + 34, "暂停", font(26), WHITE)
        d.rounded_rectangle((280, y0 + 44, 560, y0 + 50), radius=3, fill=(70, 70, 70))
        d.rounded_rectangle((280, y0 + 44, 420, y0 + 50), radius=3, fill=WHITE)
        pill(d, (576, y0 + 24, 676, y0 + 76), 20, (255, 120, 120))
        center(d, 626, y0 + 34, "退出", font(26), (60, 20, 20))
        d.text((46, y0 + 108), "第 2 / 12 行", font=font(22), fill=(150, 150, 150))
        d.text((560, y0 + 108), "20%", font=font(22), fill=(150, 150, 150))
    return img


# ---------------------------------------------------------------- 顶部胶囊
def s_capsule(t):
    # 相机取景背景
    img = Image.new("RGB", (W, H), (18, 20, 24))
    d = ImageDraw.Draw(img)
    for i, (cx, cy, r, c) in enumerate((
            (120, 320, 90, (46, 52, 62)), (600, 260, 70, (38, 44, 54)),
            (540, 900, 120, (40, 46, 56)), (150, 1020, 80, (34, 40, 48)))):
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=c)
    d.text((40, 158), "正在拍摄…", font=font(26), fill=(120, 126, 136))

    # 胶囊（单行滚动）
    cw, ch = 520, 96
    x0, y0 = (W - cw) // 2, 40
    f = font(42, True)
    text = "把台词粘进来 点一下就开始滚"
    tx = 60 - (t * 90) % (tw(d, text, f) + 200)
    # 胶囊底色（不透明）
    d.rounded_rectangle((x0, y0, x0 + cw, y0 + ch), radius=ch // 2, fill=(8, 9, 12))
    # 文字单独一层，边缘按渐变擦除，只擦文字不擦底色
    layer = Image.new("RGBA", (cw, ch), (0, 0, 0, 0))
    ImageDraw.Draw(layer).text((tx, 18), text, font=f, fill=(243, 244, 246, 255))
    mask = Image.new("L", (cw, ch), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, cw, ch), radius=ch // 2, fill=255)
    grad = Image.new("L", (cw, 1))
    gp = grad.load()
    for x in range(cw):
        if x < 42:
            v = int(255 * x / 42)
        elif x > cw - 92:
            v = int(255 * max(0.0, (cw - 92 - x) / 42.0))
        else:
            v = 255
        gp[x, 0] = v
    mask = ImageChops.multiply(mask, grad.resize((cw, ch)))
    # 只用「文字自身的 alpha × 渐变」当蒙版，避免把透明区域的黑色涂上去
    alpha = ImageChops.multiply(layer.getchannel("A"), mask)
    img.paste(layer, (x0, y0), alpha)
    d.rounded_rectangle((x0, y0, x0 + cw, y0 + ch), radius=ch // 2,
                        outline=(60, 64, 74), width=2)
    # 进度环
    d.arc((x0 + cw - 70, y0 + 26, x0 + cw - 22, y0 + 74), -90, -90 + 300,
          fill=(74, 222, 128), width=4)

    # 双击 → 变成悬浮窗
    if t > 2.4:
        k = ease(t, 2.4, 3.2)
        w2, h2 = int(300 + 240 * k), int(70 + 260 * k)
        x2, y2 = int((W - w2) / 2), int(90 + 90 * k)
        d.rounded_rectangle((x2, y2, x2 + w2, y2 + h2), radius=26,
                            fill=(10, 10, 12), outline=(70, 74, 84), width=2)
        f2 = font(34, True)
        for i, line in enumerate(wrap(d, SCRIPT[3], f2, w2 - 60)):
            d.text((x2 + 28, y2 + 26 + i * 44), line, font=f2, fill=(240, 240, 244))
        d.text((x2 + 28, y2 + h2 - 46), "双击放大成悬浮窗", font=font(20), fill=(140, 146, 156))
    return img


# ---------------------------------------------------------------- 设置面板
def s_settings(t):
    img = Image.new("RGB", (W, H), PAGE)
    d = ImageDraw.Draw(img)
    status_bar(d, dark=False)
    d.text((36, 84), "提词器", font=font(44, True), fill=INK)
    # 背景压暗
    img = img.point(lambda v: int(v * 0.45 + 110))
    d = ImageDraw.Draw(img)

    k = ease(t, 0.3, 1.0)
    top = int((H + 40) - (H + 40 - 380) * k)
    d.rounded_rectangle((24, top, W - 24, H + 40), radius=44, fill=WHITE)
    d.rounded_rectangle((W / 2 - 30, top + 20, W / 2 + 30, top + 28), radius=4, fill=(216, 220, 227))
    d.text((56, top + 60), "设置", font=font(38, True), fill=INK)
    pill(d, (560, top + 60, 664, top + 112), 26, ACCENT)
    center(d, 612, top + 70, "完成", font(26), WHITE)

    def section(y, label):
        d.text((56, y), label, font=font(24), fill=SUB)
        return y + 46

    y = section(top + 150, "滚动方式")
    for i, label in enumerate(("整屏上滚", "单行横移")):
        x = 56 + i * 170
        sel = i == 0
        pill(d, (x, y, x + 150, y + 64), 32, ACCENT if sel else (250, 250, 252),
             None if sel else (222, 226, 232), 2)
        center(d, x + 75, y + 16, label, font(26), WHITE if sel else (74, 81, 96))
    y += 106

    y = section(y, "播放方式")
    for i, label in enumerate(("全屏", "悬浮窗", "顶部胶囊")):
        x = 56 + i * 140
        prog = ease(t, 1.2 + i * 0.18, 1.6 + i * 0.18) if i == 2 else 0
        sel = i == 2
        if sel:
            c = tuple(int(a + (b - a) * prog) for a, b in zip((255, 255, 255), ACCENT))
            tc = tuple(int(a + (b - a) * prog) for a, b in zip((74, 81, 96), (255, 255, 255)))
        else:
            c, tc = (250, 250, 252), (74, 81, 96)
        pill(d, (x, y, x + 124, y + 60), 30, c, None if sel else (222, 226, 232), 2)
        center(d, x + 62, y + 15, label, font(24), tc)
    y += 100

    y = section(y, "字号")
    d.rounded_rectangle((56, y + 22, 664, y + 32), radius=5, fill=(226, 229, 235))
    d.rounded_rectangle((56, y + 22, 430, y + 32), radius=5, fill=ACCENT)
    d.ellipse((408, y + 6, 452, y + 50), fill=WHITE, outline=ACCENT, width=4)
    y += 86

    y = section(y, "速度")
    d.rounded_rectangle((56, y + 22, 664, y + 32), radius=5, fill=(226, 229, 235))
    d.rounded_rectangle((56, y + 22, 300, y + 32), radius=5, fill=ACCENT)
    d.ellipse((278, y + 6, 322, y + 50), fill=WHITE, outline=ACCENT, width=4)
    return img


# ---------------------------------------------------------------- 结尾
def s_outro(t):
    img = Image.new("RGB", (W, H), (11, 12, 16))
    d = ImageDraw.Draw(img)
    a = ease(t, 0.1, 0.8)
    center(d, W / 2, 430, "提词器", font(110, True), (243, 244, 246))
    center(d, W / 2, 580, "v1.32 · 60 KB · 开源无广告", font(30), (150, 156, 168))
    if t > 0.8:
        a2 = ease(t, 0.8, 1.5)
        g = int(255 * a2)
        pill(d, (110, 700, W - 110, 796), 48, (g, g, g))
        center(d, W / 2, 726, "github.com/Aether-whr/PromptFlow", font(24), (30, 32, 38))
    center(d, W / 2, 900, "点一下屏幕，就能开始", font(26), (110, 116, 128))
    return img


SCENES = [
    (s_intro, 3.2),
    (s_editor, 5.6),
    (s_play, 5.8),
    (s_capsule, 5.6),
    (s_settings, 4.6),
    (s_outro, 3.2),
]
FADE = 0.45


def render(t):
    starts = []
    acc = 0.0
    for fn, dur in SCENES:
        starts.append(acc)
        acc += dur
    total = acc
    for i, (fn, dur) in enumerate(SCENES):
        if t < starts[i] + dur or i == len(SCENES) - 1:
            local = t - starts[i]
            cur = fn(max(0.0, local))
            if i > 0 and local < FADE:
                prev = SCENES[i - 1][0](SCENES[i - 1][1])
                return Image.blend(prev, cur, local / FADE)
            if i < len(SCENES) - 1 and local > dur - FADE:
                nxt = SCENES[i + 1][0](0.0)
                return Image.blend(cur, nxt, (local - (dur - FADE)) / FADE)
            return cur
    return SCENES[-1][0](0.0)


def main():
    total = sum(d for _, d in SCENES)
    frames = int(total * FPS)
    out = "/sdcard/Download/DSH-Go/提词器-宣传视频.mp4"
    ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
    proc = subprocess.Popen(
        [ffmpeg, "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
         "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "medium",
         "-crf", "20", "-pix_fmt", "yuv420p", "-movflags", "+faststart", out],
        stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for i in range(frames):
        img = render(i / FPS)
        proc.stdin.write(img.tobytes())
        if i % 30 == 0:
            print(f"frame {i}/{frames}", flush=True)
    proc.stdin.close()
    proc.wait()
    print("done", out, f"{total:.1f}s")


if __name__ == "__main__":
    main()
