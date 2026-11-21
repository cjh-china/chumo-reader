package com.chumoreader.data;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.chumoreader.book.BookParser;
import com.chumoreader.book.BookUtil;
import com.chumoreader.book.ParseResult;
import com.chumoreader.util.IoUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/** 从 SAF 导入一本书：复制到私有目录 → 解析元数据 → 生成封面。 */
public final class Importer {

    private Importer() {
    }

    public static Book importUri(Context c, Uri uri) throws Exception {
        String name = queryName(c, uri);
        if (name == null || name.length() == 0) {
            name = "未命名";
        }
        String ext = BookParser.extOf(name);
        if (!BookParser.isSupported(name)) {
            throw new IOException("不支持的格式：" + (ext.length() == 0 ? "未知" : ext.toUpperCase(java.util.Locale.ROOT)));
        }
        Library lib = Library.get(c);
        Book b = new Book();
        b.format = ext.toUpperCase(java.util.Locale.ROOT);
        b.title = BookUtil.stripExt(name);

        File dest = new File(lib.booksDir(), b.id + "." + ext);
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            throw new IOException("无法读取该文件");
        }
        try {
            IoUtil.copy(in, dest);
        } finally {
            IoUtil.close(in);
        }
        b.path = dest.getName();

        ParseResult r;
        try {
            r = BookParser.parse(dest, name);
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            dest.delete();
            throw new IOException(e.getMessage() == null ? "解析失败" : e.getMessage());
        }

        if (r.title != null && r.title.trim().length() > 0) {
            b.title = r.title.trim();
        }
        // 兜底：解析器若拿到的还是内部文件名（<id>），改用导入时的外部名
        if (b.title == null || b.title.trim().length() == 0 || b.title.trim().equals(b.id)) {
            b.title = BookUtil.stripExt(name);
        }
        b.author = r.author;
        b.chars = r.totalChars();
        b.chapterCount = r.isImageMode() ? r.pages.count() : r.chapters.size();
        b.format = r.format;

        if (r.cover != null) {
            b.coverPath = saveCover(c, b.id, r.cover);
        }
        if (r.pages != null) {
            r.pages.close();
        }
        return b;
    }

    private static String saveCover(Context c, String id, Bitmap bmp) {
        try {
            File f = new File(Library.get(c).coverDir(), id + ".jpg");
            java.io.FileOutputStream out = new java.io.FileOutputStream(f);
            try {
                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out);
                out.flush();
            } finally {
                IoUtil.close(out);
            }
            if (!bmp.isRecycled()) {
                bmp.recycle();
            }
            return f.getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static String queryName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cur != null && cur.moveToFirst()) {
                return cur.getString(0);
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Exception ignored) {
                }
            }
        }
        String p = uri.getLastPathSegment();
        return p == null ? "book.txt" : p;
    }
}
