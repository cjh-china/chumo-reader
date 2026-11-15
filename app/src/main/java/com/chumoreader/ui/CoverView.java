package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

/** 书籍封面：有图用图，没图用渐变 + 竖排书名生成一张「像封面」的封面。 */
public class CoverView extends View {

    private static final int[][] PALETTE = {
            {0xFF2B4257, 0xFF4A6C82},
            {0xFF6B3F38, 0xFFA5645A},
            {0xFF35513A, 0xFF5C8259},
            {0xFF443A5E, 0xFF6F5F92},
            {0xFF4E3B24, 0xFF93714A},
            {0xFF1F3A44, 0xFF3D7A78},
            {0xFF5A2A33, 0xFF8E4A55},
            {0xFF2E3A46, 0xFF556575},
    };

    private String title = "";
    private String author = "";
    private String format = "TXT";
    private Bitmap bitmap;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();

    // 翻页/滚动时 onDraw 会被高频调用，渐变对象按尺寸缓存一次
    private Shader spineShader;
    private Shader glossShader;
    private int shaderW;
    private int shaderH;

    public CoverView(Context c) {
        super(c);
        init();
    }

    public CoverView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        textPaint.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    public void setData(String title, String author, String format, Bitmap bmp) {
        this.title = title == null ? "" : title;
        this.author = author == null ? "" : author;
        this.format = format == null ? "TXT" : format.toUpperCase(java.util.Locale.ROOT);
        this.bitmap = bmp;
        invalidate();
    }

    public void setBitmap(Bitmap bmp) {
        this.bitmap = bmp;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float radius = w * 0.055f;
        rect.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);

        int save = canvas.save();
        canvas.clipPath(clip);

        if (bitmap != null && !bitmap.isRecycled()) {
            drawBitmapCover(canvas, w, h);
        } else {
            drawGeneratedCover(canvas, w, h);
        }

        ensureShaders(w, h);

        // 书脊阴影：左侧一条深色渐变
        paint.setShader(spineShader);
        canvas.drawRect(0, 0, w * 0.10f, h, paint);
        paint.setShader(null);

        // 顶部高光
        paint.setShader(glossShader);
        canvas.drawRect(0, 0, w, h * 0.16f, paint);
        paint.setShader(null);

        canvas.restoreToCount(save);

        // 极细描边
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w * 0.004f));
        paint.setColor(0x1A000000);
        rect.set(paint.getStrokeWidth() / 2, paint.getStrokeWidth() / 2,
                w - paint.getStrokeWidth() / 2, h - paint.getStrokeWidth() / 2);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void ensureShaders(int w, int h) {
        if (spineShader != null && shaderW == w && shaderH == h) {
            return;
        }
        shaderW = w;
        shaderH = h;
        spineShader = new LinearGradient(0, 0, w * 0.10f, 0,
                new int[]{0x55000000, 0x00000000}, null, Shader.TileMode.CLAMP);
        glossShader = new LinearGradient(0, 0, 0, h * 0.16f,
                new int[]{0x1AFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
    }

    private void drawBitmapCover(Canvas canvas, int w, int h) {
        float scale = Math.max(w / (float) bitmap.getWidth(), h / (float) bitmap.getHeight());
        float dw = bitmap.getWidth() * scale;
        float dh = bitmap.getHeight() * scale;
        float left = (w - dw) / 2f;
        float top = (h - dh) / 2f;
        canvas.drawBitmap(bitmap, null, new RectF(left, top, left + dw, top + dh), paint);
    }

    private void drawGeneratedCover(Canvas canvas, int w, int h) {
        int[] colors = PALETTE[Math.abs((title + format).hashCode()) % PALETTE.length];
        paint.setShader(new LinearGradient(0, 0, w * 0.9f, h,
                new int[]{colors[0], colors[1]}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        // 装饰：右上角细圆环
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, w * 0.006f));
        paint.setColor(0x22FFFFFF);
        canvas.drawCircle(w * 0.86f, h * 0.13f, w * 0.20f, paint);
        paint.setStyle(Paint.Style.FILL);

        drawVerticalTitle(canvas, w, h);

        // 底部格式标签
        if (format.length() > 0 && format.length() <= 5) {
            textPaint.setTextSize(w * 0.088f);
            textPaint.setColor(0x88FFFFFF);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(format, w / 2f, h - w * 0.10f, textPaint);
        }
    }

    /** 竖排书名：从右侧往左，最多 7 列，每列 12 字。 */
    private void drawVerticalTitle(Canvas canvas, int w, int h) {
        String t = title.replaceAll("\\s+", "").trim();
        if (t.length() == 0) {
            return;
        }
        int maxCols = 2;
        float colGap = w * 0.17f;
        float startX = w * 0.66f;
        float perCol = Math.max(5, (int) (h / (w * 0.235f)) - 2);
        int total = Math.min(t.length(), (int) (maxCols * perCol));

        textPaint.setColor(0xF2FFFFFF);
        textPaint.setTextSize(w * 0.155f);
        textPaint.setFakeBoldText(true);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setShadowLayer(w * 0.02f, 0, w * 0.012f, 0x55000000);

        int idx = 0;
        for (int col = 0; col < maxCols && idx < total; col++) {
            float x = startX - col * colGap;
            float y = h * 0.20f;
            for (int k = 0; k < perCol && idx < total; k++) {
                String ch = t.substring(idx, idx + 1);
                canvas.drawText(ch, x, y, textPaint);
                y += w * 0.235f;
                idx++;
            }
        }
        textPaint.setShadowLayer(0, 0, 0, 0);
        textPaint.setFakeBoldText(false);

        if (author != null && author.length() > 0) {
            textPaint.setTextSize(w * 0.095f);
            textPaint.setColor(0xAAFFFFFF);
            String a = author.length() > 8 ? author.substring(0, 8) : author;
            canvas.drawText(a, w * 0.30f, h * 0.26f, textPaint);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }
}
