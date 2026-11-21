package com.chumoreader.book;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** DOCX：读 word/document.xml，按 w:p 段落重建。 */
public final class DocxParser {

    private DocxParser() {
    }

    private static final Pattern P = Pattern.compile("<w:p\\b([^>]*)>(.*?)</w:p>", Pattern.DOTALL);
    private static final Pattern T = Pattern.compile("<w:t\\b[^>]*>(.*?)</w:t>", Pattern.DOTALL);
    private static final Pattern STYLE = Pattern.compile("<w:pStyle\\b[^>]*w:val\\s*=\\s*\"([^\"]+)\"");
    private static final Pattern BR = Pattern.compile("<w:(?:br|cr)\\b[^>]*/?>");
    private static final Pattern TAB = Pattern.compile("<w:tab\\b[^>]*/?>");

    public static ParseResult parse(File f, String sourceName) throws Exception {
        ZipFile zip = new ZipFile(f);
        try {
            ZipEntry e = zip.getEntry("word/document.xml");
            if (e == null) {
                throw new java.io.IOException("不是有效的 DOCX");
            }
            String xml;
            InputStream in = zip.getInputStream(e);
            try {
                xml = com.chumoreader.util.IoUtil.decodeText(
                        com.chumoreader.util.IoUtil.readBytes(in, (int) Math.min(e.getSize(), 8 * 1024 * 1024L)));
            } finally {
                com.chumoreader.util.IoUtil.close(in);
            }

            ParseResult r = new ParseResult();
            r.format = "DOCX";
            r.title = BookUtil.titleOf(f, sourceName);

            List<Block> blocks = new ArrayList<>();
            Matcher m = P.matcher(xml);
            while (m.find()) {
                String attrs = m.group(1);
                String inner = m.group(2);
                StringBuilder sb = new StringBuilder();
                Matcher tm = T.matcher(inner);
                while (tm.find()) {
                    sb.append(HtmlText.decode(tm.group(1)));
                }
                String text = sb.toString().replace('\u0000', ' ');
                if (text.trim().length() == 0) {
                    continue;
                }
                Matcher sm = STYLE.matcher(attrs);
                int level = 0;
                if (sm.find()) {
                    String st = sm.group(1).toLowerCase(java.util.Locale.ROOT);
                    if (st.contains("heading1") || st.equals("1") || st.contains("标题1")) {
                        level = 1;
                    } else if (st.contains("heading2") || st.equals("2") || st.contains("标题2")) {
                        level = 2;
                    } else if (st.contains("heading3") || st.equals("3") || st.contains("标题3")) {
                        level = 3;
                    } else if (st.startsWith("heading")) {
                        level = 3;
                    }
                }
                text = BR.matcher(text).replaceAll("\n");
                text = TAB.matcher(text).replaceAll("    ");
                if (level > 0) {
                    blocks.add(Block.h(level, text.trim()));
                } else {
                    for (String line : text.split("\n")) {
                        String l = line.trim();
                        if (l.length() > 0) {
                            blocks.add(Block.p(l));
                        }
                    }
                }
            }
            if (blocks.isEmpty()) {
                throw new java.io.IOException("DOCX 没有正文");
            }
            BookUtil.chapterize(r, blocks, r.title);
            BookUtil.pruneEmpty(r);
        return r;
        } finally {
            try {
                zip.close();
            } catch (Exception ignored) {
            }
        }
    }
}
