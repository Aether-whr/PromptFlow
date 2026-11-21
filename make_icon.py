#!/usr/bin/env python3
# 生成提词器图标：蓝色圆角方块 + 三行白色文字条
import zlib, struct

S = 576          # 采样分辨率（4x supersample）
OUT = 144
BG = (47, 111, 237)
FG = (255, 255, 255)

def in_round_rect(x, y, x0, y0, x1, y1, r):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    cx = min(max(x, x0 + r), x1 - r)
    cy = min(max(y, y0 + r), y1 - r)
    dx, dy = x - cx, y - cy
    return dx * dx + dy * dy <= r * r

def render():
    px = bytearray(S * S * 4)
    # 背景圆角方块
    br = 132.0 * 4
    bars = [
        (96 * 4, 156 * 4, 480 * 4, 200 * 4),
        (96 * 4, 256 * 4, 480 * 4, 300 * 4),
        (96 * 4, 356 * 4, 400 * 4, 400 * 4),
    ]
    for y in range(S):
        for x in range(S):
            i = (y * S + x) * 4
            r, g, b, a = 0, 0, 0, 0
            if in_round_rect(x, y, 12, 12, S - 12, S - 12, br):
                r, g, b, a = BG[0], BG[1], BG[2], 255
                for (x0, y0, x1, y1) in bars:
                    if in_round_rect(x, y, x0, y0, x1, y1, (y1 - y0) / 2.0):
                        r, g, b = FG
                        break
            px[i:i + 4] = bytes((r, g, b, a))
    return px

def downsample(px):
    out = bytearray(OUT * OUT * 4)
    for y in range(OUT):
        for x in range(OUT):
            acc = [0, 0, 0, 0]
            for dy in range(4):
                for dx in range(4):
                    i = ((y * 4 + dy) * S + (x * 4 + dx)) * 4
                    for k in range(4):
                        acc[k] += px[i + k]
            o = (y * OUT + x) * 4
            for k in range(4):
                out[o + k] = acc[k] // 16
    return out

def chunk(tag, data):
    c = struct.pack(">I", len(data)) + tag + data
    return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

def write_png(path, w, h, rgba):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(rgba[y * w * 4:(y + 1) * w * 4])
    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    with open(path, "wb") as f:
        f.write(png)

write_png("/root/workspace/tp/res/mipmap-xxhdpi/ic_launcher.png", OUT, OUT,
          downsample(render()))
print("ok")
