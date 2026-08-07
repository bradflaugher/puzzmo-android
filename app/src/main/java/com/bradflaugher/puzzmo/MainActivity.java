package com.bradflaugher.puzzmo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

/**
 * Full-screen WebView host for https://www.puzzmo.com — same idea as the
 * official iOS app: the site is the product; the shell is thin and invisible.
 */
public final class MainActivity extends Activity {
    private static final String PUZZMO_URL = "https://www.puzzmo.com/";

    private WebView webView;
    private ProgressBar progress;
    private View splash;
    private LinearLayout errorView;
    private TextView errorDetail;
    private boolean splashHidden;
    private String pendingUrl;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setupEdgeToEdge();

        progress = findViewById(R.id.progress);
        webView = findViewById(R.id.web_view);
        splash = findViewById(R.id.splash);
        errorView = findViewById(R.id.error_view);
        errorDetail = findViewById(R.id.error_detail);
        Button retry = findViewById(R.id.retry_button);

        setupWebView();
        retry.setOnClickListener(v -> loadPuzzmo(true));

        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (webView.canGoBack()) {
                        webView.goBack();
                    } else {
                        finish();
                    }
                });

        String deepLink = intentUrl(getIntent());
        if (savedInstanceState == null) {
            loadPuzzmo(false, deepLink);
        } else {
            webView.restoreState(savedInstanceState);
            hideSplash();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = intentUrl(intent);
        if (url != null) {
            loadPuzzmo(true, url);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onPause() {
        CookieManager.getInstance().flush();
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.destroy();
        super.onDestroy();
    }

    private void setupEdgeToEdge() {
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        WindowInsetsController controller = getWindow().getInsetsController();
        if (controller != null) {
            controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }

        View root = findViewById(R.id.root);
        final int errorPad = (int) (32f * getResources().getDisplayMetrics().density);
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            // Pad the web surface so puzzle UI never sits under the cutout / gesture bar.
            webView.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            progress.setTranslationY(bars.top);
            errorView.setPadding(
                    errorPad + bars.left,
                    errorPad + bars.top,
                    errorPad + bars.right,
                    errorPad + bars.bottom);
            return WindowInsets.CONSUMED;
        });
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setBackgroundColor(getColor(R.color.paper));
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        // Look like Chrome Mobile — some auth flows reject "; wv)" WebViews.
        settings.setUserAgentString(sanitizeUserAgent(settings.getUserAgentString()));

        webView.setWebViewClient(new PuzzmoWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setVisibility(newProgress > 0 && newProgress < 100
                        ? View.VISIBLE
                        : View.GONE);
            }
        });
        webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) ->
                openExternal(url));
    }

    private void loadPuzzmo(boolean force) {
        loadPuzzmo(force, null);
    }

    private void loadPuzzmo(boolean force, String startUrl) {
        hideError();
        if (!isOnline()) {
            showError(getString(R.string.error_message));
            hideSplash();
            return;
        }

        String url = (startUrl != null && isAllowedUrl(startUrl)) ? startUrl : PUZZMO_URL;
        pendingUrl = url;

        String current = webView.getUrl();
        if (force || current == null || current.isEmpty() || "about:blank".equals(current)) {
            progress.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        } else if (startUrl != null) {
            progress.setVisibility(View.VISIBLE);
            webView.loadUrl(url);
        }
    }

    private static String sanitizeUserAgent(String original) {
        String cleaned = original
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "");
        if (cleaned.contains("Chrome/")) {
            return cleaned;
        }
        return "Mozilla/5.0 (Linux; Android " + Build.VERSION.RELEASE + ") "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/131.0.0.0 Mobile Safari/537.36";
    }

    private static boolean isAllowedUrl(String url) {
        Uri uri = Uri.parse(url);
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase();
        return host.equals("puzzmo.com")
                || host.endsWith(".puzzmo.com")
                || host.equals("puzzmo")
                // Auth / payments / CDN hosts the site may bounce through.
                || host.endsWith(".auth0.com")
                || host.endsWith(".stripe.com")
                || host.endsWith(".stripe.network")
                || host.endsWith(".cloudflare.com")
                || host.endsWith(".cloudflareinsights.com")
                || host.endsWith(".google.com")
                || host.endsWith(".gstatic.com")
                || host.endsWith(".googleapis.com")
                || host.endsWith(".googleusercontent.com")
                || host.equals("accounts.google.com");
    }

    private boolean shouldOpenExternally(String url) {
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme();
        if (scheme == null) {
            return true;
        }
        scheme = scheme.toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return true;
        }
        return !isAllowedUrl(url);
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) {
            return true;
        }
        var network = cm.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void hideSplash() {
        if (splashHidden) {
            return;
        }
        splashHidden = true;
        splash.animate()
                .alpha(0f)
                .setDuration(280)
                .withEndAction(() -> {
                    splash.setVisibility(View.GONE);
                    splash.setAlpha(1f);
                })
                .start();
    }

    private void showError(String message) {
        errorDetail.setText(message);
        errorView.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    private void hideError() {
        errorView.setVisibility(View.GONE);
    }

    private static String intentUrl(Intent intent) {
        if (intent == null || intent.getData() == null) {
            return null;
        }
        return intent.getData().toString();
    }

    private final class PuzzmoWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return handleUrl(request.getUrl().toString());
        }

        private boolean handleUrl(String url) {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return true;
            }
            scheme = scheme.toLowerCase();

            switch (scheme) {
                case "http":
                case "https":
                    if (shouldOpenExternally(url)) {
                        openExternal(url);
                        return true;
                    }
                    return false;
                case "mailto":
                case "tel":
                case "sms":
                    openExternal(url);
                    return true;
                case "intent":
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        String fallback = intent.getStringExtra("browser_fallback_url");
                        if (intent.resolveActivity(getPackageManager()) != null) {
                            startActivity(intent);
                        } else if (fallback != null && !fallback.isEmpty()) {
                            webView.loadUrl(fallback);
                        }
                    } catch (Exception ignored) {
                        // Malformed intent: ignore.
                    }
                    return true;
                default:
                    openExternal(url);
                    return true;
            }
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            progress.setVisibility(View.VISIBLE);
            hideError();
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            progress.setVisibility(View.GONE);
            hideSplash();
            CookieManager.getInstance().flush();
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        @Override
        public void onReceivedError(
                WebView view,
                WebResourceRequest request,
                WebResourceError error) {
            if (request.isForMainFrame()) {
                hideSplash();
                showError(getString(R.string.error_message));
            }
        }
    }
}
