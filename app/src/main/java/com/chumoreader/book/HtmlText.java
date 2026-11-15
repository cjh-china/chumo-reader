package com.chumoreader.book;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 容错 HTML → 排版块。
 * 不用严格 XML 解析器（epub 里的 xhtml 经常不合规），
 * 用「标签归一 + 标记字符」把结构压平成行，再切成段落。
 */
public final class HtmlText {

    private HtmlText() {
    }

    // 用私有区字符做标题标记：String.trim() 不会删掉它们
    private static final char M = '\uE000';
    private static final char E = '\uE001';

    private static final Pattern SCRIPT = Pattern.compile("<(script|style|head)[^>]*>.*?</\\1\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern H_OPEN = Pattern.compile("<h([1-6])[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern H_CLOSE = Pattern.compile("</h[1-6]\\s*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOCK_TAG = Pattern.compile(
            "</?(p|div|br|li|tr|ul|ol|table|section|article|blockquote|hr|pre|dd|dt|figcaption|h[1-6])\\b[^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]{0,400}>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});");

    public static List<Block> toBlocks(String html) {
        List<Block> out = new ArrayList<>();
        if (html == null || html.length() == 0) {
            return out;
        }
        String s = html;
        s = COMMENT.matcher(s).replaceAll(" ");
        s = SCRIPT.matcher(s).replaceAll(" ");
        s = H_OPEN.matcher(s).replaceAll("\n" + M + "$1" + E);
        s = H_CLOSE.matcher(s).replaceAll("\n");
        s = BLOCK_TAG.matcher(s).replaceAll("\n");
        s = ANY_TAG.matcher(s).replaceAll("");
        s = decode(s);
        s = s.replace('\u00A0', ' ').replace('\u3000', ' ');

        int len = s.length();
        int pos = 0;
        while (pos < len) {
            int nl = s.indexOf('\n', pos);
            int end = nl < 0 ? len : nl;
            String line = s.substring(pos, end).trim();
            if (line.length() > 0) {
                int level = 0;
                // 行首可能带层级标记：\u0002 1 \u0003
                if (line.length() > 0 && line.charAt(0) == M) {
                    int e = line.indexOf(E, 1);
                    if (e > 1 && e - 1 <= 2) {
                        try {
                            level = Integer.parseInt(line.substring(1, e));
                        } catch (NumberFormatException ignored) {
                            level = 1;
                        }
                        line = line.substring(e + 1).trim();
                    } else {
                        line = line.substring(1).trim();
                    }
                }
                if (line.length() > 0) {
                    if (level > 0) {
                        out.add(Block.h(level, collapse(line)));
                    } else {
                        out.add(Block.p(collapse(line)));
                    }
                }
            }
            if (nl < 0) {
                break;
            }
            pos = nl + 1;
        }
        return out;
    }

    public static String toPlain(String html) {
        StringBuilder sb = new StringBuilder();
        for (Block b : toBlocks(html)) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(b.text);
        }
        return sb.toString();
    }

    private static String collapse(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == M || c == E) {
                continue;
            }
            if (c == ' ' || c == '\t') {
                if (!space && sb.length() > 0) {
                    sb.append(' ');
                    space = true;
                }
            } else {
                sb.append(c);
                space = false;
            }
        }
        return sb.toString().trim();
    }

    public static String decode(String s) {
        Matcher m = ENTITY.matcher(s);
        StringBuffer sb = new StringBuffer(s.length());
        while (m.find()) {
            String g = m.group(1);
            String rep;
            try {
                if (g.charAt(0) == '#') {
                    int cp = (g.charAt(1) == 'x' || g.charAt(1) == 'X')
                            ? Integer.parseInt(g.substring(2), 16)
                            : Integer.parseInt(g.substring(1));
                    rep = new String(Character.toChars(cp > 0 && cp <= 0x10FFFF ? cp : 32));
                } else {
                    rep = named(g);
                }
            } catch (Exception e) {
                rep = "";
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(rep));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String named(String n) {
        switch (n) {
            case "amp":
                return "&";
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            case "nbsp":
                return " ";
            case "mdash":
                return "—";
            case "ndash":
                return "–";
            case "hellip":
                return "…";
            case "ldquo":
                return "“";
            case "rdquo":
                return "”";
            case "lsquo":
                return "‘";
            case "rsquo":
                return "’";
            case "middot":
                return "·";
            case "times":
                return "×";
            case "copy":
                return "©";
            case "reg":
                return "®";
            case "deg":
                return "°";
            case "bull":
                return "•";
            case "laquo":
                return "«";
            case "raquo":
                return "»";
            default:
                return "";
        }
    }
}
