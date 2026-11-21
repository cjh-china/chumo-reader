package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chumoreader.R;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;
import com.chumoreader.data.Prefs;

import java.util.List;

/** 书架页：标题栏 + 继续阅读卡片 + 封面网格 + 空态。 */
public class ShelfPage extends LinearLayout {

    public interface Host {
        void onRequestImport();

        void onOpenBook(Book b);

        void onOpenSearch();
    }

    private final Context c;
    private GridView grid;
    private BookAdapter adapter;
    private View emptyView;
    private LinearLayout continueWrap;
    private FrameLayout continueCard;
    private TextView countLabel;
    private Host host;

    public ShelfPage(Context c) {
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

        continueWrap = new LinearLayout(c);
        continueWrap.setOrientation(VERTICAL);
        addView(continueWrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout wrap = new FrameLayout(c);
        grid = new GridView(c);
        grid.setNumColumns(Prefs.get(c).shelfColumns());
        grid.setHorizontalSpacing(Ui.dp(c, 12));
        grid.setVerticalSpacing(Ui.dp(c, 18));
        grid.setPadding(Ui.dp(c, 18), Ui.dp(c, 12), Ui.dp(c, 18), Ui.dp(c, 24));
        grid.setClipToPadding(false);
        grid.setStretchMode(GridView.NO_STRETCH);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        grid.setVerticalScrollBarEnabled(false);
        adapter = new BookAdapter(c);
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, view, pos, id) -> {
            Book b = adapter.getItem(pos);
            if (host != null && b != null) {
                host.onOpenBook(b);
            }
        });
        grid.setOnItemLongClickListener((parent, view, pos, id) -> {
            Book b = adapter.getItem(pos);
            if (b != null) {
                new BookActions(c, b, this::reload).show();
            }
            return true;
        });
        wrap.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // 列宽必须由我们自己算：GridView 在 NO_STRETCH 下不会替 item 定宽
        grid.setColumnWidth(adapter.cellWidth());
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = r - l;
            if (w > 0 && w != or - ol) {
                adapter.setCellWidth(w);
                grid.setColumnWidth(adapter.cellWidth());
            }
        });

        emptyView = buildEmpty();
        wrap.addView(emptyView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        grid.setEmptyView(emptyView);

        addView(wrap, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    private View buildHeader() {
        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 10), 0);

        LinearLayout left = new LinearLayout(c);
        left.setOrientation(VERTICAL);
        TextView t = Menus.text(c, "书架", 22, Ui.attrColor(c, R.attr.appTextPrimary), true);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        countLabel = Menus.text(c, "", 11.5f, Ui.attrColor(c, R.attr.appTextTertiary), false);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = Ui.dp(c, 2);
        countLabel.setLayoutParams(clp);
        left.addView(t);
        left.addView(countLabel);
        bar.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        int tint = Ui.attrColor(c, R.attr.appTextSecondary);
        bar.addView(Menus.icon(c, R.drawable.ic_search, tint, 42, 21, v -> {
            if (host != null) {
                host.onOpenSearch();
            }
        }));
        bar.addView(Menus.icon(c, R.drawable.ic_sort, tint, 42, 21, v -> showSortSheet()));
        bar.addView(Menus.icon(c, R.drawable.ic_add, Ui.attrColor(c, R.attr.appAccent), 44, 23, v -> {
            if (host != null) {
                host.onRequestImport();
            }
        }));
        return bar;
    }

    private View buildEmpty() {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(Ui.dp(c, 30), 0, Ui.dp(c, 30), Ui.dp(c, 60));
        box.setVisibility(View.GONE);

        ImageView icon = new ImageView(c);
        icon.setImageResource(R.drawable.ic_book);
        icon.setColorFilter(Ui.alpha(Ui.attrColor(c, R.attr.appTextTertiary), 0.5f));
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(c, 62), Ui.dp(c, 62));
        icon.setLayoutParams(ilp);
        box.addView(icon);

        TextView t = Menus.text(c, "书架还是空的", 16, Ui.attrColor(c, R.attr.appTextPrimary), true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(c, 16);
        t.setLayoutParams(tlp);
        box.addView(t);

        TextView s = Menus.text(c, "支持 TXT / EPUB / MOBI / PDF / UMD 等 12 种格式", 12.5f,
                Ui.attrColor(c, R.attr.appTextTertiary), false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 8);
        s.setLayoutParams(slp);
        s.setGravity(Gravity.CENTER);
        box.addView(s);

        TextView btn = Menus.text(c, "导入本地书籍", 14, 0xFFFFFFFF, false);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(Ui.dp(c, 26), Ui.dp(c, 12), Ui.dp(c, 26), Ui.dp(c, 12));
        btn.setBackground(Ui.round(Ui.attrColor(c, R.attr.appAccent), Ui.dpf(c, 999)));
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = Ui.dp(c, 22);
        btn.setLayoutParams(blp);
        btn.setOnClickListener(v -> {
            if (host != null) {
                host.onRequestImport();
            }
        });
        box.addView(btn);
        return box;
    }

    private void buildContinueCard(Book b) {
        int card = Ui.attrColor(c, R.attr.appCard);
        int accent = Ui.attrColor(c, R.attr.appAccent);
        int textMain = Ui.attrColor(c, R.attr.appTextPrimary);
        int textSub = Ui.attrColor(c, R.attr.appTextSecondary);

        final FrameLayout card0 = new FrameLayout(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card);
        bg.setCornerRadius(Ui.dpf(c, 14));
        card0.setBackground(bg);
        card0.setElevation(Ui.dpf(c, 1));

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(c, 12), Ui.dp(c, 12), Ui.dp(c, 14), Ui.dp(c, 12));

        GlobalRoundCover mini = new GlobalRoundCover(c);
        mini.setData(b.title, b.format, CoverLoader.peek(b.coverPath));
        if (b.coverPath != null) {
            final GlobalRoundCover target = mini;
            CoverLoader.load(Library.get(c), b, Ui.dp(c, 44), bmp -> {
                if (bmp != null) {
                    target.setBitmap(bmp);
                }
            });
        }
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(Ui.dp(c, 42), Ui.dp(c, 58));
        row.addView(mini, mlp);

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(VERTICAL);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        midLp.leftMargin = Ui.dp(c, 12);
        mid.setLayoutParams(midLp);

        TextView tag = Menus.text(c, "继续阅读", 11, accent, false);
        mid.addView(tag);
        TextView name = Menus.text(c, b.title == null ? "" : b.title, 14.5f, textMain, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = Ui.dp(c, 3);
        mid.addView(name, nlp);
        TextView sub = Menus.text(c, b.progressText() + " · 共 " + b.chapterCount + " 章", 11.5f, textSub, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 3);
        mid.addView(sub, slp);
        row.addView(mid);

        ImageView arrow = Menus.icon(c, R.drawable.ic_chevron, textSub, 26, 16, null);
        row.addView(arrow);

        card0.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        card0.setOnClickListener(v -> {
            if (host != null) {
                host.onOpenBook(b);
            }
        });

        continueCard = card0;
        continueWrap.removeAllViews();
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(Ui.dp(c, 18), Ui.dp(c, 2), Ui.dp(c, 18), Ui.dp(c, 4));
        continueWrap.addView(card0, clp);
        continueWrap.setVisibility(VISIBLE);
    }

    public void reload() {
        Prefs p = Prefs.get(c);
        List<Book> list = Library.get(c).sortedBooks(p.shelfSort());
        adapter.setColumns(p.shelfColumns());
        adapter.setItems(list);
        countLabel.setText(list.isEmpty() ? "还没有书" : ("共 " + list.size() + " 本"));
        grid.setNumColumns(p.shelfColumns());
        grid.setColumnWidth(adapter.cellWidth());

        Book recent = null;
        long best = 0;
        for (Book b : list) {
            if (b.lastReadAt > best && b.progress > 0.001f && !b.finished) {
                best = b.lastReadAt;
                recent = b;
            }
        }
        if (recent != null) {
            buildContinueCard(recent);
        } else {
            continueWrap.removeAllViews();
            continueWrap.setVisibility(GONE);
        }
        if (list.isEmpty()) {
            emptyView.setVisibility(VISIBLE);
        } else {
            emptyView.setVisibility(GONE);
        }
    }

    private void showSortSheet() {
        final SheetDialog d = new SheetDialog(c);
        LinearLayout body = d.body();
        int textMain = Ui.attrColor(c, R.attr.appTextPrimary);
        int sub = Ui.attrColor(c, R.attr.appTextSecondary);
        int accent = Ui.attrColor(c, R.attr.appAccent);
        body.setBackgroundColor(Ui.attrColor(c, R.attr.appCard));

        body.addView(Menus.handle(c, Ui.attrColor(c, R.attr.appDivider)));
        TextView title = Menus.text(c, "书架排列", 15, textMain, true);
        title.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), Ui.dp(c, 6));
        body.addView(title);

        final Prefs p = Prefs.get(c);
        String[] sorts = {"最近阅读", "添加时间", "书名"};
        for (int i = 0; i < sorts.length; i++) {
            final int idx = i;
            boolean on = p.shelfSort() == i;
            LinearLayout row = Menus.row(c, 0, sorts[i], null, on ? accent : textMain,
                    sub, sub, true, v -> {
                        p.setShelfSort(idx);
                        d.dismiss();
                        reload();
                    });
            body.addView(row);
        }
        body.addView(Menus.divider(c, Ui.attrColor(c, R.attr.appDivider)));

        TextView cols = Menus.text(c, "每行显示", 13, sub, false);
        cols.setPadding(Ui.dp(c, 18), Ui.dp(c, 14), Ui.dp(c, 18), Ui.dp(c, 8));
        body.addView(cols);

        LinearLayout colRow = Menus.horizontal(c);
        colRow.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), Ui.dp(c, 18));
        int[] opts = {3, 4, 5};
        for (int n : opts) {
            final int nn = n;
            boolean on = p.shelfColumns() == n;
            TextView chip = Menus.pill(c, n + " 列",
                    on ? Ui.alpha(accent, 0.14f) : Ui.attrColor(c, R.attr.appSurface),
                    on ? accent : textMain);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = Ui.dp(c, 10);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(v -> {
                p.setShelfColumns(nn);
                d.dismiss();
                reload();
            });
            colRow.addView(chip);
        }
        body.addView(colRow);
        d.show();
    }

    /** 小圆角封面（继续阅读卡片用） */
    static class GlobalRoundCover extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private final android.graphics.Path path = new android.graphics.Path();
        private static final android.graphics.Typeface SERIF =
                android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL);
        // 渐变与字体在 onDraw 里只创建一次
        private android.graphics.Shader shader;
        private int shaderW, shaderH;
        private String title = "";
        private String format = "";
        private Bitmap bmp;

        GlobalRoundCover(Context c) {
            super(c);
        }

        void setData(String t, String f, Bitmap b) {
            title = t == null ? "" : t;
            format = f == null ? "" : f;
            bmp = b;
            invalidate();
        }

        void setBitmap(Bitmap b) {
            bmp = b;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float r = w * 0.12f;
            rect.set(0, 0, w, h);
            path.reset();
            path.addRoundRect(rect, r, r, android.graphics.Path.Direction.CW);
            int save = canvas.save();
            canvas.clipPath(path);
            if (bmp != null && !bmp.isRecycled()) {
                float sc = Math.max(w / (float) bmp.getWidth(), h / (float) bmp.getHeight());
                float dw = bmp.getWidth() * sc;
                float dh = bmp.getHeight() * sc;
                rect.set((w - dw) / 2, (h - dh) / 2, (w + dw) / 2, (h + dh) / 2);
                canvas.drawBitmap(bmp, null, rect, paint);
            } else {
                if (shader == null || shaderW != w || shaderH != h) {
                    shaderW = w;
                    shaderH = h;
                    shader = new android.graphics.LinearGradient(0, 0, w, h,
                            new int[]{0xFF2B4257, 0xFF4A6C82}, null,
                            android.graphics.Shader.TileMode.CLAMP);
                }
                paint.setShader(shader);
                canvas.drawRect(0, 0, w, h, paint);
                paint.setShader(null);
                paint.setColor(0xEEFFFFFF);
                paint.setTextSize(w * 0.30f);
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                paint.setTypeface(SERIF);
                String t = title.length() > 2 ? title.substring(0, 2) : title;
                canvas.drawText(t, w / 2f, h * 0.52f, paint);
                paint.setTextSize(w * 0.20f);
                paint.setColor(0x99FFFFFF);
                canvas.drawText(format, w / 2f, h * 0.86f, paint);
            }
            canvas.restoreToCount(save);
        }
    }
}
