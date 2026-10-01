package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Modern framework-only Settings screen.
 *
 * No AndroidX, no AppCompat and no lambda expressions.
 * All settings continue using the same SharedPreferences keys as the
 * original PreferenceActivity, so existing app behaviour is preserved.
 */
public class SettingsActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private LinearLayout content;
    private TextView themeSummary;
    private TextView sortSummary;
    private TextView sleepSummary;
    private CheckBox headsetCheck;
    private CheckBox reminderCheck;

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        buildSettingsScreen();
        applyTheme();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (prefs == null) {
            prefs = PreferenceManager.getDefaultSharedPreferences(this);
        }

        if (content != null) {
            updateDynamicSummaries();
            applyTheme();
        }
    }

    private void buildSettingsScreen() {
        ThemeEngine theme = ThemeEngine.get(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, 0, 0, dp(24));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(28));
        content.setBackgroundColor(theme.background);

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(4), dp(8), dp(4), dp(22));

        TextView title = makeText("Settings", 30, true);
        title.setTextColor(theme.textPrimary);

        TextView subtitle = makeText("Personalize your listening experience", 15, false);
        subtitle.setTextColor(theme.textSecondary);
        subtitle.setPadding(0, dp(5), 0, 0);

        header.addView(title);
        header.addView(subtitle);
        content.addView(header);

        // Sections
        addSection("APPEARANCE", new SectionBuilder() {
            @Override
            public void build(LinearLayout card) {
                View themeRow = makeRow("App Theme", "Choose the look of Muziki", false);
                themeSummary = getRowSummary(themeRow);
                themeRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ThemePickerDialog.show(SettingsActivity.this,
                                new ThemePickerDialog.OnThemeChosenListener() {
                                    @Override
                                    public void onThemeChosen(String themeKey) {
                                        // ThemeEngine was invalidated by the picker.
                                        // Paint this exact Settings screen now.
                                        updateDynamicSummaries();
                                        applyTheme();
                                    }
                                });
                    }
                });
                card.addView(themeRow);

                View sortRow = makeRow("Default Sorting", "Choose how music is sorted on startup", false);
                sortSummary = getRowSummary(sortRow);
                sortRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showSortDialog();
                    }
                });
                card.addView(sortRow);
            }
        });

        addSection("PLAYBACK", new SectionBuilder() {
            @Override
            public void build(LinearLayout card) {
                View sleepRow = makeRow("Sleep Timer", "Stop playback automatically after a set time", false);
                sleepSummary = getRowSummary(sleepRow);
                sleepRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showSleepTimerDialog();
                    }
                });
                card.addView(sleepRow);

                View pauseRow = makeCheckRow("Pause on Unplug",
                        "Pause music when headphones are disconnected");
                headsetCheck = (CheckBox) pauseRow.findViewWithTag("setting_check");
                if (headsetCheck != null) {
                    headsetCheck.setChecked(prefs.getBoolean("pref_headset_pause", true));
                    headsetCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            prefs.edit().putBoolean("pref_headset_pause", isChecked).apply();
                        }
                    });
                }
                card.addView(pauseRow);

                View reminderRow = makeCheckRow("Music Reminders",
                        "Get a reminder when you have not opened Muziki recently");
                reminderCheck = (CheckBox) reminderRow.findViewWithTag("setting_check");
                if (reminderCheck != null) {
                    reminderCheck.setChecked(prefs.getBoolean("pref_song_reminder", true));
                    reminderCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                            prefs.edit().putBoolean("pref_song_reminder", isChecked).apply();
                            if (isChecked) {
                                ReminderScheduler.ensureScheduled(SettingsActivity.this);
                            } else {
                                ReminderScheduler.cancel(SettingsActivity.this);
                            }
                        }
                    });
                }
                card.addView(reminderRow);
            }
        });

        addSection("LIBRARY", new SectionBuilder() {
            @Override
            public void build(LinearLayout card) {
                View cleanupRow = makeRow("Clean Up Broken Files",
                        "Find and remove songs missing from your device", false);
                cleanupRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startBrokenFileCleanup();
                    }
                });
                card.addView(cleanupRow);
            }
        });

        addSection("SUPPORT & CONTACT", new SectionBuilder() {
            @Override
            public void build(LinearLayout card) {
                View emailRow = makeRow("Email Developer",
                        "tresormayuto1962@gmail.com", false);
                emailRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_SENDTO);
                        intent.setData(Uri.parse("mailto:tresormayuto1962@gmail.com"));
                        intent.putExtra(Intent.EXTRA_SUBJECT, "Muziki App Feedback");
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this,
                                    "No email app found", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                card.addView(emailRow);

                View whatsappRow = makeRow("WhatsApp Support",
                        "+257 62488880", false);
                whatsappRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(Intent.ACTION_VIEW);
                        intent.setData(Uri.parse("https://api.whatsapp.com/send?phone=25762488880"));
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(SettingsActivity.this,
                                    "WhatsApp not installed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                card.addView(whatsappRow);

                View portalRow = makeRow("Official Portal",
                        "muziki.42web.io - open securely inside Muziki", false);
                portalRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent intent = new Intent(SettingsActivity.this,
                                InAppBrowserActivity.class);
                        intent.putExtra(InAppBrowserActivity.EXTRA_URL,
                                "https://muziki.42web.io");
                        startActivity(intent);
                    }
                });
                card.addView(portalRow);
            }
        });

        addSection("PREMIUM", new SectionBuilder() {
            @Override
            public void build(LinearLayout card) {
                View proRow = makeRow("Upgrade to PRO",
                        "Remove ads and unlock the Equalizer", true);
                proRow.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(SettingsActivity.this,
                                "Pro version coming soon to Play Store!",
                                Toast.LENGTH_LONG).show();
                    }
                });
                card.addView(proRow);
            }
        });

        addSection("ABOUT MUZIKI", new SectionBuilder() {
            @Override
            public void build(LinearLayout card) {
                View versionRow = makeRow("Version", "2.1.0 · Native Edition", false);
                card.addView(versionRow);

                View description = makeRow("About",
                        "Muziki is a lightweight native music player designed for performance.",
                        false);
                description.setClickable(false);
                card.addView(description);

                View copyright = makeRow("Copyright",
                        "© 2026 Mayuto Dev. All rights reserved.",
                        false);
                copyright.setClickable(false);
                card.addView(copyright);
            }
        });

        scroll.addView(content);
        setContentView(scroll);
    }

    private interface SectionBuilder {
        void build(LinearLayout card);
    }

    private void addSection(String label, SectionBuilder builder) {
        ThemeEngine theme = ThemeEngine.get(this);

        TextView section = makeText(label, 12, true);
        section.setTextColor(theme.textSecondary);
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            section.setLetterSpacing(0.12f);
        }
        section.setPadding(dp(4), dp(16), dp(4), dp(9));
        content.addView(section);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(0, 0, 0, 0);
        card.setBackground(roundBackground(theme.surface, dp(16)));

        builder.build(card);

        card.setTag("settings_card");

        content.addView(card, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private TextView makeText(String text, float size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER_VERTICAL);
        if (bold) {
            view.setTypeface(android.graphics.Typeface.DEFAULT,
                    android.graphics.Typeface.BOLD);
        }
        return view;
    }

    private View makeRow(String title, String summary, boolean accentTitle) {
        ThemeEngine theme = ThemeEngine.get(this);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(15), dp(18), dp(15));
        row.setMinimumHeight(dp(70));
        row.setTag("settings_row");
        row.setBackgroundColor(theme.surface);

        TextView titleView = makeText(title, 16, true);
        titleView.setTag("setting_title");
        titleView.setTextColor(accentTitle ? theme.accent : theme.textPrimary);

        TextView summaryView = makeText(summary, 13, false);
        summaryView.setTag("setting_summary");
        summaryView.setTextColor(theme.textSecondary);
        summaryView.setPadding(0, dp(4), 0, 0);

        row.addView(titleView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(summaryView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addDivider(row);
        return row;
    }

    private View makeCheckRow(String title, String summary) {
        ThemeEngine theme = ThemeEngine.get(this);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(10), dp(10), dp(10));
        row.setMinimumHeight(dp(70));
        row.setTag("settings_row");
        row.setBackgroundColor(theme.surface);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setGravity(Gravity.CENTER_VERTICAL);

        TextView titleView = makeText(title, 16, true);
        titleView.setTag("setting_title");
        TextView summaryView = makeText(summary, 13, false);
        summaryView.setTag("setting_summary");
        summaryView.setTextColor(theme.textSecondary);
        summaryView.setPadding(0, dp(4), 0, 0);

        texts.addView(titleView);
        texts.addView(summaryView);

        LinearLayout.LayoutParams textParams =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(texts, textParams);

        CheckBox check = new CheckBox(this);
        check.setTag("setting_check");
        setCheckTint(check, theme);
        row.addView(check, new LinearLayout.LayoutParams(dp(52), dp(52)));

        addDivider(row);
        return row;
    }

    private void addDivider(LinearLayout row) {
        View divider = new View(this);
        divider.setTag("setting_divider");
        divider.setBackgroundColor(ThemeEngine.get(this).divider);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        params.leftMargin = dp(18);
        row.addView(divider, params);
    }

    private GradientDrawable roundBackground(int color, float radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        bg.setStroke(dp(1), ThemeEngine.get(this).divider);
        return bg;
    }

    private TextView getRowSummary(View row) {
        if (!(row instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) row;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && i > 0) {
                return (TextView) child;
            }
        }
        return null;
    }

    private void updateDynamicSummaries() {
        ThemeEngine theme = ThemeEngine.get(this);

        String key = prefs.getString(ThemeEngine.PREF_KEY, "white");
        for (int i = 0; i < ThemeEngine.THEME_KEYS.length; i++) {
            if (ThemeEngine.THEME_KEYS[i].equals(key)) {
                if (themeSummary != null) {
                    themeSummary.setText(ThemeEngine.THEME_LABELS[i]);
                }
                break;
            }
        }

        String sort = prefs.getString("pref_sort_default", "title");
        if ("artist".equals(sort)) {
            if (sortSummary != null) sortSummary.setText("Artist");
        } else if ("date".equals(sort)) {
            if (sortSummary != null) sortSummary.setText("Recently added");
        } else {
            if (sortSummary != null) sortSummary.setText("Title");
        }

        if (sleepSummary != null) {
            long remainingMillis = SleepTimer.getRemainingMillis(this);
            if (remainingMillis <= 0) {
                sleepSummary.setText("Off");
            } else {
                long totalMinutes = (remainingMillis / 1000L) / 60L;
                long hours = totalMinutes / 60L;
                long minutes = totalMinutes % 60L;
                if (hours > 0) {
                    sleepSummary.setText("Playback stops in " + hours + "h " + minutes + "m");
                } else {
                    sleepSummary.setText("Playback stops in " + minutes + "m");
                }
            }
        }
    }

    private void applyTheme() {
        ThemeEngine theme = ThemeEngine.get(this);
        ThemeEngine.applyToActivity(this);

        if (content != null) {
            content.setBackgroundColor(theme.background);
        }

        // Rebuild only the visual layer, never the stored settings.
        if (themeSummary != null) themeSummary.setTextColor(theme.textSecondary);
        if (sortSummary != null) sortSummary.setTextColor(theme.textSecondary);
        if (sleepSummary != null) sleepSummary.setTextColor(theme.textSecondary);

        if (headsetCheck != null) {
            setCheckTint(headsetCheck, theme);
        }
        if (reminderCheck != null) {
            setCheckTint(reminderCheck, theme);
        }

        repaintSettingsTree(content, theme);
    }

    private void repaintSettingsTree(View view, ThemeEngine theme) {
        if (view == null) return;

        Object tag = view.getTag();

        if ("setting_divider".equals(tag)) {
            view.setBackgroundColor(theme.divider);
        } else if ("settings_card".equals(tag)) {
            view.setBackground(roundBackground(theme.surface, dp(16)));
        } else if ("settings_row".equals(tag)) {
            view.setBackgroundColor(theme.surface);
        } else if ("setting_summary".equals(tag)) {
            ((TextView) view).setTextColor(theme.textSecondary);
        } else if ("setting_title".equals(tag)) {
            TextView text = (TextView) view;
            if ("Upgrade to PRO".equals(text.getText().toString())) {
                text.setTextColor(theme.accent);
            } else {
                text.setTextColor(theme.textPrimary);
            }
        } else if (view instanceof TextView) {
            TextView text = (TextView) view;
            String value = text.getText() == null ? "" : text.getText().toString();
            if (value.equals("APPEARANCE") || value.equals("PLAYBACK")
                    || value.equals("LIBRARY") || value.equals("SUPPORT & CONTACT")
                    || value.equals("PREMIUM") || value.equals("ABOUT MUZIKI")) {
                text.setTextColor(theme.textSecondary);
            } else {
                text.setTextColor(theme.textPrimary);
            }
        }

        if (view instanceof CheckBox) {
            setCheckTint((CheckBox) view, theme);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                repaintSettingsTree(group.getChildAt(i), theme);
            }
        }
    }

    private void setCheckTint(CheckBox check, ThemeEngine theme) {
        if (check == null || android.os.Build.VERSION.SDK_INT < 21) return;

        int[][] states = new int[][] {
                new int[] { android.R.attr.state_checked },
                new int[] { -android.R.attr.state_checked }
        };
        int[] colors = new int[] { theme.accent, theme.textSecondary };
        check.setButtonTintList(new android.content.res.ColorStateList(states, colors));
    }

    private void showSortDialog() {
        final String[] labels = {"Title", "Artist", "Recently added"};
        final String[] values = {"title", "artist", "date"};
        String current = prefs.getString("pref_sort_default", "title");
        int selected = 0;

        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selected = i;
                break;
            }
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Default Sorting")
                .setSingleChoiceItems(labels, selected, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        prefs.edit().putString("pref_sort_default", values[which]).apply();
                        updateDynamicSummaries();
                        dialogInterface.dismiss();
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void showSleepTimerDialog() {
        final String[] labels;
        final int[] minutesValues = {15, 30, 45, 60, 90};

        if (SleepTimer.isActive(this)) {
            labels = new String[]{"15 minutes", "30 minutes", "45 minutes",
                    "1 hour", "1 hour 30 min", "Cancel timer"};
        } else {
            labels = new String[]{"15 minutes", "30 minutes", "45 minutes",
                    "1 hour", "1 hour 30 min"};
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Stop playback after...")
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        if (which < minutesValues.length) {
                            SleepTimer.start(SettingsActivity.this, minutesValues[which]);
                            Toast.makeText(SettingsActivity.this,
                                    "Playback will stop in " + labels[which],
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            SleepTimer.cancel(SettingsActivity.this);
                            Toast.makeText(SettingsActivity.this,
                                    "Sleep timer cancelled", Toast.LENGTH_SHORT).show();
                        }
                        updateDynamicSummaries();
                    }
                })
                .create();
        dialog.show();
        styleDialog(dialog);
    }

    private void styleDialog(AlertDialog dialog) {
        ThemeEngine theme = ThemeEngine.get(this);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(roundBackground(theme.surface, dp(16)));
        }
    }

    private void startBrokenFileCleanup() {
        final ProgressDialog scanningDialog = new ProgressDialog(this);
        scanningDialog.setMessage("Scanning your library for broken files...");
        scanningDialog.setCancelable(false);
        scanningDialog.show();

        LibraryCleanupHelper.scanAndClean(this, true,
                new LibraryCleanupHelper.CleanupCallback() {
                    @Override
                    public void onCleanupFinished(
                            final LibraryCleanupHelper.CleanupResult dryRunResult) {
                        scanningDialog.dismiss();
                        if (isFinishing()) return;

                        if (dryRunResult.removed == 0) {
                            new AlertDialog.Builder(SettingsActivity.this)
                                    .setTitle("Your library is clean")
                                    .setMessage("Scanned " + dryRunResult.scanned
                                            + " songs. No broken or missing files were found.")
                                    .setPositiveButton("OK", null)
                                    .show();
                            return;
                        }

                        String message = dryRunResult.removed == 1
                                ? "Found 1 song that's missing or deleted from your device. "
                                + "Remove it from Muziki?"
                                : "Found " + dryRunResult.removed
                                + " songs that are missing or deleted from your device. "
                                + "Remove them from Muziki?";

                        new AlertDialog.Builder(SettingsActivity.this)
                                .setTitle("Broken files found")
                                .setMessage(message)
                                .setPositiveButton("Remove",
                                        new DialogInterface.OnClickListener() {
                                            @Override
                                            public void onClick(DialogInterface dialog, int which) {
                                                runRealCleanup();
                                            }
                                        })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
    }

    private void runRealCleanup() {
        final ProgressDialog cleaningDialog = new ProgressDialog(this);
        cleaningDialog.setMessage("Cleaning up your library...");
        cleaningDialog.setCancelable(false);
        cleaningDialog.show();

        LibraryCleanupHelper.scanAndClean(this, false,
                new LibraryCleanupHelper.CleanupCallback() {
                    @Override
                    public void onCleanupFinished(
                            LibraryCleanupHelper.CleanupResult result) {
                        cleaningDialog.dismiss();
                        if (isFinishing()) return;

                        String message = result.removed == 1
                                ? "Removed 1 broken song from your library."
                                : "Removed " + result.removed
                                + " broken songs from your library.";
                        Toast.makeText(SettingsActivity.this,
                                message, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
