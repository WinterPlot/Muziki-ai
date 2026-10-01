package com.mayuto.dev.muziki;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.MediaStore;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Broken / missing file cleanup.
 *
 * MUZIKI's library is built from MediaStore, which does not automatically
 * drop a row when the underlying file is deleted, moved, or was on
 * removable storage that's no longer mounted. Over time this leaves
 * "ghost" tracks that show up in the library but fail to play. This
 * helper scans every audio row MUZIKI knows about, checks whether the
 * file actually still exists on disk, and if not:
 *
 *  1. Best-effort deletes the stale row from MediaStore so other apps
 *     querying the same provider see a clean result too.
 *  2. Removes the path from the favorites set, so a ghost track can't
 *     linger in the Favorites tab either.
 *
 * Runs off the main thread (AsyncTask) since it walks the whole library
 * and touches disk for every single track.
 */
public final class LibraryCleanupHelper {

    private LibraryCleanupHelper() {
    }

    public interface CleanupCallback {
        /** Called on the main thread once the scan (and removal) finishes. */
        void onCleanupFinished(CleanupResult result);
    }

    public static final class CleanupResult {
        public final int scanned;
        public final int removed;
        public final ArrayList<String> removedTitles;

        CleanupResult(int scanned, int removed, ArrayList<String> removedTitles) {
            this.scanned = scanned;
            this.removed = removed;
            this.removedTitles = removedTitles;
        }
    }

    /**
     * Scans the whole MediaStore audio library on a background thread.
     * If dryRun is true, nothing is deleted — the result just reports
     * how many broken entries were found, so the caller can show a
     * confirmation dialog first.
     */
    public static void scanAndClean(final Context context, final boolean dryRun, final CleanupCallback callback) {
        final Context appContext = context.getApplicationContext();

        new AsyncTask<Void, Void, CleanupResult>() {
            @Override
            protected CleanupResult doInBackground(Void... voids) {
                ContentResolver resolver = appContext.getContentResolver();
                ArrayList<String> brokenPaths = new ArrayList<String>();
                ArrayList<Long> brokenIds = new ArrayList<Long>();
                ArrayList<String> brokenTitles = new ArrayList<String>();
                int scanned = 0;

                Cursor cursor = resolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        null,
                        MediaStore.Audio.Media.IS_MUSIC + " != 0",
                        null,
                        null);

                if (cursor != null) {
                    int idIdx = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                    int dataIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                    int titleIdx = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);

                    while (cursor.moveToNext()) {
                        scanned++;
                        String path = dataIdx >= 0 ? cursor.getString(dataIdx) : null;
                        if (path == null || path.length() == 0) {
                            continue;
                        }
                        File file = new File(path);
                        if (!file.exists()) {
                            brokenPaths.add(path);
                            brokenIds.add(idIdx >= 0 ? cursor.getLong(idIdx) : -1L);
                            brokenTitles.add(titleIdx >= 0 ? cursor.getString(titleIdx) : path);
                        }
                    }
                    cursor.close();
                }

                if (!dryRun && !brokenPaths.isEmpty()) {
                    // Best-effort delete of stale MediaStore rows.
                    for (Long id : brokenIds) {
                        if (id == null || id < 0) continue;
                        try {
                            Uri rowUri = Uri.withAppendedPath(
                                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                            resolver.delete(rowUri, null, null);
                        } catch (Exception ignored) {
                            // Some OEM providers restrict deletes; the row
                            // just stays as-is and will be filtered out by
                            // MainActivity's normal existence checks.
                        }
                    }

                    // Clean the same paths out of Favorites.
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(appContext);
                    Set<String> favorites = prefs.getStringSet("favorites", new HashSet<String>());
                    Set<String> updatedFavorites = new HashSet<String>(favorites);
                    boolean favoritesChanged = updatedFavorites.removeAll(brokenPaths);
                    if (favoritesChanged) {
                        prefs.edit().putStringSet("favorites", updatedFavorites).apply();
                    }
                }

                return new CleanupResult(scanned, brokenPaths.size(), brokenTitles);
            }

            @Override
            protected void onPostExecute(CleanupResult result) {
                if (callback != null) {
                    callback.onCleanupFinished(result);
                }
            }
        }.execute();
    }
}
