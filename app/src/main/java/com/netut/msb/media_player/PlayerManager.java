package com.netut.msb.media_player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.util.Log;

import java.io.IOException;

public class PlayerManager {

    private static final String TAG = "PlayerManager";

    public interface Listener {
        void onPlaybackStarted(String name);
        void onPlaybackStopped();
        void onPlaybackCompleted();
        void onPlaybackPaused();
        void onPlaybackResumed();
    }

    private final Context ctx;
    private MediaPlayer mp;
    private AssetFileDescriptor afd;
    private String currentPath;
    private String currentName;
    private boolean paused;
    private boolean looping;
    private float pendingSpeed = 1f;
    private Listener listener;

    public PlayerManager(Context ctx) { this.ctx = ctx; }
    public void setListener(Listener l) { this.listener = l; }

    public void play(String path, String name, boolean fromAssets) {
        stopInternal();
        try {
            mp = new MediaPlayer();
            if (fromAssets) {
                afd = ctx.getAssets().openFd(path);
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } else {
                mp.setDataSource(path);
            }
            mp.prepare();
            mp.setLooping(looping);
            applySpeed();
            mp.start();

            currentPath = path;
            currentName = name;
            paused = false;

            mp.setOnCompletionListener(m -> {
                stopInternal();
                if (listener != null) listener.onPlaybackCompleted();
            });
            mp.setOnErrorListener((m, w, e) -> {
                Log.e(TAG, "MediaPlayer error " + w + "/" + e);
                stopInternal();
                if (listener != null) listener.onPlaybackStopped();
                return true;
            });

            if (listener != null) listener.onPlaybackStarted(name);
        } catch (IOException e) {
            Log.e(TAG, "play failed: " + path, e);
            stopInternal();
        }
    }

    public void pause() {
        if (mp != null && mp.isPlaying()) { mp.pause(); paused = true; if (listener != null) listener.onPlaybackPaused(); }
    }

    public void resume() {
        if (mp != null && paused) {
            mp.start();
            applySpeed();
            paused = false;
            if (listener != null) listener.onPlaybackResumed();
        }
    }

    public void stop() { stopInternal(); if (listener != null) listener.onPlaybackStopped(); }

    public void setLooping(boolean l) {
        looping = l;
        if (mp != null) { try { mp.setLooping(l); } catch (IllegalStateException ignored) {} }
    }

    public void setSpeed(float s) {
        pendingSpeed = s;
        applySpeed();
    }

    private void applySpeed() {
        if (mp == null) return;
        try {
            PlaybackParams pp = mp.getPlaybackParams();
            pp.setSpeed(pendingSpeed);
            mp.setPlaybackParams(pp);
        } catch (Exception e) {
            Log.w(TAG, "setSpeed failed", e);
        }
    }

    private void stopInternal() {
        if (mp != null) {
            try { if (mp.isPlaying()) mp.stop(); } catch (Exception ignored) {}
            mp.release();
            mp = null;
        }
        if (afd != null) { try { afd.close(); } catch (IOException ignored) {} afd = null; }
        currentPath = null; currentName = null; paused = false;
    }

    public boolean isPlaying()  { return mp != null && mp.isPlaying(); }
    public boolean isPaused()   { return paused; }
    public boolean hasTrack()   { return mp != null; }
    public int  getCurrentPosition() { try { return mp == null ? 0 : mp.getCurrentPosition(); } catch (Exception e) { return 0; } }
    public int  getDuration()        { try { return mp == null ? 0 : mp.getDuration();        } catch (Exception e) { return 0; } }
    public String getCurrentPath() { return currentPath; }
    public String getCurrentName() { return currentName; }

    public void release() { stopInternal(); }
}
