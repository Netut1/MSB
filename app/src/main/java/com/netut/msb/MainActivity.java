package com.netut.msb;

import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.netut.msb.repository.DownloadController;
import com.netut.msb.repository.PlaybackController;
import com.netut.msb.repository.LocalMusicManager;
import com.netut.msb.media_player.SoundAdapter;
import com.netut.msb.package_files.SoundNode;
import com.netut.msb.package_files.SoundTreeBuilder;
import com.netut.msb.package_files.TreeCache;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.List;

public class MainActivity extends AppCompatActivity implements PlaybackController.PlaybackUi, DownloadController.Listener {

    private static final String REPO_OWNER  = "Netut1";
    private static final String REPO_NAME   = "MSB_technical";
    private static final String REPO_BRANCH = "main";

    private RecyclerView list;
    private ProgressBar loading, progress;
    private TextView trackName, syncStatus;
    private ImageButton btnPlayPause, btnStop, btnMenu;
    private EditText search;
    private LinearLayout sidePanel, speedRow;
    private TextView modeNormal, modeRepeat, modeAll, btnSync;

    private SoundAdapter adapter;
    private SoundNode root;
    private boolean panelOpen = false;

    private PlaybackController playback;
    private DownloadController download;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TextView btnAddLocal, btnAddFolder, btnClearLocal;
    private ActivityResultLauncher<String[]>   localPicker;
    private ActivityResultLauncher<Uri>        folderPicker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bindViews();
        applyInsets();

        playback = new PlaybackController(this, ui, this);
        download = new DownloadController(this, io, ui, this, REPO_OWNER, REPO_NAME, REPO_BRANCH);

        localPicker = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> { if (uris != null && !uris.isEmpty()) importLocalFiles(uris); });

        folderPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocumentTree(),
                uri -> { if (uri != null) importLocalTree(uri); });

        setupSearch();
        setupSidePanel();
        setupPlayerButtons();
        setupSpeedChips();

        download.register();
        download.resumeIfAny();

        updateModeButtons();
        loadTree();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        download.unregister();
        io.shutdownNow();
        playback.release();
    }

    private void bindViews() {
        list         = findViewById(R.id.list);
        loading      = findViewById(R.id.loading);
        progress     = findViewById(R.id.progress);
        trackName    = findViewById(R.id.track_name);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnStop      = findViewById(R.id.btn_stop);
        btnMenu      = findViewById(R.id.btn_menu);
        search       = findViewById(R.id.search);
        sidePanel    = findViewById(R.id.side_panel);
        speedRow     = findViewById(R.id.speed_row);
        modeNormal   = findViewById(R.id.mode_normal);
        modeRepeat   = findViewById(R.id.mode_repeat);
        modeAll      = findViewById(R.id.mode_all);
        btnSync      = findViewById(R.id.btn_sync);
        syncStatus   = findViewById(R.id.sync_status);

        list.setLayoutManager(new LinearLayoutManager(this));
        sidePanel.setVisibility(View.GONE);
        btnAddLocal   = findViewById(R.id.btn_add_local);
        btnAddFolder  = findViewById(R.id.btn_add_folder);
        btnClearLocal = findViewById(R.id.btn_clear_local);
    }

    private void applyInsets() {
        final View topBar      = findViewById(R.id.top_bar);
        final View content     = findViewById(R.id.content);
        final View playerPanel = findViewById(R.id.player_panel);

        final int tTop = topBar.getPaddingTop();
        final int tStart = topBar.getPaddingStart();
        final int tEnd = topBar.getPaddingEnd();

        final int pTop = playerPanel.getPaddingTop();
        final int pStart = playerPanel.getPaddingStart();
        final int pEnd = playerPanel.getPaddingEnd();
        final int pBottom = playerPanel.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            topBar.setPaddingRelative(tStart + bars.left, tTop + bars.top, tEnd + bars.right, topBar.getPaddingBottom());
            content.setPadding(bars.left, 0, bars.right, 0);
            playerPanel.setPaddingRelative(pStart + bars.left, pTop, pEnd + bars.right, pBottom + bars.bottom);

            return insets;
        });
    }

    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) adapter.setQuery(s.toString());
            }
        });
    }

    private void setupSidePanel() {
        btnMenu.setOnClickListener(v -> togglePanel());

        modeNormal.setOnClickListener(v -> selectMode(PlaybackController.Mode.NORMAL));
        modeRepeat.setOnClickListener(v -> selectMode(PlaybackController.Mode.REPEAT_ONE));
        modeAll   .setOnClickListener(v -> selectMode(PlaybackController.Mode.PLAY_ALL));

        btnSync.setOnClickListener(v -> confirmSync());
        btnAddLocal  .setOnClickListener(v -> localPicker.launch(new String[]{"audio/*"}));
        btnAddFolder .setOnClickListener(v -> folderPicker.launch(null));
        btnClearLocal.setOnClickListener(v -> confirmClearLocal());
    }

    private void selectMode(PlaybackController.Mode m) {
        playback.setMode(m);
        updateModeButtons();
    }

    private void updateModeButtons() {
        PlaybackController.Mode m = playback.getMode();
        modeNormal.setSelected(m == PlaybackController.Mode.NORMAL);
        modeRepeat.setSelected(m == PlaybackController.Mode.REPEAT_ONE);
        modeAll   .setSelected(m == PlaybackController.Mode.PLAY_ALL);
    }

    private void togglePanel() {
        float d = getResources().getDisplayMetrics().density;
        float w = 280 * d + 12 * d;

        if (panelOpen) {
            sidePanel.animate().translationX(w).setDuration(180).withEndAction(() -> sidePanel.setVisibility(View.GONE)).start();
            panelOpen = false;
        } else {
            sidePanel.setVisibility(View.VISIBLE);
            sidePanel.setTranslationX(w);
            sidePanel.animate().translationX(0).setDuration(180).start();
            panelOpen = true;
        }
    }

    private void setupPlayerButtons() {
        btnStop.setOnClickListener(v -> playback.stop());
        btnPlayPause.setOnClickListener(v -> playback.togglePlayPause());
    }

    private void setupSpeedChips() {
        speedRow.removeAllViews();

        float d = getResources().getDisplayMetrics().density;
        float[] speeds = playback.getSpeeds();
        int gap = (int) (4 * d);
        int perRow = 4;
        int rowHeight = (int) (40 * d);

        int total = speeds.length;
        int rows = (total + perRow - 1) / perRow;

        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (r > 0) rowLp.topMargin = gap;
            row.setLayoutParams(rowLp);

            int start = r * perRow;
            int end = Math.min(start + perRow, total);

            for (int i = start; i < end; i++) {
                final float s = speeds[i];
                String label = (s == Math.floor(s)
                        ? String.valueOf((int) s)
                        : String.valueOf(s)) + "×";

                TextView tv = new TextView(this);
                tv.setText(label);
                tv.setTextSize(11);
                tv.setGravity(android.view.Gravity.CENTER);
                tv.setTypeface(getResources().getFont(R.font.monocraft));
                tv.setTextColor(getResources().getColorStateList(R.color.mc_toggle_text));
                tv.setBackgroundResource(R.drawable.mc_button);
                tv.setPadding(0, 0, 0, 0);
                tv.setSingleLine(true);
                tv.setSelected(s == playback.getSpeed());

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, rowHeight, 1f);
                if (i > start) lp.leftMargin = gap;
                tv.setLayoutParams(lp);

                tv.setOnClickListener(v -> {
                    playback.setSpeed(s);
                    resetChipSelection();
                    v.setSelected(true);
                });

                row.addView(tv);
            }

            for (int i = end; i < start + perRow; i++) {
                View spacer = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, rowHeight, 1f);
                lp.leftMargin = gap;
                spacer.setLayoutParams(lp);
                row.addView(spacer);
            }

            speedRow.addView(row);
        }
    }

    private void resetChipSelection() {
        for (int r = 0; r < speedRow.getChildCount(); r++) {
            View rowView = speedRow.getChildAt(r);
            if (!(rowView instanceof LinearLayout)) continue;
            LinearLayout row = (LinearLayout) rowView;
            for (int i = 0; i < row.getChildCount(); i++) {
                row.getChildAt(i).setSelected(false);
            }
        }
    }

    @Override public void onTrackName(String name) {
        trackName.setText(name);
    }
    @Override public void onPlayIcon(boolean playing) {
        btnPlayPause.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
    }
    @Override public void onProgressVisible(boolean visible) {
        progress.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }
    @Override public void onProgress(int currentMs, int durationMs) {
        progress.setMax(durationMs);
        progress.setProgress(currentMs);
    }

    private void loadTree() {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                File downloaded = new File(getFilesDir(), "sounds");
                File[] kids = downloaded.listFiles();
                boolean useFiles = downloaded.exists() && kids != null && kids.length > 0;
                String sourceTag = useFiles ? "files" : "assets";

                SoundNode cached = TreeCache.load(this, sourceTag);
                final SoundNode built;
                if (cached != null) {
                    built = cached;
                } else if (useFiles) {
                    built = SoundTreeBuilder.buildFromDir(downloaded);
                    TreeCache.save(this, built, sourceTag);
                } else {
                    built = SoundTreeBuilder.buildFromAssets(getAssets());
                    TreeCache.save(this, built, sourceTag);
                }

                File localDir = LocalMusicManager.getLocalDir(this);
                File[] localKids = localDir.listFiles();
                if (localKids != null && localKids.length > 0) {
                    SoundNode localNode = SoundTreeBuilder.buildLocalTree(localDir);
                    built.children.add(localNode);
                }

                ui.post(() -> {
                    root = built;
                    adapter = new SoundAdapter(root,
                            node -> { node.expanded = !node.expanded; adapter.rebuild(); },
                            node -> playback.playNode(node));
                    list.setAdapter(adapter);
                    loading.setVisibility(View.GONE);
                    syncStatus.setText(useFiles
                            ? getString(R.string.sync_status_downloaded_files)
                            : getString(R.string.sync_assets));
                });
            } catch (IOException e) {
                ui.post(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(this,
                            getString(R.string.loading_error, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override public void onTreeNeedsReload() {
        loadTree();
    }
    @Override public void onStatus(String text) {
        syncStatus.setText(text);
    }
    @Override public void onBusy(boolean busy) {
        btnSync.setEnabled(!busy);
    }

    private void confirmSync() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sync_title)
                .setMessage(R.string.sync_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (!isOnline()) {
                        Toast.makeText(this, getString(R.string.network_error_no_internet),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    download.start();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void importLocalFiles(List<Uri> uris) {
        Toast.makeText(this, getString(R.string.local_importing), Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                int n = LocalMusicManager.importFiles(this, uris);
                ui.post(() -> {
                    Toast.makeText(this,
                            getString(R.string.local_added, n),
                            Toast.LENGTH_SHORT).show();
                    loadTree();
                });
            } catch (IOException e) {
                ui.post(() -> Toast.makeText(this,
                        getString(R.string.local_add_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void importLocalTree(Uri treeUri) {
        Toast.makeText(this, getString(R.string.local_importing), Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                int n = LocalMusicManager.importTree(this, treeUri);
                ui.post(() -> {
                    Toast.makeText(this,
                            getString(R.string.local_added, n),
                            Toast.LENGTH_SHORT).show();
                    loadTree();
                });
            } catch (IOException e) {
                ui.post(() -> Toast.makeText(this,
                        getString(R.string.local_add_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmClearLocal() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.local_clear_title)
                .setMessage(R.string.local_clear_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    io.execute(() -> {
                        int n = LocalMusicManager.clearAll(this);
                        ui.post(() -> {
                            Toast.makeText(this,
                                    getString(R.string.local_cleared, n),
                                    Toast.LENGTH_SHORT).show();
                            loadTree();
                        });
                    });
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        if (panelOpen) {
            sidePanel.setVisibility(View.GONE);
            sidePanel.setTranslationX(0f);
            panelOpen = false;
        }
        setupSpeedChips();
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }
}
