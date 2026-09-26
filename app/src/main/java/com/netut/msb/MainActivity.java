package com.netut.msb;

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

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.netut.msb.media_player.PlayerManager;
import com.netut.msb.media_player.SoundAdapter;
import com.netut.msb.package_files.SoundDownloader;
import com.netut.msb.package_files.SoundNode;
import com.netut.msb.package_files.SoundTreeBuilder;
import com.netut.msb.package_files.TreeCache;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ⚠️ Замени на свой репозиторий
    private static final String REPO_OWNER  = "YOUR_GITHUB_USERNAME";
    private static final String REPO_NAME   = "YOUR_REPO_NAME";
    private static final String REPO_BRANCH = "main";
    private static final String REPO_PATH   = "sounds";

    // Режимы воспроизведения
    private enum Mode { NORMAL, REPEAT_ONE, PLAY_ALL }
    private Mode mode = Mode.NORMAL;

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};
    private float speed = 1f;

    private RecyclerView list;
    private ProgressBar loading, progress;
    private TextView trackName, syncStatus;
    private ImageButton btnPlayPause, btnStop, btnMenu;
    private EditText search;
    private LinearLayout sidePanel, speedRow;
    private TextView modeNormal, modeRepeat, modeAll, btnSync;

    private SoundAdapter adapter;
    private PlayerManager player;

    private SoundNode root;
    private String sourceTag = "assets";

    private List<SoundNode> currentPlaylist = new ArrayList<>();
    private int currentIndex = -1;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private boolean panelOpen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        bindViews();
        applyInsets();
        setupSearch();
        setupSidePanel();
        setupPlayer();
        setupSpeedChips();

        player = new PlayerManager(this);
        player.setListener(playerListener);
        player.setSpeed(speed);
        updateModeButtons();

        loadTree();
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
    }

    // ─────────────────────────────────────────────────────────────────────
    // Insets: сверху — вырез камеры, снизу — навигация, сбоку — то же самое в ландшафте
    // ─────────────────────────────────────────────────────────────────────
    private void applyInsets() {
        final View topBar      = findViewById(R.id.top_bar);
        final View content     = findViewById(R.id.content);
        final View playerPanel = findViewById(R.id.player_panel);

        final int baseTop     = topBar.getPaddingTop();
        final int baseStart   = topBar.getPaddingStart();
        final int baseEnd     = topBar.getPaddingEnd();
        final int baseBottom  = topBar.getPaddingBottom();

        final int ppTop    = playerPanel.getPaddingTop();
        final int ppStart  = playerPanel.getPaddingStart();
        final int ppEnd    = playerPanel.getPaddingEnd();
        final int ppBottom = playerPanel.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());

            topBar.setPaddingRelative(baseStart + bars.left, baseTop + bars.top,
                    baseEnd   + bars.right, baseBottom);
            content.setPadding(bars.left, 0, bars.right, 0);
            playerPanel.setPaddingRelative(ppStart + bars.left, ppTop,
                    ppEnd + bars.right, ppBottom + bars.bottom);

            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) sidePanel.getLayoutParams();
            lp.topMargin = 0;
            sidePanel.setLayoutParams(lp);

            return insets;
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Поиск
    // ─────────────────────────────────────────────────────────────────────
    private void setupSearch() {
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (adapter != null) adapter.setQuery(s.toString());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Сайд-панель
    // ─────────────────────────────────────────────────────────────────────
    private void setupSidePanel() {
        btnMenu.setOnClickListener(v -> togglePanel());

        modeNormal.setOnClickListener(v -> { mode = Mode.NORMAL;     updateModeButtons(); applyModeToPlayer(); });
        modeRepeat.setOnClickListener(v -> { mode = Mode.REPEAT_ONE; updateModeButtons(); applyModeToPlayer(); });
        modeAll   .setOnClickListener(v -> { mode = Mode.PLAY_ALL;   updateModeButtons(); applyModeToPlayer(); });

        btnSync.setOnClickListener(v -> confirmSync());
    }

    private void togglePanel() {
        float d = getResources().getDisplayMetrics().density;
        float w = 280 * d + 12 * d;

        if (panelOpen) {
            sidePanel.animate()
                    .translationX(w)
                    .setDuration(180)
                    .withEndAction(() -> sidePanel.setVisibility(View.GONE))
                    .start();
            panelOpen = false;
        } else {
            sidePanel.setVisibility(View.VISIBLE);
            sidePanel.setTranslationX(w);
            sidePanel.animate().translationX(0).setDuration(180).start();
            panelOpen = true;
        }
    }

    private void updateModeButtons() {
        modeNormal.setSelected(mode == Mode.NORMAL);
        modeRepeat.setSelected(mode == Mode.REPEAT_ONE);
        modeAll   .setSelected(mode == Mode.PLAY_ALL);
    }

    private void applyModeToPlayer() {
        player.setLooping(mode == Mode.REPEAT_ONE);
    }

    private void setupSpeedChips() {
        speedRow.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (float s : SPEEDS) {
            TextView tv = new TextView(this);
            String label = (s == Math.floor(s) ? String.valueOf((int) s) : String.valueOf(s)) + "×";
            tv.setText(label);
            tv.setTextSize(12);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setTypeface(getResources().getFont(R.font.monocraft));
            tv.setTextColor(getResources().getColorStateList(R.color.mc_text));
            tv.setBackgroundResource(R.drawable.mc_chip);
            int padH = (int) (10 * d), padV = (int) (6 * d);
            tv.setPadding(padH, padV, padH, padV);
            tv.setSelected(s == speed);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = (int) (6 * d);
            tv.setLayoutParams(lp);

            tv.setOnClickListener(v -> {
                speed = s;
                player.setSpeed(s);
                for (int i = 0; i < speedRow.getChildCount(); i++) {
                    View c = speedRow.getChildAt(i);
                    c.setSelected(false);
                }
                v.setSelected(true);
            });
            speedRow.addView(tv);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Плеер
    // ─────────────────────────────────────────────────────────────────────
    private void setupPlayer() {
        btnStop.setOnClickListener(v -> player.stop());
        btnPlayPause.setOnClickListener(v -> {
            if (!player.hasTrack()) return;
            if (player.isPlaying()) player.pause();
            else if (player.isPaused()) player.resume();
        });
    }

    private final PlayerManager.Listener playerListener = new PlayerManager.Listener() {
        @Override public void onPlaybackStarted(String name) {
            trackName.setText(name);
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            progress.setVisibility(View.VISIBLE);
            startProgress();
        }
        @Override public void onPlaybackPaused()  { btnPlayPause.setImageResource(R.drawable.ic_play); stopProgress(); }
        @Override public void onPlaybackResumed() { btnPlayPause.setImageResource(R.drawable.ic_pause); startProgress(); }
        @Override public void onPlaybackStopped() { resetPlayerUi(); }
        @Override public void onPlaybackCompleted() {
            // Обработка в зависимости от режима
            if (mode == Mode.PLAY_ALL && !currentPlaylist.isEmpty()) {
                currentIndex = (currentIndex + 1) % currentPlaylist.size();
                playCurrent();
            } else {
                resetPlayerUi();
            }
        }
    };

    private void playCurrent() {
        if (currentPlaylist.isEmpty() || currentIndex < 0 || currentIndex >= currentPlaylist.size()) return;
        SoundNode n = currentPlaylist.get(currentIndex);
        boolean fromAssets = !n.path.startsWith("/");
        player.setLooping(mode == Mode.REPEAT_ONE);
        player.play(n.path, n.name, fromAssets);
    }

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                int dur = player.getDuration();
                int pos = player.getCurrentPosition();
                if (dur > 0) { progress.setMax(dur); progress.setProgress(pos); }
                ui.postDelayed(this, 250);
            }
        }
    };
    private void startProgress() { ui.removeCallbacks(progressTick); ui.post(progressTick); }
    private void stopProgress()  { ui.removeCallbacks(progressTick); }
    private void resetPlayerUi() {
        trackName.setText(R.string.no_track);
        btnPlayPause.setImageResource(R.drawable.ic_play);
        progress.setProgress(0);
        progress.setVisibility(View.INVISIBLE);
        stopProgress();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Загрузка дерева (кэш / assets / files)
    // ─────────────────────────────────────────────────────────────────────
    private void loadTree() {
        loading.setVisibility(View.VISIBLE);
        io.execute(() -> {
            try {
                File downloaded = new File(getFilesDir(), "sounds");
                boolean useFiles = downloaded.exists() && downloaded.listFiles() != null
                        && Objects.requireNonNull(downloaded.listFiles()).length > 0;
                sourceTag = useFiles ? "files" : "assets";

                // 1. Пробуем кэш
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

                ui.post(() -> {
                    root = built;
                    adapter = new SoundAdapter(root,
                            node -> { node.expanded = !node.expanded; adapter.rebuild(); },
                            this::onSoundClicked);
                    list.setAdapter(adapter);
                    loading.setVisibility(View.GONE);
                    syncStatus.setText(useFiles ? "Downloaded (files)" : getString(R.string.sync_assets));
                });
            } catch (IOException e) {
                ui.post(() -> {
                    loading.setVisibility(View.GONE);
                    Toast.makeText(this, getString(R.string.loading_error, e.getMessage()), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void onSoundClicked(SoundNode node) {
        if (node.parent == null) return;
        List<SoundNode> siblings = new ArrayList<>();
        for (SoundNode c : node.parent.children) if (c.isSound()) siblings.add(c);
        currentPlaylist = siblings;
        currentIndex = siblings.indexOf(node);
        if (currentIndex < 0) currentIndex = 0;
        playCurrent();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Синхронизация с GitHub
    // ─────────────────────────────────────────────────────────────────────
    private void confirmSync() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sync_title)
                .setMessage(R.string.sync_confirm)
                .setPositiveButton(android.R.string.ok, (d, w) -> startSync())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void startSync() {
        btnSync.setEnabled(false);
        syncStatus.setText("Connecting…");

        SoundDownloader.download(this, REPO_OWNER, REPO_NAME, REPO_BRANCH, REPO_PATH,
                new SoundDownloader.Callback() {
                    @Override public void onProgress(int done, int total, String currentFile) {
                        ui.post(() -> syncStatus.setText(
                                getString(R.string.sync_in_progress, done, total) + "  " + currentFile));
                    }
                    @Override public void onDone(int totalFiles) {
                        ui.post(() -> {
                            btnSync.setEnabled(true);
                            syncStatus.setText(getString(R.string.sync_done, totalFiles));
                            sourceTag = "files";
                            TreeCache.invalidate(MainActivity.this);
                            loadTree(); // перезагрузить из files
                        });
                    }
                    @Override public void onError(Exception e) {
                        ui.post(() -> {
                            btnSync.setEnabled(true);
                            syncStatus.setText(getString(R.string.sync_error, e.getMessage()));
                        });
                    }
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacks(progressTick);
        io.shutdownNow();
        if (player != null) player.release();
    }
}
