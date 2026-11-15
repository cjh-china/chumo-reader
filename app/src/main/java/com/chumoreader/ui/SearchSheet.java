package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.chumoreader.R;
import com.chumoreader.data.Book;
import com.chumoreader.data.Library;

import java.util.ArrayList;
import java.util.List;

/** 书架搜索：输入即时过滤。 */
public class SearchSheet {

    public interface OnPick {
        void onPick(Book b);
    }

    private final Context c;
    private final OnPick onPick;
    private final List<Book> results = new ArrayList<>();
    private SheetDialog dialog;
    private ResultAdapter adapter;

    public SearchSheet(Context c, OnPick onPick) {
        this.c = c;
        this.onPick = onPick;
    }

    public void show() {
        dialog = new SheetDialog(c);
        dialog.setHeightRatio(0.78f);
        LinearLayout body = dialog.body();
        body.setBackgroundColor(Ui.attrColor(c, R.attr.appCard));
        body.addView(Menus.handle(c, Ui.attrColor(c, R.attr.appDivider)));

        TextView title = Menus.text(c, "搜索书架", 16, Ui.attrColor(c, R.attr.appTextPrimary), true);
        title.setPadding(Ui.dp(c, 20), Ui.dp(c, 8), Ui.dp(c, 20), Ui.dp(c, 12));
        body.addView(title);

        LinearLayout searchBox = new LinearLayout(c);
        searchBox.setOrientation(LinearLayout.HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Ui.attrColor(c, R.attr.appSurface));
        g.setCornerRadius(Ui.dpf(c, 999));
        searchBox.setBackground(g);
        searchBox.addView(Menus.icon(c, R.drawable.ic_search,
                Ui.attrColor(c, R.attr.appTextTertiary), 22, 17, null));

        final EditText et = new EditText(c);
        et.setHint("书名、作者或格式");
        et.setHintTextColor(Ui.attrColor(c, R.attr.appTextTertiary));
        et.setTextColor(Ui.attrColor(c, R.attr.appTextPrimary));
        et.setTextSize(14);
        et.setSingleLine(true);
        et.setBackground(null);
        et.setPadding(Ui.dp(c, 8), 0, Ui.dp(c, 8), 0);
        searchBox.addView(et, new LinearLayout.LayoutParams(0, Ui.dp(c, 42), 1f));

        LinearLayout wrap = new LinearLayout(c);
        wrap.setPadding(Ui.dp(c, 18), 0, Ui.dp(c, 18), Ui.dp(c, 10));
        wrap.addView(searchBox, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(c, 44)));
        body.addView(wrap);

        ListView list = new ListView(c);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        adapter = new ResultAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Book b = results.get(position);
            dialog.dismiss();
            onPick.onPick(b);
        });
        body.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        filter("");
        dialog.setOnShowListener(d -> {
            et.requestFocus();
            InputMethodManager imm = (InputMethodManager) c.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
            }
        });
        dialog.show();
    }

    private void filter(String q) {
        results.clear();
        String key = q.trim().toLowerCase(java.util.Locale.ROOT);
        for (Book b : Library.get(c).sortedBooks(0)) {
            if (key.length() == 0) {
                results.add(b);
                continue;
            }
            String title = b.title == null ? "" : b.title.toLowerCase(java.util.Locale.ROOT);
            String author = b.author == null ? "" : b.author.toLowerCase(java.util.Locale.ROOT);
            String format = b.format == null ? "" : b.format.toLowerCase(java.util.Locale.ROOT);
            if (title.contains(key) || author.contains(key) || format.contains(key)) {
                results.add(b);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private class ResultAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public Object getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(c, 20), Ui.dp(c, 12), Ui.dp(c, 20), Ui.dp(c, 12));

            Book b = results.get(position);
            LinearLayout mid = new LinearLayout(c);
            mid.setOrientation(LinearLayout.VERTICAL);
            mid.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView t = Menus.text(c, b.title == null ? "" : b.title, 14,
                    Ui.attrColor(c, R.attr.appTextPrimary), false);
            t.setSingleLine(true);
            t.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(t);

            TextView s = Menus.text(c, (b.author == null || b.author.length() == 0 ? "未知作者" : b.author)
                    + " · " + b.format + " · " + b.progressText(), 11,
                    Ui.attrColor(c, R.attr.appTextTertiary), false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(c, 3);
            mid.addView(s, slp);
            row.addView(mid);
            row.addView(Menus.icon(c, R.drawable.ic_chevron,
                    Ui.attrColor(c, R.attr.appTextTertiary), 22, 15, null));
            return row;
        }
    }
}
