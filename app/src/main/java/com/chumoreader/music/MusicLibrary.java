package com.chumoreader.music;

import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 本地音乐扫描（MediaStore，无需遍历文件系统）。 */
public final class MusicLibrary {

    private MusicLibrary() {
    }

    private static List<Track> cache;
    private static long cacheAt;

    public static synchronized List<Track> scan(Context c, boolean force) {
        if (!force && cache != null && System.currentTimeMillis() - cacheAt < 60_000) {
            return cache;
        }
        List<Track> out = new ArrayList<>();
        String[] proj = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.ALBUM_ID
        };
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0", null,
                    MediaStore.Audio.Media.TITLE + " ASC");
            if (cur != null) {
                while (cur.moveToNext()) {
                    Track t = new Track();
                    t.id = cur.getLong(0);
                    t.title = nz(cur.getString(1));
                    t.artist = nz(cur.getString(2));
                    t.album = nz(cur.getString(3));
                    t.path = nz(cur.getString(4));
                    t.duration = cur.getLong(5);
                    t.albumId = cur.getLong(6);
                    if (t.path.endsWith(".ogg") || t.path.endsWith(".mp3") || t.path.endsWith(".flac")
                            || t.path.endsWith(".m4a") || t.path.endsWith(".aac") || t.path.endsWith(".wav")
                            || t.path.endsWith(".wma") || t.path.endsWith(".opus")
                            || t.path.endsWith(".mid") || t.path.endsWith(".mp4")) {
                        if (t.duration > 20_000) {
                            out.add(t);
                        }
                    }
                }
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
        Collections.sort(out, new Comparator<Track>() {
            @Override
            public int compare(Track a, Track b) {
                String ta = a.title == null ? "" : a.title;
                String tb = b.title == null ? "" : b.title;
                return ta.compareToIgnoreCase(tb);
            }
        });
        cache = out;
        cacheAt = System.currentTimeMillis();
        return out;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    public static void invalidate() {
        cache = null;
    }
}
