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

    // ---- 初墨播放器 v2.2.0 起的「文本转音频」对外契约 ----
    /** 显式朗读动作，见播放器 README「对外接口（文本转音频）」 */
    public static final String ACTION_SPEAK_TEXT = "com.chumosplayer.action.SPEAK_TEXT";
    public static final String EXTRA_TEXT = "com.chumosplayer.extra.TEXT";
    public static final String EXTRA_TITLE = "com.chumosplayer.extra.TITLE";
    public static final String EXTRA_AUTO_PLAY = "com.chumosplayer.extra.AUTO_PLAY";
    private static final String TTS_ACTIVITY = "com.chumosplayer.TextToAudioActivity";

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

    /**
     * 把一段文字交给初墨播放器合成朗读（v2.2.0+ 的对外接口）。
     *
     * <p>先按显式组件拉起，避免同 action 被别人抢走；组件名对不上时退回隐式 Intent。
     * 文本仍然只在两个 App 之间传递，落地文件由播放器写自己的私有目录，本 App 不碰存储。
     *
     * @return true 表示已经交出去，调用方不要再启动内置 TTS
     */
    public static boolean speak(Context c, String text, String title) {
        if (c == null || text == null || text.trim().isEmpty() || !isInstalled(c)) {
            return false;
        }
        if (sendTts(c, intentWithClass(), text, title)) {
            return true;
        }
        return sendTts(c, new Intent(ACTION_SPEAK_TEXT), text, title);
    }

    private static Intent intentWithClass() {
        Intent i = new Intent(ACTION_SPEAK_TEXT);
        i.setClassName(PKG, TTS_ACTIVITY);
        return i;
    }

    private static boolean sendTts(Context c, Intent i, String text, String title) {
        i.putExtra(EXTRA_TEXT, text);
        if (title != null && title.trim().length() > 0) {
            i.putExtra(EXTRA_TITLE, title.trim());
        }
        // 从阅读页点「听书」时意图已经明确，直接进入合成并播放，不再让用户点第二次
        i.putExtra(EXTRA_AUTO_PLAY, true);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            c.startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
