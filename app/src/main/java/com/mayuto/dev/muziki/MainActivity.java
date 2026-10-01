package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.StrictMode;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private GridView gridView;
    private RelativeLayout miniPlayer, header, searchPill;
    private LinearLayout emptyStateView, pullRefreshContainer;
    private TextView txtMiniTitle, headerText, btnTabAll, btnTabFav, btnTabFolder, btnTabSmart;
    private TextView txtLibraryStats, txtEmptyStateMessage, txtPullRefreshLabel;
    private ImageButton btnMiniPlayPause, btnSearchTrigger, btnViewToggle, btnSettings, btnSort, btnClearSearch, btnPlaylists;
    private ImageButton btnMuzikiAI;
    private View fabWatchVideos;
    private ImageView imgPullRefresh;
    private View dotNowPlaying;
    private EditText editSearch;
    private View tabIndicator;

    // Master Lists
    private ArrayList<String> songList = new ArrayList<String>();
    private ArrayList<String> pathList = new ArrayList<String>();
    private ArrayList<String> albumList = new ArrayList<String>();

    // Current Display Lists
    private ArrayList<String> filteredTitles = new ArrayList<String>();
    private ArrayList<String> filteredPaths = new ArrayList<String>();

    // Folder View Specifics
    private ArrayList<String> uniqueFolders = new ArrayList<String>();
    private ArrayList<Integer> folderSongCount = new ArrayList<Integer>();

    private SongAdapter adapter;
    private String currentSortOrder = MediaStore.Audio.Media.TITLE + " ASC";
    private boolean isGridView = false;
    private boolean isSearchActive = false;

    // Navigation State
    private int currentTab = 0; // 0=All, 1=Folders, 2=Favs, 3=Smart
    private boolean isInsideFolder = false;
    private String currentFolderOpen = "";

    // Pull-to-refresh state
    private float pullStartY = -1f;
    private boolean isPulling = false;
    private boolean isRefreshing = false;
    private static final float PULL_TRIGGER_DISTANCE = 130f;

    // Swipe-to-switch-tab gesture state
    private GestureDetector tabSwipeDetector;
    private static final int[] TAB_ORDER = {0, 1, 2, 3}; // All, Folders, Favorites, Smart

    private BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            saveLastPlayedState();
            updateMiniPlayerUI();
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // First-run gate: if the user has not yet accepted the Welcome
        // terms/privacy screen, redirect there instead of loading the
        // library. This keeps MainActivity as the single LAUNCHER entry
        // point (so shortcuts/intents still work) while still enforcing
        // the onboarding flow on first install.
        if (!WelcomeActivity.hasAcceptedTerms(this)) {
            startActivity(new Intent(this, WelcomeActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.main);

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                Method m = StrictMode.class.getMethod("disableDeathOnFileUriExposure");
                m.invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Initialize Views
        gridView = (GridView) findViewById(R.id.songGridView);
        miniPlayer = (RelativeLayout) findViewById(R.id.miniPlayer);
        header = (RelativeLayout) findViewById(R.id.header);
        searchPill = (RelativeLayout) findViewById(R.id.searchPill);
        emptyStateView = (LinearLayout) findViewById(R.id.emptyStateView);
        pullRefreshContainer = (LinearLayout) findViewById(R.id.pullRefreshContainer);
        txtMiniTitle = (TextView) findViewById(R.id.txtMiniTitle);
        headerText = (TextView) findViewById(R.id.headerText);
        txtLibraryStats = (TextView) findViewById(R.id.txtLibraryStats);
        txtEmptyStateMessage = (TextView) findViewById(R.id.txtEmptyStateMessage);
        txtPullRefreshLabel = (TextView) findViewById(R.id.txtPullRefreshLabel);
        btnMiniPlayPause = (ImageButton) findViewById(R.id.btnMiniPlayPause);
        btnSearchTrigger = (ImageButton) findViewById(R.id.btnSearchTrigger);
        btnViewToggle = (ImageButton) findViewById(R.id.btnViewToggle);
        btnSettings = (ImageButton) findViewById(R.id.btnSettings);
        btnPlaylists = (ImageButton) findViewById(R.id.btnPlaylists);
        btnMuzikiAI = (ImageButton) findViewById(R.id.btnMuzikiAI);
        fabWatchVideos = findViewById(R.id.fabWatchVideos);
        btnSort = (ImageButton) findViewById(R.id.btnSort);
        btnClearSearch = (ImageButton) findViewById(R.id.btnClearSearch);
        imgPullRefresh = (ImageView) findViewById(R.id.imgPullRefresh);
        editSearch = (EditText) findViewById(R.id.editSearch);
        tabIndicator = findViewById(R.id.tabIndicator);
        dotNowPlaying = findViewById(R.id.dotNowPlaying);

        btnTabAll = (TextView) findViewById(R.id.btnTabAll);
        btnTabFolder = (TextView) findViewById(R.id.btnTabAlbum);
        btnTabFav = (TextView) findViewById(R.id.btnTabFav);
        btnTabSmart = (TextView) findViewById(R.id.btnTabSmart);

        btnTabFolder.setText("FOLDERS");

        if (header != null && header.getBackground() instanceof AnimationDrawable) {
            AnimationDrawable anim = (AnimationDrawable) header.getBackground();
            anim.setEnterFadeDuration(2000);
            anim.setExitFadeDuration(2000);
            anim.start();
        }

        loadPreferences();
        restoreLastPlayedState();
        if (MusicService.currentTitle != null && !MusicService.currentTitle.isEmpty()) {
            ReminderScheduler.ensureScheduled(this);
        }
        setupListeners();
        setupPullToRefresh();
        checkAllPermissions();

        // Set the initial tab indicator position and icon colors once layout has measured itself
        btnTabAll.post(new Runnable() {
            @Override public void run() { updateTabUI(); }
        });

        handleExternalIntent(getIntent());
    }

    private void openPlaylists() {
        final ArrayList<PlaylistManager.Playlist> playlists = PlaylistManager.getAll(this);
        final String[] names = new String[playlists.size() + 1];
        names[0] = "+ Create playlist";
        for (int i = 0; i < playlists.size(); i++) {
            PlaylistManager.Playlist p = playlists.get(i);
            names[i + 1] = p.name + "  (" + p.paths.size() + " songs)";
        }
        new AlertDialog.Builder(this).setTitle("Playlists").setItems(names, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    final EditText input = new EditText(MainActivity.this);
                    input.setHint("e.g. My Chill Music");
                    input.setSingleLine(true);
                    new AlertDialog.Builder(MainActivity.this).setTitle("Create playlist").setView(input)
                        .setPositiveButton("Create", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                PlaylistManager.Playlist p = PlaylistManager.create(MainActivity.this, input.getText().toString());
                                Intent i = new Intent(MainActivity.this, PlaylistActivity.class);
                                i.putExtra("playlist_id", p.id); startActivity(i);
                            }
                        }).setNegativeButton("Cancel", null).show();
                } else {
                    Intent i = new Intent(MainActivity.this, PlaylistActivity.class);
                    i.putExtra("playlist_id", playlists.get(which - 1).id); startActivity(i);
                }
            }
        }).setNegativeButton("Close", null).show();
    }

    private void saveLastPlayedState() {
        if (MusicService.currentTitle != null) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("saved_song_title", MusicService.currentTitle);
            editor.apply();
        }
    }

    /**
     * Marks "the user has been in the app" right now. The daily reminder
     * check (ReminderReceiver) compares this timestamp against the last time
     * a song was played - if the user opened the app *after* that song
     * played, they've already seen it and don't need a nag notification.
     */
    private void stampAppOpenedNow() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putLong("last_app_opened_time", System.currentTimeMillis()).apply();
    }

    private void restoreLastPlayedState() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String savedTitle = prefs.getString("saved_song_title", "");
        if (!savedTitle.isEmpty() && (MusicService.currentTitle == null || MusicService.currentTitle.isEmpty())) {
            MusicService.currentTitle = savedTitle;
            MusicService.isPlaying = false;
        }
    }

    private void handleExternalIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                Intent serviceIntent = new Intent(this, MusicService.class);
                serviceIntent.setAction(MusicService.ACTION_PLAY_FROM_URI);
                serviceIntent.setData(data);
                startService(serviceIntent);
            }
        }
    }

    private void setupListeners() {
        btnTabAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToTabForced(0); }
        });

        btnTabFolder.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToTabForced(1); }
        });

        btnTabFav.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToTabForced(2); }
        });

        btnTabSmart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { goToTabForced(3); }
        });

        btnSearchTrigger.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!isSearchActive) {
                    openSearch();
                } else {
                    closeSearch();
                }
            }
        });

        btnClearSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editSearch.setText("");
                editSearch.requestFocus();
                btnClearSearch.setVisibility(View.GONE);
            }
        });

        btnSort.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showSortMenu(v); }
        });

        btnViewToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isGridView = !isGridView;
                applyViewFormat();
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
                editor.putBoolean("pref_grid_default", isGridView);
                editor.apply();
            }
        });

        btnSettings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        btnMuzikiAI.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (AuthManager.isSignedIn(MainActivity.this)) {
                    startActivity(new Intent(MainActivity.this, MuzikiAIActivity.class));
                } else {
                    startActivity(new Intent(MainActivity.this, AuthGateActivity.class));
                }
            }
        });

        if (fabWatchVideos != null) {
            fabWatchVideos.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    openVideoLibrary();
                }
            });
        }

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                filterSongs(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        gridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentTab == 1 && !isInsideFolder) {
                    if (position >= 0 && position < uniqueFolders.size()) {
                        enterFolder(uniqueFolders.get(position));
                    }
                } else {
                    playMusic(position);
                }
            }
        });

        gridView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (currentTab == 1 && !isInsideFolder) return false;
                showCustomOptionsDialog(position);
                return true;
            }
        });

        btnPlaylists.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPlaylists(); }
        });

        miniPlayer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PlayerActivity.class));
            }
        });

        btnMiniPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, MusicService.class);
                if (MusicService.isPlaying) {
                    intent.setAction(MusicService.ACTION_PAUSE);
                    MusicService.isPlaying = false;
                } else {
                    intent.setAction(MusicService.ACTION_RESUME);
                    MusicService.isPlaying = true;
                }
                startService(intent);
                updatePlayPauseIcon();
                saveLastPlayedState();
                if (adapter != null) adapter.notifyDataSetChanged();
            }
        });
    }

    // ---------- FEATURE: Redesigned animated search bar ----------

    private void openSearch() {
        isSearchActive = true;
        searchPill.setVisibility(View.VISIBLE);
        Animation expand = AnimationUtils.loadAnimation(this, R.anim.search_expand);
        searchPill.startAnimation(expand);
        headerText.setVisibility(View.GONE);
        txtLibraryStats.setVisibility(View.GONE);
        editSearch.requestFocus();
    }

    private void closeSearch() {
        isSearchActive = false;
        Animation collapse = AnimationUtils.loadAnimation(this, R.anim.search_collapse);
        collapse.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation animation) {}
            @Override public void onAnimationRepeat(Animation animation) {}
            @Override public void onAnimationEnd(Animation animation) {
                searchPill.setVisibility(View.GONE);
            }
        });
        searchPill.startAnimation(collapse);

        headerText.setVisibility(View.VISIBLE);
        headerText.startAnimation(AnimationUtils.loadAnimation(this, R.anim.fade_in_fast));
        txtLibraryStats.setVisibility(View.VISIBLE);

        editSearch.setText("");
        btnClearSearch.setVisibility(View.GONE);
        hideKeyboard();
        refreshList();
    }

    private void hideKeyboard() {
        try {
            android.view.inputmethod.InputMethodManager imm =
                    (android.view.inputmethod.InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(editSearch.getWindowToken(), 0);
        } catch (Exception e) { /* best effort */ }
    }

    // ---------- FEATURE: Pull-to-refresh gesture on the song list ----------

    private void setupPullToRefresh() {
        // Detects a clean horizontal swipe (left/right) on the song list to
        // switch tabs, the same way tapping a tab does. Only fires on a
        // fast, mostly-horizontal fling so it never fights with normal
        // vertical list scrolling or the pull-to-refresh gesture above it.
        tabSwipeDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            private static final int SWIPE_MIN_DISTANCE = 90;
            private static final int SWIPE_MAX_OFF_PATH = 200;
            private static final int SWIPE_THRESHOLD_VELOCITY = 200;

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                if (isSearchActive || isRefreshing || isPulling) return false;

                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();

                if (Math.abs(dy) > SWIPE_MAX_OFF_PATH) return false;
                if (Math.abs(dx) < SWIPE_MIN_DISTANCE) return false;
                if (Math.abs(velocityX) < SWIPE_THRESHOLD_VELOCITY) return false;

                if (dx < 0) {
                    swipeToAdjacentTab(true);  // swiped left: next tab
                } else {
                    swipeToAdjacentTab(false); // swiped right: previous tab
                }
                return true;
            }
        });

        gridView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                tabSwipeDetector.onTouchEvent(event);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isAtTopOfList()) {
                            pullStartY = event.getY();
                        } else {
                            pullStartY = -1f;
                        }
                        isPulling = false;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (pullStartY >= 0 && isAtTopOfList() && !isRefreshing) {
                            float delta = event.getY() - pullStartY;
                            if (delta > 24) {
                                isPulling = true;
                                float pullAmount = Math.min(delta, PULL_TRIGGER_DISTANCE * 1.4f);
                                pullRefreshContainer.setVisibility(View.VISIBLE);
                                float rotation = (pullAmount / PULL_TRIGGER_DISTANCE) * 360f;
                                imgPullRefresh.setRotation(rotation);
                                boolean ready = pullAmount >= PULL_TRIGGER_DISTANCE;
                                txtPullRefreshLabel.setText(ready ? "Release to refresh" : "Pull to refresh");
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isPulling && pullStartY >= 0) {
                            float delta = event.getY() - pullStartY;
                            if (delta >= PULL_TRIGGER_DISTANCE) {
                                triggerLibraryRefresh();
                            } else {
                                pullRefreshContainer.setVisibility(View.GONE);
                            }
                        }
                        pullStartY = -1f;
                        isPulling = false;
                        break;
                }
                return false; // allow normal scrolling/click handling to continue
            }
        });
    }

    private boolean isAtTopOfList() {
        if (gridView.getChildCount() == 0) return true;
        return gridView.getFirstVisiblePosition() == 0 && gridView.getChildAt(0).getTop() >= 0;
    }

    private void triggerLibraryRefresh() {
        if (isRefreshing) return;
        isRefreshing = true;
        txtPullRefreshLabel.setText("Refreshing...");
        Animation spin = AnimationUtils.loadAnimation(this, R.anim.refresh_spin);
        imgPullRefresh.startAnimation(spin);

        loadMusicDataSilently(new Runnable() {
            @Override
            public void run() {
                imgPullRefresh.clearAnimation();
                pullRefreshContainer.setVisibility(View.GONE);
                isRefreshing = false;
                Toast.makeText(MainActivity.this, "Library refreshed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showSortMenu(View v) {
        PopupMenu popup = new PopupMenu(this, v);
        popup.getMenu().add(1, 1, 1, "Name (A-Z)");
        popup.getMenu().add(1, 2, 2, "Latest Added");
        popup.getMenu().add(1, 3, 3, "File Size (Large)");
        popup.getMenu().add(1, 4, 4, "Artist (A-Z)");

        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit();
                switch (item.getItemId()) {
                    case 1:
                        currentSortOrder = MediaStore.Audio.Media.TITLE + " ASC";
                        editor.putString("pref_sort_default", "title");
                        break;
                    case 2:
                        currentSortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
                        editor.putString("pref_sort_default", "date");
                        break;
                    case 3:
                        currentSortOrder = MediaStore.Audio.Media.SIZE + " DESC";
                        editor.putString("pref_sort_default", "size");
                        break;
                    case 4:
                        currentSortOrder = MediaStore.Audio.Media.ARTIST + " ASC";
                        editor.putString("pref_sort_default", "artist");
                        break;
                }
                editor.apply();
                loadMusicData();
                return true;
            }
        });
        popup.show();
    }

    private void enterFolder(String folderPath) {
        isInsideFolder = true;
        currentFolderOpen = folderPath;
        headerText.setText(new File(folderPath).getName());

        filteredTitles.clear();
        filteredPaths.clear();

        for (int i = 0; i < pathList.size(); i++) {
            File file = new File(pathList.get(i));
            String parent = file.getParent();
            if (parent != null && parent.equals(folderPath)) {
                filteredTitles.add(songList.get(i));
                filteredPaths.add(pathList.get(i));
            }
        }

        if (adapter != null) adapter.notifyDataSetChanged();
        gridView.setSelection(0);
        updateEmptyState();
        playListEntranceAnimation();
    }

    private void refreshList() {
        filteredTitles.clear();
        filteredPaths.clear();
        if (!isSearchActive) headerText.setText("muziki");

        if (currentTab == 2) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Set<String> favorites = prefs.getStringSet("favorites", new HashSet<String>());
            for (int i = 0; i < pathList.size(); i++) {
                if (favorites.contains(pathList.get(i))) {
                    filteredTitles.add(songList.get(i));
                    filteredPaths.add(pathList.get(i));
                }
            }
        } else if (currentTab == 1) {
            uniqueFolders.clear();
            folderSongCount.clear();
            for (String path : pathList) {
                File file = new File(path);
                String parent = file.getParent();
                if (parent != null && !uniqueFolders.contains(parent)) {
                    uniqueFolders.add(parent);
                    int count = 0;
                    for (String p : pathList) {
                        File pf = new File(p);
                        if (parent.equals(pf.getParent())) count++;
                    }
                    folderSongCount.add(count);
                }
            }
            for (String folder : uniqueFolders) {
                filteredTitles.add(new File(folder).getName());
            }
        } else if (currentTab == 3) {
            if (!isSearchActive) headerText.setText("Smart Mix");
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            for (int i = 0; i < pathList.size(); i++) {
                String path = pathList.get(i);
                if (prefs.getInt(path + "_plays", 0) > 0) {
                    filteredTitles.add(songList.get(i));
                    filteredPaths.add(path);
                }
            }
        } else {
            filteredTitles.addAll(songList);
            filteredPaths.addAll(pathList);
        }

        if (adapter == null) {
            adapter = new SongAdapter(this, filteredTitles);
            gridView.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
        }

        updateLibraryStats();
        updateEmptyState();
    }

    private void filterSongs(String text) {
        if (text.isEmpty()) {
            refreshList();
            return;
        }
        ArrayList<String> baseTitles = new ArrayList<String>();
        ArrayList<String> basePaths = new ArrayList<String>();
        if (currentTab == 2) {
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            Set<String> favorites = prefs.getStringSet("favorites", new HashSet<String>());
            for (int i = 0; i < songList.size(); i++) {
                if (favorites.contains(pathList.get(i))) {
                    baseTitles.add(songList.get(i));
                    basePaths.add(pathList.get(i));
                }
            }
        } else {
            baseTitles.addAll(songList);
            basePaths.addAll(pathList);
        }
        filteredTitles.clear();
        filteredPaths.clear();
        for (int i = 0; i < baseTitles.size(); i++) {
            if (baseTitles.get(i).toLowerCase().contains(text.toLowerCase())) {
                filteredTitles.add(baseTitles.get(i));
                filteredPaths.add(basePaths.get(i));
            }
        }
        if (adapter != null) adapter.notifyDataSetChanged();

        // Live result count feedback while typing
        int count = filteredTitles.size();
        txtEmptyStateMessage.setText("No results for \"" + text + "\"");
        updateEmptyState();
        if (count > 0) {
            Toast.makeText(this, count + (count == 1 ? " result" : " results"), Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- FEATURE: Empty state + live library stats ----------

    private void updateEmptyState() {
        if (emptyStateView == null) return;
        boolean isEmpty = filteredTitles.isEmpty();
        if (isEmpty) {
            if (!isSearchActive) {
                txtEmptyStateMessage.setText(currentTab == 2 ? "No favorites yet" :
                        currentTab == 3 ? "No smart picks yet - play some songs first" : "No songs found");
            }
            if (emptyStateView.getVisibility() != View.VISIBLE) {
                emptyStateView.setVisibility(View.VISIBLE);
                emptyStateView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.empty_state_in));
            }
        } else {
            emptyStateView.setVisibility(View.GONE);
        }
    }

    private void updateLibraryStats() {
        if (txtLibraryStats == null) return;
        int totalSongs = songList.size();
        int totalFolders;
        HashSet<String> folderSet = new HashSet<String>();
        for (String p : pathList) {
            File f = new File(p);
            if (f.getParent() != null) folderSet.add(f.getParent());
        }
        totalFolders = folderSet.size();
        txtLibraryStats.setText(totalSongs + (totalSongs == 1 ? " song" : " songs") + " \u00b7 " +
                totalFolders + (totalFolders == 1 ? " folder" : " folders"));

        if (dotNowPlaying != null) {
            boolean nowPlaying = MusicService.currentTitle != null
                    && !MusicService.currentTitle.isEmpty()
                    && MusicService.isPlaying;
            dotNowPlaying.setVisibility(nowPlaying ? View.VISIBLE : View.GONE);
        }
    }

    private void playListEntranceAnimation() {
        if (gridView != null) {
            gridView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.list_item_in));
        }
    }

    @Override
    public void onBackPressed() {
        if (isSearchActive) {
            closeSearch();
        } else if (currentTab == 1 && isInsideFolder) {
            isInsideFolder = false;
            refreshList();
        } else if (currentTab != 0) {
            currentTab = 0;
            updateTabUI();
            refreshList();
        } else {
            super.onBackPressed();
        }
    }

    private void playMusic(int position) {
        if (position < 0 || position >= filteredPaths.size()) return;
        String selectedPath = filteredPaths.get(position);

        // --- SMART TRACKING ---
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int plays = prefs.getInt(selectedPath + "_plays", 0);
        prefs.edit()
             .putInt(selectedPath + "_plays", plays + 1)
             .putLong(selectedPath + "_lastPlayed", System.currentTimeMillis())
             .apply();

        Intent serviceIntent = new Intent(MainActivity.this, MusicService.class);
        serviceIntent.setAction(MusicService.ACTION_PLAY_NEW);
        serviceIntent.putStringArrayListExtra("paths", filteredPaths);
        serviceIntent.putStringArrayListExtra("titles", filteredTitles);
        serviceIntent.putExtra("index", position);
        startService(serviceIntent);

        MusicService.isPlaying = true;
        saveLastPlayedState();
        updateMiniPlayerUI();
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    /**
     * Moves to the next/previous tab in TAB_ORDER, wrapping at the ends.
     * Used by the left/right swipe gesture on the song list; tapping a
     * tab directly still works exactly as before and calls goToTab too.
     */
    private void swipeToAdjacentTab(boolean forward) {
        // While drilled into a folder, a swipe should back out to the
        // folder list first rather than immediately jumping tabs — this
        // matches what the back button already does.
        if (currentTab == 1 && isInsideFolder) {
            isInsideFolder = false;
            refreshList();
            return;
        }

        int index = indexOfTab(currentTab);
        int nextIndex = forward ? index + 1 : index - 1;
        if (nextIndex < 0) nextIndex = TAB_ORDER.length - 1;
        if (nextIndex >= TAB_ORDER.length) nextIndex = 0;

        goToTab(TAB_ORDER[nextIndex]);
    }

    private int indexOfTab(int tab) {
        for (int i = 0; i < TAB_ORDER.length; i++) {
            if (TAB_ORDER[i] == tab) return i;
        }
        return 0;
    }

    /** Single entry point for switching tabs, whether by tap or by swipe. */
    private void goToTab(int tab) {
        if (currentTab == tab && !isInsideFolder) return;
        currentTab = tab;
        isInsideFolder = false;
        updateTabUI();
        refreshList();
    }

    /** Same as goToTab, but always re-applies even if already on that tab (used for direct taps). */
    private void goToTabForced(int tab) {
        currentTab = tab;
        isInsideFolder = false;
        updateTabUI();
        refreshList();
    }

    private void updateTabUI() {
        ThemeEngine theme = ThemeEngine.get(this);
        int accent = theme.accent;
        int inactive = theme.textSecondary;
        btnTabAll.setTextColor(currentTab == 0 ? accent : inactive);
        btnTabFolder.setTextColor(currentTab == 1 ? accent : inactive);
        btnTabFav.setTextColor(currentTab == 2 ? accent : inactive);
        btnTabSmart.setTextColor(currentTab == 3 ? accent : inactive);
        tintTabIcon(btnTabAll, currentTab == 0 ? accent : inactive);
        tintTabIcon(btnTabFolder, currentTab == 1 ? accent : inactive);
        tintTabIcon(btnTabFav, currentTab == 2 ? accent : inactive);
        tintTabIcon(btnTabSmart, currentTab == 3 ? accent : inactive);
        moveTabIndicator();
    }

    /**
     * Tints the "drawableTop" compound icon of a tab label to match its
     * current text color (active = accent, inactive = secondary). Runtime
     * tinting is used instead of AndroidX's DrawableCompat/tint-list APIs
     * since this project stays on the plain framework; each tab icon is a
     * flat white vector, so PorterDuff SRC_IN is enough to recolor it.
     */
    private void tintTabIcon(TextView tab, int color) {
        Drawable[] drawables = tab.getCompoundDrawables();
        Drawable top = drawables[1]; // index 1 = "top" position
        if (top != null) {
            top.mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN);
        }
    }

    private void moveTabIndicator() {
        if (tabIndicator == null) return;
        TextView target = btnTabAll;
        if (currentTab == 1) target = btnTabFolder;
        else if (currentTab == 2) target = btnTabFav;
        else if (currentTab == 3) target = btnTabSmart;

        final TextView finalTarget = target;
        tabIndicator.post(new Runnable() {
            @Override
            public void run() {
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tabIndicator.getLayoutParams();
                lp.width = finalTarget.getWidth();
                tabIndicator.setLayoutParams(lp);
                tabIndicator.animate().translationX(finalTarget.getLeft()).setDuration(220).start();
            }
        });
    }

    private void applyDynamicColors() {
        ThemeEngine.applyToActivity(this);
        ThemeEngine theme = ThemeEngine.get(this);

        if (gridView != null) gridView.setBackgroundColor(theme.background);

        // Root background (behind everything)
        View root = findViewById(android.R.id.content);
        if (root != null) root.setBackgroundColor(theme.background);

        // Tab card (the strip that holds the pill) + the pill track itself
        View tabBarWrapView = findViewById(R.id.tabBarWrap);
        if (tabBarWrapView != null) tabBarWrapView.setBackgroundColor(theme.background);

        View tabBarView = findViewById(R.id.tabBar);
        if (tabBarView != null && tabBarView.getParent() instanceof View) {
            View pillTrack = (View) tabBarView.getParent(); // the FrameLayout with tab_bar_track bg
            if (pillTrack.getBackground() != null) {
                pillTrack.getBackground().setColorFilter(theme.surface, android.graphics.PorterDuff.Mode.SRC_IN);
            }
        }

        View dividerView = findViewById(R.id.divider);
        if (dividerView != null) dividerView.setBackgroundColor(theme.divider);
        updateTabUI(); // re-applies accent/secondary text colors on tab labels

        // Mini player card + text
        if (miniPlayer != null) {
            // Keep the rounded pill drawable but recolor via tint-safe background swap
            miniPlayer.getBackground().setColorFilter(theme.surface, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (txtMiniTitle != null) txtMiniTitle.setTextColor(theme.textPrimary);

        // Empty state icon backdrop + text
        View emptyIconBg = findViewById(R.id.emptyStateIconBg);
        if (emptyIconBg != null && emptyIconBg.getBackground() != null) {
            emptyIconBg.getBackground().setColorFilter(theme.surface, android.graphics.PorterDuff.Mode.SRC_IN);
        }
        if (txtEmptyStateMessage != null) txtEmptyStateMessage.setTextColor(theme.textSecondary);

        if (adapter != null) adapter.notifyDataSetChanged();
    }

    private void showCustomOptionsDialog(final int position) {
        if (position < 0 || position >= filteredPaths.size()) return;
        final String songPath = filteredPaths.get(position);
        final String songTitle = filteredTitles.get(position);
        final File file = new File(songPath);

        final AlertDialog dialog = new AlertDialog.Builder(this).create();
        View dialogView = getLayoutInflater().inflate(R.layout.custom_dialog, null);
        dialog.setView(dialogView);

        TextView txtTitle = (TextView) dialogView.findViewById(R.id.dialogTitle);
        TextView menuRename = (TextView) dialogView.findViewById(R.id.menuRename);
        TextView menuShare = (TextView) dialogView.findViewById(R.id.menuShare);
        TextView menuFavorite = (TextView) dialogView.findViewById(R.id.menuFavorite);
        TextView menuDelete = (TextView) dialogView.findViewById(R.id.menuDelete);
        TextView menuPlayNext = (TextView) dialogView.findViewById(R.id.menuPlayNext);
        TextView menuAddToQueue = (TextView) dialogView.findViewById(R.id.menuAddToQueue);

        txtTitle.setText(songTitle);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> favorites = prefs.getStringSet("favorites", new HashSet<String>());
        final boolean isFav = favorites.contains(songPath);
        menuFavorite.setText(isFav ? "Remove from Favorites" : "Add to Favorites");

        menuPlayNext.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MusicService.class);
                i.setAction(MusicService.ACTION_PLAY_NEXT);
                i.putExtra("path", songPath);
                i.putExtra("title", songTitle);
                startService(i);
                Toast.makeText(MainActivity.this, "Play Next: " + songTitle, Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        menuAddToQueue.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent i = new Intent(MainActivity.this, MusicService.class);
                i.setAction(MusicService.ACTION_ADD_TO_QUEUE);
                i.putExtra("path", songPath);
                i.putExtra("title", songTitle);
                startService(i);
                Toast.makeText(MainActivity.this, "Added to Queue", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        menuRename.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                showRenameDialog(file, songTitle);
            }
        });

        menuShare.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("audio/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                    startActivity(Intent.createChooser(shareIntent, "Share Music"));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Sharing failed", Toast.LENGTH_SHORT).show();
                }
                dialog.dismiss();
            }
        });

        menuFavorite.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Set<String> newFavs = new HashSet<String>(prefs.getStringSet("favorites", new HashSet<String>()));
                if (isFav) newFavs.remove(songPath);
                else newFavs.add(songPath);
                prefs.edit().putStringSet("favorites", newFavs).apply();
                dialog.dismiss();
                refreshList();
            }
        });

        menuDelete.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                dialog.dismiss();
                confirmDelete(file);
            }
        });
        dialog.show();
    }

    private void showRenameDialog(final File file, String oldName) {
        final EditText input = new EditText(this);
        input.setText(oldName);
        new AlertDialog.Builder(this).setTitle("Rename").setView(input).setPositiveButton("Rename", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String newName = input.getText().toString().trim();
                if (newName.isEmpty()) return;
                String extension = "";
                int i = file.getName().lastIndexOf('.');
                if (i > 0) extension = file.getName().substring(i);
                File newFile = new File(file.getParent(), newName + extension);
                if (file.renameTo(newFile)) {
                    refreshMediaStore(file, newFile);
                } else {
                    Toast.makeText(MainActivity.this, "Rename failed - check storage permission", Toast.LENGTH_SHORT).show();
                }
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void confirmDelete(final File file) {
        new AlertDialog.Builder(this).setTitle("Delete").setMessage("Delete permanently?").setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (file.delete()) {
                    MediaScannerConnection.scanFile(MainActivity.this, new String[]{file.getAbsolutePath()}, null, null);
                    checkAllPermissions();
                } else {
                    Toast.makeText(MainActivity.this, "Delete failed - check storage permission", Toast.LENGTH_SHORT).show();
                }
            }
        }).setNegativeButton("No", null).show();
    }

    private void refreshMediaStore(File oldFile, File newFile) {
        MediaScannerConnection.scanFile(MainActivity.this,
                new String[]{oldFile.getAbsolutePath(), newFile.getAbsolutePath()},
                null, null);
        checkAllPermissions();
    }

    private void loadPreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        isGridView = prefs.getBoolean("pref_grid_default", false);
        applyViewFormat();
        String sortPref = prefs.getString("pref_sort_default", "title");
        if (sortPref.equals("artist")) currentSortOrder = MediaStore.Audio.Media.ARTIST + " ASC";
        else if (sortPref.equals("date")) currentSortOrder = MediaStore.Audio.Media.DATE_ADDED + " DESC";
        else if (sortPref.equals("size")) currentSortOrder = MediaStore.Audio.Media.SIZE + " DESC";
        else currentSortOrder = MediaStore.Audio.Media.TITLE + " ASC";
    }

    private void applyViewFormat() {
        if (isGridView) gridView.setNumColumns(2);
        else gridView.setNumColumns(1);
        btnViewToggle.setImageResource(isGridView ? R.drawable.view_list : R.drawable.view_module);
    }

    private void checkAllPermissions() {
        String perm = (Build.VERSION.SDK_INT >= 33) ? "android.permission.READ_MEDIA_AUDIO" : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, 100);
        } else {
            loadMusicData();
        }
    }

    /**
     * Opens the MUZIKI Video library. On API 33+, video access is its own
     * granular permission (READ_MEDIA_VIDEO) separate from audio, so this
     * is requested independently rather than piggy-backing on the audio
     * permission flow above - a user who only grants audio access
     * shouldn't be silently blocked from ever seeing the video request.
     */
    private void openVideoLibrary() {
        String perm = (Build.VERSION.SDK_INT >= 33) ? "android.permission.READ_MEDIA_VIDEO" : android.Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{perm}, 101);
        } else {
            startActivity(new Intent(MainActivity.this, VideoListActivity.class));
        }
    }

    private void loadMusicData() {
        final ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Updating library...");
        pd.setCancelable(false);
        pd.show();

        loadMusicDataSilently(new Runnable() {
            @Override
            public void run() {
                if (isFinishing()) return;
                try { pd.dismiss(); } catch (Exception e) { /* activity may be gone */ }
            }
        });
    }

    /** Rescans MediaStore without showing the blocking ProgressDialog - used by pull-to-refresh. */
    private void loadMusicDataSilently(final Runnable onComplete) {
        final Handler handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (isFinishing()) return;
                refreshList();
                updateMiniPlayerUI();
                applyDynamicColors();
                playListEntranceAnimation();
                if (onComplete != null) onComplete.run();
            }
        };

        new Thread(new Runnable() {
            @Override
            public void run() {
                ArrayList<String> newSongs = new ArrayList<String>();
                ArrayList<String> newPaths = new ArrayList<String>();
                ArrayList<String> newAlbums = new ArrayList<String>();
                ContentResolver resolver = getContentResolver();
                Cursor cursor = resolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null,
                        MediaStore.Audio.Media.IS_MUSIC + "!= 0", null, currentSortOrder);
                if (cursor != null) {
                    int ti = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                    int di = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                    int ai = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                    while (cursor.moveToNext()) {
                        newSongs.add(cursor.getString(ti));
                        newPaths.add(cursor.getString(di));
                        newAlbums.add(cursor.getString(ai));
                    }
                    cursor.close();
                }
                songList = newSongs;
                pathList = newPaths;
                albumList = newAlbums;
                handler.sendEmptyMessage(0);
            }
        }).start();
    }

    private void updateMiniPlayerUI() {
        if (MusicService.currentTitle != null && !MusicService.currentTitle.isEmpty()) {
            miniPlayer.setVisibility(View.VISIBLE);
            txtMiniTitle.setText(MusicService.currentTitle);
            updatePlayPauseIcon();
        } else {
            miniPlayer.setVisibility(View.GONE);
        }
        if (dotNowPlaying != null) {
            boolean nowPlaying = MusicService.currentTitle != null
                    && !MusicService.currentTitle.isEmpty()
                    && MusicService.isPlaying;
            dotNowPlaying.setVisibility(nowPlaying ? View.VISIBLE : View.GONE);
        }
    }

    private void updatePlayPauseIcon() {
        btnMiniPlayPause.setImageResource(MusicService.isPlaying ? R.drawable.pauses : R.drawable.plays);
    }

    @Override
    protected void onResume() {
        super.onResume();
        stampAppOpenedNow();
        applyDynamicColors();
        updateMiniPlayerUI();
        updateTabUI();
        // Cheap re-scan so changes made elsewhere (e.g. Settings > Clean Up
        // Broken Files) are reflected the moment the user comes back here,
        // without needing to force-close and reopen the app.
        loadMusicDataSilently(null);
        if (Build.VERSION.SDK_INT >= 33) {
            // 4 == Context.RECEIVER_NOT_EXPORTED. Using the raw value instead of
            // the constant so this still compiles even if the local machine's
            // installed Android SDK Platform is older than 33/34.
            registerReceiver(statusReceiver, new IntentFilter("SONG_CHANGED"), 4);
        } else {
            registerReceiver(statusReceiver, new IntentFilter("SONG_CHANGED"));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(statusReceiver); } catch (Exception e) { /* not registered */ }
    }

    private class SongAdapter extends BaseAdapter {
        private Context context;
        private ArrayList<String> titles;
        public SongAdapter(Context context, ArrayList<String> titles) {
            this.context = context;
            this.titles = titles;
        }
        @Override public int getCount() { return titles.size(); }
        @Override public Object getItem(int position) { return titles.get(position); }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
            TextView txtTitle = (TextView) convertView.findViewById(R.id.song_title);
            TextView txtSub = (TextView) convertView.findViewById(R.id.song_artist);
            ImageView imgIcon = (ImageView) convertView.findViewById(R.id.imgMusicIcon);

            ThemeEngine theme = ThemeEngine.get(context);
            int mainTextColor = theme.textPrimary;
            int subTextColor = theme.textSecondary;

            String itemText = titles.get(position);
            txtTitle.setText(itemText);

            if (currentTab == 1 && !isInsideFolder) {
                int count = (position < folderSongCount.size()) ? folderSongCount.get(position) : 0;
                txtSub.setText(count + (count == 1 ? " Song" : " Songs"));
                imgIcon.setImageResource(R.drawable.ic_folder_album);
                imgIcon.clearColorFilter();
                imgIcon.clearAnimation();
                txtTitle.setTextColor(mainTextColor);
                txtSub.setTextColor(subTextColor);
            } else {
                boolean isCurrent = MusicService.currentTitle != null && MusicService.currentTitle.equals(itemText);
                txtTitle.setTextColor(isCurrent ? theme.accent : mainTextColor);
                txtSub.setTextColor(isCurrent ? theme.accent : subTextColor);
                txtSub.setText(isCurrent ? "Now Playing" : "Audio File");
                imgIcon.setImageResource(R.drawable.voice_note);
                if (isCurrent) {
                    imgIcon.setColorFilter(theme.accent);
                    if (MusicService.isPlaying) imgIcon.startAnimation(AnimationUtils.loadAnimation(context, R.anim.pulse));
                    else imgIcon.clearAnimation();
                } else {
                    imgIcon.clearColorFilter();
                    imgIcon.clearAnimation();
                }
            }
            return convertView;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadMusicData();
        } else if (requestCode == 101) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(MainActivity.this, VideoListActivity.class));
            } else {
                Toast.makeText(this, "Video access is needed to watch videos in Muziki", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
