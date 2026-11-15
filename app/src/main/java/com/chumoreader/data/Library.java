package com.chumoreader.data;

import android.content.Context;

import com.chumoreader.util.IoUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 书库（JSON 持久化在私有目录）。 */
public class Library {

    private static Library inst;

    private final File dir;
    private final File booksDir;
    private final File coverDir;
    private final File libFile;
    private final List<Book> books = new ArrayList<>();

    private Library(Context c) {
        dir = c.getApplicationContext().getFilesDir();
        booksDir = new File(dir, "books");
        coverDir = new File(dir, "covers");
        libFile = new File(dir, "library.json");
        if (!booksDir.exists()) {
            booksDir.mkdirs();
        }
        if (!coverDir.exists()) {
            coverDir.mkdirs();
        }
        load();
    }

    public static synchronized Library get(Context c) {
        if (inst == null) {
            inst = new Library(c);
        }
        return inst;
    }

    public File booksDir() {
        return booksDir;
    }

    public File coverDir() {
        return coverDir;
    }

    private void load() {
        books.clear();
        if (!libFile.exists()) {
            return;
        }
        try {
            String json = IoUtil.readText(libFile, "UTF-8");
            JSONObject root = new JSONObject(json);
            JSONArray arr = root.optJSONArray("books");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    Book b = Book.fromJson(arr.getJSONObject(i));
                    if (b.path != null && b.file(booksDir) != null && b.file(booksDir).exists()) {
                        books.add(b);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void save() {
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);
            root.put("books", new JSONArray(Book.arrayToJson(books)));
            IoUtil.writeBytes(libFile, root.toString().getBytes("UTF-8"));
        } catch (Exception ignored) {
        }
    }

    public List<Book> books() {
        return books;
    }

    public List<Book> sortedBooks(int sort) {
        List<Book> list = new ArrayList<>(books);
        if (sort == 1) {
            Collections.sort(list, new Comparator<Book>() {
                @Override
                public int compare(Book a, Book b) {
                    return Long.compare(b.addedAt, a.addedAt);
                }
            });
        } else if (sort == 2) {
            Collections.sort(list, new Comparator<Book>() {
                @Override
                public int compare(Book a, Book b) {
                    String ta = a.title == null ? "" : a.title;
                    String tb = b.title == null ? "" : b.title;
                    return ta.compareTo(tb);
                }
            });
        } else {
            Collections.sort(list, new Comparator<Book>() {
                @Override
                public int compare(Book a, Book b) {
                    return Long.compare(b.lastReadAt, a.lastReadAt);
                }
            });
        }
        return list;
    }

    public Book find(String id) {
        for (Book b : books) {
            if (b.id != null && b.id.equals(id)) {
                return b;
            }
        }
        return null;
    }

    public Book findAny() {
        return books.isEmpty() ? null : books.get(0);
    }

    public void add(Book b) {
        books.add(0, b);
        save();
    }

    public void update(Book b) {
        for (int i = 0; i < books.size(); i++) {
            if (books.get(i).id != null && books.get(i).id.equals(b.id)) {
                books.set(i, b);
                break;
            }
        }
        save();
    }

    public void remove(Book b) {
        books.remove(b);
        File f = b.file(booksDir);
        if (f != null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
        if (b.coverPath != null) {
            File c = new File(b.coverPath);
            if (c.isAbsolute()) {
                //noinspection ResultOfMethodCallIgnored
                c.delete();
            } else {
                //noinspection ResultOfMethodCallIgnored
                new File(coverDir, b.coverPath).delete();
            }
        }
        save();
    }

    public void moveTop(Book b) {
        books.remove(b);
        books.add(0, b);
        save();
    }
}
