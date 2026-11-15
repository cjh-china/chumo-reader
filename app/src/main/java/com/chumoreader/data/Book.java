package com.chumoreader.data;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/** 书架上的一本书（元数据 + 阅读进度）。 */
public class Book {

    public String id;
    public String title;
    public String author;
    public String path;          // books/<id>.<ext>
    public String coverPath;     // covers/<id>.jpg，可空
    public String format = "TXT";
    public int chapterIndex;
    public int chapterCount;
    public int lastChar;      // 章节内字符偏移，用于重排版后精确定位
    public float progress;       // 0..1
    public long lastReadAt;
    public long addedAt;
    public long chars;
    public boolean finished;
    public String groups = "";

    public Book() {
        id = String.valueOf(System.currentTimeMillis()) + (int) (Math.random() * 1000);
        addedAt = System.currentTimeMillis();
    }

    public File file(File booksDir) {
        if (path == null) {
            return null;
        }
        File f = new File(path);
        return f.isAbsolute() ? f : new File(booksDir, path);
    }

    public String progressText() {
        if (chapterCount <= 0) {
            return "未读";
        }
        if (finished || progress >= 0.999f) {
            return "已读完";
        }
        if (progress <= 0.001f) {
            return "未读";
        }
        return "已读 " + Math.round(progress * 100) + "%";
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("title", title == null ? "" : title);
            o.put("author", author == null ? "" : author);
            o.put("path", path == null ? "" : path);
            o.put("cover", coverPath == null ? "" : coverPath);
            o.put("format", format);
            o.put("chapterIndex", chapterIndex);
            o.put("lastChar", lastChar);
            o.put("chapterCount", chapterCount);
            o.put("progress", (double) progress);
            o.put("lastReadAt", lastReadAt);
            o.put("addedAt", addedAt);
            o.put("chars", chars);
            o.put("finished", finished);
            o.put("groups", groups == null ? "" : groups);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Book fromJson(JSONObject o) {
        Book b = new Book();
        b.id = o.optString("id");
        b.title = o.optString("title");
        b.author = o.optString("author");
        b.path = o.optString("path");
        b.coverPath = o.optString("cover");
        if (b.coverPath.length() == 0) {
            b.coverPath = null;
        }
        b.format = o.optString("format", "TXT");
        b.chapterIndex = o.optInt("chapterIndex");
        b.lastChar = o.optInt("lastChar");
        b.chapterCount = o.optInt("chapterCount");
        b.progress = (float) o.optDouble("progress");
        b.lastReadAt = o.optLong("lastReadAt");
        b.addedAt = o.optLong("addedAt");
        b.chars = o.optLong("chars");
        b.finished = o.optBoolean("finished");
        b.groups = o.optString("groups");
        return b;
    }

    public static String arrayToJson(java.util.List<Book> list) {
        JSONArray arr = new JSONArray();
        for (Book b : list) {
            arr.put(b.toJson());
        }
        return arr.toString();
    }
}
