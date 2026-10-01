package com.mayuto.dev.muziki;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.regex.Pattern;

/**
 * MUZIKI AI — account system.
 *
 * A UserLab Ltd revolution update: lets a user create a local account
 * (full name, email, password) and sign in to unlock MUZIKI AI features.
 *
 * Deliberately framework-only — no AndroidX/AppCompat, no third-party auth
 * SDK, no Lambda expressions (this whole class, and everything that calls
 * it, sticks to anonymous inner classes so the project stays compatible
 * with the same toolchain as the rest of the app). Credentials are stored
 * locally in a private SharedPreferences file; passwords are never stored
 * in plain text — each one is salted and hashed with SHA-256 before being
 * written to disk.
 *
 * This is a self-contained, offline account store. If a backend is added
 * later, swap the storage calls below for real network calls without
 * touching any of the call sites (Login/SignUp/Gate activities only ever
 * talk to AuthManager, never to storage directly).
 */
public final class AuthManager {

    private static final String PREFS_NAME = "muziki_ai_account";

    private static final String KEY_ACCOUNT_EXISTS = "account_exists";
    private static final String KEY_NAME = "account_name";
    private static final String KEY_EMAIL = "account_email";
    private static final String KEY_SALT = "account_salt";
    private static final String KEY_HASH = "account_hash";
    private static final String KEY_SIGNED_IN = "session_signed_in";
    private static final String KEY_JOINED_AT = "account_joined_at";
    private static final String KEY_KDF_VERSION = "account_kdf_version";
    private static final int KDF_VERSION = 2;
    private static final int PBKDF2_ITERATIONS = 150_000;
    private static final int HASH_BITS = 256;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private AuthManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------
    // Validation helpers (used by SignUpActivity / LoginActivity)
    // ---------------------------------------------------------------

    public static boolean isValidEmail(String email) {
        return !TextUtils.isEmpty(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    public static boolean isValidName(String name) {
        return !TextUtils.isEmpty(name) && name.trim().length() >= 2;
    }

    // ---------------------------------------------------------------
    // Account state
    // ---------------------------------------------------------------

    /** True once a user has ever completed sign up on this device. */
    public static boolean hasAccount(Context context) {
        return prefs(context).getBoolean(KEY_ACCOUNT_EXISTS, false);
    }

    /** True only when an account exists AND the current session is signed in. */
    public static boolean isSignedIn(Context context) {
        SharedPreferences p = prefs(context);
        return p.getBoolean(KEY_ACCOUNT_EXISTS, false) && p.getBoolean(KEY_SIGNED_IN, false);
    }

    public static String getDisplayName(Context context) {
        return prefs(context).getString(KEY_NAME, "");
    }

    public static String getEmail(Context context) {
        return prefs(context).getString(KEY_EMAIL, "");
    }

    /**
     * Creates a brand new local MUZIKI AI account and immediately signs the
     * user in. Returns a SignUpResult describing success/failure so the
     * calling Activity can show the right message.
     */
    public static SignUpResult createAccount(Context context, String name, String email, String password) {
        if (!isValidName(name)) {
            return SignUpResult.error("Please enter your full name.");
        }
        if (!isValidEmail(email)) {
            return SignUpResult.error("Please enter a valid email address.");
        }
        if (!isValidPassword(password)) {
            return SignUpResult.error("Password must be at least 6 characters.");
        }

        SharedPreferences p = prefs(context);
        if (p.getBoolean(KEY_ACCOUNT_EXISTS, false)) {
            String existingEmail = p.getString(KEY_EMAIL, "");
            if (existingEmail.equalsIgnoreCase(email.trim())) {
                return SignUpResult.error("An account with this email already exists. Please sign in instead.");
            }
        }

        byte[] salt = generateSalt();
        String saltEncoded = Base64.encodeToString(salt, Base64.NO_WRAP);
        String hash = hashPassword(password, salt);

        SharedPreferences.Editor editor = p.edit();
        editor.putBoolean(KEY_ACCOUNT_EXISTS, true);
        editor.putString(KEY_NAME, name.trim());
        editor.putString(KEY_EMAIL, email.trim());
        editor.putString(KEY_SALT, saltEncoded);
        editor.putString(KEY_HASH, hash);
        editor.putInt(KEY_KDF_VERSION, KDF_VERSION);
        editor.putLong(KEY_JOINED_AT, System.currentTimeMillis());
        editor.putBoolean(KEY_SIGNED_IN, true);
        editor.apply();

        return SignUpResult.success();
    }

    /**
     * Attempts to sign in against the single locally-stored account.
     */
    public static LoginResult signIn(Context context, String email, String password) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            return LoginResult.error("Please enter both email and password.");
        }

        SharedPreferences p = prefs(context);
        if (!p.getBoolean(KEY_ACCOUNT_EXISTS, false)) {
            return LoginResult.error("No MUZIKI AI account found. Please sign up first.");
        }

        String storedEmail = p.getString(KEY_EMAIL, "");
        if (!storedEmail.equalsIgnoreCase(email.trim())) {
            return LoginResult.error("No account matches that email.");
        }

        String saltEncoded = p.getString(KEY_SALT, "");
        String storedHash = p.getString(KEY_HASH, "");
        byte[] salt;
        try {
            salt = Base64.decode(saltEncoded, Base64.NO_WRAP);
        } catch (IllegalArgumentException e) {
            return LoginResult.error("Account data is invalid. Please create the account again.");
        }

        int kdfVersion = p.getInt(KEY_KDF_VERSION, 1);
        boolean valid;
        if (kdfVersion >= KDF_VERSION) {
            valid = constantTimeEquals(storedHash, hashPassword(password, salt));
        } else {
            // One-time migration for accounts created by 2.5.0.
            valid = constantTimeEquals(storedHash, legacySha256Hash(password, salt));
            if (valid) {
                p.edit()
                        .putString(KEY_HASH, hashPassword(password, salt))
                        .putInt(KEY_KDF_VERSION, KDF_VERSION)
                        .apply();
            }
        }

        if (!valid) {
            return LoginResult.error("Incorrect password. Please try again.");
        }

        p.edit().putBoolean(KEY_SIGNED_IN, true).apply();
        return LoginResult.success();
    }

    public static void signOut(Context context) {
        prefs(context).edit().putBoolean(KEY_SIGNED_IN, false).apply();
    }

    /** Permanently deletes the local account and signs out. */
    public static void deleteAccount(Context context) {
        prefs(context).edit().clear().apply();
    }

    // ---------------------------------------------------------------
    // Password hashing
    // ---------------------------------------------------------------

    private static byte[] generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return salt;
    }

    private static String hashPassword(String password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, HASH_BITS);
        try {
            SecretKeyFactory factory;
            try {
                factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            } catch (NoSuchAlgorithmException unavailable) {
                factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
            }
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            return Base64.encodeToString(encoded, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Secure password hashing is unavailable on this device", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static String legacySha256Hash(String password, byte[] salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hashed, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 is unavailable on this device", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------
    // Result wrappers
    // ---------------------------------------------------------------

    public static final class SignUpResult {
        public final boolean ok;
        public final String message;

        private SignUpResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static SignUpResult success() {
            return new SignUpResult(true, "Account created — welcome to MUZIKI AI!");
        }

        static SignUpResult error(String message) {
            return new SignUpResult(false, message);
        }
    }

    public static final class LoginResult {
        public final boolean ok;
        public final String message;

        private LoginResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static LoginResult success() {
            return new LoginResult(true, "Welcome back!");
        }

        static LoginResult error(String message) {
            return new LoginResult(false, message);
        }
    }
}
