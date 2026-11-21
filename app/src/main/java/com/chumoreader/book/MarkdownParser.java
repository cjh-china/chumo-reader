package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Markdown：标题成章，引用/代码块保留结构。 */
public final class MarkdownParser {

    private MarkdownParser() {
    }

    public static ParseResult parse(File f, String sourceName) throws Exception {
        String text = IoUtil.decodeText(IoUtil.readBytes(f)).replace("\r\n", "\n").replace('\r', '\n');
        ParseResult r = new ParseResult();
        r.format = "MD";
        r.title = BookUtil.titleOf(f, sourceName);

        Chapter cur = null;
        StringBuilder para = new StringBuilder();
        boolean inCode = false;
        StringBuilder code = new StringBuilder();

        String[] lines = text.split("\n");
        for (String raw : lines) {
            String line = raw;
            String trim = line.trim();

            if (trim.startsWith("```")) {
                if (inCode) {
                    if (code.length() > 0) {
                        Block b = Block.p(code.toString().trim());
                        if (cur == null) {
                            cur = new Chapter(r.title);
                        }
                        cur.add(b);
                        code.setLength(0);
                    }
                    inCode = false;
                } else {
                    flushPara(cur, para, r);
                    inCode = true;
                }
                continue;
            }
            if (inCode) {
                code.append(line).append('\n');
                continue;
            }

            if (trim.length() == 0) {
                flushPara(cur, para, r);
                continue;
            }

            int level = headingLevel(trim);
            if (level > 0) {
                flushPara(cur, para, r);
                String title = BookUtil.tidyTitle(trim.substring(level).trim());
                if (level <= 2) {
                    cur = new Chapter(title);
                    r.chapters.add(cur);
                } else {
                    if (cur == null) {
                        cur = new Chapter(r.title);
                        r.chapters.add(cur);
                    }
                    cur.add(Block.h(level, title));
                }
                continue;
            }

            if (trim.startsWith(">")) {
                flushPara(cur, para, r);
                String q = trim.replaceAll("^>+\\s?", "");
                if (q.length() > 0) {
                    if (cur == null) {
                        cur = new Chapter(r.title);
                        r.chapters.add(cur);
                    }
                    cur.add(new Block(Block.QUOTE, q));
                }
                continue;
            }

            if (trim.matches("^([-*_]\\s*){3,}$")) {
                flushPara(cur, para, r);
                if (cur == null) {
                    cur = new Chapter(r.title);
                    r.chapters.add(cur);
                }
                cur.add(Block.sep());
                continue;
            }

            String item = trim.replaceAll("^([-*+]|\\d+[.)])\\s+", "");
            if (para.length() > 0) {
                para.append('\n');
            }
            para.append(inline(item));
        }
        flushPara(cur, para, r);

        if (r.chapters.isEmpty()) {
            throw new java.io.IOException("Markdown 没有正文");
        }
        BookUtil.pruneEmpty(r);
        return r;
    }

    private static void flushPara(Chapter cur, StringBuilder para, ParseResult r) {
        if (para.length() == 0) {
            return;
        }
        String s = para.toString().trim();
        para.setLength(0);
        if (s.length() == 0) {
            return;
        }
        if (cur == null) {
            cur = new Chapter(r.title);
            r.chapters.add(cur);
        }
        cur.add(s);
    }

    private static int headingLevel(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == '#') {
            n++;
        }
        if (n >= 1 && n <= 6 && s.length() > n && s.charAt(n) == ' ') {
            return n;
        }
        return 0;
    }

    /** 去掉行内标记，保留文字 */
    private static String inline(String s) {
        String r = s;
        r = r.replaceAll("!\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        r = r.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        r = r.replaceAll("(\\*\\*|__)(.*?)\\1", "$2");
        r = r.replaceAll("(?<!\\*)\\*(?!\\*)([^*]+)\\*(?!\\*)", "$1");
        r = r.replaceAll("`([^`]*)`", "$1");
        r = r.replaceAll("<[^>]+>", "");
        return HtmlText.decode(r);
    }
}
