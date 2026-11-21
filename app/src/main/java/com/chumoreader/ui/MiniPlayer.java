package com.chumoreader.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chumoreader.MusicPlayerActivity;
import com.chumoreader.R;
import com.chumoreader.music.MusicService;
import com.chumoreader.music.Track;

/** 悬浮迷你播放条：封面 + 歌名 + 播放控制 + 细进度线。 */
public class MiniPlayer extends LinearLayout implements MusicService.Listener {

    private ImageView cover;
    private TextView title;
    private TextView sub;
    private ImageView playPause;
    private View progressFill;
    private View progressTrack;
    private boolean attached;

    public MiniPlayer(Context c) {
        super(c);
        build();
    }

    public MiniPlayer(Context c, AttributeSet a) {
        super(c, a);
        build();
    }

    private void build() {
        setOrientation(VERTICAL);
        int card = Ui.attrColor(getContext(), R.attr.appCard);
        int textMain = Ui.attrColor(getContext(), R.attr.appTextPrimary);
        int textSub = Ui.attrColor(getContext(), R.attr.appTextSecondary);
        int accent = Ui.attrColor(getContext(), R.attr.appAccent);
        int surface = Ui.attrColor(getContext(), R.attr.appSurface);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(card);
        bg.setCornerRadius(Ui.dpf(getContext(), 14));
        setBackground(bg);
        setElevation(Ui.dpf(getContext(), 6));

        progressTrack = new View(getContext());
        progressTrack.setBackgroundColor(Ui.alpha(accent, 0.15f));
        addView(progressTrack, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 2)));

        FrameLayout bar = new FrameLayout(getContext());
        progressFill = new View(getContext());
        progressFill.setBackgroundColor(accent);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(0, Ui.dp(getContext(), 2));
        bar.addView(progressFill, flp);
        addView(bar, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, Ui.dp(getContext(), 2)));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int ph = Ui.dp(getContext(), 10);
        row.setPadding(ph, Ui.dp(getContext(), 8), Ui.dp(getContext(), 6), Ui.dp(getContext(), 8));

        cover = new ImageView(getContext());
        cover.setImageResource(R.drawable.ic_music);
        cover.setColorFilter(0xFFFFFFFF, PorterDuff.Mode.SRC_IN);
        GradientDrawable cb = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF4A6C82, 0xFF2B4257});
        cb.setCornerRadius(Ui.dpf(getContext(), 9));
        cover.setBackground(cb);
        cover.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int cs = Ui.dp(getContext(), 38);
        cover.setPadding(cs / 4, cs / 4, cs / 4, cs / 4);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(cs, cs);
        row.addView(cover, clp);

        LinearLayout texts = new LinearLayout(getContext());
        texts.setOrientation(VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ui.dp(getContext(), 10);
        texts.setLayoutParams(tlp);

        title = Menus.text(getContext(), "未在播放", 13.5f, textMain, false);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub = Menus.text(getContext(), "去音乐库挑一首", 11f, textSub, false);
        sub.setSingleLine(true);
        texts.addView(title);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ui.dp(getContext(), 2);
        sub.setLayoutParams(slp);
        texts.addView(sub);
        row.addView(texts);

        playPause = Menus.icon(getContext(), R.drawable.ic_play, textMain, 40, 20, v -> {
            MusicService s = MusicService.get();
            if (s != null) {
                s.toggle();
            }
        });
        row.addView(playPause);

        ImageView next = Menus.icon(getContext(), R.drawable.ic_next, textMain, 38, 18, v -> {
            MusicService s = MusicService.get();
            if (s != null) {
                s.next(true);
            }
        });
        row.addView(next);

        row.setOnClickListener(v -> {
            Context c = getContext();
            Intent i = new Intent(c, MusicPlayerActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            c.startActivity(i);
        });

        addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        setVisibility(GONE);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        attached = true;
        MusicService.bind(this);
        refresh();
    }

    @Override
    protected void onDetachedFromWindow() {
        attached = false;
        MusicService s = MusicService.get();
        if (s != null) {
            s.removeListener(this);
        }
        super.onDetachedFromWindow();
    }

    @Override
    public void onMusicState() {
        refresh();
    }

    public void refresh() {
        MusicService.bind(this);
        MusicService s = MusicService.get();
        Track t = s == null ? null : s.currentTrack();
        if (t == null) {
            setVisibility(GONE);
            armTick(s);
            return;
        }
        setVisibility(VISIBLE);
        title.setText(t.display());
        sub.setText(t.artistOrUnknown());
        playPause.setImageResource(s.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play);
        int dur = s.duration();
        int pos = s.position();
        float p = dur > 0 ? Math.min(1f, pos / (float) dur) : 0f;
        View parent = (View) progressFill.getParent();
        if (parent != null && parent.getWidth() > 0) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) progressFill.getLayoutParams();
            lp.width = (int) (parent.getWidth() * p);
            progressFill.setLayoutParams(lp);
        }
        armTick(s);
    }

    /** 播放中 600ms 一次；未播放时低频重试，用来兜住服务晚于视图创建的情况。 */
    private void armTick(MusicService s) {
        removeCallbacks(tick);
        if (attached) {
            postDelayed(tick, s != null && s.isPlaying() ? 600 : 1500);
        }
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };
}
