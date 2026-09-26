package com.netut.msb.repository;

import android.app.DownloadManager;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.netut.msb.R;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class SoundDownloader {

    private static final String TAG = "SoundDownloader";
    private static final String PREF  = "msb_dl";
    private static final String KEY_ID = "download_id";
    private static final String ZIP_NAME = "sounds.zip";

    public interface Callback {
        void onProgress(int percent, String state);
        void onExtracting();
        void onDone(int fileCount);
        void onError(String message);
    }

    public static long start(Context ctx, String owner, String repo, String branch) {
        cancel(ctx);

        String url = "https://github.com/" + owner + "/" + repo + "/archive/refs/heads/" + branch + ".zip";

        File zip = new File(ctx.getExternalFilesDir(null), ZIP_NAME);
        if (zip.exists()) zip.delete();

        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setTitle(ctx.getString(R.string.downloader_notification_title));
        req.setDescription(ctx.getString(R.string.downloader_notification_description));
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        req.setDestinationInExternalFilesDir(ctx, null, ZIP_NAME);
        req.setAllowedOverMetered(true);
        req.setAllowedOverRoaming(true);

        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return -1L;

        long id = dm.enqueue(req);
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putLong(KEY_ID, id).apply();
        Log.i(TAG, "enqueued id=" + id + " url=" + url);
        return id;
    }

    public static void cancel(Context ctx) {
        long id = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_ID, -1L);
        if (id != -1L) {
            DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm != null) dm.remove(id);
        }
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_ID).apply();
    }

    public static long getSavedId(Context ctx) {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getLong(KEY_ID, -1L);
    }

    public static void clearSavedId(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY_ID).apply();
    }

    public static int[] getProgress(Context ctx, long id) {
        DownloadManager dm = (DownloadManager) ctx.getSystemService(Context.DOWNLOAD_SERVICE);
        if (dm == null) return null;
        DownloadManager.Query q = new DownloadManager.Query().setFilterById(id);
        try (Cursor c = dm.query(q)) {
            if (c == null || !c.moveToFirst()) return null;
            int done   = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            int total  = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            int percent = total > 0 ? (int) (done * 100L / total) : 0;
            return new int[]{percent, status};
        } catch (Exception e) {
            return null;
        }
    }

    public static int extractSounds(Context ctx) throws IOException {
        File zip = new File(ctx.getExternalFilesDir(null), ZIP_NAME);
        if (!zip.exists()) {
            throw new IOException(ctx.getString(R.string.downloader_error_zip_not_found, zip.getAbsolutePath()));
        }

        File outRoot = new File(ctx.getFilesDir(), "sounds");
        if (outRoot.exists()) deleteRecursive(outRoot);
        outRoot.mkdirs();

        String outCanon = outRoot.getCanonicalPath();
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(
                new BufferedInputStream(new FileInputStream(zip)))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String entryName = e.getName();

                int firstSlash = entryName.indexOf('/');
                if (firstSlash < 0) continue;
                String rel = entryName.substring(firstSlash + 1);
                if (rel.isEmpty()) continue;

                if (!rel.startsWith("sounds/")) continue;
                String inner = rel.substring("sounds/".length());
                if (inner.isEmpty()) continue;

                File f = new File(outRoot, inner);
                if (!f.getCanonicalPath().startsWith(outCanon)) continue;

                if (e.isDirectory()) {
                    f.mkdirs();
                } else {
                    File p = f.getParentFile();
                    if (p != null && !p.exists()) p.mkdirs();
                    try (FileOutputStream os = new FileOutputStream(f)) {
                        byte[] buf = new byte[32 * 1024];
                        int n;
                        while ((n = zis.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    if (f.getName().toLowerCase().endsWith(".ogg")) count++;
                }
                zis.closeEntry();
            }
        }

        zip.delete();
        clearSavedId(ctx);
        Log.i(TAG, "extracted " + count + " ogg files");
        return count;
    }

    private static void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k);
        }
        f.delete();
    }
}
