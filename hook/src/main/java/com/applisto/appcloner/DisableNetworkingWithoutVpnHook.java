package com.applisto.appcloner;

import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Constructor;

/**
 * DisableNetworkingWithoutVpnHook - process-level kill-switch that binds the app
 * to a dummy network whenever VPN is not active, restoring networking once VPN appears.
 * Mirrors secondary.jar DisableNetworkingWithoutVpn behavior.
 */
public class DisableNetworkingWithoutVpnHook implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "DisableNetWithoutVpn";
    private static final long CHECK_INTERVAL_MS = 10_000L;
    private static final AtomicBoolean sInstalled = new AtomicBoolean(false);
    private static final AtomicBoolean sNetworkingDisabled = new AtomicBoolean(false);

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger mActivityCount = new AtomicInteger(0);

    private Context mContext;
    private boolean mEnabled;

    public void init(Context context) {
        if (context == null || sInstalled.get()) return;
        mContext = context.getApplicationContext();

        try {
            mEnabled = ClonerSettings.get(context).raw().optBoolean("DisableNetworkingWithoutVpn", false);
        } catch (Throwable t) {
            mEnabled = false;
        }

        if (!mEnabled) return;

        if (mContext instanceof Application) {
            ((Application) mContext).registerActivityLifecycleCallbacks(this);
        }

        sInstalled.set(true);
        Log.i(TAG, "DisableNetworkingWithoutVpnHook initialized");
        mHandler.post(this::evaluateAndSchedule);
    }

    private void evaluateAndSchedule() {
        try {
            evaluateVpnState();
        } catch (Throwable t) {
            Log.w(TAG, "evaluateVpnState failed", t);
        } finally {
            mHandler.postDelayed(this::evaluateAndSchedule, CHECK_INTERVAL_MS);
        }
    }

    private void evaluateVpnState() {
        boolean vpnActive = isVpnActive();
        if (vpnActive) {
            enableNetworking();
        } else {
            disableNetworking();
        }
    }

    private boolean isVpnActive() {
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        } catch (Throwable t) {
            Log.w(TAG, "isVpnActive failed", t);
            return false;
        }
    }

    private void disableNetworking() {
        if (sNetworkingDisabled.get()) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                int netId;
                if (Build.VERSION.SDK_INT >= 30) {
                    netId = 101;
                } else if (Build.VERSION.SDK_INT >= 25) {
                    netId = 51;
                } else {
                    netId = 99;
                }
                Network dummy = createDummyNetwork(netId);
                if (dummy != null) {
                    boolean bound = cm.bindProcessToNetwork(dummy);
                    Log.i(TAG, "disableNetworking; bound to dummy network: " + bound + " (netId=" + netId + ")");
                    if (bound) {
                        sNetworkingDisabled.set(true);
                    }
                } else {
                    Log.w(TAG, "disableNetworking; failed to create dummy network, skip bind");
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "disableNetworking failed", t);
        }
    }

    private void enableNetworking() {
        if (!sNetworkingDisabled.get()) return;
        try {
            ConnectivityManager cm = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                boolean unbound = cm.bindProcessToNetwork(null);
                Log.i(TAG, "enableNetworking; unbound dummy network: " + unbound);
            }
        } catch (Throwable t) {
            Log.w(TAG, "enableNetworking failed", t);
        } finally {
            sNetworkingDisabled.set(false);
        }
    }

    private static Network createDummyNetwork(int netId) {
        try {
            Constructor<Network> c = Network.class.getDeclaredConstructor(int.class);
            c.setAccessible(true);
            return c.newInstance(netId);
        } catch (Throwable t) {
            Log.w(TAG, "createDummyNetwork failed", t);
            return null;
        }
    }

    /* Activity lifecycle: re-evaluate when app moves foreground/background */
    @Override
    public void onActivityStarted(android.app.Activity activity) {
        if (!mEnabled) return;
        if (mActivityCount.getAndIncrement() == 0) {
            evaluateVpnState();
        }
    }

    @Override public void onActivityCreated(android.app.Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(android.app.Activity activity) {}
    @Override public void onActivityPaused(android.app.Activity activity) {}
    @Override public void onActivitySaveInstanceState(android.app.Activity activity, Bundle outState) {}
    @Override public void onActivityStopped(android.app.Activity activity) {
        if (!mEnabled) return;
        if (mActivityCount.decrementAndGet() <= 0) {
            evaluateVpnState();
        }
    }
    @Override public void onActivityDestroyed(android.app.Activity activity) {}
}
