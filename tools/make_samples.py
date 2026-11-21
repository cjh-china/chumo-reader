# -*- coding: utf-8 -*-
"""生成 PDF / CBZ 测试样本（纯标准库，不依赖第三方包）。

PDF：手写 PDF 1.4 结构，两页，含标题、正文、矩形填充。
CBZ：三张 PNG，带大号页码，方便肉眼判断翻页是否正确。

音频样本不进仓库（体积不值当），需要时用 ffmpeg 现造一条 30 秒的
（MusicLibrary 会过滤 20 秒以下的文件，所以时长要够）：

    ffmpeg -y -f lavfi -i sine=frequency=440:duration=30 \
           -ac 2 -ar 44100 -b:a 96k tools/samples/tone30.mp3
"""

import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "tools", "samples")


# ---------------------------------------------------------------- PNG

def write_png(path, w, h, pixels):
    """pixels: 每行一个 bytearray，RGB888。"""
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        raw.extend(pixels[y])
    comp = zlib.compress(bytes(raw), 9)

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", ihdr))
        f.write(chunk(b"IDAT", comp))
        f.write(chunk(b"IEND", b""))


# 7 段数码管风格的数字，画大一点方便辨认
SEG = {
    "0": "abcdef", "1": "bc", "2": "abdeg", "3": "abcdg", "4": "bcfg",
    "5": "acdfg", "6": "acdefg", "7": "abc", "8": "abcdefg", "9": "abcdfg",
}


def draw_digit(px, w, h, x0, y0, dw, dh, digit, color):
    segs = SEG.get(digit, "")
    t = max(4, dw // 5)  # 笔画粗细

    def fill(x1, y1, x2, y2):
        for yy in range(max(0, y1), min(h, y2)):
            row = px[yy]
            for xx in range(max(0, x1), min(w, x2)):
                row[xx * 3:xx * 3 + 3] = color

    gap = max(2, t // 3)
    # a: 上横  b: 右上竖  c: 右下竖  d: 下横  e: 左下竖  f: 左上竖  g: 中横
    if "a" in segs:
        fill(x0 + gap + t // 2, y0, x0 + dw - gap - t // 2, y0 + t)
    if "g" in segs:
        fill(x0 + gap + t // 2, y0 + dh // 2 - t // 2,
             x0 + dw - gap - t // 2, y0 + dh // 2 + t // 2)
    if "d" in segs:
        fill(x0 + gap + t // 2, y0 + dh - t, x0 + dw - gap - t // 2, y0 + dh)
    if "f" in segs:
        fill(x0, y0 + gap + t // 2, x0 + t, y0 + dh // 2 - gap)
    if "b" in segs:
        fill(x0 + dw - t, y0 + gap + t // 2, x0 + dw, y0 + dh // 2 - gap)
    if "e" in segs:
        fill(x0, y0 + dh // 2 + gap, x0 + t, y0 + dh - gap - t // 2)
    if "c" in segs:
        fill(x0 + dw - t, y0 + dh // 2 + gap, x0 + dw, y0 + dh - gap - t // 2)


def make_page_png(path, index, total):
    w, h = 800, 1200
    bg = (247, 243, 236)
    px = [bytearray(bytes(bg) * w) for _ in range(h)]

    # 顶部色带，每页颜色不同，便于确认顺序
    tints = [(214, 92, 58), (58, 122, 150), (110, 150, 92)]
    tint = tints[index % len(tints)]
    band = int(h * 0.06)
    for y in range(band):
        row = px[y]
        for x in range(w):
            row[x * 3:x * 3 + 3] = bytes(tint)

    # 几个占位"段落"色块
    block = (214, 206, 194)
    for i in range(6):
        y0 = int(h * 0.16) + i * int(h * 0.10)
        bx0, bx1 = int(w * 0.10), int(w * 0.90) - (0 if i % 2 == 0 else int(w * 0.22))
        for y in range(y0, y0 + int(h * 0.045)):
            if 0 <= y < h:
                row = px[y]
                for x in range(bx0, bx1):
                    row[x * 3:x * 3 + 3] = bytes(block)

    # 大页码
    text = str(index + 1)
    dw = int(w * 0.16)
    dh = int(dw * 1.9)
    total_w = len(text) * (dw + 14) - 14
    x = (w - total_w) // 2
    y = int(h * 0.84)
    for ch in text:
        draw_digit(px, w, h, x, y, dw, dh, ch, bytes(tint))
        x += dw + 14

    # 底部小标记（第 n / total 页的点）
    dot = int(w * 0.018)
    for i in range(total):
        cx = int(w / 2 - (total - 1) * dot * 1.5) + i * dot * 3
        for yy in range(int(h * 0.955), int(h * 0.955) + dot):
            row = px[yy]
            for xx in range(cx, cx + dot):
                row[xx * 3:xx * 3 + 3] = bytes((120, 110, 100) if i != index else tint)

    write_png(path, w, h, px)


# ---------------------------------------------------------------- PDF

def make_pdf(path, pages):
    """pages: [(title, [line, ...]), ...] 只支持 ASCII。"""
    objs = []

    def add(body):
        objs.append(body)
        return len(objs)

    catalog = add(None)          # 1
    pages_id = add(None)         # 2
    font_id = add(b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>")

    kids = []
    for title, lines in pages:
        ops = ["BT", "/F1 28 Tf", "72 760 Td", "(%s) Tj" % _esc(title), "ET"]
        ops += ["0.84 0.36 0.23 rg", "72 742 451 4 re", "f", "0 0 0 rg"]
        y = 700
        for ln in lines:
            ops += ["BT", "/F1 13 Tf", "72 %d Td" % y, "(%s) Tj" % _esc(ln), "ET"]
            y -= 22
        # 一个浅灰底框，方便确认渲染范围
        ops = ["0.95 0.94 0.91 rg", "60 90 475 660 re", "f", "0 0 0 rg"] + ops
        content = "\n".join(ops).encode("latin-1")
        cid = add(b"<< /Length %d >>\nstream\n" % len(content) + content + b"\nendstream")
        pid = add(("<< /Type /Page /Parent %d 0 R /MediaBox [0 0 595 842] "
                   "/Resources << /Font << /F1 %d 0 R >> >> /Contents %d 0 R >>"
                   % (pages_id, font_id, cid)).encode("latin-1"))
        kids.append(pid)

    objs[catalog - 1] = b"<< /Type /Catalog /Pages %d 0 R >>" % pages_id
    objs[pages_id - 1] = ("<< /Type /Pages /Kids [%s] /Count %d >>"
                          % (" ".join("%d 0 R" % k for k in kids), len(kids))
                          ).encode("latin-1")

    out = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
    offsets = [0]
    for i, body in enumerate(objs, 1):
        offsets.append(len(out))
        out += b"%d 0 obj\n" % i + body + b"\nendobj\n"
    start_xref = len(out)
    out += b"xref\n0 %d\n" % (len(objs) + 1)
    out += b"0000000000 65535 f \n"
    for off in offsets[1:]:
        out += b"%010d 00000 n \n" % off
    out += (b"trailer\n<< /Size %d /Root %d 0 R >>\nstartxref\n%d\n%%%%EOF\n"
            % (len(objs) + 1, catalog, start_xref))
    with open(path, "wb") as f:
        f.write(out)


def _esc(s):
    return s.replace("\\", r"\\").replace("(", r"\(").replace(")", r"\)")


# ---------------------------------------------------------------- main

def main():
    os.makedirs(OUT, exist_ok=True)

    pdf = os.path.join(OUT, "sample.pdf")
    make_pdf(pdf, [
        ("The Lighthouse in the Fog", [
            "Chapter One - A Signal Across the Water",
            "",
            "The lamp turned once every twelve seconds, and each time it",
            "swept the harbour the fog pulled back just enough to show",
            "a line of black water. Keeper Aldous had counted those turns",
            "for nineteen years.",
            "",
            "This page exists to verify that PdfRenderer output is",
            "displayed with the correct aspect ratio and that page",
            "turning works in image mode.",
        ]),
        ("Chapter Two - The Second Light", [
            "By the third night there were two lights on the horizon,",
            "one answering the other, and neither of them belonged to",
            "any ship in the registry.",
            "",
            "Page two. If you can read this, the second page rendered",
            "correctly and the pager advanced.",
        ]),
    ])
    print("PDF  ->", pdf, os.path.getsize(pdf), "bytes")

    cbz = os.path.join(OUT, "sample.cbz")
    tmp = []
    for i in range(3):
        p = os.path.join(OUT, "_cbz_%02d.png" % i)
        make_page_png(p, i, 3)
        tmp.append(p)
    import zipfile
    with zipfile.ZipFile(cbz, "w", zipfile.ZIP_STORED) as z:
        for i, p in enumerate(tmp):
            z.write(p, "page-%02d.png" % (i + 1))
    for p in tmp:
        os.remove(p)
    print("CBZ  ->", cbz, os.path.getsize(cbz), "bytes")


if __name__ == "__main__":
    main()
