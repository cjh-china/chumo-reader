package com.chumoreader.book;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.IOException;

/** PDF：系统 PdfRenderer 逐页渲染成位图（API 21+ 原生支持）。 */
public final class PdfParser {

    private PdfParser() {
    }

    public static ParseResult parse(File f) throws Exception {
        ParcelFileDescriptor pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
        PdfRenderer renderer;
        try {
            renderer = new PdfRenderer(pfd);
        } catch (Exception e) {
            try {
                pfd.close();
            } catch (Exception ignored) {
            }
            throw new IOException("PDF 打不开：" + e.getMessage());
        }
        ParseResult r = new ParseResult();
        r.format = "PDF";
        r.title = BookUtil.stripExt(f.getName());
        r.pages = new PdfPages(renderer, pfd);
        return r;
    }

    private static final class PdfPages implements ParseResult.ImagePages {
        private final PdfRenderer renderer;
        private final ParcelFileDescriptor pfd;

        PdfPages(PdfRenderer renderer, ParcelFileDescriptor pfd) {
            this.renderer = renderer;
            this.pfd = pfd;
        }

        @Override
        public int count() {
            return renderer.getPageCount();
        }

        @Override
        public Bitmap render(int index, int targetWidth) throws IOException {
            if (index < 0 || index >= count()) {
                return null;
            }
            synchronized (this) {
                PdfRenderer.Page page = renderer.openPage(index);
                try {
                    float scale = targetWidth / (float) page.getWidth();
                    int w = Math.max(1, targetWidth);
                    int h = Math.max(1, (int) (page.getHeight() * scale));
                    // 限制单页像素，避免低内存机型 OOM
                    while ((long) w * h > 4200000L) {
                        w = (int) (w * 0.85f);
                        h = (int) (h * 0.85f);
                    }
                    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
                    bmp.eraseColor(Color.WHITE);
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    return bmp;
                } finally {
                    page.close();
                }
            }
        }

        @Override
        public void close() {
            try {
                renderer.close();
            } catch (Exception ignored) {
            }
            try {
                pfd.close();
            } catch (Exception ignored) {
            }
        }
    }
}
