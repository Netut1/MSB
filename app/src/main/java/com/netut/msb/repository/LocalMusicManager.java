package com.netut.msb.repository;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class LocalMusicManager {

    public static File getLocalDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "local");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static int importFiles(Context ctx, List<Uri> uris) throws IOException {
        File dir = getLocalDir(ctx);
        int count = 0;
        for (Uri uri : uris) {
            if (uri == null) continue;
            String name = getDisplayName(ctx, uri);
            if (!isAudioFile(name)) continue;
            File dest = uniqueTarget(dir, sanitize(name));
            if (copyTo(ctx, uri, dest)) count++;
        }
        return count;
    }

    public static int importTree(Context ctx, Uri treeUri) throws IOException {
        DocumentFile root = DocumentFile.fromTreeUri(ctx, treeUri);
        if (root == null) return 0;
        return copyTree(ctx, root, getLocalDir(ctx));
    }

    private static int copyTree(Context ctx, DocumentFile srcDir, File dstDir) throws IOException {
        int count = 0;
        for (DocumentFile f : srcDir.listFiles()) {
            String rawName = f.getName();
            if (rawName == null) continue;

            if (f.isDirectory()) {
                File sub = new File(dstDir, sanitize(rawName));
                if (!sub.exists() && !sub.mkdirs()) continue;
                count += copyTree(ctx, f, sub);
            } else if (f.isFile() && isAudioFile(rawName)) {
                File dest = uniqueTarget(dstDir, sanitize(rawName));
                if (copyTo(ctx, f.getUri(), dest)) count++;
            }
        }
        return count;
    }

    public static int clearAll(Context ctx) {
        File dir = getLocalDir(ctx);
        int[] c = new int[]{0};
        deleteRecursive(dir, c);
        dir.mkdirs();
        return c[0];
    }

    private static void deleteRecursive(File f, int[] counter) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursive(k, counter);
        } else {
            counter[0]++;
        }
        f.delete();
    }

    private static boolean copyTo(Context ctx, Uri uri, File dest) throws IOException {
        try (InputStream is = ctx.getContentResolver().openInputStream(uri);
             FileOutputStream os = new FileOutputStream(dest)) {
            if (is == null) return false;
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = is.read(buf)) > 0) os.write(buf, 0, n);
        }
        return true;
    }

    private static File uniqueTarget(File dir, String name) {
        File dest = new File(dir, name);
        if (!dest.exists()) return dest;

        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext  = dot > 0 ? name.substring(dot) : "";
        int i = 1;
        while (dest.exists()) {
            dest = new File(dir, base + "_" + i + ext);
            i++;
        }
        return dest;
    }

    public static boolean isAudioFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".ogg")
                || lower.endsWith(".mp3")
                || lower.endsWith(".wav")
                || lower.endsWith(".m4a")
                || lower.endsWith(".aac")
                || lower.endsWith(".opus")
                || lower.endsWith(".flac");
    }

    private static String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String getDisplayName(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String dn = c.getString(idx);
                    if (dn != null && !dn.isEmpty()) return dn;
                }
            }
        } catch (Exception ignored) { }

        String path = uri.getLastPathSegment();
        if (path != null) {
            int slash = path.lastIndexOf('/');
            if (slash >= 0) path = path.substring(slash + 1);
        }
        return path;
    }
}