package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * MOBI / AZW / KF8 解析。
 * 只支持未加密 + PalmDOC 压缩（compression=2）或明文（=1）；
 * HUFF/CDIC 压缩或 DRM 文件会明确报错，而不是给出乱码。
 */
public final class MobiParser {

    private MobiParser() {
    }

    public static ParseResult parse(File f) throws Exception {
        byte[] data = IoUtil.readBytes(f);
        if (data.length < 128) {
            throw new java.io.IOException("MOBI 文件过小");
        }
        String type = new String(data, 60, 8, Charset.forName("ISO-8859-1"));
        if (!type.startsWith("BOOKMOBI") && !type.startsWith("TEXtREAd")) {
            // 有些工具写的是其它 type，继续尝试
        }
        int numRecords = u16(data, 0x4C);
        if (numRecords <= 1 || numRecords > 65535) {
            throw new java.io.IOException("MOBI 记录表异常");
        }
        int rec0Off = (int) u32(data, 0x4E);
        if (rec0Off <= 0 || rec0Off + 24 >= data.length) {
            throw new java.io.IOException("MOBI 头部损坏");
        }
        int compression = u16(data, rec0Off);
        int textLength = (int) u32(data, rec0Off + 4);
        int textRecords = u16(data, rec0Off + 8);
        int encryption = u16(data, rec0Off + 12);
        if (encryption != 0) {
            throw new java.io.IOException("这本书有 DRM 加密，暂不支持");
        }
        if (compression != 1 && compression != 2) {
            throw new java.io.IOException("MOBI 压缩方式不受支持（HUFF/CDIC），请转成 EPUB 再导入");
        }

        int encoding = 65001;
        int firstNonBook = textRecords;
        if (rec0Off + 16 + 0x18 < data.length) {
            String mobiId = new String(data, rec0Off + 16, 4, Charset.forName("ISO-8859-1"));
            if ("MOBI".equals(mobiId)) {
                encoding = u16(data, rec0Off + 16 + 0x0C);
                int fnb = (int) u32(data, rec0Off + 16 + 0x50);
                if (fnb > 0 && fnb < textRecords) {
                    firstNonBook = fnb;
                }
            }
        }
        Charset cs = encoding == 1252 ? Charset.forName("windows-1252") : Charset.forName("UTF-8");

        ByteArrayOutputStream text = new ByteArrayOutputStream(Math.max(1024, textLength));
        int limit = Math.min(firstNonBook, textRecords);
        for (int i = 1; i <= limit; i++) {
            int off = (int) u32(data, 0x4E + i * 8);
            int nextOff = (i + 1 <= numRecords) ? (int) u32(data, 0x4E + (i + 1) * 8) : data.length;
            if (off <= 0 || off >= data.length) {
                continue;
            }
            if (nextOff <= off || nextOff > data.length) {
                nextOff = data.length;
            }
            if (compression == 1) {
                // 明文：严格按记录边界截取，避免把下一条记录算进来
                text.write(data, off, nextOff - off);
            } else {
                decompressPalmDoc(data, off, nextOff, text);
            }
        }

        String html = new String(text.toByteArray(), cs);
        html = html.replace('\u0000', ' ');

        ParseResult r = new ParseResult();
        r.format = "MOBI";
        r.title = BookUtil.stripExt(f.getName());

        List<Block> blocks = HtmlText.toBlocks(html);
        if (blocks.isEmpty()) {
            blocks.add(Block.p(html.replaceAll("<[^>]+>", " ").trim()));
        }
        BookUtil.chapterize(r, blocks, r.title);
        if (r.chapters.isEmpty()) {
            throw new java.io.IOException("MOBI 没有可读正文");
        }
        BookUtil.pruneEmpty(r);
        return r;
    }

    /** PalmDOC LZ77 变体解压（以记录为单位，不跨记录引用） */
    private static void decompressPalmDoc(byte[] data, int off, int endLimit, ByteArrayOutputStream out) {
        int end = Math.min(data.length, endLimit);
        int capacity = Math.min(Math.max(8192, end - off), 1024 * 1024);
        byte[] tmp = new byte[capacity];
        int op = 0;
        int i = off;
        while (i < end) {
            if (op + 64 >= tmp.length) {
                tmp = grow(tmp);
            }
            int c = data[i++] & 0xFF;
            if (c == 0) {
                tmp[op++] = 0;
            } else if (c <= 8) {
                for (int k = 0; k < c && i < end; k++) {
                    if (op >= tmp.length) {
                        tmp = grow(tmp);
                    }
                    tmp[op++] = data[i++];
                }
            } else if (c <= 0x7F) {
                tmp[op++] = (byte) c;
            } else if (c <= 0xBF) {
                if (i >= end) {
                    break;
                }
                int c2 = data[i++] & 0xFF;
                int pair = (c << 8) | c2;
                int dist = (pair >> 3) & 0x07FF;
                int len = (pair & 0x07) + 3;
                for (int k = 0; k < len; k++) {
                    if (op >= tmp.length) {
                        tmp = grow(tmp);
                    }
                    int src = op - dist;
                    tmp[op++] = (src >= 0 && src < op) ? tmp[src] : (byte) ' ';
                }
            } else {
                if (op + 2 >= tmp.length) {
                    tmp = grow(tmp);
                }
                tmp[op++] = (byte) ' ';
                tmp[op++] = (byte) (c ^ 0x80);
            }
        }
        out.write(tmp, 0, op);
    }

    private static byte[] grow(byte[] a) {
        byte[] b = new byte[a.length * 2];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    private static int u16(byte[] d, int off) {
        if (off + 1 >= d.length) {
            return 0;
        }
        return ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
    }

    private static long u32(byte[] d, int off) {
        if (off + 3 >= d.length) {
            return 0;
        }
        return ((long) (d[off] & 0xFF) << 24) | ((d[off + 1] & 0xFF) << 16)
                | ((d[off + 2] & 0xFF) << 8) | (d[off + 3] & 0xFF);
    }
}
