package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.chumoreader.R;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;
import com.chumoreader.data.Prefs;

import java.util.List;

/** 我的：阅读统计 + 功能入口。 */
public class MinePage extends LinearLayout {

    public interface Host {
        void onOpenMusic();

        void onOpenFormats();

        void onOpenReadingPrefs();

        void onOpenAbout();
    }

    private final Context c;
    private Host host;
    private LinearLayout body;

    public MinePage(Context c) {
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
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setFillViewport(true);
        body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        sv.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    }

    public void reload() {
        if (body == null) {
            return;
        }
        body.removeAllViews();
        List<Book> all = Library.get(c).books();
        int finished = 0;
        long chars = 0;
        for (Book b : all) {
            if (b.finished) {
                finished++;
            }
            chars += b.chars;
        }
        long seconds = Prefs.get(c).totalReadSeconds();

        body.addView(buildHero(all.size()));
        body.addView(buildStats(all.size(), finished, chars, seconds));
        body.addView(buildMenu());
        body.addView(buildFooter());
    }

    private View buildHero(int bookCount) {
        LinearLayout hero = new LinearLayout(c);
        hero.setOrientation(VERTICAL);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF3B2C26, 0xFF7A4B38});
        g.setCornerRadii(new float[]{0, 0, 0, 0, Ui.dpf(c, 22), Ui.dpf(c, 22), Ui.dpf(c, 22), Ui.dpf(c, 22)});
        hero.setBackground(g);
        hero.setPadding(Ui.dp(c, 20), Ui.dp(c, 46), Ui.dp(c, 20), Ui.dp(c, 28));

        LinearLayout row = Menus.horizontal(c);
        TextView avatar = Menus.text(c, "墨", 26, 0xFF7A4B38, true);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTypeface(Typeface.create("serif", Typeface.BOLD));
        GradientDrawable ag = new GradientDrawable();
        ag.setColor(0xFFF7EFE6);
        ag.setShape(GradientDrawable.OVAL);
        avatar.setBackground(ag);
        int size = Ui.dp(c, 62);
        row.addView(avatar, new LinearLayout.LayoutParams(size, size));

        LinearLayout info = new LinearLayout(c);
        info.setOrientation(VERTICAL);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        ilp.leftMargin = Ui.dp(c, 14);
        info.setLayoutParams(ilp);
        TextView name = Menus.text(c, "书友", 19, 0xFFFFFFFF, true);
        info.addView(name);
        TextView sub = Menus.text(c, "书架上有 " + bookCount + " 本书，慢慢读", 12,
                0xCCFFFFFF, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 5);
        info.addView(sub, slp);
        row.addView(info);
        hero.addView(row);
        return hero;
    }

    private String durationText(long seconds) {
        if (seconds < 60) {
            return seconds + " 秒";
        }
        if (seconds < 3600) {
            return (seconds / 60) + " 分钟";
        }
        return String.format(java.util.Locale.CHINA, "%.1f 小时", seconds / 3600f);
    }

    private View buildStats(int books, int finished, long chars, long seconds) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.attrColor(c, R.attr.appCard));
        g.setCornerRadius(Ui.dpf(c, 16));
        card.setBackground(g);
        card.setPadding(Ui.dp(c, 6), Ui.dp(c, 18), Ui.dp(c, 6), Ui.dp(c, 18));

        card.addView(statCell(String.valueOf(books), "在架书籍"));
        card.addView(dividerVertical());
        card.addView(statCell(String.valueOf(finished), "已读完"));
        card.addView(dividerVertical());
        card.addView(statCell(durationText(seconds), "累计阅读"));
        card.addView(dividerVertical());
        card.addView(statCell(chars > 10000 ? (chars / 10000) + " 万" : String.valueOf(chars), "总字数"));

        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 16), Ui.dp(c, 18), Ui.dp(c, 4));
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    private View statCell(String value, String label) {
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(VERTICAL);
        cell.setGravity(Gravity.CENTER);
        TextView v = Menus.text(c, value, 16, Ui.attrColor(c, R.attr.appTextPrimary), true);
        v.setGravity(Gravity.CENTER);
        cell.addView(v);
        TextView l = Menus.text(c, label, 10.5f, Ui.attrColor(c, R.attr.appTextTertiary), false);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = Ui.dp(c, 5);
        cell.addView(l, llp);
        cell.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return cell;
    }

    private View dividerVertical() {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.max(1, Ui.dp(c, 0.6f)),
                Ui.dp(c, 26));
        lp.gravity = Gravity.CENTER_VERTICAL;
        v.setLayoutParams(lp);
        v.setBackgroundColor(Ui.attrColor(c, R.attr.appDivider));
        return v;
    }

    private View buildMenu() {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.attrColor(c, R.attr.appCard));
        g.setCornerRadius(Ui.dpf(c, 16));
        card.setBackground(g);
        card.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));

        int text = Ui.attrColor(c, R.attr.appTextPrimary);
        int sub = Ui.attrColor(c, R.attr.appTextSecondary);
        int accent = Ui.attrColor(c, R.attr.appAccent);

        card.addView(Menus.row(c, R.drawable.ic_font, "阅读排版", "字号 / 行距 / 字体 / 背景",
                text, sub, accent, true, v -> {
                    if (host != null) {
                        host.onOpenReadingPrefs();
                    }
                }));
        card.addView(Menus.divider(c, Ui.attrColor(c, R.attr.appDivider)));
        boolean extPlayer = com.chumoreader.music.ExternalPlayer.isInstalled(c);
        card.addView(Menus.row(c, R.drawable.ic_music, "本地音乐",
                extPlayer ? "已装初墨播放器，将直接打开" : "边读边听，后台播放",
                text, sub, accent, true, v -> {
                    if (host != null) {
                        host.onOpenMusic();
                    }
                }));
        card.addView(Menus.divider(c, Ui.attrColor(c, R.attr.appDivider)));
        card.addView(Menus.row(c, R.drawable.ic_folder, "支持的格式", "12 种电子书格式",
                text, sub, sub, true, v -> {
                    if (host != null) {
                        host.onOpenFormats();
                    }
                }));
        card.addView(Menus.divider(c, Ui.attrColor(c, R.attr.appDivider)));
        card.addView(Menus.row(c, R.drawable.ic_person, "关于初墨阅读", "版本 " + versionName(c),
                text, sub, sub, true, v -> {
                    if (host != null) {
                        host.onOpenAbout();
                    }
                }));

        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 18), Ui.dp(c, 10), Ui.dp(c, 18), 0);
        wrap.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }

    /** 版本号从包信息读，避免每发一版都要手改文案。 */
    private static String versionName(Context c) {
        try {
            String v = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private View buildFooter() {
        TextView t = Menus.text(c, "初墨阅读 · " + versionName(c) + "\n所有内容都留在这台设备上", 11,
                Ui.attrColor(c, R.attr.appTextTertiary), false);
        t.setGravity(Gravity.CENTER);
        t.setLineSpacing(Ui.dpf(c, 4), 1f);
        t.setPadding(0, Ui.dp(c, 26), 0, Ui.dp(c, 30));
        return t;
    }
}
