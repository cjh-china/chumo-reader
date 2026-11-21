package com.chumoreader.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import com.chumoreader.MainActivity;
import com.chumoreader.R;
import com.chumoreader.data.Prefs;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 后台播放服务：MediaPlayer + 前台通知 + MediaSession。
 * 阅读时切到后台也继续放，通知栏/耳机键都能控制。
 */
public class MusicService extends Service {

    public static final String ACTION_PLAY = "com.chumoreader.PLAY";
    public static final String ACTION_TOGGLE = "com.chumoreader.TOGGLE";
    public static final String ACTION_NEXT = "com.chumoreader.NEXT";
    public static final String ACTION_PREV = "com.chumoreader.PREV";
    public static final String ACTION_STOP = "com.chumoreader.STOP";

    private static final String CHANNEL = "chumo_music";
    private static final int NOTIF_ID = 1001;

    private static MusicService inst;
    private static ArrayList<Track> pendingQueue;

    public interface Listener {
        void onMusicState();
    }

    private final List<Listener> listeners = new CopyOnWriteArrayList<>();

    private MediaPlayer player;
    private MediaSession session;
    private final ArrayList<Track> queue = new ArrayList<>();
    private int index = -1;
    private boolean prepared;
    private int errorStreak;
    private String lastError;

    public static MusicService get() {
        return inst;
    }

    public static void play(Context c, ArrayList<Track> list, int idx) {
        pendingQueue = list;
        Intent i = new Intent(c, MusicService.class);
        i.setAction(ACTION_PLAY);
        i.putExtra("index", idx);
        startCompat(c, i);
    }

    public static void sendAction(Context c, String action) {
        Intent i = new Intent(c, MusicService.class);
        i.setAction(action);
        startCompat(c, i);
    }

    private static void startCompat(Context c, Intent i) {
        if (Build.VERSION.SDK_INT >= 26) {
            c.startForegroundService(i);
        } else {
            c.startService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        inst = this;
        createChannel();
        session = new MediaSession(this, "ChumoReader");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setActive(true);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onSkipToNext() {
                next(true);
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }
        });
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL, "音乐播放",
                        NotificationManager.IMPORTANCE_LOW);
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        String action = intent.getAction();
        if (ACTION_PLAY.equals(action)) {
            int idx = intent.getIntExtra("index", 0);
            if (pendingQueue != null && !pendingQueue.isEmpty()) {
                queue.clear();
                queue.addAll(pendingQueue);
                pendingQueue = null;
                index = Math.max(0, Math.min(idx, queue.size() - 1));
                open(true);
            } else if (!queue.isEmpty()) {
                open(true);
            }
        } else if (ACTION_TOGGLE.equals(action)) {
            toggle();
        } else if (ACTION_NEXT.equals(action)) {
            next(true);
        } else if (ACTION_PREV.equals(action)) {
            prev();
        } else if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        // 有内容就进入前台；没有内容就立刻结束，避免前台服务空转被系统判 ANR
        if (current() != null) {
            startForegroundCompat();
            notifyState();
        } else {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void startForegroundCompat() {
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private Notification buildNotification() {
        Track t = current();
        String title = t == null ? "初墨阅读" : t.display();
        String sub = t == null ? "未在播放" : t.artistOrUnknown();

        Intent open = new Intent(this, MainActivity.class);
        open.putExtra("open_music", true);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            b = new Notification.Builder(this, CHANNEL);
        } else {
            b = new Notification.Builder(this);
        }
        b.setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(sub)
                .setContentIntent(pi)
                .setOngoing(isPlaying())
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .addAction(action(android.R.drawable.ic_media_previous, "上一首", ACTION_PREV))
                .addAction(isPlaying()
                        ? action(android.R.drawable.ic_media_pause, "暂停", ACTION_TOGGLE)
                        : action(android.R.drawable.ic_media_play, "播放", ACTION_TOGGLE))
                .addAction(action(android.R.drawable.ic_media_next, "下一首", ACTION_NEXT));

        if (Build.VERSION.SDK_INT >= 21) {
            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setShowActionsInCompactView(0, 1, 2);
            if (session != null) {
                style.setMediaSession(session.getSessionToken());
            }
            b.setStyle(style);
            b.setVisibility(Notification.VISIBILITY_PUBLIC);
        }
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    @SuppressWarnings("deprecation")
    private Notification.Action action(int icon, String title, String action) {
        Intent i = new Intent(this, MusicService.class);
        i.setAction(action);
        PendingIntent pi = PendingIntent.getService(this, action.hashCode(), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (Build.VERSION.SDK_INT >= 23) {
            return new Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, icon), title, pi).build();
        }
        return new Notification.Action(icon, title, pi);
    }

    private void refreshNotification() {
        if (current() == null) {
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification());
    }

    // ---------------- 播放控制 ----------------

    private Track current() {
        if (index < 0 || index >= queue.size()) {
            return null;
        }
        return queue.get(index);
    }

    public ArrayList<Track> queue() {
        return queue;
    }

    public int index() {
        return index;
    }

    public Track currentTrack() {
        return current();
    }

    public boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    public int position() {
        try {
            return prepared && player != null ? player.getCurrentPosition() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    public int duration() {
        try {
            return prepared && player != null ? player.getDuration() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void open(boolean autoPlay) {
        Track t = current();
        if (t == null) {
            return;
        }
        releasePlayer();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(t.path);
            player.setOnPreparedListener(mp -> {
                prepared = true;
                if (autoPlay) {
                    mp.start();
                }
                if (session != null) {
                    session.setPlaybackState(state());
                }
                notifyState();
                refreshNotification();
            });
            player.setOnCompletionListener(mp -> {
                if (Prefs.get(MusicService.this).playMode() == 1) {
                    seekTo(0);
                    if (player != null) {
                        player.start();
                    }
                } else {
                    next(false);
                }
            });
            player.setOnErrorListener((mp, what, extra) -> {
                prepared = false;
                errorStreak++;
                lastError = "播放失败：" + t.display();
                if (errorStreak < 3) {
                    next(false);
                } else {
                    notifyState();
                }
                return true;
            });
            player.prepareAsync();
            if (session != null) {
                session.setMetadata(new android.media.MediaMetadata.Builder()
                        .putString(android.media.MediaMetadata.METADATA_KEY_TITLE, t.display())
                        .putString(android.media.MediaMetadata.METADATA_KEY_ARTIST, t.artistOrUnknown())
                        .putLong(android.media.MediaMetadata.METADATA_KEY_DURATION, t.duration)
                        .build());
            }
        } catch (Exception e) {
            prepared = false;
            lastError = e.getMessage();
            errorStreak++;
            if (errorStreak < 3) {
                next(false);
            }
        }
    }

    public void toggle() {
        if (isPlaying()) {
            pause();
        } else {
            resume();
        }
    }

    public void pause() {
        try {
            if (player != null && prepared && player.isPlaying()) {
                player.pause();
            }
        } catch (Exception ignored) {
        }
        if (session != null) {
            session.setPlaybackState(state());
        }
        refreshNotification();
        notifyState();
    }

    public void resume() {
        try {
            if (player != null && prepared) {
                if (!player.isPlaying()) {
                    player.start();
                }
            } else if (current() != null) {
                open(true);
                return;
            }
        } catch (Exception ignored) {
        }
        if (session != null) {
            session.setPlaybackState(state());
        }
        refreshNotification();
        notifyState();
    }

    public void next(boolean manual) {
        if (queue.isEmpty()) {
            return;
        }
        if (manual) {
            errorStreak = 0;
        }
        int mode = Prefs.get(this).playMode();
        if (mode == 2 && queue.size() > 1) {
            int n;
            do {
                n = (int) (Math.random() * queue.size());
            } while (n == index);
            index = n;
        } else {
            index = (index + 1) % queue.size();
        }
        open(true);
        notifyState();
    }

    public void prev() {
        if (queue.isEmpty()) {
            return;
        }
        if (position() > 3000) {
            seekTo(0);
            return;
        }
        index = (index - 1 + queue.size()) % queue.size();
        open(true);
        notifyState();
    }

    public void playAt(int i) {
        if (i < 0 || i >= queue.size()) {
            return;
        }
        index = i;
        open(true);
        notifyState();
    }

    public void seekTo(int ms) {
        try {
            if (player != null && prepared) {
                player.seekTo(Math.max(0, Math.min(ms, duration())));
            }
        } catch (Exception ignored) {
        }
        notifyState();
    }

    public void setVolume(float v) {
        try {
            if (player != null) {
                player.setVolume(v, v);
            }
        } catch (Exception ignored) {
        }
    }

    private PlaybackState state() {
        if (session == null) {
            return null;
        }
        PlaybackState.Builder b = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO);
        int st = isPlaying() ? PlaybackState.STATE_PLAYING
                : (current() == null ? PlaybackState.STATE_NONE : PlaybackState.STATE_PAUSED);
        b.setState(st, position(), 1f);
        return b.build();
    }

    private void releasePlayer() {
        try {
            if (player != null) {
                player.reset();
                player.release();
            }
        } catch (Exception ignored) {
        }
        player = null;
        prepared = false;
    }

    // ---------------- 状态分发 ----------------

    /**
     * 服务是 startService 异步拉起的，Activity/View 就绪时它可能还没创建。
     * 用这个入口就无需判空，绑不上也无害，下次刷新时再试即可。
     */
    public static void bind(Listener l) {
        MusicService s = get();
        if (s != null) {
            s.addListener(l);
        }
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) {
            listeners.add(l);
        }
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public String lastError() {
        return lastError;
    }

    private void notifyState() {
        for (Listener l : listeners) {
            try {
                l.onMusicState();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        if (session != null) {
            session.release();
            session = null;
        }
        inst = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
