package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

/**
 * Full-screen "sign in required" gate for MUZIKI AI.
 *
 * Shown whenever a signed-out user tries to open a screen that requires a
 * MUZIKI AI account. Presents a clean, professional prompt with three
 * clear choices: create an account, sign in, or cancel and go back.
 *
 * This activity never grants access itself — it only routes to
 * SignUpActivity / LoginActivity (which redirect into MuzikiAIActivity on
 * success) or simply finishes on Cancel/back.
 */
public class AuthGateActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth_gate);

        Button btnSignUp = (Button) findViewById(R.id.btn_gate_signup);
        Button btnLogin = (Button) findViewById(R.id.btn_gate_login);
        Button btnCancel = (Button) findViewById(R.id.btn_gate_cancel);

        btnSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AuthGateActivity.this, SignUpActivity.class);
                intent.putExtra(SignUpActivity.EXTRA_REDIRECT_TO_AI, true);
                startActivity(intent);
                finish();
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(AuthGateActivity.this, LoginActivity.class);
                intent.putExtra(LoginActivity.EXTRA_REDIRECT_TO_AI, true);
                startActivity(intent);
                finish();
            }
        });

        btnCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
