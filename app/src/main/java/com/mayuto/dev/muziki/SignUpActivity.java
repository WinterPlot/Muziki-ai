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
 * Sign-up screen for MUZIKI AI accounts.
 *
 * A UserLab Ltd revolution update: creating an account here is what
 * unlocks the MUZIKI AI screen. This activity never touches storage
 * directly — all account creation logic lives in {@link AuthManager}.
 *
 * If this screen was launched because the user tapped "Create free
 * account" on the sign-in gate, EXTRA_REDIRECT_TO_AI tells it to jump
 * straight into MuzikiAIActivity after a successful sign up instead of
 * just closing.
 */
public class SignUpActivity extends AppCompatActivity {

    public static final String EXTRA_REDIRECT_TO_AI = "extra_redirect_to_ai";

    private EditText inputName;
    private EditText inputEmail;
    private EditText inputPassword;
    private TextView txtError;
    private Button btnSignUp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        inputName = (EditText) findViewById(R.id.input_signup_name);
        inputEmail = (EditText) findViewById(R.id.input_signup_email);
        inputPassword = (EditText) findViewById(R.id.input_signup_password);
        txtError = (TextView) findViewById(R.id.txt_signup_error);
        btnSignUp = (Button) findViewById(R.id.btn_do_signup);

        ImageButton btnClose = (ImageButton) findViewById(R.id.btn_close_signup);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        btnSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSignUp();
            }
        });

        TextView goToLogin = (TextView) findViewById(R.id.txt_go_to_login);
        goToLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SignUpActivity.this, LoginActivity.class);
                if (getIntent().getBooleanExtra(EXTRA_REDIRECT_TO_AI, false)) {
                    intent.putExtra(LoginActivity.EXTRA_REDIRECT_TO_AI, true);
                }
                startActivity(intent);
                finish();
            }
        });
    }

    private void attemptSignUp() {
        hideError();

        String name = inputName.getText().toString();
        String email = inputEmail.getText().toString();
        String password = inputPassword.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showError("Please fill in all fields.");
            return;
        }

        AuthManager.SignUpResult result = AuthManager.createAccount(this, name, email, password);
        if (!result.ok) {
            showError(result.message);
            return;
        }

        Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();

        if (getIntent().getBooleanExtra(EXTRA_REDIRECT_TO_AI, false)) {
            Intent intent = new Intent(SignUpActivity.this, MuzikiAIActivity.class);
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
