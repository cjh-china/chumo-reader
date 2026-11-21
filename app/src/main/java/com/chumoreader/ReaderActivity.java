package com.chumoreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.chumoreader.book.BookParser;
import com.chumoreader.book.Chapter;
import com.chumoreader.book.ParseResult;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;
import com.chumoreader.data.Prefs;
import com.chumoreader.ui.Menus;
import com.chumoreader.ui.ReaderTheme;
import com.chumoreader.ui.ReaderView;
import com.chumoreader.ui.SheetDialog;
import com.chumoreader.ui.Ui;

import java.io.File;
import java.util.Locale;

/** 阅读页：仿番茄小说的沉浸式阅读界面。 */
public class ReaderActivity extends Activity implements ReaderView.Callback {

    public static void open(Context c, Book b) {
        Intent i = new Intent(c, ReaderActivity.class);
        i.putExtra("bookId", b.id);
        c.startActivity(i);
    }

    private Book book;
    private ParseResult content;
    private Prefs prefs;
    private Library lib;

    private FrameLayout root;
    private ReaderView reader;
    private LinearLayout topBar;
    private LinearLayout bottomBar;
    private TextView titleView;
    private TextView chapterLabel;
    private TextView pageLabel;
    private TextView toastView;
    private View nightBtn;
    private SeekBar progressBar;
    private FrameLayout drawerLayer;
    private LinearLayout drawerPanel;
    private TextView drawerTitle;
    private ListView tocList;
    private View loadingView;

    private boolean menuVisible;
    private boolean busy;
    private int themeIndex;
    private long resumeAt;
    private TextToSpeech tts;
    private boolean ttsReady;
    private boolean speaking;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = Prefs.get(this);
        lib = Library.get(this);
        themeIndex = prefs.readerTheme();

        String id = getIntent().getStringExtra("bookId");
        book = lib.find(id);
        if (book == null) {
            Ui.toast(this, "这本书不见了");
            finish();
            return;
        }

        applyWindowColors();
        buildUi();
        loadBook();
    }

    private void applyWindowColors() {
        Window w = getWindow();
        int bg = ReaderTheme.bg(themeIndex);
        w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bg));
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(ReaderTheme.statusBarColor(themeIndex));
        w.setNavigationBarColor(ReaderTheme.statusBarColor(themeIndex));
        if (Build.VERSION.SDK_INT < 23) {
            // 老系统状态栏图标固定白色，深色底才看得清
            w.setStatusBarColor(ReaderTheme.isNight(themeIndex) ? 0xFF121417 : 0xFF33302B);
        }
        if (Build.VERSION.SDK_INT < 26) {
            w.setNavigationBarColor(ReaderTheme.isNight(themeIndex) ? 0xFF121417 : 0xFF33302B);
        }
        setStatusBarIcons(ReaderTheme.isNight(themeIndex));
    }

    private void setStatusBarIcons(boolean lightIcons) {
        if (Build.VERSION.SDK_INT >= 23) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (lightIcons) {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    // ---------------- 界面搭建 ----------------

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(ReaderTheme.bg(themeIndex));

        reader = new ReaderView(this);
        reader.setCallback(this);
        reader.applyTheme(themeIndex);
        root.addView(reader, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        buildTopBar();
        buildBottomBar();
        buildToast();
        buildDrawer();

        loadingView = buildLoading();
        root.addView(loadingView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        applyBrightness(prefs.brightness());
        updateKeepScreen();
    }

    private void buildTopBar() {
        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 4));
        topBar.setBackgroundColor(ReaderTheme.barBg(themeIndex));
        topBar.setVisibility(View.GONE);
        topBar.setElevation(Ui.dpf(this, 3));

        int tint = ReaderTheme.text(themeIndex);
        topBar.addView(Menus.icon(this, R.drawable.ic_back, tint, 44, 22, v -> finish()));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        mid.setLayoutParams(mlp);
        titleView = Menus.text(this, book.title == null ? "" : book.title, 13.5f, tint, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER);
        mid.addView(titleView);
        topBar.addView(mid);

        topBar.addView(Menus.icon(this, R.drawable.ic_more, tint, 44, 22, v -> showMoreSheet()));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        lp.gravity = Gravity.TOP;
        root.addView(topBar, lp);
    }

    private void buildBottomBar() {
        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        bottomBar.setBackgroundColor(ReaderTheme.barBg(themeIndex));
        bottomBar.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 6));
        bottomBar.setVisibility(View.GONE);
        bottomBar.setElevation(Ui.dpf(this, 6));

        int sub = ReaderTheme.sub(themeIndex);
        int main = ReaderTheme.text(themeIndex);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.HORIZONTAL);
        labels.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        chapterLabel = Menus.text(this, "", 11.5f, sub, false);
        pageLabel = Menus.text(this, "", 11.5f, sub, false);
        labels.addView(chapterLabel, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        pageLabel.setGravity(Gravity.END);
        labels.addView(pageLabel);
        bottomBar.addView(labels);

        progressBar = new SeekBar(this);
        progressBar.setMax(1000);
        progressBar.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        if (Build.VERSION.SDK_INT >= 21) {
            progressBar.setProgressTintList(android.content.res.ColorStateList.valueOf(
                    ReaderTheme.accent(themeIndex)));
            progressBar.setThumbTintList(android.content.res.ColorStateList.valueOf(
                    ReaderTheme.accent(themeIndex)));
        }
        progressBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && content != null && content.chapters.size() > 1) {
                    int idx = (int) (progress / 1000f * (content.chapters.size() - 1));
                    chapterLabel.setText(shorten(content.chapters.get(idx).title, 18));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (content == null || content.chapters.isEmpty()) {
                    return;
                }
                int idx = (int) (seekBar.getProgress() / 1000f * (content.chapters.size() - 1));
                reader.gotoChapter(idx);
                updateMenuState();
            }
        });
        bottomBar.addView(progressBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));

        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8), 0);
        int accent = ReaderTheme.accent(themeIndex);

        tools.addView(toolItem("目录", R.drawable.ic_list, main, v -> showDrawer()));
        tools.addView(toolItem("亮度", R.drawable.ic_bright, main, v -> showBrightSheet()));
        tools.addView(toolItem("听书", R.drawable.ic_headphone, main, v -> toggleTts()));
        tools.addView(toolItem("字体", R.drawable.ic_font, main, v -> showTypeSheet()));

        nightBtn = toolItem("夜间", R.drawable.ic_night,
                ReaderTheme.isNight(themeIndex) ? accent : main, v -> toggleNight());
        tools.addView(nightBtn);
        tools.addView(toolItem("设置", R.drawable.ic_tune, main, v -> showTypeSheet()));

        bottomBar.addView(tools, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        root.addView(bottomBar, lp);
    }

    private View toolItem(String label, int icon, int tint, View.OnClickListener l) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        iv.setColorFilter(tint);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(this, 21), Ui.dp(this, 21));
        ilp.gravity = Gravity.CENTER_HORIZONTAL;
        item.addView(iv, ilp);
        TextView tv = Menus.text(this, label, 10.5f, tint, false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.CENTER_HORIZONTAL;
        tlp.topMargin = Ui.dp(this, 3);
        item.addView(tv, tlp);
        item.setOnClickListener(l);
        return item;
    }

    private void buildToast() {
        toastView = Menus.text(this, "", 12.5f, ReaderTheme.text(themeIndex), false);
        toastView.setGravity(Gravity.CENTER);
        toastView.setPadding(Ui.dp(this, 14), Ui.dp(this, 8), Ui.dp(this, 14), Ui.dp(this, 8));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.alpha(ReaderTheme.isNight(themeIndex) ? 0xFF2A2D33 : 0xFFFFFFFF, 0.94f));
        g.setCornerRadius(Ui.dpf(this, 999));
        toastView.setBackground(g);
        toastView.setAlpha(0f);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(this, 92);
        root.addView(toastView, lp);
    }

    private void buildDrawer() {
        drawerLayer = new FrameLayout(this);
        drawerLayer.setVisibility(View.GONE);

        View scrim = new View(this);
        scrim.setBackgroundColor(0x88000000);
        scrim.setOnClickListener(v -> hideDrawer());
        drawerLayer.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        drawerPanel = new LinearLayout(this);
        drawerPanel.setOrientation(LinearLayout.VERTICAL);
        drawerPanel.setBackgroundColor(ReaderTheme.isNight(themeIndex) ? 0xFF191B1F : 0xFFFBF8F3);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 18), Ui.dp(this, 10));
        LinearLayout titleRow = Menus.horizontal(this);
        TextView t = Menus.text(this, "目录", 17, ReaderTheme.text(themeIndex), true);
        titleRow.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(Menus.icon(this, R.drawable.ic_close, ReaderTheme.sub(themeIndex), 34, 18,
                v -> hideDrawer()));
        head.addView(titleRow);
        drawerTitle = Menus.text(this, "", 11.5f, ReaderTheme.sub(themeIndex), false);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = Ui.dp(this, 4);
        head.addView(drawerTitle, dlp);
        drawerPanel.addView(head);

        tocList = new ListView(this);
        tocList.setDivider(null);
        tocList.setDividerHeight(0);
        tocList.setVerticalScrollBarEnabled(false);
        tocList.setAdapter(new TocAdapter());
        tocList.setOnItemClickListener((parent, view, position, id) -> {
            reader.gotoChapter(position);
            hideDrawer();
            updateMenuState();
        });
        drawerPanel.addView(tocList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.80f),
                ViewGroup.LayoutParams.MATCH_PARENT);
        drawerLayer.addView(drawerPanel, plp);
        root.addView(drawerLayer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private View buildLoading() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(ReaderTheme.bg(themeIndex));
        box.setClickable(true);

        android.widget.ProgressBar pb = new android.widget.ProgressBar(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        box.addView(pb, plp);

        TextView t = Menus.text(this, "正在排版…", 13, ReaderTheme.sub(themeIndex), false);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = Ui.dp(this, 14);
        box.addView(t, tlp);
        return box;
    }

    // ---------------- 加载 ----------------

    private void loadBook() {
        busy = true;
        final File f = book.file(lib.booksDir());
        new Thread(() -> {
            ParseResult r = null;
            String err = null;
            try {
                if (f == null || !f.exists()) {
                    throw new java.io.IOException("书籍文件已丢失");
                }
                r = BookParser.parse(f);
            } catch (Exception e) {
                err = e.getMessage() == null ? "解析失败" : e.getMessage();
            }
            final ParseResult result = r;
            final String error = err;
            ui.post(() -> {
                busy = false;
                loadingView.setVisibility(View.GONE);
                if (result == null) {
                    new AlertDialog.Builder(ReaderActivity.this)
                            .setTitle("打不开这本书")
                            .setMessage(error)
                            .setPositiveButton("返回", (d, w) -> finish())
                            .setCancelable(false)
                            .show();
                    return;
                }
                content = result;
                titleView.setText(result.title == null ? book.title : result.title);
                book.chapterCount = result.isImageMode() ? result.pages.count() : result.chapters.size();
                drawerTitle.setText("共 " + book.chapterCount + " 章 · 已读 "
                        + Math.round(book.progress * 100) + "%");
                reader.setBook(result, book.chapterIndex, 0);
                final int restoreChar = book.lastChar;
                reader.post(() -> {
                    if (!result.isImageMode() && restoreChar > 0) {
                        reader.gotoChar(restoreChar);
                    }
                    updateMenuState();
                });
                updateMenuState();
            });
        }).start();
    }

    // ---------------- 菜单 ----------------

    private void toggleMenu() {
        menuVisible = !menuVisible;
        if (menuVisible) {
            topBar.setVisibility(View.VISIBLE);
            bottomBar.setVisibility(View.VISIBLE);
            topBar.setAlpha(0f);
            bottomBar.setAlpha(0f);
            topBar.animate().alpha(1f).setDuration(180).start();
            bottomBar.animate().alpha(1f).setDuration(180).start();
            reader.applyMenuInsets(true);
            updateMenuState();
        } else {
            topBar.animate().alpha(0f).setDuration(140).withEndAction(() ->
                    topBar.setVisibility(View.GONE)).start();
            bottomBar.animate().alpha(0f).setDuration(140).withEndAction(() ->
                    bottomBar.setVisibility(View.GONE)).start();
            reader.applyMenuInsets(false);
        }
    }

    private void hideMenu() {
        if (menuVisible) {
            toggleMenu();
        }
    }

    private void updateMenuState() {
        if (content == null) {
            return;
        }
        chapterLabel.setText(shorten(reader.chapterTitle(), 16));
        if (content.isImageMode()) {
            pageLabel.setText((reader.chapterIndex() + 1) + " / " + reader.chapterCount());
        } else {
            pageLabel.setText((reader.pageIndex() + 1) + " / " + Math.max(1, reader.pageCount()));
        }
        progressBar.setProgress((int) (reader.overallProgress() * 1000));
    }

    private static String shorten(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() > n ? s.substring(0, n) + "…" : s;
    }

    private void showChapterToast(String text) {
        toastView.setText(text);
        toastView.animate().cancel();
        toastView.setAlpha(0f);
        toastView.animate().alpha(1f).setDuration(160).start();
        ui.removeCallbacks(hideToast);
        ui.postDelayed(hideToast, 1600);
    }

    private final Runnable hideToast = () -> toastView.animate().alpha(0f).setDuration(220).start();

    // ---------------- 目录 ----------------

    private void showDrawer() {
        if (content == null) {
            return;
        }
        drawerLayer.setVisibility(View.VISIBLE);
        drawerPanel.setTranslationX(-drawerPanel.getWidth());
        drawerPanel.animate().translationX(0).setDuration(220).start();
        int idx = reader.chapterIndex();
        drawerTitle.setText("共 " + book.chapterCount + " 章 · 当前第 " + (idx + 1) + " 章");
        tocList.setSelection(Math.max(0, idx - 2));
    }

    private void hideDrawer() {
        drawerPanel.animate().translationX(-drawerPanel.getWidth()).setDuration(180)
                .withEndAction(() -> drawerLayer.setVisibility(View.GONE)).start();
    }

    private class TocAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            if (content == null) {
                return 0;
            }
            return content.isImageMode() ? content.pages.count() : content.chapters.size();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(ReaderActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(ReaderActivity.this, 18), Ui.dp(ReaderActivity.this, 12),
                    Ui.dp(ReaderActivity.this, 14), Ui.dp(ReaderActivity.this, 12));

            boolean current = position == reader.chapterIndex();
            int color = current ? ReaderTheme.accent(themeIndex) : ReaderTheme.text(themeIndex);

            TextView tv = new TextView(ReaderActivity.this);
            tv.setTextColor(color);
            tv.setTextSize(13.5f);
            tv.setSingleLine(true);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (current) {
                tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            }
            String label;
            if (content.isImageMode()) {
                label = "第 " + (position + 1) + " 页";
            } else {
                Chapter ch = content.chapters.get(position);
                label = ch.title == null ? ("第 " + (position + 1) + " 章") : ch.title;
            }
            tv.setText(label);
            row.addView(tv, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            if (current) {
                ImageView dot = new ImageView(ReaderActivity.this);
                dot.setImageResource(R.drawable.ic_check);
                dot.setColorFilter(ReaderTheme.accent(themeIndex));
                row.addView(dot, new LinearLayout.LayoutParams(Ui.dp(ReaderActivity.this, 15),
                        Ui.dp(ReaderActivity.this, 15)));
            }
            return row;
        }
    }

    // ---------------- 亮度 ----------------

    private void showBrightSheet() {
        final SheetDialog d = new SheetDialog(this);
        LinearLayout body = d.body();
        int card = ReaderTheme.isNight(themeIndex) ? 0xFF191B1F : 0xFFFFFFFF;
        int text = ReaderTheme.isNight(themeIndex) ? 0xFFE8E8E8 : 0xFF1B1C1E;
        int sub = ReaderTheme.isNight(themeIndex) ? 0xFF8E8E8E : 0xFF6A6F77;
        body.setBackgroundColor(card);
        body.addView(Menus.handle(this, 0x33000000));

        LinearLayout head = Menus.horizontal(this);
        head.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 6));
        head.addView(Menus.icon(this, R.drawable.ic_bright, sub, 30, 20, null));
        TextView t = Menus.text(this, "亮度", 14, text, false);
        t.setPadding(Ui.dp(this, 8), 0, 0, 0);
        head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView val = Menus.text(this, "", 12, sub, false);
        head.addView(val);
        body.addView(head);

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        int cur = prefs.brightness() < 0 ? 70 : prefs.brightness();
        sb.setProgress(cur);
        val.setText(cur + "%");
        sb.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        if (Build.VERSION.SDK_INT >= 21) {
            sb.setProgressTintList(android.content.res.ColorStateList.valueOf(ReaderTheme.accent(themeIndex)));
            sb.setThumbTintList(android.content.res.ColorStateList.valueOf(ReaderTheme.accent(themeIndex)));
        }
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                val.setText(progress + "%");
                applyBrightness(progress);
                prefs.setBrightness(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        body.addView(sb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 44)));

        TextView tip = Menus.text(this, "阅读时在屏幕左侧上下滑动也可以调亮度", 11.5f, sub, false);
        tip.setPadding(Ui.dp(this, 20), Ui.dp(this, 2), Ui.dp(this, 20), Ui.dp(this, 20));
        body.addView(tip);
        d.show();
    }

    private void applyBrightness(int percent) {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        if (percent <= 0) {
            lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        } else {
            lp.screenBrightness = Math.max(0.04f, Math.min(1f, percent / 100f));
        }
        getWindow().setAttributes(lp);
    }

    // ---------------- 排版设置 ----------------

    private void showTypeSheet() {
        final SheetDialog d = new SheetDialog(this);
        final LinearLayout body = d.body();
        final boolean night = ReaderTheme.isNight(themeIndex);
        body.setBackgroundColor(night ? 0xFF191B1F : 0xFFFFFFFF);
        renderTypeSheet(d, body, night);
        d.show();
    }

    private void renderTypeSheet(final SheetDialog d, LinearLayout body, final boolean night) {
        body.removeAllViews();
        final int text = night ? 0xFFE8E8E8 : 0xFF1B1C1E;
        final int sub = night ? 0xFF8E8E8E : 0xFF6A6F77;
        final int accent = ReaderTheme.accent(themeIndex);
        final int surface = night ? 0xFF26292E : 0xFFEFF1F4;

        body.addView(Menus.handle(this, 0x33000000));

        // 字号
        LinearLayout sizeRow = Menus.horizontal(this);
        sizeRow.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 10));
        TextView label = Menus.text(this, "字号", 13, sub, false);
        sizeRow.addView(label, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView minus = Menus.pill(this, "A−", surface, text);
        minus.setOnClickListener(v -> {
            prefs.setFontSize(prefs.fontSize() - 1);
            applyType();
            renderTypeSheet(d, body, night);
        });
        sizeRow.addView(minus);
        TextView sizeVal = Menus.text(this, String.valueOf(prefs.fontSize()), 14, text, true);
        sizeVal.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), 0);
        sizeRow.addView(sizeVal);
        TextView plus = Menus.pill(this, "A+", surface, text);
        plus.setOnClickListener(v -> {
            prefs.setFontSize(prefs.fontSize() + 1);
            applyType();
            renderTypeSheet(d, body, night);
        });
        sizeRow.addView(plus);
        body.addView(sizeRow);

        // 行距
        body.addView(sectionLabel("行距", sub));
        LinearLayout ll = chipRow();
        String[] lineNames = {"紧凑", "标准", "宽松", "很松"};
        for (int i = 0; i < lineNames.length; i++) {
            final int idx = i;
            boolean on = prefs.lineSpace() == i;
            TextView chip = Menus.pill(this, lineNames[i], on ? Ui.alpha(accent, 0.14f) : surface,
                    on ? accent : text);
            chip.setOnClickListener(v -> {
                prefs.setLineSpace(idx);
                applyType();
                renderTypeSheet(d, body, night);
            });
            ll.addView(chip);
        }
        body.addView(ll);

        // 字体
        body.addView(sectionLabel("字体", sub));
        LinearLayout fl = chipRow();
        String[] fontNames = {"宋体", "黑体", "等宽"};
        for (int i = 0; i < fontNames.length; i++) {
            final int idx = i;
            boolean on = prefs.fontFamily() == i;
            TextView chip = Menus.pill(this, fontNames[i], on ? Ui.alpha(accent, 0.14f) : surface,
                    on ? accent : text);
            chip.setOnClickListener(v -> {
                prefs.setFontFamily(idx);
                applyType();
                renderTypeSheet(d, body, night);
            });
            fl.addView(chip);
        }
        body.addView(fl);

        // 缩进
        body.addView(sectionLabel("首行缩进", sub));
        LinearLayout il = chipRow();
        String[] indents = {"无", "一字", "两字"};
        for (int i = 0; i < indents.length; i++) {
            final int idx = i;
            boolean on = prefs.indent() == idx;
            TextView chip = Menus.pill(this, indents[i], on ? Ui.alpha(accent, 0.14f) : surface,
                    on ? accent : text);
            chip.setOnClickListener(v -> {
                prefs.setIndent(idx);
                applyType();
                renderTypeSheet(d, body, night);
            });
            il.addView(chip);
        }
        body.addView(il);

        // 背景
        body.addView(sectionLabel("阅读背景", sub));
        LinearLayout bl = new LinearLayout(this);
        bl.setOrientation(LinearLayout.HORIZONTAL);
        bl.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), Ui.dp(this, 12));
        for (int i = 0; i < ReaderTheme.BG.length; i++) {
            final int idx = i;
            FrameLayout swatch = new FrameLayout(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(ReaderTheme.bg(i));
            g.setCornerRadius(Ui.dpf(this, 10));
            g.setStroke(Ui.dp(this, 1.5f), idx == themeIndex ? accent : 0x22000000);
            swatch.setBackground(g);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(Ui.dp(this, 34), Ui.dp(this, 34));
            slp.rightMargin = Ui.dp(this, 9);
            swatch.setLayoutParams(slp);
            swatch.setOnClickListener(v -> {
                themeIndex = idx;
                prefs.setReaderTheme(idx);
                applyThemeEverywhere();
                renderTypeSheet(d, body, ReaderTheme.isNight(themeIndex));
            });
            bl.addView(swatch);
        }
        body.addView(bl);

        // 翻页
        body.addView(sectionLabel("翻页方式", sub));
        LinearLayout pl = chipRow();
        String[] anims = {"覆盖", "无动画"};
        for (int i = 0; i < anims.length; i++) {
            final int idx = i;
            boolean on = prefs.pageAnim() == i;
            TextView chip = Menus.pill(this, anims[i], on ? Ui.alpha(accent, 0.14f) : surface,
                    on ? accent : text);
            chip.setOnClickListener(v -> {
                prefs.setPageAnim(idx);
                renderTypeSheet(d, body, night);
            });
            pl.addView(chip);
        }
        body.addView(pl);

        // 其他
        LinearLayout extra = Menus.horizontal(this);
        extra.setPadding(Ui.dp(this, 20), Ui.dp(this, 6), Ui.dp(this, 20), Ui.dp(this, 20));
        final TextView vk = Menus.pill(this, "音量键翻页：" + (prefs.volumeKey() ? "开" : "关"),
                surface, text);
        vk.setOnClickListener(v -> {
            prefs.setVolumeKey(!prefs.volumeKey());
            renderTypeSheet(d, body, night);
        });
        extra.addView(vk);
        final TextView ks = Menus.pill(this, "常亮：" + (prefs.keepScreen() ? "开" : "关"), surface, text);
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        klp.leftMargin = Ui.dp(this, 10);
        ks.setLayoutParams(klp);
        ks.setOnClickListener(v -> {
            prefs.setKeepScreen(!prefs.keepScreen());
            updateKeepScreen();
            renderTypeSheet(d, body, night);
        });
        extra.addView(ks);
        body.addView(extra);
    }

    private TextView sectionLabel(String s, int color) {
        TextView tv = Menus.text(this, s, 12, color, false);
        tv.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 7));
        return tv;
    }

    private LinearLayout chipRow() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setPadding(Ui.dp(this, 20), 0, Ui.dp(this, 20), 0);
        return l;
    }

    private void applyType() {
        reader.refreshTypography();
        updateMenuState();
    }

    private void toggleNight() {
        themeIndex = ReaderTheme.isNight(themeIndex) ? 0 : 5;
        prefs.setReaderTheme(themeIndex);
        applyThemeEverywhere();
    }

    private void applyThemeEverywhere() {
        reader.applyTheme(themeIndex);
        int bg = ReaderTheme.bg(themeIndex);
        root.setBackgroundColor(bg);
        topBar.setBackgroundColor(ReaderTheme.barBg(themeIndex));
        bottomBar.setBackgroundColor(ReaderTheme.barBg(themeIndex));
        applyWindowColors();
        if (loadingView != null) {
            loadingView.setBackgroundColor(bg);
        }
        if (drawerPanel != null) {
            drawerPanel.setBackgroundColor(ReaderTheme.isNight(themeIndex) ? 0xFF191B1F : 0xFFFBF8F3);
        }
        toastView.setTextColor(ReaderTheme.text(themeIndex));
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.alpha(ReaderTheme.isNight(themeIndex) ? 0xFF2A2D33 : 0xFFFFFFFF, 0.94f));
        g.setCornerRadius(Ui.dpf(this, 999));
        toastView.setBackground(g);
        rebuildBars();
    }

    private void rebuildBars() {
        // 重建顶/底栏，让图标颜色跟上主题
        boolean wasVisible = menuVisible;
        if (topBar != null) {
            root.removeView(topBar);
        }
        if (bottomBar != null) {
            root.removeView(bottomBar);
        }
        buildTopBar();
        buildBottomBar();
        menuVisible = wasVisible;
        topBar.setVisibility(wasVisible ? View.VISIBLE : View.GONE);
        bottomBar.setVisibility(wasVisible ? View.VISIBLE : View.GONE);
        updateMenuState();
    }

    // ---------------- 更多菜单 ----------------

    private void showMoreSheet() {
        final SheetDialog d = new SheetDialog(this);
        LinearLayout body = d.body();
        boolean night = ReaderTheme.isNight(themeIndex);
        int card = night ? 0xFF191B1F : 0xFFFFFFFF;
        int text = night ? 0xFFE8E8E8 : 0xFF1B1C1E;
        int sub = night ? 0xFF8E8E8E : 0xFF6A6F77;
        int accent = ReaderTheme.accent(themeIndex);
        body.setBackgroundColor(card);
        body.addView(Menus.handle(this, 0x33000000));

        body.addView(Menus.row(this, R.drawable.ic_headphone, "朗读本页", "用系统语音念出来",
                text, sub, accent, true, v -> {
                    d.dismiss();
                    toggleTts();
                }));

        body.addView(Menus.row(this, R.drawable.ic_share, "复制本页文字", null,
                text, sub, sub, true, v -> {
                    d.dismiss();
                    copyPage();
                }));

        body.addView(Menus.row(this, R.drawable.ic_list, "跳到章节", "打开目录",
                text, sub, sub, true, v -> {
                    d.dismiss();
                    showDrawer();
                }));

        body.addView(Menus.row(this, R.drawable.ic_book, "书籍信息", null,
                text, sub, sub, true, v -> {
                    d.dismiss();
                    showBookInfo();
                }));
        body.addView(Menus.handle(this, 0x00000000));
        d.show();
    }

    private void copyPage() {
        String text = reader.currentPageText();
        if (text == null || text.length() == 0) {
            Ui.toast(this, "本页没有可复制的文字");
            return;
        }
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("初墨阅读", text));
        Ui.toast(this, "本页文字已复制");
    }

    private void showBookInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("书名：").append(book.title).append('\n');
        sb.append("作者：").append(book.author == null || book.author.length() == 0 ? "未知" : book.author).append('\n');
        sb.append("格式：").append(book.format).append('\n');
        sb.append("章节：").append(book.chapterCount).append('\n');
        if (book.chars > 0) {
            sb.append("字数：约 ").append(book.chars).append(" 字\n");
        }
        sb.append("进度：").append(Math.round(reader.overallProgress() * 100)).append('%');
        new AlertDialog.Builder(this)
                .setTitle("书籍信息")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    // ---------------- 听书 ----------------

    private void toggleTts() {
        if (speaking) {
            stopTts();
            return;
        }
        String text = reader.currentPageText();
        if (text == null || text.trim().length() == 0) {
            Ui.toast(this, "本页没有可朗读的文字");
            return;
        }
        if (tts == null) {
            tts = new TextToSpeech(this, status -> {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    tts.setLanguage(Locale.CHINA);
                    tts.setSpeechRate(0.92f);
                    speakNow(text);
                } else {
                    Ui.toast(ReaderActivity.this, "系统语音引擎不可用");
                }
            });
        } else if (ttsReady) {
            speakNow(text);
        } else {
            Ui.toast(this, "语音引擎还在准备");
        }
    }

    private void speakNow(String text) {
        try {
            speaking = true;
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chumo-page");
            showChapterToast("开始朗读本页");
        } catch (Exception e) {
            speaking = false;
            Ui.toast(this, "朗读失败");
        }
    }

    private void stopTts() {
        try {
            if (tts != null) {
                tts.stop();
            }
        } catch (Exception ignored) {
        }
        speaking = false;
        showChapterToast("已停止朗读");
    }

    // ---------------- 生命周期 ----------------

    private void updateKeepScreen() {
        if (prefs.keepScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeAt = System.currentTimeMillis();
        applyBrightness(prefs.brightness());
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveProgress();
        if (resumeAt > 0) {
            long sec = (System.currentTimeMillis() - resumeAt) / 1000;
            if (sec > 5) {
                prefs.addReadSeconds(sec);
            }
        }
        stopTts();
    }

    private void saveProgress() {
        if (content == null || book == null) {
            return;
        }
        book.chapterIndex = reader.chapterIndex();
        book.lastChar = reader.currentCharOffset();
        book.progress = reader.overallProgress();
        book.lastReadAt = System.currentTimeMillis();
        book.chapterCount = reader.chapterCount();
        if (book.progress >= 0.999f) {
            book.finished = true;
        }
        lib.update(book);
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        if (reader != null) {
            reader.release();
        }
        if (content != null && content.pages != null) {
            content.pages.close();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayer != null && drawerLayer.getVisibility() == View.VISIBLE) {
            hideDrawer();
            return;
        }
        if (menuVisible) {
            hideMenu();
            return;
        }
        saveProgress();
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (prefs.volumeKey() && content != null) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                reader.nextPage();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                reader.prevPage();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    // ---------------- ReaderView 回调 ----------------

    @Override
    public void onTapCenter() {
        toggleMenu();
    }

    @Override
    public void onPageChanged() {
        updateMenuState();
        saveProgressLite();
    }

    private void saveProgressLite() {
        if (content == null || book == null) {
            return;
        }
        book.chapterIndex = reader.chapterIndex();
        book.lastChar = reader.currentCharOffset();
        book.progress = reader.overallProgress();
        book.lastReadAt = System.currentTimeMillis();
        lib.update(book);
    }

    @Override
    public void onChapterChanged(int index, String title) {
        updateMenuState();
        showChapterToast(title);
        saveProgressLite();
    }

    @Override
    public void onLongPress() {
        final SheetDialog d = new SheetDialog(this);
        LinearLayout body = d.body();
        boolean night = ReaderTheme.isNight(themeIndex);
        int text = night ? 0xFFE8E8E8 : 0xFF1B1C1E;
        int sub = night ? 0xFF8E8E8E : 0xFF6A6F77;
        int accent = ReaderTheme.accent(themeIndex);
        body.setBackgroundColor(night ? 0xFF191B1F : 0xFFFFFFFF);
        body.addView(Menus.handle(this, 0x33000000));
        body.addView(Menus.row(this, R.drawable.ic_share, "复制本页文字", null, text, sub, sub, true, v -> {
            d.dismiss();
            copyPage();
        }));
        body.addView(Menus.row(this, R.drawable.ic_headphone, "朗读本页", null, text, sub, accent, true, v -> {
            d.dismiss();
            toggleTts();
        }));
        body.addView(Menus.row(this, R.drawable.ic_night, "切换夜间", null, text, sub, sub, true, v -> {
            d.dismiss();
            toggleNight();
        }));
        body.addView(Menus.handle(this, 0x00000000));
        d.show();
    }

    @Override
    public void onBrightnessDelta(float delta) {
        int cur = prefs.brightness() < 0 ? 70 : prefs.brightness();
        int next = Math.max(1, Math.min(100, cur + Math.round(delta * 100)));
        prefs.setBrightness(next);
        applyBrightness(next);
    }

    @Override
    public void onNeedMenuRefresh() {
        updateMenuState();
    }
}
