package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 纯文本解析：编码自适应 + 章节标题正则识别 + 无章节时按体量切分。
 * 中文网文的章节标题样式极杂，这里用「行长度 + 关键词」双条件降低误判。
 */
public final class TxtParser {

    private TxtParser() {
    }

    /** 第X章 / 第X节 / 序章 / 楔子 / 番外 / Chapter N */
    private static final Pattern CHAPTER = Pattern.compile(
            "^\\s*(?:正文\\s*)?(?:"
                    + "第\\s*[0-9０-９零一二三四五六七八九十百千万两〇]{1,10}\\s*[章节節回卷集篇话話幕]"
                    + "|序章|序言|自序|楔子|引子|前言|后记|後記|尾声|尾聲|终章|終章|大结局|番外(?:篇)?"
                    + "|Chapter\\s*\\d+"
                    + ")\\s*[、.．:：\\-—－]?\\s*.{0,40}$",
            Pattern.CASE_INSENSITIVE);

    /** 明显是句子而非标题的结尾 */
    private static final Pattern SENTENCE_END = Pattern.compile("[，。！？；、,.!?;…）」』】]$");

    private static final int MAX_TITLE_LEN = 34;
    private static final int AUTO_SPLIT_CHARS = 6000;

    public static ParseResult parse(File f, String sourceName) throws IOException {
        byte[] data = IoUtil.readBytes(f);
        String text = IoUtil.decodeText(data);
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }

        ParseResult r = new ParseResult();
        r.format = "TXT";
        r.title = BookUtil.titleOf(f, sourceName);
        r.author = guessAuthor(text);

        List<int[]> marks = new ArrayList<>();   // {行起点, 标题结束位置}
        List<String> titles = new ArrayList<>();

        int len = text.length();
        int pos = 0;
        while (pos < len) {
            int nl = text.indexOf('\n', pos);
            int lineEnd = nl < 0 ? len : nl;
            if (lineEnd > pos) {
                String line = text.substring(pos, lineEnd).trim();
                if (line.length() > 1 && line.length() <= MAX_TITLE_LEN && looksLikeChapter(line)) {
                    marks.add(new int[]{pos, lineEnd});
                    titles.add(line);
                }
            }
            if (nl < 0) {
                break;
            }
            pos = nl + 1;
        }

        if (marks.size() < 2) {
            // 没有可靠的章节结构，按体量切分，保证目录和进度可用
            marks.clear();
            titles.clear();
            int total = text.length();
            int pieces = Math.max(1, (total + AUTO_SPLIT_CHARS - 1) / AUTO_SPLIT_CHARS);
            for (int i = 0; i < pieces; i++) {
                int s = i * AUTO_SPLIT_CHARS;
                int e = Math.min(total, s + AUTO_SPLIT_CHARS);
                // 尽量在换行处断开
                if (e < total) {
                    int cut = text.lastIndexOf('\n', e);
                    if (cut > s + AUTO_SPLIT_CHARS / 2) {
                        e = cut;
                    }
                }
                marks.add(new int[]{s, s});
                titles.add(pieces == 1 ? r.title : "第 " + (i + 1) + " 部分");
            }
        }

        for (int i = 0; i < marks.size(); i++) {
            int start = marks.get(i)[0];
            int titleEnd = marks.get(i)[1];
            int end = (i + 1 < marks.size()) ? marks.get(i + 1)[0] : len;
            String title = titles.get(i);
            Chapter ch = new Chapter(title);
            String body = text.substring(titleEnd, Math.max(titleEnd, end));
            fillChapter(ch, body);
            r.chapters.add(ch);
        }

        // 首个章节之前若有正文（书名页等），并入第一章之前
        if (marks.size() > 0 && marks.get(0)[0] > 0) {
            String head = text.substring(0, marks.get(0)[0]).trim();
            boolean titlePage = head.length() < 80 && !head.matches("(?s).*[。！？!?；;].*");
            if (head.length() > 0 && head.length() < 2000 && !titlePage) {
                Chapter pre = new Chapter("开始");
                fillChapter(pre, head);
                if (!pre.isEmpty()) {
                    r.chapters.add(0, pre);
                }
            }
        }
        BookUtil.pruneEmpty(r);
        return r;
    }

    private static boolean looksLikeChapter(String line) {
        if (SENTENCE_END.matcher(line).find()) {
            return false;
        }
        Matcher m = CHAPTER.matcher(line);
        if (!m.matches()) {
            return false;
        }
        // 「第一章」后面若紧跟大段正文（没有换行），说明这一行不是纯标题
        return line.length() <= MAX_TITLE_LEN;
    }

    private static void fillChapter(Chapter ch, String body) {
        int len = body.length();
        int pos = 0;
        StringBuilder buf = new StringBuilder();
        while (pos < len) {
            int nl = body.indexOf('\n', pos);
            int lineEnd = nl < 0 ? len : nl;
            String line = body.substring(pos, lineEnd);
            String t = line.trim();
            if (t.length() == 0) {
                if (buf.length() > 0) {
                    ch.add(buf.toString());
                    buf.setLength(0);
                }
            } else {
                if (buf.length() > 0) {
                    buf.append('\n');
                }
                buf.append(t);
            }
            if (nl < 0) {
                break;
            }
            pos = nl + 1;
        }
        if (buf.length() > 0) {
            ch.add(buf.toString());
        }
    }

    private static String guessAuthor(String text) {
        int limit = Math.min(text.length(), 3000);
        String head = text.substring(0, limit);
        String[] lines = head.split("\n");
        for (int i = 0; i < Math.min(lines.length, 20); i++) {
            String l = lines[i].trim();
            if (l.length() > 0 && l.length() < 40) {
                Matcher m = Pattern.compile("^(?:作\\s*者|著\\s*者)\\s*[:：]?\\s*(.{1,24})$").matcher(l);
                if (m.find()) {
                    return m.group(1).trim();
                }
                if (l.startsWith("作者：") || l.startsWith("作者:")) {
                    return l.substring(3).trim();
                }
            }
        }
        return null;
    }
}
