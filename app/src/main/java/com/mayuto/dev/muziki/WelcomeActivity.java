package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * First screen shown on first install. Explains the app, the developer,
 * the company, privacy stance and anti-copy protections. User must accept
 * to continue to the permissions guide; declining exits the app.
 *
 * This screen only ever shows once — after acceptance we store a flag in
 * SharedPreferences and every future launch skips straight past it.
 */
public class WelcomeActivity extends AppCompatActivity {

    public static final String PREF_ACCEPTED_TERMS = "pref_accepted_terms";

    private ImageView imgLogo;
    private Button btnAccept;
    private Button btnDecline;
    private ScrollView scrollPolicy;
    private TextView txtScrollHint;
    private boolean reachedBottom = false;

    /** Call this from a splash/launcher check to decide whether Welcome must be shown. */
    public static boolean hasAcceptedTerms(android.content.Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(PREF_ACCEPTED_TERMS, false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        imgLogo = (ImageView) findViewById(R.id.img_onboarding_logo);
        btnAccept = (Button) findViewById(R.id.btn_accept);
        btnDecline = (Button) findViewById(R.id.btn_decline);
        scrollPolicy = (ScrollView) findViewById(R.id.scroll_policy);
        txtScrollHint = (TextView) findViewById(R.id.txt_scroll_hint);

        startLogoAnimation();
        watchScrollProgress();

        btnAccept.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onAccept();
            }
        });

        btnDecline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDecline();
            }
        });
    }

    private void startLogoAnimation() {
        Drawable d = imgLogo.getDrawable();
        if (d instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) d).start();
        }
    }

    /** Gently nudge the user to scroll by fading the hint once they reach the bottom. */
    private void watchScrollProgress() {
        scrollPolicy.getViewTreeObserver().addOnScrollChangedListener(new ViewTreeObserver.OnScrollChangedListener() {
            @Override
            public void onScrollChanged() {
                View content = scrollPolicy.getChildAt(0);
                if (content == null) return;
                int scrollBottom = scrollPolicy.getScrollY() + scrollPolicy.getHeight();
                int contentHeight = content.getHeight();
                if (scrollBottom >= contentHeight - 24) {
                    if (!reachedBottom) {
                        reachedBottom = true;
                        txtScrollHint.animate().alpha(0f).setDuration(250).start();
                    }
                }
            }
        });
    }

    private void onAccept() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.edit().putBoolean(PREF_ACCEPTED_TERMS, true).apply();

        Intent intent = new Intent(WelcomeActivity.this, PermissionGuideActivity.class);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        finish();
    }

    private void confirmDecline() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.welcome_decline_confirm_title)
                .setMessage(R.string.welcome_decline_confirm_msg)
                .setPositiveButton(android.R.string.yes, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (android.os.Build.VERSION.SDK_INT >= 16) {
                            finishAffinity();
                        } else {
                            finish();
                        }
                        System.exit(0);
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        confirmDecline();
    }
}
