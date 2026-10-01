package com.mayuto.dev.muziki;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Fires when the device finishes booting (or after the app is updated /
 * re-enabled). AlarmManager alarms are wiped on reboot, so this is what
 * re-establishes the daily "song waiting for you" reminder check without
 * requiring the user to open the app first.
 *
 * This receiver does NOT start music playback automatically - it only makes
 * sure the reminder alarm exists again. Music itself only ever plays when the
 * user asks it to, from MainActivity/PlayerActivity.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;

        String action = intent.getAction();
        boolean isBootAction = Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);

        if (!isBootAction) return;

        // Only reschedule if there is actually a "last played song" worth
        // reminding the user about, and the user hasn't disabled reminders.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String lastTitle = prefs.getString("saved_song_title", "");
        boolean reminderEnabled = prefs.getBoolean("pref_song_reminder", true);
        if (reminderEnabled && lastTitle != null && !lastTitle.isEmpty()) {
            ReminderScheduler.ensureScheduled(context);
        }
    }
}
