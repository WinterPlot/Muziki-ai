package com.mayuto.dev.muziki;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

/**
 * Owns the scheduling of the "your song is waiting for you" reminder check.
 *
 * Design: instead of trying to keep one long-lived exact alarm alive forever
 * (fragile across reboots, Doze, and OEM battery killers), we schedule a
 * repeating inexact alarm that fires roughly once a day. Every time it fires,
 * {@link ReminderReceiver} decides - based on saved SharedPreferences state -
 * whether the user already opened the app "today" (relative to the last time
 * they played a song) and only posts a notification if not.
 *
 * This class is called from three places:
 *  - WelcomeActivity/PermissionGuideActivity/MainActivity, right after the
 *    user has music playing, so the schedule starts as soon as there's
 *    something to remind them about.
 *  - BootReceiver, so the schedule survives device reboots (AlarmManager
 *    alarms do NOT survive a reboot by themselves).
 *  - MusicService, whenever a new song starts playing, to make sure a
 *    schedule always exists once there's a "last played" song to remember.
 */
public class ReminderScheduler {

    // Roughly 24 hours. Being "inexact" (setInexactRepeating / setAndAllowWhileIdle
    // loop) is intentional - it plays nicely with Doze/battery optimization
    // instead of fighting the system for exact daily wake-ups.
    private static final long INTERVAL_MILLIS = AlarmManager.INTERVAL_DAY;

    public static final String ACTION_REMINDER_CHECK = "com.mayuto.dev.muziki.ACTION_REMINDER_CHECK";
    private static final int REQUEST_CODE = 5001;

    private ReminderScheduler() { }

    /** Ensures the repeating reminder-check alarm is scheduled. Safe to call many times. */
    public static void ensureScheduled(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean reminderEnabled = prefs.getBoolean("pref_song_reminder", true);
        if (!reminderEnabled) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context);

        long firstTriggerAt = System.currentTimeMillis() + INTERVAL_MILLIS;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Inexact but battery-friendly; system will batch this with other alarms.
                alarmManager.setInexactRepeating(AlarmManager.RTC, firstTriggerAt, INTERVAL_MILLIS, pendingIntent);
            } else {
                alarmManager.setRepeating(AlarmManager.RTC, firstTriggerAt, INTERVAL_MILLIS, pendingIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Cancels the reminder schedule entirely (e.g. if the user disables reminders in Settings). */
    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        alarmManager.cancel(buildPendingIntent(context));
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER_CHECK);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags);
    }
}
