package com.chumoreader.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chumoreader.R;
import com.chumoreader.ReaderActivity;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 长按一本书后的操作菜单。 */
public class BookActions {

    private final Context c;
    private final Book book;
    private final Runnable onChanged;

    public BookActions(Context c, Book book, Runnable onChanged) {
        this.c = c;
        this.book = book;
        this.onChanged = onChanged;
    }

    public void show() {
        final SheetDialog d = new SheetDialog(c);
        LinearLayout body = d.body();
        int textMain = Ui.attrColor(c, R.attr.appTextPrimary);
        int sub = Ui.attrColor(c, R.attr.appTextSecondary);
        int accent = Ui.attrColor(c, R.attr.appAccent);
        body.setBackgroundColor(Ui.attrColor(c, R.attr.appCard));
        body.addView(Menus.handle(c, Ui.attrColor(c, R.attr.appDivider)));

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Ui.dp(c, 18), Ui.dp(c, 8), Ui.dp(c, 18), Ui.dp(c, 12));
        TextView t = Menus.text(c, book.title == null ? "" : book.title, 16, textMain, true);
        t.setMaxLines(2);
        head.addView(t);
        TextView s = Menus.text(c, (book.author == null || book.author.length() == 0 ? "未知作者" : book.author)
                + " · " + book.format + " · " + book.chapterCount + " 章", 12, sub, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(c, 5);
        head.addView(s, slp);
        body.addView(head);
        body.addView(Menus.divider(c, Ui.attrColor(c, R.attr.appDivider)));

        body.addView(Menus.row(c, R.drawable.ic_book, "开始阅读",
                book.progress > 0.001f ? book.progressText() : "第一次打开", textMain, sub, accent, true, v -> {
                    d.dismiss();
                    ReaderActivity.open(c, book);
                }));

        body.addView(Menus.row(c, R.drawable.ic_bookmark, "置顶到书架最前", null, textMain, sub, sub, true, v -> {
            Library.get(c).moveTop(book);
            d.dismiss();
            if (onChanged != null) {
                onChanged.run();
            }
        }));

        body.addView(Menus.row(c, R.drawable.ic_time, "书籍详情", null, textMain, sub, sub, true,
                v -> {
                    d.dismiss();
                    showDetail();
                }));

        body.addView(Menus.row(c, R.drawable.ic_delete, "删除这本书", "同时移除本地文件",
                c.getResources().getColor(R.color.error), sub, c.getResources().getColor(R.color.error), true, v -> {
                    d.dismiss();
                    confirmDelete();
                }));

        body.addView(Menus.handle(c, 0x00000000));
        d.show();
    }

    private void showDetail() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy 年 M 月 d 日", Locale.CHINA);
        StringBuilder sb = new StringBuilder();
        sb.append("书名：").append(book.title).append('\n');
        sb.append("作者：").append(book.author == null || book.author.length() == 0 ? "未知" : book.author).append('\n');
        sb.append("格式：").append(book.format).append('\n');
        sb.append("章节：").append(book.chapterCount).append(" 章\n");
        if (book.chars > 0) {
            sb.append("字数：约 ").append(book.chars).append(" 字\n");
        }
        sb.append("进度：").append(book.progressText()).append('\n');
        sb.append("导入：").append(fmt.format(new Date(book.addedAt))).append('\n');
        if (book.lastReadAt > 0) {
            sb.append("上次阅读：").append(fmt.format(new Date(book.lastReadAt)));
        }
        new AlertDialog.Builder(c)
                .setTitle("书籍详情")
                .setMessage(sb.toString())
                .setPositiveButton("知道了", null)
                .show();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(c)
                .setTitle("删除《" + (book.title == null ? "" : book.title) + "》？")
                .setMessage("书会从书架移除，本地文件也会一起删除。阅读进度将无法恢复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Library.get(c).remove(book);
                        if (onChanged != null) {
                            onChanged.run();
                        }
                        Ui.toast(c, "已删除");
                    }
                })
                .show();
    }
}
