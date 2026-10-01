package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

/**
 * MUZIKI AI — a UserLab Ltd revolution update.
 *
 * This screen only ever works for a signed-in user. Every time it is
 * entered (including returning via onResume, e.g. after backgrounding the
 * app) it re-checks {@link AuthManager#isSignedIn}. If the user is not
 * signed in, it immediately hands off to {@link AuthGateActivity} — the
 * full-screen "sign in required" prompt — and closes itself so it can
 * never be left showing behind the gate.
 */
public class MuzikiAIActivity extends AppCompatActivity {

    private boolean gateLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!AuthManager.isSignedIn(this)) {
            launchGateAndFinish();
            return;
        }

        setContentView(R.layout.activity_muziki_ai);

        TextView txtWelcome = (TextView) findViewById(R.id.txt_ai_welcome);
        String name = AuthManager.getDisplayName(this);
        if (name != null && name.length() > 0) {
            txtWelcome.setText("Welcome back, " + name);
        } else {
            txtWelcome.setText("Welcome back");
        }

        ImageButton btnBack = (ImageButton) findViewById(R.id.btn_ai_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        ImageButton btnSignOut = (ImageButton) findViewById(R.id.btn_ai_signout);
        btnSignOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmSignOut();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-check on every return to this screen. If the session was
        // signed out while we were away (e.g. from another Activity),
        // never let this screen keep showing.
        if (!gateLaunched && !AuthManager.isSignedIn(this)) {
            launchGateAndFinish();
        }
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out?")
                .setMessage("You'll need to sign in again to use MUZIKI AI.")
                .setPositiveButton("Sign out", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AuthManager.signOut(MuzikiAIActivity.this);
                        finish();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void launchGateAndFinish() {
        gateLaunched = true;
        Intent intent = new Intent(MuzikiAIActivity.this, AuthGateActivity.class);
        startActivity(intent);
        finish();
    }
}
