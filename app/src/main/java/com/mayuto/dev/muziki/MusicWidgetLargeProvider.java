package com.mayuto.dev.muziki;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

/**
 * LARGE home-screen widget: big square-ish album art, title + "muziki"
 * subtitle, and prev/play-pause/next controls. Meant for people who want a
 * more visual, "now playing card" style widget. Fully resizable.
 */
public class MusicWidgetLargeProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, Bundle newOptions) {
        updateWidget(context, appWidgetManager, appWidgetId);
    }

    public static void pushUpdateToAllWidgets(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName thisWidget = new ComponentName(context, MusicWidgetLargeProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(thisWidget);
        for (int widgetId : widgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        WidgetUpdateHelper.update(context, appWidgetManager, appWidgetId, R.layout.widget_music_large,
                R.id.widgetLargeArt, R.id.widgetLargeTitle, R.id.widgetLargeArtist,
                R.id.widgetLargeBtnPlayPause, R.id.widgetLargeBtnPrev, R.id.widgetLargeBtnNext);
    }
}
