package com.mayuto.dev.muziki;

import android.Manifest;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

/**
 * Clean, guided, step-by-step permission screen shown once right after the
 * user accepts the Welcome terms. Explains WHY each permission is needed
 * before asking for it, and reflects granted/pending state live.
 *
 * Runtime permission requests are guarded by Android API level so the app
 * requests only the narrow media permission required by the current platform.
 * Activity#requestPermissions APIs (available since API 23) with manual
 * SDK-version guards for older devices where permissions are granted
 * at install time instead.
 */
public class PermissionGuideActivity extends AppCompatActivity {

    private static final int REQ_STORAGE = 101;
    private static final int REQ_NOTIF = 102;
    public static final String PREF_TOUR_PENDING = "pref_tour_pending";

    private View statusDotStorage;
    private View statusDotNotif;
    private Button btnGrantStorage;
    private Button btnGrantNotif;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_permission_guide);

        ImageView imgShield = (ImageView) findViewById(R.id.img_perm_shield);
        Drawable d = imgShield.getDrawable();
        if (d instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) d).start();
        }

        statusDotStorage = findViewById(R.id.status_dot_storage);
        statusDotNotif = findViewById(R.id.status_dot_notif);
        btnGrantStorage = (Button) findViewById(R.id.btn_grant_storage);
        btnGrantNotif = (Button) findViewById(R.id.btn_grant_notif);

        btnGrantStorage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestStoragePermission();
            }
        });

        btnGrantNotif.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                requestNotificationPermission();
            }
        });

        findViewById(R.id.btn_continue).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                proceedToApp();
            }
        });

        findViewById(R.id.btn_skip).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                proceedToApp();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatusDots();
    }

    private void refreshStatusDots() {
        statusDotStorage.setBackgroundResource(
                hasStoragePermission() ? R.drawable.perm_status_granted : R.drawable.perm_status_pending);
        statusDotNotif.setBackgroundResource(
                hasNotificationPermission() ? R.drawable.perm_status_granted : R.drawable.perm_status_pending);

        btnGrantStorage.setText(hasStoragePermission() ? R.string.perm_granted_label : R.string.perm_grant_storage);
        btnGrantNotif.setText(hasNotificationPermission() ? R.string.perm_granted_label : R.string.perm_grant_notif);
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        if (Build.VERSION.SDK_INT >= 33) {
            boolean audio = checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean video = checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
            return audio && video;
        }
        return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return true; // no runtime prompt needed before Android 13
        return checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT < 23) {
            refreshStatusDots();
            return;
        }
        if (hasStoragePermission()) {
            refreshStatusDots();
            return;
        }
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_VIDEO}, REQ_STORAGE);
        } else if (Build.VERSION.SDK_INT >= 29) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_STORAGE);
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            refreshStatusDots();
            return;
        }
        if (hasNotificationPermission()) {
            refreshStatusDots();
            return;
        }
        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatusDots();

        boolean anyDenied = false;
        for (int r : grantResults) {
            if (r != PackageManager.PERMISSION_GRANTED) anyDenied = true;
        }
        if (anyDenied) {
            // Send the user to app settings so they can flip it on manually, per perm_step_3.
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void proceedToApp() {
        // Mark that the in-app narrator tour still needs to run once MainActivity loads.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_TOUR_PENDING, true).apply();

        Intent intent = new Intent(PermissionGuideActivity.this, MainActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }
}
