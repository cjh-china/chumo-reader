package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chumoreader.R;

/** 底部三tab：书架 / 书城 / 我的。 */
public class BottomBar extends LinearLayout {

    public interface OnTabSelected {
        void onSelected(int index);
    }

    private static final int[] ICONS = {R.drawable.ic_book, R.drawable.ic_explore, R.drawable.ic_person};
    private static final String[] LABELS = {"书架", "书城", "我的"};

    private final ImageView[] icons = new ImageView[3];
    private final TextView[] labels = new TextView[3];
    private int current = 0;
    private OnTabSelected listener;

    public BottomBar(Context c) {
        super(c);
        build();
    }

    public BottomBar(Context c, AttributeSet a) {
        super(c, a);
        build();
    }

    private void build() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int accent = Ui.attrColor(getContext(), R.attr.appAccent);
        int normal = Ui.attrColor(getContext(), R.attr.appTextTertiary);
        setBackgroundColor(Ui.attrColor(getContext(), R.attr.appCard));

        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(getContext());
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));

            ImageView iv = new ImageView(getContext());
            iv.setImageResource(ICONS[i]);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(Ui.dp(getContext(), 22), Ui.dp(getContext(), 22));
            ilp.gravity = Gravity.CENTER_HORIZONTAL;
            iv.setLayoutParams(ilp);

            TextView tv = new TextView(getContext());
            tv.setText(LABELS[i]);
            tv.setTextSize(10.5f);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            tlp.gravity = Gravity.CENTER_HORIZONTAL;
            tlp.topMargin = Ui.dp(getContext(), 3);
            tv.setLayoutParams(tlp);
            tv.setIncludeFontPadding(false);

            item.addView(iv);
            item.addView(tv);
            item.setOnClickListener(v -> select(idx, true));

            icons[i] = iv;
            labels[i] = tv;
            addView(item, item.getLayoutParams());
        }
        applyColors(accent, normal);
    }

    private void applyColors(int accent, int normal) {
        for (int i = 0; i < 3; i++) {
            boolean on = i == current;
            icons[i].setColorFilter(on ? accent : normal);
            labels[i].setTextColor(on ? accent : normal);
            labels[i].setTypeface(Typeface.DEFAULT, on ? Typeface.BOLD : Typeface.NORMAL);
        }
    }

    public void select(int index, boolean notify) {
        current = index;
        applyColors(Ui.attrColor(getContext(), R.attr.appAccent),
                Ui.attrColor(getContext(), R.attr.appTextTertiary));
        if (notify && listener != null) {
            listener.onSelected(index);
        }
    }

    public int current() {
        return current;
    }

    public void setOnTabSelected(OnTabSelected l) {
        this.listener = l;
    }

    public static Drawable topLine(Context c, int color) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        return g;
    }
}
