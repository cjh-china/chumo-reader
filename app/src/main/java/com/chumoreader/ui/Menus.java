package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 常用控件的代码工厂：保证圆角、触控尺寸、字色一致。 */
public final class Menus {

    private Menus() {
    }

    public static ImageView icon(Context c, int res, int tint, float boxDp, float iconDp, View.OnClickListener l) {
        ImageView iv = new ImageView(c);
        iv.setImageResource(res);
        iv.setColorFilter(tint, PorterDuff.Mode.SRC_IN);
        int box = Ui.dp(c, boxDp);
        int icon = Ui.dp(c, iconDp);
        iv.setPadding((box - icon) / 2, (box - icon) / 2, (box - icon) / 2, (box - icon) / 2);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(box, box);
        iv.setLayoutParams(lp);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundColor(Color.TRANSPARENT);
        if (l != null) {
            iv.setOnClickListener(l);
        }
        iv.setClickable(l != null);
        return iv;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) {
            tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        tv.setIncludeFontPadding(false);
        return tv;
    }

    /** 列表行：左图标 + 标题/副标题 + 右箭头 */
    public static LinearLayout row(Context c, int iconRes, String title, String sub,
                                   int textColor, int subColor, int iconTint,
                                   boolean setHeight, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padH = Ui.dp(c, 18);
        row.setPadding(padH, Ui.dp(c, 13), padH, Ui.dp(c, 13));
        if (setHeight) {
            row.setMinimumHeight(Ui.dp(c, 52));
        }

        if (iconRes != 0) {
            ImageView iv = icon(c, iconRes, iconTint, 30, 21, null);
            row.addView(iv);
        }

        LinearLayout mid = new LinearLayout(c);
        mid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        mlp.leftMargin = Ui.dp(c, iconRes != 0 ? 10 : 0);
        mid.setLayoutParams(mlp);

        TextView t = text(c, title, 15, textColor, false);
        mid.addView(t);
        if (sub != null && sub.length() > 0) {
            TextView s = text(c, sub, 12, subColor, false);
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            slp.topMargin = Ui.dp(c, 3);
            s.setLayoutParams(slp);
            mid.addView(s);
        }
        row.addView(mid);

        if (l != null) {
            ImageView arrow = icon(c, com.chumoreader.R.drawable.ic_chevron, subColor, 24, 16, null);
            row.addView(arrow);
            row.setOnClickListener(l);
        }
        row.setBackground(selectable(c, ui_bg(c)));
        return row;
    }

    private static Drawable ui_bg(Context c) {
        return Ui.ripple(c, Color.TRANSPARENT, 0, 0x14000000);
    }

    public static Drawable selectable(Context c, Drawable base) {
        return base;
    }

    public static View divider(Context c, int color) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Math.max(1, Ui.dp(c, 0.6f)));
        lp.leftMargin = Ui.dp(c, 18);
        lp.rightMargin = Ui.dp(c, 18);
        v.setLayoutParams(lp);
        v.setBackgroundColor(color);
        return v;
    }

    /** 弹层顶部的小横条 */
    public static View handle(Context c, int color) {
        View v = new View(c);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(c, 34), Ui.dp(c, 4));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = Ui.dp(c, 8);
        lp.bottomMargin = Ui.dp(c, 4);
        v.setLayoutParams(lp);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(Ui.dpf(c, 2));
        v.setBackground(g);
        return v;
    }

    public static LinearLayout vertical(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout horizontal(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w) {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w);
    }

    /** 胶囊按钮 */
    public static TextView pill(Context c, String label, int bg, int fg) {
        TextView tv = text(c, label, 13, fg, false);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(Ui.dp(c, 14), Ui.dp(c, 8), Ui.dp(c, 14), Ui.dp(c, 8));
        tv.setBackground(Ui.round(bg, Ui.dpf(c, 999)));
        return tv;
    }
}
