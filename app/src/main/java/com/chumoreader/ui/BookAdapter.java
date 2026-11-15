package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chumoreader.R;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;

import java.util.ArrayList;
import java.util.List;

/** 书架网格 adapter。 */
public class BookAdapter extends BaseAdapter {

    private final Context c;
    private final Library lib;
    private List<Book> items = new ArrayList<>();
    private int columns = 3;
    private int cellW;
    private int coverH;

    public BookAdapter(Context c) {
        this.c = c;
        this.lib = Library.get(c);
    }

    public void setItems(List<Book> list) {
        items = list == null ? new ArrayList<>() : list;
        notifyDataSetChanged();
    }

    public void setColumns(int n) {
        if (n != columns) {
            columns = n;
            cellW = 0;
            notifyDataSetChanged();
        }
    }

    public List<Book> items() {
        return items;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Book getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    private void measureIfNeeded(int parentWidth) {
        if (cellW > 0 || parentWidth <= 0) {
            return;
        }
        int pad = Ui.dp(c, 18);
        int gap = Ui.dp(c, 12);
        int avail = parentWidth - pad * 2 - gap * (columns - 1);
        cellW = Math.max(1, avail / columns);
        coverH = (int) (cellW * 1.38f);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        measureIfNeeded(parent.getWidth() > 0 ? parent.getWidth()
                : c.getResources().getDisplayMetrics().widthPixels);
        Holder h;
        LinearLayout item;
        if (convertView == null) {
            item = new LinearLayout(c);
            item.setOrientation(LinearLayout.VERTICAL);
            h = new Holder();

            FrameLayout box = new FrameLayout(c);
            h.cover = new CoverView(c);
            box.addView(h.cover, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            h.box = box;
            item.addView(box);

            h.title = new TextView(c);
            h.title.setTextSize(12.5f);
            h.title.setMaxLines(2);
            h.title.setEllipsize(TextUtils.TruncateAt.END);
            h.title.setLineSpacing(Ui.dpf(c, 1.5f), 1f);
            h.title.setIncludeFontPadding(false);
            h.title.setGravity(Gravity.START);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tlp.topMargin = Ui.dp(c, 7);
            item.addView(h.title, tlp);

            h.sub = new TextView(c);
            h.sub.setTextSize(10.5f);
            h.sub.setMaxLines(1);
            h.sub.setIncludeFontPadding(false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(c, 3);
            item.addView(h.sub, slp);

            item.setTag(h);
            convertView = item;
        } else {
            item = (LinearLayout) convertView;
            h = (Holder) item.getTag();
        }

        if (cellW > 0) {
            LinearLayout.LayoutParams blp = (LinearLayout.LayoutParams) h.box.getLayoutParams();
            blp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            blp.height = coverH;
            h.box.setLayoutParams(blp);
        }

        Book b = items.get(position);
        h.title.setTextColor(Ui.attrColor(c, R.attr.appTextPrimary));
        h.sub.setTextColor(Ui.attrColor(c, R.attr.appTextTertiary));
        h.title.setText(b.title == null ? "" : b.title);
        h.sub.setText(b.progressText() + " · " + (b.format == null ? "TXT" : b.format));

        h.cover.setData(b.title, b.author, b.format, CoverLoader.peek(b.coverPath));
        final CoverView cv = h.cover;
        final String wantKey = b.coverPath;
        cv.setTag(wantKey);
        if (b.coverPath != null) {
            Bitmap cached = CoverLoader.peek(b.coverPath);
            if (cached == null) {
                CoverLoader.load(lib, b, cellW, bmp -> {
                    if (bmp != null && wantKey != null && wantKey.equals(cv.getTag())) {
                        cv.setBitmap(bmp);
                    }
                });
            }
        }
        return convertView;
    }

    private static class Holder {
        FrameLayout box;
        CoverView cover;
        TextView title;
        TextView sub;
    }
}
