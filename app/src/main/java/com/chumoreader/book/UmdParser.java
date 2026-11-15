package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Inflater;

/**
 * UMD（手机书老格式）尽力解析。
 * 正文是 zlib 压缩的 UTF-16LE 文本块，这里扫描 zlib 流逐个解压再拼接，
 * 不依赖固定偏移，遇到残缺文件也不会直接崩。
 */
public final class UmdParser {

    private UmdParser() {
    }

    public static ParseResult parse(File f) throws Exception {
        byte[] data = IoUtil.readBytes(f);
        if (data.length < 64) {
            throw new java.io.IOException("UMD 文件过小");
        }
        ParseResult r = new ParseResult();
        r.format = "UMD";
        r.title = BookUtil.stripExt(f.getName());

        StringBuilder sb = new StringBuilder();
        int i = 0;
        int found = 0;
        while (i < data.length - 2) {
            int b0 = data[i] & 0xFF;
            int b1 = data[i + 1] & 0xFF;
            boolean zlib = (b0 == 0x78) && (b1 == 0x9C || b1 == 0x01 || b1 == 0xDA || b1 == 0x5E);
            if (zlib) {
                byte[] out = inflate(data, i);
                if (out != null) {
                    String piece = decodeChunk(out);
                    if (piece != null && piece.trim().length() > 0) {
                        sb.append(piece);
                        found++;
                        i += 2;
                        continue;
                    }
                }
            }
            i++;
        }
        if (found == 0 || sb.length() < 32) {
            throw new java.io.IOException("UMD 正文解压失败（可能是加密文件）");
        }
        String text = cleanup(sb.toString());
        splitIntoChapters(r, text);
        BookUtil.pruneEmpty(r);
        return r;
    }

    private static void splitIntoChapters(ParseResult r, String text) {
        String[] lines = text.split("\n");
        Chapter cur = null;
        StringBuilder buf = new StringBuilder();
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0) {
                if (buf.length() > 0) {
                    if (cur == null) {
                        cur = new Chapter(r.title);
                        r.chapters.add(cur);
                    }
                    cur.add(buf.toString());
                    buf.setLength(0);
                }
                continue;
            }
            if (BookUtil.headingLike(line)) {
                if (buf.length() > 0 && cur != null) {
                    cur.add(buf.toString());
                    buf.setLength(0);
                }
                cur = new Chapter(BookUtil.tidyTitle(line));
                r.chapters.add(cur);
                continue;
            }
            if (buf.length() > 0) {
                buf.append('\n');
            }
            buf.append(line);
        }
        if (buf.length() > 0) {
            if (cur == null) {
                cur = new Chapter(r.title);
                r.chapters.add(cur);
            }
            cur.add(buf.toString());
        }
        if (r.chapters.isEmpty()) {
            Chapter c = new Chapter(r.title);
            c.add(text);
            r.chapters.add(c);
        }
    }

    private static String cleanup(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\r') {
                continue;
            }
            if (c < 32 && c != '\n' && c != '\t') {
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    private static String decodeChunk(byte[] out) {
        if (out.length < 24) {
            return null;
        }
        // 判断是否 UTF-16LE 中文：高字节集中在 0x00 / 0x4E-0x9F / 0x30-0x9F
        int pairs = Math.min(out.length / 2, 4000);
        int zero = 0, cjk = 0;
        for (int i = 0; i < pairs; i++) {
            int hi = out[i * 2 + 1] & 0xFF;
            int lo = out[i * 2] & 0xFF;
            if (hi == 0) {
                zero++;
            }
            if (hi >= 0x34 && hi <= 0x9F && lo >= 0x00) {
                cjk++;
            }
        }
        if ((cjk * 2 > pairs || zero * 2 > pairs) && cjk + zero > pairs / 2) {
            return new String(out, java.nio.charset.Charset.forName("UTF-16LE"));
        }
        String utf8 = new String(out, java.nio.charset.Charset.forName("UTF-8"));
        if (utf8.indexOf('\uFFFD') >= 0) {
            return new String(out, java.nio.charset.Charset.forName("GB18030"));
        }
        return utf8;
    }

    private static byte[] inflate(byte[] data, int off) {
        Inflater inf = new Inflater();
        try {
            inf.setInput(data, off, data.length - off);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(16 * 1024);
            byte[] buf = new byte[16 * 1024];
            int guard = 0;
            while (!inf.finished() && guard++ < 4096) {
                int n = inf.inflate(buf);
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) {
                        break;
                    }
                } else {
                    bos.write(buf, 0, n);
                }
                if (bos.size() > 4 * 1024 * 1024) {
                    break;
                }
            }
            return bos.size() > 64 ? bos.toByteArray() : null;
        } catch (Exception e) {
            return null;
        } finally {
            inf.end();
        }
    }
}
