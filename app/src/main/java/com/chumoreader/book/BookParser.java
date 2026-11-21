package com.chumoreader.book;

import java.io.File;
import java.util.Locale;

/**
 * 格式分发：按扩展名路由到具体解析器。
 * 全部为纯 Java + Android Framework 实现，不引入第三方解析库。
 */
public final class BookParser {

    private BookParser() {
    }

    /** 书库界面拿来展示的格式清单 */
    public static final String[][] SUPPORTED = {
            {"TXT", "纯文本", "自动识别 UTF-8 / GBK / UTF-16，正则切分章节"},
            {"EPUB", "电子书标准", "解析 OPF 目录与正文，提取封面、作者"},
            {"HTML", "网页 / XHTML", "剥离标签保留段落与标题层级"},
            {"UMD", "手机书老格式", "解包 UMD 正文块"},
            {"MOBI", "Kindle 旧格式", "PalmDOC 解压 + MOBI 头部解析（含 AZW）"},
            {"AZW3", "Kindle KF8", "按 KF8 文本块提取"},
            {"FB2", "FictionBook", "XML 结构直接转段落"},
            {"PDF", "文档", "用系统 PdfRenderer 逐页渲染"},
            {"CBZ", "漫画包", "按图片条目顺序翻阅"},
            {"DOCX", "Word 文档", "读 document.xml 提取段落"},
            {"MD", "Markdown", "标题 / 引用 / 代码块"},
            {"RTF", "富文本文档", "剥离控制字，还原中文编码"},
    };

    public static String extOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isSupported(String fileName) {
        String e = extOf(fileName);
        switch (e) {
            case "txt":
            case "text":
            case "log":
            case "epub":
            case "html":
            case "htm":
            case "xhtml":
            case "umd":
            case "mobi":
            case "azw":
            case "azw3":
            case "prc":
            case "fb2":
            case "pdf":
            case "cbz":
            case "docx":
            case "md":
            case "markdown":
            case "rtf":
                return true;
            default:
                return false;
        }
    }

    public static ParseResult parse(File file) throws Exception {
        return parse(file, null);
    }

    /**
     * @param sourceName 外部显示名（SAF 的 DISPLAY_NAME，含扩展名）。书库内部存的是
     *                   {@code <id>.<ext>}，解析器不该用它当书名，所以由导入方传进来。
     */
    public static ParseResult parse(File file, String sourceName) throws Exception {
        if (file == null || !file.exists() || file.length() == 0) {
            throw new java.io.IOException("文件不存在或为空");
        }
        String ext = extOf(file.getName());
        ParseResult r;
        switch (ext) {
            case "epub":
                r = EpubParser.parse(file, sourceName);
                break;
            case "html":
            case "htm":
            case "xhtml":
                r = HtmlBookParser.parse(file, sourceName);
                break;
            case "umd":
                r = UmdParser.parse(file, sourceName);
                break;
            case "mobi":
            case "azw":
            case "azw3":
            case "prc":
                r = MobiParser.parse(file, sourceName);
                break;
            case "fb2":
                r = Fb2Parser.parse(file, sourceName);
                break;
            case "pdf":
                r = PdfParser.parse(file, sourceName);
                break;
            case "cbz":
                r = CbzParser.parse(file, sourceName);
                break;
            case "docx":
                r = DocxParser.parse(file, sourceName);
                break;
            case "md":
            case "markdown":
                r = MarkdownParser.parse(file, sourceName);
                break;
            case "rtf":
                r = RtfParser.parse(file, sourceName);
                break;
            default:
                r = TxtParser.parse(file, sourceName);
                break;
        }
        if (r.title == null || r.title.trim().length() == 0) {
            r.title = BookUtil.titleOf(file, sourceName);
        }
        if (r.chapters.isEmpty() && r.pages == null) {
            throw new java.io.IOException("没有解析到正文内容");
        }
        return r;
    }

    public static String stripExt(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** 大致判断是不是文本类（用于导入时给图标/颜色） */
    public static String labelOf(String ext) {
        String e = ext == null ? "" : ext.toUpperCase(Locale.ROOT);
        if (e.length() == 0) {
            return "TXT";
        }
        return e;
    }
}
