package com.mayuto.dev.muziki;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Build;
import android.widget.RemoteViews;

/**
 * Shared painting logic for all 3 home-screen widget sizes (small / medium / large).
 * Each size has its own AppWidgetProvider subclass (Android requires a distinct
 * receiver+manifest entry per widget), but they all render from the exact same
 * live MusicService state through this one class, so "accurate" only has to be
 * gotten right in one place.
 */
final class WidgetUpdateHelper {

    private WidgetUpdateHelper() { }

    /**
     * Paints one widget instance of the given layout into appWidgetId.
     * playPauseId/prevId/nextId/titleId/artId/artistId let each layout use its
     * own view ids; pass 0 for any view a given layout doesn't have (e.g. the
     * small widget has no title/artist text views).
     */
    static void update(Context context, AppWidgetManager manager, int appWidgetId, int layoutRes,
                        int artId, int titleId, int artistId, int playPauseId, int prevId, int nextId) {

        RemoteViews views = new RemoteViews(context.getPackageName(), layoutRes);

        boolean hasSong = MusicService.currentSourcePath != null && !MusicService.currentSourcePath.isEmpty();
        boolean isPlaying = MusicService.isPlaying;

        if (titleId != 0) {
            String title = hasSong ? MusicService.currentTitle : "No song playing";
            if (title == null || title.isEmpty()) title = "No song playing";
            views.setTextViewText(titleId, title);
        }

        if (artistId != 0) {
            views.setTextViewText(artistId, hasSong ? "muziki" : "Tap to open muziki");
        }

        if (playPauseId != 0) {
            // Accurate state: only show the "pause" glyph when something is actually playing.
            // With no song loaded at all, still show "play" (not a spinner/blank) so the
            // button always has an obvious, tappable meaning.
            int icon = isPlaying ? R.drawable.pause : R.drawable.play;
            views.setImageViewResource(playPauseId, icon);
        }

        if (artId != 0) {
            Bitmap art = hasSong ? resolveAlbumArt() : null;
            if (art != null) {
                views.setImageViewBitmap(artId, art);
            } else {
                views.setImageViewResource(artId, R.drawable.voice_note);
            }
        }

        // Tapping art/title opens the app - always wired, whether or not a song is loaded.
        PendingIntent openApp = buildActivityPendingIntent(context);
        if (titleId != 0) views.setOnClickPendingIntent(titleId, openApp);
        if (artId != 0) views.setOnClickPendingIntent(artId, openApp);

        // Controls always call the service directly with the CURRENT correct action;
        // MusicService itself no-ops safely if there's nothing to play/pause/skip.
        if (prevId != 0) {
            views.setOnClickPendingIntent(prevId, buildServicePendingIntent(context, MusicService.ACTION_PREV, appWidgetId * 10 + 1));
        }
        if (playPauseId != 0) {
            String action = isPlaying ? MusicService.ACTION_PAUSE : MusicService.ACTION_RESUME;
            views.setOnClickPendingIntent(playPauseId, buildServicePendingIntent(context, action, appWidgetId * 10 + 2));
        }
        if (nextId != 0) {
            views.setOnClickPendingIntent(nextId, buildServicePendingIntent(context, MusicService.ACTION_NEXT, appWidgetId * 10 + 3));
        }

        manager.updateAppWidget(appWidgetId, views);
    }

    /** Prefers the bitmap MusicService already decoded in memory; only falls back to
     *  re-reading from disk if that's unavailable (e.g. process was killed and restarted
     *  and a widget onUpdate fires before playback resumes). */
    private static Bitmap resolveAlbumArt() {
        Bitmap cached = MusicService.getCurrentAlbumArt();
        if (cached != null) return cached;

        String path = MusicService.currentSourcePath;
        if (path == null || path.isEmpty()) return null;

        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            if (art != null) return BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Exception e) {
            // Not fatal - path may be a content:// URI needing a Context overload, or the
            // file may no longer exist. Widget just falls back to the placeholder icon.
        } finally {
            try { retriever.release(); } catch (Exception ignored) { }
        }
        return null;
    }

    private static PendingIntent buildActivityPendingIntent(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getActivity(context, 100, intent, flags);
    }

    private static PendingIntent buildServicePendingIntent(Context context, String action, int requestCode) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getService(context, requestCode, intent, flags);
    }
}
