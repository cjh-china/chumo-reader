package com.chumoreader.ui;

import android.graphics.Color;

/** 阅读界面配色：每套 = 纸色 + 文字 + 次要文字 + 顶部栏底色。 */
public final class ReaderTheme {

    private ReaderTheme() {
    }

    public static final String[] NAMES = {"米黄", "羊皮", "护眼", "淡蓝", "纯白", "夜间", "纯黑"};

    public static final int[] BG = {
            0xFFF5EDDF, 0xFFFBF0D2, 0xFFE4EFE1, 0xFFE6EDF5, 0xFFFFFFFF, 0xFF121417, 0xFF000000
    };

    public static final int[] TEXT = {
            0xFF332E28, 0xFF3A3223, 0xFF2A3328, 0xFF26303B, 0xFF1F1F1F, 0xFF9E9E9E, 0xFF8A8A8A
    };

    public static final int[] SUB = {
            0xFF7D7365, 0xFF7E7256, 0xFF6C7B69, 0xFF6B7B8C, 0xFF8A8A8A, 0xFF6E6E6E, 0xFF5F5F5F
    };

    public static int bg(int index) {
        return BG[clamp(index)];
    }

    public static int text(int index) {
        return TEXT[clamp(index)];
    }

    public static int sub(int index) {
        return SUB[clamp(index)];
    }

    public static boolean isNight(int index) {
        return clamp(index) >= 5;
    }

    public static int clamp(int i) {
        return i < 0 ? 0 : (i >= BG.length ? BG.length - 1 : i);
    }

    /** 与纸色协调的强调色：夜间用亮一点的番茄红，否则用品牌色。 */
    public static int accent(int index) {
        return isNight(index) ? 0xFFFF7A52 : 0xFFE84A1F;
    }

    public static int statusBarColor(int index) {
        return bg(index);
    }

    public static int barBg(int index) {
        return isNight(index) ? Color.argb(255, 24, 26, 30) : Color.argb(247, 255, 253, 250);
    }
}
