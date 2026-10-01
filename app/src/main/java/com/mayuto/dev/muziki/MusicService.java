package com.mayuto.dev.muziki;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import java.util.ArrayList;
import java.util.Random;

public class MusicService extends Service {
    public static MediaPlayer mediaPlayer;
    public static String currentTitle = "";
    public static boolean isPlaying = false;
    private static Bitmap albumArt = null;

    /** Path/URI string of whatever is currently loaded, valid whether it came from the
     *  queue (songPaths/currentIndex) or from an externally-opened file (handlePlayFromUri).
     *  Widgets should read this instead of indexing into songPaths directly. */
    public static String currentSourcePath = null;

    // --- UPDATED MASTER QUEUE ---
    public static ArrayList<String> songPaths = new ArrayList<String>();
    public static ArrayList<String> songTitles = new ArrayList<String>();
    public static int currentIndex = -1;

    // Repeat & Shuffle Constants
    public static final int REPEAT_OFF = 0;
    public static final int REPEAT_ALL = 1;
    public static final int REPEAT_ONE = 2;
    public static int repeatMode = REPEAT_OFF;
    public static boolean isShuffle = false;

    // --- Playback Speed & Pitch (0.5x - 2.0x) ---
    // Applied via MediaPlayer#setPlaybackParams, which requires API 23+.
    // Below that, speed/pitch changes are silently ignored (playbackSpeed
    // and playbackPitch still get saved so the UI reflects the user's
    // choice, but MediaPlayer itself just keeps playing at 1.0x/1.0x).
    public static final float MIN_PLAYBACK_SPEED = 0.5f;
    public static final float MAX_PLAYBACK_SPEED = 2.0f;
    public static final float MIN_PLAYBACK_PITCH = 0.5f;
    public static final float MAX_PLAYBACK_PITCH = 2.0f;
    public static final String PREF_PLAYBACK_SPEED = "pref_playback_speed";
    public static final String PREF_PLAYBACK_PITCH = "pref_playback_pitch";
    public static float playbackSpeed = 1.0f;
    public static float playbackPitch = 1.0f;

    // Actions
    public static final String ACTION_PLAY_NEW = "PLAY_NEW";
    public static final String ACTION_PAUSE = "PAUSE";
    public static final String ACTION_RESUME = "RESUME";
    public static final String ACTION_NEXT = "NEXT";
    public static final String ACTION_PREV = "PREV";
    public static final String ACTION_STOP = "STOP";
    public static final String ACTION_PLAY_FROM_URI = "ACTION_PLAY_FROM_URI";
    
    // NEW QUEUE ACTIONS
    public static final String ACTION_PLAY_NEXT = "PLAY_NEXT";
    public static final String ACTION_ADD_TO_QUEUE = "ADD_TO_QUEUE";
    public static final String ACTION_CLEAR_QUEUE = "CLEAR_QUEUE";

    // Fired by SleepTimer's AlarmManager alarm when the countdown reaches zero.
    public static final String ACTION_SLEEP_TIMER_FIRED = "ACTION_SLEEP_TIMER_FIRED";

    // Playback Speed / Pitch control
    public static final String ACTION_SET_PLAYBACK_SPEED = "ACTION_SET_PLAYBACK_SPEED";
    public static final String ACTION_SET_PLAYBACK_PITCH = "ACTION_SET_PLAYBACK_PITCH";
    public static final String EXTRA_SPEED = "extra_speed";
    public static final String EXTRA_PITCH = "extra_pitch";

    private static final String CHANNEL_ID = "muziki_channel";
    private static final int NOTIFICATION_ID = 1;

    private MediaSession mediaSession;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        playbackSpeed = prefs.getFloat(PREF_PLAYBACK_SPEED, 1.0f);
        playbackPitch = prefs.getFloat(PREF_PLAYBACK_PITCH, 1.0f);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaSession = new MediaSession(this, "MuzikiSession");
            mediaSession.setCallback(new MediaSession.Callback() {
                @Override
                public void onSeekTo(long pos) {
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo((int) pos);
                        updateMediaSessionMetadata();
                    }
                }
                @Override
                public void onPlay() { resumeSong(); }
                @Override
                public void onPause() { pauseSong(); }
                @Override
                public void onSkipToNext() { playNext(); }
                @Override
                public void onSkipToPrevious() { playPrevious(); }
                @Override
                public void onStop() { stopMusicService(); }
            });
            mediaSession.setActive(true);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) return START_STICKY;

        String action = intent.getAction();
        // MusicService is internal-only. Keep a second validation layer so
        // malformed intents cannot inject arbitrary paths/URIs if this class
        // is ever exposed through another integration point.
        if (!isTrustedInternalIntent(intent)) return START_NOT_STICKY;

        switch (action) {
            case ACTION_PLAY_NEW:
                ArrayList<String> paths = intent.getStringArrayListExtra("paths");
                ArrayList<String> titles = intent.getStringArrayListExtra("titles");
                if (paths == null || paths.isEmpty() || paths.size() > 500) break;
                ArrayList<String> safePaths = new ArrayList<>();
                for (String path : paths) {
                    if (!isAllowedMediaPath(path)) return START_NOT_STICKY;
                    safePaths.add(path);
                }
                ArrayList<String> safeTitles = new ArrayList<>();
                if (titles != null) {
                    safeTitles.addAll(titles);
                }
                while (safeTitles.size() < safePaths.size()) safeTitles.add("Unknown track");
                if (safeTitles.size() > safePaths.size()) {
                    safeTitles = new ArrayList<>(safeTitles.subList(0, safePaths.size()));
                }
                songPaths = safePaths;
                songTitles = safeTitles;
                currentIndex = intent.getIntExtra("index", 0);
                if (currentIndex < 0 || currentIndex >= songPaths.size()) currentIndex = 0;
                playSong();
                break;

            case ACTION_PLAY_NEXT:
                handleQueueAction(intent, true);
                break;

            case ACTION_ADD_TO_QUEUE:
                handleQueueAction(intent, false);
                break;

            case ACTION_CLEAR_QUEUE:
                clearQueue();
                break;

            case ACTION_PLAY_FROM_URI:
                Uri source = intent.getData();
                if (isAllowedMediaUri(source)) handlePlayFromUri(source);
                break;

            case ACTION_PAUSE:
                pauseSong();
                break;

            case ACTION_RESUME:
                resumeSong();
                break;

            case ACTION_NEXT:
                playNext();
                break;

            case ACTION_PREV:
                playPrevious();
                break;

            case ACTION_STOP:
                stopMusicService();
                break;

            case ACTION_SLEEP_TIMER_FIRED:
                handleSleepTimerFired();
                break;

            case ACTION_SET_PLAYBACK_SPEED:
                setPlaybackSpeed(intent.getFloatExtra(EXTRA_SPEED, 1.0f));
                break;

            case ACTION_SET_PLAYBACK_PITCH:
                setPlaybackPitch(intent.getFloatExtra(EXTRA_PITCH, 1.0f));
                break;
        }

        return START_STICKY;
    }


    private boolean isTrustedInternalIntent(Intent intent) {
        // Only intents originating in this package should reach this service.
        // exported=false is the primary boundary; this check hardens future changes.
        String creatorPackage = intent.getPackage();
        return creatorPackage == null || getPackageName().equals(creatorPackage);
    }

    private boolean isAllowedMediaPath(String path) {
        if (path == null || path.length() == 0 || path.length() > 4096) return false;
        if (path.indexOf('\0') >= 0) return false;
        if (path.startsWith("content://") || path.startsWith("file://")) return true;
        // Internal callers historically pass filesystem paths. Reject obvious
        // network schemes so playback cannot become an arbitrary URL fetcher.
        return !path.contains("://");
    }

    private boolean isAllowedMediaUri(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return "content".equalsIgnoreCase(scheme) || "file".equalsIgnoreCase(scheme);
    }

    private void sendSongChangedBroadcast() {
        Intent update = new Intent("SONG_CHANGED");
        update.setPackage(getPackageName());
        sendBroadcast(update);
    }

    private void handleQueueAction(Intent intent, boolean isPlayNext) {
        String path = intent.getStringExtra("path");
        String title = intent.getStringExtra("title");
        if (!isAllowedMediaPath(path)) return;
        if (title == null) title = "Unknown track";

        if (isPlayNext) {
            // Insert right after current song
            int insertAt = currentIndex + 1;
            songPaths.add(insertAt, path);
            songTitles.add(insertAt, title);
        } else {
            // Add to the end of list
            songPaths.add(path);
            songTitles.add(title);
        }
        sendSongChangedBroadcast();
        notifyWidgets();
    }

    private void clearQueue() {
        if (currentIndex != -1 && !songPaths.isEmpty()) {
            String currentPath = songPaths.get(currentIndex);
            String currentTitleStr = songTitles.get(currentIndex);
            songPaths.clear();
            songTitles.clear();
            // Keep current song as the only item in queue
            songPaths.add(currentPath);
            songTitles.add(currentTitleStr);
            currentIndex = 0;
        }
        sendSongChangedBroadcast();
        notifyWidgets();
    }

    private void playSong() {
        if (currentIndex < 0 || currentIndex >= songPaths.size()) return;

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
        }

        try {
            String path = songPaths.get(currentIndex);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(path);
            mediaPlayer.prepare();
            mediaPlayer.start();
            applyPlaybackParams();

            currentTitle = songTitles.get(currentIndex);
            isPlaying = true;
            currentSourcePath = path;
            albumArt = loadAlbumArt(path);

            // Save for resume logic + last-played reminder tracking
            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit()
                .putString("last_played_path", path)
                .putString("saved_song_title", currentTitle)
                .putLong(ReminderReceiver.PREF_LAST_PLAYED_TIME, System.currentTimeMillis())
                .apply();
            ReminderScheduler.ensureScheduled(this);

            updateMediaSessionMetadata();
            updateNotification();
            sendSongChangedBroadcast();
            notifyWidgets();

            mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    if (repeatMode == REPEAT_ONE) {
                        playSong(); 
                    } else {
                        playNext(); 
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void playNext() {
        if (songPaths.isEmpty()) return;

        if (isShuffle) {
            currentIndex = new Random().nextInt(songPaths.size());
        } else {
            if (currentIndex < songPaths.size() - 1) {
                currentIndex++;
            } else {
                if (repeatMode == REPEAT_ALL) {
                    currentIndex = 0;
                } else {
                    pauseSong();
                    return;
                }
            }
        }
        playSong();
    }

    public void playPrevious() {
        if (songPaths.isEmpty()) return;
        currentIndex = (currentIndex - 1 < 0) ? songPaths.size() - 1 : currentIndex - 1;
        playSong();
    }

    /** Lets widgets reuse the already-decoded art instead of re-running MediaMetadataRetriever themselves. */
    public static Bitmap getCurrentAlbumArt() {
        return albumArt;
    }

    private Bitmap loadAlbumArt(String path) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(path);
            byte[] art = retriever.getEmbeddedPicture();
            retriever.release();
            if (art != null) return BitmapFactory.decodeByteArray(art, 0, art.length);
        } catch (Exception e) { e.printStackTrace(); }
        return null;
    }

    private void updateMediaSessionMetadata() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && mediaSession != null) {
            MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder();
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_TITLE, currentTitle);
            metadataBuilder.putString(MediaMetadata.METADATA_KEY_ARTIST, "muziki");

            if (mediaPlayer != null) {
                metadataBuilder.putLong(MediaMetadata.METADATA_KEY_DURATION, (long) mediaPlayer.getDuration());
            }
            if (albumArt != null) {
                metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, albumArt);
            }

            mediaSession.setMetadata(metadataBuilder.build());

            PlaybackState.Builder stateBuilder = new PlaybackState.Builder();
            stateBuilder.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE |
                    PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS |
                    PlaybackState.ACTION_STOP | PlaybackState.ACTION_SEEK_TO);

            stateBuilder.setState(isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                    mediaPlayer != null ? (long) mediaPlayer.getCurrentPosition() : 0L, 1.0f);

            mediaSession.setPlaybackState(stateBuilder.build());
        }
    }

    private void updateNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, intent, flags);

        int playPauseIcon = isPlaying ? R.drawable.pauses : R.drawable.plays;

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);

        builder.setSmallIcon(R.drawable.plays)
                .setContentTitle(currentTitle)
                .setContentText("muziki")
                .setLargeIcon(albumArt)
                .setContentIntent(contentIntent)
                .setOngoing(isPlaying)
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(new Notification.Action(R.drawable.prevs, "Previous", getServiceTask(ACTION_PREV)))
                .addAction(new Notification.Action(playPauseIcon, isPlaying ? "Pause" : "Play", getServiceTask(isPlaying ? ACTION_PAUSE : ACTION_RESUME)))
                .addAction(new Notification.Action(R.drawable.nexts, "Next", getServiceTask(ACTION_NEXT)))
                .addAction(new Notification.Action(R.drawable.ic_stop, "Stop", getServiceTask(ACTION_STOP)));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Notification.MediaStyle style = new Notification.MediaStyle();
            style.setShowActionsInCompactView(1, 2, 3);
            if (mediaSession != null) style.setMediaSession(mediaSession.getSessionToken());
            builder.setStyle(style);
        }

        Notification notification = builder.build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, 2); 
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private PendingIntent getServiceTask(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getService(this, action.hashCode(), intent, flags);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Muziki Playback", NotificationManager.IMPORTANCE_LOW);
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private void handleSleepTimerFired() {
        pauseSong();
        SleepTimer.cancel(this);
        Intent timerUpdate = new Intent("SLEEP_TIMER_FIRED");
        timerUpdate.setPackage(getPackageName());
        sendBroadcast(timerUpdate);
    }

    private void pauseSong() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPlaying = false;
            updateMediaSessionMetadata();
            updateNotification();
            notifyWidgets();
        }
    }

    private void resumeSong() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            mediaPlayer.start();
            applyPlaybackParams();
            isPlaying = true;
            updateMediaSessionMetadata();
            updateNotification();
            notifyWidgets();
        }
    }

    /**
     * Repaints every placed widget (all 3 sizes) directly, in-process.
     *
     * Why not sendBroadcast("SONG_CHANGED")? On Android 8+ (API 26+) the OS
     * restricts *implicit* broadcasts from reaching manifest-registered
     * ("static") receivers - MusicWidgetProvider is exactly that kind of
     * receiver. sendBroadcast() with no explicit component/package set is
     * silently dropped for the widget in most real-world cases, which is why
     * the widget only ever painted once (at placement/onUpdate) and then
     * froze on "No song playing" or stale play/pause icons forever after.
     * Calling the provider's static push method directly bypasses the whole
     * broadcast-delivery question and always works, from any Android version.
     */
    private void notifyWidgets() {
        MusicWidgetProvider.pushUpdateToAllWidgets(this);
        MusicWidgetSmallProvider.pushUpdateToAllWidgets(this);
        MusicWidgetLargeProvider.pushUpdateToAllWidgets(this);
    }

    private void handlePlayFromUri(Uri uri) {
        if (uri == null) return;
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); }
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.prepare();
            mediaPlayer.start();
            applyPlaybackParams();
            isPlaying = true;
            currentIndex = -1; // playing outside the queue; widget falls back to the freshly-loaded albumArt below
            currentSourcePath = uri.toString();
            updateCurrentSongFromUri(uri);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
            prefs.edit()
                .putString("last_played_path", uri.toString())
                .putString("saved_song_title", currentTitle)
                .putLong(ReminderReceiver.PREF_LAST_PLAYED_TIME, System.currentTimeMillis())
                .apply();
            ReminderScheduler.ensureScheduled(this);

            updateMediaSessionMetadata();
            updateNotification();
            sendSongChangedBroadcast();
            notifyWidgets();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void updateCurrentSongFromUri(Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, uri);
            currentTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
            if (currentTitle == null || currentTitle.isEmpty()) currentTitle = uri.getLastPathSegment();
            byte[] art = retriever.getEmbeddedPicture();
            albumArt = (art != null) ? BitmapFactory.decodeByteArray(art, 0, art.length) : null;
            retriever.release();
        } catch (Exception e) { currentTitle = "External File"; }
    }

    private void stopMusicService() {
        isPlaying = false;
        currentTitle = "";
        currentSourcePath = null;
        albumArt = null;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
        sendSongChangedBroadcast();
        notifyWidgets();
        stopForeground(true);
        stopSelf();
    }

    // ---------------------------------------------------------------
    // Playback Speed & Pitch (0.5x - 2.0x)
    // ---------------------------------------------------------------

    /**
     * Sets playback speed (0.5x - 2.0x) and applies it immediately to the
     * currently loaded track. Persisted so it carries over to the next
     * track and the next app launch. Silently clamped to the supported
     * range and silently ignored below API 23 (PlaybackParams didn't
     * exist yet) - the value is still saved either way so the UI is
     * consistent even on an old device.
     */
    public void setPlaybackSpeed(float speed) {
        playbackSpeed = clampSpeedPitch(speed);
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putFloat(PREF_PLAYBACK_SPEED, playbackSpeed).apply();
        applyPlaybackParams();
    }

    /** Sets playback pitch (0.5x - 2.0x), same rules as setPlaybackSpeed. */
    public void setPlaybackPitch(float pitch) {
        playbackPitch = clampSpeedPitch(pitch);
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit().putFloat(PREF_PLAYBACK_PITCH, playbackPitch).apply();
        applyPlaybackParams();
    }

    private float clampSpeedPitch(float value) {
        if (value < MIN_PLAYBACK_SPEED) return MIN_PLAYBACK_SPEED;
        if (value > MAX_PLAYBACK_SPEED) return MAX_PLAYBACK_SPEED;
        return value;
    }

    /**
     * Pushes the current playbackSpeed/playbackPitch onto the live
     * MediaPlayer. Only works on API 23+ (PlaybackParams). Wrapped in a
     * try/catch because some OEM decoders throw if asked for an extreme
     * speed/pitch combination on a given codec - in that case we simply
     * leave playback running at whatever rate it already had rather than
     * crashing the service.
     */
    private void applyPlaybackParams() {
        if (mediaPlayer == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return; // API 23+
        try {
            PlaybackParams params = mediaPlayer.getPlaybackParams();
            params.setSpeed(playbackSpeed);
            params.setPitch(playbackPitch);
            mediaPlayer.setPlaybackParams(params);
        } catch (Exception ignored) {
            // Leave playback at its previous rate rather than crash.
        }
    }

    @Override
    public void onDestroy() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (mediaSession != null) mediaSession.release();
        super.onDestroy();
    }
}

