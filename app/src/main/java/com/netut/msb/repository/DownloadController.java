package com.netut.msb.repository;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

import com.netut.msb.package_files.TreeCache;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class DownloadController {

    public interface Listener {
        void onTreeNeedsReload();
        void onStatus(String text);
        void onBusy(boolean busy);
    }

    private final Context ctx;
    private final ExecutorService io;
    private final Handler ui;
    private final Listener listener;

    private final String owner, repo, branch;

    private BroadcastReceiver dlReceiver;
    private boolean extractInProgress = false;
    private boolean busy = false;

    public DownloadController(Context ctx, ExecutorService io, Handler ui, Listener listener, String owner, String repo, String branch) {
        this.ctx = ctx;
        this.io = io;
        this.ui = ui;
        this.listener = listener;
        this.owner = owner;
        this.repo = repo;
        this.branch = branch;
    }

    public void register() {
        dlReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                long id = i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                long saved = SoundDownloader.getSavedId(ctx);
                if (id != saved) return;

                int[] p = SoundDownloader.getProgress(ctx, id);
                if (p == null) return;

                if (p[1] == DownloadManager.STATUS_SUCCESSFUL) {
                    extractAndReload();
                } else {
                    setBusy(false);
                    notifyStatus("Download failed");
                    SoundDownloader.clearSavedId(ctx);
                }
            }
        };

        IntentFilter f = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.registerReceiver(dlReceiver, f, Context.RECEIVER_EXPORTED);
        } else {
            ctx.registerReceiver(dlReceiver, f, Context.RECEIVER_NOT_EXPORTED);
        }
    }

    public void unregister() {
        ui.removeCallbacks(dlPollTick);
        if (dlReceiver != null) {
            try { ctx.unregisterReceiver(dlReceiver); } catch (IllegalArgumentException ignored) {}
            dlReceiver = null;
        }
    }

    public boolean isBusy() { return busy; }

    public void resumeIfAny() {
        long id = SoundDownloader.getSavedId(ctx);
        if (id == -1L) return;

        int[] p = SoundDownloader.getProgress(ctx, id);
        if (p == null) { SoundDownloader.clearSavedId(ctx); return; }

        switch (p[1]) {
            case DownloadManager.STATUS_RUNNING:
            case DownloadManager.STATUS_PENDING:
            case DownloadManager.STATUS_PAUSED:
                setBusy(true);
                notifyStatus("Resuming download…");
                ui.post(dlPollTick);
                break;
            case DownloadManager.STATUS_SUCCESSFUL:
                extractAndReload();
                break;
            case DownloadManager.STATUS_FAILED:
                SoundDownloader.clearSavedId(ctx);
                break;
        }
    }

    public void start() {
        if (busy) return;
        setBusy(true);
        notifyStatus("Connecting…");

        long id = SoundDownloader.start(ctx, owner, repo, branch);
        if (id == -1L) {
            setBusy(false);
            notifyStatus("Cannot start download");
            return;
        }
        ui.post(dlPollTick);
    }

    private void setBusy(boolean b) {
        busy = b;
        if (listener != null) listener.onBusy(b);
    }

    private void notifyStatus(String s) {
        if (listener != null) listener.onStatus(s);
    }

    private final Runnable dlPollTick = new Runnable() {
        @Override public void run() {
            long id = SoundDownloader.getSavedId(ctx);
            if (id == -1L) return;

            int[] p = SoundDownloader.getProgress(ctx, id);
            if (p == null) return;

            int percent = p[0], status = p[1];
            switch (status) {
                case DownloadManager.STATUS_RUNNING:
                case DownloadManager.STATUS_PENDING:
                    notifyStatus("Downloading… " + percent + "%");
                    ui.postDelayed(this, 700);
                    break;
                case DownloadManager.STATUS_PAUSED:
                    notifyStatus("Paused… " + percent + "%");
                    ui.postDelayed(this, 1500);
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    notifyStatus("Downloaded 100%");
                    break;
                case DownloadManager.STATUS_FAILED:
                    notifyStatus("Download failed");
                    setBusy(false);
                    SoundDownloader.clearSavedId(ctx);
                    break;
            }
        }
    };

    private void extractAndReload() {
        if (extractInProgress) return;
        extractInProgress = true;
        setBusy(true);
        notifyStatus("Extracting…");

        io.execute(() -> {
            try {
                int n = SoundDownloader.extractSounds(ctx);
                TreeCache.invalidate(ctx);
                ui.post(() -> {
                    extractInProgress = false;
                    setBusy(false);
                    notifyStatus("Downloaded " + n + " files");
                    if (listener != null) listener.onTreeNeedsReload();
                });
            } catch (IOException ex) {
                ui.post(() -> {
                    extractInProgress = false;
                    setBusy(false);
                    notifyStatus("Extract failed: " + ex.getMessage());
                });
            }
        });
    }
}
