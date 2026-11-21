package com.chumoreader.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import com.chumoreader.data.Book;
import com.chumoreader.data.Library;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 封面异步加载 + 内存缓存。 */
public final class CoverLoader {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    private CoverLoader() {
    }

    public interface Callback {
        void onLoaded(Bitmap bmp);
    }

    public static Bitmap peek(String key) {
        // LruCache.get(null) 会直接抛 NullPointerException，没有封面的书 coverPath 就是 null
        if (key == null || key.length() == 0) {
            return null;
        }
        return CACHE.get(key);
    }

    public static void load(final Library lib, final Book book, final int targetWidth, final Callback cb) {
        if (book == null || book.coverPath == null || book.coverPath.length() == 0) {
            cb.onLoaded(null);
            return;
        }
        final String key = book.coverPath;
        Bitmap hit = CACHE.get(key);
        if (hit != null && !hit.isRecycled()) {
            cb.onLoaded(hit);
            return;
        }
        final File f = book.coverPath.startsWith("/") ? new File(book.coverPath) : new File(lib.coverDir(), book.coverPath);
        if (!f.exists()) {
            cb.onLoaded(null);
            return;
        }
        POOL.execute(() -> {
            Bitmap bmp = null;
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                int sample = 1;
                while (o.outWidth / sample > Math.max(120, targetWidth * 2)) {
                    sample *= 2;
                }
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = sample;
                o2.inPreferredConfig = Bitmap.Config.RGB_565;
                bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), o2);
            } catch (Exception ignored) {
            }
            final Bitmap result = bmp;
            if (result != null) {
                CACHE.put(key, result);
            }
            MAIN.post(() -> cb.onLoaded(result));
        });
    }

    public static void clear() {
        CACHE.evictAll();
    }
}
