package com.example.huangguoplayer;

import android.app.PictureInPictureParams;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Rational;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.AlertDialog;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.ui.PlayerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
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
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private static final String SITE = "https://huangguoai.com";
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

    private ExoPlayer player;
    private final List<Drama> displayed = new ArrayList<>();
    private final List<Drama> lastSearch = new ArrayList<>();
    private final List<Episode> currentEpisodes = new ArrayList<>();
    private Drama currentDrama;
    private int currentEpisodeIndex = -1;
    private String currentTab = "home";
    private String currentCategoryId = "home";
    private int currentPage = 1;
    private boolean categoryLoading = false;
    private boolean fullscreen = false;
    private String currentMediaUrl = "";
    private float playbackSpeed = 1f;

    private final Map<String, Bitmap> imageCache = new HashMap<>();

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
        setupPlayer();
        setupEvents();
        renderCategories();
        renderSearchHistory();
        styleTabs();
        updateSpeedButton();
        loadCategory("home", true);
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
    }

    private void setupPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        player.setPlaybackParameters(new PlaybackParameters(playbackSpeed));
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
            public void onPlayerError(@NonNull PlaybackException error) {
                setStatus("播放失败：" + error.getErrorCodeName() + "，可点击重试");
            }
        });
    }

    private void setupEvents() {
        searchButton.setOnClickListener(v -> search());
        clearHistoryButton.setOnClickListener(v -> clearSearchHistory());
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

        prevButton.setOnClickListener(v -> {
            if (currentEpisodeIndex > 0) playEpisode(currentEpisodeIndex - 1);
        });
        nextButton.setOnClickListener(v -> {
            if (currentEpisodeIndex >= 0 && currentEpisodeIndex < currentEpisodes.size() - 1) {
                playEpisode(currentEpisodeIndex + 1);
            }
        });
        episodeButton.setOnClickListener(v -> showEpisodeDialog());
        retryButton.setOnClickListener(v -> retryCurrent());
        speedButton.setOnClickListener(v -> showSpeedDialog());
        pipButton.setOnClickListener(v -> enterPip());
        fullscreenButton.setOnClickListener(v -> toggleFullscreen());
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
        button.setBackgroundColor(selected ? Color.WHITE : Color.rgb(46, 52, 64));
        button.setTextColor(selected ? Color.BLACK : Color.WHITE);
    }

    private void renderCategories() {
        categoryRow.removeAllViews();
        for (Category category : CATEGORIES) {
            Button b = new Button(this);
            b.setText(category.name);
            b.setAllCaps(false);
            b.setTextSize(12);
            b.setTextColor(category.id.equals(currentCategoryId) ? Color.BLACK : Color.WHITE);
            b.setBackground(rounded(category.id.equals(currentCategoryId) ? Color.WHITE : Color.rgb(32, 36, 45), 18));
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
        if (categoryLoading) return;
        categoryLoading = true;
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
                String url;
                if ("home".equals(category)) {
                    url = SITE + "/";
                } else {
                    url = SITE + "/" + category + "/" + (page > 1 ? page + "/" : "");
                }
                String html = httpGetText(url, SITE + "/");
                List<Drama> results;
                if (category.contains("rank")) results = parseRanks(html);
                else results = parseGridCards(html, "home".equals(category));

                main.post(() -> {
                    if (reset) displayed.clear();
                    appendUnique(displayed, results);
                    setStatus(results.isEmpty() && page == 1 ? "没有找到内容" : "已加载 " + displayed.size() + " 项");
                    renderDramas(displayed);
                    boolean canPage = !"home".equals(category) && !results.isEmpty();
                    loadMoreButton.setVisibility(canPage ? View.VISIBLE : View.GONE);
                    loadMoreButton.setEnabled(true);
                    categoryLoading = false;
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (!reset && currentPage > 1) currentPage--;
                    setStatus("加载失败：" + safeMessage(e));
                    loadMoreButton.setEnabled(true);
                    categoryLoading = false;
                });
            }
        });
    }

    private void search() {
        final String keyword = searchInput.getText().toString().trim();
        if (keyword.isEmpty()) return;
        currentTab = "search";
        styleTabs();
        saveSearchHistory(keyword);
        renderSearchHistory();
        setStatus("正在搜索...");
        contentGrid.removeAllViews();
        loadMoreButton.setVisibility(View.GONE);

        io.execute(() -> {
            try {
                String url = SITE + "/search/video/" + URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()) + "/";
                String html = httpGetText(url, SITE + "/");
                List<Drama> results = parseGridCards(html, false);
                if (results.isEmpty()) results = parseSearchFallback(html);
                List<Drama> finalResults = results;
                main.post(() -> {
                    lastSearch.clear();
                    lastSearch.addAll(finalResults);
                    setStatus("找到 " + finalResults.size() + " 个结果");
                    renderDramas(finalResults);
                });
            } catch (Exception e) {
                main.post(() -> setStatus("搜索失败：" + safeMessage(e)));
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
            b.setTextColor(Color.rgb(210, 216, 224));
            b.setBackground(rounded(Color.rgb(32, 36, 45), 18));
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
        card.setPadding(dp(6), dp(6), dp(6), dp(8));
        card.setBackground(rounded(Color.rgb(23, 26, 33), 14));

        GridLayout.LayoutParams cardLp = new GridLayout.LayoutParams();
        cardLp.columnSpec = GridLayout.spec(index % 2, 1f);
        cardLp.width = 0;
        cardLp.setMargins(dp(5), dp(5), dp(5), dp(5));
        card.setLayoutParams(cardLp);

        FrameLayout posterFrame = new FrameLayout(this);
        LinearLayout.LayoutParams posterLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(210));
        posterFrame.setLayoutParams(posterLp);
        posterFrame.setBackgroundColor(Color.rgb(36, 40, 50));

        ImageView poster = new ImageView(this);
        poster.setScaleType(ImageView.ScaleType.CENTER_CROP);
        posterFrame.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button fav = new Button(this);
        fav.setText(isFavorite(drama.id) ? "★" : "☆");
        fav.setTextSize(18);
        fav.setTextColor(Color.WHITE);
        fav.setBackgroundColor(Color.argb(165, 0, 0, 0));
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

        TextView title = textView(drama.title, 14, Color.WHITE);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setPadding(dp(4), dp(8), dp(4), dp(2));
        title.setMaxLines(2);
        card.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!drama.poster.isEmpty()) loadImage(drama.poster, poster);

        card.setOnClickListener(v -> loadEpisodes(drama));
        fav.setOnClickListener(v -> {
            toggleFavorite(drama);
            fav.setText(isFavorite(drama.id) ? "★" : "☆");
            if ("favorites".equals(currentTab)) renderDramas(loadFavorites());
        });

        return card;
    }

    private void loadEpisodes(Drama drama) {
        currentDrama = drama;
        setStatus("正在读取：" + drama.title);
        io.execute(() -> {
            try {
                String html = httpGetText(SITE + "/detail/" + drama.id + "/", SITE + "/");
                List<Episode> episodes = parseEpisodes(html);
                main.post(() -> {
                    currentEpisodes.clear();
                    currentEpisodes.addAll(episodes);
                    currentEpisodeIndex = loadResumeEpisodeIndex(drama.id);
                    if (currentEpisodeIndex < 0 || currentEpisodeIndex >= episodes.size()) currentEpisodeIndex = -1;
                    setStatus(drama.title + " · " + episodes.size() + " 集");
                    if (episodes.isEmpty()) Toast.makeText(this, "没有解析到剧集", Toast.LENGTH_SHORT).show();
                    else showEpisodeDialog();
                });
            } catch (Exception e) {
                main.post(() -> setStatus("读取剧集失败：" + safeMessage(e)));
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
                b.setTextColor(Color.BLACK);
                b.setBackgroundColor(Color.WHITE);
            }
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
        savePlaybackProgress();
        currentEpisodeIndex = index;
        Episode ep = currentEpisodes.get(index);
        playerPanel.setVisibility(View.VISIBLE);
        nowPlaying.setText(currentDrama.title + " · " + ep.name);
        prevButton.setEnabled(index > 0);
        nextButton.setEnabled(index < currentEpisodes.size() - 1);
        setStatus("正在解析 " + ep.name + "...");
        saveRecent(currentDrama, index, ep.name);

        io.execute(() -> {
            try {
                String html = httpGetText(ep.url, SITE + "/");
                String mediaUrl = extractPlayback(html, ep.ep);
                if (mediaUrl.isEmpty()) throw new IllegalStateException("未解析到播放地址");
                main.post(() -> startHls(mediaUrl, index));
            } catch (Exception e) {
                main.post(() -> setStatus("解析失败：" + safeMessage(e)));
            }
        });
    }

    private void startHls(String mediaUrl, int episodeIndex) {
        currentMediaUrl = mediaUrl;
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", SITE + "/");
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
            setStatus("正在重试...");
            startHls(currentMediaUrl, currentEpisodeIndex);
        } else if (currentEpisodeIndex >= 0) {
            playEpisode(currentEpisodeIndex);
        }
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
        } else if (!fullscreen) {
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
        fullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        topArea.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        contentScroll.setVisibility(View.GONE);
        nowPlaying.setVisibility(View.GONE);
        playerActions1.setVisibility(View.GONE);
        playerActions2.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.GONE);
        speedButton.setVisibility(View.VISIBLE);
        pipButton.setVisibility(View.VISIBLE);
        fullscreenButton.setText("退出全屏");

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        playerPanel.setLayoutParams(panelLp);
        LinearLayout.LayoutParams videoLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        playerView.setLayoutParams(videoLp);
        hideSystemBars();
    }

    private void exitFullscreen() {
        fullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        restoreNormalUi();
        showSystemBars();
    }

    private void restoreNormalUi() {
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
        playerView.setLayoutParams(videoLp);
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

        Pattern gridPattern = Pattern.compile(
                "<div\\s+class=\\\"[^\\\"]*\\bhg-web-detail__ep-grid\\b[^\\\"]*\\\"[^>]*>([\\s\\S]*?)</div>",
                Pattern.CASE_INSENSITIVE);
        Matcher grid = gridPattern.matcher(html);
        String area = grid.find() ? grid.group(1) : html;

        Pattern links = Pattern.compile("<a\\b[^>]*>[\\s\\S]*?</a>", Pattern.CASE_INSENSITIVE);
        Matcher lm = links.matcher(area);
        while (lm.find()) {
            String tag = lm.group();
            String href = firstGroup(tag, "href=\\\"([^\\\"]+)\\\"");
            if (href.isEmpty()) continue;
            String ep = firstGroup(tag, "data-ep-id=\\\"([^\\\"]*)\\\"");
            addEpisode(out, seen, ep, href);
        }

        if (out.isEmpty()) {
            String fallback = firstGroup(html,
                    "<a\\b[^>]*class=\\\"[^\\\"]*\\bhg-web-detail__play\\b[^\\\"]*\\\"[^>]*href=\\\"([^\\\"]+)\\\"");
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
        Bitmap cached = imageCache.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }

        io.execute(() -> {
            try {
                HttpURLConnection conn = openConnection(url, SITE + "/");
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
                        main.post(() -> target.setImageBitmap(finalBmp));
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
        if (value.startsWith("/")) return SITE + value;
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
    protected void onPause() {
        super.onPause();
        savePlaybackProgress();
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacks(progressSaver);
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
}
