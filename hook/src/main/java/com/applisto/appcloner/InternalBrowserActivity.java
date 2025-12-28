package com.applisto.appcloner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class InternalBrowserActivity extends Activity {
    private static final String TAG = "InternalBrowserActivity";
    private FrameLayout mBorderFrameLayout;
    private final List<WebView> mWebViews = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        Uri data = intent.getData();
        Log.i(TAG, "onCreate; data: " + data);

        if (data == null) {
            finish();
            return;
        }

        String url = data.toString();
        Log.i(TAG, "onCreate; url: " + url);

        if (TextUtils.isEmpty(url)) {
            finish();
            return;
        }

        int padding = dp2px(this, 32f);
        FrameLayout outerLayout = new FrameLayout(this);
        outerLayout.setPadding(padding, padding, padding, padding);

        mBorderFrameLayout = new FrameLayout(this);
        // Color -fc560c -> 0xFF03A9F4 (Light Blue) ??? No, wait.
        // -fc560c in hex is roughly 0xFF03A9F4 if it was ARGB?
        // -fc560c is a negative integer. 0xFF000000 | 0x00FC560C? No.
        // Let's assume a color or calculate it.
        // -16537076 = 0xFF03A9F4 (Material Light Blue 500)
        // -fc560c hex:
        // In python: hex(-16537076 & 0xFFFFFFFF) -> '0xff03a9f4'
        // The smali says const v4, -0xfc560c.
        // Let's interpret it as 0xFF03A9F4.
        // Actually, 0xFF000000 - 0xFC560C = ...
        // Let's just use a reasonable border color or the exact int value.
        mBorderFrameLayout.setBackgroundColor(0xFF03A9F4);

        int borderPadding = dp2px(this, 3f);
        mBorderFrameLayout.setPadding(borderPadding, borderPadding, borderPadding, borderPadding);

        WebView webView = addWebView();
        webView.loadUrl(url);

        setContentView(outerLayout);
        outerLayout.addView(mBorderFrameLayout);

        // Close button container
        FrameLayout closeButtonContainer = new FrameLayout(this);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END
        );
        closeButtonContainer.setLayoutParams(params);

        ImageView closeButton = new ImageView(this);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        FrameLayout.LayoutParams imgParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        closeButton.setLayoutParams(imgParams);

        closeButtonContainer.addView(closeButton);
        outerLayout.addView(closeButtonContainer);

        closeButton.setOnClickListener(v -> finish());

        // Handle Facebook login cookies logic
        if (intent.hasExtra("facebookWebViewLoginCookies")) {
             Boolean val = (Boolean) intent.getSerializableExtra("facebookWebViewLoginCookies");
             FacebookWebViewLoginCookies.install(val);
        }
    }

    private WebView addWebView() {
        WebView webView = createWebView();
        mWebViews.add(webView);
        showLatestWebView();
        return webView;
    }

    private WebView createWebView() {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); // 0 = MIXED_CONTENT_ALWAYS_ALLOW
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(true);
        settings.setUserAgentString("Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36");

        CookieManager.getInstance().setAcceptCookie(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Log.i(TAG, "shouldInterceptRequest; url: " + request.getUrl());
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri urlUri = request.getUrl();
                String urlString = urlUri.toString();
                Log.i(TAG, "shouldOverrideUrlLoading; url: " + urlString);

                if (urlString.startsWith("https://m.facebook.com/oauth/error/?error_code=PLATFORM__LOGIN_DISABLED_FROM_WEBVIEW")) {
                    Log.i(TAG, "shouldOverrideUrlLoading; overridden PLATFORM__LOGIN_DISABLED_FROM_WEBVIEW");
                    return true;
                }

                Intent intent;
                try {
                    if (urlString.startsWith("intent://")) {
                        intent = Intent.parseUri(urlString, Intent.URI_INTENT_SCHEME);
                    } else {
                        intent = new Intent(Intent.ACTION_VIEW, urlUri);
                    }
                } catch (URISyntaxException e) {
                    Log.e(TAG, "shouldOverrideUrlLoading; URISyntaxException: " + e.getMessage());
                    return false;
                }

                List<ResolveInfo> activities = getPackageManager().queryIntentActivities(intent, 0);
                boolean handled = false;
                for (ResolveInfo info : activities) {
                    if (getPackageName().equals(info.activityInfo.packageName)) {
                         Log.i(TAG, "shouldOverrideUrlLoading; should override; resolveInfo: " + info);
                         intent.setPackage(getPackageName());
                         startActivity(intent);
                         handled = true;
                         break;
                    }
                }

                if (handled) {
                     runOnUiThread(() -> finish());
                     return true;
                }

                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d(TAG, "onConsoleMessage; message: " + consoleMessage.message() +
                        ", lineNumber: " + consoleMessage.lineNumber() +
                        ", sourceId: " + consoleMessage.sourceId());
                return true;
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                Log.i(TAG, "onCreateWindow; isDialog: " + isDialog +
                        ", isUserGesture: " + isUserGesture +
                        ", resultMsg: " + resultMsg);

                WebView newWebView = addWebView();
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWebView);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public void onCloseWindow(WebView window) {
                Log.i(TAG, "onCloseWindow;");
                removeWebView(window);
            }
        });

        return webView;
    }

    private void removeWebView(WebView webView) {
        webView.clearHistory();
        webView.clearCache(true);
        webView.loadUrl("about:blank");
        webView.onPause();
        webView.removeAllViews();

        mWebViews.remove(webView);
        showLatestWebView();
    }

    private void showLatestWebView() {
        mBorderFrameLayout.removeAllViews();
        if (mWebViews.isEmpty()) {
            finish();
        } else {
            WebView latest = mWebViews.get(mWebViews.size() - 1);
            mBorderFrameLayout.addView(latest);
        }
    }

    public static int dp2px(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
    }
}
