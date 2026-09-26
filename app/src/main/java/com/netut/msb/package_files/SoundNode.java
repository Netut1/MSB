package com.netut.msb.package_files;

import java.util.ArrayList;
import java.util.List;

public class SoundNode {
    public enum Type { FOLDER, SOUND }

    public final Type type;
    public final String name;
    public final String path;
    public final List<SoundNode> children = new ArrayList<>();
    public SoundNode parent;
    public boolean expanded = false;
    public int depth = 0;

    public SoundNode(Type type, String name, String path) {
        this.type = type; this.name = name; this.path = path;
    }
    public boolean isFolder() { return type == Type.FOLDER; }
    public boolean isSound()  { return type == Type.SOUND; }
}