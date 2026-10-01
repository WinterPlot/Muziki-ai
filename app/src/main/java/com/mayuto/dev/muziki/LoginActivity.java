package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Sign-in screen for MUZIKI AI accounts.
 *
 * A UserLab Ltd revolution update. Never touches storage directly — all
 * credential checks live in {@link AuthManager}.
 *
 * If launched from the sign-in gate with EXTRA_REDIRECT_TO_AI, a
 * successful login jumps straight into MuzikiAIActivity.
 */
public class LoginActivity extends AppCompatActivity {

    public static final String EXTRA_REDIRECT_TO_AI = "extra_redirect_to_ai";

    private EditText inputEmail;
    private EditText inputPassword;
    private TextView txtError;
    private Button btnLogin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        inputEmail = (EditText) findViewById(R.id.input_login_email);
        inputPassword = (EditText) findViewById(R.id.input_login_password);
        txtError = (TextView) findViewById(R.id.txt_login_error);
        btnLogin = (Button) findViewById(R.id.btn_do_login);

        ImageButton btnClose = (ImageButton) findViewById(R.id.btn_close_login);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });

        TextView goToSignUp = (TextView) findViewById(R.id.txt_go_to_signup);
        goToSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(LoginActivity.this, SignUpActivity.class);
                if (getIntent().getBooleanExtra(EXTRA_REDIRECT_TO_AI, false)) {
                    intent.putExtra(SignUpActivity.EXTRA_REDIRECT_TO_AI, true);
                }
                startActivity(intent);
                finish();
            }
        });
    }

    private void attemptLogin() {
        hideError();

        String email = inputEmail.getText().toString();
        String password = inputPassword.getText().toString();

        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Please enter both email and password.");
            return;
        }

        AuthManager.LoginResult result = AuthManager.signIn(this, email, password);
        if (!result.ok) {
            showError(result.message);
            return;
        }

        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();

        if (getIntent().getBooleanExtra(EXTRA_REDIRECT_TO_AI, false)) {
            Intent intent = new Intent(LoginActivity.this, MuzikiAIActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
        finish();
    }

    private void showError(String message) {
        txtError.setText(message);
        txtError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        txtError.setVisibility(View.GONE);
    }
}
