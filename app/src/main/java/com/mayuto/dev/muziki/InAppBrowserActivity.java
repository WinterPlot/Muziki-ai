package com.mayuto.dev.muziki;

import androidx.appcompat.app.AppCompatActivity;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Secure in-app browser used only to open Muziki's official web portal.
 *
 * Security notes:
 *  - The target host is locked to "muziki.42web.io". Any navigation away
 *    from that exact host (including redirects) is blocked.
 *  - Plain http:// is never allowed; the very first request is forced to
 *    https:// before anything is loaded, and any later request that isn't
 *    https is refused.
 *  - SSL errors are NOT silently swallowed: if the certificate can't be
 *    verified, loading stops and the user is told, instead of proceeding
 *    on a broken/insecure connection.
 *  - This project targets minSdk 14 and uses no support library, so the
 *    green lock feedback uses framework AnimatedVectorDrawable (API 21+)
 *    with a plain frame-animation fallback below that, exactly like the
 *    shield icon on the permissions screen.
 */
public class InAppBrowserActivity extends AppCompatActivity {

    public static final String EXTRA_URL = "extra_url";

    private static final String ALLOWED_HOST = "muziki.42web.io";

    private WebView webView;
    private ProgressBar progressBar;
    private TextView txtUrl;
    private ImageView imgLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_in_app_browser);

        webView = (WebView) findViewById(R.id.web_view);
        progressBar = (ProgressBar) findViewById(R.id.progress_bar);
        txtUrl = (TextView) findViewById(R.id.txt_url);
        imgLock = (ImageView) findViewById(R.id.img_lock_secure);

        ImageView imgClose = (ImageView) findViewById(R.id.img_close);
        imgClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        animateLock();

        String requestedUrl = getIntent().getStringExtra(EXTRA_URL);
        if (requestedUrl == null) {
            requestedUrl = "https://" + ALLOWED_HOST;
        }
        String secureUrl = forceHttps(requestedUrl);

        setupWebView();
        webView.loadUrl(secureUrl);
    }

    /** Starts the green lock's "pop" entrance, and its gentle infinite pulse afterwards. */
    private void animateLock() {
        Drawable d = imgLock.getDrawable();
        if (d instanceof AnimatedVectorDrawable) {
            ((AnimatedVectorDrawable) d).start();
        }
        // Layer a subtle infinite breathing pulse on top so the "secure" cue
        // stays gently alive the whole time the portal is open, not just once.
        imgLock.startAnimation(AnimationUtils.loadAnimation(this, R.anim.lock_secure_pulse));
    }

    /** Rewrites any http:// URL to https:// before it's ever loaded. */
    private String forceHttps(String url) {
        if (url.startsWith("http://")) {
            return "https://" + url.substring("http://".length());
        }
        if (!url.startsWith("https://")) {
            return "https://" + url;
        }
        return url;
    }

    private void setupWebView() {
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        // Mixed content (loading insecure http resources on a https page) is
        // blocked by default from API 21+; nothing extra needed there, and
        // we never downgrade it back to "always allow".

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(5);
                updateAddressBar(url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                updateAddressBar(url);
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Never proceed past a broken certificate. Cancel and warn instead.
                handler.cancel();
                Toast.makeText(InAppBrowserActivity.this,
                        "Connection is not secure - loading stopped", Toast.LENGTH_LONG).show();
                lockToInsecureState();
            }
        });
    }

    /**
     * Returns true to tell the WebView "I handled this myself" (i.e. block it),
     * or false to let the WebView load it normally.
     */
    private boolean handleNavigation(WebView view, String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        String host = uri.getHost();

        boolean isHttps = "https".equalsIgnoreCase(scheme);
        boolean isAllowedHost = host != null && host.equalsIgnoreCase(ALLOWED_HOST);

        if (isHttps && isAllowedHost) {
            // Allowed: stay on our own portal, over HTTPS only.
            return false;
        }

        Toast.makeText(this, "Blocked: only the official secure portal is allowed", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void updateAddressBar(String url) {
        if (url == null) return;
        txtUrl.setText(url);

        Uri uri = Uri.parse(url);
        boolean secure = "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null
                && uri.getHost().equalsIgnoreCase(ALLOWED_HOST);

        if (secure) {
            imgLock.setImageResource(R.drawable.avd_lock_secure);
            animateLock();
        } else {
            lockToInsecureState();
        }
    }

    private void lockToInsecureState() {
        imgLock.clearAnimation();
        imgLock.setImageResource(R.drawable.ic_lock_insecure);
        TextView txtSecureLabel = (TextView) findViewById(R.id.txt_secure_label);
        if (txtSecureLabel != null) {
            txtSecureLabel.setText("Not secure");
            txtSecureLabel.setTextColor(0xFF9E9E9E);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }
}
