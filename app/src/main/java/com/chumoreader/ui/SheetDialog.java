package com.chumoreader.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.chumoreader.R;

/** 从底部升起的弹层，圆角卡片 + 点击空白关闭。 */
public class SheetDialog extends Dialog {

    private final LinearLayout card;
    private final FrameLayout root;

    public SheetDialog(Context c) {
        super(c, R.style.Theme_ChumoReader_Sheet);
        root = new FrameLayout(c);
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFFFFFFF);
        float r = Ui.dpf(c, 18);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(bg);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM;
        root.addView(card, lp);
        root.setOnClickListener(v -> dismiss());
        card.setOnClickListener(v -> {
        });
        setContentView(root);
    }

    public LinearLayout body() {
        return card;
    }

    /** 让弹层占屏幕高度的比例（内部有 weight 列表时需要）。 */
    public void setHeightRatio(float ratio) {
        int h = (int) (getContext().getResources().getDisplayMetrics().heightPixels * ratio);
        ViewGroup.LayoutParams lp = card.getLayoutParams();
        lp.height = h;
        card.setLayoutParams(lp);
    }

    public void tint(int color, int textColor) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        float r = Ui.dpf(getContext(), 18);
        bg.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        card.setBackground(bg);
    }

    @Override
    public void show() {
        super.show();
        Window w = getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.45f;
            w.setAttributes(lp);
            w.setBackgroundDrawableResource(android.R.color.transparent);
        }
    }
}
