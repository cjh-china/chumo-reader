package com.chumoreader.data;

import android.content.Context;
import android.content.SharedPreferences;

/** 阅读与界面偏好。所有值都有合理默认，第一次打开就能读。 */
public class Prefs {

    private static Prefs inst;

    private final SharedPreferences sp;

    private Prefs(Context c) {
        sp = c.getApplicationContext().getSharedPreferences("reader_prefs", Context.MODE_PRIVATE);
    }

    public static synchronized Prefs get(Context c) {
        if (inst == null) {
            inst = new Prefs(c);
        }
        return inst;
    }

    // ---------- 阅读排版 ----------
    public int fontSize() {
        return sp.getInt("font_size", 19);
    }

    public void setFontSize(int v) {
        sp.edit().putInt("font_size", clamp(v, 13, 32)).apply();
    }

    public int lineSpace() {
        return sp.getInt("line_space", 1);   // 0=紧凑 1=标准 2=宽松 3=很松
    }

    public void setLineSpace(int v) {
        sp.edit().putInt("line_space", clamp(v, 0, 3)).apply();
    }

    /** 0 宋体 / 1 黑体 / 2 等宽 */
    public int fontFamily() {
        return sp.getInt("font_family", 0);
    }

    public void setFontFamily(int v) {
        sp.edit().putInt("font_family", clamp(v, 0, 2)).apply();
    }

    public int indent() {
        return sp.getInt("indent", 2);
    }

    public void setIndent(int v) {
        sp.edit().putInt("indent", clamp(v, 0, 2)).apply();
    }

    // ---------- 阅读主题 ----------
    /** 0 米黄 1 羊皮 2 护眼绿 3 淡蓝 4 纯白 5 夜间 6 纯黑 */
    public int readerTheme() {
        return sp.getInt("reader_theme", 0);
    }

    public void setReaderTheme(int v) {
        sp.edit().putInt("reader_theme", clamp(v, 0, 6)).apply();
    }

    /** 0 覆盖 1 无动画 2 淡入 */
    public int pageAnim() {
        return sp.getInt("page_anim", 0);
    }

    public void setPageAnim(int v) {
        sp.edit().putInt("page_anim", clamp(v, 0, 2)).apply();
    }

    public boolean volumeKey() {
        return sp.getBoolean("volume_key", false);
    }

    public void setVolumeKey(boolean v) {
        sp.edit().putBoolean("volume_key", v).apply();
    }

    public boolean keepScreen() {
        return sp.getBoolean("keep_screen", true);
    }

    public void setKeepScreen(boolean v) {
        sp.edit().putBoolean("keep_screen", v).apply();
    }

    public boolean showStatusBar() {
        return sp.getBoolean("show_status_bar", false);
    }

    public void setShowStatusBar(boolean v) {
        sp.edit().putBoolean("show_status_bar", v).apply();
    }

    public int brightness() {
        return sp.getInt("brightness", -1);   // -1 跟随系统
    }

    public void setBrightness(int v) {
        sp.edit().putInt("brightness", clamp(v, 1, 100)).apply();
    }

    // ---------- 书架 ----------
    /** 0 最近阅读 1 添加时间 2 书名 */
    public int shelfSort() {
        return sp.getInt("shelf_sort", 0);
    }

    public void setShelfSort(int v) {
        sp.edit().putInt("shelf_sort", clamp(v, 0, 2)).apply();
    }

    public int shelfColumns() {
        return sp.getInt("shelf_columns", 3);
    }

    public void setShelfColumns(int v) {
        sp.edit().putInt("shelf_columns", clamp(v, 2, 5)).apply();
    }

    // ---------- 音乐 ----------
    public int musicVolume() {
        return sp.getInt("music_volume", 80);
    }

    public void setMusicVolume(int v) {
        sp.edit().putInt("music_volume", clamp(v, 0, 100)).apply();
    }

    /** 0 顺序 1 单曲 2 随机 */
    public int playMode() {
        return sp.getInt("play_mode", 0);
    }

    public void setPlayMode(int v) {
        sp.edit().putInt("play_mode", clamp(v, 0, 2)).apply();
    }

    public long totalReadSeconds() {
        return sp.getLong("read_seconds", 0);
    }

    public void addReadSeconds(long s) {
        sp.edit().putLong("read_seconds", totalReadSeconds() + Math.max(0, s)).apply();
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
