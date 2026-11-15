# -*- coding: utf-8 -*-
"""离线预览自适应图标（不依赖 PIL）：把 ic_launcher_background/foreground 的几何
在 108x108 viewport 里扫描线光栅化，裁切 18..90 的可见区域并按圆形遮罩输出 PNG。"""
import zlib, struct, math

SS = 4          # 垂直子采样
W = H = 216     # 输出边长
VIS_LO, VIS_HI = 18.0, 90.0
SCALE = W / (VIS_HI - VIS_LO)


def cubic(p0, p1, p2, p3, n=24):
    pts = []
    for i in range(1, n + 1):
        t = i / n
        mt = 1 - t
        x = mt**3*p0[0] + 3*mt*mt*t*p1[0] + 3*mt*t*t*p2[0] + t**3*p3[0]
        y = mt**3*p0[1] + 3*mt*mt*t*p1[1] + 3*mt*t*t*p2[1] + t**3*p3[1]
        pts.append((x, y))
    return pts


def left_page():
    p = [(29.6, 39.86)]
    p += cubic((29.6, 39.86), (35.76, 36.34), (45.68, 36.34), (53.52, 41.14))
    p.append((53.52, 70.42))
    p += cubic((53.52, 70.42), (45.68, 65.62), (35.76, 65.62), (29.6, 69.14))
    return p


def right_page():
    p = [(78.4, 39.86)]
    p += cubic((78.4, 39.86), (72.24, 36.34), (62.32, 36.34), (54.48, 41.14))
    p.append((54.48, 70.42))
    p += cubic((54.48, 70.42), (62.32, 65.62), (72.24, 65.62), (78.4, 69.14))
    return p


SPINE = [(52.88, 41.2), (55.12, 41.2), (55.12, 70.5), (52.88, 70.5)]
RIBBON = [(68.5, 40.6), (73.6, 40.6), (73.6, 56.4), (71.05, 52.9), (68.5, 56.4)]


def spans(poly, y):
    """单个扫描行（含 SS 子采样）的 even-odd 填充区间。"""
    xs = []
    n = len(poly)
    for i in range(n):
        x1, y1 = poly[i]
        x2, y2 = poly[(i + 1) % n]
        if y1 == y2:
            continue
        if min(y1, y2) <= y < max(y1, y2):
            xs.append(x1 + (y - y1) * (x2 - x1) / (y2 - y1))
    xs.sort()
    return [(xs[i], xs[i + 1]) for i in range(0, len(xs) - 1, 2)]


def render():
    bg_start = (0xFF, 0x7A, 0x45)
    img = bytearray(W * H * 3)

    # 背景：angle=315 的线性渐变（起点左上，终点右下）
    for py in range(H):
        for px in range(W):
            dx = (px + 0.5) / SCALE + VIS_LO
            dy = (py + 0.5) / SCALE + VIS_LO
            t = ((dx - VIS_LO) + (dy - VIS_LO)) / (2 * (VIS_HI - VIS_LO))
            t = min(1.0, max(0.0, t))
            if t < 0.5:
                k = t * 2
                c0, c1 = bg_start, (0xFF, 0x5A, 0x2C)
            else:
                k = (t - 0.5) * 2
                c0, c1 = (0xFF, 0x5A, 0x2C), (0xE2, 0x3E, 0x14)
            r = c0[0] + (c1[0] - c0[0]) * k
            g = c0[1] + (c1[1] - c0[1]) * k
            b = c0[2] + (c1[2] - c0[2]) * k
            o = (py * W + px) * 3
            img[o], img[o + 1], img[o + 2] = int(r), int(g), int(b)

    layers = [
        (left_page(), (0xFF, 0xFF, 0xFF)),
        (right_page(), (0xFF, 0xF3, 0xEE)),
        (SPINE, (0xFF, 0xE2, 0xD3)),
        (RIBBON, (0xFF, 0x5A, 0x2C)),
    ]
    for poly, color in layers:
        cov = bytearray(W * H)
        for py in range(H):
            for s in range(SS):
                yy = (py + (s + 0.5) / SS) / SCALE + VIS_LO
                for x1, x2 in spans(poly, yy):
                    a = (x1 - VIS_LO) * SCALE
                    b = (x2 - VIS_LO) * SCALE
                    ia, ib = max(0, int(round(a))), min(W, int(round(b)))
                    for px in range(ia, ib):
                        idx = py * W + px
                        if cov[idx] < SS:
                            cov[idx] += 1
        for idx, c in enumerate(cov):
            if c == 0:
                continue
            k = c / SS
            o = idx * 3
            img[o] = int(img[o] + (color[0] - img[o]) * k)
            img[o + 1] = int(img[o + 1] + (color[1] - img[o + 1]) * k)
            img[o + 2] = int(img[o + 2] + (color[2] - img[o + 2]) * k)

    # 圆形遮罩 + 抗锯齿边缘
    cx = cy = W / 2.0
    rad = W / 2.0 - 1.0
    for py in range(H):
        for px in range(W):
            d = math.hypot(px + 0.5 - cx, py + 0.5 - cy)
            a = min(1.0, max(0.0, rad - d + 0.5))
            o = (py * W + px) * 3
            for i in range(3):
                img[o + i] = int(255 * (1 - a) + img[o + i] * a)

    raw = bytearray()
    for y in range(H):
        raw.append(0)
        raw += img[y * W * 3:(y + 1) * W * 3]

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", W, H, 8, 2, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    return png


if __name__ == "__main__":
    data = render()
    with open("build/icon_preview.png", "wb") as f:
        f.write(data)
    print("icon_preview.png", len(data), "bytes")
