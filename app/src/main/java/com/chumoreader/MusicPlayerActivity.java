package com.chumoreader;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.chumoreader.data.Prefs;
import com.chumoreader.music.MusicLibrary;
import com.chumoreader.music.MusicService;
import com.chumoreader.music.Track;
import com.chumoreader.ui.Menus;
import com.chumoreader.ui.SheetDialog;
import com.chumoreader.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** 音乐页：本地音乐库 + 全屏播放界面。 */
public class MusicPlayerActivity extends Activity implements MusicService.Listener {

    private static final int REQ_PERM = 3001;

    private FrameLayout root;
    private LinearLayout libraryView;
    private LinearLayout playerView;
    private ListView trackList;
    private TrackAdapter adapter;
    private List<Track> tracks = new ArrayList<>();

    private ImageView playBtn;
    private TextView titleView;
    private TextView artistView;
    private TextView curTime;
    private TextView totalTime;
    private TextView modeView;
    private SeekBar seekBar;
    private boolean seeking;

    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(0xFF14161A);
        getWindow().setNavigationBarColor(0xFF14161A);
        buildUi();
        ensurePermission();
    }

    private void buildUi() {
        root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF14161A, 0xFF1E2228});
        root.setBackground(bg);

        libraryView = buildLibrary();
        playerView = buildPlayer();
        root.addView(libraryView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(playerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    // ---------------- 音乐库 ----------------

    private LinearLayout buildLibrary() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 20), Ui.dp(this, 6));
        LinearLayout row = Menus.horizontal(this);
        row.addView(Menus.icon(this, R.drawable.ic_back, 0xFFFFFFFF, 40, 22, v -> finish()));
        TextView t = Menus.text(this, "本地音乐", 20, 0xFFFFFFFF, true);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(this, 6);
        row.addView(t, tlp);
        row.addView(Menus.icon(this, R.drawable.ic_search, 0x99FFFFFF, 40, 20,
                v -> Ui.toast(this, "音乐库共 " + tracks.size() + " 首")));
        head.addView(row);
        TextView sub = Menus.text(this, "手机里的音频文件 · 阅读时可后台播放", 11.5f, 0x88FFFFFF, false);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = Ui.dp(this, 46);
        slp.topMargin = Ui.dp(this, 2);
        head.addView(sub, slp);
        box.addView(head);

        trackList = new ListView(this);
        trackList.setDivider(null);
        trackList.setDividerHeight(0);
        trackList.setVerticalScrollBarEnabled(false);
        trackList.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20));
        trackList.setClipToPadding(false);
        adapter = new TrackAdapter();
        trackList.setAdapter(adapter);
        trackList.setOnItemClickListener((parent, view, position, id) -> playAt(position));
        box.addView(trackList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return box;
    }

    private void playAt(int index) {
        if (tracks.isEmpty()) {
            return;
        }
        ArrayList<Track> list = new ArrayList<>(tracks);
        MusicService.play(this, list, index);
        ui.postDelayed(() -> {
            showPlayer();
            refresh();
        }, 220);
    }

    private void showPlayer() {
        libraryView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
    }

    private void showLibrary() {
        playerView.setVisibility(View.GONE);
        libraryView.setVisibility(View.VISIBLE);
        rescan();
    }

    // ---------------- 播放界面 ----------------

    private LinearLayout buildPlayer() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setVisibility(View.GONE);
        box.setPadding(0, 0, 0, Ui.dp(this, 16));

        LinearLayout top = Menus.horizontal(this);
        top.setPadding(Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8), 0);
        top.addView(Menus.icon(this, R.drawable.ic_chevron, 0xFFFFFFFF, 44, 22,
                v -> {
                    if (MusicService.get() == null) {
                        showLibrary();
                    } else {
                        finish();
                    }
                }));
        TextView label = Menus.text(this, "正在播放", 12, 0x99FFFFFF, false);
        label.setGravity(Gravity.CENTER);
        top.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        top.addView(Menus.icon(this, R.drawable.ic_list, 0xFFFFFFFF, 44, 22, v -> showQueue()));
        box.addView(top);

        FrameLayout coverBox = new FrameLayout(this);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        clp.setMargins(Ui.dp(this, 34), Ui.dp(this, 14), Ui.dp(this, 34), Ui.dp(this, 10));
        box.addView(coverBox, clp);

        FrameLayout cover = new FrameLayout(this);
        GradientDrawable cg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF35506B, 0xFF1D2A3A});
        cg.setCornerRadius(Ui.dpf(this, 22));
        cover.setBackground(cg);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        coverBox.addView(cover, flp);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_music);
        icon.setColorFilter(0x55FFFFFF);
        cover.addView(icon, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setPadding(0, 0, 0, 0);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(Ui.dp(this, 26), 0, Ui.dp(this, 26), 0);
        titleView = Menus.text(this, "未在播放", 19, 0xFFFFFFFF, true);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(titleView);
        artistView = Menus.text(this, "", 12.5f, 0x99FFFFFF, false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.topMargin = Ui.dp(this, 6);
        texts.addView(artistView, alp);
        box.addView(texts);

        seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setPadding(Ui.dp(this, 22), 0, Ui.dp(this, 22), 0);
        if (Build.VERSION.SDK_INT >= 21) {
            seekBar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF7A52));
            seekBar.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFF7A52));
        }
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    MusicService s = MusicService.get();
                    if (s != null && s.duration() > 0) {
                        curTime.setText(fmt((int) (progress / 1000f * s.duration())));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                seeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                seeking = false;
                MusicService s = MusicService.get();
                if (s != null) {
                    s.seekTo((int) (sb.getProgress() / 1000f * s.duration()));
                }
            }
        });
        box.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 34)));

        LinearLayout timeRow = Menus.horizontal(this);
        timeRow.setPadding(Ui.dp(this, 26), 0, Ui.dp(this, 26), Ui.dp(this, 4));
        curTime = Menus.text(this, "0:00", 10.5f, 0x88FFFFFF, false);
        totalTime = Menus.text(this, "0:00", 10.5f, 0x88FFFFFF, false);
        timeRow.addView(curTime, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        totalTime.setGravity(Gravity.END);
        timeRow.addView(totalTime);
        box.addView(timeRow);

        LinearLayout controls = Menus.horizontal(this);
        controls.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 6));

        modeView = Menus.text(this, "顺序", 11, 0xCCFFFFFF, false);
        modeView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        modeView.setLayoutParams(mlp);
        modeView.setOnClickListener(v -> {
            Prefs p = Prefs.get(this);
            p.setPlayMode((p.playMode() + 1) % 3);
            refreshMode();
        });
        controls.addView(modeView);

        controls.addView(Menus.icon(this, R.drawable.ic_prev, 0xFFFFFFFF, 58, 30,
                v -> {
                    MusicService s = MusicService.get();
                    if (s != null) {
                        s.prev();
                    }
                }), new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));

        FrameLayout playWrap = new FrameLayout(this);
        GradientDrawable pg = new GradientDrawable();
        pg.setShape(GradientDrawable.OVAL);
        pg.setColor(0xFFFF7A52);
        playWrap.setBackground(pg);
        playBtn = new ImageView(this);
        playBtn.setImageResource(R.drawable.ic_play);
        playBtn.setColorFilter(0xFFFFFFFF);
        playWrap.addView(playBtn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        playBtn.setScaleType(ImageView.ScaleType.CENTER);
        playWrap.setOnClickListener(v -> {
            MusicService s = MusicService.get();
            if (s != null) {
                s.toggle();
                refresh();
            } else if (!tracks.isEmpty()) {
                playAt(0);
            }
        });
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(Ui.dp(this, 62), Ui.dp(this, 62));
        plp.gravity = Gravity.CENTER_VERTICAL;
        controls.addView(playWrap, plp);

        controls.addView(Menus.icon(this, R.drawable.ic_next, 0xFFFFFFFF, 58, 30,
                v -> {
                    MusicService s = MusicService.get();
                    if (s != null) {
                        s.next(true);
                    }
                }), new LinearLayout.LayoutParams(0, Ui.dp(this, 58), 1f));

        TextView queue = Menus.text(this, "列表", 11, 0xCCFFFFFF, false);
        queue.setGravity(Gravity.CENTER);
        queue.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        queue.setOnClickListener(v -> showQueue());
        controls.addView(queue);

        box.addView(controls);
        refreshMode();
        return box;
    }

    private void refreshMode() {
        if (modeView == null) {
            return;
        }
        int m = Prefs.get(this).playMode();
        modeView.setText(m == 1 ? "单曲" : (m == 2 ? "随机" : "顺序"));
    }

    private void showQueue() {
        MusicService s = MusicService.get();
        final SheetDialog d = new SheetDialog(this);
        LinearLayout body = d.body();
        body.setBackgroundColor(0xFF1C1F24);
        body.addView(Menus.handle(this, 0x33FFFFFF));
        TextView t = Menus.text(this, "播放列表", 15, 0xFFFFFFFF, true);
        t.setPadding(Ui.dp(this, 20), Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 10));
        body.addView(t);

        if (s == null || s.queue().isEmpty()) {
            TextView empty = Menus.text(this, "还没有在播放的曲目", 12.5f, 0x88FFFFFF, false);
            empty.setPadding(Ui.dp(this, 20), Ui.dp(this, 10), Ui.dp(this, 20), Ui.dp(this, 26));
            body.addView(empty);
            d.show();
            return;
        }

        ListView lv = new ListView(this);
        final List<Track> q = new ArrayList<>(s.queue());
        final int cur = s.index();
        lv.setAdapter(new BaseAdapter() {
            @Override
            public int getCount() {
                return q.size();
            }

            @Override
            public Object getItem(int position) {
                return q.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                LinearLayout row = new LinearLayout(MusicPlayerActivity.this);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(Ui.dp(MusicPlayerActivity.this, 20), Ui.dp(MusicPlayerActivity.this, 11),
                        Ui.dp(MusicPlayerActivity.this, 20), Ui.dp(MusicPlayerActivity.this, 11));
                Track tr = q.get(position);
                boolean on = position == cur;
                TextView a = Menus.text(MusicPlayerActivity.this,
                        (on ? "▶ " : "") + tr.display(), 13.5f,
                        on ? 0xFFFF7A52 : 0xFFFFFFFF, on);
                row.addView(a);
                TextView b = Menus.text(MusicPlayerActivity.this, tr.artistOrUnknown(), 11, 0x77FFFFFF, false);
                LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                blp.topMargin = Ui.dp(MusicPlayerActivity.this, 3);
                row.addView(b, blp);
                return row;
            }
        });
        lv.setOnItemClickListener((parent, view, position, id) -> {
            MusicService ms = MusicService.get();
            if (ms != null) {
                ms.playAt(position);
                refresh();
            }
            d.dismiss();
        });
        body.addView(lv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 360)));
        d.show();
    }

    // ---------------- 数据 / 权限 ----------------

    private void ensurePermission() {
        if (Build.VERSION.SDK_INT < 23) {
            // Android 5.x 安装即授权，没有运行时权限这回事
            rescan();
            return;
        }
        String perm = Build.VERSION.SDK_INT >= 33
                ? Manifest.permission.READ_MEDIA_AUDIO
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            need.add(perm);
        }
        if (need.isEmpty()) {
            rescan();
            return;
        }
        requestPermissions(need.toArray(new String[0]), REQ_PERM);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            if (Build.VERSION.SDK_INT < 23) {
                rescan();
                return;
            }
            String perm = Build.VERSION.SDK_INT >= 33
                    ? Manifest.permission.READ_MEDIA_AUDIO
                    : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
                rescan();
            } else {
                Ui.toast(this, "没有读取音频的权限，看不到本地音乐");
            }
        }
    }

    private void rescan() {
        new Thread(() -> {
            final List<Track> list = MusicLibrary.scan(this, true);
            ui.post(() -> {
                tracks = list;
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }
                if (tracks.isEmpty()) {
                    Ui.toast(this, "手机里没有找到音频文件");
                }
            });
        }).start();
    }

    private class TrackAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return tracks.size();
        }

        @Override
        public Object getItem(int position) {
            return tracks.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = new LinearLayout(MusicPlayerActivity.this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(MusicPlayerActivity.this, 20), Ui.dp(MusicPlayerActivity.this, 10),
                    Ui.dp(MusicPlayerActivity.this, 20), Ui.dp(MusicPlayerActivity.this, 10));

            FrameLayout iconBox = new FrameLayout(MusicPlayerActivity.this);
            GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF35506B, 0xFF1D2A3A});
            g.setCornerRadius(Ui.dpf(MusicPlayerActivity.this, 9));
            iconBox.setBackground(g);
            ImageView iv = new ImageView(MusicPlayerActivity.this);
            iv.setImageResource(R.drawable.ic_music);
            iv.setColorFilter(0x88FFFFFF);
            iconBox.addView(iv, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            iv.setScaleType(ImageView.ScaleType.CENTER);
            row.addView(iconBox, new LinearLayout.LayoutParams(
                    Ui.dp(MusicPlayerActivity.this, 40), Ui.dp(MusicPlayerActivity.this, 40)));

            Track t = tracks.get(position);
            LinearLayout mid = new LinearLayout(MusicPlayerActivity.this);
            mid.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            mlp.leftMargin = Ui.dp(MusicPlayerActivity.this, 12);
            mid.setLayoutParams(mlp);

            MusicService s = MusicService.get();
            boolean playing = s != null && s.currentTrack() != null
                    && t.path != null && t.path.equals(s.currentTrack().path);
            TextView a = Menus.text(MusicPlayerActivity.this, t.display(), 13.5f,
                    playing ? 0xFFFF7A52 : 0xFFFFFFFF, playing);
            a.setSingleLine(true);
            a.setEllipsize(android.text.TextUtils.TruncateAt.END);
            mid.addView(a);
            TextView b = Menus.text(MusicPlayerActivity.this, t.artistOrUnknown(), 11, 0x77FFFFFF, false);
            b.setSingleLine(true);
            mid.addView(b);
            row.addView(mid);

            TextView dur = Menus.text(MusicPlayerActivity.this, t.durationText(), 10.5f, 0x66FFFFFF, false);
            row.addView(dur);
            return row;
        }
    }

    private static String fmt(int ms) {
        int s = Math.max(0, ms / 1000);
        return String.format(java.util.Locale.CHINA, "%d:%02d", s / 60, s % 60);
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        // 服务可能还没创建，bind 会静默跳过，ticker 里还会继续补绑
        MusicService.bind(this);
        MusicService s = MusicService.get();
        if (s != null) {
            if (s.currentTrack() != null) {
                showPlayer();
            } else {
                showPlayerIfNoLibraryNeeded();
            }
        }
        refresh();
        ui.post(progressTick);
    }

    private void showPlayerIfNoLibraryNeeded() {
        if (!tracks.isEmpty()) {
            libraryView.setVisibility(View.VISIBLE);
            playerView.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onPause() {
        MusicService s = MusicService.get();
        if (s != null) {
            s.removeListener(this);
        }
        ui.removeCallbacks(progressTick);
        super.onPause();
    }

    private int lastPlayIcon;

    /** 播放状态可能与 View 就绪顺序错开，交给 ticker 兜底同步。 */
    private void syncPlayIcon(MusicService s) {
        if (playBtn == null) {
            return;
        }
        int res = (s != null && s.isPlaying()) ? R.drawable.ic_pause : R.drawable.ic_play;
        if (res != lastPlayIcon) {
            lastPlayIcon = res;
            playBtn.setImageResource(res);
        }
    }

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
            MusicService.bind(MusicPlayerActivity.this);
            MusicService s = MusicService.get();
            syncPlayIcon(s);
            if (s != null && s.currentTrack() != null && !seeking) {
                int dur = s.duration();
                int pos = s.position();
                if (dur > 0) {
                    seekBar.setProgress((int) (pos * 1000L / dur));
                }
                totalTime.setText(fmt(dur));
                curTime.setText(fmt(pos));
            }
            ui.postDelayed(this, 500);
        }
    };

    @Override
    public void onMusicState() {
        refresh();
    }

    private void refresh() {
        MusicService s = MusicService.get();
        Track t = s == null ? null : s.currentTrack();
        if (t == null) {
            if (playerView.getVisibility() == View.VISIBLE) {
                titleView.setText("未在播放");
                artistView.setText("");
            }
            return;
        }
        showPlayer();
        titleView.setText(t.display());
        artistView.setText(t.artistOrUnknown());
        syncPlayIcon(s);
        refreshMode();
    }
}
