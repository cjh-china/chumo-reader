package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FictionBook (FB2)：把类 HTML 结构交给 HtmlText 处理。 */
public final class Fb2Parser {

    private Fb2Parser() {
    }

    public static ParseResult parse(File f) throws Exception {
        String xml = IoUtil.decodeText(IoUtil.readBytes(f));
        ParseResult r = new ParseResult();
        r.format = "FB2";
        r.title = BookUtil.stripExt(f.getName());

        Matcher bt = Pattern.compile("<book-title\\b[^>]*>(.*?)</book-title>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        if (bt.find()) {
            String t = HtmlText.decode(stripTags(bt.group(1))).trim();
            if (t.length() > 0) {
                r.title = t;
            }
        }
        Matcher au = Pattern.compile("<author\\b[^>]*>(.*?)</author>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(xml);
        if (au.find()) {
            String a = HtmlText.decode(stripTags(au.group(1))).trim().replaceAll("\\s+", " ");
            if (a.length() > 0) {
                r.author = a;
            }
        }

        String body = xml;
        int b1 = xml.indexOf("<body");
        int b2 = xml.lastIndexOf("</body>");
        if (b1 >= 0 && b2 > b1) {
            body = xml.substring(b1, b2);
        }
        String html = body
                .replaceAll("(?i)<title\\s*>", "<h2>")
                .replaceAll("(?i)</title\\s*>", "</h2>")
                .replaceAll("(?i)<subtitle\\s*>", "<h3>")
                .replaceAll("(?i)</subtitle\\s*>", "</h3>")
                .replaceAll("(?i)<section\\b[^>]*>", "<div>")
                .replaceAll("(?i)</section\\s*>", "</div>")
                .replaceAll("(?i)<poem\\b[^>]*>|<stanza\\b[^>]*>|<v\\b[^>]*>", "<p>")
                .replaceAll("(?i)</poem\\s*>|</stanza\\s*>|</v\\s*>", "</p>")
                .replaceAll("(?i)<empty-line\\s*/?>", "<br/>")
                .replaceAll("(?i)<image\\b[^>]*/?>", "")
                .replaceAll("(?i)<binary\\b.*?</binary>", "");

        List<Block> blocks = HtmlText.toBlocks(html);
        if (blocks.isEmpty()) {
            throw new java.io.IOException("FB2 没有正文");
        }
        BookUtil.chapterize(r, blocks, r.title);
        if (r.chapters.isEmpty()) {
            throw new java.io.IOException("FB2 没有可读章节");
        }
        BookUtil.pruneEmpty(r);
        return r;
    }

    private static String stripTags(String s) {
        return s.replaceAll("<[^>]+>", " ");
    }
}
