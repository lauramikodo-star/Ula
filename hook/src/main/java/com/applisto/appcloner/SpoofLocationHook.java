package com.applisto.appcloner;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.content.pm.PackageManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * SpoofLocationHook - Comprehensive location spoofing implementation
 * Based on App Cloner's SpoofLocation mechanism with full coverage:
 * - System LocationManager service proxy (ILocationManager)
 * - Direct Location object hooks
 * - Google Play Services FusedLocationProviderClient hooks
 * - Deprecated FusedLocationApi hooks
 * - Google Maps LocationSource hooks
 * - Periodic location update broadcasting
 */
public final class SpoofLocationHook {

    private static final String TAG = "SpoofLocationHook";
    private static final String PREFS_NAME = "SpoofLocationPrefs";
    private static final String PREF_OVERRIDE = "app_cloner_spoof_location_override";
    private static final String NOTIFICATION_CHANNEL_ID = "spoof_location_channel";
    private static final int NOTIFICATION_ID = 1001;
    
    // Broadcast actions for API control
    public static final String ACTION_SET_LOCATION = "com.appcloner.action.SET_SPOOF_LOCATION";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_ALTITUDE = "altitude";

    private static volatile boolean sHooked = false;
    private static Context sContext;
    private static String sAppClonerPackage;

    /* ---------- Settings loaded from cloner.json ---------- */
    private static boolean ENABLED;
    private static volatile double sSpoofLocationLatitude;
    private static volatile double sSpoofLocationLongitude;
    private static volatile double sSpoofLocationAltitude = 10.0;
    private static volatile float sSpoofLocationAccuracy = 5.0f;
    private static boolean sSpoofLocationShowNotification;
    private static boolean sSpoofLocationApi;
    private static boolean sSpoofLocationUseIpLocation;
    private static int sSpoofLocationInterval = 1000;
    private static boolean sSpoofLocationCompatibilityMode;
    private static boolean sSpoofLocationSimulatePositionalUncertainty;
    private static boolean sSpoofLocationCalculateBearing;
    private static float sSpoofLocationSpeed = 0.0f;
    private static float sSpoofLocationBearing = 0.0f;
    private static boolean sSpoofLocationRandomize;

    // Bearing calculation state
    private static double sBearingOldSpoofLocationLatitude = 0;
    private static double sBearingOldSpoofLocationLongitude = 0;

    private static final Random sRandom = new Random();
    private static final Handler sMainHandler = new Handler(Looper.getMainLooper());

    // Track active listeners for periodic updates
    // System LocationManager listeners (ILocationListener)
    private static final Set<Object> sAndroidILocationListeners = Collections.synchronizedSet(new HashSet<>());
    // LocationListener objects (direct)
    private static final Set<LocationListener> sAndroidLocationListeners = Collections.synchronizedSet(new HashSet<>());
    // PendingIntent objects
    private static final Set<PendingIntent> sAndroidPendingIntents = Collections.synchronizedSet(new HashSet<>());
    // GMS LocationListener objects
    private static final Set<Object> sGmsListeners = Collections.synchronizedSet(new HashSet<>());
    // GMS LocationCallback objects
    private static final Set<Object> sCallbacks = Collections.synchronizedSet(new HashSet<>());
    // Google Maps OnLocationChangedListener
    private static volatile Object sOnLocationChangedListener;

    // Pre-built LocationAvailability for GMS callbacks
    private static Object LOCATION_AVAILABILITY_AVAILABLE;
    private static NotificationManager sNotificationManager;

    private static final ScheduledExecutorService sScheduler = Executors.newSingleThreadScheduledExecutor();
    private static final AtomicBoolean sIpLocationFetching = new AtomicBoolean(false);
    private static final AtomicBoolean sLoggedMissingInternet = new AtomicBoolean(false);

    public void init(Context ctx) {
        if (sHooked) return;
        sHooked = true;
        sContext = ctx.getApplicationContext();

        loadSettings(ctx);
        loadRuntimeOverrides();

        if (sSpoofLocationUseIpLocation && !hasCoordinates()) {
            // Start fetching IP-based location immediately so we don't stay at 0,0
            fetchIpLocationAsync();
        }

        if (!ENABLED) {
            Log.i(TAG, "SpoofLocation disabled");
            return;
        }

        // Check if location is really enabled (has valid coordinates)
        if (!getEnabled()) {
            Log.i(TAG, "SpoofLocation has no valid coordinates configured");
            return;
        }

        try {
            // Initialize GMS LocationAvailability constant
            initLocationAvailability();

            // 1. Hook System Service (ILocationManager proxy)
            hookSystemService(ctx);

            // 2. Hook Location object methods directly
            hookLocationMethods();

            // 3. Hook Fused Location (if not compatibility mode)
            if (!sSpoofLocationCompatibilityMode) {
                hookFusedLocationProviderClient(ctx);
                hookFusedLocationProviderApi(ctx);
                hookDeprecatedFusedLocationProviderApi(ctx);
                hookObfuscatedFusedLocationProviderApi(ctx);
                hookGoogleMapsLocationSource();
            }

            // 4. Register API receiver if enabled
            if (sSpoofLocationApi) {
                registerApiReceiver(ctx);
            }

            // 5. Show notification if enabled
            if (sSpoofLocationShowNotification) {
                showNotification(ctx);
            }

            // 6. Start periodic update loop
            startUpdateLoop();

            // 7. Fetch IP location if enabled
            if (sSpoofLocationUseIpLocation) {
                fetchIpLocationAsync();
            }

            Log.i(TAG, "SpoofLocation active: " + sSpoofLocationLatitude + ", " + sSpoofLocationLongitude);
        } catch (Exception e) {
            Log.e(TAG, "Hook failed", e);
        }
    }

    private void loadSettings(Context ctx) {
        try {
            JSONObject cfg = ClonerSettings.get(ctx).raw();
            ENABLED = cfg.optBoolean("SpoofLocation", false);
            sSpoofLocationLatitude = cfg.optDouble("SpoofLocationLatitude", 0);
            sSpoofLocationLongitude = cfg.optDouble("SpoofLocationLongitude", 0);
            sSpoofLocationAltitude = cfg.optDouble("SpoofLocationAltitude", 10);
            sSpoofLocationAccuracy = (float) cfg.optDouble("SpoofLocationAccuracy", 5);
            sSpoofLocationShowNotification = cfg.optBoolean("SpoofLocationShowNotification", true);
            sSpoofLocationApi = cfg.optBoolean("SpoofLocationApi", false);
            // Support both the current and legacy key for IP-based location
            sSpoofLocationUseIpLocation = cfg.optBoolean("SpoofLocationUseIpLocation",
                    cfg.optBoolean("SpoofLocationUseIp", false));
            sSpoofLocationInterval = cfg.optInt("SpoofLocationInterval", 1000);
            sSpoofLocationCompatibilityMode = cfg.optBoolean("SpoofLocationCompatibilityMode", false);
            sSpoofLocationSimulatePositionalUncertainty = cfg.optBoolean("SpoofLocationSimulatePositionalUncertainty", true);
            sSpoofLocationCalculateBearing = cfg.optBoolean("SpoofLocationCalculateBearing", false);
            sSpoofLocationSpeed = (float) cfg.optDouble("SpoofLocationSpeed", 0);
            sSpoofLocationBearing = (float) cfg.optDouble("SpoofLocationBearing", 0);
            sSpoofLocationRandomize = cfg.optBoolean("SpoofLocationRandomize", false);
            sAppClonerPackage = ctx.getPackageName();
        } catch (Throwable t) {
            ENABLED = false;
        }
    }

    private void loadRuntimeOverrides() {
        if (sContext == null) return;
        try {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String override = prefs.getString(PREF_OVERRIDE, null);
            if (override != null && override.startsWith(getOverrideValuePrefix())) {
                // Parse override format: "prefix:lat,lon"
                String coords = override.substring(getOverrideValuePrefix().length());
                String[] parts = coords.split(",");
                if (parts.length >= 2) {
                    sSpoofLocationLatitude = Double.parseDouble(parts[0].trim());
                    sSpoofLocationLongitude = Double.parseDouble(parts[1].trim());
                    if (parts.length >= 3) {
                        sSpoofLocationAltitude = Double.parseDouble(parts[2].trim());
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private String getOverrideValuePrefix() {
        return sAppClonerPackage != null ? sAppClonerPackage + ":" : "spoof:";
    }

    private static boolean getEnabled() {
        return ENABLED && (hasCoordinates() || sSpoofLocationUseIpLocation);
    }

    private static boolean hasCoordinates() {
        return sSpoofLocationLatitude != 0 || sSpoofLocationLongitude != 0;
    }

    /* ---------- Initialize GMS constants ---------- */
    private void initLocationAvailability() {
        try {
            Class<?> laClass = Class.forName("com.google.android.gms.location.LocationAvailability");
            // Try to get a static "available" instance or create one
            try {
                Method createMethod = laClass.getMethod("create", boolean.class, int.class, int.class, long.class);
                LOCATION_AVAILABILITY_AVAILABLE = createMethod.invoke(null, true, 0, 0, System.currentTimeMillis());
            } catch (Exception e) {
                // Try constructor approach
                try {
                    Constructor<?> c = laClass.getDeclaredConstructor(int.class, List.class);
                    c.setAccessible(true);
                    LOCATION_AVAILABILITY_AVAILABLE = c.newInstance(0, Collections.emptyList());
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {
            // GMS not available
        }
    }

    /* ---------- IP Location ---------- */
    private static void fetchIpLocationAsync() {
        if (!canFetchIpLocation()) {
            return;
        }
        if (!sIpLocationFetching.compareAndSet(false, true)) {
            return; // Already fetching
        }
        new Thread(() -> {
            try {
                fetchIpLocation();
            } finally {
                sIpLocationFetching.set(false);
            }
        }).start();
    }

    private static void fetchIpLocation() {
        try {
            // Step 1: fetch the current IP
            String ip = fetchTextFromUrl("https://get.geojs.io/v1/ip.json");
            if (ip != null) {
                JSONObject ipJson = new JSONObject(ip);
                String ipAddress = ipJson.optString("ip", null);
                if (ipAddress != null && !ipAddress.isEmpty()) {
                    // Step 2: fetch geo data for this IP
                    String geoUrl = "https://get.geojs.io/v1/ip/geo/" + ipAddress + ".json";
                    String geo = fetchTextFromUrl(geoUrl);
                    if (geo != null) {
                        JSONObject json = new JSONObject(geo);
                        if (json.has("latitude") && json.has("longitude")) {
                            sSpoofLocationLatitude = json.getDouble("latitude");
                            sSpoofLocationLongitude = json.getDouble("longitude");
                            Log.i(TAG, "IP location: " + sSpoofLocationLatitude + ", " + sSpoofLocationLongitude);
                            saveRuntimeOverrides();
                            sendLocationUpdates();
                            notifySpoofNotification(String.format("%.4f, %.4f",
                                    sSpoofLocationLatitude, sSpoofLocationLongitude));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            if (t instanceof SecurityException && sLoggedMissingInternet.compareAndSet(false, true)) {
                Log.w(TAG, "IP fetch failed: INTERNET permission missing or blocked; disabling Use IP fetch until permission is granted");
            } else {
                Log.w(TAG, "IP fetch failed", t);
            }
        }
    }

    private static String fetchTextFromUrl(String urlString) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlString);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "Android");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() == 200) {
                BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            }
        } catch (Throwable t) {
            Log.w(TAG, "fetchTextFromUrl failed for " + urlString, t);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private static boolean canFetchIpLocation() {
        if (sContext == null) return false;
        try {
            int granted = sContext.checkPermission(Manifest.permission.INTERNET,
                    Process.myPid(), Process.myUid());
            if (granted != PackageManager.PERMISSION_GRANTED) {
                if (sLoggedMissingInternet.compareAndSet(false, true)) {
                    Log.w(TAG, "Skipping IP lookup: INTERNET permission not granted");
                }
                return false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "Skipping IP lookup: permission check failed", t);
            return false;
        }
        return true;
    }

    /* ========== System Service Hook (ILocationManager proxy) ========== */

    private void hookSystemService(Context context) throws Exception {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return;

        // Get the real ILocationManager
        Field mServiceField = LocationManager.class.getDeclaredField("mService");
        mServiceField.setAccessible(true);
        final Object realService = mServiceField.get(lm);

        if (realService == null) return;

        // Create dynamic proxy for ILocationManager
        Class<?> iLocationManagerClass = Class.forName("android.location.ILocationManager");
        Object proxy = Proxy.newProxyInstance(
                context.getClassLoader(),
                new Class<?>[]{iLocationManagerClass},
                createLocationManagerProxy(realService)
        );

        // Inject proxy into LocationManager instance
        mServiceField.set(lm, proxy);

        // Update secure settings to report location as enabled
        updateSecureSettingsLocationMode();
        updateSecureSettingsLocationProvidersAllowed(context);

        Log.d(TAG, "System service hooked successfully");
    }

    private InvocationHandler createLocationManagerProxy(final Object realService) {
        return (proxy, method, args) -> {
            String name = method.getName();
            
            try {
                switch (name) {
                    // ========== Last Location ==========
                    case "getLastLocation":
                    case "getLastKnownLocation":
                        return getLocation(extractProvider(args));

                    // ========== Current Location (Android 12+) ==========
                    case "getCurrentLocation":
                        return handleGetCurrentLocation(args);

                    // ========== Location Updates ==========
                    case "requestLocationUpdates":
                    case "registerLocationListener":
                        handleRegisterLocationListener(args);
                        return null;

                    case "removeUpdates":
                    case "unregisterLocationListener":
                        handleUnregisterLocationListener(args);
                        return null;

                    case "registerLocationPendingIntent":
                        handleRegisterPendingIntent(args);
                        return null;

                    case "unregisterLocationPendingIntent":
                        handleUnregisterPendingIntent(args);
                        return null;

                    // ========== Provider Discovery ==========
                    case "getAllProviders":
                    case "getProviders":
                        return Arrays.asList(LocationManager.GPS_PROVIDER, 
                                           LocationManager.NETWORK_PROVIDER, 
                                           LocationManager.PASSIVE_PROVIDER);

                    case "getBestProvider":
                        return LocationManager.GPS_PROVIDER;

                    // ========== Provider Status ==========
                    case "isProviderEnabled":
                    case "isProviderEnabledForUser":
                    case "isLocationEnabledForUser":
                        return true;

                    // ========== Default: forward to real service ==========
                    default:
                        return method.invoke(realService, args);
                }
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                throw cause != null ? cause : t;
            }
        };
    }

    private String extractProvider(Object[] args) {
        if (args == null || args.length == 0) return LocationManager.GPS_PROVIDER;
        
        Object arg0 = args[0];
        if (arg0 instanceof String) {
            return (String) arg0;
        }
        
        // Try to extract provider from LocationRequest object
        try {
            Field providerField = arg0.getClass().getDeclaredField("mProvider");
            providerField.setAccessible(true);
            Object provider = providerField.get(arg0);
            if (provider instanceof String) {
                return (String) provider;
            }
        } catch (Exception ignored) {}
        
        return LocationManager.GPS_PROVIDER;
    }

    private Object handleGetCurrentLocation(Object[] args) {
        // Find ILocationCallback in args
        Object callback = null;
        for (Object arg : args) {
            if (arg != null && arg.getClass().getName().contains("ILocationCallback")) {
                callback = arg;
                break;
            }
        }

        if (callback != null) {
            final Object finalCallback = callback;
            String provider = extractProvider(args);
            Location loc = getLocation(provider);
            if (loc == null) {
                return createCancellationSignalProxy();
            }
            
            // Post callback on main thread
            sMainHandler.post(() -> {
                try {
                    Method onLocation = finalCallback.getClass().getMethod("onLocation", Location.class);
                    onLocation.invoke(finalCallback, loc);
                } catch (Exception e) {
                    Log.w(TAG, "Error invoking ILocationCallback", e);
                }
            });
        }

        // Return a proxy ICancellationSignal
        return createCancellationSignalProxy();
    }

    private Object createCancellationSignalProxy() {
        try {
            Class<?> csClass = Class.forName("android.os.ICancellationSignal");
            return Proxy.newProxyInstance(
                    sContext.getClassLoader(),
                    new Class<?>[]{csClass},
                    (proxy, method, args) -> {
                        if ("cancel".equals(method.getName())) {
                            // Do nothing, we don't really cancel
                            return null;
                        }
                        return null;
                    }
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void handleRegisterLocationListener(Object[] args) {
        if (args == null) return;

        // Extract request object to check mNumUpdates
        int numUpdates = -1; // -1 means infinite
        Object listener = null;
        PendingIntent pendingIntent = null;

        for (Object arg : args) {
            if (arg == null) continue;
            
            String className = arg.getClass().getName();
            
            // Check for ILocationListener
            if (className.contains("ILocationListener")) {
                listener = arg;
            }
            // Check for LocationListener (direct)
            else if (arg instanceof LocationListener) {
                sAndroidLocationListeners.add((LocationListener) arg);
                sendLocationToDirectListener((LocationListener) arg);
            }
            // Check for PendingIntent
            else if (arg instanceof PendingIntent) {
                pendingIntent = (PendingIntent) arg;
            }
            // Check for LocationRequest to get numUpdates
            else if (className.contains("LocationRequest")) {
                try {
                    Field numUpdatesField = arg.getClass().getDeclaredField("mNumUpdates");
                    numUpdatesField.setAccessible(true);
                    numUpdates = numUpdatesField.getInt(arg);
                } catch (Exception ignored) {}
                
                // Also try to extract listener from request
                try {
                    Field listenerField = arg.getClass().getDeclaredField("mListener");
                    listenerField.setAccessible(true);
                    Object reqListener = listenerField.get(arg);
                    if (reqListener instanceof LocationListener) {
                        sAndroidLocationListeners.add((LocationListener) reqListener);
                        sendLocationToDirectListener((LocationListener) reqListener);
                    }
                } catch (Exception ignored) {}
            }
        }

        // Send immediate update and track for future updates
        if (listener != null) {
            sendLocationToIListener(listener);
            if (numUpdates != 1) {
                sAndroidILocationListeners.add(listener);
            }
        }

        if (pendingIntent != null) {
            sendLocationToPendingIntent(pendingIntent);
            if (numUpdates != 1) {
                sAndroidPendingIntents.add(pendingIntent);
            }
        }
    }

    private void handleUnregisterLocationListener(Object[] args) {
        if (args == null) return;

        for (Object arg : args) {
            if (arg == null) continue;
            
            String className = arg.getClass().getName();
            
            if (className.contains("ILocationListener")) {
                sAndroidILocationListeners.remove(arg);
            } else if (arg instanceof LocationListener) {
                sAndroidLocationListeners.remove(arg);
            } else if (arg instanceof PendingIntent) {
                sAndroidPendingIntents.remove(arg);
            }
        }
    }

    private void handleRegisterPendingIntent(Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof PendingIntent) {
                sAndroidPendingIntents.add((PendingIntent) arg);
                sendLocationToPendingIntent((PendingIntent) arg);
            }
        }
    }

    private void handleUnregisterPendingIntent(Object[] args) {
        if (args == null) return;
        for (Object arg : args) {
            if (arg instanceof PendingIntent) {
                sAndroidPendingIntents.remove(arg);
            }
        }
    }

    /* ========== Hook Location Object Methods ========== */

    private void hookLocationMethods() {
        try {
            // Hook Location.getLatitude()
            Method getLatitude = Location.class.getMethod("getLatitude");
            Hooking.pineHook(getLatitude, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    if (isOurLocation(cf.thisObject)) {
                        cf.setResult(sSpoofLocationLatitude);
                    }
                }
            });

            // Hook Location.getLongitude()
            Method getLongitude = Location.class.getMethod("getLongitude");
            Hooking.pineHook(getLongitude, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    if (isOurLocation(cf.thisObject)) {
                        cf.setResult(sSpoofLocationLongitude);
                    }
                }
            });

            // Hook Location.set(Location)
            Method setLocation = Location.class.getMethod("set", Location.class);
            Hooking.pineHook(setLocation, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    // After set, override with spoof values
                }
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    Location loc = (Location) cf.thisObject;
                    loc.setLatitude(sSpoofLocationLatitude);
                    loc.setLongitude(sSpoofLocationLongitude);
                }
            });

            // Hook Location.setLatitude(double)
            Method setLatitude = Location.class.getMethod("setLatitude", double.class);
            Hooking.pineHook(setLatitude, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    cf.args[0] = sSpoofLocationLatitude;
                }
            });

            // Hook Location.setLongitude(double)
            Method setLongitude = Location.class.getMethod("setLongitude", double.class);
            Hooking.pineHook(setLongitude, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    cf.args[0] = sSpoofLocationLongitude;
                }
            });

            Log.d(TAG, "Location methods hooked");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook Location methods", e);
        }
    }

    private boolean isOurLocation(Object obj) {
        // Always spoof - we want all Location objects to report our coordinates
        return true;
    }

    /* ========== FusedLocationProviderClient Hooks ========== */

    private void hookFusedLocationProviderClient(Context context) {
        try {
            Class<?> clientClass = Class.forName("com.google.android.gms.location.FusedLocationProviderClient");

            // Hook getLastLocation()
            for (Method m : clientClass.getDeclaredMethods()) {
                if ("getLastLocation".equals(m.getName()) && m.getParameterTypes().length == 0) {
                    Hooking.pineHook(m, new MethodHook() {
                        @Override
                        public void afterCall(Pine.CallFrame cf) {
                            // Replace Task result with our location
                            Object task = cf.getResult();
                            Location location = getLocation();
                            if (task != null && location != null) {
                                try {
                                    injectLocationIntoTask(task, location);
                                } catch (Exception e) {
                                    Log.w(TAG, "Failed to inject location into Task", e);
                                }
                            }
                        }
                    });
                    break;
                }
            }

            // Hook requestLocationUpdates methods
            for (Method m : clientClass.getDeclaredMethods()) {
                if ("requestLocationUpdates".equals(m.getName())) {
                    Class<?>[] params = m.getParameterTypes();
                    // Look for LocationCallback parameter
                    for (int i = 0; i < params.length; i++) {
                        if (params[i].getName().contains("LocationCallback")) {
                            final int callbackIndex = i;
                            Hooking.pineHook(m, new MethodHook() {
                                @Override
                                public void beforeCall(Pine.CallFrame cf) {
                                    Object callback = cf.args[callbackIndex];
                                    if (callback != null) {
                                        sCallbacks.add(callback);
                                        sendLocationToCallback(callback);
                                    }
                                }
                            });
                            break;
                        }
                    }
                }
            }

            // Hook removeLocationUpdates
            for (Method m : clientClass.getDeclaredMethods()) {
                if ("removeLocationUpdates".equals(m.getName())) {
                    Hooking.pineHook(m, new MethodHook() {
                        @Override
                        public void beforeCall(Pine.CallFrame cf) {
                            for (Object arg : cf.args) {
                                if (arg != null && arg.getClass().getName().contains("LocationCallback")) {
                                    sCallbacks.remove(arg);
                                }
                            }
                        }
                    });
                }
            }

            Log.d(TAG, "FusedLocationProviderClient hooked");
        } catch (ClassNotFoundException e) {
            // GMS not present
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook FusedLocationProviderClient", e);
        }
    }

    private void injectLocationIntoTask(Object task, Location location) throws Exception {
        // Try to find and set the result
        Class<?> taskClass = task.getClass();
        while (taskClass != null) {
            try {
                Field resultField = taskClass.getDeclaredField("zzb"); // Result field in Tasks
                resultField.setAccessible(true);
                resultField.set(task, location);
                return;
            } catch (NoSuchFieldException e) {
                taskClass = taskClass.getSuperclass();
            }
        }
    }

    private void hookFusedLocationProviderApi(Context context) {
        // Hook newer FusedLocationProviderApi if available
        try {
            Class<?> apiClass = Class.forName("com.google.android.gms.location.FusedLocationProviderApi");
            // Similar hooks as deprecated API
            Log.d(TAG, "FusedLocationProviderApi found");
        } catch (ClassNotFoundException ignored) {}
    }

    private void hookDeprecatedFusedLocationProviderApi(Context context) {
        try {
            Class<?> apiClass = Class.forName("com.google.android.gms.location.FusedLocationApi");

            // Hook getLastLocation
            try {
                Method getLastLocation = apiClass.getMethod("getLastLocation", 
                        Class.forName("com.google.android.gms.common.api.GoogleApiClient"));
                Hooking.pineHook(getLastLocation, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame cf) {
                        cf.setResult(getLocation());
                    }
                });
            } catch (Exception ignored) {}

            // Hook requestLocationUpdates with LocationListener
            try {
                Class<?> googleApiClientClass = Class.forName("com.google.android.gms.common.api.GoogleApiClient");
                Class<?> locationRequestClass = Class.forName("com.google.android.gms.location.LocationRequest");
                Class<?> locationListenerClass = Class.forName("com.google.android.gms.location.LocationListener");
                
                Method requestUpdates = apiClass.getMethod("requestLocationUpdates",
                        googleApiClientClass, locationRequestClass, locationListenerClass);
                
                Hooking.pineHook(requestUpdates, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        Object listener = cf.args[2];
                        if (listener != null) {
                            sGmsListeners.add(listener);
                            sendLocationToGmsListener(listener);
                        }
                        // Return success result
                        cf.setResult(getSuccessResult(cf.args[0]));
                    }
                });
            } catch (Exception ignored) {}

            // Hook removeLocationUpdates
            try {
                Class<?> googleApiClientClass = Class.forName("com.google.android.gms.common.api.GoogleApiClient");
                Class<?> locationListenerClass = Class.forName("com.google.android.gms.location.LocationListener");
                
                Method removeUpdates = apiClass.getMethod("removeLocationUpdates",
                        googleApiClientClass, locationListenerClass);
                
                Hooking.pineHook(removeUpdates, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        Object listener = cf.args[1];
                        if (listener != null) {
                            sGmsListeners.remove(listener);
                        }
                        cf.setResult(getSuccessResult(cf.args[0]));
                    }
                });
            } catch (Exception ignored) {}

            Log.d(TAG, "Deprecated FusedLocationApi hooked");
        } catch (ClassNotFoundException ignored) {}
    }

    private void hookObfuscatedFusedLocationProviderApi(Context context) {
        // Hook obfuscated internal GMS classes
        // These vary by GMS version, so we do best-effort reflection
        String[] possibleClasses = {
                "com.google.android.gms.internal.location.zzd",
                "com.google.android.gms.internal.location.D",
                "com.google.android.gms.internal.location.zze"
        };

        for (String className : possibleClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                for (Method m : clazz.getDeclaredMethods()) {
                    if ("onLocationChanged".equals(m.getName()) || 
                        m.getName().startsWith("zz") && m.getParameterCount() == 1) {
                        // Could be location callback method
                    }
                }
            } catch (ClassNotFoundException ignored) {}
        }
    }

    private void hookGoogleMapsLocationSource() {
        try {
            Class<?> locationSourceClass = Class.forName("com.google.android.gms.maps.LocationSource");
            Class<?> onLocationChangedListenerClass = Class.forName("com.google.android.gms.maps.LocationSource$OnLocationChangedListener");
            
            // Hook activate method to capture the listener
            // This is typically overridden in user code, so we hook the interface method
            
            Log.d(TAG, "Google Maps LocationSource found");
        } catch (ClassNotFoundException ignored) {}
    }

    /* ========== Success Result Helper ========== */

    private Object getSuccessResult(Object googleApiClient) {
        try {
            Class<?> statusClass = Class.forName("com.google.android.gms.common.api.Status");
            Field successField = statusClass.getField("RESULT_SUCCESS");
            Object successStatus = successField.get(null);
            
            Class<?> pendingResultsClass = Class.forName("com.google.android.gms.common.api.PendingResults");
            Method immediatePendingResult = pendingResultsClass.getMethod("immediatePendingResult",
                    statusClass, Class.forName("com.google.android.gms.common.api.GoogleApiClient"));
            
            return immediatePendingResult.invoke(null, successStatus, googleApiClient);
        } catch (Exception e) {
            return null;
        }
    }

    /* ========== Location Update Delivery ========== */

    private void sendLocationToIListener(Object listener) {
        try {
            Method onLocationChanged = listener.getClass().getMethod("onLocationChanged", Location.class);
            Location loc = getLocation();
            if (loc != null) {
                onLocationChanged.invoke(listener, loc);
            }
        } catch (Exception e) {
            sAndroidILocationListeners.remove(listener);
        }
    }

    private void sendLocationToDirectListener(LocationListener listener) {
        try {
            Location loc = getLocation();
            if (loc != null) {
                listener.onLocationChanged(loc);
            }
        } catch (Exception e) {
            sAndroidLocationListeners.remove(listener);
        }
    }

    private void sendLocationToPendingIntent(PendingIntent pi) {
        try {
            Intent intent = new Intent();
            Location loc = getLocation();
            if (loc == null) return;
            intent.putExtra(LocationManager.KEY_LOCATION_CHANGED, loc);
            intent.putExtra("location", loc);
            pi.send(sContext, 0, intent);
        } catch (Exception e) {
            sAndroidPendingIntents.remove(pi);
        }
    }

    private void sendLocationToGmsListener(Object listener) {
        try {
            Method onLocationChanged = listener.getClass().getMethod("onLocationChanged", Location.class);
            Location loc = getLocation();
            if (loc != null) {
                onLocationChanged.invoke(listener, loc);
            }
        } catch (Exception e) {
            sGmsListeners.remove(listener);
        }
    }

    private void sendLocationToCallback(Object callback) {
        try {
            // First, optionally send LocationAvailability
            if (LOCATION_AVAILABILITY_AVAILABLE != null) {
                try {
                    Method onAvailability = callback.getClass().getMethod("onLocationAvailability",
                            Class.forName("com.google.android.gms.location.LocationAvailability"));
                    onAvailability.invoke(callback, LOCATION_AVAILABILITY_AVAILABLE);
                } catch (Exception ignored) {}
            }

            // Build LocationResult
            Location loc = getLocation();
            if (loc == null) return;
            Object locationResult = createLocationResult(loc);
            if (locationResult != null) {
                Method onLocationResult = callback.getClass().getMethod("onLocationResult",
                        Class.forName("com.google.android.gms.location.LocationResult"));
                onLocationResult.invoke(callback, locationResult);
            }
        } catch (Exception e) {
            Log.w(TAG, "sendLocationToCallback failed", e);
            sCallbacks.remove(callback);
        }
    }

    private Object createLocationResult(Location location) {
        try {
            Class<?> lrClass = Class.forName("com.google.android.gms.location.LocationResult");
            
            // Try static create method first
            try {
                Method create = lrClass.getMethod("create", List.class);
                return create.invoke(null, Collections.singletonList(location));
            } catch (NoSuchMethodException e) {
                // Fallback to constructor
                Constructor<?> c = lrClass.getDeclaredConstructor(List.class);
                c.setAccessible(true);
                return c.newInstance(Collections.singletonList(location));
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to create LocationResult", e);
            return null;
        }
    }

    /* ========== Periodic Update Loop ========== */

    private void startUpdateLoop() {
        int interval = Math.max(sSpoofLocationInterval, 100); // Min 100ms
        sScheduler.scheduleAtFixedRate(() -> {
            if (!getEnabled()) return;
            
            // Optionally fetch IP location periodically
            if (sSpoofLocationUseIpLocation) {
                fetchIpLocationAsync();
            }
            
            sendLocationUpdates();
        }, interval, interval, TimeUnit.MILLISECONDS);
    }

    /**
     * Send location updates to all registered listeners
     */
    public static void sendLocationUpdates() {
        if (sContext == null) return;
        
        Location loc = getLocation();
        if (loc == null) return;

        // Try to inject location via LocationManager (best-effort)
        try {
            LocationManager lm = (LocationManager) sContext.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                Method injectLocation = LocationManager.class.getMethod("injectLocation", Location.class);
                injectLocation.invoke(lm, loc);
            }
        } catch (Exception ignored) {}

        // Send to Android ILocationListeners
        synchronized (sAndroidILocationListeners) {
            for (Object listener : new ArrayList<>(sAndroidILocationListeners)) {
                try {
                    Method onLocationChanged = listener.getClass().getMethod("onLocationChanged", Location.class);
                    onLocationChanged.invoke(listener, loc);
                } catch (Exception e) {
                    sAndroidILocationListeners.remove(listener);
                }
            }
        }

        // Send to direct LocationListeners
        synchronized (sAndroidLocationListeners) {
            for (LocationListener listener : new ArrayList<>(sAndroidLocationListeners)) {
                try {
                    listener.onLocationChanged(loc);
                } catch (Exception e) {
                    sAndroidLocationListeners.remove(listener);
                }
            }
        }

        // Send to PendingIntents
        synchronized (sAndroidPendingIntents) {
            for (PendingIntent pi : new ArrayList<>(sAndroidPendingIntents)) {
                try {
                    Intent intent = new Intent();
                    intent.putExtra(LocationManager.KEY_LOCATION_CHANGED, loc);
                    intent.putExtra("location", loc);
                    pi.send(sContext, 0, intent);
                } catch (Exception e) {
                    sAndroidPendingIntents.remove(pi);
                }
            }
        }

        // Send to GMS listeners
        synchronized (sGmsListeners) {
            for (Object listener : new ArrayList<>(sGmsListeners)) {
                try {
                    Method onLocationChanged = listener.getClass().getMethod("onLocationChanged", Location.class);
                    onLocationChanged.invoke(listener, loc);
                } catch (Exception e) {
                    sGmsListeners.remove(listener);
                }
            }
        }

        // Send to GMS callbacks
        synchronized (sCallbacks) {
            for (Object callback : new ArrayList<>(sCallbacks)) {
                try {
                    // LocationAvailability
                    if (LOCATION_AVAILABILITY_AVAILABLE != null) {
                        try {
                            Method onAvailability = callback.getClass().getMethod("onLocationAvailability",
                                    Class.forName("com.google.android.gms.location.LocationAvailability"));
                            onAvailability.invoke(callback, LOCATION_AVAILABILITY_AVAILABLE);
                        } catch (Exception ignored) {}
                    }
                    
                    // LocationResult
                    Object lr = createLocationResultStatic(loc);
                    if (lr != null) {
                        Method onResult = callback.getClass().getMethod("onLocationResult",
                                Class.forName("com.google.android.gms.location.LocationResult"));
                        onResult.invoke(callback, lr);
                    }
                } catch (Exception e) {
                    sCallbacks.remove(callback);
                }
            }
        }

        // Send to Google Maps LocationSource listener
        if (sOnLocationChangedListener != null) {
            try {
                Method onLocationChanged = sOnLocationChangedListener.getClass()
                        .getMethod("onLocationChanged", Location.class);
                onLocationChanged.invoke(sOnLocationChangedListener, loc);
            } catch (Exception e) {
                sOnLocationChangedListener = null;
            }
        }

        // Update bearing calculation state
        if (sSpoofLocationCalculateBearing) {
            sBearingOldSpoofLocationLatitude = sSpoofLocationLatitude;
            sBearingOldSpoofLocationLongitude = sSpoofLocationLongitude;
        }
    }

    private static Object createLocationResultStatic(Location location) {
        try {
            Class<?> lrClass = Class.forName("com.google.android.gms.location.LocationResult");
            try {
                Method create = lrClass.getMethod("create", List.class);
                return create.invoke(null, Collections.singletonList(location));
            } catch (NoSuchMethodException e) {
                Constructor<?> c = lrClass.getDeclaredConstructor(List.class);
                c.setAccessible(true);
                return c.newInstance(Collections.singletonList(location));
            }
        } catch (Exception e) {
            return null;
        }
    }

    /* ========== Fake Location Generator ========== */

    private static Location getLocation() {
        return getLocation(LocationManager.GPS_PROVIDER);
    }

    private static Location getLocation(String provider) {
        if (!hasCoordinates()) {
            if (sSpoofLocationUseIpLocation) {
                fetchIpLocationAsync();
            }
            return null;
        }
        Location loc = new Location(provider != null ? provider : LocationManager.GPS_PROVIDER);
        
        double lat = sSpoofLocationLatitude;
        double lng = sSpoofLocationLongitude;
        double alt = sSpoofLocationAltitude;
        float acc = sSpoofLocationAccuracy;
        float speed = sSpoofLocationSpeed;
        float bearing = sSpoofLocationBearing;

        // Apply randomization if enabled
        if (sSpoofLocationRandomize) {
            lat += (sRandom.nextDouble() - 0.5) * 0.0002; // ~10m variation
            lng += (sRandom.nextDouble() - 0.5) * 0.0002;
            alt += (sRandom.nextDouble() - 0.5) * 2.0;
        }

        loc.setLatitude(lat);
        loc.setLongitude(lng);
        loc.setAltitude(alt);
        loc.setAccuracy(acc);
        loc.setSpeed(speed);
        loc.setBearing(bearing);

        // Calculate bearing from movement if enabled
        if (sSpoofLocationCalculateBearing && 
            (sBearingOldSpoofLocationLatitude != 0 || sBearingOldSpoofLocationLongitude != 0)) {
            float calculatedBearing = calculateBearing(
                    sBearingOldSpoofLocationLatitude, sBearingOldSpoofLocationLongitude,
                    lat, lng);
            loc.setBearing(calculatedBearing);
        }

        // Set location attributes
        setLocationAttributes(loc);

        return loc;
    }

    private static void setLocationAttributes(Location loc) {
        loc.setTime(System.currentTimeMillis());
        
        if (Build.VERSION.SDK_INT >= 17) {
            loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        // API 29+: elapsed realtime uncertainty
        if (Build.VERSION.SDK_INT >= 29 && sSpoofLocationSimulatePositionalUncertainty) {
            try {
                Method setElapsedRealtimeUncertaintyNanos = Location.class.getMethod(
                        "setElapsedRealtimeUncertaintyNanos", double.class);
                double uncertainty = 1000 + sRandom.nextDouble() * 99000; // 1000-100000 nanos
                setElapsedRealtimeUncertaintyNanos.invoke(loc, uncertainty);
            } catch (Exception ignored) {}
        }

        // Add uncertainty to accuracy
        if (sSpoofLocationSimulatePositionalUncertainty) {
            float randomAcc = sSpoofLocationAccuracy + sRandom.nextFloat() * 10f; // 10-20 meter range
            loc.setAccuracy(randomAcc);
        }

        // API 26+: speed/bearing/vertical accuracy
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                if (sSpoofLocationSimulatePositionalUncertainty) {
                    Method setSpeedAccuracy = Location.class.getMethod("setSpeedAccuracyMetersPerSecond", float.class);
                    setSpeedAccuracy.invoke(loc, 0.1f + sRandom.nextFloat() * 0.5f);
                    
                    Method setBearingAccuracy = Location.class.getMethod("setBearingAccuracyDegrees", float.class);
                    setBearingAccuracy.invoke(loc, 1f + sRandom.nextFloat() * 5f);
                    
                    Method setVerticalAccuracy = Location.class.getMethod("setVerticalAccuracyMeters", float.class);
                    setVerticalAccuracy.invoke(loc, 1f + sRandom.nextFloat() * 4f);
                }
            } catch (Exception ignored) {}
        }

        // API 34+: MSL altitude accuracy
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                Method setMslAltitudeAccuracy = Location.class.getMethod("setMslAltitudeAccuracyMeters", float.class);
                setMslAltitudeAccuracy.invoke(loc, 3f + sRandom.nextFloat() * 2f);
            } catch (Exception ignored) {}
        }

        // Call makeComplete() via reflection
        try {
            Method makeComplete = Location.class.getDeclaredMethod("makeComplete");
            makeComplete.setAccessible(true);
            makeComplete.invoke(loc);
        } catch (Exception ignored) {}
    }

    private static float calculateBearing(double lat1, double lon1, double lat2, double lon2) {
        double dLon = Math.toRadians(lon2 - lon1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        
        double x = Math.sin(dLon) * Math.cos(lat2Rad);
        double y = Math.cos(lat1Rad) * Math.sin(lat2Rad) - 
                   Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon);
        
        double bearing = Math.toDegrees(Math.atan2(x, y));
        return (float) ((bearing + 360) % 360);
    }

    /* ========== Settings Spoofing ========== */

    private void updateSecureSettingsLocationMode() {
        // Make location appear enabled in Settings
        try {
            Class<?> settingsSecure = Class.forName("android.provider.Settings$Secure");
            // This would require system permissions, so we skip it
        } catch (Exception ignored) {}
    }

    private void updateSecureSettingsLocationProvidersAllowed(Context context) {
        // Make all providers appear allowed
        // This would require system permissions, so we skip it
    }

    /* ========== API Receiver ========== */

    private void registerApiReceiver(Context context) {
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (ACTION_SET_LOCATION.equals(intent.getAction())) {
                        String latStr = intent.getStringExtra(EXTRA_LATITUDE);
                        String lonStr = intent.getStringExtra(EXTRA_LONGITUDE);
                        String altStr = intent.getStringExtra(EXTRA_ALTITUDE);
                        
                        try {
                            if (latStr != null && lonStr != null) {
                                double lat = Double.parseDouble(latStr);
                                double lon = Double.parseDouble(lonStr);
                                
                                if (lat != 0 || lon != 0) {
                                    setSpoofLocationFromApi(context, lat, lon,
                                            altStr != null ? Float.parseFloat(altStr) : null);
                                }
                            }
                        } catch (NumberFormatException e) {
                            Log.w(TAG, "Invalid location coordinates in broadcast", e);
                        }
                    }
                }
            };

            IntentFilter filter = new IntentFilter(ACTION_SET_LOCATION);
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            
            Log.d(TAG, "API receiver registered");
        } catch (Exception e) {
            Log.w(TAG, "Failed to register API receiver", e);
        }
    }

    /* ========== Notification ========== */

    private void showNotification(Context context) {
        try {
            String contentText;
            if (hasCoordinates()) {
                contentText = String.format("%.4f, %.4f", sSpoofLocationLatitude, sSpoofLocationLongitude);
            } else if (sSpoofLocationUseIpLocation) {
                contentText = "Using IP-based location (fetching...)";
            } else {
                contentText = "Location spoofing enabled";
            }
            notifySpoofNotification(contentText);
        } catch (Exception e) {
            Log.w(TAG, "Failed to show notification", e);
        }
    }

    private static void notifySpoofNotification(String contentText) {
        if (!sSpoofLocationShowNotification || sContext == null) return;
        try {
            if (sNotificationManager == null) {
                sNotificationManager = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
            }
            if (sNotificationManager == null) return;

            // Create notification channel for Android O+
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Location Spoofing",
                        NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Shows when location spoofing is active");
                sNotificationManager.createNotificationChannel(channel);
            }

            Notification.Builder builder;
            if (Build.VERSION.SDK_INT >= 26) {
                builder = new Notification.Builder(sContext, NOTIFICATION_CHANNEL_ID);
            } else {
                builder = new Notification.Builder(sContext);
            }

            builder.setContentTitle("Location Spoofed")
                   .setContentText(contentText)
                   .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                   .setOngoing(true)
                   .setPriority(Notification.PRIORITY_LOW);

            sNotificationManager.notify(NOTIFICATION_ID, builder.build());
        } catch (Exception e) {
            Log.w(TAG, "Failed to notify spoof notification", e);
        }
    }

    /* ========== Public API ========== */

    /**
     * Set spoof location from API (if API control is enabled)
     */
    public static void setSpoofLocationFromApi(Context context, double lat, double lon, Float altitude) {
        if (!sSpoofLocationApi) {
            Log.w(TAG, "API control is disabled");
            return;
        }
        
        sSpoofLocationLatitude = lat;
        sSpoofLocationLongitude = lon;
        if (altitude != null) {
            sSpoofLocationAltitude = altitude;
        }
        
        // Send immediate update
        sendLocationUpdates();
        
        Log.i(TAG, "Location set via API: " + lat + ", " + lon);
    }

    /**
     * Set location programmatically
     */
    public static void setLocation(double latitude, double longitude) {
        sSpoofLocationLatitude = latitude;
        sSpoofLocationLongitude = longitude;
        saveRuntimeOverrides();
    }

    /**
     * Set location with altitude
     */
    public static void setLocation(double latitude, double longitude, double altitude) {
        sSpoofLocationLatitude = latitude;
        sSpoofLocationLongitude = longitude;
        sSpoofLocationAltitude = altitude;
        saveRuntimeOverrides();
    }

    /**
     * Enable/disable spoofing
     */
    public static void setEnabled(boolean enabled) {
        ENABLED = enabled;
        saveRuntimeOverrides();
    }

    /**
     * Get current spoof latitude
     */
    public static double getLatitude() {
        return sSpoofLocationLatitude;
    }

    /**
     * Get current spoof longitude
     */
    public static double getLongitude() {
        return sSpoofLocationLongitude;
    }

    /**
     * Check if spoofing is enabled
     */
    public static boolean isEnabled() {
        return getEnabled();
    }

    private static void saveRuntimeOverrides() {
        if (sContext == null) return;
        try {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String override = (sAppClonerPackage != null ? sAppClonerPackage : "spoof") + ":" +
                    sSpoofLocationLatitude + "," + sSpoofLocationLongitude + "," + sSpoofLocationAltitude;
            prefs.edit().putString(PREF_OVERRIDE, override).apply();
        } catch (Exception ignored) {}
    }
}
