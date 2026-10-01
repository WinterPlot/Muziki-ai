package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * MUZIKI Video — video library screen.
 *
 * Lists every playable video already on the device (scanned via
 * VideoLibraryHelper/MediaStore.Video) so the user can start watching
 * instantly and for free — no streaming, no account, no ads, no waiting
 * on a network connection. Tapping a row opens VideoPlayerActivity, the
 * native local player with full transport control (seek, 10s skip,
 * speed, fullscreen).
 */
public class VideoListActivity extends AppCompatActivity {

    private ListView videoListView;
    private ProgressBar scanProgress;
    private View emptyState;
    private TextView txtVideoCount;
    private TextView txtEmptyTitle, txtEmptySubtitle;
    private EditText editVideoSearch;
    private ImageButton btnClearVideoSearch;

    // The full, unfiltered library from the last scan.
    private final ArrayList<VideoLibraryHelper.VideoItem> allVideos = new ArrayList<VideoLibraryHelper.VideoItem>();
    // The subset currently bound to the adapter/list - either allVideos
    // unchanged, or whatever matches the current search query.
    private final ArrayList<VideoLibraryHelper.VideoItem> videos = new ArrayList<VideoLibraryHelper.VideoItem>();
    // Caches decoded thumbnails per video path so scrolling the list back
    // and forth doesn't re-decode the same frame over and over.
    private final HashMap<String, Bitmap> thumbCache = new HashMap<String, Bitmap>();

    private VideoAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_list);

        videoListView = (ListView) findViewById(R.id.videoListView);
        scanProgress = (ProgressBar) findViewById(R.id.videoScanProgress);
        emptyState = findViewById(R.id.videoEmptyState);
        txtVideoCount = (TextView) findViewById(R.id.txtVideoCount);
        txtEmptyTitle = (TextView) findViewById(R.id.txtEmptyTitle);
        txtEmptySubtitle = (TextView) findViewById(R.id.txtEmptySubtitle);
        editVideoSearch = (EditText) findViewById(R.id.editVideoSearch);
        btnClearVideoSearch = (ImageButton) findViewById(R.id.btnClearVideoSearch);

        ImageButton btnBack = (ImageButton) findViewById(R.id.btnVideoListBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        adapter = new VideoAdapter();
        videoListView.setAdapter(adapter);

        videoListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                VideoLibraryHelper.VideoItem item = videos.get(position);
                Intent intent = new Intent(VideoListActivity.this, VideoPlayerActivity.class);
                intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_PATH, item.path);
                intent.putExtra(VideoPlayerActivity.EXTRA_VIDEO_TITLE, item.title);
                startActivity(intent);
            }
        });

        setupSearch();
        loadVideos();
    }

    /**
     * Live, case-insensitive filter over the video title as the user
     * types - matches the search behavior already used for songs in
     * MainActivity, so it feels consistent across the app.
     */
    private void setupSearch() {
        editVideoSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnClearVideoSearch.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                applySearchFilter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnClearVideoSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editVideoSearch.setText("");
            }
        });
    }

    private void applySearchFilter(String query) {
        String needle = query.trim().toLowerCase(Locale.US);

        videos.clear();
        if (needle.length() == 0) {
            videos.addAll(allVideos);
        } else {
            for (VideoLibraryHelper.VideoItem item : allVideos) {
                if (item.title != null && item.title.toLowerCase(Locale.US).contains(needle)) {
                    videos.add(item);
                }
            }
        }
        adapter.notifyDataSetChanged();
        updateListVisibility(needle.length() > 0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-scan in case a video was deleted/added elsewhere while this
        // screen was backgrounded (e.g. via file manager, or the user
        // recorded something new with the camera app).
        if (!allVideos.isEmpty()) {
            loadVideos();
        }
    }

    private void loadVideos() {
        VideoLibraryHelper.scanVideos(this, new VideoLibraryHelper.VideoScanCallback() {
            @Override
            public void onScanComplete(ArrayList<VideoLibraryHelper.VideoItem> result) {
                if (isFinishing()) return;

                allVideos.clear();
                allVideos.addAll(result);

                String currentQuery = editVideoSearch.getText().toString();
                applySearchFilter(currentQuery);

                scanProgress.setVisibility(View.GONE);
            }
        });
    }

    /**
     * Updates list/empty-state visibility for either the full library or
     * a search result set. isSearching distinguishes "no videos on this
     * device at all" from "no videos match this search" so the empty
     * state message and search bar don't look contradictory.
     */
    private void updateListVisibility(boolean isSearching) {
        if (allVideos.isEmpty()) {
            videoListView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            txtEmptyTitle.setText("No videos found on this device");
            txtEmptySubtitle.setText("Videos you save to your device will show up here");
            txtVideoCount.setText("");
            return;
        }

        if (videos.isEmpty()) {
            videoListView.setVisibility(View.GONE);
            emptyState.setVisibility(View.VISIBLE);
            txtEmptyTitle.setText("No matching videos");
            txtEmptySubtitle.setText("Try a different search term");
            txtVideoCount.setText("0 results");
        } else {
            videoListView.setVisibility(View.VISIBLE);
            emptyState.setVisibility(View.GONE);
            if (isSearching) {
                txtVideoCount.setText(videos.size() == 1 ? "1 result" : (videos.size() + " results"));
            } else {
                txtVideoCount.setText(videos.size() == 1 ? "1 video" : (videos.size() + " videos"));
            }
        }
    }

    /**
     * Plain ArrayAdapter-style adapter (no RecyclerView dependency, kept
     * consistent with the rest of this no-AndroidX project) that recycles
     * item_video.xml rows and lazily loads thumbnails per row.
     */
    private class VideoAdapter extends ArrayAdapter<VideoLibraryHelper.VideoItem> {

        VideoAdapter() {
            super(VideoListActivity.this, R.layout.item_video, videos);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = getLayoutInflater().inflate(R.layout.item_video, parent, false);
            }

            final VideoLibraryHelper.VideoItem item = videos.get(position);

            TextView title = (TextView) row.findViewById(R.id.txtVideoTitle);
            TextView meta = (TextView) row.findViewById(R.id.txtVideoMeta);
            TextView duration = (TextView) row.findViewById(R.id.txtVideoDuration);
            final ImageView thumb = (ImageView) row.findViewById(R.id.imgVideoThumb);

            title.setText(item.title);
            meta.setText(item.getFormattedSize());
            duration.setText(item.getFormattedDuration());

            // Tag the ImageView with the path it currently represents so
            // that if the row gets recycled before the async thumbnail
            // finishes loading, we don't paint the wrong video's frame
            // onto a reused row.
            thumb.setTag(item.path);

            Bitmap cached = thumbCache.get(item.path);
            if (cached != null) {
                thumb.setImageBitmap(cached);
            } else {
                thumb.setImageResource(R.drawable.ic_video_library);
                VideoLibraryHelper.loadThumbnail(VideoListActivity.this, item, new VideoLibraryHelper.ThumbnailCallback() {
                    @Override
                    public void onThumbnailReady(Bitmap bitmap) {
                        if (bitmap == null || isFinishing()) return;
                        thumbCache.put(item.path, bitmap);
                        if (item.path.equals(thumb.getTag())) {
                            thumb.setImageBitmap(bitmap);
                        }
                    }
                });
            }

            return row;
        }
    }
}
