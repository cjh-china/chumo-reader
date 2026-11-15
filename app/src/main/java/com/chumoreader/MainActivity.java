package com.chumoreader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chumoreader.data.Book;
import com.chumoreader.data.Importer;
import com.chumoreader.data.Library;
import com.chumoreader.data.Prefs;
import com.chumoreader.music.MusicService;
import com.chumoreader.ui.BottomBar;
import com.chumoreader.ui.MinePage;
import com.chumoreader.ui.MiniPlayer;
import com.chumoreader.ui.SearchSheet;
import com.chumoreader.ui.ShelfPage;
import com.chumoreader.ui.StorePage;
import com.chumoreader.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** 主界面：书架 / 书城 / 我的 三个一级入口 + 迷你播放条。 */
public class MainActivity extends Activity implements ShelfPage.Host {

    private static final int REQ_IMPORT = 2001;

    private FrameLayout content;
    private ShelfPage shelfPage;
    private StorePage storePage;
    private MinePage minePage;
    private MiniPlayer mini;
    private BottomBar bar;

    private AlertDialog importDialog;
    private TextView importText;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applySystemBarCompat();
        buildUi();
        if (Prefs.get(this).musicVolume() > 0) {
            // 音量偏好本身不发声音，这里只是确保服务能被后续调用唤起
        }
        if (getIntent() != null && getIntent().getBooleanExtra("open_music", false)) {
            openMusic();
        }
    }

    /** API 23 以下没有浅色状态栏图标，只能把栏底色调深，否则白字看不见。 */
    private void applySystemBarCompat() {
        if (Build.VERSION.SDK_INT < 23) {
            getWindow().setStatusBarColor(0xFF2A2C31);
        }
        if (Build.VERSION.SDK_INT < 26) {
            getWindow().setNavigationBarColor(0xFF2A2C31);
        }
    }

    private void buildUi() {
        int bg = Ui.attrColor(this, R.attr.appBg);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);

        content = new FrameLayout(this);
        content.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        shelfPage = new ShelfPage(this);
        shelfPage.setHost(this);
        storePage = new StorePage(this);
        minePage = new MinePage(this);

        frame(shelfPage);
        frame(storePage);
        frame(minePage);
        storePage.setVisibility(View.GONE);
        minePage.setVisibility(View.GONE);

        root.addView(content);

        mini = new MiniPlayer(this);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mlp.setMargins(Ui.dp(this, 10), 0, Ui.dp(this, 10), Ui.dp(this, 6));
        mini.setLayoutParams(mlp);
        root.addView(mini);

        bar = new BottomBar(this);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 58));
        bar.setLayoutParams(blp);
        bar.setOnTabSelected(this::switchTab);
        root.addView(bar);

        setContentView(root);
        shelfPage.reload();

        storePage.setHost(new StorePage.Host() {
            @Override
            public void onOpenBook(Book b) {
                ReaderActivity.open(MainActivity.this, b);
            }

            @Override
            public void onOpenSearch() {
                openSearch();
            }

            @Override
            public void onOpenMusic() {
                openMusic();
            }
        });

        minePage.setHost(new MinePage.Host() {
            @Override
            public void onOpenMusic() {
                openMusic();
            }

            @Override
            public void onOpenFormats() {
                showFormats();
            }

            @Override
            public void onOpenReadingPrefs() {
                Book b = lastReadBook();
                if (b != null) {
                    Ui.toast(MainActivity.this, "打开一本书后可在阅读页调排版");
                    ReaderActivity.open(MainActivity.this, b);
                } else {
                    Ui.toast(MainActivity.this, "先在阅读页点「字体」即可调整排版");
                }
            }

            @Override
            public void onOpenAbout() {
                showAbout();
            }
        });
    }

    private void frame(View v) {
        content.addView(v, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private Book lastReadBook() {
        List<Book> list = Library.get(this).sortedBooks(0);
        for (Book b : list) {
            if (b.lastReadAt > 0) {
                return b;
            }
        }
        return list.isEmpty() ? null : list.get(0);
    }

    private void switchTab(int index) {
        shelfPage.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        storePage.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        minePage.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        if (index == 0) {
            shelfPage.reload();
        } else if (index == 1) {
            storePage.reload();
        } else {
            minePage.reload();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (shelfPage != null) {
            shelfPage.reload();
        }
        if (storePage != null) {
            storePage.reload();
        }
        if (minePage != null) {
            minePage.reload();
        }
        if (mini != null) {
            mini.refresh();
        }
    }

    // ---------------- 导入 ----------------

    @Override
    public void onRequestImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivityForResult(i, REQ_IMPORT);
        } catch (Exception e) {
            Ui.toast(this, "没有可用的文件选择器");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_IMPORT || resultCode != RESULT_OK || data == null) {
            return;
        }
        final List<Uri> uris = new ArrayList<>();
        if (data.getClipData() != null) {
            for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                uris.add(data.getClipData().getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            return;
        }
        doImport(uris);
    }

    private void doImport(final List<Uri> uris) {
        importText = new TextView(this);
        importText.setPadding(Ui.dp(this, 24), Ui.dp(this, 22), Ui.dp(this, 24), Ui.dp(this, 22));
        importText.setTextSize(14);
        importText.setTextColor(Ui.attrColor(this, R.attr.appTextPrimary));
        importText.setText("正在导入 0/" + uris.size());
        importDialog = new AlertDialog.Builder(this)
                .setTitle("导入书籍")
                .setView(importText)
                .setCancelable(false)
                .create();
        importDialog.show();

        new Thread(() -> {
            int ok = 0;
            final StringBuilder fail = new StringBuilder();
            for (int i = 0; i < uris.size(); i++) {
                final int idx = i;
                ui.post(() -> {
                    if (importText != null) {
                        importText.setText("正在导入 " + (idx + 1) + "/" + uris.size() + "…");
                    }
                });
                try {
                    Book b = Importer.importUri(MainActivity.this, uris.get(i));
                    Library.get(MainActivity.this).add(b);
                    ok++;
                } catch (Exception e) {
                    String m = e.getMessage() == null ? "解析失败" : e.getMessage();
                    if (fail.length() > 0) {
                        fail.append('\n');
                    }
                    fail.append("· ").append(m);
                }
            }
            final int success = ok;
            ui.post(() -> {
                if (importDialog != null && importDialog.isShowing()) {
                    importDialog.dismiss();
                }
                importDialog = null;
                shelfPage.reload();
                storePage.reload();
                minePage.reload();
                if (fail.length() == 0) {
                    Ui.toast(MainActivity.this, "成功导入 " + success + " 本");
                } else {
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(success > 0 ? "部分导入成功" : "导入失败")
                            .setMessage((success > 0 ? "成功 " + success + " 本\n\n" : "") + fail)
                            .setPositiveButton("知道了", null)
                            .show();
                }
            });
        }).start();
    }

    // ---------------- 搜索 / 音乐 / 说明 ----------------

    @Override
    public void onOpenBook(Book b) {
        ReaderActivity.open(this, b);
    }

    @Override
    public void onOpenSearch() {
        openSearch();
    }

    private void openSearch() {
        SearchSheet s = new SearchSheet(this, book -> ReaderActivity.open(this, book));
        s.show();
    }

    private void openMusic() {
        Intent i = new Intent(this, MusicPlayerActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
    }

    private void showFormats() {
        StringBuilder sb = new StringBuilder();
        for (String[] row : com.chumoreader.book.BookParser.SUPPORTED) {
            sb.append("· ").append(row[0]).append("  ").append(row[1]).append('\n')
                    .append("   ").append(row[2]).append("\n\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("支持的格式")
                .setMessage(sb.toString().trim())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this)
                .setTitle("关于初墨阅读")
                .setMessage("一个从零写起的本地电子书阅读器。\n\n"
                        + "· 零第三方依赖，只用 Android 系统组件\n"
                        + "· 支持 12 种书籍格式，阅读排版可调\n"
                        + "· 内置本地音乐播放，可边读边听\n"
                        + "· 最低兼容 Android 5.0\n\n"
                        + "所有书籍与音乐都留在这台设备上，不联网、不上传。")
                .setPositiveButton("好", null)
                .show();
    }
}
