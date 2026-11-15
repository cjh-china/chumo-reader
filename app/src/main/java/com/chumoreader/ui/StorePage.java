package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chumoreader.R;
import com.chumoreader.book.BookParser;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 书城页：本地书的发现入口（不联网，只做本地聚合与格式说明）。 */
public class StorePage extends LinearLayout {

    public interface Host {
        void onOpenBook(Book b);

        void onOpenSearch();

        void onOpenMusic();
    }

    private final Context c;
    private Host host;
    private LinearLayout body;

    public StorePage(Context c) {
        super(c);
        this.c = c;
        setOrientation(VERTICAL);
        setBackgroundColor(Ui.attrColor(c, R.attr.appBg));
        build();
    }

    public void setHost(Host h) {
        this.host = h;
    }

    private void build() {
        addView(buildHeader(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 58)));

        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setPadding(0, 0, 0, Ui.dp(c, 20));
        body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(VERTICAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18), 0);
        TextView t = Menus.text(c, "书城", 22, Ui.attrColor(c, R.attr.appTextPrimary), true);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        bar.addView(t);
        TextView s = Menus.text(c, "本地书库 · 格式与阅读入口", 11.5f,
                Ui.attrColor(c, R.attr.appTextTertiary), false);
        bar.addView(s);
        return bar;
    }

    public void reload() {
        if (body == null) {
            return;
        }
        body.removeAllViews();
        List<Book> all = Library.get(c).sortedBooks(0);

        body.addView(buildSearchBox());
        body.addView(sectionTitle("格式支持"));
        body.addView(buildFormatStrip());

        if (!all.isEmpty()) {
            body.addView(sectionTitle("继续阅读"));
            body.addView(buildRecentStrip(all));
            body.addView(sectionTitle("按格式分布"));
            body.addView(buildFormatStats(all));
        } else {
            body.addView(buildEmptyHint());
        }

        body.addView(sectionTitle("阅读与音乐"));
        body.addView(buildMusicCard());

        TextView note = Menus.text(c,
                "初墨阅读是一个纯本地阅读器：书和音乐都来自你的设备，不联网抓取、不上传任何内容。",
                11.5f, Ui.attrColor(c, R.attr.appTextTertiary), false);
        note.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 10));
        note.setLineSpacing(Ui.dpf(c, 3), 1f);
        body.addView(note);
    }

    private View buildSearchBox() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.attrColor(c, R.attr.appCard));
        g.setCornerRadius(Ui.dpf(c, 999));
        box.setBackground(g);

        box.addView(Menus.icon(c, R.drawable.ic_search, Ui.attrColor(c, R.attr.appTextTertiary), 24, 18, null));
        TextView hint = Menus.text(c, "搜索书架里的书", 13, Ui.attrColor(c, R.attr.appTextTertiary), false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(c, 8);
        box.addView(hint, lp);
        box.setOnClickListener(v -> {
            if (host != null) {
                host.onOpenSearch();
            }
        });

        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), Ui.dp(c, 8));
        wrap.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 42)));
        return wrap;
    }

    private View sectionTitle(String s) {
        TextView t = Menus.text(c, s, 15, Ui.attrColor(c, R.attr.appTextPrimary), true);
        t.setPadding(Ui.dp(c, 20), Ui.dp(c, 16), Ui.dp(c, 20), Ui.dp(c, 10));
        return t;
    }

    private View buildFormatStrip() {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 8), 0);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        int[] colors = {0xFF2B4257, 0xFF6B3F38, 0xFF35513A, 0xFF443A5E,
                0xFF4E3B24, 0xFF1F3A44, 0xFF5A2A33, 0xFF2E3A46};
        for (int i = 0; i < BookParser.SUPPORTED.length; i++) {
            String[] item = BookParser.SUPPORTED[i];
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(VERTICAL);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{colors[i % colors.length], Ui.mix(colors[i % colors.length], 0xFFFFFFFF, 0.18f)});
            g.setCornerRadius(Ui.dpf(c, 12));
            card.setBackground(g);
            card.setPadding(Ui.dp(c, 13), Ui.dp(c, 12), Ui.dp(c, 13), Ui.dp(c, 12));

            TextView code = Menus.text(c, item[0], 14, 0xFFFFFFFF, true);
            card.addView(code);
            TextView name = Menus.text(c, item[1], 10.5f, 0xB3FFFFFF, false);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            nlp.topMargin = Ui.dp(c, 5);
            card.addView(name, nlp);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(Ui.dp(c, 88), Ui.dp(c, 68));
            clp.rightMargin = Ui.dp(c, 10);
            row.addView(card, clp);
            card.setOnClickListener(v -> Ui.toast(c, item[0] + " · " + item[2]));
        }
        hs.addView(row);
        return hs;
    }

    private View buildRecentStrip(List<Book> all) {
        HorizontalScrollView hs = new HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setClipToPadding(false);
        hs.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 10), 0);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        int shown = 0;
        for (Book b : all) {
            if (shown >= 8) {
                break;
            }
            shown++;
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);

            FrameLayout box = new FrameLayout(c);
            CoverView cover = new CoverView(c);
            cover.setData(b.title, b.author, b.format, CoverLoader.peek(b.coverPath));
            box.addView(cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            if (b.coverPath != null) {
                final CoverView cv = cover;
                CoverLoader.load(Library.get(c), b, Ui.dp(c, 96), new CoverLoader.Callback() {
                    @Override
                    public void onLoaded(Bitmap bmp) {
                        if (bmp != null) {
                            cv.setBitmap(bmp);
                        }
                    }
                });
            }
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(Ui.dp(c, 92), Ui.dp(c, 126));
            item.addView(box, blp);

            TextView t = Menus.text(c, b.title == null ? "" : b.title, 12,
                    Ui.attrColor(c, R.attr.appTextPrimary), false);
            t.setMaxLines(2);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(Ui.dp(c, 92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(c, 7);
            item.addView(t, tlp);

            TextView s = Menus.text(c, b.progressText(), 10.5f,
                    Ui.attrColor(c, R.attr.appTextTertiary), false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(c, 2);
            item.addView(s, slp);

            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            ilp.rightMargin = Ui.dp(c, 14);
            row.addView(item, ilp);
            item.setOnClickListener(v -> {
                if (host != null) {
                    host.onOpenBook(b);
                }
            });
        }
        hs.addView(row);
        return hs;
    }

    private View buildFormatStats(List<Book> all) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (Book b : all) {
            String f = b.format == null ? "TXT" : b.format;
            Integer n = count.get(f);
            count.put(f, n == null ? 1 : n + 1);
        }
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        box.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.attrColor(c, R.attr.appCard));
        g.setCornerRadius(Ui.dpf(c, 14));
        box.setBackground(g);
        box.setPadding(Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16), Ui.dp(c, 14));

        int max = 1;
        for (Integer v : count.values()) {
            max = Math.max(max, v);
        }
        int accent = Ui.attrColor(c, R.attr.appAccent);
        int total = all.size();
        for (Map.Entry<String, Integer> e : count.entrySet()) {
            LinearLayout row = Menus.horizontal(c);
            TextView name = Menus.text(c, e.getKey(), 12,
                    Ui.attrColor(c, R.attr.appTextSecondary), false);
            row.addView(name, new LinearLayout.LayoutParams(Ui.dp(c, 52),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            RatioBar bar = new RatioBar(c, e.getValue() / (float) max,
                    Ui.attrColor(c, R.attr.appSurface), accent);
            row.addView(bar, new LinearLayout.LayoutParams(0, Ui.dp(c, 18), 1f));

            TextView num = Menus.text(c, e.getValue() + " 本 · "
                            + Math.round(e.getValue() * 100f / total) + "%", 11,
                    Ui.attrColor(c, R.attr.appTextTertiary), false);
            num.setGravity(Gravity.END);
            row.addView(num, new LinearLayout.LayoutParams(Ui.dp(c, 78),
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rlp.topMargin = Ui.dp(c, 8);
            box.addView(row, rlp);
        }
        return box;
    }

    private View buildMusicCard() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(Ui.dp(c, 16), Ui.dp(c, 16), Ui.dp(c, 14), Ui.dp(c, 16));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF2B4257, 0xFF4A6C82});
        g.setCornerRadius(Ui.dpf(c, 14));
        card.setBackground(g);

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(VERTICAL);
        TextView t = Menus.text(c, "边读边听", 15, 0xFFFFFFFF, true);
        left.addView(t);
        TextView s = Menus.text(c, "本地音乐库，阅读时后台播放", 11.5f, 0xB3FFFFFF, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 4);
        left.addView(s, slp);
        card.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btn = Menus.text(c, "打开", 12.5f, 0xFF2B4257, false);
        btn.setPadding(Ui.dp(c, 16), Ui.dp(c, 8), Ui.dp(c, 16), Ui.dp(c, 8));
        btn.setBackground(Ui.round(0xFFFFFFFF, Ui.dpf(c, 999)));
        card.addView(btn);
        card.setOnClickListener(v -> {
            if (host != null) {
                host.onOpenMusic();
            }
        });

        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), 0);
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    /** 按比例填充的细条，避免依赖 post 后的宽度。 */
    static class RatioBar extends View {
        private final float ratio;
        private final int trackColor;
        private final int fillColor;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();

        RatioBar(Context c, float ratio, int trackColor, int fillColor) {
            super(c);
            this.ratio = Math.max(0.02f, Math.min(1f, ratio));
            this.trackColor = trackColor;
            this.fillColor = fillColor;
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0) {
                return;
            }
            float barH = Math.max(4f, h * 0.42f);
            float top = (h - barH) / 2f;
            float r = barH / 2f;
            paint.setColor(trackColor);
            rect.set(0, top, w, top + barH);
            canvas.drawRoundRect(rect, r, r, paint);
            paint.setColor(fillColor);
            rect.set(0, top, Math.max(barH, w * ratio), top + barH);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }

    private View buildEmptyHint() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.attrColor(c, R.attr.appCard));
        g.setCornerRadius(Ui.dpf(c, 14));
        box.setBackground(g);
        box.setPadding(Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18), Ui.dp(c, 18));

        TextView t = Menus.text(c, "还没有导入的书", 14,
                Ui.attrColor(c, R.attr.appTextPrimary), true);
        box.addView(t);
        TextView s = Menus.text(c, "去「书架」点右上角 + ，从手机里选一本开始。",
                12, Ui.attrColor(c, R.attr.appTextSecondary), false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 6);
        box.addView(s, slp);

        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 4), Ui.dp(c, 18), 0);
        wrap.addView(box, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }
}
