package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;

public final class ClonerSettings {
    private static final String ASSET_FILE   = "cloner.json";
    private static final String RUNTIME_FILE = "cloner.json"; // inside /data/data/<pkg>/files/
    private static ClonerSettings INSTANCE;

    private final JSONObject cfg;
    private final Context mContext;

    private ClonerSettings(Context c) {
        mContext = c.getApplicationContext();
        try {
            JSONObject tmpCfg = new JSONObject();

            // 1) Load from assets (base config)
            try (InputStream in = mContext.getAssets().open(ASSET_FILE)) {
                byte[] buf = new byte[in.available()];
                int read = in.read(buf);
                if (read > 0) {
                    tmpCfg = new JSONObject(new String(buf, 0, read));
                }
                Log.i("ClonerSettings", "Loaded asset JSON");
            } catch (FileNotFoundException e) {
                Log.w("ClonerSettings", "cloner.json not found in assets; using defaults");
            }

            // 2) Merge runtime override (for identity regeneration or root/adb push)
            File runtime = new File(mContext.getFilesDir(), RUNTIME_FILE);
            if (runtime.exists()) {
                try (InputStream in = mContext.openFileInput(RUNTIME_FILE)) {
                    byte[] buf = new byte[(int) runtime.length()];
                    int read = in.read(buf);
                    if (read > 0) {
                        JSONObject runtimeCfg = new JSONObject(new String(buf, 0, read));
                        // Merge keys from runtime config into base config
                        java.util.Iterator<String> keys = runtimeCfg.keys();
                        while (keys.hasNext()) {
                            String key = keys.next();
                            tmpCfg.put(key, runtimeCfg.opt(key));
                        }
                        Log.i("ClonerSettings", "Merged runtime JSON overrides");
                    }
                } catch (Exception e) {
                    Log.w("ClonerSettings", "Failed to load/merge runtime JSON", e);
                }
            }

            cfg = tmpCfg;
        } catch (Exception e) {
            throw new RuntimeException("Cannot load config", e);
        }
    }

    public static synchronized ClonerSettings get(Context c) {
        if (INSTANCE == null) {
            INSTANCE = new ClonerSettings(c);
        }
        return INSTANCE;
    }

    /* existing helpers */
    public String androidId()        { return cfg.optString("android_id"); }
    public String wifiMac()          { return cfg.optString("wifi_mac");   }
    public String bluetoothMac()     { return cfg.optString("bluetooth_mac"); }
    public boolean identityNotificationsEnabled() {
        return cfg.optBoolean("identity_notifications", false);
    }
    public boolean identityNotificationsClearCache() {
        return cfg.optBoolean("identity_notifications_clear_cache", true);
    }
    public boolean identityNotificationsClearData() {
        return cfg.optBoolean("identity_notifications_clear_data", true);
    }
    public boolean identityNotificationsRestartApp() {
        return cfg.optBoolean("identity_notifications_restart_app", true);
    }
    // Persistent notification (cannot be dismissed, always visible)
    public boolean identityNotificationsPersistent() {
        return cfg.optBoolean("identity_notifications_persistent", false);
    }
    // Randomize build props when regenerating identity
    public boolean identityNotificationsRandomizeBuildProps() {
        return cfg.optBoolean("identity_notifications_randomize_build_props", true);
    }
    public String userAgent()        { return cfg.optString("user_agent"); }
    public boolean userAgentHookEnabled() { return cfg.optBoolean("user_agent_hook_enabled", false); }
    public boolean internalBrowserEnabled() { return cfg.optBoolean("internal_browser_enabled", false); }
    public JSONObject raw()          { return cfg; }

    public boolean socksProxy()      { return cfg.optBoolean("socks_proxy", false); }
    public String socksProxyHost()   { return cfg.optString("socks_proxy_host"); }
    public int    socksProxyPort()   { return cfg.optInt("socks_proxy_port", 1080); }
    public String socksProxyUser()   { return cfg.optString("socks_proxy_user"); }
    public String socksProxyPass()   { return cfg.optString("socks_proxy_pass"); }

    // FaceTec / Signature Spoofing Settings
    public String signatureSpoofSha1() {
        return cfg.optString("signature_spoof_sha1", null);
    }
    public String signatureSpoofSha256() {
        return cfg.optString("signature_spoof_sha256", null);
    }
    public String signatureSpoofMd5() {
        return cfg.optString("signature_spoof_md5", null);
    }

    /* NEW: settings for AccessibleDataDirHook
     *
     * Adjust the JSON keys ("accessible_data_dir_...") if your cloner.json
     * uses different names.
     */

    // Enable making the internal data directory accessible (default: true)
    public boolean accessibleDataDirInternalEnabled() {
        return cfg.optBoolean("accessible_data_dir_internal", true);
    }

    // Enable making the external data directory accessible (default: true)
    public boolean accessibleDataDirExternalEnabled() {
        return cfg.optBoolean("accessible_data_dir_external", true);
    }

    // Access mode: "READ_ONLY" or "READ_WRITE" (default: "READ_ONLY")
    public String accessibleDataDirMode() {
        return cfg.optString("accessible_data_dir_mode", "READ_ONLY");
    }

    // Periodic re-apply enabled? (default: false)
    public boolean accessibleDataDirAdvancedMode() {
        return cfg.optBoolean("accessible_data_dir_advanced_mode", false);
    }

    // Interval in seconds for advanced mode (default: 60)
    public long accessibleDataDirAdvancedInterval() {
        return cfg.optLong("accessible_data_dir_advanced_interval", 60L);
    }

    // Enable restoring bundled app data from assets on startup (default: false)
    public boolean bundleAppData() {
        return cfg.optBoolean("bundle_app_data", false);
    }

    // Original package name of the app being cloned
    public String originalPackageName() {
        return cfg.optString("original_package_name", null);
    }

    // Enable User-Agent workaround (restoring original package name)
    public boolean userAgentWorkaround() {
        return cfg.optBoolean("userAgentWorkaround", false);
    }

    // Enable URI scheme workaround in User-Agent workaround
    public boolean userAgentWorkaroundUriSchemeWorkaround() {
        return cfg.optBoolean("userAgentWorkaroundUriSchemeWorkaround", false);
    }

    // Enable Package Name workaround
    public boolean packageNameWorkaround() {
        return cfg.optBoolean("package_name_workaround", false);
    }

    // Facebook WebView Login Cookies
    public Boolean facebookWebViewLoginCookies() {
        if (cfg.has("facebook_webview_login_cookies")) {
            return cfg.optBoolean("facebook_webview_login_cookies");
        }
        return null;
    }

    /* Fake Calculator Settings */
    
    // Enable fake calculator entrance (default: false)
    public boolean fakeCalculatorEnabled() {
        return cfg.optBoolean("fake_calculator_enabled", false);
    }
    
    // Passcode to access the real app (default: "1234")
    public String fakeCalculatorPasscode() {
        return cfg.optString("fake_calculator_passcode", "1234");
    }

    // Ask once for passcode (default: false)
    public boolean fakeCalculatorAskOnce() {
        return cfg.optBoolean("fake_calculator_ask_once", false);
    }
    
    /* Build Props Hook Settings */
    
    // Enable build props override (default: true)
    public boolean buildPropsEnabled() {
        return cfg.optBoolean("build_props_enabled", true);
    }
    
    // Hook SystemProperties.get() for complete spoofing (default: true)
    public boolean buildPropsHookSystemProperties() {
        return cfg.optBoolean("build_props_hook_system_properties", true);
    }
    
    // Randomize fingerprint on each launch (default: false)
    public boolean buildPropsRandomizeFingerprint() {
        return cfg.optBoolean("build_props_randomize_fingerprint", false);
    }
    
    // Device preset to use (e.g., "samsung_s24_ultra", "pixel_8_pro")
    public String buildPropsDevicePreset() {
        return cfg.optString("build_props_device_preset", null);
    }
    
    /* Fake Camera Settings - organized to match screenshot categories */
    
    // Enable fake camera (default: false)
    public boolean fakeCameraEnabled() {
        return cfg.optBoolean("FakeCamera", false);
    }
    
    // Alternative Mode (default: false)
    public boolean fakeCameraAlternativeMode() {
        return cfg.optBoolean("FakeCameraAlternativeMode", false) ||
               cfg.optBoolean("AlternativeMode", false); // Legacy support
    }
    
    // App Support - hooks for camera-using apps (default: false)
    public boolean fakeCameraAppSupport() {
        return cfg.optBoolean("FakeCameraAppSupport", false);
    }
    
    // Close Stream Workaround (default: false)
    public boolean fakeCameraCloseStreamWorkaround() {
        return cfg.optBoolean("FakeCameraCloseStreamWorkaround", false);
    }
    
    // Fix Orientation (default: false)
    public boolean fakeCameraFixOrientation() {
        return cfg.optBoolean("FakeCameraFixOrientation", false);
    }
    
    // Flip Horizontally (default: false)
    public boolean fakeCameraFlipHorizontally() {
        return cfg.optBoolean("FlipHorizontally", false);
    }
    
    // Open Stream Workaround (default: false)
    public boolean fakeCameraOpenStreamWorkaround() {
        return cfg.optBoolean("FakeCameraOpenStreamWorkaround", false) ||
               cfg.optBoolean("OpenStreamWorkaround", false); // Legacy support
    }
    
    // Randomize the fake image slightly (default: false)
    public boolean fakeCameraRandomizeImage() {
        return cfg.optBoolean("RandomizeImage", false);
    }
    
    // Randomization strength (default: 25)
    public int fakeCameraRandomizeStrength() {
        return cfg.optInt("RandomizeStrength", 25);
    }
    
    // Enable floating menu overlay (default: false)
    public boolean fakeCameraFloatingMenuEnabled() {
        return cfg.optBoolean("FakeCameraFloatingMenu", false);
    }

    // Resize Picture (default: false)
    public boolean fakeCameraResizeImage() {
        return cfg.optBoolean("ResizeImage", false);
    }
    
    // Rotation setting (default: "NO_CHANGE")
    public String fakeCameraRotation() {
        return cfg.optString("FakeCameraRotation", "NO_CHANGE");
    }
    
    // Use Original Image File (default: false)
    public boolean fakeCameraUseOriginalImageFile() {
        return cfg.optBoolean("FakeCameraUseOriginalImageFile", false);
    }
    
    // Legacy settings for backward compatibility
    
    // Fake camera image path (default: "FakeCameraImagePath", "fake_camera.jpg")
    public String fakeCameraImagePath() {
        return cfg.optString("FakeCameraImagePath", "fake_camera.jpg");
    }
    
    // Add EXIF attributes to fake photos (default: true)
    public boolean fakeCameraAddExifAttributes() {
        return cfg.optBoolean("AddExifAttributes", true);
    }
    
    // Add spoofed location to EXIF (default: false)
    public boolean fakeCameraAddSpoofedLocation() {
        return cfg.optBoolean("AddSpoofedLocation", false);
    }

    /* Live Video Hook Settings */
    public boolean liveVideoHookEnabled() {
        return cfg.optBoolean("live_video_hook_enabled", false);
    }

    /* Device Identity Spoofing Settings */
    
    // Serial number spoofing (empty means keep original)
    public String serialNumber() {
        return cfg.optString("serial_number", "");
    }
    
    // IMEI spoofing (empty means keep original)
    public String imei() {
        return cfg.optString("imei", "");
    }
    
    // IMSI spoofing (empty means keep original)
    public String imsi() {
        return cfg.optString("imsi", "");
    }

    /* Dialog Blocker Settings */
    
    // Enable dialog blocking (default: false)
    public boolean dialogBlockerEnabled() {
        return cfg.optBoolean("dialog_blocker_enabled", false);
    }
    
    // Block update dialogs (default: true when blocker enabled)
    public boolean blockUpdateDialogs() {
        return cfg.optBoolean("block_update_dialogs", true);
    }
    
    // Block rating dialogs (default: true when blocker enabled)
    public boolean blockRatingDialogs() {
        return cfg.optBoolean("block_rating_dialogs", true);
    }
    
    // Block ad dialogs (default: true when blocker enabled)
    public boolean blockAdDialogs() {
        return cfg.optBoolean("block_ad_dialogs", true);
    }
    
    // Block subscription/premium dialogs (default: false)
    public boolean blockSubscriptionDialogs() {
        return cfg.optBoolean("block_subscription_dialogs", false);
    }
    
    // Custom keywords to block (comma-separated)
    public String dialogBlockKeywords() {
        return cfg.optString("dialog_block_keywords", "");
    }

    /* Skip Dialogs Settings */
    public java.util.List<String> skipDialogs() {
        java.util.List<String> list = new java.util.ArrayList<>();
        // Can be a string array or comma separated string?
        // Usually JSON array in cloner.json
        org.json.JSONArray arr = cfg.optJSONArray("skip_dialogs");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i));
            }
        }
        return list;
    }

    public java.util.List<String> skipDialogsStacktraces() {
        java.util.List<String> list = new java.util.ArrayList<>();
        org.json.JSONArray arr = cfg.optJSONArray("skip_dialogs_stacktraces");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i));
            }
        }
        return list;
    }

    public boolean monitorStacktraces() {
        return cfg.optBoolean("monitor_stacktraces", false);
    }

    /* Keep Playing Media */
    public boolean keepPlayingMedia() {
        return cfg.optBoolean("keep_playing_media", false);
    }

    public boolean keepPlayingMediaCompatibilityMode() {
        return cfg.optBoolean("keep_playing_media_compatibility_mode", true);
    }

    /* Custom Build Props File */
    public String customBuildPropsString() {
        String val = cfg.optString("custom_build_props", "");
        if (val.isEmpty()) {
            try (InputStream in = mContext.getAssets().open("custom_build_props")) {
                byte[] buf = new byte[in.available()];
                int read = in.read(buf);
                if (read > 0) {
                    val = new String(buf, 0, read);
                }
            } catch (Exception e) {
                // Asset not found or error reading, ignore
            }
        }
        return val;
    }

    /* Mock Network Connection */
    public String mockWifiConnection() { return cfg.optString("mock_wifi_connection", null); }
    public String mockMobileConnection() { return cfg.optString("mock_mobile_connection", null); }
    public String mockEthernetConnection() { return cfg.optString("mock_ethernet_connection", null); }
    
    /* Internal Browser Settings */
    
    // Internal Browser settings are accessed via the helper method defined above

    /* Screenshot Detection Blocker Settings */
    
    // Enable screenshot detection blocking (default: false)
    public boolean screenshotDetectionBlockerEnabled() {
        return cfg.optBoolean("screenshot_detection_blocker", false);
    }

    /* Disable License Validation */
    public boolean disableLicenseValidation() {
        return cfg.optBoolean("disable_license_validation", false);
    }

    /* Local Web Console (default: true) */
    public boolean localWebConsoleEnabled() {
        return cfg.optBoolean("local_web_console_enabled", true);
    }

    /* Host Monitor (default: false) */
    public boolean hostMonitorEnabled() {
        return cfg.optBoolean("host_monitor_enabled", false);
    }

    /* Header Monitor (default: false) */
    public boolean headerMonitorEnabled() {
        return cfg.optBoolean("header_monitor_enabled", false);
    }

    /* Preferences Monitor (default: false) */
    public boolean preferencesMonitorEnabled() {
        return cfg.optBoolean("preferences_monitor_enabled", false);
    }

    public java.util.List<String> hostMonitorFilter() {
        java.util.List<String> list = new java.util.ArrayList<>();
        org.json.JSONArray arr = cfg.optJSONArray("host_monitor_filter");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i));
            }
        }
        return list;
    }

    /* Hide Root */
    public boolean hideRoot() {
        return cfg.optBoolean("hide_root", false);
    }

    /* Hide Emulator */
    public boolean hideEmulator() {
        return cfg.optBoolean("hide_emulator", false);
    }

    /* Disable Networking Without VPN */
    public boolean disableNetworkingWithoutVpn() {
        return cfg.optBoolean("DisableNetworkingWithoutVpn", false);
    }

    /* No Background Services */
    public boolean noBackgroundServices() {
        return cfg.optBoolean("NoBackgroundServices", false);
    }

    /* Location Spoofing Settings */
    
    // Enable location spoofing (default: false)
    public boolean spoofLocationEnabled() {
        return cfg.optBoolean("SpoofLocation", false);
    }
    
    // Spoofed latitude
    public double spoofLocationLatitude() {
        return cfg.optDouble("SpoofLocationLatitude", 0.0);
    }
    
    // Spoofed longitude
    public double spoofLocationLongitude() {
        return cfg.optDouble("SpoofLocationLongitude", 0.0);
    }
    
    // Spoofed altitude
    public double spoofLocationAltitude() {
        return cfg.optDouble("SpoofLocationAltitude", 10.0);
    }
    
    // Location accuracy
    public float spoofLocationAccuracy() {
        return (float) cfg.optDouble("SpoofLocationAccuracy", 5.0);
    }
    
    // Randomize location
    public boolean spoofLocationRandomize() {
        return cfg.optBoolean("SpoofLocationRandomize", false);
    }
    
    // Use IP-based location
    public boolean spoofLocationUseIp() {
        return cfg.optBoolean("SpoofLocationUseIp", false);
    }

    /* Disable Background Networking Settings */
    public boolean disableBackgroundNetworking() {
        return cfg.optBoolean("disable_background_networking", false);
    }
    
    public int disableBackgroundNetworkingDelay() {
        return cfg.optInt("disable_background_networking_delay", 0);
    }
    
    public boolean disableBackgroundNetworkingSilent() {
        return cfg.optBoolean("disable_background_networking_silent", false);
    }

    /* System User Agent Settings */
    public boolean changeSystemUserAgent() {
        return cfg.optBoolean("change_system_user_agent", false);
    }
    
    public String systemUserAgent() {
        return cfg.optString("system_user_agent", "");
    }

    /* Hide CPU Info Settings */
    public boolean hideCpuInfo() {
        return cfg.optBoolean("hide_cpu_info", false);
    }
    
    public String customCpuInfo() {
        return cfg.optString("custom_cpu_info", "");
    }

    /* Hide GPU Info Settings */
    public boolean hideGpuInfo() {
        return cfg.optBoolean("hide_gpu_info", false);
    }
    
    public String gpuVendor() {
        return cfg.optString("gpu_vendor", "ARM");
    }
    
    public String gpuRenderer() {
        return cfg.optString("gpu_renderer", "Mali-G78");
    }
    
    public String gpuVersion() {
        return cfg.optString("gpu_version", "OpenGL ES 3.2 v1.r32p1");
    }

    /* Hide DNS Servers Settings */
    public boolean hideDnsServers() {
        return cfg.optBoolean("hide_dns_servers", false);
    }
    
    public boolean hideDnsServersCompletely() {
        return cfg.optBoolean("hide_dns_servers_completely", true);
    }
    
    public java.util.List<String> spoofedDnsServers() {
        java.util.List<String> list = new java.util.ArrayList<>();
        org.json.JSONArray arr = cfg.optJSONArray("spoofed_dns_servers");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                list.add(arr.optString(i));
            }
        }
        return list;
    }

    /* Hide SIM Operator Settings */
    public boolean hideSimOperator() {
        return cfg.optBoolean("hide_sim_operator", false);
    }
    
    public String spoofedOperatorName() {
        return cfg.optString("spoofed_operator_name", "");
    }
    
    public String spoofedOperatorNumeric() {
        return cfg.optString("spoofed_operator_numeric", "");
    }
    
    public String spoofedSimCountryIso() {
        return cfg.optString("spoofed_sim_country_iso", "");
    }
    
    /**
     * Get a human-readable display name for a settings key.
     * This helps show user-friendly names in UI instead of raw config keys.
     */
    public static String getDisplayName(String key) {
        if (key == null) return "Unknown";
        
        switch (key) {
            // Fake Camera
            case "FakeCamera": return "Fake Camera";
            case "FakeCameraAlternativeMode": return "Alternative Mode";
            case "FakeCameraAppSupport": return "App Support";
            case "FakeCameraCloseStreamWorkaround": return "Close Stream Workaround";
            case "FakeCameraFixOrientation": return "Fix Orientation";
            case "FakeCameraFloatingMenu": return "Floating Menu";
            case "FakeCameraOpenStreamWorkaround": return "Open Stream Workaround";
            case "FakeCameraRotation": return "Rotation";
            case "FlipHorizontally": return "Flip Horizontally";
            case "RandomizeImage": return "Randomize Picture";
            case "RandomizeStrength": return "Randomization Strength";
            case "ResizeImage": return "Resize Picture";
            case "AddExifAttributes": return "Add EXIF Attributes";
            
            // Device Identity
            case "android_id": return "Android ID";
            case "wifi_mac": return "Wi-Fi MAC";
            case "bluetooth_mac": return "Bluetooth MAC";
            case "serial_number": return "Serial Number";
            case "imei": return "IMEI";
            case "imsi": return "IMSI";
            case "user_agent": return "User Agent";
            case "user_agent_hook_enabled": return "Enable User Agent Hook";
            
            // Build Props
            case "build_props_device_preset": return "Device Preset";
            case "build_MANUFACTURER": return "Manufacturer";
            case "build_MODEL": return "Model";
            case "build_BRAND": return "Brand";
            
            // Internal Browser
            case "internal_browser_enabled": return "Internal Browser";
            
            // Screenshot Detection
            case "screenshot_detection_blocker": return "Block Screenshot Detection";
            case "DisableNetworkingWithoutVpn": return "Disable Networking Without VPN";
            case "NoBackgroundServices": return "No Background Services";
            
            // Background Media
            case "background_media": return "Background Media";
            case "background_media_webview": return "WebView Background";
            case "background_media_mediaplayer": return "MediaPlayer Background";
            case "background_media_exoplayer": return "ExoPlayer Background";
            case "background_media_audio_focus": return "Audio Focus";
            
            // Accessible Data Dir
            case "accessible_data_dir_internal": return "Internal Data Access";
            case "accessible_data_dir_external": return "External Data Access";
            case "accessible_data_dir_mode": return "Access Mode";
            case "accessible_data_dir_advanced_mode": return "Advanced Mode";
            case "bundle_app_data": return "Bundle App Data";
            
            default:
                // Convert snake_case or camelCase to readable format
                return formatAsDisplayName(key);
        }
    }
    
    private static String formatAsDisplayName(String key) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        
        for (char c : key.toCharArray()) {
            if (c == '_' || c == '-') {
                sb.append(' ');
                capitalizeNext = true;
            } else if (Character.isUpperCase(c) && sb.length() > 0 && 
                       sb.charAt(sb.length() - 1) != ' ') {
                sb.append(' ');
                sb.append(c);
                capitalizeNext = false;
            } else if (capitalizeNext) {
                sb.append(Character.toUpperCase(c));
                capitalizeNext = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        
        return sb.toString();
    }
}
