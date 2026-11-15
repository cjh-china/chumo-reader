package com.chumoreader.book;

import java.util.ArrayList;
import java.util.List;

/** 解析器公用工具。 */
public final class BookUtil {

    private BookUtil() {
    }

    /**
     * 按标题块把长文切成多章：H1/H2 视为新章起点。
     * 用于「整本书塞在一个 xhtml / txt 里」的粗糙文件。
     */
    public static List<Chapter> splitByHeadings(List<Chapter> src) {
        List<Chapter> out = new ArrayList<>();
        Chapter cur = null;
        for (Chapter c : src) {
            for (Block b : c.blocks) {
                boolean isHead = b.type == Block.H1 || b.type == Block.H2;
                if (isHead) {
                    String t = b.text.trim();
                    if (t.length() > 0 && t.length() <= 50) {
                        if (cur != null && cur.charCount() > 0) {
                            out.add(cur);
                        }
                        cur = new Chapter(t);
                        continue;
                    }
                }
                if (cur == null) {
                    cur = new Chapter(c.title);
                }
                cur.add(b);
            }
        }
        if (cur != null && cur.charCount() > 0) {
            out.add(cur);
        }
        return out;
    }

    /** 从块流里按标题切章（MOBI / FB2 / DOCX 等共用）。 */
    public static void chapterize(ParseResult r, List<Block> blocks, String fallbackTitle) {
        Chapter cur = null;
        for (Block b : blocks) {
            boolean head = (b.type == Block.H1 || b.type == Block.H2)
                    || (b.type == Block.PARA && b.text.length() <= 34 && headingLike(b.text));
            if (head) {
                if (cur != null && cur.charCount() > 0) {
                    r.chapters.add(cur);
                }
                cur = new Chapter(tidyTitle(b.text));
                continue;
            }
            if (cur == null) {
                cur = new Chapter(fallbackTitle);
            }
            cur.add(b);
        }
        if (cur != null && cur.charCount() > 0) {
            r.chapters.add(cur);
        }
        if (r.chapters.size() == 1 && r.chapters.get(0).charCount() > 20000) {
            List<Chapter> split = splitBySize(r.chapters.get(0), 6000);
            if (split.size() > 1) {
                r.chapters.clear();
                r.chapters.addAll(split);
            }
        }
    }

    /** 把超长单章按字数切开，标题沿用原名加序号。 */
    public static List<Chapter> splitBySize(Chapter src, int perChapter) {
        List<Chapter> out = new ArrayList<>();
        StringBuilder buf = new StringBuilder();
        Chapter cur = null;
        int idx = 1;
        for (Block b : src.blocks) {
            if (cur == null) {
                cur = new Chapter(src.title + "（" + idx + "）");
            }
            cur.add(b);
            if (b.type != Block.IMG) {
                buf.append(b.text);
            }
            if (buf.length() >= perChapter) {
                out.add(cur);
                cur = null;
                idx++;
                buf.setLength(0);
            }
        }
        if (cur != null && cur.charCount() > 0) {
            out.add(cur);
        }
        return out;
    }

    /** 丢掉解析出来的空章 / 残渣章（常见于书名页、目录页、封面页）。 */
    public static void pruneEmpty(ParseResult r) {
        List<Chapter> keep = new ArrayList<>();
        for (Chapter c : r.chapters) {
            if (c.charCount() >= 2) {
                keep.add(c);
            }
        }
        if (!keep.isEmpty()) {
            r.chapters.clear();
            r.chapters.addAll(keep);
        }
        for (Chapter c : r.chapters) {
            if (c.title == null || c.title.trim().length() == 0) {
                c.title = "正文";
            }
        }
    }

    /** 去掉扩展名，作为书名兜底。 */
    public static String stripExt(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 清理章节标题里常见的杂质。 */
    public static String tidyTitle(String t) {
        if (t == null) {
            return "";
        }
        String s = t.replace('\u00A0', ' ').replaceAll("[\\r\\n\\t]+", " ").trim();
        s = s.replaceAll("^[\\-—－·•\\s]+", "").replaceAll("[\\-—－·•\\s]+$", "");
        return s;
    }

    /** 简单判断某行是否像章节标题（给非 TXT 格式做兜底）。 */
    public static boolean headingLike(String line) {
        if (line == null) {
            return false;
        }
        String s = line.trim();
        if (s.length() < 2 || s.length() > 34) {
            return false;
        }
        return s.matches("^(第\\s*[0-9零一二三四五六七八九十百千万两]{1,10}\\s*[章节節回卷集篇话]"
                + "|序章|序言|楔子|引子|前言|后记|尾声|番外).*$");
    }
}
