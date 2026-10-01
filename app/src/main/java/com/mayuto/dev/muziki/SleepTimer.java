package com.mayuto.dev.muziki;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

/**
 * Real sleep timer, backed by AlarmManager instead of an in-memory
 * Handler/CountDownTimer.
 *
 * Why AlarmManager and not a Handler: a Handler-based countdown lives inside
 * whatever screen created it (e.g. SettingsActivity) and inside MusicService's
 * process. If the user leaves Settings, locks the phone, or the system trims
 * memory, an in-memory timer silently stops counting and the "sleep timer"
 * quietly does nothing - which is exactly the placeholder-that-doesn't-work
 * problem we're fixing. An exact AlarmManager alarm is owned by the system,
 * so it fires at the right wall-clock time regardless of what screen is open
 * or whether the app process was trimmed in between.
 *
 * When the alarm fires, MusicService.ACTION_SLEEP_TIMER_FIRED pauses playback
 * (same as pressing pause) and clears the saved timer state.
 */
public class SleepTimer {

    private static final String PREF_END_TIME = "sleep_timer_end_time";
    private static final int REQUEST_CODE = 6001;

    private SleepTimer() { }

    /** Starts (or replaces) the sleep timer to fire after the given number of minutes. */
    public static void start(Context context, int minutes) {
        long endTime = System.currentTimeMillis() + (minutes * 60L * 1000L);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putLong(PREF_END_TIME, endTime).apply();

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endTime, pendingIntent);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, endTime, pendingIntent);
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, endTime, pendingIntent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Cancels any pending sleep timer. */
    public static void cancel(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(buildPendingIntent(context));
        }
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().remove(PREF_END_TIME).apply();
    }

    /** Milliseconds remaining until the timer fires, or 0 if none is active/already passed. */
    public static long getRemainingMillis(Context context) {
        long endTime = PreferenceManager.getDefaultSharedPreferences(context).getLong(PREF_END_TIME, 0L);
        long remaining = endTime - System.currentTimeMillis();
        return remaining > 0 ? remaining : 0L;
    }

    public static boolean isActive(Context context) {
        return getRemainingMillis(context) > 0;
    }

    private static PendingIntent buildPendingIntent(Context context) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(MusicService.ACTION_SLEEP_TIMER_FIRED);
        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        // Sleep timer delivers straight to the service (not a broadcast) so it
        // can pause mediaPlayer directly even if no Activity is on screen.
        return PendingIntent.getService(context, REQUEST_CODE, intent, flags);
    }
}
