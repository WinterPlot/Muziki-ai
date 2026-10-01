package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.LoudnessEnhancer;
import android.media.audiofx.PresetReverb;
import android.media.audiofx.Virtualizer;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * Audio FX screen — Mr DJ Treyzo Sound Engine.
 *
 * Beyond the classic 5-band Equalizer + presets, this screen wires up four
 * extra android.media.audiofx effects that ship with the platform but were
 * previously unused: BassBoost, Virtualizer (3D surround), PresetReverb
 * (room ambience) and LoudnessEnhancer (post-gain make-up volume). Every
 * effect is attached to the same audio session as the Equalizer so they all
 * apply together to whatever MusicService is currently playing.
 *
 * Every screen color is pulled from ThemeEngine so this looks correct across
 * all 6 app themes, and the "Mr DJ TREYZO" brand banner at the top uses the
 * app's constant accent pink so it always pops regardless of theme.
 */
public class EqualizerActivity extends AppCompatActivity {

    private Equalizer mEqualizer;
    private BassBoost mBassBoost;
    private Virtualizer mVirtualizer;
    private PresetReverb mPresetReverb;
    private LoudnessEnhancer mLoudnessEnhancer;

    private LinearLayout eqContainer;
    private LinearLayout eqRootLayout;
    private ScrollView eqScrollRoot;

    private ThemeEngine theme;

    // Cards, so Reset All + theming can walk them generically.
    private LinearLayout cardPresets, cardBands, cardBass, cardVirtualizer, cardReverb, cardLoudness, cardSpeedPitch;
    private LinearLayout djBrandBanner, btnResetAll;

    private TextView txtEqTitle, txtResetAll, txtPresetLabel, txtBandsLabel;
    private TextView txtBassLabel, txtVirtualizerLabel, txtReverbLabel, txtLoudnessLabel, txtSpeedPitchLabel;
    private TextView txtDjBrandName, txtDjBrandTagline;
    private TextView txtSpeedValue, txtPitchValue, btnSpeedPitchResetSection;

    private CheckBox toggleBass, toggleVirtualizer, toggleReverb, toggleLoudness, toggleLinkPitch;
    private SeekBar seekBass, seekVirtualizer, seekLoudness, seekSpeed, seekPitch;
    private Spinner presetSpinner, reverbSpinner;

    private boolean bassAvailable, virtualizerAvailable, reverbAvailable, loudnessAvailable;
    private boolean speedPitchAvailable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_equalizer);

        theme = ThemeEngine.get(this);

        bindViews();
        applyTheme();

        int sessionId = getIntent().getIntExtra("SESSION_ID", 0);

        try {
            // ---- Core Equalizer (unchanged behavior) ----
            mEqualizer = new Equalizer(0, sessionId);
            mEqualizer.setEnabled(true);
            setupPresetSpinner();
            setupFrequencyBands();
        } catch (Exception e) {
            e.printStackTrace();
            showErrorState();
            return;
        }

        // ---- New: Bass Boost ----
        try {
            mBassBoost = new BassBoost(0, sessionId);
            bassAvailable = true;
            setupBassBoost();
        } catch (Exception e) {
            bassAvailable = false;
            disableCard(cardBass, getString(R.string.eq_bass_unavailable));
        }

        // ---- New: Playback Speed & Pitch ----
        speedPitchAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
        if (speedPitchAvailable) {
            setupSpeedPitch();
        } else {
            disableCard(cardSpeedPitch, getString(R.string.eq_speed_pitch_unavailable));
        }

        // ---- New: Virtualizer (3D Surround) ----
        try {
            mVirtualizer = new Virtualizer(0, sessionId);
            virtualizerAvailable = true;
            setupVirtualizer();
        } catch (Exception e) {
            virtualizerAvailable = false;
            disableCard(cardVirtualizer, getString(R.string.eq_virtualizer_unavailable));
        }

        // ---- New: Preset Reverb (room ambience) ----
        try {
            mPresetReverb = new PresetReverb(0, sessionId);
            reverbAvailable = true;
            setupReverb();
        } catch (Exception e) {
            reverbAvailable = false;
            disableCard(cardReverb, getString(R.string.eq_reverb_unavailable));
        }

        // ---- New: Loudness Enhancer (make-up gain) ----
        try {
            mLoudnessEnhancer = new LoudnessEnhancer(sessionId);
            loudnessAvailable = true;
            setupLoudness();
        } catch (Exception e) {
            loudnessAvailable = false;
            disableCard(cardLoudness, getString(R.string.eq_loudness_unavailable));
        }

        setupResetAll();
    }

    private void bindViews() {
        eqScrollRoot = (ScrollView) findViewById(R.id.eqScrollRoot);
        eqRootLayout = (LinearLayout) findViewById(R.id.eqRootLayout);
        eqContainer = (LinearLayout) findViewById(R.id.eqContainer);

        djBrandBanner = (LinearLayout) findViewById(R.id.djBrandBanner);
        txtDjBrandName = (TextView) findViewById(R.id.txtDjBrandName);
        txtDjBrandTagline = (TextView) findViewById(R.id.txtDjBrandTagline);

        txtEqTitle = (TextView) findViewById(R.id.txtEqTitle);
        btnResetAll = (LinearLayout) findViewById(R.id.btnResetAll);
        txtResetAll = (TextView) findViewById(R.id.txtResetAll);

        cardPresets = (LinearLayout) findViewById(R.id.cardPresets);
        cardBands = (LinearLayout) findViewById(R.id.cardBands);
        cardBass = (LinearLayout) findViewById(R.id.cardBass);
        cardVirtualizer = (LinearLayout) findViewById(R.id.cardVirtualizer);
        cardReverb = (LinearLayout) findViewById(R.id.cardReverb);
        cardLoudness = (LinearLayout) findViewById(R.id.cardLoudness);
        cardSpeedPitch = (LinearLayout) findViewById(R.id.cardSpeedPitch);

        txtPresetLabel = (TextView) findViewById(R.id.txtPresetLabel);
        txtBandsLabel = (TextView) findViewById(R.id.txtBandsLabel);
        txtBassLabel = (TextView) findViewById(R.id.txtBassLabel);
        txtVirtualizerLabel = (TextView) findViewById(R.id.txtVirtualizerLabel);
        txtReverbLabel = (TextView) findViewById(R.id.txtReverbLabel);
        txtLoudnessLabel = (TextView) findViewById(R.id.txtLoudnessLabel);
        txtSpeedPitchLabel = (TextView) findViewById(R.id.txtSpeedPitchLabel);
        txtSpeedValue = (TextView) findViewById(R.id.txtSpeedValue);
        txtPitchValue = (TextView) findViewById(R.id.txtPitchValue);
        btnSpeedPitchResetSection = (TextView) findViewById(R.id.btnSpeedPitchResetSection);

        presetSpinner = (Spinner) findViewById(R.id.presetSpinner);
        reverbSpinner = (Spinner) findViewById(R.id.reverbSpinner);

        toggleBass = (CheckBox) findViewById(R.id.toggleBass);
        toggleVirtualizer = (CheckBox) findViewById(R.id.toggleVirtualizer);
        toggleReverb = (CheckBox) findViewById(R.id.toggleReverb);
        toggleLoudness = (CheckBox) findViewById(R.id.toggleLoudness);
        toggleLinkPitch = (CheckBox) findViewById(R.id.toggleLinkPitch);

        seekBass = (SeekBar) findViewById(R.id.seekBass);
        seekVirtualizer = (SeekBar) findViewById(R.id.seekVirtualizer);
        seekLoudness = (SeekBar) findViewById(R.id.seekLoudness);
        seekSpeed = (SeekBar) findViewById(R.id.seekSpeed);
        seekPitch = (SeekBar) findViewById(R.id.seekPitch);
    }

    /** Paints every card/background/text color from the active ThemeEngine palette. */
    private void applyTheme() {
        ThemeEngine.applyToActivity(this);
        eqRootLayout.setBackgroundColor(theme.background);
        txtEqTitle.setTextColor(theme.textPrimary);
        txtResetAll.setTextColor(theme.textSecondary);

        txtPresetLabel.setTextColor(theme.textSecondary);
        txtBandsLabel.setTextColor(theme.textSecondary);
        txtBassLabel.setTextColor(theme.textSecondary);
        txtVirtualizerLabel.setTextColor(theme.textSecondary);
        txtReverbLabel.setTextColor(theme.textSecondary);
        txtLoudnessLabel.setTextColor(theme.textSecondary);
        txtSpeedPitchLabel.setTextColor(theme.textSecondary);

        setCardSurface(cardPresets);
        setCardSurface(cardBands);
        setCardSurface(cardBass);
        setCardSurface(cardVirtualizer);
        setCardSurface(cardReverb);
        setCardSurface(cardLoudness);
        setCardSurface(cardSpeedPitch);

        // Brand banner keeps the constant accent gradient (it's the DJ Treyzo
        // signature look) regardless of theme, so it isn't re-tinted here.
    }

    private void setCardSurface(LinearLayout card) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(theme.surface);
        bg.setCornerRadius(dp(16));
        card.setBackgroundDrawable(bg);
    }

    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }

    // ============================= EQUALIZER =============================

    private void setupPresetSpinner() {
        short numPresets = mEqualizer.getNumberOfPresets();
        final String[] presets = new String[numPresets];

        for (short i = 0; i < numPresets; i++) {
            presets[i] = mEqualizer.getPresetName(i);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, presets);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);

        presetSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    mEqualizer.usePreset((short) position);
                    updateSliders();
                } catch (Exception e) {
                    // Handle potential preset switching errors
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void setupFrequencyBands() {
        final short bands = mEqualizer.getNumberOfBands();
        final short minLevel = mEqualizer.getBandLevelRange()[0];
        final short maxLevel = mEqualizer.getBandLevelRange()[1];

        for (short i = 0; i < bands; i++) {
            final short band = i;

            LinearLayout bandLayout = new LinearLayout(this);
            bandLayout.setOrientation(LinearLayout.VERTICAL);
            bandLayout.setPadding(0, 10, 0, 30);

            int milliHz = mEqualizer.getCenterFreq(band);
            String freqLabel = (milliHz < 1000000) ?
                               (milliHz / 1000) + " Hz" :
                               (milliHz / 1000000.0) + " kHz";

            TextView freqTitle = new TextView(this);
            freqTitle.setText(freqLabel);
            freqTitle.setTextSize(14);
            freqTitle.setTextColor(theme.textSecondary);
            freqTitle.setTypeface(null, Typeface.BOLD);
            freqTitle.setPadding(10, 0, 0, 8);

            SeekBar bar = new SeekBar(this);
            bar.setId(i + 1000); // offset to avoid clashing with layout ids
            bar.setMax(maxLevel - minLevel);
            bar.setProgress(mEqualizer.getBandLevel(band) - minLevel);

            bar.setProgressDrawable(getResources().getDrawable(R.drawable.custom_seekbar_track));
            bar.setThumb(getResources().getDrawable(R.drawable.custom_thumb));

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser) {
                        mEqualizer.setBandLevel(band, (short) (progress + minLevel));
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });

            bandLayout.addView(freqTitle);
            bandLayout.addView(bar);
            eqContainer.addView(bandLayout);
        }
    }

    private void updateSliders() {
        short bands = mEqualizer.getNumberOfBands();
        short minLevel = mEqualizer.getBandLevelRange()[0];
        for (short i = 0; i < bands; i++) {
            SeekBar bar = (SeekBar) eqContainer.findViewById(i + 1000);
            if (bar != null) {
                bar.setProgress(mEqualizer.getBandLevel(i) - minLevel);
            }
        }
    }

    // ============================= BASS BOOST =============================

    private void setupBassBoost() {
        boolean strengthSupported = mBassBoost.getStrengthSupported();
        seekBass.setEnabled(strengthSupported);
        toggleBass.setEnabled(strengthSupported);

        short currentStrength = 0;
        try {
            currentStrength = mBassBoost.getRoundedStrength();
        } catch (Exception ignored) {}

        boolean enabled = currentStrength > 0;
        mBassBoost.setEnabled(enabled);
        toggleBass.setChecked(enabled);
        seekBass.setProgress(currentStrength);

        toggleBass.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    mBassBoost.setEnabled(isChecked);
                } catch (Exception ignored) {}
            }
        });

        seekBass.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    try {
                        mBassBoost.setStrength((short) progress);
                        if (progress > 0 && !toggleBass.isChecked()) {
                            toggleBass.setChecked(true);
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ============================= PLAYBACK SPEED & PITCH =============================

    /**
     * 0.5x-2.0x speed and pitch, applied live to MusicService's MediaPlayer
     * via a fire-and-forget Intent (MusicService is a started, not bound,
     * service here, so we talk to it the same way the rest of the app's
     * transport controls already do - through startService actions).
     *
     * SeekBar progress 0..150 maps directly to speed/pitch 0.50x..2.00x
     * (progress/100 + 0.5), matching android:max="150" in the layout.
     *
     * "Link pitch to speed" is on by default so dragging Speed moves Pitch
     * in lockstep, giving the classic "vinyl/tape" pitch-follows-speed
     * feel. Unchecking it frees Pitch to be set independently (useful for
     * slowing a song down without dropping its key, and vice versa).
     */
    private void setupSpeedPitch() {
        float savedSpeed = MusicService.playbackSpeed;
        float savedPitch = MusicService.playbackPitch;

        seekSpeed.setProgress(speedToProgress(savedSpeed));
        seekPitch.setProgress(speedToProgress(savedPitch));
        txtSpeedValue.setText(formatRate(savedSpeed));
        txtPitchValue.setText(formatRate(savedPitch));

        seekSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float speed = progressToSpeed(progress);
                txtSpeedValue.setText(formatRate(speed));
                if (fromUser) {
                    sendSpeedToService(speed);
                    if (toggleLinkPitch.isChecked()) {
                        seekPitch.setProgress(progress);
                        txtPitchValue.setText(formatRate(speed));
                        sendPitchToService(speed);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekPitch.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float pitch = progressToSpeed(progress);
                txtPitchValue.setText(formatRate(pitch));
                if (fromUser) {
                    sendPitchToService(pitch);
                    // Manually dragging Pitch away from Speed implies the
                    // user wants them independent from here on.
                    if (toggleLinkPitch.isChecked() && seekPitch.getProgress() != seekSpeed.getProgress()) {
                        toggleLinkPitch.setChecked(false);
                    }
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSpeedPitchResetSection.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLinkPitch.setChecked(true);
                seekSpeed.setProgress(speedToProgress(1.0f));
                seekPitch.setProgress(speedToProgress(1.0f));
                txtSpeedValue.setText(formatRate(1.0f));
                txtPitchValue.setText(formatRate(1.0f));
                sendSpeedToService(1.0f);
                sendPitchToService(1.0f);
            }
        });
    }

    private int speedToProgress(float rate) {
        int progress = Math.round((rate - MusicService.MIN_PLAYBACK_SPEED) * 100f);
        return clamp(progress, 0, 150);
    }

    private float progressToSpeed(int progress) {
        return MusicService.MIN_PLAYBACK_SPEED + (progress / 100f);
    }

    private String formatRate(float rate) {
        return String.format(java.util.Locale.US, "%.2fx", rate);
    }

    private void sendSpeedToService(float speed) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_SET_PLAYBACK_SPEED);
        intent.putExtra(MusicService.EXTRA_SPEED, speed);
        startService(intent);
    }

    private void sendPitchToService(float pitch) {
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_SET_PLAYBACK_PITCH);
        intent.putExtra(MusicService.EXTRA_PITCH, pitch);
        startService(intent);
    }

    // ============================= VIRTUALIZER (3D SURROUND) =============================

    private void setupVirtualizer() {
        boolean strengthSupported = mVirtualizer.getStrengthSupported();
        seekVirtualizer.setEnabled(strengthSupported);
        toggleVirtualizer.setEnabled(strengthSupported);

        short currentStrength = 0;
        try {
            currentStrength = mVirtualizer.getRoundedStrength();
        } catch (Exception ignored) {}

        boolean enabled = currentStrength > 0;
        mVirtualizer.setEnabled(enabled);
        toggleVirtualizer.setChecked(enabled);
        seekVirtualizer.setProgress(currentStrength);

        toggleVirtualizer.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    mVirtualizer.setEnabled(isChecked);
                } catch (Exception ignored) {}
            }
        });

        seekVirtualizer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    try {
                        mVirtualizer.setStrength((short) progress);
                        if (progress > 0 && !toggleVirtualizer.isChecked()) {
                            toggleVirtualizer.setChecked(true);
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ============================= PRESET REVERB =============================

    private void setupReverb() {
        final String[] roomNames = getResources().getStringArray(R.array.eq_reverb_room_names);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item, roomNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        reverbSpinner.setAdapter(adapter);

        short currentPreset = PresetReverb.PRESET_NONE;
        try {
            currentPreset = mPresetReverb.getPreset();
        } catch (Exception ignored) {}

        boolean enabled = currentPreset != PresetReverb.PRESET_NONE;
        mPresetReverb.setEnabled(enabled);
        toggleReverb.setChecked(enabled);
        reverbSpinner.setSelection(clamp(currentPreset, 0, roomNames.length - 1));

        toggleReverb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    mPresetReverb.setEnabled(isChecked);
                } catch (Exception ignored) {}
            }
        });

        reverbSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    mPresetReverb.setPreset((short) position);
                    boolean shouldEnable = position != PresetReverb.PRESET_NONE;
                    mPresetReverb.setEnabled(shouldEnable);
                    if (toggleReverb.isChecked() != shouldEnable) {
                        toggleReverb.setChecked(shouldEnable);
                    }
                } catch (Exception ignored) {}
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    // ============================= LOUDNESS ENHANCER =============================

    private void setupLoudness() {
        float currentGainMb = 0f;
        try {
            currentGainMb = mLoudnessEnhancer.getTargetGain();
        } catch (Exception ignored) {}

        boolean enabled = currentGainMb > 0f;
        mLoudnessEnhancer.setEnabled(enabled);
        toggleLoudness.setChecked(enabled);
        seekLoudness.setProgress((int) currentGainMb);

        toggleLoudness.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    mLoudnessEnhancer.setEnabled(isChecked);
                } catch (Exception ignored) {}
            }
        });

        seekLoudness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    try {
                        mLoudnessEnhancer.setTargetGain(progress);
                        if (progress > 0 && !toggleLoudness.isChecked()) {
                            toggleLoudness.setChecked(true);
                        }
                    } catch (Exception ignored) {}
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    // ============================= RESET ALL =============================

    private void setupResetAll() {
        btnResetAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mEqualizer.usePreset((short) 0);
                    updateSliders();
                    presetSpinner.setSelection(0);
                } catch (Exception ignored) {}

                if (bassAvailable) {
                    try {
                        mBassBoost.setStrength((short) 0);
                        mBassBoost.setEnabled(false);
                        toggleBass.setChecked(false);
                        seekBass.setProgress(0);
                    } catch (Exception ignored) {}
                }

                if (virtualizerAvailable) {
                    try {
                        mVirtualizer.setStrength((short) 0);
                        mVirtualizer.setEnabled(false);
                        toggleVirtualizer.setChecked(false);
                        seekVirtualizer.setProgress(0);
                    } catch (Exception ignored) {}
                }

                if (reverbAvailable) {
                    try {
                        mPresetReverb.setPreset(PresetReverb.PRESET_NONE);
                        mPresetReverb.setEnabled(false);
                        toggleReverb.setChecked(false);
                        reverbSpinner.setSelection(0);
                    } catch (Exception ignored) {}
                }

                if (loudnessAvailable) {
                    try {
                        mLoudnessEnhancer.setTargetGain(0);
                        mLoudnessEnhancer.setEnabled(false);
                        toggleLoudness.setChecked(false);
                        seekLoudness.setProgress(0);
                    } catch (Exception ignored) {}
                }

                if (speedPitchAvailable) {
                    toggleLinkPitch.setChecked(true);
                    seekSpeed.setProgress(speedToProgress(1.0f));
                    seekPitch.setProgress(speedToProgress(1.0f));
                    txtSpeedValue.setText(formatRate(1.0f));
                    txtPitchValue.setText(formatRate(1.0f));
                    sendSpeedToService(1.0f);
                    sendPitchToService(1.0f);
                }
            }
        });
    }

    // ============================= HELPERS =============================

    private int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private void disableCard(LinearLayout card, String message) {
        // Dim the whole card and swap in an explanatory message so the user
        // understands why the toggle/slider don't respond, instead of the
        // controls silently doing nothing.
        card.setAlpha(0.45f);
        TextView note = new TextView(this);
        note.setText(message);
        note.setTextSize(12);
        note.setTextColor(theme.textSecondary);
        note.setPadding(0, 8, 0, 0);
        card.addView(note);
    }

    private void showErrorState() {
        TextView errorText = new TextView(this);
        errorText.setText(R.string.eq_unavailable);
        errorText.setGravity(Gravity.CENTER);
        errorText.setTextColor(theme.textSecondary);
        errorText.setPadding(20, 50, 20, 20);
        eqRootLayout.addView(errorText);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Intentionally NOT releasing mEqualizer/mBassBoost/mVirtualizer/
        // mPresetReverb/mLoudnessEnhancer here. They're attached to the
        // MusicService's audio session (not this Activity's lifecycle), so
        // releasing them on exit would silently undo the user's settings
        // the moment they leave this screen while music keeps playing in
        // the background — same reasoning as the original Equalizer-only
        // implementation.
    }
}
