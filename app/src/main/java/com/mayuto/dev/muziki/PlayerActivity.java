package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.AlphaAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Toast;

import java.util.HashSet;
import java.util.Set;

public class PlayerActivity extends AppCompatActivity {

    private TextView txtTitle, txtCurrentTime, txtTotalTime;
    private SeekBar musicSeekBar;
    private ImageButton btnPlayPause, btnNext, btnPrev, btnRepeat, btnShuffle, btnEqualizer, btnFavorite;
    private ImageView imgSeekFeedback, imgVinylDisc, imgTonearm;
    private View playerContent;
    private AudioVisualizerView audioVisualizer;

    private final Handler handler = new Handler();
    private Runnable seekUpdateRunnable;

    // Track disc spin state so we don't restart/interrupt the animation unnecessarily
    private boolean discIsSpinning = false;
    private boolean tonearmIsDown = false;

    // Double-Tap Logic
    private long lastTapTime = 0;
    private static final long DOUBLE_TAP_DELAY = 300;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.player);

        // Bind Views
        txtTitle = (TextView) findViewById(R.id.txtTitle);
        musicSeekBar = (SeekBar) findViewById(R.id.musicSeekBar);
        btnPlayPause = (ImageButton) findViewById(R.id.btnPlayPause);
        btnNext = (ImageButton) findViewById(R.id.btnNext);
        btnPrev = (ImageButton) findViewById(R.id.btnPrev);
        btnRepeat = (ImageButton) findViewById(R.id.btnRepeat);
        btnShuffle = (ImageButton) findViewById(R.id.btnShuffle);
        btnEqualizer = (ImageButton) findViewById(R.id.btnEqualizer);
        btnFavorite = (ImageButton) findViewById(R.id.btnFavorite);
        txtCurrentTime = (TextView) findViewById(R.id.txtCurrentTime);
        txtTotalTime = (TextView) findViewById(R.id.txtTotalTime);
        imgSeekFeedback = (ImageView) findViewById(R.id.imgSeekFeedback);
        imgVinylDisc = (ImageView) findViewById(R.id.imgVinylDisc);
        imgTonearm = (ImageView) findViewById(R.id.imgTonearm);
        playerContent = findViewById(R.id.playerContent);
        audioVisualizer = (AudioVisualizerView) findViewById(R.id.audioVisualizer);

        // Enable Marquee
        txtTitle.setSelected(true);

        // Entrance animation - whole screen slides up and fades in
        if (playerContent != null) {
            Animation entrance = AnimationUtils.loadAnimation(this, R.anim.slide_up_fade_in);
            playerContent.startAnimation(entrance);
        }

        // Handle External Intents
        handleIntent(getIntent());

        // --- DOUBLE TAP DETECTION ON ROOT ---
        View rootLayout = findViewById(R.id.playerRoot);
        if (rootLayout != null) {
            rootLayout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastTapTime < DOUBLE_TAP_DELAY) {
                            handleDoubleTap(event.getX(), v.getWidth());
                        }
                        lastTapTime = currentTime;
                    }
                    return true;
                }
            });
        }

        // Shuffle Logic
        btnShuffle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bounce(v);
                MusicService.isShuffle = !MusicService.isShuffle;
                updateShuffleUI();
            }
        });

        // Repeat Logic
        btnRepeat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bounce(v);
                if (MusicService.repeatMode == MusicService.REPEAT_OFF) {
                    MusicService.repeatMode = MusicService.REPEAT_ALL;
                } else if (MusicService.repeatMode == MusicService.REPEAT_ALL) {
                    MusicService.repeatMode = MusicService.REPEAT_ONE;
                } else {
                    MusicService.repeatMode = MusicService.REPEAT_OFF;
                }
                updateRepeatUI();
            }
        });

        // Favorite Logic - toggles the same favorites set MainActivity uses
        btnFavorite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleFavorite();
            }
        });

        // Equalizer Navigation
        btnEqualizer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bounce(v);
                if (MusicService.mediaPlayer != null) {
                    Intent intent = new Intent(PlayerActivity.this, EqualizerActivity.class);
                    intent.putExtra("SESSION_ID", MusicService.mediaPlayer.getAudioSessionId());
                    startActivity(intent);
                } else {
                    Toast.makeText(PlayerActivity.this, "Play music to enable Equalizer", Toast.LENGTH_SHORT).show();
                }
            }
        });

        // SeekBar Logic
        musicSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && MusicService.mediaPlayer != null) {
                    MusicService.mediaPlayer.seekTo(progress);
                    txtCurrentTime.setText(formatTime(progress));
                }
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                handler.removeCallbacks(seekUpdateRunnable);
            }
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (MusicService.isPlaying) handler.post(seekUpdateRunnable);
            }
        });

        // Play/Pause Logic
        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bounce(v);
                Intent intent = new Intent(PlayerActivity.this, MusicService.class);
                if (MusicService.isPlaying) {
                    intent.setAction(MusicService.ACTION_PAUSE);
                    MusicService.isPlaying = false;
                } else {
                    intent.setAction(MusicService.ACTION_RESUME);
                    MusicService.isPlaying = true;
                }
                startService(intent);
                updateUI();
            }
        });

        btnNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bounce(v);
                sendActionToService(MusicService.ACTION_NEXT);
                refreshUIWithDelay();
            }
        });

        btnPrev.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                bounce(v);
                sendActionToService(MusicService.ACTION_PREV);
                refreshUIWithDelay();
            }
        });

        setupSeekBarUpdater();
    }

    /** Quick press-feedback animation shared by the transport buttons. */
    private void bounce(View v) {
        if (v == null) return;
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.button_bounce);
        v.startAnimation(anim);
    }

    private void toggleFavorite() {
        if (MusicService.currentIndex < 0 || MusicService.currentIndex >= MusicService.songPaths.size()) {
            Toast.makeText(this, "Play a song first", Toast.LENGTH_SHORT).show();
            return;
        }
        String currentPath = MusicService.songPaths.get(MusicService.currentIndex);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> favorites = new HashSet<String>(prefs.getStringSet("favorites", new HashSet<String>()));

        boolean nowFavorite;
        if (favorites.contains(currentPath)) {
            favorites.remove(currentPath);
            nowFavorite = false;
        } else {
            favorites.add(currentPath);
            nowFavorite = true;
        }
        prefs.edit().putStringSet("favorites", favorites).apply();

        btnFavorite.setImageResource(nowFavorite ? R.drawable.ic_player_heart_filled : R.drawable.ic_player_heart_outline);
        Animation pop = AnimationUtils.loadAnimation(this, R.anim.heart_pop);
        btnFavorite.startAnimation(pop);
    }

    private void updateFavoriteIcon() {
        if (btnFavorite == null) return;
        if (MusicService.currentIndex < 0 || MusicService.currentIndex >= MusicService.songPaths.size()) {
            btnFavorite.setImageResource(R.drawable.ic_player_heart_outline);
            return;
        }
        String currentPath = MusicService.songPaths.get(MusicService.currentIndex);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> favorites = prefs.getStringSet("favorites", new HashSet<String>());
        btnFavorite.setImageResource(favorites.contains(currentPath) ? R.drawable.ic_player_heart_filled : R.drawable.ic_player_heart_outline);
    }

    private void handleDoubleTap(float xPosition, int viewWidth) {
        if (MusicService.mediaPlayer == null) return;

        int currentPos = MusicService.mediaPlayer.getCurrentPosition();
        int seekTime = 10000; // 10 seconds

        if (xPosition < viewWidth / 2.0) {
            int newPos = Math.max(0, currentPos - seekTime);
            MusicService.mediaPlayer.seekTo(newPos);
            showSeekFeedback(R.drawable.ic_rewind_10);
        } else {
            int newPos = Math.min(MusicService.mediaPlayer.getDuration(), currentPos + seekTime);
            MusicService.mediaPlayer.seekTo(newPos);
            showSeekFeedback(R.drawable.ic_forward_10);
        }
        updateUI();
    }

    private void showSeekFeedback(int resId) {
        if (imgSeekFeedback == null) return;

        imgSeekFeedback.setImageResource(resId);
        imgSeekFeedback.setVisibility(View.VISIBLE);
        imgSeekFeedback.setAlpha(1.0f);

        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(1000);
        fadeOut.setFillAfter(true);
        imgSeekFeedback.startAnimation(fadeOut);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                imgSeekFeedback.setVisibility(View.GONE);
            }
        }, 1000);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri audioUri = intent.getData();
            if (audioUri != null) {
                Intent serviceIntent = new Intent(this, MusicService.class);
                serviceIntent.setAction(MusicService.ACTION_PLAY_FROM_URI);
                serviceIntent.setData(audioUri);
                startService(serviceIntent);
                refreshUIWithDelay();
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void setupSeekBarUpdater() {
        seekUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                if (MusicService.mediaPlayer != null) {
                    try {
                        int currentPos = MusicService.mediaPlayer.getCurrentPosition();
                        int totalDuration = MusicService.mediaPlayer.getDuration();
                        musicSeekBar.setMax(totalDuration);
                        musicSeekBar.setProgress(currentPos);
                        txtCurrentTime.setText(formatTime(currentPos));
                        txtTotalTime.setText(formatTime(totalDuration));
                    } catch (Exception e) { /* player mid-transition, skip this tick */ }
                }
                if (MusicService.isPlaying) handler.postDelayed(this, 1000);
            }
        };
    }

    private void updateUI() {
        if (MusicService.currentTitle != null && !MusicService.currentTitle.isEmpty()) {
            txtTitle.setText(MusicService.currentTitle);
            btnPlayPause.setImageResource(MusicService.isPlaying ? R.drawable.pause : R.drawable.play);

            if (MusicService.isPlaying) {
                handler.removeCallbacks(seekUpdateRunnable);
                handler.post(seekUpdateRunnable);
            } else if (MusicService.mediaPlayer != null) {
                int currentPos = MusicService.mediaPlayer.getCurrentPosition();
                int total = MusicService.mediaPlayer.getDuration();
                musicSeekBar.setMax(total);
                musicSeekBar.setProgress(currentPos);
                txtCurrentTime.setText(formatTime(currentPos));
                txtTotalTime.setText(formatTime(total));
            }
        } else {
            txtTitle.setText("No music playing");
            btnPlayPause.setImageResource(R.drawable.play);
        }
        updateRepeatUI();
        updateShuffleUI();
        updateFavoriteIcon();
        updateDiscAnimation();
        updateVisualizer();
    }

    /** Spins the vinyl disc while playing and drops/lifts the tonearm to match. */
    private void updateDiscAnimation() {
        if (imgVinylDisc == null || imgTonearm == null) return;

        if (MusicService.isPlaying) {
            if (!discIsSpinning) {
                Animation spin = AnimationUtils.loadAnimation(this, R.anim.vinyl_spin);
                imgVinylDisc.startAnimation(spin);
                discIsSpinning = true;
            }
            if (!tonearmIsDown) {
                Animation drop = AnimationUtils.loadAnimation(this, R.anim.tonearm_drop);
                imgTonearm.startAnimation(drop);
                tonearmIsDown = true;
            }
        } else {
            if (discIsSpinning) {
                imgVinylDisc.clearAnimation();
                discIsSpinning = false;
            }
            if (tonearmIsDown) {
                Animation lift = AnimationUtils.loadAnimation(this, R.anim.tonearm_lift);
                imgTonearm.startAnimation(lift);
                tonearmIsDown = false;
            }
        }
    }


    /** Attach the built-in Android FFT/waveform visualizer to the current player session. */
    private void updateVisualizer() {
        if (audioVisualizer == null) return;
        if (MusicService.mediaPlayer != null) {
            try {
                audioVisualizer.attachToSession(MusicService.mediaPlayer.getAudioSessionId());
            } catch (Throwable ignored) { }
        } else {
            audioVisualizer.releaseVisualizer();
        }
    }

    private void updateRepeatUI() {
        if (btnRepeat == null) return;
        int activeColor = Color.parseColor("#FF4081");
        int inactiveColor = Color.parseColor("#757575");
        if (MusicService.repeatMode == MusicService.REPEAT_OFF) {
            btnRepeat.setImageResource(R.drawable.repeat);
            btnRepeat.setColorFilter(inactiveColor);
        } else {
            btnRepeat.setImageResource(MusicService.repeatMode == MusicService.REPEAT_ONE ? R.drawable.repeat_once : R.drawable.repeat);
            btnRepeat.setColorFilter(activeColor);
        }
    }

    private void updateShuffleUI() {
        if (btnShuffle == null) return;
        btnShuffle.setColorFilter(MusicService.isShuffle ? Color.parseColor("#FF4081") : Color.parseColor("#757575"));
    }

    private String formatTime(int milliseconds) {
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void sendActionToService(String action) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(action);
        startService(intent);
    }

    private void refreshUIWithDelay() {
        handler.postDelayed(new Runnable() { @Override public void run() { updateUI(); } }, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(seekUpdateRunnable);
        if (audioVisualizer != null) audioVisualizer.releaseVisualizer();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
