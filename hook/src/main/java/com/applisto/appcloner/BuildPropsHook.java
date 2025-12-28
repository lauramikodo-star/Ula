package com.applisto.appcloner;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * Enhanced BuildPropsHook that dynamically changes device build properties.
 * 
 * Features:
 * - Override Build.* and Build.VERSION.* fields
 * - Hook SystemProperties.get() for dynamic property spoofing
 * - Support for device presets (common device profiles)
 * - Random fingerprint generation
 * - Per-app property customization
 * - Runtime property updates via SharedPreferences
 */
public final class BuildPropsHook {

    private static final String TAG = "BuildPropsHook";
    private static final String PREFS_NAME = "BuildPropsHookPrefs";
    
    // Cached configuration
    private static Context sContext;
    private static JSONObject sConfig;
    private static boolean sEnabled = false;
    private static boolean sHookSystemProperties = false;
    private static boolean sRandomizeFingerprint = false;
    private static String sDevicePreset = null;
    
    // Property overrides map for SystemProperties hook
    private static final Map<String, String> sPropertyOverrides = new HashMap<>();
    
    // Original values backup
    private static final Map<String, Object> sOriginalBuildValues = new HashMap<>();
    private static final Map<String, Object> sOriginalVersionValues = new HashMap<>();
    
    // Device presets - common device profiles for spoofing
    private static final Map<String, DeviceProfile> DEVICE_PRESETS = new HashMap<>();
    
    static {
        // Samsung Galaxy S24 Ultra
        DEVICE_PRESETS.put("samsung_s24_ultra", new DeviceProfile(
            "Samsung", "SM-S928B", "Galaxy S24 Ultra", "samsung",
            "s24ultra", "samsung/s24ultrxx/s24ultra:14/UP1A.231005.007/S928BXXU1AWLM:user/release-keys",
            34, "14", "UP1A.231005.007"
        ));
        
        // Google Pixel 8 Pro
        DEVICE_PRESETS.put("pixel_8_pro", new DeviceProfile(
            "Google", "Pixel 8 Pro", "Pixel 8 Pro", "google",
            "husky", "google/husky/husky:14/UD1A.231105.004/11010374:user/release-keys",
            34, "14", "UD1A.231105.004"
        ));
        
        // OnePlus 12
        DEVICE_PRESETS.put("oneplus_12", new DeviceProfile(
            "OnePlus", "CPH2573", "OnePlus 12", "oneplus",
            "aston", "OnePlus/CPH2573/OP5913L1:14/UKQ1.230924.001/T.18d1b7f_17e7_19:user/release-keys",
            34, "14", "UKQ1.230924.001"
        ));
        
        // Xiaomi 14 Pro
        DEVICE_PRESETS.put("xiaomi_14_pro", new DeviceProfile(
            "Xiaomi", "23116PN5BC", "Xiaomi 14 Pro", "xiaomi",
            "shennong", "Xiaomi/shennong/shennong:14/UKQ1.231003.002/V816.0.5.0.UNACNXM:user/release-keys",
            34, "14", "UKQ1.231003.002"
        ));
        
        // Huawei Mate 60 Pro
        DEVICE_PRESETS.put("huawei_mate60_pro", new DeviceProfile(
            "HUAWEI", "ALN-AL00", "HUAWEI Mate 60 Pro", "huawei",
            "ALN", "HUAWEI/ALN-AL00/HWALN:12/HUAWEIALN-AL00/105.0.0.73C00:user/release-keys",
            31, "12", "HUAWEIALN-AL00"
        ));
        
        // Sony Xperia 1 V
        DEVICE_PRESETS.put("sony_xperia_1v", new DeviceProfile(
            "Sony", "XQ-DQ72", "Xperia 1 V", "sony",
            "pdx234", "Sony/XQ-DQ72/XQ-DQ72:14/67.2.A.2.118/067002A002011800301508470:user/release-keys",
            34, "14", "67.2.A.2.118"
        ));
        
        // OPPO Find X7 Ultra
        DEVICE_PRESETS.put("oppo_find_x7_ultra", new DeviceProfile(
            "OPPO", "PHZ110", "OPPO Find X7 Ultra", "oppo",
            "PHZ110", "OPPO/PHZ110/OP5D3BL1:14/UP1A.231005.007/S.17f2e97_1e89_8:user/release-keys",
            34, "14", "UP1A.231005.007"
        ));
        
        // Vivo X100 Pro
        DEVICE_PRESETS.put("vivo_x100_pro", new DeviceProfile(
            "vivo", "V2324A", "vivo X100 Pro", "vivo",
            "PD2324", "vivo/PD2324/PD2324:14/UP1A.231005.007/compiler11211512:user/release-keys",
            34, "14", "UP1A.231005.007"
        ));
    }

    public void init(Context context) {
        Log.i(TAG, "Initializing enhanced Build props overrides...");
        sContext = context.getApplicationContext();
        
        try {
            sConfig = ClonerSettings.get(context).raw();
            
            // Check if build props hook is enabled
            sEnabled = sConfig.optBoolean("build_props_enabled", true);
            if (!sEnabled) {
                Log.i(TAG, "BuildPropsHook is disabled in config");
                return;
            }
            
            // Load configuration options
            sHookSystemProperties = sConfig.optBoolean("build_props_hook_system_properties", true);
            sRandomizeFingerprint = sConfig.optBoolean("build_props_randomize_fingerprint", false);
            sDevicePreset = sConfig.optString("build_props_device_preset", null);
            
            // Backup original values
            backupOriginalValues();
            
            // Apply device preset first if specified
            if (sDevicePreset != null && !sDevicePreset.isEmpty() && DEVICE_PRESETS.containsKey(sDevicePreset)) {
                applyDevicePreset(sDevicePreset);
            }
            
            // Load custom overrides from config (these override preset values)
            Map<String, Object> buildOverrides = new HashMap<>();
            Map<String, Object> versionOverrides = new HashMap<>();
            
            loadInto(buildOverrides, sConfig, "build_");
            loadInto(versionOverrides, sConfig, "version_");
            
            // Apply custom overrides
            if (!buildOverrides.isEmpty()) {
                overrideFields(Build.class, buildOverrides);
            }
            if (!versionOverrides.isEmpty()) {
                overrideFields(Build.VERSION.class, versionOverrides);
            }
            
            // Randomize fingerprint if enabled
            if (sRandomizeFingerprint) {
                randomizeFingerprint();
            }
            
            // Hook SystemProperties for complete spoofing
            if (sHookSystemProperties) {
                hookSystemProperties();
            }
            
            // Load runtime overrides from SharedPreferences
            loadRuntimeOverrides();
            
            Log.i(TAG, "Enhanced Build props overrides applied successfully");
            logCurrentBuildInfo();
            
        } catch (Throwable t) {
            Log.e(TAG, "Failed to override Build props", t);
        }
    }
    
    /**
     * Backup original Build values for potential restoration
     */
    private void backupOriginalValues() {
        try {
            for (Field f : Build.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    sOriginalBuildValues.put(f.getName(), f.get(null));
                }
            }
            for (Field f : Build.VERSION.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    sOriginalVersionValues.put(f.getName(), f.get(null));
                }
            }
            Log.d(TAG, "Backed up original Build values");
        } catch (Exception e) {
            Log.w(TAG, "Failed to backup some Build values", e);
        }
    }
    
    /**
     * Apply a predefined device preset
     * NOTE: SDK_INT is NOT overridden as it causes app crashes and compatibility issues
     */
    private void applyDevicePreset(String presetName) {
        DeviceProfile profile = DEVICE_PRESETS.get(presetName);
        if (profile == null) {
            Log.w(TAG, "Unknown device preset: " + presetName);
            return;
        }
        
        Log.i(TAG, "Applying device preset: " + presetName);
        
        Map<String, Object> buildOverrides = new HashMap<>();
        buildOverrides.put("MANUFACTURER", profile.manufacturer);
        buildOverrides.put("MODEL", profile.model);
        buildOverrides.put("PRODUCT", profile.product);
        buildOverrides.put("BRAND", profile.brand);
        buildOverrides.put("DEVICE", profile.device);
        buildOverrides.put("FINGERPRINT", profile.fingerprint);
        buildOverrides.put("DISPLAY", profile.displayId);
        buildOverrides.put("ID", profile.displayId);
        
        Map<String, Object> versionOverrides = new HashMap<>();
        // NOTE: SDK_INT is intentionally NOT overridden as it causes app crashes
        // Apps often use SDK_INT for runtime feature checks, and spoofing it
        // breaks API compatibility and causes crashes
        // versionOverrides.put("SDK_INT", profile.sdkInt);  // DISABLED
        versionOverrides.put("RELEASE", profile.release);
        
        overrideFields(Build.class, buildOverrides);
        overrideFields(Build.VERSION.class, versionOverrides);
        
        // Also update property overrides for SystemProperties hook
        sPropertyOverrides.put("ro.product.manufacturer", profile.manufacturer);
        sPropertyOverrides.put("ro.product.model", profile.model);
        sPropertyOverrides.put("ro.product.name", profile.product);
        sPropertyOverrides.put("ro.product.brand", profile.brand);
        sPropertyOverrides.put("ro.product.device", profile.device);
        sPropertyOverrides.put("ro.build.fingerprint", profile.fingerprint);
        sPropertyOverrides.put("ro.build.display.id", profile.displayId);
        // NOTE: SDK version property spoofing can also cause issues, kept for compatibility
        // but apps should not rely on this for feature detection
        sPropertyOverrides.put("ro.build.version.sdk", String.valueOf(Build.VERSION.SDK_INT)); // Use real SDK
        sPropertyOverrides.put("ro.build.version.release", profile.release);
    }
    
    /**
     * Generate a randomized fingerprint based on current device info
     */
    private static final Random sRandom = new Random();

    private static void randomizeFingerprint() {
        try {
            String randomSuffix = String.format("%06d", sRandom.nextInt(1000000));

            String currentFingerprint = Build.FINGERPRINT;
            String[] parts = currentFingerprint.split("/");
            
            if (parts.length >= 5) {
                // Modify the build number portion
                parts[parts.length - 1] = parts[parts.length - 1].replace(
                    "release-keys", 
                    "release-keys-" + randomSuffix
                );
                String newFingerprint = String.join("/", parts);
                
                updateBuildProperty("FINGERPRINT", newFingerprint);
                updateSystemProperty("ro.build.fingerprint", newFingerprint);
                Log.i(TAG, "Randomized fingerprint: " + newFingerprint);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to randomize fingerprint", e);
        }
    }
    
    /**
     * Hook SystemProperties.get() for complete property spoofing
     */
    private void hookSystemProperties() {
        try {
            // Load property overrides from config
            if (sConfig.has("system_properties")) {
                JSONObject props = sConfig.optJSONObject("system_properties");
                if (props != null) {
                    for (Iterator<String> it = props.keys(); it.hasNext(); ) {
                        String key = it.next();
                        sPropertyOverrides.put(key, props.optString(key));
                    }
                }
            }
            
            // Also add build properties to the map
            addBuildPropertiesToMap();
            
            // Hook SystemProperties.get(String)
            Class<?> systemPropsClass = Class.forName("android.os.SystemProperties");
            
            Method getMethod = systemPropsClass.getMethod("get", String.class);
            Hooking.pineHook(getMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    String key = (String) callFrame.args[0];
                    if (sPropertyOverrides.containsKey(key)) {
                        String spoofedValue = sPropertyOverrides.get(key);
                        callFrame.setResult(spoofedValue);
                        Log.v(TAG, "Spoofed property: " + key + " = " + spoofedValue);
                    }
                }
            });
            
            // Hook SystemProperties.get(String, String) - with default value
            Method getWithDefaultMethod = systemPropsClass.getMethod("get", String.class, String.class);
            Hooking.pineHook(getWithDefaultMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    String key = (String) callFrame.args[0];
                    if (sPropertyOverrides.containsKey(key)) {
                        String spoofedValue = sPropertyOverrides.get(key);
                        callFrame.setResult(spoofedValue);
                        Log.v(TAG, "Spoofed property (with default): " + key + " = " + spoofedValue);
                    }
                }
            });
            
            Log.i(TAG, "SystemProperties hooks installed. " + sPropertyOverrides.size() + " properties will be spoofed.");
            
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "SystemProperties class not found, skipping hook");
        } catch (Exception e) {
            Log.e(TAG, "Failed to hook SystemProperties", e);
        }
    }
    
    /**
     * Add current Build field values to property overrides map
     */
    private void addBuildPropertiesToMap() {
        try {
            sPropertyOverrides.put("ro.product.manufacturer", Build.MANUFACTURER);
            sPropertyOverrides.put("ro.product.model", Build.MODEL);
            sPropertyOverrides.put("ro.product.name", Build.PRODUCT);
            sPropertyOverrides.put("ro.product.brand", Build.BRAND);
            sPropertyOverrides.put("ro.product.device", Build.DEVICE);
            sPropertyOverrides.put("ro.build.fingerprint", Build.FINGERPRINT);
            sPropertyOverrides.put("ro.build.display.id", Build.DISPLAY);
            sPropertyOverrides.put("ro.build.id", Build.ID);
            sPropertyOverrides.put("ro.build.version.sdk", String.valueOf(Build.VERSION.SDK_INT));
            sPropertyOverrides.put("ro.build.version.release", Build.VERSION.RELEASE);
            sPropertyOverrides.put("ro.build.version.incremental", Build.VERSION.INCREMENTAL);
            sPropertyOverrides.put("ro.build.type", Build.TYPE);
            sPropertyOverrides.put("ro.build.tags", Build.TAGS);
            sPropertyOverrides.put("ro.build.host", Build.HOST);
            sPropertyOverrides.put("ro.build.user", Build.USER);
            sPropertyOverrides.put("ro.hardware", Build.HARDWARE);
            sPropertyOverrides.put("ro.board.platform", Build.BOARD);
            sPropertyOverrides.put("ro.bootloader", Build.BOOTLOADER);
            sPropertyOverrides.put("ro.serialno", Build.SERIAL);
        } catch (Exception e) {
            Log.w(TAG, "Failed to add Build properties to map", e);
        }
    }
    
    /**
     * Load runtime overrides from SharedPreferences (allows dynamic updates)
     */
    private void loadRuntimeOverrides() {
        try {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            Map<String, ?> allPrefs = prefs.getAll();
            
            Map<String, Object> buildOverrides = new HashMap<>();
            Map<String, Object> versionOverrides = new HashMap<>();
            
            for (Map.Entry<String, ?> entry : allPrefs.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                
                if (key.startsWith("build_")) {
                    buildOverrides.put(key.substring(6), value);
                } else if (key.startsWith("version_")) {
                    versionOverrides.put(key.substring(8), value);
                } else if (key.startsWith("prop_")) {
                    sPropertyOverrides.put(key.substring(5), String.valueOf(value));
                }
            }
            
            if (!buildOverrides.isEmpty()) {
                overrideFields(Build.class, buildOverrides);
            }
            if (!versionOverrides.isEmpty()) {
                overrideFields(Build.VERSION.class, versionOverrides);
            }
            
            Log.d(TAG, "Loaded " + allPrefs.size() + " runtime overrides from SharedPreferences");
        } catch (Exception e) {
            Log.w(TAG, "Failed to load runtime overrides", e);
        }
    }
    
    /**
     * Log current Build information for debugging
     */
    private void logCurrentBuildInfo() {
        Log.i(TAG, "Current Build Info:");
        Log.i(TAG, "  MANUFACTURER: " + Build.MANUFACTURER);
        Log.i(TAG, "  MODEL: " + Build.MODEL);
        Log.i(TAG, "  BRAND: " + Build.BRAND);
        Log.i(TAG, "  DEVICE: " + Build.DEVICE);
        Log.i(TAG, "  PRODUCT: " + Build.PRODUCT);
        Log.i(TAG, "  FINGERPRINT: " + Build.FINGERPRINT);
        Log.i(TAG, "  SDK_INT: " + Build.VERSION.SDK_INT);
        Log.i(TAG, "  RELEASE: " + Build.VERSION.RELEASE);
    }

    /* ---------- helpers ---------- */

    /** Copy every key that starts with <prefix> into dest, removing the prefix. */
    private static void loadInto(Map<String, Object> dest,
                                 JSONObject src,
                                 String prefix) throws Exception {
        for (Iterator<String> it = src.keys(); it.hasNext(); ) {
            String key = it.next();
            if (key.startsWith(prefix)) {
                String fieldName = key.substring(prefix.length());
                Object value = src.opt(key);
                
                // Handle type conversions
                if (value instanceof Integer || value instanceof Long) {
                    dest.put(fieldName, ((Number) value).intValue());
                } else if (value instanceof Boolean) {
                    dest.put(fieldName, value);
                } else {
                    dest.put(fieldName, String.valueOf(value));
                }
            }
        }
    }

    /** Reflectively overwrite static final fields. */
    private static void overrideFields(Class<?> clazz, Map<String, Object> overrides) {
        for (Map.Entry<String, Object> e : overrides.entrySet()) {
            try {
                Field f = clazz.getDeclaredField(e.getKey());
                f.setAccessible(true);
                
                // Remove final modifier if present
                Field modifiersField = null;
                try {
                    modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(f, f.getModifiers() & ~Modifier.FINAL);
                } catch (NoSuchFieldException ignored) {
                    // On newer Android versions, modifiers field may not exist
                }

                Object old = f.get(null);
                Object newValue = e.getValue();
                
                // Type conversion if needed
                Class<?> fieldType = f.getType();
                if (fieldType == int.class && newValue instanceof String) {
                    newValue = Integer.parseInt((String) newValue);
                } else if (fieldType == long.class && newValue instanceof String) {
                    newValue = Long.parseLong((String) newValue);
                } else if (fieldType == boolean.class && newValue instanceof String) {
                    newValue = Boolean.parseBoolean((String) newValue);
                }
                
                f.set(null, newValue);

                Log.d(TAG, clazz.getSimpleName() + '.' + e.getKey() +
                        " : " + old + " -> " + newValue);
            } catch (NoSuchFieldException ignored) {
                Log.w(TAG, "Field " + e.getKey() + " not found in " + clazz.getSimpleName());
            } catch (Throwable t) {
                Log.e(TAG, "Cannot override " + clazz.getSimpleName() + '.' + e.getKey(), t);
            }
        }
    }
    
    /* ---------- Public API for runtime updates ---------- */
    
    /**
     * Dynamically update a Build property at runtime
     * @param fieldName The Build field name (e.g., "MODEL", "MANUFACTURER")
     * @param value The new value
     */
    public static void updateBuildProperty(String fieldName, Object value) {
        Map<String, Object> override = new HashMap<>();
        override.put(fieldName, value);
        overrideFields(Build.class, override);
        
        // Also save to SharedPreferences for persistence
        if (sContext != null) {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString("build_" + fieldName, String.valueOf(value)).apply();
        }
    }
    
    /**
     * Dynamically update a Build.VERSION property at runtime
     * @param fieldName The Build.VERSION field name (e.g., "RELEASE", "SDK_INT")
     * @param value The new value
     */
    public static void updateVersionProperty(String fieldName, Object value) {
        Map<String, Object> override = new HashMap<>();
        override.put(fieldName, value);
        overrideFields(Build.VERSION.class, override);
        
        // Also save to SharedPreferences for persistence
        if (sContext != null) {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString("version_" + fieldName, String.valueOf(value)).apply();
        }
    }
    
    /**
     * Dynamically update a system property at runtime
     * @param propName The property name (e.g., "ro.product.model")
     * @param value The new value
     */
    public static void updateSystemProperty(String propName, String value) {
        sPropertyOverrides.put(propName, value);
        
        // Also save to SharedPreferences for persistence
        if (sContext != null) {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString("prop_" + propName, value).apply();
        }
    }
    
    /**
     * Apply a device preset at runtime
     * @param presetName The preset name (e.g., "samsung_s24_ultra", "pixel_8_pro")
     */
    public static void applyPreset(String presetName) {
        if (DEVICE_PRESETS.containsKey(presetName)) {
            DeviceProfile profile = DEVICE_PRESETS.get(presetName);
            
            updateBuildProperty("MANUFACTURER", profile.manufacturer);
            updateBuildProperty("MODEL", profile.model);
            updateBuildProperty("PRODUCT", profile.product);
            updateBuildProperty("BRAND", profile.brand);
            updateBuildProperty("DEVICE", profile.device);
            updateBuildProperty("FINGERPRINT", profile.fingerprint);
            updateBuildProperty("DISPLAY", profile.displayId);
            
            // NOTE: SDK_INT is NOT updated as it causes app crashes
            // updateVersionProperty("SDK_INT", profile.sdkInt);  // DISABLED
            updateVersionProperty("RELEASE", profile.release);
            
            Log.i(TAG, "Applied device preset: " + presetName);
        } else {
            Log.w(TAG, "Unknown device preset: " + presetName);
        }
    }
    
    /**
     * Get list of available device presets
     * @return Array of preset names
     */
    public static String[] getAvailablePresets() {
        return DEVICE_PRESETS.keySet().toArray(new String[0]);
    }
    
    /**
     * Get list of available device presets with display names.
     * @return Array of [preset_key, display_name] pairs
     */
    public static String[][] getPresetsWithNames() {
        String[][] result = new String[DEVICE_PRESETS.size() + 1][2];
        result[0] = new String[]{"", "-- None (Keep Original) --"};
        
        int i = 1;
        for (Map.Entry<String, DeviceProfile> entry : DEVICE_PRESETS.entrySet()) {
            DeviceProfile profile = entry.getValue();
            result[i] = new String[]{
                entry.getKey(),
                profile.manufacturer + " " + profile.product
            };
            i++;
        }
        return result;
    }
    
    /**
     * Get display name for a preset key.
     */
    public static String getPresetDisplayName(String presetKey) {
        if (presetKey == null || presetKey.isEmpty()) {
            return "-- None (Keep Original) --";
        }
        DeviceProfile profile = DEVICE_PRESETS.get(presetKey);
        if (profile != null) {
            return profile.manufacturer + " " + profile.product;
        }
        return presetKey; // Fallback to key
    }
    
    /**
     * Check if a preset exists.
     */
    public static boolean hasPreset(String presetKey) {
        return presetKey != null && DEVICE_PRESETS.containsKey(presetKey);
    }
    
    /**
     * Restore original Build values
     */
    public static void restoreOriginalValues() {
        overrideFields(Build.class, sOriginalBuildValues);
        overrideFields(Build.VERSION.class, sOriginalVersionValues);
        sPropertyOverrides.clear();
        
        // Clear SharedPreferences
        if (sContext != null) {
            SharedPreferences prefs = sContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().clear().apply();
        }
        
        Log.i(TAG, "Restored original Build values");
    }
    
    /**
     * Get current value of a Build property
     */
    public static Object getBuildProperty(String fieldName) {
        try {
            Field f = Build.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Get current value of a Build.VERSION property
     */
    public static Object getVersionProperty(String fieldName) {
        try {
            Field f = Build.VERSION.class.getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            return null;
        }
    }
    
    /* ---------- Device Profile class ---------- */
    
    private static class DeviceProfile {
        final String manufacturer;
        final String model;
        final String product;
        final String brand;
        final String device;
        final String fingerprint;
        final int sdkInt;
        final String release;
        final String displayId;
        
        DeviceProfile(String manufacturer, String model, String product, String brand,
                      String device, String fingerprint, int sdkInt, String release, String displayId) {
            this.manufacturer = manufacturer;
            this.model = model;
            this.product = product;
            this.brand = brand;
            this.device = device;
            this.fingerprint = fingerprint;
            this.sdkInt = sdkInt;
            this.release = release;
            this.displayId = displayId;
        }
    }

    /**
     * Pick a random device preset key from the available list.
     */
    public static String getRandomDevicePreset() {
        if (DEVICE_PRESETS.isEmpty()) {
            return null;
        }

        ArrayList<String> keys = new ArrayList<>(DEVICE_PRESETS.keySet());
        return keys.get(sRandom.nextInt(keys.size()));
    }

    /**
     * Randomize build properties at runtime by selecting a preset and refreshing the fingerprint.
     */
    public static void randomizeBuildPropsRuntime() {
        try {
            String preset = getRandomDevicePreset();
            if (preset != null) {
                applyPreset(preset);
            }

            randomizeFingerprint();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to randomize build props at runtime", t);
        }
    }
}
