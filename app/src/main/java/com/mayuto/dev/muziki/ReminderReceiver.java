package com.mayuto.dev.muziki;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;

/**
 * Runs once a day (see {@link ReminderScheduler}). Its only job: if the user
 * has a "last played song" saved AND has not opened muziki since they last
 * listened to it AND we have not already nagged them today, post a
 * notification inviting them back.
 *
 * Example the user asked for:
 *  - Last night the user played "Song A" then closed the app.
 *  - Today they have not opened muziki at all.
 *  - This receiver fires, sees "last opened" is still older than "last
 *    played", and shows: "Song A is waiting for you".
 *
 * If the user DID open the app today (MainActivity.onResume() stamps
 * "last_app_opened_time"), this receiver stays silent - the user already
 * saw their music, no need to nag them.
 */
public class ReminderReceiver extends BroadcastReceiver {

    static final String PREF_LAST_PLAYED_TIME = "last_played_time";
    static final String PREF_LAST_APP_OPENED_TIME = "last_app_opened_time";
    static final String PREF_LAST_REMINDER_SHOWN_TIME = "last_reminder_shown_time";

    private static final String CHANNEL_ID = "muziki_reminder_channel";
    private static final int NOTIFICATION_ID = 2;

    // Don't nag more than once in this window, even if the alarm fires more
    // than once for any reason.
    private static final long MIN_GAP_BETWEEN_REMINDERS_MILLIS = 20L * 60L * 60L * 1000L; // 20h

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        boolean reminderEnabled = prefs.getBoolean("pref_song_reminder", true);
        if (!reminderEnabled) {
            return;
        }

        String lastTitle = prefs.getString("saved_song_title", "");
        if (lastTitle == null || lastTitle.isEmpty()) {
            // Nothing has ever been played - nothing to remind about.
            return;
        }

        long lastPlayedTime = prefs.getLong(PREF_LAST_PLAYED_TIME, 0L);
        long lastAppOpenedTime = prefs.getLong(PREF_LAST_APP_OPENED_TIME, 0L);
        long lastReminderShownTime = prefs.getLong(PREF_LAST_REMINDER_SHOWN_TIME, 0L);
        long now = System.currentTimeMillis();

        boolean userAlreadyOpenedAppSinceLastPlay = lastAppOpenedTime >= lastPlayedTime;
        if (userAlreadyOpenedAppSinceLastPlay) {
            // They've already been back in the app since that song played -
            // no need to remind them, they've seen it.
            return;
        }

        boolean reminderShownRecently = (now - lastReminderShownTime) < MIN_GAP_BETWEEN_REMINDERS_MILLIS;
        if (reminderShownRecently) {
            return;
        }

        showReminderNotification(context, lastTitle);

        prefs.edit().putLong(PREF_LAST_REMINDER_SHOWN_TIME, now).apply();
    }

    private void showReminderNotification(Context context, String songTitle) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;

        createChannelIfNeeded(context, manager);

        Intent contentIntent = new Intent(context, MainActivity.class);
        contentIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, contentIntent, flags);

        String text = songTitle + " is waiting for you";

        Notification.Builder builder = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);

        builder.setSmallIcon(R.drawable.plays)
                .setContentTitle("Pick up where you left off")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(Notification.PRIORITY_DEFAULT);

        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void createChannelIfNeeded(Context context, NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = manager.getNotificationChannel(CHANNEL_ID);
            if (channel == null) {
                channel = new NotificationChannel(CHANNEL_ID, "Muziki Reminders", NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription("Reminds you about the song you last listened to.");
                manager.createNotificationChannel(channel);
            }
        }
    }
}
