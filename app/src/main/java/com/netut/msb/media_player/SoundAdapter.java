package com.netut.msb.media_player;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.netut.msb.R;
import com.netut.msb.package_files.SoundNode;

import java.util.ArrayList;
import java.util.List;

public class SoundAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnFolderClick { void onFolderClick(SoundNode node); }
    public interface OnSoundClick  { void onSoundClick(SoundNode node); }

    private static final int T_FOLDER = 0, T_SOUND = 1;

    private final List<SoundNode> flat = new ArrayList<>();
    private final SoundNode root;
    private final OnFolderClick folderClick;
    private final OnSoundClick  soundClick;
    private String query = "";

    public SoundAdapter(SoundNode root, OnFolderClick f, OnSoundClick s) {
        this.root = root; this.folderClick = f; this.soundClick = s;
        rebuild();
    }

    public void setQuery(String q) { this.query = q == null ? "" : q.trim(); rebuild(); }

    public void rebuild() {
        flat.clear();
        if (query.isEmpty()) {
            flatten(root, 0);
        } else {
            String lq = query.toLowerCase();
            for (SoundNode c : root.children) {
                if (matches(c, lq)) flattenFiltered(c, 0, lq);
            }
        }
        notifyDataSetChanged();
    }

    private void flatten(SoundNode node, int depth) {
        for (SoundNode c : node.children) {
            c.depth = depth;
            flat.add(c);
            if (c.isFolder() && c.expanded) flatten(c, depth + 1);
        }
    }

    private boolean matches(SoundNode n, String q) {
        if (n.name.toLowerCase().contains(q)) return true;
        if (n.isFolder()) for (SoundNode c : n.children) if (matches(c, q)) return true;
        return false;
    }

    private void flattenFiltered(SoundNode n, int depth, String q) {
        n.depth = depth;
        flat.add(n);
        if (n.isFolder()) for (SoundNode c : n.children) if (matches(c, q)) flattenFiltered(c, depth + 1, q);
    }

    @Override public int getItemViewType(int p) { return flat.get(p).isFolder() ? T_FOLDER : T_SOUND; }
    @Override public int getItemCount() { return flat.size(); }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(parent.getContext());
        if (viewType == T_FOLDER) return new FolderVH(inf.inflate(R.layout.item_folder, parent, false));
        return new SoundVH(inf.inflate(R.layout.item_sound, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
        SoundNode n = flat.get(position);
        if (h instanceof FolderVH) {
            FolderVH vh = (FolderVH) h;
            vh.arrow.setText(n.expanded ? "▼" : "▶");
            vh.name.setText(n.name);
            applyIndent(vh.itemView, n.depth);
            vh.itemView.setOnClickListener(v -> folderClick.onFolderClick(n));
        } else {
            SoundVH vh = (SoundVH) h;
            vh.name.setText(n.name);
            applyIndent(vh.itemView, n.depth);
            vh.itemView.setOnClickListener(v -> soundClick.onSoundClick(n));
        }
    }

    private void applyIndent(View v, int depth) {
        float d = v.getResources().getDisplayMetrics().density;
        int start = (int) (12 * d) + depth * (int) (18 * d);
        v.setPaddingRelative(start, (int)(12*d), (int)(12*d), (int)(12*d));
    }

    static class FolderVH extends RecyclerView.ViewHolder {
        final TextView arrow, name;
        FolderVH(View v) { super(v); arrow = v.findViewById(R.id.arrow); name = v.findViewById(R.id.name); }
    }
    static class SoundVH extends RecyclerView.ViewHolder {
        final TextView name;
        SoundVH(View v) { super(v); name = v.findViewById(R.id.name); }
    }
}