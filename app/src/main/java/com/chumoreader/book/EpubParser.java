package com.chumoreader.book;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.chumoreader.util.IoUtil;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * EPUB 解析：container.xml → OPF → spine 顺序 → 逐篇 XHTML 转块。
 * 标题优先取 NCX / nav，缺失时退回 XHTML 的 &lt;title&gt;。
 */
public final class EpubParser {

    private EpubParser() {
    }

    private static final Pattern ITEM = Pattern.compile("<item\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ITEMREF = Pattern.compile("<itemref\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTR = Pattern.compile("([\\w:.-]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern ROOTFILE = Pattern.compile("<rootfile\\b([^>]*)/?>", Pattern.CASE_INSENSITIVE);
    private static final Pattern NAVPOINT = Pattern.compile(
            "<navPoint\\b[^>]*>(.*?)</navPoint>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern NAVLABEL = Pattern.compile(
            "<text\\b[^>]*>(.*?)</text>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CONTENT_SRC = Pattern.compile(
            "<content\\b[^>]*src\\s*=\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_TAG = Pattern.compile(
            "<title\\b[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public static ParseResult parse(File f, String sourceName) throws Exception {
        ZipFile zip = new ZipFile(f);
        try {
            ParseResult r = new ParseResult();
            r.format = "EPUB";
            r.title = BookUtil.titleOf(f, sourceName);

            String opfPath = findOpf(zip);
            if (opfPath == null) {
                throw new java.io.IOException("EPUB 缺少 OPF 文件");
            }
            String opf = readEntry(zip, opfPath);
            String base = dirOf(opfPath);

            Matcher tm = Pattern.compile("<(?:dc:)?title\\b[^>]*>(.*?)</(?:dc:)?title>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(opf);
            if (tm.find()) {
                String t = HtmlText.decode(tm.group(1)).trim();
                if (t.length() > 0) {
                    r.title = t;
                }
            }
            Matcher am = Pattern.compile("<(?:dc:)?creator\\b[^>]*>(.*?)</(?:dc:)?creator>",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(opf);
            if (am.find()) {
                String a = HtmlText.decode(am.group(1)).trim();
                if (a.length() > 0) {
                    r.author = a;
                }
            }

            Map<String, String[]> manifest = new HashMap<>();   // id -> {href, mediaType, properties}
            Matcher im = ITEM.matcher(opf);
            while (im.find()) {
                Map<String, String> a = attrs(im.group(1));
                String id = a.get("id");
                String href = a.get("href");
                if (id != null && href != null) {
                    manifest.put(id, new String[]{href, nz(a.get("media-type")), nz(a.get("properties"))});
                }
            }

            List<String> spine = new ArrayList<>();
            Matcher rm = ITEMREF.matcher(opf);
            while (rm.find()) {
                String idref = attrs(rm.group(1)).get("idref");
                if (idref != null && manifest.containsKey(idref)) {
                    spine.add(manifest.get(idref)[0]);
                }
            }

            // 封面
            String coverHref = null;
            Matcher cm = Pattern.compile("<meta\\b[^>]*name\\s*=\\s*\"cover\"[^>]*content\\s*=\\s*\"([^\"]+)\"",
                    Pattern.CASE_INSENSITIVE).matcher(opf);
            if (cm.find()) {
                String[] it = manifest.get(cm.group(1));
                if (it != null) {
                    coverHref = it[0];
                }
            }
            if (coverHref == null) {
                for (String[] v : manifest.values()) {
                    if (v[2].contains("cover-image")) {
                        coverHref = v[0];
                        break;
                    }
                }
            }
            if (coverHref == null) {
                for (Map.Entry<String, String[]> e : manifest.entrySet()) {
                    String href = e.getValue()[0].toLowerCase(java.util.Locale.ROOT);
                    String type = e.getValue()[1].toLowerCase(java.util.Locale.ROOT);
                    if (type.startsWith("image/") && (href.contains("cover") || e.getKey().toLowerCase(java.util.Locale.ROOT).contains("cover"))) {
                        coverHref = e.getValue()[0];
                        break;
                    }
                }
            }
            if (coverHref != null) {
                r.cover = readImage(zip, join(base, coverHref), 640);
            }

            // 目录标题映射
            Map<String, String> tocTitles = new HashMap<>();
            String ncxHref = null;
            String navHref = null;
            for (String[] v : manifest.values()) {
                String type = v[1];
                if (type.contains("dtbncx") || v[0].toLowerCase(java.util.Locale.ROOT).endsWith(".ncx")) {
                    ncxHref = v[0];
                }
                if (v[2].contains("nav")) {
                    navHref = v[0];
                }
            }
            if (ncxHref != null) {
                try {
                    readNcx(readEntry(zip, join(base, ncxHref)), base, tocTitles);
                } catch (Exception ignored) {
                }
            }
            if (tocTitles.isEmpty() && navHref != null) {
                try {
                    readNav(readEntry(zip, join(base, navHref)), base, tocTitles);
                } catch (Exception ignored) {
                }
            }

            int index = 0;
            for (String href : spine) {
                index++;
                String entryName = join(base, href);
                String html;
                try {
                    html = readEntry(zip, entryName);
                } catch (Exception e) {
                    continue;
                }
                List<Block> blocks = HtmlText.toBlocks(html);
                if (blocks.isEmpty()) {
                    continue;
                }
                String title = tocTitles.get(cleanHref(href));
                if (title == null) {
                    title = tocTitles.get(cleanHref(entryName));
                }
                if (title == null) {
                    Matcher ttm = TITLE_TAG.matcher(html);
                    if (ttm.find()) {
                        title = HtmlText.decode(ttm.group(1)).trim();
                    }
                }
                if (title == null || title.length() == 0) {
                    title = "第 " + index + " 节";
                }
                Chapter ch = new Chapter(cleanTitle(title));
                ch.blocks.addAll(blocks);
                if (ch.charCount() < 12 && ch.blocks.size() <= 2) {
                    continue;   // 封面页 / 版权页
                }
                r.chapters.add(ch);
            }

            if (r.chapters.isEmpty()) {
                throw new java.io.IOException("EPUB 没有可读正文");
            }
            // 单文件长文：按标题再切一次，让目录可用
            if (r.chapters.size() <= 2 && r.totalChars() > 8000) {
                List<Chapter> split = BookUtil.splitByHeadings(r.chapters);
                if (split.size() > 2) {
                    r.chapters.clear();
                    r.chapters.addAll(split);
                }
            }
            return r;
        } finally {
            try {
                zip.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void readNcx(String xml, String base, Map<String, String> out) {
        Matcher m = NAVPOINT.matcher(xml);
        while (m.find()) {
            String body = m.group(1);
            Matcher lm = NAVLABEL.matcher(body);
            Matcher sm = CONTENT_SRC.matcher(body);
            if (lm.find() && sm.find()) {
                String title = HtmlText.decode(lm.group(1)).trim();
                String src = cleanHref(sm.group(1));
                if (title.length() > 0) {
                    out.put(src, title);
                    out.put(cleanHref(join(base, src)), title);
                }
            }
        }
    }

    private static void readNav(String html, String base, Map<String, String> out) {
        Matcher m = Pattern.compile("<a\\b[^>]*href\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        while (m.find()) {
            String title = HtmlText.decode(ANY_TAG.matcher(m.group(2)).replaceAll("")).trim();
            String src = cleanHref(m.group(1));
            if (title.length() > 0) {
                out.put(src, title);
                out.put(cleanHref(join(base, src)), title);
            }
        }
    }

    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");

    private static String cleanHref(String href) {
        int hash = href.indexOf('#');
        String s = hash >= 0 ? href.substring(0, hash) : href;
        try {
            s = java.net.URLDecoder.decode(s, "UTF-8");
        } catch (Exception ignored) {
        }
        return s;
    }

    private static String cleanTitle(String t) {
        String s = t.replaceAll("\\s+", " ").trim();
        return s.length() > 60 ? s.substring(0, 60) : s;
    }

    private static String findOpf(ZipFile zip) throws Exception {
        String container = null;
        ZipEntry ce = zip.getEntry("META-INF/container.xml");
        if (ce != null) {
            container = readEntry(zip, "META-INF/container.xml");
        }
        if (container != null) {
            Matcher m = ROOTFILE.matcher(container);
            if (m.find()) {
                String p = attrs(m.group(1)).get("full-path");
                if (p != null && zip.getEntry(p) != null) {
                    return p;
                }
            }
        }
        // 兜底：扫任意 .opf
        java.util.Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".opf")) {
                return e.getName();
            }
        }
        return null;
    }

    private static Map<String, String> attrs(String s) {
        Map<String, String> map = new HashMap<>();
        Matcher m = ATTR.matcher(s);
        while (m.find()) {
            map.put(m.group(1).toLowerCase(java.util.Locale.ROOT), m.group(2));
        }
        return map;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static String readEntry(ZipFile zip, String name) throws Exception {
        ZipEntry e = zip.getEntry(name);
        if (e == null) {
            e = zip.getEntry(java.net.URLDecoder.decode(name, "UTF-8"));
        }
        if (e == null) {
            throw new java.io.IOException("压缩包内缺少：" + name);
        }
        InputStream in = zip.getInputStream(e);
        try {
            return IoUtil.decodeText(IoUtil.readBytes(in, (int) Math.min(e.getSize(), 4 * 1024 * 1024L)));
        } finally {
            IoUtil.close(in);
        }
    }

    static Bitmap readImage(ZipFile zip, String name, int maxSize) {
        try {
            java.util.zip.ZipEntry e = zip.getEntry(name);
            if (e == null) {
                return null;
            }
            InputStream in = zip.getInputStream(e);
            byte[] data;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[32 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                data = bos.toByteArray();
            } finally {
                IoUtil.close(in);
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            while (o.outWidth / sample > maxSize) {
                sample *= 2;
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        } catch (Exception e) {
            return null;
        }
    }

    public static String dirOf(String path) {
        int i = path.lastIndexOf('/');
        return i > 0 ? path.substring(0, i) : "";
    }

    public static String join(String base, String href) {
        if (href.startsWith("/")) {
            href = href.substring(1);
        }
        String p = base.length() == 0 ? href : base + "/" + href;
        // 规范化 ../ 与 ./
        String[] parts = p.split("/");
        ArrayList<String> stack = new ArrayList<>();
        for (String s : parts) {
            if (s.length() == 0 || s.equals(".")) {
                continue;
            }
            if (s.equals("..")) {
                if (!stack.isEmpty()) {
                    stack.remove(stack.size() - 1);
                }
            } else {
                stack.add(s);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.size(); i++) {
            if (i > 0) {
                sb.append('/');
            }
            sb.append(stack.get(i));
        }
        return sb.toString();
    }
}
