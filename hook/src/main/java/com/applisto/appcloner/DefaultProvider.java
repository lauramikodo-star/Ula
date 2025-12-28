package com.applisto.appcloner;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.applisto.appcloner.NoBackgroundServicesHook;
import com.applisto.appcloner.OverridePreferencesHook;
import com.applisto.appcloner.DisableNetworkingWithoutVpnHook;
import com.applisto.appcloner.DisableBackgroundNetworkingHook;
import com.applisto.appcloner.ChangeSystemUserAgentHook;
import com.applisto.appcloner.HideCpuInfoHook;
import com.applisto.appcloner.HideGpuInfoHook;
import com.applisto.appcloner.HideDnsServersHook;
import com.applisto.appcloner.HideSimOperatorHook;

public class DefaultProvider extends AbstractContentProvider {
    private static final String TAG = "DefaultProvider";
    
    // Track initialization state
    private static final AtomicBoolean sInitialized = new AtomicBoolean(false);
    private static volatile AccessibleDataDirHook sAccessibleDirHook;
    
    // Dynamic authority based on package name
    private String mAuthority;

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);
        if (info != null) {
            mAuthority = info.authority;
            Log.d(TAG, "Provider attached with authority: " + mAuthority);
        }
    }

    @Override
    public boolean onCreate() {
        Context context = getContext();
        if (context == null) {
            Log.e(TAG, "Context is null");
            return false;
        }
        
        // Prevent double initialization
        if (!sInitialized.compareAndSet(false, true)) {
            Log.w(TAG, "DefaultProvider already initialized, skipping");
            return true;
        }

        Log.i(TAG, "Initializing hooks for package: " + context.getPackageName());

        // Register DataExportReceiver
        try {
            IntentFilter filter = new IntentFilter(DataExportReceiver.ACTION_EXPORT_DATA);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(new DataExportReceiver(), filter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(new DataExportReceiver(), filter);
            }
        } catch (Throwable t) {
             Log.e(TAG, "Failed to register DataExportReceiver", t);
        }

        // Register IdentityRegenerationReceiver for generating new device identities
        try {
            IntentFilter identityFilter = new IntentFilter(IdentityRegenerationReceiver.ACTION_REGENERATE_IDENTITY);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(new IdentityRegenerationReceiver(), identityFilter, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(new IdentityRegenerationReceiver(), identityFilter);
            }
            Log.i(TAG, "IdentityRegenerationReceiver registered");

            // Show an opt-in notification in the cloned app to trigger regeneration
            ClonerSettings settings = ClonerSettings.get(context);
            if (settings.identityNotificationsEnabled()) {
                boolean persistent = settings.identityNotificationsPersistent();
                Log.i(TAG, "Showing identity notification (persistent=" + persistent + ")");
                
                if (persistent) {
                    // Show persistent notification that cannot be dismissed
                    IdentityRegenerationReceiver.showPersistentIdentityNotification(
                            context,
                            settings.identityNotificationsClearCache(),
                            settings.identityNotificationsClearData(),
                            settings.identityNotificationsRestartApp()
                    );
                } else {
                    // Show regular dismissible notification
                    IdentityRegenerationReceiver.showIdentityNotification(
                            context,
                            settings.identityNotificationsClearCache(),
                            settings.identityNotificationsClearData(),
                            settings.identityNotificationsRestartApp()
                    );
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to register IdentityRegenerationReceiver", t);
        }

        // Handle bundled app data restore
        try {
             ClonerSettings settings = ClonerSettings.get(context);
             if (settings.bundleAppData()) {
                 Log.i(TAG, "bundle_app_data enabled, attempting to import bundled data...");
                 AppDataManager dataManager = new AppDataManager(context, context.getPackageName(), false);
                 dataManager.importBundledAppDataIfAvailable();
             }
        } catch (Throwable t) {
             Log.e(TAG, "Failed to handle bundled app data", t);
        }


        /* 1.  initialise the smart engine once */
        com.applisto.appcloner.hooking.Hooking.setUseNewHooks(true);
        com.applisto.appcloner.hooking.Hooking.initHooking(context);   // <-- NEW
        
        new Socks5ProxyHook().init(context);
        
       // new ClonerSettings.get(context);

        /* 2.  all your existing hooks stay identical */
        new AndroidIdHook().init(context);
        new WifiMacHook().init(context);
        new BuildPropsHook().init(context);
        new UserAgentHook().init(context);
        // Add after other hooks in onCreate()
        new BackgroundMediaHook().init(context);
        
        FakeCameraHook hook = new FakeCameraHook();
        hook.init(context);

        // Initialize Fake Calculator Hook
        FakeCalculatorHook calculatorHook = new FakeCalculatorHook(context);
        calculatorHook.install(context);

        // Get fresh settings instance since previous one was scoped
        ClonerSettings settings = ClonerSettings.get(context);

        if (settings.keepPlayingMedia()) {
            KeepPlayingMedia.install(context, settings.keepPlayingMediaCompatibilityMode());
        }

        CustomBuildPropsFile.install(context, settings.customBuildPropsString(), true);

        MockNetworkConnection.install(context,
                settings.mockWifiConnection(),
                settings.mockMobileConnection(),
                settings.mockEthernetConnection());

        if (settings.disableLicenseValidation()) {
            DisableLicenseValidation.install(context);
        }

        if (settings.hideRoot()) {
            HideRoot.install(context);
        }

        if (settings.hideEmulator()) {
            HideEmulator.install(context);
        }

        OverridePreferencesHook.install(context);

        if (settings.noBackgroundServices()) {
            new NoBackgroundServicesHook().init(context);
        }

        if (settings.disableNetworkingWithoutVpn()) {
            new DisableNetworkingWithoutVpnHook().init(context);
        }
        
        // Disable Background Networking
        if (settings.disableBackgroundNetworking()) {
            DisableBackgroundNetworkingHook.install(
                context,
                settings.disableBackgroundNetworkingDelay(),
                settings.disableBackgroundNetworkingSilent()
            );
        }
        
        // Change System User Agent
        if (settings.changeSystemUserAgent()) {
            String ua = settings.systemUserAgent();
            if (ua == null || ua.isEmpty()) {
                ua = settings.userAgent(); // Fallback to regular user agent
            }
            if (ua != null && !ua.isEmpty()) {
                ChangeSystemUserAgentHook.install(context, ua);
            }
        }
        
        // Hide CPU Info
        if (settings.hideCpuInfo()) {
            HideCpuInfoHook.install(context, settings.customCpuInfo());
        }
        
        // Hide GPU Info
        if (settings.hideGpuInfo()) {
            HideGpuInfoHook.install(
                context,
                settings.gpuVendor(),
                settings.gpuRenderer(),
                settings.gpuVersion()
            );
        }
        
        // Hide DNS Servers
        if (settings.hideDnsServers()) {
            HideDnsServersHook.install(
                context,
                settings.hideDnsServersCompletely(),
                settings.spoofedDnsServers()
            );
        }
        
        // Hide SIM Operator
        if (settings.hideSimOperator()) {
            String opName = settings.spoofedOperatorName();
            String opNumeric = settings.spoofedOperatorNumeric();
            String countryIso = settings.spoofedSimCountryIso();
            
            if ((opName != null && !opName.isEmpty()) || 
                (opNumeric != null && !opNumeric.isEmpty())) {
                HideSimOperatorHook.install(context, opName, opNumeric, opName, countryIso, opName, countryIso);
            } else {
                HideSimOperatorHook.install(context); // Hide all
            }
        }
        
        if (settings.liveVideoHookEnabled()) {
             LiveVideoHook.install(context);
        }

        // Initialize location spoofing hook
        SpoofLocationHook locationHook = new SpoofLocationHook();
        locationHook.init(context);
        // Optional: Set custom location
        // SpoofLocationHook.setSpoofedLocation(40.7128, -74.0060); // New York
        // SpoofLocationHook.enableLocationSpoofing(true);

        // Device Identity Hooks
        new ImsiHook().init(context);
        new ImeiHook().init(context);  // NEW: IMEI spoofing
        new SerialHook().init(context);
        new BtMacHook().init(context);

        // Dialog Intercept and Blocker (replaced by SkipDialogs logic or kept parallel?)
        // The user implied "skipdialog" is what they want.
        // new DialogInterceptHook().init(context);

        // Skip Dialogs
        java.util.Properties props = new java.util.Properties();
        // Populate properties from settings if needed, or pass empty if hardcoded checks are enough
        // properties used for "device_lock_title" etc.
        // We can load them from cloner.json if they exist
        try {
            org.json.JSONObject json = settings.raw();
            java.util.Iterator<String> keys = json.keys();
            while(keys.hasNext()) {
                String key = keys.next();
                props.setProperty(key, json.optString(key));
            }
        } catch (Exception ignored) {}

        SkipDialogs.install(context, settings.skipDialogs(), settings.skipDialogsStacktraces(), settings.monitorStacktraces(), props);

        // Internal Browser Hook (for intercepting URLs)
        InternalBrowserHook browserHook = new InternalBrowserHook(context);
        browserHook.init();
        
        // Initialize AccessibleDataDirHook and keep reference for SharedPrefs operations
        sAccessibleDirHook = new AccessibleDataDirHook();
        sAccessibleDirHook.init(context);

        ForcedBackCameraHook.install(context);
        ScreenshotDetectionBlocker.install(context);

        if (settings.userAgentWorkaround()) {
            UserAgentWorkaround.install(context, settings.userAgentWorkaroundUriSchemeWorkaround());
        }

        if (settings.packageNameWorkaround()) {
            PackageNameWorkaround.install(context);
        }

        new FixBundleClassLoaderHook().init(context);

        // Initialize Local Web Console
        // Force enable if Host Monitor is enabled, as it relies on the web server
        if (settings.localWebConsoleEnabled() || settings.hostMonitorEnabled()) {
            try {
                LocalWebConsole.install(context, 18080);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to install LocalWebConsole", t);
            }
        }

        // Initialize Host Monitor
        if (settings.hostMonitorEnabled()) {
            try {
                HostMonitor.install(context, settings.hostMonitorFilter(), 2000, true);
            } catch (Throwable t) {
                Log.e(TAG, "Failed to install HostMonitor", t);
            }
        }

        if (settings.headerMonitorEnabled()) {
            try {
                HeaderMonitor.install();
                HostMonitorNotifications.install(context, 2001, "header-monitor", "Header Monitor");
            } catch (Throwable t) {
                Log.e(TAG, "Failed to install HeaderMonitor", t);
            }
        }

        if (settings.preferencesMonitorEnabled()) {
            try {
                PreferencesMonitor.install();
                HostMonitorNotifications.install(context, 2002, "preferences-monitor", "Preferences Monitor");
            } catch (Throwable t) {
                Log.e(TAG, "Failed to install PreferencesMonitor", t);
            }
        }

        Log.i(TAG, "All hooks initialized.");
        return true;
    }

    // IPC permission for secure operations
    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";
    
    // Handler for main thread operations
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Log.d(TAG, "call() method=" + method + ", arg=" + arg);
        
        // Check if caller has IPC permission OR is the same app (for internal calls)
        if (!hasIpcPermissionOrSameApp()) {
            Log.w(TAG, "call() denied: caller lacks IPC permission. CallingUid=" + Binder.getCallingUid());
            Bundle result = new Bundle();
            result.putBoolean("ok", false);
            result.putString("error", "Permission denied - IPC permission required");
            return result;
        }

        try {
            if ("list_prefs".equals(method)) {
                return listPrefs();
            } else if ("get_prefs".equals(method)) {
                return getPrefs(arg);
            } else if ("put_pref".equals(method)) {
                return putPref(extras);
            } else if ("remove_pref".equals(method)) {
                return removePref(extras);
            } else if ("ping".equals(method)) {
                // Simple ping method to test provider connectivity
                Bundle result = new Bundle();
                result.putBoolean("ok", true);
                result.putString("package", getContext().getPackageName());
                result.putString("authority", mAuthority);
                return result;
            } else if ("get_config".equals(method)) {
                // Return current configuration
                return getConfig();
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error in call() method=" + method, t);
            Bundle result = new Bundle();
            result.putBoolean("ok", false);
            result.putString("error", "Internal error: " + t.getMessage());
            return result;
        }
        return super.call(method, arg, extras);
    }

    /**
     * Check if caller has IPC permission or is calling from within the same app.
     * This allows both the app cloner (with IPC permission) and internal hooks
     * (same UID) to access the provider.
     */
    private boolean hasIpcPermissionOrSameApp() {
        Context context = getContext();
        if (context == null) return false;

        // Allow same-app calls (internal hooks)
        int callingUid = Binder.getCallingUid();
        int myUid = android.os.Process.myUid();
        if (callingUid == myUid) {
            Log.d(TAG, "Same app call - permission granted");
            return true;
        }

        // Check if caller has IPC permission
        try {
            // First try checkCallingPermission (most accurate for IPC)
            int permissionCheck = context.checkCallingPermission(IPC_PERMISSION);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "IPC permission granted via checkCallingPermission");
                return true;
            }
            
            // Fallback: check with checkCallingOrSelfPermission
            permissionCheck = context.checkCallingOrSelfPermission(IPC_PERMISSION);
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "IPC permission granted via checkCallingOrSelfPermission");
                return true;
            }
            
            // Additional fallback: Check if the calling package has the permission declared
            String[] callingPackages = context.getPackageManager().getPackagesForUid(callingUid);
            if (callingPackages != null) {
                for (String pkg : callingPackages) {
                    // Allow if caller is the app cloner
                    if ("com.appcloner.replica".equals(pkg)) {
                        Log.d(TAG, "Caller is app cloner - permission granted");
                        return true;
                    }
                    // Check package permissions
                    try {
                        int pkgPermission = context.getPackageManager().checkPermission(IPC_PERMISSION, pkg);
                        if (pkgPermission == PackageManager.PERMISSION_GRANTED) {
                            Log.d(TAG, "Package " + pkg + " has IPC permission");
                            return true;
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            // Log failure for debugging
            Log.w(TAG, "Permission check failed. CallingUid=" + callingUid + ", MyUid=" + myUid + 
                  ", CallingPackages=" + (callingPackages != null ? String.join(",", callingPackages) : "null"));
            return false;
        } catch (Exception e) {
            Log.w(TAG, "Error checking permission", e);
            return false;
        }
    }
    
    /**
     * Get current hook configuration.
     */
    private Bundle getConfig() {
        Bundle result = new Bundle();
        try {
            Context context = getContext();
            if (context != null) {
                ClonerSettings settings = ClonerSettings.get(context);
                // Add key configuration values
                result.putBoolean("fake_calculator_enabled", settings.fakeCalculatorEnabled());
                result.putBoolean("fake_camera_enabled", settings.fakeCameraEnabled());
                result.putBoolean("spoof_location_enabled", settings.spoofLocationEnabled());
                result.putBoolean("dialog_blocker_enabled", settings.dialogBlockerEnabled());
                result.putBoolean("background_media", settings.raw().optBoolean("background_media", false));
                result.putBoolean("ok", true);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Error getting config", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }

    private Bundle listPrefs() {
        Bundle result = new Bundle();
        try {
            File prefsDir = new File(getContext().getApplicationInfo().dataDir, "shared_prefs");
            ArrayList<String> files = new ArrayList<>();
            if (prefsDir.exists() && prefsDir.isDirectory()) {
                File[] list = prefsDir.listFiles();
                if (list != null) {
                    for (File f : list) {
                        String name = f.getName();
                        if (name.endsWith(".xml")) {
                            files.add(name.substring(0, name.length() - 4));
                        }
                    }
                }
            }
            result.putStringArrayList("files", files);
        } catch (Throwable t) {
            Log.e(TAG, "listPrefs error", t);
        }
        return result;
    }

    private Bundle getPrefs(String file) {
        Bundle result = new Bundle();
        if (file == null) return result;
        try {
            SharedPreferences prefs = getContext().getSharedPreferences(file, Context.MODE_PRIVATE);
            Map<String, ?> all = prefs.getAll();
            for (Map.Entry<String, ?> entry : all.entrySet()) {
                String key = entry.getKey();
                Object val = entry.getValue();
                if (val instanceof Boolean) {
                    result.putBoolean(key, (Boolean) val);
                } else if (val instanceof Integer) {
                    result.putInt(key, (Integer) val);
                } else if (val instanceof Long) {
                    result.putLong(key, (Long) val);
                } else if (val instanceof Float) {
                    result.putFloat(key, (Float) val);
                } else if (val instanceof String) {
                    result.putString(key, (String) val);
                } else if (val instanceof Set) {
                    // Bundle doesn't support Set<String>, so we use ArrayList<String>
                    result.putStringArrayList(key, new ArrayList<>((Set<String>) val));
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "getPrefs error", t);
        }
        return result;
    }

    private Bundle putPref(Bundle extras) {
        Bundle result = new Bundle();
        if (extras == null) {
            result.putBoolean("ok", false);
            result.putString("error", "Extras is null");
            return result;
        }
        
        final String file = extras.getString("file");
        final String key = extras.getString("key");
        final String type = extras.getString("type");
        
        if (file == null || key == null || type == null) {
            result.putBoolean("ok", false);
            result.putString("error", "Missing args: file=" + file + ", key=" + key + ", type=" + type);
            return result;
        }

        Log.d(TAG, "putPref: file=" + file + ", key=" + key + ", type=" + type);
        
        try {
            final Context context = getContext();
            if (context == null) {
                result.putBoolean("ok", false);
                result.putString("error", "Context is null");
                return result;
            }
            
            // Perform SharedPreferences operation on main thread to avoid concurrency issues
            final AtomicBoolean committed = new AtomicBoolean(false);
            final AtomicReference<String> errorRef = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(1);
            
            Runnable writeOp = () -> {
                try {
                    SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
                    SharedPreferences.Editor edit = prefs.edit();

                    switch (type) {
                        case "Boolean":
                            edit.putBoolean(key, extras.getBoolean("value"));
                            break;
                        case "Integer":
                            edit.putInt(key, extras.getInt("value"));
                            break;
                        case "Long":
                            edit.putLong(key, extras.getLong("value"));
                            break;
                        case "Float":
                            edit.putFloat(key, extras.getFloat("value"));
                            break;
                        case "StringSet":
                            ArrayList<String> list = extras.getStringArrayList("value");
                            if (list != null) {
                                edit.putStringSet(key, new HashSet<>(list));
                            }
                            break;
                        default: // String
                            edit.putString(key, extras.getString("value"));
                    }
                    
                    // Use commit() for synchronous write
                    committed.set(edit.commit());
                    
                    // Refresh file permissions after write
                    if (sAccessibleDirHook != null && committed.get()) {
                        sAccessibleDirHook.ensureSharedPrefsAccessible();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "putPref write error", t);
                    errorRef.set(t.getMessage());
                } finally {
                    latch.countDown();
                }
            };
            
            // Execute on main thread if we're on a different thread
            if (Looper.myLooper() == Looper.getMainLooper()) {
                writeOp.run();
            } else {
                sMainHandler.post(writeOp);
                // Wait for operation to complete with timeout
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    result.putBoolean("ok", false);
                    result.putString("error", "Operation timed out");
                    return result;
                }
            }
            
            if (errorRef.get() != null) {
                result.putBoolean("ok", false);
                result.putString("error", errorRef.get());
            } else {
                result.putBoolean("ok", committed.get());
                if (!committed.get()) {
                    result.putString("error", "Failed to commit SharedPreferences");
                }
            }
            
            Log.d(TAG, "putPref committed=" + committed.get());
            
        } catch (Throwable t) {
            Log.e(TAG, "putPref error", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }

    private Bundle removePref(Bundle extras) {
        Bundle result = new Bundle();
        if (extras == null) {
            result.putBoolean("ok", false);
            result.putString("error", "Extras is null");
            return result;
        }
        
        final String file = extras.getString("file");
        final String key = extras.getString("key");
        
        if (file == null || key == null) {
            result.putBoolean("ok", false);
            result.putString("error", "Missing args: file=" + file + ", key=" + key);
            return result;
        }
        
        Log.d(TAG, "removePref: file=" + file + ", key=" + key);
        
        try {
            final Context context = getContext();
            if (context == null) {
                result.putBoolean("ok", false);
                result.putString("error", "Context is null");
                return result;
            }
            
            final AtomicBoolean committed = new AtomicBoolean(false);
            final AtomicReference<String> errorRef = new AtomicReference<>();
            final CountDownLatch latch = new CountDownLatch(1);
            
            Runnable removeOp = () -> {
                try {
                    SharedPreferences prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE);
                    committed.set(prefs.edit().remove(key).commit());
                    
                    // Refresh file permissions after write
                    if (sAccessibleDirHook != null && committed.get()) {
                        sAccessibleDirHook.ensureSharedPrefsAccessible();
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "removePref write error", t);
                    errorRef.set(t.getMessage());
                } finally {
                    latch.countDown();
                }
            };
            
            if (Looper.myLooper() == Looper.getMainLooper()) {
                removeOp.run();
            } else {
                sMainHandler.post(removeOp);
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    result.putBoolean("ok", false);
                    result.putString("error", "Operation timed out");
                    return result;
                }
            }
            
            if (errorRef.get() != null) {
                result.putBoolean("ok", false);
                result.putString("error", errorRef.get());
            } else {
                result.putBoolean("ok", committed.get());
                if (!committed.get()) {
                    result.putString("error", "Failed to commit SharedPreferences");
                }
            }
            
            Log.d(TAG, "removePref committed=" + committed.get());
            
        } catch (Throwable t) {
            Log.e(TAG, "removePref error", t);
            result.putBoolean("ok", false);
            result.putString("error", t.getMessage());
        }
        return result;
    }
}
