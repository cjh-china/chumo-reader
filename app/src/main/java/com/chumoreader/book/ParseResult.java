package com.chumoreader.book;

import android.graphics.Bitmap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** 解析结果：文本型给 chapters，图片型（PDF / CBZ）给 pages。 */
public class ParseResult {

    public String title;
    public String author;
    public String format = "TXT";
    public Bitmap cover;

    /** 文本型内容 */
    public final List<Chapter> chapters = new ArrayList<>();

    /** 图片型内容（PDF / 漫画压缩包），非空时忽略 chapters */
    public ImagePages pages;

    public boolean isImageMode() {
        return pages != null;
    }

    public int totalChars() {
        int n = 0;
        for (Chapter c : chapters) {
            n += c.charCount();
        }
        return n;
    }

    public interface ImagePages {
        int count();

        /** 渲染第 index 页，宽度不超过 targetWidth；返回 null 表示无内容。 */
        Bitmap render(int index, int targetWidth) throws IOException;

        void close();
    }
}
