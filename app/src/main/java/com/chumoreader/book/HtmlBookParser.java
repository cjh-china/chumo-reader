package com.chumoreader.book;

import com.chumoreader.util.IoUtil;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTML / XHTML 单文件读物。 */
public final class HtmlBookParser {

    private HtmlBookParser() {
    }

    public static ParseResult parse(File f) throws Exception {
        String html = IoUtil.decodeText(IoUtil.readBytes(f));
        ParseResult r = new ParseResult();
        r.format = "HTML";
        r.title = BookUtil.stripExt(f.getName());

        Matcher tm = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(html);
        if (tm.find()) {
            String t = HtmlText.decode(tm.group(1)).trim();
            if (t.length() > 0) {
                r.title = t;
            }
        }
        Matcher am = Pattern.compile("<meta[^>]+name=[\"']author[\"'][^>]*content=[\"']([^\"']+)",
                Pattern.CASE_INSENSITIVE).matcher(html);
        if (am.find()) {
            r.author = am.group(1).trim();
        }

        List<Block> blocks = HtmlText.toBlocks(html);
        if (blocks.isEmpty()) {
            throw new java.io.IOException("HTML 里没有正文");
        }
        // h1/h2 切章；没有标题就按体量切，保证目录和进度条可用
        BookUtil.chapterize(r, blocks, r.title);
        BookUtil.pruneEmpty(r);
        return r;
    }
}
