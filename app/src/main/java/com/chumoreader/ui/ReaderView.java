package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.StaticLayout;
import android.text.Spanned;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import com.chumoreader.book.Block;
import com.chumoreader.book.Chapter;
import com.chumoreader.book.ParseResult;
import com.chumoreader.data.Prefs;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 阅读视图：自绘分页 + 左右翻页手势 + 覆盖式翻页动画。
 * 文本型按行高切页，图片型（PDF/CBZ）逐页渲染。
 */
public class ReaderView extends View {

    public static final int MODE_TEXT = 0;
    public static final int MODE_IMAGE = 1;

    public interface Callback {
        void onTapCenter();

        void onPageChanged();

        void onChapterChanged(int index, String title);

        void onLongPress();

        void onBrightnessDelta(float delta);

        void onNeedMenuRefresh();
    }

    private static class Page {
        int startLine;
        int endLine;
        int charStart;
    }

    private final Prefs prefs;
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint footerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // 页脚时间：每分钟才格式化一次，避免 onDraw 里反复 new SimpleDateFormat
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm", Locale.CHINA);
    private String timeText = "";
    private long lastMinute = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private ParseResult book;
    private int mode = MODE_TEXT;
    private Callback callback;

    private StaticLayout layout;
    private final List<Page> pages = new ArrayList<>();
    private int chapterIndex = 0;
    private int pageIndex = 0;

    private int padH, padTop, padBottom, footerH;
    private int contentW, contentH;

    private int textColor = 0xFF332E28;
    private int subColor = 0xFF7D7365;
    private int bgColor = 0xFFF5EDDF;
    private int themeIndex = 0;

    // 手势状态
    private float downX, downY, lastY;
    private boolean dragging;
    private boolean tapped;
    private boolean longPressFired;
    private boolean brightnessMode;
    private int animDir;
    private float animProgress;
    private VelocityTracker velocity;
    private final int touchSlop;
    private final int minFling;

    // 图片模式
    private Bitmap pageBitmap;
    private int imagePage;

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (!dragging && callback != null) {
                longPressFired = true;
                callback.onLongPress();
            }
        }
    };

    public ReaderView(Context c) {
        super(c);
        prefs = Prefs.get(c);
        touchSlop = ViewConfiguration.get(c).getScaledTouchSlop();
        minFling = (int) (ViewConfiguration.get(c).getScaledMinimumFlingVelocity() * 0.8f);
        footerPaint.setTextSize(Ui.sp(c, 10.5f));
        buildPaint();
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    private void buildPaint() {
        paint.setTextSize(Ui.sp(getContext(), prefs.fontSize()));
        paint.setColor(textColor);
        paint.setTypeface(typeface());
        paint.setSubpixelText(true);
        paint.setAntiAlias(true);
    }

    private Typeface typeface() {
        switch (prefs.fontFamily()) {
            case 1:
                return Typeface.create("sans-serif", Typeface.NORMAL);
            case 2:
                return Typeface.create("monospace", Typeface.NORMAL);
            default:
                return Typeface.create("serif", Typeface.NORMAL);
        }
    }

    // ---------------- 数据与分页 ----------------

    public void setBook(ParseResult r, int chapter, int page) {
        this.book = r;
        this.mode = r.isImageMode() ? MODE_IMAGE : MODE_TEXT;
        if (mode == MODE_IMAGE) {
            imagePage = Math.max(0, chapter);
            requestImage();
        } else {
            this.chapterIndex = clampChapter(chapter);
            if (contentW <= 0) {
                // 视图还没测量，等一帧再排版
                post(() -> {
                    buildLayout();
                    gotoPage(page);
                    invalidate();
                });
            } else {
                buildLayout();
                gotoPage(page);
            }
        }
        invalidate();
    }

    public void applyTheme(int index) {
        themeIndex = ReaderTheme.clamp(index);
        bgColor = ReaderTheme.bg(themeIndex);
        textColor = ReaderTheme.text(themeIndex);
        subColor = ReaderTheme.sub(themeIndex);
        paint.setColor(textColor);
        footerPaint.setColor(subColor);
        invalidate();
    }

    public void refreshTypography() {
        buildPaint();
        if (mode == MODE_TEXT) {
            int keepChapter = chapterIndex;
            int keepChar = currentCharOffset();
            buildLayout();
            gotoChar(keepChar);
            if (chapterIndex != keepChapter) {
                chapterIndex = keepChapter;
            }
            invalidate();
        }
    }

    public int themeIndex() {
        return themeIndex;
    }

    public int bgColor() {
        return bgColor;
    }

    private int clampChapter(int i) {
        if (book == null || book.chapters.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(i, book.chapters.size() - 1));
    }

    public void buildLayout() {
        pages.clear();
        layout = null;
        if (book == null || book.chapters.isEmpty() || contentW <= 0 || contentH <= 0) {
            return;
        }
        Chapter ch = book.chapters.get(chapterIndex);
        CharSequence text = compose(ch);
        if (text.length() == 0) {
            text = "（本章暂无内容）";
        }
        int spacingAdd = (int) (paint.getTextSize() * (0.55f + prefs.lineSpace() * 0.22f));
        layout = new StaticLayout(text, paint, contentW, Layout.Alignment.ALIGN_NORMAL,
                1f, spacingAdd, true);
        paginate();
    }

    /** 把章节块拼成带样式的文本 */
    private CharSequence compose(Chapter ch) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int indent = (int) (paint.getTextSize() * (prefs.indent() > 0 ? 1.9f : 0));
        int titleSize = (int) (paint.getTextSize() * 1.32f);

        for (int i = 0; i < ch.blocks.size(); i++) {
            Block b = ch.blocks.get(i);
            if (i > 0) {
                sb.append("\n");
                if (b.type == Block.H1 || b.type == Block.H2 || b.type == Block.H3) {
                    sb.append("\n");
                }
            }
            int start = sb.length();
            sb.append(b.type == Block.SEP ? "· · ·" : b.text);
            int end = sb.length();

            if (end > start) {
                if (b.type == Block.H1 || b.type == Block.H2) {
                    sb.setSpan(new RelativeSizeSpan(1.32f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (b.type == Block.H3) {
                    sb.setSpan(new RelativeSizeSpan(1.14f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (b.type == Block.QUOTE) {
                    sb.setSpan(new StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    sb.setSpan(new LeadingMarginSpan.Standard(indent, indent), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                } else if (b.type == Block.PARA && indent > 0) {
                    sb.setSpan(new LeadingMarginSpan.Standard(indent, 0), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        // 为了避免「标题字号」影响首行，这里不再追加尾部空行
        return sb;
    }

    private void paginate() {
        pages.clear();
        if (layout == null) {
            return;
        }
        int lineCount = layout.getLineCount();
        int startLine = 0;
        int guard = 0;
        while (startLine < lineCount && guard++ < 20000) {
            int top = layout.getLineTop(startLine);
            int endLine = startLine;
            while (endLine < lineCount) {
                int bottom = layout.getLineBottom(endLine);
                if (bottom - top > contentH && endLine > startLine) {
                    break;
                }
                endLine++;
            }
            if (endLine == startLine) {
                endLine = startLine + 1;
            }
            Page p = new Page();
            p.startLine = startLine;
            p.endLine = endLine;
            p.charStart = layout.getLineStart(startLine);
            pages.add(p);
            startLine = endLine;
        }
        if (pages.isEmpty()) {
            Page p = new Page();
            p.startLine = 0;
            p.endLine = Math.max(1, lineCount);
            pages.add(p);
        }
    }

    // ---------------- 绘制 ----------------

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w == oldw && h == oldh && layout != null) {
            return;
        }
        padH = Ui.dp(getContext(), 22);
        padTop = Ui.dp(getContext(), 42);
        padBottom = Ui.dp(getContext(), 34);
        footerH = Ui.dp(getContext(), 22);
        contentW = Math.max(1, w - padH * 2);
        contentH = Math.max(1, h - padTop - padBottom - footerH);
        if (mode == MODE_TEXT && book != null) {
            int keep = currentCharOffset();
            buildLayout();
            gotoChar(keep);
        }
        requestImage();
    }

    public void setPaddingDp(int h, int top, int bottom) {
        padH = Ui.dp(getContext(), h);
        padTop = Ui.dp(getContext(), top);
        padBottom = Ui.dp(getContext(), bottom);
        contentW = Math.max(1, getWidth() - padH * 2);
        contentH = Math.max(1, getHeight() - padTop - padBottom - footerH);
        if (mode == MODE_TEXT && book != null) {
            buildLayout();
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(bgColor);
        if (book == null) {
            return;
        }
        if (mode == MODE_IMAGE) {
            drawImage(canvas);
            drawFooter(canvas, imagePage, imagePage + 1, imageCount(), 0);
            return;
        }
        if (layout == null || pages.isEmpty()) {
            return;
        }
        if (animDir == 0) {
            drawTextPage(canvas, pageIndex, 0);
        } else if (animDir == 1) {
            drawTextPage(canvas, pageIndex, 0);
            if (pageIndex + 1 < pages.size()) {
                drawTextPage(canvas, pageIndex + 1, getWidth() * (1 - animProgress));
            }
        } else {
            // 上一页跟手：当前页左移，上一页从左侧跟进来
            drawTextPage(canvas, pageIndex, -getWidth() * animProgress);
            if (pageIndex - 1 >= 0) {
                drawTextPage(canvas, pageIndex - 1, -getWidth() + getWidth() * animProgress);
            }
        }
        drawFooter(canvas, pageIndex, pageIndex + 1, pages.size(), 0);
    }

    private void drawTextPage(Canvas canvas, int idx, float dx) {
        if (layout == null || idx < 0 || idx >= pages.size()) {
            return;
        }
        Page p = pages.get(idx);
        int save = canvas.save();
        canvas.translate(dx, 0);
        canvas.clipRect(padH, padTop, padH + contentW, padTop + contentH);
        int dy = layout.getLineTop(p.startLine);
        canvas.translate(padH, padTop - dy);
        layout.draw(canvas);
        canvas.restoreToCount(save);
    }

    private void drawFooter(Canvas canvas, int pageIdx, int pageNo, int total, float dx) {
        int w = getWidth();
        int h = getHeight();
        footerPaint.setColor(subColor);
        footerPaint.setTextSize(Ui.sp(getContext(), 10.5f));
        footerPaint.setTextAlign(Paint.Align.LEFT);
        long now = System.currentTimeMillis();
        long minute = now / 60000L;
        if (minute != lastMinute) {
            lastMinute = minute;
            timeText = timeFmt.format(new Date(now));
        }
        canvas.drawText(timeText, padH, h - padBottom + Ui.dp(getContext(), 2), footerPaint);

        footerPaint.setTextAlign(Paint.Align.CENTER);
        String mid;
        if (mode == MODE_IMAGE) {
            mid = pageNo + " / " + Math.max(1, total);
        } else {
            mid = chapterShort() + "  " + pageNo + "/" + Math.max(1, total);
        }
        canvas.drawText(mid, w / 2f, h - padBottom + Ui.dp(getContext(), 2), footerPaint);

        footerPaint.setTextAlign(Paint.Align.RIGHT);
        String right = overallPercent();
        canvas.drawText(right, w - padH, h - padBottom + Ui.dp(getContext(), 2), footerPaint);
    }

    private String chapterShort() {
        if (book == null || book.chapters.isEmpty()) {
            return "";
        }
        String t = book.chapters.get(clampChapter(chapterIndex)).title;
        if (t == null) {
            return "";
        }
        return t.length() > 12 ? t.substring(0, 12) + "…" : t;
    }

    private String overallPercent() {
        if (book == null || book.chapters.isEmpty()) {
            return "";
        }
        float chapterPart = (chapterIndex + (pages.isEmpty() ? 0 : (pageIndex + 1f) / pages.size()))
                / (float) book.chapters.size();
        return Math.round(chapterPart * 100) + "%";
    }

    // ---------------- 图片模式 ----------------

    private int imageCount() {
        return book != null && book.pages != null ? book.pages.count() : 0;
    }

    private void requestImage() {
        if (mode != MODE_IMAGE || book == null || book.pages == null) {
            return;
        }
        if (getWidth() <= 0) {
            return;
        }
        int want = getWidth() - padH * 2;
        try {
            pageBitmap = book.pages.render(imagePage, Math.max(240, want));
        } catch (Exception e) {
            pageBitmap = null;
        }
        invalidate();
    }

    private void drawImage(Canvas canvas) {
        if (pageBitmap == null || pageBitmap.isRecycled()) {
            footerPaint.setColor(subColor);
            footerPaint.setTextAlign(Paint.Align.CENTER);
            footerPaint.setTextSize(Ui.sp(getContext(), 13));
            canvas.drawText("正在渲染…", getWidth() / 2f, getHeight() / 2f, footerPaint);
            return;
        }
        int availW = getWidth() - padH * 2;
        int availH = getHeight() - padTop - padBottom - footerH;
        float sc = Math.min(availW / (float) pageBitmap.getWidth(), availH / (float) pageBitmap.getHeight());
        float dw = pageBitmap.getWidth() * sc;
        float dh = pageBitmap.getHeight() * sc;
        float left = (getWidth() - dw) / 2f;
        float top = padTop + (availH - dh) / 2f;
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(pageBitmap, null, new android.graphics.RectF(left, top, left + dw, top + dh), p);
    }

    // ---------------- 手势 ----------------

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                lastY = e.getY();
                dragging = false;
                tapped = false;
                longPressFired = false;
                brightnessMode = false;
                animDir = 0;
                animProgress = 0;
                if (velocity == null) {
                    velocity = VelocityTracker.obtain();
                } else {
                    velocity.clear();
                }
                velocity.addMovement(e);
                handler.postDelayed(longPressRunnable, 460);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) {
                    velocity.addMovement(e);
                }
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    return true;
                }
                handler.removeCallbacks(longPressRunnable);
                if (longPressFired) {
                    // 长按菜单已经弹出，别在它背后继续拖页
                    return true;
                }
                if (!dragging) {
                    dragging = true;
                    if (Math.abs(dy) > Math.abs(dx) * 1.4f && downX < getWidth() * 0.32f) {
                        brightnessMode = true;
                    }
                }
                if (brightnessMode) {
                    float delta = (lastY - e.getY()) / (float) getHeight();
                    if (Math.abs(delta) > 0.002f && callback != null) {
                        callback.onBrightnessDelta(delta);
                    }
                    lastY = e.getY();
                    return true;
                }
                if (mode == MODE_IMAGE) {
                    return true;
                }
                if (dx < 0) {
                    if (pageIndex + 1 < pages.size()) {
                        animDir = 1;
                        animProgress = Math.min(1f, -dx / getWidth());
                    }
                } else if (dx > 0) {
                    if (pageIndex - 1 >= 0) {
                        animDir = -1;
                        animProgress = Math.min(1f, dx / getWidth());
                    }
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP: {
                handler.removeCallbacks(longPressRunnable);
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                float vx = 0;
                if (velocity != null) {
                    velocity.computeCurrentVelocity(1000);
                    vx = velocity.getXVelocity();
                }
                if (brightnessMode) {
                    brightnessMode = false;
                    return true;
                }
                if (dragging) {
                    if (mode == MODE_IMAGE) {
                        if (dx < -getWidth() * 0.18f) {
                            imageNext();
                        } else if (dx > getWidth() * 0.18f) {
                            imagePrev();
                        }
                        dragging = false;
                        return true;
                    }
                    boolean flingNext = vx < -minFling && dx < 0;
                    boolean flingPrev = vx > minFling && dx > 0;
                    if (animDir == 1 && (animProgress > 0.28f || flingNext)) {
                        snapNext();
                    } else if (animDir == -1 && (animProgress > 0.28f || flingPrev)) {
                        snapPrev();
                    } else {
                        snapBack();
                    }
                    dragging = false;
                    return true;
                }
                if (!longPressFired && Math.abs(dx) < touchSlop && Math.abs(dy) < touchSlop) {
                    tapped = true;
                    handleTap(e.getX());
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                handler.removeCallbacks(longPressRunnable);
                snapBack();
                dragging = false;
                return true;
            }
        }
        return super.onTouchEvent(e);
    }

    private void handleTap(float x) {
        float w = getWidth();
        if (x < w * 0.30f) {
            prevPage();
        } else if (x > w * 0.70f) {
            nextPage();
        } else if (callback != null) {
            callback.onTapCenter();
        }
    }

    private void snapBack() {
        animDir = 0;
        animProgress = 0;
        invalidate();
    }

    private void snapNext() {
        animDir = 1;
        animateFlip(1f, () -> {
            pageIndex++;
            animDir = 0;
            animProgress = 0;
            if (callback != null) {
                callback.onPageChanged();
            }
            invalidate();
        });
    }

    private void snapPrev() {
        animDir = -1;
        animateFlip(1f, () -> {
            pageIndex--;
            animDir = 0;
            animProgress = 0;
            if (callback != null) {
                callback.onPageChanged();
            }
            invalidate();
        });
    }

    /** 从当前进度补完到 1，再落地。 */
    private void animateFlip(float target, Runnable end) {
        final float from = animProgress;
        final long dur = Math.max(90, (long) (200 * Math.abs(target - from)));
        final long startAt = android.os.SystemClock.uptimeMillis();
        postOnAnimation(new Runnable() {
            @Override
            public void run() {
                float t = Math.min(1f, (android.os.SystemClock.uptimeMillis() - startAt) / (float) dur);
                // ease-out
                float e = 1f - (1f - t) * (1f - t);
                animProgress = from + (target - from) * e;
                invalidate();
                if (t < 1f) {
                    postOnAnimation(this);
                } else if (end != null) {
                    end.run();
                }
            }
        });
    }

    // ---------------- 翻页 / 章节 ----------------

    public void nextPage() {
        if (mode == MODE_IMAGE) {
            imageNext();
            return;
        }
        if (pages.isEmpty()) {
            return;
        }
        if (pageIndex + 1 < pages.size()) {
            pageIndex++;
            invalidate();
            notifyPage();
        } else {
            nextChapter();
        }
    }

    public void prevPage() {
        if (mode == MODE_IMAGE) {
            imagePrev();
            return;
        }
        if (pages.isEmpty()) {
            return;
        }
        if (pageIndex > 0) {
            pageIndex--;
            invalidate();
            notifyPage();
        } else {
            prevChapter();
        }
    }

    public void nextChapter() {
        if (book == null || chapterIndex + 1 >= book.chapters.size()) {
            Ui.toast(getContext(), "已经是最后一章了");
            return;
        }
        chapterIndex++;
        buildLayout();
        pageIndex = 0;
        invalidate();
        if (callback != null) {
            callback.onChapterChanged(chapterIndex, chapterTitle());
            callback.onPageChanged();
        }
    }

    public void prevChapter() {
        if (chapterIndex <= 0) {
            Ui.toast(getContext(), "已经是第一章了");
            return;
        }
        chapterIndex--;
        buildLayout();
        pageIndex = Math.max(0, pages.size() - 1);
        invalidate();
        if (callback != null) {
            callback.onChapterChanged(chapterIndex, chapterTitle());
            callback.onPageChanged();
        }
    }

    private void imageNext() {
        if (book == null || book.pages == null) {
            return;
        }
        if (imagePage + 1 < book.pages.count()) {
            imagePage++;
            requestImage();
        } else {
            Ui.toast(getContext(), "已经是最后一页");
        }
        if (callback != null) {
            callback.onPageChanged();
        }
    }

    private void imagePrev() {
        if (imagePage > 0) {
            imagePage--;
            requestImage();
        }
        if (callback != null) {
            callback.onPageChanged();
        }
    }

    private void notifyPage() {
        if (callback != null) {
            callback.onPageChanged();
        }
    }

    // ---------------- 对外状态 ----------------

    public int chapterIndex() {
        return mode == MODE_IMAGE ? imagePage : chapterIndex;
    }

    public int pageIndex() {
        return pageIndex;
    }

    public int pageCount() {
        return mode == MODE_IMAGE ? imageCount() : pages.size();
    }

    public String chapterTitle() {
        if (book == null || book.chapters.isEmpty()) {
            return "";
        }
        if (mode == MODE_IMAGE) {
            return "第 " + (imagePage + 1) + " 页";
        }
        String t = book.chapters.get(clampChapter(chapterIndex)).title;
        return t == null ? "" : t;
    }

    public int chapterCount() {
        return book == null ? 0 : (mode == MODE_IMAGE ? imageCount() : book.chapters.size());
    }

    public float overallProgress() {
        if (book == null) {
            return 0;
        }
        if (mode == MODE_IMAGE) {
            int n = Math.max(1, imageCount());
            return (imagePage + 1f) / n;
        }
        int n = Math.max(1, book.chapters.size());
        float inChapter = pages.isEmpty() ? 0 : (pageIndex + 1f) / pages.size();
        return (chapterIndex + inChapter) / n;
    }

    public int currentCharOffset() {
        if (mode == MODE_IMAGE || pages.isEmpty() || layout == null) {
            return 0;
        }
        int idx = Math.max(0, Math.min(pageIndex, pages.size() - 1));
        return pages.get(idx).charStart;
    }

    public void gotoChapter(int index) {
        if (book == null || book.chapters.isEmpty()) {
            return;
        }
        if (mode == MODE_IMAGE) {
            imagePage = Math.max(0, Math.min(index, imageCount() - 1));
            requestImage();
            return;
        }
        chapterIndex = clampChapter(index);
        buildLayout();
        pageIndex = 0;
        invalidate();
        if (callback != null) {
            callback.onChapterChanged(chapterIndex, chapterTitle());
            callback.onPageChanged();
        }
    }

    public void gotoChar(int charOffset) {
        if (pages.isEmpty() || charOffset <= 0) {
            return;
        }
        for (int i = 0; i < pages.size(); i++) {
            Page p = pages.get(i);
            int nextStart = i + 1 < pages.size() ? pages.get(i + 1).charStart : Integer.MAX_VALUE;
            if (charOffset >= p.charStart && charOffset < nextStart) {
                pageIndex = i;
                return;
            }
        }
        pageIndex = Math.max(0, pages.size() - 1);
    }

    private void gotoPage(int page) {
        if (pages.isEmpty()) {
            pageIndex = 0;
            return;
        }
        pageIndex = Math.max(0, Math.min(page, pages.size() - 1));
    }

    public ParseResult book() {
        return book;
    }

    /** 当前页的纯文字，用于朗读 / 复制。 */
    public String currentPageText() {
        if (mode == MODE_IMAGE) {
            return "";
        }
        if (layout == null || pages.isEmpty()) {
            return "";
        }
        Page p = pages.get(Math.max(0, Math.min(pageIndex, pages.size() - 1)));
        try {
            int endLine = Math.min(p.endLine, layout.getLineCount()) - 1;
            if (endLine < p.startLine) {
                return "";
            }
            int s = layout.getLineStart(p.startLine);
            int e = layout.getLineEnd(endLine);
            return layout.getText().subSequence(s, e).toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    public void release() {
        handler.removeCallbacksAndMessages(null);
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
        if (pageBitmap != null && !pageBitmap.isRecycled()) {
            pageBitmap.recycle();
        }
        pageBitmap = null;
    }
}
