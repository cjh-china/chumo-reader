package com.chumoreader.book;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** CBZ 漫画包：按图片文件名自然序翻阅。 */
public final class CbzParser {

    private CbzParser() {
    }

    public static ParseResult parse(File f) throws Exception {
        ZipFile zip = new ZipFile(f);
        List<String> names = new ArrayList<>();
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) {
                continue;
            }
            String n = e.getName().toLowerCase(Locale.ROOT);
            if (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                    || n.endsWith(".webp") || n.endsWith(".bmp") || n.endsWith(".gif")) {
                names.add(e.getName());
            }
        }
        if (names.isEmpty()) {
            zip.close();
            throw new IOException("漫画包里没有图片");
        }
        Collections.sort(names, NATURAL);

        ParseResult r = new ParseResult();
        r.format = "CBZ";
        r.title = BookUtil.stripExt(f.getName());
        r.pages = new ZipPages(zip, names);
        r.cover = r.pages.render(0, 640);
        return r;
    }

    private static final Comparator<String> NATURAL = new Comparator<String>() {
        @Override
        public int compare(String a, String b) {
            int i = 0, j = 0;
            while (i < a.length() && j < b.length()) {
                char ca = a.charAt(i);
                char cb = b.charAt(j);
                if (Character.isDigit(ca) && Character.isDigit(cb)) {
                    int ni = i, nj = j;
                    while (ni < a.length() && Character.isDigit(a.charAt(ni))) {
                        ni++;
                    }
                    while (nj < b.length() && Character.isDigit(b.charAt(nj))) {
                        nj++;
                    }
                    long va = Long.parseLong(a.substring(i, ni));
                    long vb = Long.parseLong(b.substring(j, nj));
                    if (va != vb) {
                        return va < vb ? -1 : 1;
                    }
                    i = ni;
                    j = nj;
                } else {
                    if (ca != cb) {
                        return ca < cb ? -1 : 1;
                    }
                    i++;
                    j++;
                }
            }
            return (a.length() - i) - (b.length() - j);
        }
    };

    private static final class ZipPages implements ParseResult.ImagePages {
        private final ZipFile zip;
        private final List<String> names;

        ZipPages(ZipFile zip, List<String> names) {
            this.zip = zip;
            this.names = names;
        }

        @Override
        public int count() {
            return names.size();
        }

        @Override
        public Bitmap render(int index, int targetWidth) throws IOException {
            if (index < 0 || index >= names.size()) {
                return null;
            }
            ZipEntry e = zip.getEntry(names.get(index));
            if (e == null) {
                return null;
            }
            InputStream in = zip.getInputStream(e);
            byte[] data;
            try {
                ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    bos.write(buf, 0, n);
                }
                data = bos.toByteArray();
            } finally {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, o);
            int sample = 1;
            if (o.outWidth > 0) {
                while (o.outWidth / sample > targetWidth * 2) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            o2.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, o2);
        }

        @Override
        public void close() {
            try {
                zip.close();
            } catch (Exception ignored) {
            }
        }
    }
}
