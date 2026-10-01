package com.mayuto.dev.muziki;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.TextView;

/**
 * Central framework-only theme engine.
 *
 * No AndroidX, no AppCompat and no lambda expressions.
 * Theme changes are persisted and can be painted onto the current Activity
 * immediately, without restarting the application.
 */
public class ThemeEngine {

    public static final String PREF_KEY = "pref_theme_color";

    public static final String[] THEME_KEYS = {
            "white", "black", "amoled", "ocean", "forest", "sunset", "royal", "rose"
    };

    public static final String[] THEME_LABELS = {
            "Light", "Midnight", "AMOLED Black", "Ocean", "Forest", "Sunset", "Royal", "Rose"
    };

    public static final int[] THEME_SWATCH = {
            0xFFF8FAFC, 0xFF15171A, 0xFF000000, 0xFF102A43,
            0xFF12251B, 0xFF2B1B1B, 0xFF21173A, 0xFF321923
    };

    public int background;
    public int surface;
    public int surfaceRaised;
    public int textPrimary;
    public int textSecondary;
    public int accent;
    public int divider;
    public int iconTint;
    public boolean isDark;

    private static ThemeEngine cached;
    private static String cachedKey;

    public static ThemeEngine get(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String key = prefs.getString(PREF_KEY, "white");
        if (cached == null || !key.equals(cachedKey)) {
            cached = loadTheme(key);
            cachedKey = key;
        }
        return cached;
    }

    public static void invalidate() {
        cached = null;
        cachedKey = null;
    }

    /**
     * Applies system chrome immediately. This is safe on all supported API levels.
     */
    public static void applyToActivity(Activity activity) {
        if (activity == null) return;

        ThemeEngine t = get(activity);
        View content = activity.findViewById(android.R.id.content);
        if (content != null) {
            content.setBackgroundColor(t.background);
        }

        if (Build.VERSION.SDK_INT >= 21) {
            activity.getWindow().setStatusBarColor(t.background);
            activity.getWindow().setNavigationBarColor(t.background);

            if (Build.VERSION.SDK_INT >= 23) {
                int flags = activity.getWindow().getDecorView().getSystemUiVisibility();
                if (!t.isDark) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= 26) {
                        flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                    if (Build.VERSION.SDK_INT >= 26) {
                        flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                    }
                }
                activity.getWindow().getDecorView().setSystemUiVisibility(flags);
            }
        }
    }

    private static ThemeEngine loadTheme(String key) {
        ThemeEngine t = new ThemeEngine();

        if ("black".equals(key)) {
            t.background = 0xFF15171A;
            t.surface = 0xFF202328;
            t.surfaceRaised = 0xFF292E34;
            t.textPrimary = 0xFFF5F7FA;
            t.textSecondary = 0xFFAEB7C2;
            t.accent = 0xFFFF4F8B;
            t.divider = 0xFF343A41;
            t.iconTint = 0xFFF5F7FA;
            t.isDark = true;
        } else if ("amoled".equals(key)) {
            t.background = 0xFF000000;
            t.surface = 0xFF101010;
            t.surfaceRaised = 0xFF181818;
            t.textPrimary = 0xFFFFFFFF;
            t.textSecondary = 0xFFAAAAAA;
            t.accent = 0xFFFF4F8B;
            t.divider = 0xFF282828;
            t.iconTint = 0xFFFFFFFF;
            t.isDark = true;
        } else if ("ocean".equals(key)) {
            t.background = 0xFF102A43;
            t.surface = 0xFF173F5F;
            t.surfaceRaised = 0xFF1E5578;
            t.textPrimary = 0xFFF2F8FC;
            t.textSecondary = 0xFFB9D3E5;
            t.accent = 0xFF46D9C5;
            t.divider = 0xFF285A78;
            t.iconTint = 0xFFF2F8FC;
            t.isDark = true;
        } else if ("forest".equals(key)) {
            t.background = 0xFF12251B;
            t.surface = 0xFF193225;
            t.surfaceRaised = 0xFF21412E;
            t.textPrimary = 0xFFF1F8F3;
            t.textSecondary = 0xFFAFC8B8;
            t.accent = 0xFF70D6A1;
            t.divider = 0xFF2A4D38;
            t.iconTint = 0xFFF1F8F3;
            t.isDark = true;
        } else if ("sunset".equals(key)) {
            t.background = 0xFF2B1B1B;
            t.surface = 0xFF3A2525;
            t.surfaceRaised = 0xFF4A3030;
            t.textPrimary = 0xFFFFF3ED;
            t.textSecondary = 0xFFDAB8A8;
            t.accent = 0xFFFFA36B;
            t.divider = 0xFF563A35;
            t.iconTint = 0xFFFFF3ED;
            t.isDark = true;
        } else if ("royal".equals(key)) {
            t.background = 0xFF21173A;
            t.surface = 0xFF2D2050;
            t.surfaceRaised = 0xFF3B2B64;
            t.textPrimary = 0xFFF7F1FF;
            t.textSecondary = 0xFFCABBE2;
            t.accent = 0xFFB79CFF;
            t.divider = 0xFF493B70;
            t.iconTint = 0xFFF7F1FF;
            t.isDark = true;
        } else if ("rose".equals(key)) {
            t.background = 0xFF321923;
            t.surface = 0xFF44232F;
            t.surfaceRaised = 0xFF55303D;
            t.textPrimary = 0xFFFFF1F5;
            t.textSecondary = 0xFFDDB8C4;
            t.accent = 0xFFFF7DA8;
            t.divider = 0xFF623746;
            t.iconTint = 0xFFFFF1F5;
            t.isDark = true;
        } else {
            t.background = 0xFFF8FAFC;
            t.surface = 0xFFFFFFFF;
            t.surfaceRaised = 0xFFF1F4F8;
            t.textPrimary = 0xFF17202A;
            t.textSecondary = 0xFF66717E;
            t.accent = 0xFFE83E78;
            t.divider = 0xFFE2E7EC;
            t.iconTint = 0xFF4F5B66;
            t.isDark = false;
        }

        return t;
    }

    public int onAccent() {
        return Color.WHITE;
    }

    /**
     * Paint only generic text/container views. Special controls keep their
     * own drawable/state so Android framework widgets remain functional.
     */
    public static void applyToViewTree(View view, ThemeEngine theme) {
        if (view == null || theme == null) return;

        if (view instanceof TextView) {
            TextView text = (TextView) view;
            text.setTextColor(theme.textPrimary);
        }

        if (!(view instanceof CompoundButton) && view.getBackground() == null) {
            view.setBackgroundColor(theme.surface);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                applyToViewTree(group.getChildAt(i), theme);
            }
        }
    }
}
