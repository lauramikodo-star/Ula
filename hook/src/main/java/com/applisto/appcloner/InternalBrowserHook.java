package com.applisto.appcloner;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

public class InternalBrowserHook extends ExecStartActivityHook {
    private static final String TAG = "InternalBrowserHook";
    private final Context mContext;
    private boolean mEnabled;
    private Boolean mFacebookWebViewLoginCookies;

    public InternalBrowserHook(Context context) {
        mContext = context;
    }

    public void init() {
        ClonerSettings settings = ClonerSettings.get(mContext);
        mEnabled = settings.internalBrowserEnabled();
        mFacebookWebViewLoginCookies = settings.facebookWebViewLoginCookies();
        if (mEnabled) {
            install(mContext);
            Log.i(TAG, "InternalBrowserHook installed");
        }
    }

    @Override
    protected boolean onExecStartActivity(ExecStartActivityArgs args) throws ActivityNotFoundException {
        if (!mEnabled) {
            return true;
        }

        Intent intent = args.intent;
        // Don't intercept navigations triggered from the internal browser itself to
        // avoid re-launching the activity for every new URL.
        if (args.target instanceof InternalBrowserActivity) {
            return true;
        }
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String scheme = data.getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    Log.i(TAG, "Intercepting URL: " + data);

                    Intent browserIntent = new Intent(mContext, InternalBrowserActivity.class);
                    browserIntent.setData(data);
                    browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    if (mFacebookWebViewLoginCookies != null) {
                        browserIntent.putExtra("facebookWebViewLoginCookies", mFacebookWebViewLoginCookies);
                    }

                    // Copy extras?
                    if (intent.getExtras() != null) {
                        browserIntent.putExtras(intent.getExtras());
                    }

                    try {
                        mContext.startActivity(browserIntent);
                        return false; // Suppress original call
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start InternalBrowserActivity", e);
                    }
                }
            }
        }

        return true;
    }
}
