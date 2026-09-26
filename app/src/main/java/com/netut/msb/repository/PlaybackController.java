package com.netut.msb.repository;

import android.content.Context;
import android.os.Handler;

import com.netut.msb.R;
import com.netut.msb.media_player.PlayerManager;
import com.netut.msb.package_files.SoundNode;

import java.util.ArrayList;
import java.util.List;

public class PlaybackController {

    public enum Mode { NORMAL, REPEAT_ONE, PLAY_ALL }

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    public interface PlaybackUi {
        void onTrackName(String name);
        void onPlayIcon(boolean playing);
        void onProgressVisible(boolean visible);
        void onProgress(int currentMs, int durationMs);
    }

    private final Context ctx;
    private final Handler ui;
    private final PlaybackUi view;
    private final PlayerManager player;

    private Mode mode = Mode.NORMAL;
    private float speed = 1f;

    private List<SoundNode> playlist = new ArrayList<>();
    private int index = -1;

    public PlaybackController(Context ctx, Handler ui, PlaybackUi view) {
        this.ctx = ctx;
        this.ui = ui;
        this.view = view;
        this.player = new PlayerManager(ctx);
        this.player.setListener(playerListener);
        this.player.setSpeed(speed);
    }

    public PlayerManager getPlayer() { return player; }
    public Mode getMode() { return mode; }
    public float getSpeed() { return speed; }
    public float[] getSpeeds() { return SPEEDS; }

    public void setMode(Mode m) {
        this.mode = m;
        player.setLooping(m == Mode.REPEAT_ONE);
    }

    public void setSpeed(float s) {
        this.speed = s;
        player.setSpeed(s);
    }

    public void togglePlayPause() {
        if (!player.hasTrack()) return;
        if (player.isPlaying()) player.pause();
        else if (player.isPaused()) player.resume();
    }

    public void stop() {
        player.stop();
    }

    public void playNode(SoundNode node) {
        if (node == null || node.parent == null) return;
        List<SoundNode> siblings = new ArrayList<>();
        for (SoundNode c : node.parent.children) if (c.isSound()) siblings.add(c);
        playlist = siblings;
        index = Math.max(0, siblings.indexOf(node));
        playCurrent();
    }

    private void playCurrent() {
        if (playlist.isEmpty() || index < 0 || index >= playlist.size()) return;
        SoundNode n = playlist.get(index);
        boolean fromAssets = !n.path.startsWith("/");
        player.setLooping(mode == Mode.REPEAT_ONE);
        player.play(n.path, n.name, fromAssets);
    }

    public void release() {
        ui.removeCallbacks(progressTick);
        player.release();
    }

    private final PlayerManager.Listener playerListener = new PlayerManager.Listener() {
        @Override public void onPlaybackStarted(String name) {
            view.onTrackName(name);
            view.onPlayIcon(true);
            view.onProgressVisible(true);
            startProgress();
        }
        @Override public void onPlaybackPaused() {
            view.onPlayIcon(false);
            stopProgress();
        }
        @Override public void onPlaybackResumed() {
            view.onPlayIcon(true);
            startProgress();
        }
        @Override public void onPlaybackStopped() {
            resetUi();
        }
        @Override public void onPlaybackCompleted() {
            if (mode == Mode.PLAY_ALL && !playlist.isEmpty()) {
                index = (index + 1) % playlist.size();
                playCurrent();
            } else {
                resetUi();
            }
        }
    };

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                int dur = player.getDuration();
                int pos = player.getCurrentPosition();
                if (dur > 0) view.onProgress(pos, dur);
                ui.postDelayed(this, 250);
            }
        }
    };

    private void startProgress() {
        ui.removeCallbacks(progressTick);
        ui.post(progressTick);
    }

    private void stopProgress() {
        ui.removeCallbacks(progressTick);
    }

    private void resetUi() {
        view.onTrackName(ctx.getString(R.string.no_track));
        view.onPlayIcon(false);
        view.onProgress(0, 100);
        view.onProgressVisible(false);
        stopProgress();
    }
}
