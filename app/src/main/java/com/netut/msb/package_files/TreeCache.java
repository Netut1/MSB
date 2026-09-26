package com.netut.msb.package_files;

import android.content.Context;
import android.util.Log;

import com.netut.msb.R;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class TreeCache {

    private static final String TAG = "TreeCache";
    private static final String FILE_NAME = "tree_cache.json";
    private static final int VERSION = 2;

    public static void save(Context ctx, SoundNode root, String sourceTag) {
        try {
            JSONObject o = new JSONObject();
            o.put("version", VERSION);
            o.put("source", sourceTag);
            o.put("root", toJson(root));
            try (FileWriter w = new FileWriter(new File(ctx.getFilesDir(), FILE_NAME))) {
                w.write(o.toString());
            }
        } catch (Exception e) {
            Log.e(TAG, ctx.getString(R.string.cache_error_save), e);
        }
    }

    public static SoundNode load(Context ctx, String sourceTag) {
        try {
            File f = new File(ctx.getFilesDir(), FILE_NAME);
            if (!f.exists()) return null;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject o = new JSONObject(sb.toString());
            if (o.optInt("version") != VERSION) return null;
            if (!sourceTag.equals(o.optString("source"))) return null;
            return fromJson(o.getJSONObject("root"), null);
        } catch (Exception e) {
            Log.e(TAG, ctx.getString(R.string.cache_error_load), e);
            return null;
        }
    }

    public static void invalidate(Context ctx) {
        new File(ctx.getFilesDir(), FILE_NAME).delete();
    }

    private static JSONObject toJson(SoundNode n) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("t", n.type.name());
        o.put("n", n.name);
        o.put("p", n.path);
        if (n.isFolder() && !n.children.isEmpty()) {
            JSONArray a = new JSONArray();
            for (SoundNode c : n.children) a.put(toJson(c));
            o.put("c", a);
        }
        return o;
    }

    private static SoundNode fromJson(JSONObject o, SoundNode parent) throws JSONException {
        SoundNode n = new SoundNode(SoundNode.Type.valueOf(o.getString("t")),
                o.getString("n"), o.getString("p"));
        n.parent = parent;
        if (o.has("c")) {
            JSONArray a = o.getJSONArray("c");
            for (int i = 0; i < a.length(); i++) n.children.add(fromJson(a.getJSONObject(i), n));
        }
        return n;
    }
}
