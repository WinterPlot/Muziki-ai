package com.mayuto.dev.muziki;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * MEDIUM home-screen "now playing" widget: album art, title, and
 * prev/play-pause/next controls that talk directly to MusicService - no
 * Activity needs to be open. Resizable both horizontally and vertically
 * (see widget_music_control_info.xml).
 *
 * Update strategy: MusicService calls pushUpdateToAllWidgets() directly
 * (in-process) every time it plays/pauses/resumes/skips, so the widget is
 * always painted from the live, current state - it never has to guess or
 * wait for a broadcast that Android may drop.
 */
public class MusicWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_WIDGET_UPDATE = "com.mayuto.dev.muziki.ACTION_WIDGET_UPDATE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        // Fires while the user is resizing the widget - repaint immediately so
        // text/art re-flow to the new size instead of waiting on the next song event.
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_WIDGET_UPDATE.equals(action)) {
            pushUpdateToAllWidgets(context);
        }
    }

    /** Called by MusicService after every play/pause/skip so all placed widgets of this size repaint immediately. */
    public static void pushUpdateToAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, MusicWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(thisWidget);
        for (int widgetId : widgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        WidgetUpdateHelper.update(context, appWidgetManager, appWidgetId, R.layout.widget_music_control,
                R.id.widgetAlbumArt, R.id.widgetSongTitle, 0,
                R.id.widgetBtnPlayPause, R.id.widgetBtnPrev, R.id.widgetBtnNext);
    }
}
