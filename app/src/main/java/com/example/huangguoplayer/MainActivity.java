package com.example.huangguoplayer;

import android.app.PictureInPictureParams;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.Settings;
import android.text.InputType;
import android.util.LruCache;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
// 短剧播放器业务类
public class MainActivity extends AppCompatActivity {

    private static final String SITE_DIRECTORY = "https://huangguoai.ai";
    // The main domain remains the most reliable search endpoint.  The rotating content
    // mirrors below can serve detail and category pages, but some of them rewrite an
    // unsupported search URL to their home page with HTTP 200.
    private static final String SEARCH_SITE = "https://huangguoai.com";
    private static final String[] CONTENT_SITE_FALLBACKS = {
            "https://d2i5ti.yhanwnftm.cc",
            "https://iov5c.yhanwnftm.cc",
            "https://h3i46.yhanwnftm.cc"
    };
    private static final String UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    // 站点前端 crypto-worker.js 使用的 16-byte UTF-8 key/iv。
    private static final String IMG_KEY = "f5d965df75336270";
    private static final String IMG_IV = "97b60394abc2fbe1";

    private static final String PREFS = "player_prefs";
    private static final String KEY_FAVORITES = "favorites";
    private static final String KEY_RECENT = "recent";
    private static final String KEY_PROGRESS = "progress";
    private static final String KEY_SEARCH_HISTORY = "search_history";
    private static final String KEY_SPEED = "playback_speed";
    private static final String KEY_UPDATE_DOWNLOAD_ID = "update_download_id";
    private static final String KEY_UPDATE_FILE = "update_file";
    private static final String KEY_UPDATE_SHA256 = "update_sha256";
    private static final String KEY_CONTENT_SITE = "content_site";
    private static final int FULLSCREEN_CONTROLS_TIMEOUT_MS = 3000;
    private static final int NORMAL_CONTROLS_TIMEOUT_MS = 5000;
    private static final int COLOR_ACCENT = Color.rgb(217, 154, 69);
    private static final int COLOR_SURFACE = Color.rgb(21, 26, 36);
    private static final int COLOR_SURFACE_ELEVATED = Color.rgb(26, 32, 44);
    private static final int COLOR_TEXT_PRIMARY = Color.rgb(248, 250, 252);
    private static final int COLOR_TEXT_SECONDARY = Color.rgb(185, 195, 210);

    private static final Category[] CATEGORIES = new Category[]{
            new Category("首页", "home"),
            new Category("AI成人短剧", "ai-duanju"),
            new Category("AI成人漫剧", "ai-manju"),
            new Category("AI换脸", "ai-huanlian"),
            new Category("AI魔改", "ai-mogai"),
            new Category("排行榜", "ranks/hot")
    };

    private final ExecutorService io = Executors.newFixedThreadPool(4);
    private final Handler main = new Handler(Looper.getMainLooper());

    private LinearLayout topArea;
    private LinearLayout searchArea;
    private HorizontalScrollView categoryScroll;
    private LinearLayout categoryRow;
    private HorizontalScrollView historyScroll;
    private LinearLayout historyRow;
    private EditText searchInput;
    private Button searchButton;
    private Button clearHistoryButton;
    private Button checkUpdateButton;
    private Button tabHome;
    private Button tabSearch;
    private Button tabFav;
    private Button tabRecent;
    private TextView statusText;
    private ScrollView contentScroll;
    private GridLayout contentGrid;
    private Button loadMoreButton;
    private LinearLayout playerPanel;
    private PlayerView playerView;
    private FrameLayout videoContainer;
    private Button fullscreenCloseButton;
    private int orientationBeforeFullscreen;
    private TextView nowPlaying;
    private LinearLayout playerActions1;
    private LinearLayout playerActions2;
    private Button prevButton;
    private Button episodeButton;
    private Button nextButton;
    private Button retryButton;
    private Button speedButton;
    private Button pipButton;
    private Button fullscreenButton;
    private Button closePlayerButton;
    private View controllerPrevButton;
    private View controllerNextButton;

    private ExoPlayer player;
    private final List<Drama> displayed = new ArrayList<>();
    private final List<Drama> lastSearch = new ArrayList<>();
    private final List<Episode> currentEpisodes = new ArrayList<>();
    private Drama currentDrama;
    private int currentEpisodeIndex = -1;
    private String currentTab = "home";
    private String currentCategoryId = "home";
    private int currentPage = 1;
    private boolean fullscreen = false;
    private String currentMediaUrl = "";
    private float playbackSpeed = 1f;
    private int categoryRequestId = 0;
    private int searchRequestId = 0;
    private int episodeRequestId = 0;
    private int playbackRequestId = 0;
    private long updateDownloadId = -1L;
    private File pendingUpdateApk;
    private String pendingUpdateSha256 = "";
    private boolean updateReceiverRegistered;
    private volatile String activeContentSite = "";
    private boolean contentSiteDirectoryChecked;
    private final BroadcastReceiver updateDownloadReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                if (id == updateDownloadId) onUpdateDownloadFinished();
            }
        }
    };

    // The cache is synchronized by LruCache and capped at 24 MiB to avoid retaining every poster.
    private final LruCache<String, Bitmap> imageCache = new LruCache<String, Bitmap>(24 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private final Runnable progressSaver = new Runnable() {
        @Override
        public void run() {
            savePlaybackProgress();
            main.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        playbackSpeed = getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(KEY_SPEED, 1f);
        activeContentSite = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_CONTENT_SITE, "");
        setupPlayer();
        setupEvents();
        setupBackNavigation();
        renderCategories();
        renderSearchHistory();
        styleTabs();
        updateSpeedButton();
        loadCategory("home", true);
        registerUpdateDownloadReceiver();
        restorePendingUpdateDownload();
        checkForAppUpdate();
        main.postDelayed(progressSaver, 2000);
    }

    private void bindViews() {
        topArea = findViewById(R.id.topArea);
        searchArea = findViewById(R.id.searchArea);
        categoryScroll = findViewById(R.id.categoryScroll);
        categoryRow = findViewById(R.id.categoryRow);
        historyScroll = findViewById(R.id.historyScroll);
        historyRow = findViewById(R.id.historyRow);
        searchInput = findViewById(R.id.searchInput);
        searchButton = findViewById(R.id.searchButton);
        clearHistoryButton = findViewById(R.id.clearHistoryButton);
        checkUpdateButton = findViewById(R.id.checkUpdateButton);
        tabHome = findViewById(R.id.tabHome);
        tabSearch = findViewById(R.id.tabSearch);
        tabFav = findViewById(R.id.tabFav);
        tabRecent = findViewById(R.id.tabRecent);
        statusText = findViewById(R.id.statusText);
        contentScroll = findViewById(R.id.contentScroll);
        contentGrid = findViewById(R.id.contentGrid);
        loadMoreButton = findViewById(R.id.loadMoreButton);
        playerPanel = findViewById(R.id.playerPanel);
        playerView = findViewById(R.id.playerView);
        nowPlaying = findViewById(R.id.nowPlaying);
        playerActions1 = findViewById(R.id.playerActions1);
        playerActions2 = findViewById(R.id.playerActions2);
        prevButton = findViewById(R.id.prevButton);
        episodeButton = findViewById(R.id.episodeButton);
        nextButton = findViewById(R.id.nextButton);
        retryButton = findViewById(R.id.retryButton);
        speedButton = findViewById(R.id.speedButton);
        pipButton = findViewById(R.id.pipButton);
        fullscreenButton = findViewById(R.id.fullscreenButton);
        closePlayerButton = findViewById(R.id.closePlayerButton);
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        videoContainer = findViewById(R.id.videoContainer);
        fullscreenCloseButton = findViewById(R.id.fullscreenCloseButton);
        fullscreenCloseButton.setOnClickListener(v -> closePlayer());
        playerView.setControllerVisibilityListener((PlayerView.ControllerVisibilityListener) visibility -> {
            if (fullscreen) {
                boolean inPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode();
                fullscreenCloseButton.setVisibility(inPip ? View.GONE : visibility);
                playerActions2.setVisibility(inPip ? View.GONE : visibility);
            }
        });
        player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
        playerView.post(() -> {
            bindPlayerControllerEpisodeButtons();
            syncEpisodeNavigationButtons();
        });
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    setStatus("");
                }
                if (playbackState == Player.STATE_ENDED && currentEpisodeIndex >= 0
                        && currentEpisodeIndex < currentEpisodes.size() - 1) {
                    playEpisode(currentEpisodeIndex + 1);
                }
            }

            @Override
            public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                updateFullscreenOrientation(videoSize);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                setStatus("播放失败：" + error.getErrorCodeName() + "，可点击重试");
            }
        });
    }

    private void setupEvents() {
        searchButton.setOnClickListener(v -> search());
        clearHistoryButton.setOnClickListener(v -> clearSearchHistory());
        checkUpdateButton.setOnClickListener(v -> checkForAppUpdate(true));
        searchInput.setSingleLine(true);
        searchInput.setInputType(InputType.TYPE_CLASS_TEXT);
        searchInput.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                search();
                return true;
            }
            return false;
        });

        tabHome.setOnClickListener(v -> switchTab("home"));
        tabSearch.setOnClickListener(v -> switchTab("search"));
        tabFav.setOnClickListener(v -> switchTab("favorites"));
        tabRecent.setOnClickListener(v -> switchTab("recent"));

        loadMoreButton.setOnClickListener(v -> loadCategory(currentCategoryId, false));

        prevButton.setOnClickListener(v -> playPreviousEpisode());
        nextButton.setOnClickListener(v -> playNextEpisode());
        episodeButton.setOnClickListener(v -> showEpisodeDialog());
        retryButton.setOnClickListener(v -> retryCurrent());
        speedButton.setOnClickListener(v -> showSpeedDialog());
        pipButton.setOnClickListener(v -> enterPip());
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
        closePlayerButton.setOnClickListener(v -> closePlayer());
    }

    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (fullscreen) {
                    exitFullscreen();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
    }

    private void bindPlayerControllerEpisodeButtons() {
        if (controllerPrevButton == null) {
            controllerPrevButton = playerView.findViewById(androidx.media3.ui.R.id.exo_prev);
            if (controllerPrevButton != null) {
                controllerPrevButton.setOnClickListener(v -> playPreviousEpisode());
            }
        }

        if (controllerNextButton == null) {
            controllerNextButton = playerView.findViewById(androidx.media3.ui.R.id.exo_next);
            if (controllerNextButton != null) {
                controllerNextButton.setOnClickListener(v -> playNextEpisode());
            }
        }
    }

    private void playPreviousEpisode() {
        if (currentEpisodeIndex > 0) {
            playEpisode(currentEpisodeIndex - 1);
        }
    }

    private void playNextEpisode() {
        if (currentEpisodeIndex >= 0 && currentEpisodeIndex < currentEpisodes.size() - 1) {
            playEpisode(currentEpisodeIndex + 1);
        }
    }

    private void syncEpisodeNavigationButtons() {
        boolean canPrev = currentEpisodeIndex > 0;
        boolean canNext = currentEpisodeIndex >= 0
                && currentEpisodeIndex < currentEpisodes.size() - 1;

        if (prevButton != null) prevButton.setEnabled(canPrev);
        if (nextButton != null) nextButton.setEnabled(canNext);

        syncControllerEpisodeButton(controllerPrevButton, canPrev);
        syncControllerEpisodeButton(controllerNextButton, canNext);
    }

    private void syncControllerEpisodeButton(View button, boolean enabled) {
        if (button == null) return;
        button.setEnabled(enabled);
        button.setClickable(enabled);
        button.setAlpha(enabled ? 1.0f : 0.35f);
    }

    private void switchTab(String tab) {
        currentTab = tab;
        boolean isHome = "home".equals(tab);
        boolean isSearch = "search".equals(tab);
        categoryScroll.setVisibility(isHome ? View.VISIBLE : View.GONE);
        searchArea.setVisibility(isSearch ? View.VISIBLE : View.GONE);
        styleTabs();

        if (isHome) {
            if (displayed.isEmpty()) loadCategory(currentCategoryId, true);
            else renderDramas(displayed);
        } else if ("favorites".equals(tab)) {
            loadMoreButton.setVisibility(View.GONE);
            renderDramas(loadFavorites());
        } else if ("recent".equals(tab)) {
            loadMoreButton.setVisibility(View.GONE);
            renderDramas(loadRecent());
        } else {
            loadMoreButton.setVisibility(View.GONE);
            renderDramas(lastSearch);
        }
    }

    private void styleTabs() {
        styleTab(tabHome, "home".equals(currentTab));
        styleTab(tabSearch, "search".equals(currentTab));
        styleTab(tabFav, "favorites".equals(currentTab));
        styleTab(tabRecent, "recent".equals(currentTab));
    }

    private void styleTab(Button button, boolean selected) {
        button.setBackground(rounded(selected ? COLOR_ACCENT : Color.TRANSPARENT, 12));
        button.setTextColor(selected ? Color.WHITE : COLOR_TEXT_SECONDARY);
        button.setTextSize(15);
        button.setElevation(selected ? dp(2) : 0);
    }

    private void renderCategories() {
        categoryRow.removeAllViews();
        for (Category category : CATEGORIES) {
            Button b = new Button(this);
            b.setText(category.name);
            b.setAllCaps(false);
            b.setTextSize(12);
            boolean selected = category.id.equals(currentCategoryId);
            b.setTextColor(selected ? Color.WHITE : COLOR_TEXT_SECONDARY);
            b.setBackground(rounded(selected ? COLOR_ACCENT : COLOR_SURFACE_ELEVATED, 18));
            b.setPadding(dp(14), 0, dp(14), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(40));
            lp.setMargins(0, 0, dp(8), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                currentTab = "home";
                currentCategoryId = category.id;
                styleTabs();
                renderCategories();
                loadCategory(category.id, true);
            });
            categoryRow.addView(b);
        }
    }

    private void loadCategory(String id, boolean reset) {
        final int requestId = ++categoryRequestId;
        currentTab = "home";
        currentCategoryId = id;
        styleTabs();
        renderCategories();

        if (reset) {
            currentPage = 1;
            displayed.clear();
            contentGrid.removeAllViews();
        } else {
            currentPage++;
        }

        final int page = currentPage;
        final String category = id;
        setStatus(page == 1 ? "正在加载..." : "正在加载第 " + page + " 页...");
        loadMoreButton.setEnabled(false);

        io.execute(() -> {
            try {
                String path;
                if ("home".equals(category)) {
                    path = "/";
                } else {
                    path = "/" + category + "/" + (page > 1 ? page + "/" : "");
                }
                String html = getContentPage(path);
                List<Drama> results;
                if (category.contains("rank")) results = parseRanks(html);
                else results = parseGridCards(html, "home".equals(category));

                main.post(() -> {
                    if (requestId != categoryRequestId) return;
                    if (reset) displayed.clear();
                    appendUnique(displayed, results);
                    setStatus(results.isEmpty() && page == 1 ? "没有找到内容" : "已加载 " + displayed.size() + " 项");
                    renderDramas(displayed);
                    // Ranking pages are a single list; the site has no /ranks/hot/2/ route.
                    boolean canPage = !"home".equals(category)
                            && !category.contains("rank")
                            && !results.isEmpty();
                    loadMoreButton.setVisibility(canPage ? View.VISIBLE : View.GONE);
                    loadMoreButton.setEnabled(true);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (requestId != categoryRequestId) return;
                    if (!reset && currentPage > 1) currentPage--;
                    setStatus("加载失败：" + safeMessage(e));
                    loadMoreButton.setEnabled(true);
                });
            }
        });
    }

    private void search() {
        final String keyword = searchInput.getText().toString().trim();
        if (keyword.isEmpty()) return;
        final int requestId = ++searchRequestId;
        currentTab = "search";
        styleTabs();
        saveSearchHistory(keyword);
        renderSearchHistory();
        setStatus("正在搜索...");
        contentGrid.removeAllViews();
        loadMoreButton.setVisibility(View.GONE);

        io.execute(() -> {
            try {
                List<Drama> finalResults = getSearchResults(keyword);
                main.post(() -> {
                    if (requestId != searchRequestId) return;
                    lastSearch.clear();
                    lastSearch.addAll(finalResults);
                    setStatus("找到 " + finalResults.size() + " 个结果");
                    renderDramas(finalResults);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (requestId == searchRequestId) setStatus("搜索失败：" + safeMessage(e));
                });
            }
        });
    }

    private void renderSearchHistory() {
        historyRow.removeAllViews();
        List<String> items = loadSearchHistory();
        historyScroll.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        clearHistoryButton.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        for (String keyword : items) {
            Button b = new Button(this);
            b.setText(keyword);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setTextColor(COLOR_TEXT_SECONDARY);
            b.setBackground(rounded(COLOR_SURFACE_ELEVATED, 18));
            b.setPadding(dp(12), 0, dp(12), 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
            lp.setMargins(0, 0, dp(7), 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                searchInput.setText(keyword);
                search();
            });
            historyRow.addView(b);
        }
    }

    private void saveSearchHistory(String keyword) {
        List<String> list = loadSearchHistory();
        list.remove(keyword);
        list.add(0, keyword);
        while (list.size() > 10) list.remove(list.size() - 1);
        JSONArray arr = new JSONArray();
        for (String s : list) arr.put(s);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply();
    }

    private List<String> loadSearchHistory() {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(getSharedPreferences(PREFS, MODE_PRIVATE)
                    .getString(KEY_SEARCH_HISTORY, "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i));
        } catch (Exception ignored) { }
        return out;
    }

    private void clearSearchHistory() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_SEARCH_HISTORY).apply();
        renderSearchHistory();
        Toast.makeText(this, "已清空搜索历史", Toast.LENGTH_SHORT).show();
    }

    private void renderDramas(List<Drama> dramas) {
        contentGrid.removeAllViews();
        contentGrid.setColumnCount(2);
        if (dramas.isEmpty()) {
            TextView empty = textView("这里还没有内容", 15, Color.rgb(154, 164, 178));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(0, 2);
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.setMargins(dp(6), dp(24), dp(6), dp(12));
            empty.setGravity(Gravity.CENTER);
            empty.setLayoutParams(lp);
            contentGrid.addView(empty);
            return;
        }

        for (int i = 0; i < dramas.size(); i++) {
            contentGrid.addView(createDramaCard(dramas.get(i), i));
        }
    }

    private View createDramaCard(Drama drama, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(6), dp(6), dp(6), dp(10));
        card.setBackground(rounded(COLOR_SURFACE, 18));
        card.setElevation(dp(2));

        GridLayout.LayoutParams cardLp = new GridLayout.LayoutParams();
        cardLp.columnSpec = GridLayout.spec(index % 2, 1f);
        cardLp.width = 0;
        cardLp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(cardLp);

        FrameLayout posterFrame = new FrameLayout(this);
        LinearLayout.LayoutParams posterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210));
        posterFrame.setLayoutParams(posterLp);
        posterFrame.setBackground(rounded(COLOR_SURFACE_ELEVATED, 14));
        posterFrame.setClipToOutline(true);

        ImageView poster = new ImageView(this);
        poster.setContentDescription(drama.title + "，打开详情");
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterFrame.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button fav = new Button(this);
        fav.setText(isFavorite(drama.id) ? "★" : "☆");
        fav.setContentDescription(isFavorite(drama.id) ? "取消收藏 " + drama.title : "收藏 " + drama.title);
        fav.setTextSize(18);
        fav.setTextColor(Color.WHITE);
        fav.setBackground(rounded(Color.argb(185, 15, 18, 26), 12));
        fav.setPadding(0, 0, 0, 0);
        FrameLayout.LayoutParams favLp = new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.TOP | Gravity.END);
        favLp.setMargins(0, dp(5), dp(5), 0);
        posterFrame.addView(fav, favLp);

        String cardBadge = drama.badge;
        int resumeEp = loadResumeEpisodeIndex(drama.id);
        if (resumeEp >= 0) {
            String resume = "续播 第" + (resumeEp + 1) + "集";
            cardBadge = cardBadge.isEmpty() ? resume : cardBadge + " · " + resume;
        }
        if (!cardBadge.isEmpty()) {
            TextView badge = textView(cardBadge, 11, Color.WHITE);
            badge.setBackground(rounded(Color.argb(195, 0, 0, 0), 7));
            badge.setPadding(dp(6), dp(3), dp(6), dp(3));
            FrameLayout.LayoutParams badgeLp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            badgeLp.setMargins(dp(5), 0, dp(7), dp(7));
            posterFrame.addView(badge, badgeLp);
        }

        card.addView(posterFrame);

        TextView title = textView(drama.title, 14, COLOR_TEXT_PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(2));
        title.setMaxLines(2);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!drama.poster.isEmpty()) loadImage(drama.poster, poster);

        card.setOnClickListener(v -> loadEpisodes(drama));
        card.setContentDescription(drama.title + "，点击查看剧集");
        fav.setOnClickListener(v -> {
            toggleFavorite(drama);
            boolean favorite = isFavorite(drama.id);
            fav.setText(favorite ? "★" : "☆");
            fav.setContentDescription(favorite ? "取消收藏 " + drama.title : "收藏 " + drama.title);
            if ("favorites".equals(currentTab)) renderDramas(loadFavorites());
        });

        return card;
    }

    private void loadEpisodes(Drama drama) {
        final int requestId = ++episodeRequestId;
        ++playbackRequestId;
        currentDrama = drama;
        currentEpisodes.clear();
        currentEpisodeIndex = -1;
        currentMediaUrl = "";
        setStatus("正在读取：" + drama.title);
        io.execute(() -> {
            try {
                String html = getContentPage("/detail/" + drama.id + "/");
                List<Episode> episodes = parseEpisodes(html);
                main.post(() -> {
                    if (requestId != episodeRequestId || currentDrama == null
                            || !drama.id.equals(currentDrama.id)) return;
                    currentEpisodes.clear();
                    currentEpisodes.addAll(episodes);
                    currentEpisodeIndex = loadResumeEpisodeIndex(drama.id);
                    // A card click should behave like a normal player: start a new drama at
                    // episode one, or continue the previously watched episode when available.
                    if (currentEpisodeIndex < 0 || currentEpisodeIndex >= episodes.size()) {
                        currentEpisodeIndex = 0;
                    }
                    if (episodes.isEmpty()) Toast.makeText(this, "没有解析到剧集", Toast.LENGTH_SHORT).show();
                    else playEpisode(currentEpisodeIndex);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (requestId == episodeRequestId) setStatus("读取剧集失败：" + safeMessage(e));
                });
            }
        });
    }

    private void showEpisodeDialog() {
        if (currentDrama == null || currentEpisodes.isEmpty()) return;

        ScrollView scroll = new ScrollView(this);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(4);
        grid.setPadding(dp(12), dp(12), dp(12), dp(18));
        scroll.addView(grid);

        for (int i = 0; i < currentEpisodes.size(); i++) {
            Episode ep = currentEpisodes.get(i);
            Button b = new Button(this);
            b.setText(ep.name + (i == currentEpisodeIndex ? " · 续播" : ""));
            b.setAllCaps(false);
            b.setTextSize(11);
            if (i == currentEpisodeIndex) {
                b.setTextColor(Color.WHITE);
                b.setBackground(rounded(COLOR_ACCENT, 12));
            } else {
                b.setTextColor(COLOR_TEXT_SECONDARY);
                b.setBackground(rounded(COLOR_SURFACE_ELEVATED, 12));
            }
            b.setPadding(dp(4), 0, dp(4), 0);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(i % 4, 1f);
            lp.width = 0;
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            b.setLayoutParams(lp);
            grid.addView(b);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(currentDrama.title + " · " + currentEpisodes.size() + " 集")
                .setView(scroll)
                .setNegativeButton("关闭", null)
                .create();

        for (int i = 0; i < grid.getChildCount(); i++) {
            final int index = i;
            grid.getChildAt(i).setOnClickListener(v -> {
                dialog.dismiss();
                playEpisode(index);
            });
        }
        dialog.show();
    }

    private void playEpisode(int index) {
        if (currentDrama == null || index < 0 || index >= currentEpisodes.size()) return;
        final int requestId = ++playbackRequestId;
        final String dramaId = currentDrama.id;
        savePlaybackProgress();
        currentEpisodeIndex = index;
        Episode ep = currentEpisodes.get(index);
        playerPanel.setVisibility(View.VISIBLE);
        nowPlaying.setText(currentDrama.title + " · " + ep.name);
        syncEpisodeNavigationButtons();
        setStatus("正在解析 " + ep.name + "...");
        saveRecent(currentDrama, index, ep.name);

        io.execute(() -> {
            try {
                String html = getEpisodePage(ep.url);
                String mediaUrl = extractPlayback(html, ep.ep);
                if (mediaUrl.isEmpty()) throw new IllegalStateException("未解析到播放地址");
                main.post(() -> {
                    if (requestId == playbackRequestId && currentDrama != null
                            && dramaId.equals(currentDrama.id)) {
                        startHls(mediaUrl, index);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (requestId == playbackRequestId) setStatus("解析失败：" + safeMessage(e));
                });
            }
        });
    }

    private void startHls(String mediaUrl, int episodeIndex) {
        currentMediaUrl = mediaUrl;
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", currentContentSite() + "/");
        headers.put("User-Agent", UA);

        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setUserAgent(UA)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(headers);

        DefaultDataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this, httpFactory);
        HlsMediaSource mediaSource = new HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(mediaUrl));

        player.stop();
        player.setMediaSource(mediaSource);
        player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));

        long resume = loadResumePosition(currentDrama.id, episodeIndex);
        if (resume > 0) player.seekTo(resume);

        player.prepare();
        player.play();
    }

    private void retryCurrent() {
        if (!currentMediaUrl.isEmpty() && currentEpisodeIndex >= 0) {
            ++playbackRequestId;
            setStatus("正在重试...");
            startHls(currentMediaUrl, currentEpisodeIndex);
        } else if (currentEpisodeIndex >= 0) {
            playEpisode(currentEpisodeIndex);
        }
    }

    private void closePlayer() {
        savePlaybackProgress();
        ++playbackRequestId;
        if (fullscreen) exitFullscreen();
        if (player != null) {
            player.pause();
            player.clearMediaItems();
        }
        playerPanel.setVisibility(View.GONE);
        currentMediaUrl = "";
        currentEpisodeIndex = -1;
        currentEpisodes.clear();
        currentDrama = null;
        setStatus("");
    }

    private void showSpeedDialog() {
        final float[] speeds = new float[]{0.75f, 1f, 1.25f, 1.5f, 2f};
        String[] labels = new String[]{"0.75x", "1.0x", "1.25x", "1.5x", "2.0x"};
        int checked = 1;
        for (int i = 0; i < speeds.length; i++) {
            if (Math.abs(playbackSpeed - speeds[i]) < 0.01f) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("播放速度")
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    playbackSpeed = speeds[which];
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(KEY_SPEED, playbackSpeed).apply();
                    if (player != null) player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
                    updateSpeedButton();
                    dialog.dismiss();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void updateSpeedButton() {
        String s = playbackSpeed == (int) playbackSpeed
                ? ((int) playbackSpeed) + ".0x"
                : playbackSpeed + "x";
        speedButton.setText(s);
    }

    private void enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Android 8.0 以上支持画中画", Toast.LENGTH_SHORT).show();
            return;
        }
        if (player == null || currentEpisodeIndex < 0) {
            Toast.makeText(this, "请先播放视频", Toast.LENGTH_SHORT).show();
            return;
        }
        PictureInPictureParams params = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9))
                .build();
        enterPictureInPictureMode(params);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        playerView.setUseController(!isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            topArea.setVisibility(View.GONE);
            statusText.setVisibility(View.GONE);
            contentScroll.setVisibility(View.GONE);
            nowPlaying.setVisibility(View.GONE);
            playerActions1.setVisibility(View.GONE);
            playerActions2.setVisibility(View.GONE);
            ((View) nowPlaying.getParent()).setVisibility(View.GONE);
            fullscreenCloseButton.setVisibility(View.GONE);
        } else if (fullscreen) {
            updateFullscreenOrientation(player.getVideoSize());
            playerView.showController();
            hideSystemBars();
        } else {
            restoreNormalUi();
        }
    }

    private void toggleFullscreen() {
        if (fullscreen) exitFullscreen();
        else enterFullscreen();
    }

    private void enterFullscreen() {
        if (playerPanel.getVisibility() != View.VISIBLE) {
            Toast.makeText(this, "请先播放视频", Toast.LENGTH_SHORT).show();
            return;
        }
        orientationBeforeFullscreen = getRequestedOrientation();
        fullscreen = true;
        updateFullscreenOrientation(player.getVideoSize());
        topArea.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        contentScroll.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.GONE);
        playerActions1.setVisibility(View.GONE);
        playerActions2.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
        speedButton.setVisibility(View.VISIBLE);
        pipButton.setVisibility(View.VISIBLE);
        fullscreenButton.setText("退出全屏");

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        playerPanel.setLayoutParams(panelLp);
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        videoContainer.setLayoutParams(videoLp);
        ((View) nowPlaying.getParent()).setVisibility(View.GONE);
        playerView.setControllerShowTimeoutMs(FULLSCREEN_CONTROLS_TIMEOUT_MS);
        playerView.setControllerAutoShow(false);
        playerView.showController();
        fullscreenCloseButton.setVisibility(View.VISIBLE);
        playerActions2.setVisibility(View.VISIBLE);
        hideSystemBars();
    }

    private void updateFullscreenOrientation(VideoSize size) {
        if (!fullscreen || size.width <= 0 || size.height <= 0
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode())) return;
        int orientation = size.width * size.pixelWidthHeightRatio > size.height
                ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT;
        if (getRequestedOrientation() != orientation) setRequestedOrientation(orientation);
    }

    private void exitFullscreen() {
        fullscreen = false;
        fullscreenCloseButton.setVisibility(View.GONE);
        playerView.setControllerShowTimeoutMs(NORMAL_CONTROLS_TIMEOUT_MS);
        playerView.setControllerAutoShow(true);
        setRequestedOrientation(orientationBeforeFullscreen);
        restoreNormalUi();
        showSystemBars();
    }

    private void restoreNormalUi() {
        ((View) nowPlaying.getParent()).setVisibility(View.VISIBLE);
        fullscreenCloseButton.setVisibility(View.GONE);
        topArea.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        contentScroll.setVisibility(View.VISIBLE);
        categoryScroll.setVisibility("home".equals(currentTab) ? View.VISIBLE : View.GONE);
        searchArea.setVisibility("search".equals(currentTab) ? View.VISIBLE : View.GONE);
        nowPlaying.setVisibility(View.VISIBLE);
        playerActions1.setVisibility(View.VISIBLE);
        playerActions2.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        fullscreenButton.setText("全屏");

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        playerPanel.setLayoutParams(panelLp);
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(220));
        videoContainer.setLayoutParams(videoLp);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                        View.SYSTEM_UI_FLAG_FULLSCREEN);
    }

    @SuppressWarnings("deprecation")
    private void showSystemBars() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    private List<Drama> parseGridCards(String html, boolean allGrids) {
        List<Drama> out = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        List<Integer> starts = findTagEnds(html,
                "<div\\s+class=\\\"[^\\\"]*\\bhg-card-grid\\b[^\\\"]*\\\"[^>]*>");
        if (starts.isEmpty()) return parseSearchFallback(html);

        int gridCount = allGrids ? starts.size() : 1;
        for (int g = 0; g < gridCount; g++) {
            int from = starts.get(g);
            int to = g + 1 < starts.size() ? starts.get(g + 1) : html.length();
            String slice = html.substring(from, to);
            List<Integer> cardStarts = findTagEnds(slice,
                    "<div\\s+class=\\\"[^\\\"]*\\bhg-drama-card\\b[^\\\"]*\\\"[^>]*>");
            for (int i = 0; i < cardStarts.size(); i++) {
                int cFrom = cardStarts.get(i);
                int cTo = i + 1 < cardStarts.size() ? cardStarts.get(i + 1) : slice.length();
                Drama d = parseCardBlock(slice.substring(cFrom, cTo));
                if (d != null && !seen.containsKey(d.id)) {
                    seen.put(d.id, true);
                    out.add(d);
                }
            }
        }
        return out;
    }

    private List<Integer> findTagEnds(String text, String regex) {
        List<Integer> out = new ArrayList<>();
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        while (m.find()) out.add(m.end());
        return out;
    }

    private Drama parseCardBlock(String block) {
        String id = firstGroup(block, "href=\\\"[^\\\"]*/detail/(\\d+)/[^\\\"]*\\\"");
        if (id.isEmpty()) return null;
        String poster = firstGroup(block, "data-src=\\\"([^\\\"]+)\\\"");
        if (poster.isEmpty()) poster = firstGroup(block, "src=\\\"([^\\\"]+)\\\"");
        String title = firstGroup(block, "hg-drama-card__title[^>]*>([\\s\\S]*?)</a>");
        if (title.isEmpty()) {
            title = firstGroup(block, "<a[^>]+href=\\\"[^\\\"]*/detail/\\d+/\\\"[^>]*>([\\s\\S]*?)</a>");
        }
        title = stripTags(title);
        if (title.isEmpty()) return null;

        String ep = stripTags(firstGroup(block, "hg-drama-card__episode[^>]*>([\\s\\S]*?)</span>"));
        String score = stripTags(firstGroup(block, "hg-drama-card__score[^>]*>([\\s\\S]*?)</span>"));
        String badge = !ep.isEmpty() && !score.isEmpty() ? ep + " · " + score : (!ep.isEmpty() ? ep : score);
        return new Drama(id, title, stableImageUrl(poster), badge);
    }

    private List<Drama> parseRanks(String html) {
        LinkedHashMap<String, Drama> out = new LinkedHashMap<>();
        Matcher listM = Pattern.compile("<div\\s+class=\\\"[^\\\"]*\\bhg-rank-list\\b[^\\\"]*\\\"[^>]*>",
                Pattern.CASE_INSENSITIVE).matcher(html);
        int from = listM.find() ? listM.end() : 0;
        String slice = html.substring(from);
        List<Integer> starts = findTagEnds(slice,
                "<div\\s+class=\\\"[^\\\"]*\\bhg-rank-item\\b[^\\\"]*\\\"[^>]*>");

        for (int i = 0; i < starts.size(); i++) {
            int to = i + 1 < starts.size() ? starts.get(i + 1) : slice.length();
            String block = slice.substring(starts.get(i), to);
            String id = firstGroup(block, "href=\\\"[^\\\"]*/detail/(\\d+)/[^\\\"]*\\\"");
            if (id.isEmpty() || out.containsKey(id)) continue;
            String poster = firstGroup(block, "data-src=\\\"([^\\\"]+)\\\"");
            if (poster.isEmpty()) poster = firstGroup(block, "src=\\\"([^\\\"]+)\\\"");
            String title = stripTags(firstGroup(block, "hg-rank-item__title[^>]*>([\\s\\S]*?)</h2>"));
            if (title.isEmpty()) title = stripTags(firstGroup(block,
                    "<a[^>]+href=\\\"[^\\\"]*/detail/\\d+/\\\"[^>]*>([\\s\\S]*?)</a>"));
            if (title.isEmpty()) continue;
            String tags = stripTags(firstGroup(block, "hg-rank-item__tags[^>]*>([\\s\\S]*?)</div>"));
            out.put(id, new Drama(id, title, stableImageUrl(poster), tags));
        }
        return new ArrayList<>(out.values());
    }

    private List<Drama> parseSearchFallback(String html) {
        LinkedHashMap<String, Drama> out = new LinkedHashMap<>();
        Pattern anchor = Pattern.compile("<a\\b[^>]*href=\\\"([^\\\"]*/detail/(\\d+)/?[^\\\"]*)\\\"[^>]*>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
        Matcher m = anchor.matcher(html);
        while (m.find()) {
            String id = m.group(2);
            if (out.containsKey(id)) continue;
            int start = Math.max(0, m.start() - 2200);
            int end = Math.min(html.length(), m.end() + 2600);
            String block = html.substring(start, end);
            String title = stripTags(firstGroup(block, "hg-drama-card__title[^>]*>([\\s\\S]*?)</a>"));
            if (title.isEmpty()) title = stripTags(firstGroup(block, "title=\\\"([^\\\"]+)\\\""));
            if (title.isEmpty()) title = stripTags(m.group(3));
            if (title.isEmpty()) continue;
            String poster = firstGroup(block, "<img\\b[^>]*(?:data-src|data-original|src)=\\\"([^\\\"]+)\\\"");
            String badge = stripTags(firstGroup(block, "((?:更新至|全)\\s*[0-9]+\\s*集)"));
            out.put(id, new Drama(id, title, stableImageUrl(poster), badge));
        }
        return new ArrayList<>(out.values());
    }

    private List<Episode> parseEpisodes(String html) {
        List<Episode> out = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();

        // Episode entries are nested in multiple divs. The old non-greedy div match stopped
        // at the first nested closing tag, so only the first episode was ever available.
        Pattern links = Pattern.compile("<a\\b(?=[^>]*\\bdata-ep-id\\s*=)[^>]*>[\\s\\S]*?</a>",
                Pattern.CASE_INSENSITIVE);
        Matcher lm = links.matcher(html);
        while (lm.find()) {
            String tag = lm.group();
            String href = attributeValue(tag, "href");
            if (href.isEmpty()) continue;
            String ep = attributeValue(tag, "data-ep-id");
            addEpisode(out, seen, ep, href);
        }

        if (out.isEmpty()) {
            String fallback = firstGroup(html,
                    "<a\\b(?=[^>]*\\bclass\\s*=\\s*[\\\"'][^\\\"']*\\bhg-web-detail__play\\b)(?=[^>]*\\bhref\\s*=\\s*[\\\"']([^\\\"']+)[\\\"'])[^>]*>");
            if (!fallback.isEmpty()) addEpisode(out, seen, "1", fallback);
        }

        out.sort((a, b) -> {
            try { return Integer.compare(Integer.parseInt(a.ep), Integer.parseInt(b.ep)); }
            catch (Exception ignored) { return a.ep.compareTo(b.ep); }
        });
        return out;
    }

    private void addEpisode(List<Episode> out, Map<String, Boolean> seen, String ep, String url) {
        if (ep == null || ep.isEmpty()) ep = String.valueOf(out.size() + 1);
        url = absoluteUrl(decodeHtml(url));
        String key = ep + "|" + url;
        if (seen.containsKey(key)) return;
        seen.put(key, true);
        out.add(new Episode("第" + ep + "集", ep, url));
    }

    private String extractPlayback(String html, String ep) throws Exception {
        Pattern p = Pattern.compile("<script\\b[^>]*id=[\\\"']videoInitialData[\\\"'][^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(html);
        if (!m.find()) return "";

        JSONObject data = new JSONObject(m.group(1).trim());
        String url = "";
        JSONObject epMap = data.optJSONObject("epPlaySrcs");
        if (epMap != null) url = epMap.optString(ep, "");
        if (url.isEmpty()) url = data.optString("videoSrc", "");
        url = decodeHtml(url.replace("\\u0026", "&").trim());
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Matcher embedded = Pattern.compile("https?://[^\\s\\\"'<>]+", Pattern.CASE_INSENSITIVE).matcher(url);
            if (embedded.find()) url = embedded.group();
        }
        return url;
    }

    private void checkForAppUpdate() {
        checkForAppUpdate(false);
    }

    private void checkForAppUpdate(boolean showResult) {
        final String repository = BuildConfig.UPDATE_REPOSITORY;
        if (repository == null || repository.trim().isEmpty()) {
            if (showResult) Toast.makeText(this, "未配置更新仓库", Toast.LENGTH_SHORT).show();
            return;
        }
        if (showResult) Toast.makeText(this, "正在检查更新…", Toast.LENGTH_SHORT).show();

        io.execute(() -> {
            try {
                JSONObject release = new JSONObject(httpGetGitHubJson(
                        "https://api.github.com/repos/" + repository + "/releases/latest"));
                UpdateInfo update = updateInfoFromRelease(release);
                if (update.versionCode <= 0) {
                    if (showResult) main.post(() -> Toast.makeText(this,
                            "最新 Release 缺少版本信息", Toast.LENGTH_SHORT).show());
                    return;
                }
                if (update.versionCode <= installedVersionCode()) {
                    if (showResult) main.post(() -> Toast.makeText(this,
                            "当前已是最新版本", Toast.LENGTH_SHORT).show());
                    return;
                }
                if (update.apkUrl.isEmpty()) {
                    if (showResult) main.post(() -> Toast.makeText(this,
                            "最新 Release 未找到 APK", Toast.LENGTH_SHORT).show());
                    return;
                }
                main.post(() -> showUpdateDialog(update));
            } catch (Exception e) {
                if (showResult) main.post(() -> Toast.makeText(this,
                        "检查更新失败：" + safeMessage(e), Toast.LENGTH_LONG).show());
            }
        });
    }

    // New releases publish update.json, while the fallback keeps old releases compatible.
    private UpdateInfo updateInfoFromRelease(JSONObject release) throws Exception {
        String defaultName = release.optString("tag_name", "新版本");
        JSONArray assets = release.optJSONArray("assets");
        if (assets == null) assets = new JSONArray();

        try {
            String metadataUrl = findReleaseAssetUrl(assets, "update.json");
            if (!metadataUrl.isEmpty()) {
                JSONObject metadata = new JSONObject(httpGetText(metadataUrl, "https://github.com/"));
                long versionCode = metadata.optLong("versionCode", 0L);
                String apkName = metadata.optString("apkName", "app-release.apk");
                String apkUrl = findReleaseAssetUrl(assets, apkName);
                if (versionCode > 0 && !apkUrl.isEmpty()) {
                    return new UpdateInfo(metadata.optString("versionName", defaultName), versionCode,
                            apkUrl, metadata.optString("sha256", ""));
                }
            }
        } catch (Exception ignored) {
            // A malformed metadata file must not prevent compatibility with older Releases.
        }

        String apkUrl = "";
        String shaUrl = "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null) continue;
            String name = asset.optString("name", "");
            String url = asset.optString("browser_download_url", "");
            if (name.endsWith(".apk") && apkUrl.isEmpty()) apkUrl = url;
            if (name.endsWith(".apk.sha256") && shaUrl.isEmpty()) shaUrl = url;
        }
        String sha256 = shaUrl.isEmpty() ? "" : sha256FromText(httpGetText(shaUrl, "https://github.com/"));
        return new UpdateInfo(defaultName, releaseVersionCode(release.optString("body", "")), apkUrl, sha256);
    }

    private String findReleaseAssetUrl(JSONArray assets, String assetName) {
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset != null && assetName.equals(asset.optString("name", ""))) {
                return asset.optString("browser_download_url", "");
            }
        }
        return "";
    }

    private String sha256FromText(String text) {
        Matcher matcher = Pattern.compile("\\b([a-fA-F0-9]{64})\\b").matcher(text == null ? "" : text);
        return matcher.find() ? matcher.group(1).toLowerCase() : "";
    }

    private String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
        }
        StringBuilder out = new StringBuilder(64);
        for (byte value : digest.digest()) out.append(String.format("%02x", value & 0xff));
        return out.toString();
    }

    private void registerUpdateDownloadReceiver() {
        ContextCompat.registerReceiver(this, updateDownloadReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_NOT_EXPORTED);
        updateReceiverRegistered = true;
    }

    private void restorePendingUpdateDownload() {
        long savedId = getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_UPDATE_DOWNLOAD_ID, -1L);
        String savedFile = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_UPDATE_FILE, "");
        if (savedFile == null || savedFile.isEmpty()) return;

        pendingUpdateApk = new File(savedFile);
        pendingUpdateSha256 = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_UPDATE_SHA256, "");
        if (savedId < 0) {
            if (pendingUpdateApk.isFile()) onUpdateDownloadFinished();
            else clearPendingUpdate(false);
            return;
        }

        updateDownloadId = savedId;
        DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(savedId))) {
            if (cursor == null || !cursor.moveToFirst()) {
                clearPendingUpdate(true);
                return;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_SUCCESSFUL) onUpdateDownloadFinished();
            else if (status == DownloadManager.STATUS_FAILED) clearPendingUpdate(true);
        } catch (Exception ignored) {
            // Keep the persisted task. DownloadManager can be queried again on the next launch.
        }
    }

    private void clearPendingUpdate(boolean deleteApk) {
        if (deleteApk && pendingUpdateApk != null && pendingUpdateApk.isFile()) pendingUpdateApk.delete();
        updateDownloadId = -1L;
        pendingUpdateApk = null;
        pendingUpdateSha256 = "";
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(KEY_UPDATE_DOWNLOAD_ID)
                .remove(KEY_UPDATE_FILE)
                .remove(KEY_UPDATE_SHA256)
                .apply();
    }

    private long releaseVersionCode(String notes) {
        Matcher matcher = Pattern.compile("versionCode\\s*:\\s*(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(notes == null ? "" : notes);
        if (!matcher.find()) return 0L;
        try {
            return Long.parseLong(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private long installedVersionCode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return getPackageManager().getPackageInfo(getPackageName(), 0).getLongVersionCode();
            }
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private void showUpdateDialog(UpdateInfo update) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("发现新版本 " + update.versionName)
                .setMessage("已有新版本可用，下载完成后将校验文件完整性并打开系统安装确认页。")
                .setNegativeButton("稍后再说", null)
                .setPositiveButton("立即更新", (dialog, which) -> downloadUpdate(update))
                .show();
    }

    private void downloadUpdate(UpdateInfo update) {
        File downloadDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir == null) {
            Toast.makeText(this, "无法创建更新下载目录", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingUpdateApk = new File(downloadDir, "huangguoplayer-" + update.versionCode + ".apk");
        pendingUpdateSha256 = update.sha256;
        if (pendingUpdateApk.exists()) pendingUpdateApk.delete();
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(update.apkUrl))
                .setTitle("短剧播放器更新")
                .setDescription("正在下载新版本")
                .setMimeType("application/vnd.android.package-archive")
                // The APK lives in this app's external-files directory.  On some OEM
                // builds the completed-download notification is opened by the system
                // downloader, which has no access to that private path and reports
                // ENOENT while parsing.  The completion receiver below opens it via
                // FileProvider instead.
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationUri(Uri.fromFile(pendingUpdateApk));
        updateDownloadId = ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putLong(KEY_UPDATE_DOWNLOAD_ID, updateDownloadId)
                .putString(KEY_UPDATE_FILE, pendingUpdateApk.getAbsolutePath())
                .putString(KEY_UPDATE_SHA256, pendingUpdateSha256)
                .apply();
        Toast.makeText(this, "已开始下载更新", Toast.LENGTH_SHORT).show();
    }

    private void onUpdateDownloadFinished() {
        updateDownloadId = -1L;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_UPDATE_DOWNLOAD_ID).apply();
        final File apkFile = pendingUpdateApk;
        final String expectedSha256 = pendingUpdateSha256;
        io.execute(() -> {
            if (apkFile == null || !apkFile.isFile() || apkFile.length() == 0) {
                main.post(() -> {
                    clearPendingUpdate(true);
                    Toast.makeText(this, "更新下载失败，请稍后重试", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            try {
                if (!expectedSha256.isEmpty() && !expectedSha256.equalsIgnoreCase(sha256(apkFile))) {
                    throw new IllegalStateException("文件校验失败");
                }
                main.post(() -> installUpdateApk(apkFile));
            } catch (Exception e) {
                main.post(() -> {
                    clearPendingUpdate(true);
                    Toast.makeText(this, "更新文件校验失败，请重新下载", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void installUpdateApk(File apkFile) {
        if (apkFile == null || !apkFile.isFile() || apkFile.length() == 0) {
            clearPendingUpdate(true);
            Toast.makeText(this, "更新文件不存在，请重新下载", Toast.LENGTH_LONG).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !getPackageManager().canRequestPackageInstalls()) {
            pendingUpdateApk = apkFile;
            new AlertDialog.Builder(this)
                    .setTitle("允许安装更新")
                    .setMessage("请允许“短剧播放器”安装未知来源应用，随后会自动打开安装确认页。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("去授权", (dialog, which) -> startActivity(new Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + getPackageName()))))
                    .show();
            return;
        }

        Uri apkUri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", apkFile);
        Intent installIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, "application/vnd.android.package-archive");
        // ClipData is required by some Android/OEM package installers to keep the
        // FileProvider read grant while their scanner process is started.
        installIntent.setClipData(ClipData.newRawUri("update-apk", apkUri));
        installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivity(installIntent);
        clearPendingUpdate(false);
    }

    private String httpGetGitHubJson(String url) throws Exception {
        HttpURLConnection conn = openConnection(url, "https://github.com/");
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private String currentContentSite() {
        return activeContentSite.isEmpty() ? CONTENT_SITE_FALLBACKS[0] : activeContentSite;
    }

    private String getContentPage(String path) throws Exception {
        Exception lastFailure = null;
        for (String site : contentSiteCandidates()) {
            try {
                String html = httpGetText(site + path, site + "/");
                if (!site.equals(activeContentSite)) {
                    activeContentSite = site;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_CONTENT_SITE, site).apply();
                }
                return html;
            } catch (Exception e) {
                lastFailure = e;
            }
        }
        if (lastFailure != null) throw lastFailure;
        throw new IllegalStateException("没有可用的内容线路");
    }

    /**
     * A content mirror can return its home page for every unknown path while still
     * returning HTTP 200.  Do not let those home-page cards become search results.
     * Try the known search origin first, then the current content candidates, and
     * accept a response only when at least one parsed title matches the query.
     */
    private List<Drama> getSearchResults(String keyword) throws Exception {
        String path = "/search/video/" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()) + "/";
        Exception lastFailure = null;
        for (String site : searchSiteCandidates()) {
            try {
                String html = httpGetText(site + path, site + "/");
                List<Drama> results = parseGridCards(html, false);
                if (results.isEmpty()) results = parseSearchFallback(html);
                List<Drama> related = filterSearchResults(results, keyword);
                if (related.isEmpty()) continue;

                if (!site.equals(activeContentSite)) {
                    activeContentSite = site;
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putString(KEY_CONTENT_SITE, site).apply();
                }
                return related;
            } catch (Exception e) {
                lastFailure = e;
            }
        }

        // An HTTP-successful page with no matching titles is treated as no result,
        // rather than exposing unrelated recommendation/home-page content.
        if (lastFailure == null) return new ArrayList<>();
        throw lastFailure;
    }

    private List<String> searchSiteCandidates() {
        LinkedHashMap<String, Boolean> sites = new LinkedHashMap<>();
        sites.put(SEARCH_SITE, true);
        for (String site : contentSiteCandidates()) sites.put(site, true);
        return new ArrayList<>(sites.keySet());
    }

    private List<Drama> filterSearchResults(List<Drama> results, String keyword) {
        List<Drama> related = new ArrayList<>();
        String query = normalizeSearchText(keyword);
        if (query.isEmpty()) return related;
        for (Drama drama : results) {
            if (normalizeSearchText(drama.title).contains(query)) related.add(drama);
        }
        return related;
    }

    private String normalizeSearchText(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{P}\\p{S}]+", "");
    }

    private List<String> contentSiteCandidates() {
        LinkedHashMap<String, Boolean> sites = new LinkedHashMap<>();
        if (!activeContentSite.isEmpty()) sites.put(activeContentSite, true);
        if (!contentSiteDirectoryChecked) {
            contentSiteDirectoryChecked = true;
            try {
                String directory = httpGetText(SITE_DIRECTORY, SITE_DIRECTORY + "/");
                Matcher matcher = Pattern.compile("https://[a-z0-9-]+\\.yhanwnftm\\.cc",
                        Pattern.CASE_INSENSITIVE).matcher(directory);
                while (matcher.find()) sites.put(matcher.group().toLowerCase(), true);
            } catch (Exception ignored) {
                // The directory itself can be temporarily unavailable; use the cached fallbacks below.
            }
        }
        for (String site : CONTENT_SITE_FALLBACKS) sites.put(site, true);
        return new ArrayList<>(sites.keySet());
    }

    private String getEpisodePage(String url) throws Exception {
        URL pageUrl = new URL(url);
        if (pageUrl.getHost().endsWith(".yhanwnftm.cc")) {
            return getContentPage(pageUrl.getFile());
        }
        return httpGetText(url, currentContentSite() + "/");
    }

    private String httpGetText(String url, String referer) throws Exception {
        HttpURLConnection conn = openConnection(url, referer);
        int code = conn.getResponseCode();
        if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private HttpURLConnection openConnection(String rawUrl, String referer) throws Exception {
        URL url = new URL(rawUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", UA);
        conn.setRequestProperty("Referer", referer);
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        return conn;
    }

    private void loadImage(String rawUrl, ImageView target) {
        String url = stableImageUrl(rawUrl);
        target.setTag(url);
        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        io.execute(() -> {
            try {
                HttpURLConnection conn = openConnection(url, currentContentSite() + "/");
                conn.setRequestProperty("Accept", "image/avif,image/webp,image/*,*/*;q=0.8");
                int code = conn.getResponseCode();
                if (code < 200 || code >= 300) throw new IllegalStateException("HTTP " + code);
                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                    byte[] raw = bos.toByteArray();
                    byte[] decoded = decryptImageIfNeeded(raw);
                    Bitmap bmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                    if (bmp == null && decoded != raw) bmp = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                    if (bmp != null) {
                        imageCache.put(url, bmp);
                        Bitmap finalBmp = bmp;
                        main.post(() -> {
                            if (url.equals(target.getTag())) target.setImageBitmap(finalBmp);
                        });
                    }
                } finally {
                    conn.disconnect();
                }
            } catch (Exception ignored) { }
        });
    }

    private byte[] decryptImageIfNeeded(byte[] raw) {
        if (raw == null || raw.length == 0 || raw.length % 16 != 0) return raw;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
            SecretKeySpec key = new SecretKeySpec(IMG_KEY.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec iv = new IvParameterSpec(IMG_IV.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.DECRYPT_MODE, key, iv);
            byte[] pt = cipher.doFinal(raw);
            if (!looksLikeImage(pt)) return raw;

            int len = pt.length;
            int pad = pt[len - 1] & 0xff;
            if (pad >= 1 && pad <= 16 && pad <= len) {
                boolean ok = true;
                for (int i = len - pad; i < len; i++) {
                    if ((pt[i] & 0xff) != pad) { ok = false; break; }
                }
                if (ok) len -= pad;
            }

            if (isJpeg(pt)) {
                for (int i = len - 2; i >= 0; i--) {
                    if ((pt[i] & 0xff) == 0xff && (pt[i + 1] & 0xff) == 0xd9) {
                        len = i + 2;
                        break;
                    }
                }
            } else if (isPng(pt)) {
                byte[] iend = new byte[]{0x49, 0x45, 0x4e, 0x44, (byte) 0xae, 0x42, 0x60, (byte) 0x82};
                int pos = lastIndexOf(pt, len, iend);
                if (pos >= 0) len = pos + iend.length;
            }
            return Arrays.copyOf(pt, len);
        } catch (Exception ignored) {
            return raw;
        }
    }

    private boolean looksLikeImage(byte[] b) {
        return isJpeg(b) || isPng(b) || isWebp(b) || isGif(b);
    }

    private boolean isJpeg(byte[] b) {
        return b.length >= 2 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8;
    }

    private boolean isPng(byte[] b) {
        byte[] sig = new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a};
        if (b.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) if (b[i] != sig[i]) return false;
        return true;
    }

    private boolean isWebp(byte[] b) {
        return b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
    }

    private boolean isGif(byte[] b) {
        return b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a';
    }

    private int lastIndexOf(byte[] data, int len, byte[] needle) {
        for (int i = len - needle.length; i >= 0; i--) {
            boolean ok = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) { ok = false; break; }
            }
            if (ok) return i;
        }
        return -1;
    }

    private boolean isFavorite(String id) {
        for (Drama d : loadFavorites()) if (d.id.equals(id)) return true;
        return false;
    }

    private void toggleFavorite(Drama drama) {
        List<Drama> items = loadFavorites();
        boolean removed = items.removeIf(d -> d.id.equals(drama.id));
        if (!removed) items.add(0, drama);
        saveDramaList(KEY_FAVORITES, items);
        Toast.makeText(this, removed ? "已取消收藏" : "已收藏", Toast.LENGTH_SHORT).show();
    }

    private List<Drama> loadFavorites() {
        return loadDramaList(KEY_FAVORITES);
    }

    private List<Drama> loadRecent() {
        return loadDramaList(KEY_RECENT);
    }

    private void saveRecent(Drama drama, int episodeIndex, String epName) {
        List<Drama> items = loadRecent();
        items.removeIf(d -> d.id.equals(drama.id));
        Drama copy = new Drama(drama.id, drama.title, drama.poster, epName);
        items.add(0, copy);
        while (items.size() > 30) items.remove(items.size() - 1);
        saveDramaList(KEY_RECENT, items);
    }

    private List<Drama> loadDramaList(String key) {
        List<Drama> out = new ArrayList<>();
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(key, "[]");
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Drama(o.optString("id"), o.optString("title"), o.optString("poster"), o.optString("badge")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    private void saveDramaList(String key, List<Drama> items) {
        JSONArray arr = new JSONArray();
        try {
            for (Drama d : items) {
                JSONObject o = new JSONObject();
                o.put("id", d.id);
                o.put("title", d.title);
                o.put("poster", d.poster);
                o.put("badge", d.badge);
                arr.put(o);
            }
        } catch (Exception ignored) { }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(key, arr.toString()).apply();
    }

    private void savePlaybackProgress() {
        if (player == null || currentDrama == null || currentEpisodeIndex < 0) return;
        long position = Math.max(0, player.getCurrentPosition());
        long duration = Math.max(0, player.getDuration());
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROGRESS, "{}");
            JSONObject root = new JSONObject(raw);
            JSONObject p = new JSONObject();
            p.put("episodeIndex", currentEpisodeIndex);
            p.put("position", position);
            p.put("duration", duration);
            root.put(currentDrama.id, p);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PROGRESS, root.toString()).apply();
        } catch (Exception ignored) { }
    }

    private long loadResumePosition(String dramaId, int episodeIndex) {
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROGRESS, "{}");
            JSONObject root = new JSONObject(raw);
            JSONObject p = root.optJSONObject(dramaId);
            if (p != null && p.optInt("episodeIndex", -1) == episodeIndex) {
                long position = p.optLong("position", 0);
                long duration = p.optLong("duration", 0);
                if (duration > 0 && position >= duration - 5000) return 0;
                return position;
            }
        } catch (Exception ignored) { }
        return 0;
    }

    private int loadResumeEpisodeIndex(String dramaId) {
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PROGRESS, "{}");
            JSONObject root = new JSONObject(raw);
            JSONObject p = root.optJSONObject(dramaId);
            if (p != null) return p.optInt("episodeIndex", -1);
        } catch (Exception ignored) { }
        return -1;
    }

    private void appendUnique(List<Drama> target, List<Drama> source) {
        Map<String, Boolean> seen = new HashMap<>();
        for (Drama d : target) seen.put(d.id, true);
        for (Drama d : source) {
            if (!seen.containsKey(d.id)) {
                seen.put(d.id, true);
                target.add(d);
            }
        }
    }

    private String firstGroup(String text, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(text);
        return m.find() ? m.group(1) : "";
    }

    private String attributeValue(String tag, String name) {
        String escapedName = Pattern.quote(name);
        Matcher quoted = Pattern.compile("\\b" + escapedName + "\\s*=\\s*([\\\"'])(.*?)\\1",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(tag);
        if (quoted.find()) return decodeHtml(quoted.group(2));
        Matcher unquoted = Pattern.compile("\\b" + escapedName + "\\s*=\\s*([^\\s>]+)",
                Pattern.CASE_INSENSITIVE).matcher(tag);
        return unquoted.find() ? decodeHtml(unquoted.group(1)) : "";
    }

    private String stripTags(String text) {
        return decodeHtml(text == null ? "" : text.replaceAll("<[^>]+>", " "))
                .replaceAll("\\s+", " ").trim();
    }

    private String decodeHtml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }

    private String absoluteUrl(String value) {
        if (value == null || value.isEmpty()) return "";
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return currentContentSite() + value;
        return value;
    }

    private String stableImageUrl(String value) {
        String u = absoluteUrl(decodeHtml(value));
        int q = u.indexOf('?');
        if ((u.startsWith("http://") || u.startsWith("https://")) && q >= 0) return u.substring(0, q);
        return u;
    }

    private String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }

    private void setStatus(String text) {
        statusText.setText(text == null ? "" : text);
    }

    private TextView textView(String text, float sp, int color) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextSize(sp);
        v.setTextColor(color);
        return v;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radiusDp));
        return d;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && pendingUpdateApk != null
                && getPackageManager().canRequestPackageInstalls()) {
            File apkFile = pendingUpdateApk;
            pendingUpdateApk = null;
            installUpdateApk(apkFile);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePlaybackProgress();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(progressSaver);
        if (updateReceiverRegistered) unregisterReceiver(updateDownloadReceiver);
        savePlaybackProgress();
        if (player != null) player.release();
        io.shutdownNow();
        super.onDestroy();
    }

    static class Category {
        final String name;
        final String id;

        Category(String name, String id) {
            this.name = name;
            this.id = id;
        }
    }

    static class Drama {
        final String id;
        final String title;
        final String poster;
        final String badge;

        Drama(String id, String title, String poster, String badge) {
            this.id = id == null ? "" : id;
            this.title = title == null ? "" : title;
            this.poster = poster == null ? "" : poster;
            this.badge = badge == null ? "" : badge;
        }
    }

    static class Episode {
        final String name;
        final String ep;
        final String url;

        Episode(String name, String ep, String url) {
            this.name = name;
            this.ep = ep;
            this.url = url;
        }
    }

    static class UpdateInfo {
        final String versionName;
        final long versionCode;
        final String apkUrl;
        final String sha256;

        UpdateInfo(String versionName, long versionCode, String apkUrl, String sha256) {
            this.versionName = versionName == null || versionName.isEmpty() ? "新版本" : versionName;
            this.versionCode = versionCode;
            this.apkUrl = apkUrl == null ? "" : apkUrl;
            this.sha256 = sha256 == null ? "" : sha256;
        }
    }
}
