package com.mayuto.dev.muziki;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Custom themed list dialog letting the user pick the app's background theme.
 * Not a stock ListPreference - a real list with color swatches and a live
 * checkmark on the current selection, so it feels like a proper product
 * setting instead of a system default dialog.
 */
public class ThemePickerDialog {

    public interface OnThemeChosenListener {
        void onThemeChosen(String themeKey);
    }

    public static void show(final Context context, final OnThemeChosenListener listener) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final String currentKey = prefs.getString(ThemeEngine.PREF_KEY, "white");
        final ThemeEngine theme = ThemeEngine.get(context);

        View titleView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, null);

        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Choose a theme");

        final ListView listView = new ListView(context);
        listView.setDivider(null);
        listView.setBackgroundColor(theme.surface);

        final ThemeListAdapter adapter = new ThemeListAdapter(context, currentKey);
        listView.setAdapter(adapter);

        final Dialog dialog = builder.setView(listView).create();

        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String chosenKey = ThemeEngine.THEME_KEYS[position];
                prefs.edit().putString(ThemeEngine.PREF_KEY, chosenKey).apply();
                ThemeEngine.invalidate();

                // Repaint the picker itself immediately. The dialog no longer
                // waits for SettingsActivity.onResume() to show the new theme.
                ThemeEngine newTheme = ThemeEngine.get(context);
                listView.setBackgroundColor(newTheme.surface);
                adapter.setSelected(chosenKey);
                if (dialog.getWindow() != null) {
                    GradientDrawable dialogBg = new GradientDrawable();
                    dialogBg.setColor(newTheme.surface);
                    dialogBg.setCornerRadius(24);
                    dialog.getWindow().setBackgroundDrawable(dialogBg);
                }

                ImageView check = (ImageView) view.findViewById(R.id.imgSelectedCheck);
                if (check != null) {
                    check.setVisibility(View.VISIBLE);
                    check.startAnimation(AnimationUtils.loadAnimation(context, R.anim.check_pop_in));
                }

                if (listener != null) listener.onThemeChosen(chosenKey);

                view.postDelayed(new Runnable() {
                    @Override public void run() { dialog.dismiss(); }
                }, 260);
            }
        });

        dialog.show();
    }

    private static class ThemeListAdapter extends BaseAdapter {
        private final Context context;
        private String selectedKey;

        ThemeListAdapter(Context context, String selectedKey) {
            this.context = context;
            this.selectedKey = selectedKey;
        }

        void setSelected(String key) {
            this.selectedKey = key;
            notifyDataSetChanged();
        }

        @Override public int getCount() { return ThemeEngine.THEME_KEYS.length; }
        @Override public Object getItem(int position) { return ThemeEngine.THEME_KEYS[position]; }
        @Override public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.row_theme_option, parent, false);
            }
            String key = ThemeEngine.THEME_KEYS[position];
            String label = ThemeEngine.THEME_LABELS[position];
            int swatchColor = ThemeEngine.THEME_SWATCH[position];

            TextView labelView = (TextView) convertView.findViewById(R.id.themeLabel);
            View swatch = convertView.findViewById(R.id.swatchColor);
            ImageView check = (ImageView) convertView.findViewById(R.id.imgSelectedCheck);

            ThemeEngine theme = ThemeEngine.get(context);
            labelView.setText(label);
            labelView.setTextColor(theme.textPrimary);

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(swatchColor);
            bg.setStroke(2, 0x22000000);
            swatch.setBackground(bg);

            boolean isSelected = key.equals(selectedKey);
            check.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            return convertView;
        }
    }
}
