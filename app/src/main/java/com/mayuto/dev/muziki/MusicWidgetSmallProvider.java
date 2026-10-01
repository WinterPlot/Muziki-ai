package com.mayuto.dev.muziki;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

/**
 * SMALL home-screen widget: a compact row of just prev / play-pause / next,
 * no title text, for people who want the smallest possible footprint (e.g. a
 * single row alongside other widgets). Fully resizable.
 */
public class MusicWidgetSmallProvider extends AppWidgetProvider {

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
        ComponentName thisWidget = new ComponentName(context, MusicWidgetSmallProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(thisWidget);
        for (int widgetId : widgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        WidgetUpdateHelper.update(context, appWidgetManager, appWidgetId, R.layout.widget_music_small,
                R.id.widgetSmallArt, 0, 0,
                R.id.widgetSmallBtnPlayPause, R.id.widgetSmallBtnPrev, R.id.widgetSmallBtnNext);
    }
}
