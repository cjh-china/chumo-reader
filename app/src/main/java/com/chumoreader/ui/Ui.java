package com.chumoreader.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.widget.Toast;

/** 尺寸、颜色、背景、提示等界面小工具。 */
public final class Ui {

    private Ui() {
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static float dpf(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static int sp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                c.getResources().getDisplayMetrics()));
    }

    public static Drawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    public static Drawable roundStroke(int color, float radiusPx, int strokeWidth, int strokeColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        g.setStroke(strokeWidth, strokeColor);
        return g;
    }

    /** 带水波纹的可点击背景（API 21+） */
    public static Drawable ripple(Context c, int color, float radiusPx, int rippleColor) {
        Drawable content = round(color, radiusPx);
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(android.content.res.ColorStateList.valueOf(rippleColor), content, null);
        }
        return content;
    }

    public static StateListDrawable selectable(Drawable normal, Drawable pressed) {
        StateListDrawable d = new StateListDrawable();
        d.addState(new int[]{android.R.attr.state_pressed}, pressed);
        d.addState(new int[]{}, normal);
        return d;
    }

    public static void toast(Context c, String msg) {
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static int mix(int a, int b, float t) {
        float u = 1 - t;
        int r = (int) (Color.red(a) * u + Color.red(b) * t);
        int g = (int) (Color.green(a) * u + Color.green(b) * t);
        int bl = (int) (Color.blue(a) * u + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    public static int alpha(int color, float a) {
        int al = Math.max(0, Math.min(255, (int) (255 * a)));
        return (color & 0x00FFFFFF) | (al << 24);
    }

    public static boolean isDark(int color) {
        double lum = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return lum < 128;
    }

    public static void setVisible(View v, boolean visible) {
        if (v != null) {
            v.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /** 读主题里的语义色 */
    public static int attrColor(Context c, int attr) {
        TypedValue tv = new TypedValue();
        if (c.getTheme().resolveAttribute(attr, tv, true)) {
            return tv.data;
        }
        return Color.BLACK;
    }
}
