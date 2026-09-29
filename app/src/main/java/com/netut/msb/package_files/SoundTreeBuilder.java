package com.netut.msb.package_files;

import android.content.res.AssetManager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SoundTreeBuilder {
    public static SoundNode buildFromAssets(AssetManager am) throws IOException {
        SoundNode root = new SoundNode(SoundNode.Type.FOLDER, "sounds", "sounds");
        walkAssets(am, "sounds", root);
        return root;
    }

    private static void walkAssets(AssetManager am, String path, SoundNode parent) throws IOException {
        String[] items = am.list(path);
        if (items == null || items.length == 0) return;
        List<String> dirs = new ArrayList<>(), files = new ArrayList<>();
        for (String it : items) {
            String[] sub = am.list(path + "/" + it);
            if (sub != null && sub.length > 0) dirs.add(it);
            else if (it.toLowerCase().endsWith(".ogg")) files.add(it);
        }
        dirs.sort(String.CASE_INSENSITIVE_ORDER);
        files.sort(String.CASE_INSENSITIVE_ORDER);
        for (String d : dirs) {
            SoundNode n = new SoundNode(SoundNode.Type.FOLDER, d, path + "/" + d);
            n.parent = parent;
            parent.children.add(n);
            walkAssets(am, path + "/" + d, n);
        }
        for (String f : files) {
            String nm = f.substring(0, f.length() - 4);
            SoundNode n = new SoundNode(SoundNode.Type.SOUND, nm, path + "/" + f);
            n.parent = parent;
            parent.children.add(n);
        }
    }

    public static SoundNode buildFromDir(File root) {
        SoundNode r = new SoundNode(SoundNode.Type.FOLDER, root.getName(), root.getAbsolutePath());
        walkDir(root, r);
        return r;
    }

    private static void walkDir(File dir, SoundNode parent) {
        File[] items = dir.listFiles();
        if (items == null) return;
        List<File> dirs = new ArrayList<>(), files = new ArrayList<>();
        for (File f : items) {
            if (f.isDirectory()) dirs.add(f);
            else if (f.getName().toLowerCase().endsWith(".ogg")) files.add(f);
        }
        dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File d : dirs) {
            SoundNode n = new SoundNode(SoundNode.Type.FOLDER, d.getName(), d.getAbsolutePath());
            n.parent = parent;
            parent.children.add(n);
            walkDir(d, n);
        }
        for (File f : files) {
            String nm = f.getName();
            SoundNode n = new SoundNode(SoundNode.Type.SOUND,
                    nm.substring(0, nm.length() - 4), f.getAbsolutePath());
            n.parent = parent;
            parent.children.add(n);
        }
    }

    public static SoundNode buildLocalTree(File root) {
        SoundNode r = new SoundNode(SoundNode.Type.FOLDER, "local", "local");
        walkLocalDir(root, r);
        return r;
    }

    private static void walkLocalDir(File dir, SoundNode parent) {
        File[] items = dir.listFiles();
        if (items == null) return;
        List<File> dirs = new ArrayList<>(), files = new ArrayList<>();
        for (File f : items) {
            if (f.isDirectory()) dirs.add(f);
            else if (isAudioFile(f.getName())) files.add(f);
        }
        dirs.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        files.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        for (File d : dirs) {
            SoundNode n = new SoundNode(SoundNode.Type.FOLDER, d.getName(), d.getAbsolutePath());
            n.parent = parent;
            parent.children.add(n);
            walkLocalDir(d, n);
        }
        for (File f : files) {
            String nm = f.getName();
            int dot = nm.lastIndexOf('.');
            String base = dot > 0 ? nm.substring(0, dot) : nm;
            SoundNode n = new SoundNode(SoundNode.Type.SOUND, base, f.getAbsolutePath());
            n.parent = parent;
            parent.children.add(n);
        }
    }

    public static boolean isAudioFile(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".ogg") || lower.endsWith(".mp3");
    }
}
