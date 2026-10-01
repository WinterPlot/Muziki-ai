package com.mayuto.dev.muziki;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.MediaStore;

import java.util.ArrayList;

/**
 * MUZIKI Video — on-device video library.
 *
 * Scans MediaStore.Video the same way MainActivity already scans
 * MediaStore.Audio, so watching a video already on the device is
 * instant and fully offline — no buffering, no data plan, no ads or
 * paywalled "watch faster with a subscription" friction some streaming
 * apps add. It's just your own files, played back with full local
 * control (seek, speed, fullscreen) through VideoPlayerActivity.
 */
public final class VideoLibraryHelper {

    private VideoLibraryHelper() {
    }

    public static final class VideoItem {
        public final long id;
        public final String title;
        public final String path;
        public final long durationMs;
        public final long sizeBytes;

        public VideoItem(long id, String title, String path, long durationMs, long sizeBytes) {
            this.id = id;
            this.title = title;
            this.path = path;
            this.durationMs = durationMs;
            this.sizeBytes = sizeBytes;
        }

        public String getFormattedDuration() {
            long totalSeconds = durationMs / 1000L;
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            long hours = minutes / 60L;
            minutes = minutes % 60L;
            if (hours > 0) {
                return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
        }

        public String getFormattedSize() {
            double mb = sizeBytes / (1024.0 * 1024.0);
            if (mb >= 1024) {
                return String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0);
            }
            return String.format(java.util.Locale.US, "%.0f MB", mb);
        }
    }

    public interface VideoScanCallback {
        void onScanComplete(ArrayList<VideoItem> videos);
    }

    /** Scans MediaStore.Video off the main thread and returns every playable video found. */
    public static void scanVideos(final Context context, final VideoScanCallback callback) {
        final Context appContext = context.getApplicationContext();

        new AsyncTask<Void, Void, ArrayList<VideoItem>>() {
            @Override
            protected ArrayList<VideoItem> doInBackground(Void... voids) {
                ArrayList<VideoItem> results = new ArrayList<VideoItem>();
                ContentResolver resolver = appContext.getContentResolver();

                Cursor cursor = resolver.query(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        null,
                        null,
                        null,
                        MediaStore.Video.Media.DATE_ADDED + " DESC");

                if (cursor != null) {
                    int idIdx = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                    int titleIdx = cursor.getColumnIndex(MediaStore.Video.Media.TITLE);
                    int dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA);
                    int durationIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION);
                    int sizeIdx = cursor.getColumnIndex(MediaStore.Video.Media.SIZE);

                    while (cursor.moveToNext()) {
                        String path = dataIdx >= 0 ? cursor.getString(dataIdx) : null;
                        if (path == null || path.length() == 0) continue;

                        java.io.File file = new java.io.File(path);
                        if (!file.exists()) continue; // skip ghost/broken rows, same rule as audio cleanup

                        long id = idIdx >= 0 ? cursor.getLong(idIdx) : -1L;
                        String title = titleIdx >= 0 ? cursor.getString(titleIdx) : file.getName();
                        long duration = durationIdx >= 0 ? cursor.getLong(durationIdx) : 0L;
                        long size = sizeIdx >= 0 ? cursor.getLong(sizeIdx) : file.length();

                        results.add(new VideoItem(id, title, path, duration, size));
                    }
                    cursor.close();
                }

                return results;
            }

            @Override
            protected void onPostExecute(ArrayList<VideoItem> result) {
                if (callback != null) {
                    callback.onScanComplete(result);
                }
            }
        }.execute();
    }

    /**
     * Loads a small thumbnail bitmap for a video, off the main thread.
     * Uses MediaStore's cached thumbnail on older APIs and
     * ThumbnailUtils.createVideoThumbnail directly as a fallback so a
     * thumbnail still shows even for a freshly-added file MediaStore
     * hasn't cached a thumbnail for yet.
     */
    public interface ThumbnailCallback {
        void onThumbnailReady(Bitmap bitmap);
    }

    public static void loadThumbnail(final Context context, final VideoItem item, final ThumbnailCallback callback) {
        final Context appContext = context.getApplicationContext();

        new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... voids) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        Uri uri = Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, String.valueOf(item.id));
                        return appContext.getContentResolver().loadThumbnail(
                                uri, new android.util.Size(240, 240), null);
                    } else {
                        return MediaStore.Video.Thumbnails.getThumbnail(
                                appContext.getContentResolver(), item.id,
                                MediaStore.Video.Thumbnails.MINI_KIND, null);
                    }
                } catch (Exception e) {
                    try {
                        return ThumbnailUtils.createVideoThumbnail(item.path, MediaStore.Video.Thumbnails.MINI_KIND);
                    } catch (Exception ignored) {
                        return null;
                    }
                }
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                if (callback != null) {
                    callback.onThumbnailReady(bitmap);
                }
            }
        }.execute();
    }
}
