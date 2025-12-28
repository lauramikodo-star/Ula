package com.applisto.appcloner;

import android.app.Activity;
import android.app.Application;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * NoBackgroundServicesHook - Mirrors the NoBackgroundServices behavior from secondary.jar.
 * Disables/stops services when the app is backgrounded, tracks startService requests
 * while backgrounded, and re-enables them on foreground. Also hooks exit/kill to
 * perform cleanup before process termination.
 */
public class NoBackgroundServicesHook implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "NoBackgroundServices";
    private static final long BACKGROUND_DELAY_MS = 1000L;
    private static final long KILL_DELAY_MS = 3000L;
    private static final long WATCHDOG_INTERVAL_MS = 30000L;

    private static volatile boolean sHooked;
    private static volatile boolean sAllowExit;

    private final AtomicInteger mActivityCount = new AtomicInteger(0);
    private final AtomicBoolean mRunning = new AtomicBoolean(false);
    private final AtomicBoolean mWatchdogScheduled = new AtomicBoolean(false);
    private final Set<Intent> mServicesToStart = Collections.synchronizedSet(new HashSet<>());
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private Context mContext;
    private boolean mEnabled;

    public void init(Context context) {
        if (sHooked) return;
        mContext = context.getApplicationContext();

        try {
            mEnabled = ClonerSettings.get(context).raw().optBoolean("NoBackgroundServices", false);
        } catch (Throwable t) {
            mEnabled = false;
        }

        if (!mEnabled) return;

        Hooking.initHooking(mContext);
        hookProcessExit();
        hookStartStopService();

        mRunning.set(false);
        mHandler.postDelayed(this::scheduleWatchdogIfNeeded, WATCHDOG_INTERVAL_MS);

        // Register for activity lifecycle events
        if (mContext instanceof Application) {
            ((Application) mContext).registerActivityLifecycleCallbacks(this);
        }

        sHooked = true;
        Log.i(TAG, "NoBackgroundServicesHook initialized");
    }

    /* ---------- Hooks ---------- */

    private void hookProcessExit() {
        try {
            Method killProcess = Process.class.getMethod("killProcess", int.class);
            Hooking.pineHook(killProcess, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (!mEnabled) return;
                    int pid = (int) cf.args[0];
                    if (pid == Process.myPid() && !sAllowExit) {
                        Log.i(TAG, "Intercepting killProcess; performing cleanup first");
                        cleanupBeforeExit();
                        cf.setResult(null);
                        mHandler.postDelayed(() -> {
                            sAllowExit = true;
                            Process.killProcess(pid);
                        }, KILL_DELAY_MS);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook killProcess", e);
        }

        try {
            Method runtimeExit = Runtime.class.getMethod("exit", int.class);
            Hooking.pineHook(runtimeExit, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    handleRuntimeExit(cf);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook Runtime.exit", e);
        }

        try {
            Method runtimeHalt = Runtime.class.getMethod("halt", int.class);
            Hooking.pineHook(runtimeHalt, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    handleRuntimeExit(cf);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook Runtime.halt", e);
        }
    }

    private void handleRuntimeExit(Pine.CallFrame cf) {
        if (!mEnabled) return;
        if (!sAllowExit) {
            Log.i(TAG, "Intercepting Runtime exit; performing cleanup first");
            cleanupBeforeExit();
            cf.setResult(null);
            mHandler.postDelayed(() -> {
                sAllowExit = true;
                try {
                    android.os.Process.killProcess(android.os.Process.myPid());
                } catch (Throwable ignored) {}
            }, KILL_DELAY_MS);
        }
    }

    private void hookStartStopService() {
        try {
            Method startService = ContextWrapper.class.getMethod("startService", Intent.class);
            Hooking.pineHook(startService, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (!mEnabled) return;
                    Intent intent = (Intent) cf.args[0];
                    if (intent == null) return;
                    if (!mRunning.get()) {
                        mServicesToStart.add(new Intent(intent));
                        cf.setResult(null);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook startService", e);
        }

        try {
            Method stopService = ContextWrapper.class.getMethod("stopService", Intent.class);
            Hooking.pineHook(stopService, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (!mEnabled) return;
                    Intent intent = (Intent) cf.args[0];
                    if (intent != null) {
                        mServicesToStart.remove(intent);
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook stopService", e);
        }
    }

    /* ---------- Lifecycle ---------- */

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (!mEnabled) return;
        int count = mActivityCount.incrementAndGet();
        if (count == 1) {
            mRunning.set(true);
            enableServices();
            restartQueuedServices();
        }
    }

    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

    @Override
    public void onActivityDestroyed(Activity activity) {
        if (!mEnabled) return;
        int remaining = mActivityCount.decrementAndGet();
        if (remaining <= 0) {
            mHandler.postDelayed(() -> {
                if (mActivityCount.get() <= 0) {
                    mRunning.set(false);
                    disableServices();
                    stopServices();
                    scheduleWatchdogIfNeeded();
                }
            }, BACKGROUND_DELAY_MS);
        }
    }

    /* ---------- Helpers ---------- */

    private void restartQueuedServices() {
        if (mServicesToStart.isEmpty()) return;
        synchronized (mServicesToStart) {
            Iterator<Intent> it = mServicesToStart.iterator();
            while (it.hasNext()) {
                Intent intent = it.next();
                try {
                    mContext.startService(intent);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to restart service: " + intent, e);
                }
                it.remove();
            }
        }
    }

    private void enableServices() {
        try {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo pkg = pm.getPackageInfo(mContext.getPackageName(),
                    PackageManager.GET_SERVICES | PackageManager.GET_DISABLED_COMPONENTS);
            if (pkg.services == null) return;
            for (ServiceInfo si : pkg.services) {
                ComponentName cn = new ComponentName(si.packageName, si.name);
                pm.setComponentEnabledSetting(cn,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (Exception e) {
            Log.w(TAG, "enableServices failed", e);
        }
    }

    private void disableServices() {
        try {
            PackageManager pm = mContext.getPackageManager();
            PackageInfo pkg = pm.getPackageInfo(mContext.getPackageName(),
                    PackageManager.GET_SERVICES);
            if (pkg.services == null) return;
            for (ServiceInfo si : pkg.services) {
                if ("com.google.firebase.components.ComponentDiscoveryService".equals(si.name)) continue;
                ComponentName cn = new ComponentName(si.packageName, si.name);
                pm.setComponentEnabledSetting(cn,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP);
            }
        } catch (Exception e) {
            Log.w(TAG, "disableServices failed", e);
        }
    }

    private void stopServices() {
        try {
            ActivityManager am = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return;
            for (ActivityManager.RunningServiceInfo info : am.getRunningServices(Integer.MAX_VALUE)) {
                if (!mContext.getPackageName().equals(info.service.getPackageName())) continue;
                if (info.started) {
                    try {
                        Intent stopIntent = new Intent();
                        stopIntent.setComponent(info.service);
                        mContext.stopService(stopIntent);
                    } catch (Exception e) {
                        Log.w(TAG, "stopServices failed for " + info.service, e);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "stopServices failed", e);
        }
    }

    private void scheduleWatchdogIfNeeded() {
        if (!mEnabled) return;
        if (!mRunning.get() && sHooked && mWatchdogScheduled.compareAndSet(false, true)) {
            mHandler.postDelayed(() -> {
                mWatchdogScheduled.set(false);
                if (!mRunning.get()) {
                    disableServices();
                    stopServices();
                    scheduleWatchdogIfNeeded();
                }
            }, WATCHDOG_INTERVAL_MS);
        }
    }

    private void cleanupBeforeExit() {
        disableServices();
        stopServices();
    }
}
