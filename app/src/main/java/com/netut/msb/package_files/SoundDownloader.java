package com.netut.msb.package_files;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class SoundDownloader {
    private static final String TAG = "SoundDownloader";

    public interface Callback {
        void onProgress(int done, int total, String currentFile);
        void onDone(int totalFiles);
        void onError(Exception e);
    }

    public static void download(Context ctx, String owner, String repo, String branch, String repoPath, Callback cb) {
        new Thread(() -> {
            try {
                String treeUrl = "https://api.github.com/repos/" + owner + "/" + repo
                        + "/git/trees/" + branch + "?recursive=1";
                String treeJson = httpGet(treeUrl);
                JSONObject root = new JSONObject(treeJson);
                JSONArray tree = root.getJSONArray("tree");

                List<String[]> files = new ArrayList<>();
                for (int i = 0; i < tree.length(); i++) {
                    JSONObject e = tree.getJSONObject(i);
                    if (!"blob".equals(e.getString("type"))) continue;
                    String p = e.getString("path");
                    if (!p.startsWith(repoPath + "/")) continue;
                    if (!p.toLowerCase().endsWith(".ogg")) continue;
                    files.add(new String[]{p, e.getString("sha")});
                }

                File outRoot = new File(ctx.getFilesDir(), "sounds");
                if (!outRoot.exists()) outRoot.mkdirs();

                int done = 0;
                for (String[] f : files) {
                    String repoFile = f[0];
                    String rel = repoFile.substring(repoPath.length() + 1);
                    File out = new File(outRoot, rel);
                    File parent = out.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();

                    if (out.exists() && out.length() > 0) {
                        done++;
                        cb.onProgress(done, files.size(), rel);
                        continue;
                    }

                    String raw = "https://raw.githubusercontent.com/" + owner + "/" + repo
                            + "/" + branch + "/" + repoFile;
                    try (InputStream in = httpStream(raw);
                         FileOutputStream os = new FileOutputStream(out)) {
                        byte[] buf = new byte[16 * 1024];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    }
                    done++;
                    cb.onProgress(done, files.size(), rel);
                }

                TreeCache.invalidate(ctx);
                cb.onDone(files.size());
            } catch (Exception e) {
                Log.e(TAG, "download failed", e);
                cb.onError(e);
            }
        }, "SoundDownloader").start();
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "MSB-Android");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally { c.disconnect(); }
    }

    private static InputStream httpStream(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", "MSB-Android");
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        return c.getInputStream();
    }
}
