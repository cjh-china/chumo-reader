package com.chumoreader.music;

import android.content.Context;
import android.content.Intent;

/**
 * 与「初墨播放器」（包名 com.chumosplayer）对接。
 *
 * <p>本机装了它就不必再塞一个重复的播放器给用户：点音乐入口直接跳过去。
 * 没装则返回 false，调用方继续走自带的 MusicPlayerActivity。
 *
 * <p>注意：targetSdk 30 起，未在 AndroidManifest 的 &lt;queries&gt; 里声明的包
 * 对 getLaunchIntentForPackage 是不可见的，会把已安装的应用误判成没装。
 */
public final class ExternalPlayer {

    public static final String PKG = "com.chumosplayer";

    private ExternalPlayer() {
    }

    /** 装了就用它的启动 Intent，否则 null。 */
    public static Intent launchIntent(Context c) {
        if (c == null) {
            return null;
        }
        try {
            Intent i = c.getPackageManager().getLaunchIntentForPackage(PKG);
            if (i == null) {
                return null;
            }
            // 已经开着的时候回到前台，而不是再叠一个任务栈
            i.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean isInstalled(Context c) {
        return launchIntent(c) != null;
    }

    /**
     * 尝试把播放交给外部播放器。
     *
     * @return true 表示已成功拉起，调用方不应再打开自带播放器
     */
    public static boolean launch(Context c) {
        Intent i = launchIntent(c);
        if (i == null) {
            return false;
        }
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            // 被停用、被卸载、或没有启动权限：交回给自带播放器兜底
            return false;
        }
    }
}
