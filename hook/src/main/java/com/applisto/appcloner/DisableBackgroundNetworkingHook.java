package com.applisto.appcloner;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DisableBackgroundNetworkingHook - Disables networking when app is in background.
 * 
 * Based on secondary.jar DisableBackgroundNetworking behavior:
 * - Uses activity lifecycle callbacks to detect foreground/background state
 * - When app goes background (last activity stops) -> disables networking
 * - When app comes foreground (first activity starts) -> enables networking after delay
 * - Uses NetworkUtils pattern: bind process to dummy network to block connections
 */
public class DisableBackgroundNetworkingHook implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "DisableBgNetworking";
    private static final String KEY = "disable_background_networking";
    
    // Reference counting for multiple features using NetworkUtils
    private static final Set<String> sDisabledKeys = new HashSet<>();
    private static final AtomicBoolean sInstalled = new AtomicBoolean(false);
    private static final AtomicBoolean sNetworkingDisabled = new AtomicBoolean(false);
    
    // Background detection delay (from secondary.jar: ~1000ms debounce)
    private static final long STOP_DELAY_MS = 1000L;
    
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final AtomicInteger mActivityCount = new AtomicInteger(0);
    private final Runnable mStopRunnable = this::onStopped;
    private Runnable mEnableRunnable;
    
    private Context mContext;
    private int mEnableDelayMillis = 0;
    private boolean mSilent = false;
    
    /**
     * Install the DisableBackgroundNetworking hook.
     * 
     * @param context Application context
     * @param enableDelayMillis Delay before re-enabling networking when app comes foreground
     * @param silent If true, suppress toast messages on failure
     */
    public static void install(Context context, int enableDelayMillis, boolean silent) {
        if (context == null || sInstalled.get()) return;
        
        DisableBackgroundNetworkingHook hook = new DisableBackgroundNetworkingHook();
        hook.mContext = context.getApplicationContext();
        hook.mEnableDelayMillis = enableDelayMillis;
        hook.mSilent = silent;
        
        // Initial disable on install (as per secondary.jar behavior)
        new Thread(() -> {
            disableNetworking(hook.mContext, KEY);
        }).start();
        
        // Register activity lifecycle callbacks
        if (hook.mContext instanceof Application) {
            ((Application) hook.mContext).registerActivityLifecycleCallbacks(hook);
        }
        
        sInstalled.set(true);
        Log.i(TAG, "DisableBackgroundNetworkingHook installed (delay=" + enableDelayMillis + "ms, silent=" + silent + ")");
    }
    
    /**
     * Disable networking for this process by binding to a dummy network.
     */
    public static void disableNetworking(Context context, String key) {
        if (context == null || key == null) return;
        
        // Must not run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new Thread(() -> disableNetworking(context, key)).start();
            return;
        }
        
        synchronized (sDisabledKeys) {
            sDisabledKeys.add(key);
        }
        
        if (sNetworkingDisabled.get()) {
            Log.d(TAG, "Networking already disabled");
            return;
        }
        
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return;
            
            // Pick netId based on Android version (from secondary.jar NetworkUtils)
            int netId;
            if (Build.VERSION.SDK_INT >= 30) {
                netId = 101; // ANDROID_11_NET_ID
            } else if (Build.VERSION.SDK_INT >= 25) {
                netId = 51;  // DUMMY_NET_ID
            } else {
                netId = 99;  // LOCAL_NET_ID
            }
            
            Network dummy = createDummyNetwork(netId);
            if (dummy != null) {
                boolean bound = cm.bindProcessToNetwork(dummy);
                Log.i(TAG, "disableNetworking: bound to dummy network=" + bound + " (netId=" + netId + ")");
                
                // Fallback for older SDK versions if first attempt fails
                if (!bound && Build.VERSION.SDK_INT < 30) {
                    dummy = createDummyNetwork(51);
                    if (dummy != null) {
                        bound = cm.bindProcessToNetwork(dummy);
                        Log.i(TAG, "disableNetworking: fallback bound=" + bound);
                    }
                }
                
                if (bound) {
                    sNetworkingDisabled.set(true);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "disableNetworking failed", t);
        }
    }
    
    /**
     * Enable networking by unbinding from dummy network.
     * Only enables if all keys have been removed.
     */
    public static void enableNetworking(Context context, String key) {
        if (context == null || key == null) return;
        
        // Must not run on main thread
        if (Looper.myLooper() == Looper.getMainLooper()) {
            new Thread(() -> enableNetworking(context, key)).start();
            return;
        }
        
        synchronized (sDisabledKeys) {
            sDisabledKeys.remove(key);
            
            // Only enable if no other features still want networking disabled
            if (!sDisabledKeys.isEmpty()) {
                Log.d(TAG, "Not enabling networking - still disabled by: " + sDisabledKeys);
                return;
            }
        }
        
        if (!sNetworkingDisabled.get()) {
            return;
        }
        
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                boolean unbound = cm.bindProcessToNetwork(null);
                Log.i(TAG, "enableNetworking: unbound from dummy network=" + unbound);
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
    
    // Activity lifecycle callbacks
    
    @Override
    public void onActivityStarted(Activity activity) {
        mHandler.removeCallbacks(mStopRunnable);
        
        if (mActivityCount.getAndIncrement() == 0) {
            // First activity started - app coming to foreground
            if (mEnableRunnable != null) {
                mHandler.removeCallbacks(mEnableRunnable);
            }
            
            mEnableRunnable = () -> {
                new Thread(() -> enableNetworking(mContext, KEY)).start();
            };
            
            mHandler.postDelayed(mEnableRunnable, mEnableDelayMillis);
        }
    }
    
    @Override
    public void onActivityStopped(Activity activity) {
        if (mActivityCount.decrementAndGet() <= 0) {
            // Last activity stopped - schedule disable after debounce delay
            mHandler.postDelayed(mStopRunnable, STOP_DELAY_MS);
        }
    }
    
    private void onStopped() {
        // App went to background - disable networking
        new Thread(() -> disableNetworking(mContext, KEY)).start();
    }
    
    @Override public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
