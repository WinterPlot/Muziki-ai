package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.content.pm.ActivityInfo;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import java.util.Locale;

/**
 * MUZIKI Video — native local video player.
 *
 * Plays a video already on the device with a custom transport overlay
 * (play/pause, +/-10s skip, drag-to-seek, playback speed, fullscreen
 * rotation). Everything runs off the device's own file with Android's
 * built-in VideoView/MediaPlayer - nothing is streamed or buffered from
 * a server, so playback starts instantly and there's no ads or
 * "upgrade to watch faster" friction some video apps add on top of
 * files you already own.
 */
public class VideoPlayerActivity extends AppCompatActivity {

    public static final String EXTRA_VIDEO_PATH = "extra_video_path";
    public static final String EXTRA_VIDEO_TITLE = "extra_video_title";

    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f};
    private static final int CONTROLS_AUTO_HIDE_MS = 3200;
    private static final int SEEK_STEP_MS = 10000;

    private VideoView videoView;
    private ProgressBar bufferingSpinner;
    private SeekBar seekBar;
    private TextView txtPosition, txtDuration, txtTitle, txtSpeedBadge;
    private ImageButton btnPlayPause, btnFullscreen;
    private View topBar, bottomBar, centerControls;
    private ConfettiBurstView confettiBurst;

    // Gesture HUDs: brightness (left drag), volume (right drag), seek (horizontal swipe)
    private View hudBrightness, hudVolume, hudSeek;
    private ProgressBar hudBrightnessBar, hudVolumeBar;
    private TextView txtHudBrightness, txtHudVolume, txtHudSeek;
    private ImageView imgHudVolumeIcon, imgHudSeekDirection;

    private AudioManager audioManager;
    private GestureDetector gestureDetector;

    // Gesture-in-progress state
    private static final int GESTURE_NONE = 0;
    private static final int GESTURE_BRIGHTNESS = 1;
    private static final int GESTURE_VOLUME = 2;
    private static final int GESTURE_SEEK = 3;
    private int activeGesture = GESTURE_NONE;

    private float gestureStartX, gestureStartY;
    private float gestureStartBrightness;
    private int gestureStartVolume;
    private int gestureStartPositionMs;
    private int gestureSeekTargetMs;
    private int maxVolume;

    // A full-width horizontal swipe covers more than just its proportional
    // slice of the video - multiplying the raw drag distance by this
    // factor makes seeking feel snappier/"a little bit faster" than a
    // strict 1:1 finger-to-timeline mapping, without losing fine control
    // on short taps/drags.
    private static final float SEEK_SWIPE_SPEED_MULTIPLIER = 1.6f;

    private String videoPath;
    private String videoTitle;
    private boolean isFullscreen = false;
    private boolean userIsSeeking = false;
    private float currentSpeed = 1.0f;
    private int resumePositionMs = 0;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());

    private final Runnable progressTicker = new Runnable() {
        @Override
        public void run() {
            updateProgressUi();
            uiHandler.postDelayed(this, 400);
        }
    };

    private final Runnable autoHideControls = new Runnable() {
        @Override
        public void run() {
            setControlsVisible(false);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge, immersive playback surface. The manifest theme
        // (Theme.DeviceDefault.NoActionBar.Fullscreen) already removes the
        // title bar and status bar chrome, so we only need to keep the
        // screen awake here - calling requestWindowFeature again on top of
        // a theme that already sets that feature throws on some OEMs.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_video_player);

        videoPath = getIntent().getStringExtra(EXTRA_VIDEO_PATH);
        videoTitle = getIntent().getStringExtra(EXTRA_VIDEO_TITLE);

        if (videoPath == null || videoPath.length() == 0) {
            Toast.makeText(this, "Couldn't open this video", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

        bindViews();
        setupVideoView();
        setupControls();
        setupGestures();

        txtTitle.setText(videoTitle != null ? videoTitle : "Video");
    }

    private void bindViews() {
        videoView = (VideoView) findViewById(R.id.videoView);
        bufferingSpinner = (ProgressBar) findViewById(R.id.videoBuffering);
        seekBar = (SeekBar) findViewById(R.id.videoSeekBar);
        txtPosition = (TextView) findViewById(R.id.txtVideoPosition);
        txtDuration = (TextView) findViewById(R.id.txtVideoDurationLabel);
        txtTitle = (TextView) findViewById(R.id.txtVideoPlayerTitle);
        txtSpeedBadge = (TextView) findViewById(R.id.txtVideoSpeedBadge);
        btnPlayPause = (ImageButton) findViewById(R.id.btnVideoPlayPause);
        btnFullscreen = (ImageButton) findViewById(R.id.btnVideoFullscreen);
        topBar = findViewById(R.id.videoTopBar);
        bottomBar = findViewById(R.id.videoBottomBar);
        centerControls = findViewById(R.id.videoCenterControls);

        hudBrightness = findViewById(R.id.hudBrightness);
        hudVolume = findViewById(R.id.hudVolume);
        hudSeek = findViewById(R.id.hudSeek);
        hudBrightnessBar = (ProgressBar) findViewById(R.id.hudBrightnessBar);
        hudVolumeBar = (ProgressBar) findViewById(R.id.hudVolumeBar);
        txtHudBrightness = (TextView) findViewById(R.id.txtHudBrightness);
        txtHudVolume = (TextView) findViewById(R.id.txtHudVolume);
        txtHudSeek = (TextView) findViewById(R.id.txtHudSeek);
        imgHudVolumeIcon = (ImageView) findViewById(R.id.imgHudVolumeIcon);
        imgHudSeekDirection = (ImageView) findViewById(R.id.imgHudSeekDirection);
        confettiBurst = (ConfettiBurstView) findViewById(R.id.confettiBurst);
    }

    private void setupVideoView() {
        videoView.setVideoURI(Uri.parse(videoPath));

        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                bufferingSpinner.setVisibility(View.GONE);
                mp.setLooping(false);
                if (resumePositionMs > 0) {
                    videoView.seekTo(resumePositionMs);
                }
                applySpeedToPlayer(mp, currentSpeed);
                videoView.start();
                updatePlayPauseIcon();
                uiHandler.post(progressTicker);
                scheduleAutoHide();
            }
        });

        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                updatePlayPauseIcon();
                setControlsVisible(true);
                uiHandler.removeCallbacks(autoHideControls);
                celebrateCompletion();
            }
        });

        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                bufferingSpinner.setVisibility(View.GONE);
                Toast.makeText(VideoPlayerActivity.this, "This video couldn't be played", Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        videoView.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mp, int what, int extra) {
                if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                    bufferingSpinner.setVisibility(View.VISIBLE);
                } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                    bufferingSpinner.setVisibility(View.GONE);
                }
                return false;
            }
        });

        bufferingSpinner.setVisibility(View.VISIBLE);
    }

    private void setupControls() {
        // Tap-to-toggle-controls and the double-tap-to-seek shortcut both
        // live in setupGestures() now, since videoTapCatcher's
        // OnTouchListener there consumes every touch event on this view -
        // a separate OnClickListener here would simply never fire.

        ImageButton btnBack = (ImageButton) findViewById(R.id.btnVideoBack);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        ImageButton btnRewind = (ImageButton) findViewById(R.id.btnVideoRewind);
        btnRewind.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekRelative(-SEEK_STEP_MS);
            }
        });

        ImageButton btnForward = (ImageButton) findViewById(R.id.btnVideoForward);
        btnForward.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                seekRelative(SEEK_STEP_MS);
            }
        });

        ImageButton btnSpeed = (ImageButton) findViewById(R.id.btnVideoSpeed);
        btnSpeed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSpeedPicker();
            }
        });

        ImageButton btnPip = (ImageButton) findViewById(R.id.btnVideoPip);
        if (isPipSupported()) {
            btnPip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    enterPipMode();
                }
            });
        } else {
            // Hide rather than disable: a grayed-out button that never
            // works is just confusing on devices/OS versions that don't
            // support PiP at all.
            btnPip.setVisibility(View.GONE);
        }

        btnFullscreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFullscreen();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser && videoView.getDuration() > 0) {
                    int targetMs = (int) ((progress / 1000.0) * videoView.getDuration());
                    txtPosition.setText(formatTime(targetMs));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) {
                userIsSeeking = true;
                uiHandler.removeCallbacks(autoHideControls);
            }

            @Override
            public void onStopTrackingTouch(SeekBar sb) {
                if (videoView.getDuration() > 0) {
                    int targetMs = (int) ((sb.getProgress() / 1000.0) * videoView.getDuration());
                    videoView.seekTo(targetMs);
                }
                userIsSeeking = false;
                scheduleAutoHide();
            }
        });
    }

    // ---------------------------------------------------------------
    // Gesture controls: left = brightness, right = volume, horizontal
    // swipe anywhere = seek. A tap (no meaningful drag) still falls
    // through to the existing show/hide-controls behavior.
    // ---------------------------------------------------------------

    private void setupGestures() {
        final View tapCatcher = findViewById(R.id.videoTapCatcher);

        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                toggleControlsVisibility();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                // Double-tap left/right thirds of the screen to skip
                // +/-10s, the same shortcut most video apps offer, in
                // addition to the dedicated rewind/forward buttons.
                int width = tapCatcher.getWidth();
                if (width <= 0) return false;
                if (e.getX() < width * 0.4f) {
                    seekRelative(-SEEK_STEP_MS);
                } else if (e.getX() > width * 0.6f) {
                    seekRelative(SEEK_STEP_MS);
                } else {
                    togglePlayPause();
                }
                return true;
            }
        });

        tapCatcher.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event);

                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        gestureStartX = event.getX();
                        gestureStartY = event.getY();
                        activeGesture = GESTURE_NONE;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        handleGestureMove(v, event);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        finishGesture();
                        return true;
                }
                return false;
            }
        });
    }

    private void handleGestureMove(View touchSurface, MotionEvent event) {
        float dx = event.getX() - gestureStartX;
        float dy = event.getY() - gestureStartY;

        if (activeGesture == GESTURE_NONE) {
            // Decide which gesture this is once the drag is clearly past
            // a small slop, based on whichever axis moved further first -
            // this keeps a mostly-vertical drag from also nudging the
            // seek position, and vice versa.
            float absDx = Math.abs(dx);
            float absDy = Math.abs(dy);
            if (Math.max(absDx, absDy) < 24) {
                return; // still within tap/slop territory
            }

            if (absDy > absDx) {
                boolean isLeftHalf = gestureStartX < touchSurface.getWidth() / 2f;
                activeGesture = isLeftHalf ? GESTURE_BRIGHTNESS : GESTURE_VOLUME;
                if (activeGesture == GESTURE_BRIGHTNESS) {
                    gestureStartBrightness = getCurrentWindowBrightness();
                } else {
                    gestureStartVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                }
                uiHandler.removeCallbacks(autoHideControls);
            } else {
                activeGesture = GESTURE_SEEK;
                gestureStartPositionMs = videoView.getDuration() > 0 ? videoView.getCurrentPosition() : 0;
                uiHandler.removeCallbacks(autoHideControls);
            }
        }

        if (activeGesture == GESTURE_BRIGHTNESS) {
            float delta = -dy / Math.max(touchSurface.getHeight(), 1);
            float newBrightness = clamp01(gestureStartBrightness + delta);
            applyWindowBrightness(newBrightness);
            showBrightnessHud(newBrightness);

        } else if (activeGesture == GESTURE_VOLUME) {
            float delta = -dy / Math.max(touchSurface.getHeight(), 1);
            int newVolume = Math.round(clamp01((gestureStartVolume / (float) maxVolume) + delta) * maxVolume);
            newVolume = Math.max(0, Math.min(maxVolume, newVolume));
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
            showVolumeHud(newVolume);

        } else if (activeGesture == GESTURE_SEEK) {
            int duration = videoView.getDuration();
            if (duration <= 0) return;
            int width = Math.max(touchSurface.getWidth(), 1);
            // See SEEK_SWIPE_SPEED_MULTIPLIER: a full-width drag covers
            // more than just its proportional slice of the timeline so
            // seeking feels quicker than a strict 1:1 mapping.
            float fractionOfDuration = (dx / width) * SEEK_SWIPE_SPEED_MULTIPLIER;
            int deltaMs = Math.round(fractionOfDuration * duration);
            int target = gestureStartPositionMs + deltaMs;
            if (target < 0) target = 0;
            if (target > duration) target = duration;
            gestureSeekTargetMs = target;
            showSeekHud(target, duration, deltaMs >= 0);
            // Live scrub the seek bar/time labels as feedback, without
            // committing the actual seek until the finger lifts - this
            // avoids stuttering the decoder with a seekTo() on every
            // pixel of movement.
            seekBar.setProgress((int) ((target / (float) duration) * 1000));
            txtPosition.setText(formatTime(target));
        }
    }

    private void finishGesture() {
        if (activeGesture == GESTURE_SEEK) {
            videoView.seekTo(gestureSeekTargetMs);
        }
        hudBrightness.setVisibility(View.GONE);
        hudVolume.setVisibility(View.GONE);
        hudSeek.setVisibility(View.GONE);
        activeGesture = GESTURE_NONE;
        scheduleAutoHide();
    }

    private float clamp01(float value) {
        if (value < 0f) return 0f;
        if (value > 1f) return 1f;
        return value;
    }

    /**
     * Reads this window's current screen brightness (0f-1f). Falls back to
     * the system's current brightness setting the first time this is
     * called, since a freshly-created window reports
     * BRIGHTNESS_OVERRIDE_NONE (-1) until we explicitly set one.
     */
    private float getCurrentWindowBrightness() {
        float current = getWindow().getAttributes().screenBrightness;
        if (current >= 0f) return current;
        try {
            int systemValue = android.provider.Settings.System.getInt(
                    getContentResolver(), android.provider.Settings.System.SCREEN_BRIGHTNESS);
            return clamp01(systemValue / 255f);
        } catch (Exception e) {
            return 0.5f;
        }
    }

    /**
     * Sets brightness for this window only (WindowManager.LayoutParams
     * .screenBrightness), which needs no special permission - unlike
     * changing the system-wide brightness setting, which would require
     * WRITE_SETTINGS. The change reverts automatically when this Activity
     * closes.
     */
    private void applyWindowBrightness(float brightness) {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = brightness;
        getWindow().setAttributes(params);
    }

    private void showBrightnessHud(float brightness) {
        hudVolume.setVisibility(View.GONE);
        hudSeek.setVisibility(View.GONE);
        hudBrightness.setVisibility(View.VISIBLE);
        int percent = Math.round(brightness * 100);
        hudBrightnessBar.setProgress(percent);
        txtHudBrightness.setText(percent + "%");
    }

    private void showVolumeHud(int volume) {
        hudBrightness.setVisibility(View.GONE);
        hudSeek.setVisibility(View.GONE);
        hudVolume.setVisibility(View.VISIBLE);
        int percent = maxVolume > 0 ? Math.round((volume / (float) maxVolume) * 100) : 0;
        hudVolumeBar.setProgress(percent);
        txtHudVolume.setText(percent + "%");
        imgHudVolumeIcon.setImageResource(volume == 0 ? R.drawable.ic_hud_volume_muted : R.drawable.ic_hud_volume);
    }

    private void showSeekHud(int targetMs, int durationMs, boolean forward) {
        hudBrightness.setVisibility(View.GONE);
        hudVolume.setVisibility(View.GONE);
        hudSeek.setVisibility(View.VISIBLE);
        imgHudSeekDirection.setImageResource(forward ? R.drawable.ic_forward_10 : R.drawable.ic_rewind_10);
        txtHudSeek.setText(formatTime(targetMs) + " / " + formatTime(durationMs));
    }

    // ---------------------------------------------------------------
    // Playback controls
    // ---------------------------------------------------------------

    private void togglePlayPause() {
        if (videoView.isPlaying()) {
            videoView.pause();
        } else {
            videoView.start();
        }
        updatePlayPauseIcon();
        scheduleAutoHide();
    }

    private void updatePlayPauseIcon() {
        btnPlayPause.setImageResource(videoView.isPlaying() ? R.drawable.pauses : R.drawable.plays);
    }

    private void seekRelative(int deltaMs) {
        int duration = videoView.getDuration();
        if (duration <= 0) return;
        int target = videoView.getCurrentPosition() + deltaMs;
        if (target < 0) target = 0;
        if (target > duration) target = duration;
        videoView.seekTo(target);
        updateProgressUi();
        scheduleAutoHide();
    }

    /**
     * 0.5x-2.0x playback speed for video, the video counterpart to the
     * audio player's speed/pitch card in EqualizerActivity (video only
     * exposes speed here - pitch-shifting a video's audio independently
     * isn't something VideoView/MediaPlayer supports cleanly, and most
     * people watching a video just want faster/slower, not a different
     * pitch). Requires API 23+ for MediaPlayer#setPlaybackParams; below
     * that the option tells the user plainly instead of silently no-oping.
     */
    private void showSpeedPicker() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            Toast.makeText(this, "Playback speed requires Android 6.0 or newer", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] labels = new String[SPEED_OPTIONS.length];
        for (int i = 0; i < SPEED_OPTIONS.length; i++) {
            labels[i] = formatSpeedLabel(SPEED_OPTIONS[i]) + (SPEED_OPTIONS[i] == 1.0f ? " (Normal)" : "");
        }

        uiHandler.removeCallbacks(autoHideControls);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Playback speed")
                .setItems(labels, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        currentSpeed = SPEED_OPTIONS[which];
                        applySpeedLive(currentSpeed);
                        scheduleAutoHide();
                    }
                })
                .setOnCancelListener(new android.content.DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(android.content.DialogInterface dialog) {
                        scheduleAutoHide();
                    }
                })
                .show();
    }

    /**
     * Applies speed to a MediaPlayer we already have a direct reference
     * to (only available inside onPrepared - VideoView doesn't expose a
     * public getter for its internal player at other times).
     */
    private void applySpeedToPlayer(MediaPlayer mp, float speed) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || mp == null) return;
        try {
            PlaybackParams params = mp.getPlaybackParams();
            params.setSpeed(speed);
            mp.setPlaybackParams(params);
        } catch (Exception ignored) {
            // Some OEM decoders reject extreme speeds for a given codec;
            // playback just continues at whatever rate it already had.
        }
    }

    /**
     * Applies a speed change requested live from the speed picker.
     * VideoView has no public getter for its internal MediaPlayer outside
     * onPrepared, so instead of reaching in directly, we force a fresh
     * onPrepared by re-seeking to the current position on the same URI:
     * VideoView re-associates its already-buffered MediaPlayer instantly
     * (no visible re-buffering on a local file), and onPrepared re-applies
     * currentSpeed for us. This keeps everything working through public
     * API only, matching the rest of this project's no-reflection style.
     */
    private void applySpeedLive(float speed) {
        int position = videoView.getCurrentPosition();
        boolean wasPlaying = videoView.isPlaying();
        resumePositionMs = position;
        videoView.setVideoURI(Uri.parse(videoPath));
        if (!wasPlaying) {
            // onPrepared always calls start(); pause right back if the
            // video was paused when the user changed speed, so the speed
            // change itself doesn't resume playback unexpectedly.
            uiHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (videoView.isPlaying()) videoView.pause();
                    updatePlayPauseIcon();
                }
            }, 150);
        }
        txtSpeedBadge.setText(speed == 1.0f ? "" : formatSpeedLabel(speed));
    }

    private void toggleFullscreen() {
        isFullscreen = !isFullscreen;
        if (isFullscreen) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            btnFullscreen.setImageResource(R.drawable.ic_video_fullscreen_exit);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            btnFullscreen.setImageResource(R.drawable.ic_video_fullscreen);
        }
    }

    // ---------------------------------------------------------------
    // Picture-in-Picture
    // ---------------------------------------------------------------

    /** PiP itself needs API 26 (Oreo); also confirm the device advertises the feature at all. */
    private boolean isPipSupported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE);
    }

    /**
     * Shrinks the player into a small floating window that stays on top
     * of other apps, so watching a video doesn't have to block using the
     * rest of the phone - the browser, chat, or Muziki's own music
     * library, for instance, all stay reachable underneath it.
     */
    private void enterPipMode() {
        if (!isPipSupported()) return;
        try {
            android.app.PictureInPictureParams.Builder builder = new android.app.PictureInPictureParams.Builder();
            if (videoView.getWidth() > 0 && videoView.getHeight() > 0) {
                builder.setAspectRatio(new android.util.Rational(videoView.getWidth(), videoView.getHeight()));
            }
            setControlsVisible(false);
            enterPictureInPictureMode(builder.build());
        } catch (Exception e) {
            Toast.makeText(this, "Picture-in-picture isn't available right now", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Auto-enters PiP when the user leaves the app while a video is
     * playing (e.g. presses Home or switches apps) - the same behavior
     * YouTube/most video apps use, so playback doesn't just stop dead the
     * moment MUZIKI Video is backgrounded.
     */
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (isPipSupported() && videoView != null && videoView.isPlaying()) {
            enterPipMode();
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, android.content.res.Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        if (isInPictureInPictureMode) {
            // The floating PiP window is tiny - the full control overlay
            // (seek bar, buttons, HUDs) has no room and no touch target
            // big enough to use, so hide it entirely while in PiP.
            setControlsVisible(false);
            uiHandler.removeCallbacks(autoHideControls);
        } else {
            setControlsVisible(true);
            scheduleAutoHide();
        }
    }

    // ---------------------------------------------------------------
    // Progress / controls visibility
    // ---------------------------------------------------------------

    private void updateProgressUi() {
        if (userIsSeeking || videoView == null) return;
        int duration = videoView.getDuration();
        int position = videoView.getCurrentPosition();
        if (duration > 0) {
            seekBar.setProgress((int) ((position / (float) duration) * 1000));
            txtDuration.setText(formatTime(duration));
        }
        txtPosition.setText(formatTime(position));
        updatePlayPauseIcon();
    }

    /**
     * MUZIKI Video's surprise: a quick confetti burst from the center of
     * the screen when a video is watched all the way to the end, paired
     * with a small congratulatory toast. Skipped gracefully while in PiP
     * (no room for it there) and never blocks any control - it's purely
     * decorative and disappears on its own in under 1.5 seconds.
     */
    private void celebrateCompletion() {
        if (confettiBurst == null) return;
        boolean isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
        if (isInPip) return;

        confettiBurst.post(new Runnable() {
            @Override
            public void run() {
                float centerX = confettiBurst.getWidth() / 2f;
                float centerY = confettiBurst.getHeight() / 2f;
                confettiBurst.burst(centerX, centerY);
            }
        });
        Toast.makeText(this, "Nice! You finished the video 🎉", Toast.LENGTH_SHORT).show();
    }

    private void toggleControlsVisibility() {
        boolean currentlyVisible = topBar.getVisibility() == View.VISIBLE;
        setControlsVisible(!currentlyVisible);
        if (!currentlyVisible) {
            scheduleAutoHide();
        }
    }

    private void setControlsVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        topBar.setVisibility(vis);
        bottomBar.setVisibility(vis);
        centerControls.setVisibility(vis);
    }

    private void scheduleAutoHide() {
        uiHandler.removeCallbacks(autoHideControls);
        if (videoView.isPlaying()) {
            uiHandler.postDelayed(autoHideControls, CONTROLS_AUTO_HIDE_MS);
        }
    }

    // ---------------------------------------------------------------
    // Formatting
    // ---------------------------------------------------------------

    private String formatTime(int ms) {
        int totalSeconds = ms / 1000;
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, seconds);
    }

    /** Formats a speed multiplier as e.g. "1x", "1.25x", "0.5x". */
    private String formatSpeedLabel(float speed) {
        if (speed == Math.round(speed)) {
            return Math.round(speed) + "x";
        }
        String trimmed = String.format(Locale.US, "%.2f", speed);
        while (trimmed.endsWith("0")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "x";
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    @Override
    protected void onPause() {
        super.onPause();
        // Entering PiP also triggers onPause on this activity (PiP is
        // still "resumed" as far as the system is concerned, just in a
        // small floating window) - pausing playback here would silently
        // defeat the whole point of watching in PiP while doing something
        // else. Only pause for a genuine backgrounding (Home, switching
        // to another full app, screen off, etc).
        boolean isInPip = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
        if (videoView != null && !isInPip) {
            resumePositionMs = videoView.getCurrentPosition();
            videoView.pause();
        }
        uiHandler.removeCallbacks(autoHideControls);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        uiHandler.removeCallbacks(progressTicker);
        uiHandler.removeCallbacks(autoHideControls);
        if (videoView != null) {
            videoView.stopPlayback();
        }
        if (confettiBurst != null) {
            confettiBurst.cancelBurst();
        }
    }
}
