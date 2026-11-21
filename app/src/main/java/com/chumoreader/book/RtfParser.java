package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/** RTF：跳过控制字，保留可见文本；\'hh 按 GB18030 还原中文。 */
public final class RtfParser {

    private RtfParser() {
    }

    /** 这些目标组整组丢弃，不要输出表里的文本。 */
    private static final java.util.Set<String> SKIP_TARGETS = new java.util.HashSet<>(java.util.Arrays.asList(
            "fonttbl", "colortbl", "stylesheet", "info", "pict", "object", "header", "footer",
            "headerl", "headerr", "footerl", "footerr", "footnote", "fldinst", "listtable",
            "listoverridetable", "themedata", "colorschememapping", "latentstyles", "datastore",
            "generator", "xmlnstbl", "rsidtbl", "filetbl", "revtbl", "pnseclvl", "listtext"));

    public static ParseResult parse(File f, String sourceName) throws Exception {
        String src = new String(IoUtil.readBytes(f), Charset.forName("ISO-8859-1"));
        String text = extract(src);
        ParseResult r = new ParseResult();
        r.format = "RTF";
        r.title = BookUtil.titleOf(f, sourceName);

        List<Block> blocks = new ArrayList<>();
        Chapter cur = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.length() == 0) {
                continue;
            }
            if (BookUtil.headingLike(line) && line.length() <= 34) {
                if (cur != null && cur.charCount() > 0) {
                    blocks.add(Block.h(2, line));
                    cur = null;
                } else {
                    blocks.add(Block.h(2, line));
                }
            } else {
                blocks.add(Block.p(line));
            }
        }
        BookUtil.chapterize(r, blocks, r.title);
        if (r.chapters.isEmpty()) {
            throw new java.io.IOException("RTF 没有正文");
        }
        BookUtil.pruneEmpty(r);
        return r;
    }

    private static String extract(String rtf) {
        StringBuilder out = new StringBuilder(rtf.length() / 2);
        ByteArrayOutputStream hex = new ByteArrayOutputStream();
        int depth = 0;
        int skipDepth = -1;
        int i = 0;
        int n = rtf.length();
        while (i < n) {
            char c = rtf.charAt(i);
            if (c == '{') {
                depth++;
                // {\* 开头的是可选目标，整组跳过
                if (i + 1 < n && rtf.charAt(i + 1) == '\\' && i + 2 < n && rtf.charAt(i + 2) == '*') {
                    skipDepth = depth;
                }
                i++;
                continue;
            }
            if (c == '}') {
                if (skipDepth == depth) {
                    skipDepth = -1;
                }
                depth--;
                i++;
                continue;
            }
            if (c == '\\') {
                if (i + 1 >= n) {
                    break;
                }
                char nx = rtf.charAt(i + 1);
                if (nx == '\'') {
                    if (i + 3 < n) {
                        int v = Integer.parseInt(rtf.substring(i + 2, i + 4), 16);
                        hex.write(v);
                        i += 4;
                        continue;
                    }
                    i += 2;
                    continue;
                }
                if (nx == '\\' || nx == '{' || nx == '}') {
                    if (skipDepth < 0) {
                        out.append(nx);
                    }
                    i += 2;
                    continue;
                }
                // 控制字
                int j = i + 1;
                StringBuilder word = new StringBuilder();
                while (j < n && Character.isLetter(rtf.charAt(j))) {
                    word.append(rtf.charAt(j));
                    j++;
                }
                while (j < n && (rtf.charAt(j) == '-' || Character.isDigit(rtf.charAt(j)))) {
                    j++;
                }
                if (j < n && rtf.charAt(j) == ' ') {
                    j++;
                }
                if (skipDepth < 0) {
                    String w = word.toString();
                    if (SKIP_TARGETS.contains(w)) {
                        // 字体表 / 颜色表 / 图片等目标组整组跳过
                        skipDepth = depth;
                    } else if (w.equals("par") || w.equals("line") || w.equals("sect") || w.equals("page")) {
                        flushHex(out, hex);
                        out.append('\n');
                    } else if (w.equals("tab")) {
                        out.append('\t');
                    } else if (w.equals("u")) {
                        // uNNNN 形式：后跟一个替代字符
                        int k = i + 1 + word.length();
                        int start = k;
                        while (k < n && (rtf.charAt(k) == '-' || Character.isDigit(rtf.charAt(k)))) {
                            k++;
                        }
                        try {
                            int cp = Integer.parseInt(rtf.substring(start, k));
                            out.append((char) (cp < 0 ? cp + 65536 : cp));
                        } catch (Exception ignored) {
                        }
                        i = k;
                        continue;
                    }
                }
                i = j;
                continue;
            }
            if (c == '\r' || c == '\n') {
                i++;
                continue;
            }
            if (skipDepth < 0) {
                flushHex(out, hex);
                out.append(c);
            }
            i++;
        }
        flushHex(out, hex);
        return out.toString();
    }

    private static void flushHex(StringBuilder out, ByteArrayOutputStream hex) {
        if (hex.size() == 0) {
            return;
        }
        byte[] b = hex.toByteArray();
        hex.reset();
        out.append(new String(b, Charset.forName("GB18030")));
    }
}
