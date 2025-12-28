package com.appcloner.replica;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * CloneSettings - Hardcoded default settings class for cloner.json generation.
 * This class contains all the default settings that will be used when creating
 * a new cloner.json configuration file for cloned applications.
 */
public class CloneSettings {
    private static final String TAG = "CloneSettings";
    private static final SecureRandom random = new SecureRandom();

    // ===== Cloning Mode Settings =====
    public static final String CLONING_MODE_REPLACE = "replace_original";
    public static final String CLONING_MODE_GENERATE = "generate_new_package";
    public static final String CLONING_MODE_CUSTOM = "custom_package";
    public static final String DEFAULT_CLONING_MODE = CLONING_MODE_REPLACE;
    
    // ===== App Name and Icon Settings =====
    public static final String DEFAULT_APP_NAME = "";  // Empty means use original
    public static final String DEFAULT_ICON_COLOR = "#FFFFFF";
    public static final float DEFAULT_ICON_HUE = 0f;
    public static final float DEFAULT_ICON_SATURATION = 0f;
    public static final float DEFAULT_ICON_LIGHTNESS = 0f;
    public static final boolean DEFAULT_ICON_AUTO_HUE = false;
    public static final boolean DEFAULT_ICON_INVERT_COLORS = false;
    public static final boolean DEFAULT_ICON_SEPIA = false;
    public static final int DEFAULT_ICON_ROTATION = 0;  // 0, 90, 180, 270
    public static final boolean DEFAULT_ICON_FLIP_HORIZONTAL = false;
    public static final boolean DEFAULT_ICON_FLIP_VERTICAL = false;
    public static final String DEFAULT_ICON_BADGE = "";  // Empty means no badge
    public static final int DEFAULT_ICON_BADGE_POSITION = 0;  // 0=top-right, 1=top-left, 2=bottom-right, 3=bottom-left

    // ===== Device Identity Settings =====
    public static final String VALUE_MODE_NO_CHANGE = "nochange";
    public static final String VALUE_MODE_RANDOM = "random";

    public static final String DEFAULT_ANDROID_ID = VALUE_MODE_NO_CHANGE;
    public static final String DEFAULT_WIFI_MAC = VALUE_MODE_NO_CHANGE;
    public static final String DEFAULT_BLUETOOTH_MAC = VALUE_MODE_NO_CHANGE;
    public static final String DEFAULT_SERIAL_NUMBER = VALUE_MODE_NO_CHANGE;
    public static final String DEFAULT_IMEI = VALUE_MODE_NO_CHANGE;  // Empty means keep original
    public static final String DEFAULT_IMSI = VALUE_MODE_NO_CHANGE;  // Empty means keep original
    public static final boolean DEFAULT_IDENTITY_NOTIFICATIONS = false;
    public static final boolean DEFAULT_IDENTITY_NOTIFICATIONS_CLEAR_CACHE = true;
    public static final boolean DEFAULT_IDENTITY_NOTIFICATIONS_CLEAR_DATA = true;
    public static final boolean DEFAULT_IDENTITY_NOTIFICATIONS_RESTART_APP = true;
    public static final boolean DEFAULT_USER_AGENT_HOOK_ENABLED = false;
    public static final String DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Mobile Safari/537.36";

    // ===== Build Props / Device Spoofing Settings =====
    public static final boolean DEFAULT_BUILD_PROPS_ENABLED = false;
    public static final String DEFAULT_BUILD_PROPS_DEVICE_PRESET = "";  // e.g., "samsung_s24_ultra", "pixel_8_pro"
    public static final boolean DEFAULT_BUILD_PROPS_RANDOMIZE_FINGERPRINT = false;
    public static final String DEFAULT_BUILD_MANUFACTURER = "";
    public static final String DEFAULT_BUILD_MODEL = "";
    public static final String DEFAULT_BUILD_BRAND = "";
    public static final String DEFAULT_BUILD_DEVICE = "";
    public static final String DEFAULT_BUILD_PRODUCT = "";
    public static final String DEFAULT_BUILD_FINGERPRINT = "";

    // ===== Dialog Intercept / Blocker Settings =====
    public static final boolean DEFAULT_DIALOG_BLOCKER_ENABLED = false;
    public static final boolean DEFAULT_BLOCK_UPDATE_DIALOGS = true;
    public static final boolean DEFAULT_BLOCK_RATING_DIALOGS = true;
    public static final boolean DEFAULT_BLOCK_AD_DIALOGS = true;
    public static final boolean DEFAULT_BLOCK_SUBSCRIPTION_DIALOGS = false;
    public static final String DEFAULT_DIALOG_BLOCK_KEYWORDS = "update,rate,review,premium,subscribe,upgrade,install,download";

    // ===== Fake Calculator Settings =====
    public static final boolean DEFAULT_FAKE_CALCULATOR_ENABLED = false;
    public static final String DEFAULT_FAKE_CALCULATOR_PASSCODE = "1234";  // Numbers only, no = required
    public static final boolean DEFAULT_FAKE_CALCULATOR_ASK_ONCE = false;  // Ask for passcode only once per session

    // ===== Fake Camera Settings =====
    // Settings organized to match the screenshot categories
    public static final boolean DEFAULT_FAKE_CAMERA_ENABLED = true;
    public static final boolean DEFAULT_FAKE_CAMERA_ALTERNATIVE_MODE = false;  // Alternative Mode
    public static final boolean DEFAULT_FAKE_CAMERA_APP_SUPPORT = false;       // App Support
    public static final boolean DEFAULT_FAKE_CAMERA_CLOSE_STREAM_WORKAROUND = false; // Close Stream Workaround
    public static final boolean DEFAULT_FAKE_CAMERA_FIX_ORIENTATION = false;   // Fix Orientation
    public static final boolean DEFAULT_FLIP_HORIZONTALLY = false;             // Flip Horizontally
    public static final boolean DEFAULT_FAKE_CAMERA_OPEN_STREAM_WORKAROUND = false; // Open Stream Workaround
    public static final boolean DEFAULT_RANDOMIZE_IMAGE = false;               // Randomize Picture
    public static final int DEFAULT_RANDOMIZE_STRENGTH = 25;                   // Randomize Picture Strength (default 25 as in screenshot)
    public static final boolean DEFAULT_RESIZE_IMAGE = false;                  // Resize Picture
    public static final String DEFAULT_FAKE_CAMERA_ROTATION = "NO_CHANGE";     // Rotation (NO_CHANGE default)
    public static final boolean DEFAULT_FAKE_CAMERA_USE_ORIGINAL_IMAGE_FILE = false; // Use Original Image File
    public static final boolean DEFAULT_FAKE_CAMERA_FLOATING_MENU = false;      // Floating Camera Menu
    // Legacy settings kept for compatibility
    public static final boolean DEFAULT_ROTATE_IMAGE = false;
    public static final int DEFAULT_ROTATION_ANGLE = 0;
    public static final boolean DEFAULT_ADD_EXIF_ATTRIBUTES = true;
    public static final boolean DEFAULT_ALTERNATIVE_MODE = false;
    public static final boolean DEFAULT_OPEN_STREAM_WORKAROUND = false;
    public static final boolean DEFAULT_USE_RANDOM_IMAGE = false;
    public static final boolean DEFAULT_PRESERVE_ASPECT_RATIO = true;
    public static final boolean DEFAULT_CENTER_IMAGE = true;
    public static final boolean DEFAULT_FILL_IMAGE = false;
    public static final boolean DEFAULT_HOOK_LOW_LEVEL_APIS = false;
    public static final boolean DEFAULT_SYSTEM_CAMERA_WORKAROUND = false;
    public static final boolean DEFAULT_FORCED_BACK_CAMERA_ENABLED = true;

    // ===== Display Settings =====
    public static final boolean DEFAULT_ALLOW_SCREENSHOTS = true;

    // ===== Privacy & Security Settings =====
    public static final boolean DEFAULT_DISABLE_LICENSE_VALIDATION = false;
    public static final boolean DEFAULT_HIDE_ROOT = false;
    public static final boolean DEFAULT_HIDE_EMULATOR = false;
    public static final boolean DEFAULT_DISABLE_NETWORKING_WITHOUT_VPN = false;
    public static final boolean DEFAULT_NO_BACKGROUND_SERVICES = false;
    public static final boolean DEFAULT_PACKAGE_NAME_WORKAROUND = false;
    
    // ===== Disable Background Networking Settings =====
    public static final boolean DEFAULT_DISABLE_BACKGROUND_NETWORKING = false;
    public static final int DEFAULT_DISABLE_BACKGROUND_NETWORKING_DELAY = 0;
    public static final boolean DEFAULT_DISABLE_BACKGROUND_NETWORKING_SILENT = false;
    
    // ===== System User Agent Settings =====
    public static final boolean DEFAULT_CHANGE_SYSTEM_USER_AGENT = false;
    public static final String DEFAULT_SYSTEM_USER_AGENT = "";
    
    // ===== Hide CPU/GPU Info Settings =====
    public static final boolean DEFAULT_HIDE_CPU_INFO = false;
    public static final boolean DEFAULT_HIDE_GPU_INFO = false;
    public static final String DEFAULT_GPU_VENDOR = "ARM";
    public static final String DEFAULT_GPU_RENDERER = "Mali-G78";
    public static final String DEFAULT_GPU_VERSION = "OpenGL ES 3.2 v1.r32p1";
    
    // ===== Hide DNS Servers Settings =====
    public static final boolean DEFAULT_HIDE_DNS_SERVERS = false;
    public static final boolean DEFAULT_HIDE_DNS_SERVERS_COMPLETELY = true;
    
    // ===== Hide SIM Operator Settings =====
    public static final boolean DEFAULT_HIDE_SIM_OPERATOR = false;
    public static final String DEFAULT_SPOOFED_OPERATOR_NAME = "";
    public static final String DEFAULT_SPOOFED_OPERATOR_NUMERIC = "";
    public static final String DEFAULT_SPOOFED_SIM_COUNTRY_ISO = "";

    // ===== Background Media Settings =====
    public static final boolean DEFAULT_BACKGROUND_MEDIA = false;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_WEBVIEW = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_MEDIAPLAYER = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_EXOPLAYER = true;
    public static final boolean DEFAULT_BACKGROUND_MEDIA_AUDIO_FOCUS = true;

    // ===== SOCKS Proxy Settings =====
    public static final boolean DEFAULT_SOCKS_PROXY_ENABLED = false;
    public static final String DEFAULT_SOCKS_PROXY_HOST = "";
    public static final int DEFAULT_SOCKS_PROXY_PORT = 1080;
    public static final String DEFAULT_SOCKS_PROXY_USER = "";
    public static final String DEFAULT_SOCKS_PROXY_PASS = "";

    // ===== Location Spoofing Settings =====
    public static final boolean DEFAULT_SPOOF_LOCATION = false;
    public static final double DEFAULT_SPOOF_LOCATION_LATITUDE = 48.8584;
    public static final double DEFAULT_SPOOF_LOCATION_LONGITUDE = 2.2945;
    public static final double DEFAULT_SPOOF_LOCATION_ALTITUDE = 35.0;
    public static final double DEFAULT_SPOOF_LOCATION_ACCURACY = 5.0;
    public static final boolean DEFAULT_SPOOF_LOCATION_SHOW_NOTIFICATION = true;
    public static final boolean DEFAULT_SPOOF_LOCATION_API = false;
    public static final boolean DEFAULT_SPOOF_LOCATION_USE_IP_LOCATION = false;
    public static final int DEFAULT_SPOOF_LOCATION_INTERVAL = 1000; // milliseconds
    public static final boolean DEFAULT_SPOOF_LOCATION_COMPATIBILITY_MODE = false;
    public static final boolean DEFAULT_SPOOF_LOCATION_SIMULATE_POSITIONAL_UNCERTAINTY = true;
    public static final boolean DEFAULT_SPOOF_LOCATION_CALCULATE_BEARING = false;
    public static final double DEFAULT_SPOOF_LOCATION_SPEED = 0.0;
    public static final double DEFAULT_SPOOF_LOCATION_BEARING = 0.0;
    public static final boolean DEFAULT_SPOOF_LOCATION_RANDOMIZE = false;

    // ===== Accessible Data Directory Settings =====
    public static final boolean DEFAULT_ACCESSIBLE_DATA_DIR_INTERNAL_ENABLED = false;
    public static final boolean DEFAULT_ACCESSIBLE_DATA_DIR_EXTERNAL_ENABLED = false;
    public static final String DEFAULT_ACCESSIBLE_DATA_DIR_MODE = "READ_ONLY";
    public static final boolean DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_MODE = false;
    public static final int DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_INTERVAL = 60;
    public static final boolean DEFAULT_BUNDLE_APP_DATA = false;

    // ===== Internal Browser Settings =====
    public static final boolean DEFAULT_INTERNAL_BROWSER_ENABLED = false;

    // ===== Screenshot Detection Blocker Settings =====
    public static final boolean DEFAULT_SCREENSHOT_DETECTION_BLOCKER = false;

    // ===== Skip Dialogs Settings =====
    public static final String DEFAULT_SKIP_DIALOGS = "";
    public static final String DEFAULT_SKIP_DIALOGS_STACKTRACES = "";
    public static final boolean DEFAULT_MONITOR_STACKTRACES = false;

    // ===== Keep Playing Media Settings =====
    public static final boolean DEFAULT_KEEP_PLAYING_MEDIA = false;
    public static final boolean DEFAULT_KEEP_PLAYING_MEDIA_COMPATIBILITY_MODE = true;

    // ===== Custom Build Props =====
    public static final String DEFAULT_CUSTOM_BUILD_PROPS = "";

    // ===== Mock Network Connection =====
    public static final String DEFAULT_MOCK_WIFI_CONNECTION = "";
    public static final String DEFAULT_MOCK_MOBILE_CONNECTION = "";
    public static final String DEFAULT_MOCK_ETHERNET_CONNECTION = "";

    // ===== Monitoring & Console =====
    public static final boolean DEFAULT_LOCAL_WEB_CONSOLE_ENABLED = true;
    public static final boolean DEFAULT_HOST_MONITOR_ENABLED = false;
    public static final boolean DEFAULT_HEADER_MONITOR_ENABLED = false;
    public static final boolean DEFAULT_PREFERENCES_MONITOR_ENABLED = false;

    /**
     * Creates a new JSONObject with all default settings.
     * @return JSONObject containing all default clone settings
     */
    public static JSONObject createDefaultSettings() {
        JSONObject json = new JSONObject();
        try {
            // Cloning Mode
            json.put("cloning_mode", DEFAULT_CLONING_MODE);
            json.put("custom_package_name", "");
            
            // App Name and Icon
            json.put("app_name", DEFAULT_APP_NAME);
            json.put("icon_color", DEFAULT_ICON_COLOR);
            json.put("icon_hue", DEFAULT_ICON_HUE);
            json.put("icon_saturation", DEFAULT_ICON_SATURATION);
            json.put("icon_lightness", DEFAULT_ICON_LIGHTNESS);
            json.put("icon_auto_hue", DEFAULT_ICON_AUTO_HUE);
            json.put("icon_invert_colors", DEFAULT_ICON_INVERT_COLORS);
            json.put("icon_sepia", DEFAULT_ICON_SEPIA);
            json.put("icon_rotation", DEFAULT_ICON_ROTATION);
            json.put("icon_flip_horizontal", DEFAULT_ICON_FLIP_HORIZONTAL);
            json.put("icon_flip_vertical", DEFAULT_ICON_FLIP_VERTICAL);
            json.put("icon_badge", DEFAULT_ICON_BADGE);
            json.put("icon_badge_position", DEFAULT_ICON_BADGE_POSITION);

            // Device Identity
            json.put("android_id", DEFAULT_ANDROID_ID);
            json.put("wifi_mac", DEFAULT_WIFI_MAC);
            json.put("bluetooth_mac", DEFAULT_BLUETOOTH_MAC);
            json.put("serial_number", DEFAULT_SERIAL_NUMBER);
            json.put("imei", DEFAULT_IMEI);
            json.put("imsi", DEFAULT_IMSI);
            json.put("identity_notifications", DEFAULT_IDENTITY_NOTIFICATIONS);
            json.put("identity_notifications_clear_cache", DEFAULT_IDENTITY_NOTIFICATIONS_CLEAR_CACHE);
            json.put("identity_notifications_clear_data", DEFAULT_IDENTITY_NOTIFICATIONS_CLEAR_DATA);
            json.put("identity_notifications_restart_app", DEFAULT_IDENTITY_NOTIFICATIONS_RESTART_APP);
            json.put("user_agent_hook_enabled", DEFAULT_USER_AGENT_HOOK_ENABLED);
            json.put("user_agent", DEFAULT_USER_AGENT);

            // Build Props / Device Spoofing
            json.put("build_props_enabled", DEFAULT_BUILD_PROPS_ENABLED);
            json.put("build_props_device_preset", DEFAULT_BUILD_PROPS_DEVICE_PRESET);
            json.put("build_props_randomize_fingerprint", DEFAULT_BUILD_PROPS_RANDOMIZE_FINGERPRINT);
            json.put("build_MANUFACTURER", DEFAULT_BUILD_MANUFACTURER);
            json.put("build_MODEL", DEFAULT_BUILD_MODEL);
            json.put("build_BRAND", DEFAULT_BUILD_BRAND);
            json.put("build_DEVICE", DEFAULT_BUILD_DEVICE);
            json.put("build_PRODUCT", DEFAULT_BUILD_PRODUCT);
            json.put("build_FINGERPRINT", DEFAULT_BUILD_FINGERPRINT);

            // Dialog Blocker
            json.put("dialog_blocker_enabled", DEFAULT_DIALOG_BLOCKER_ENABLED);
            json.put("block_update_dialogs", DEFAULT_BLOCK_UPDATE_DIALOGS);
            json.put("block_rating_dialogs", DEFAULT_BLOCK_RATING_DIALOGS);
            json.put("block_ad_dialogs", DEFAULT_BLOCK_AD_DIALOGS);
            json.put("block_subscription_dialogs", DEFAULT_BLOCK_SUBSCRIPTION_DIALOGS);
            json.put("dialog_block_keywords", DEFAULT_DIALOG_BLOCK_KEYWORDS);

            // Fake Calculator
            json.put("fake_calculator_enabled", DEFAULT_FAKE_CALCULATOR_ENABLED);
            json.put("fake_calculator_passcode", DEFAULT_FAKE_CALCULATOR_PASSCODE);
            json.put("fake_calculator_ask_once", DEFAULT_FAKE_CALCULATOR_ASK_ONCE);

            // Fake Camera - organized to match screenshot categories
            json.put("FakeCamera", DEFAULT_FAKE_CAMERA_ENABLED);
            json.put("FakeCameraAlternativeMode", DEFAULT_FAKE_CAMERA_ALTERNATIVE_MODE);
            json.put("FakeCameraAppSupport", DEFAULT_FAKE_CAMERA_APP_SUPPORT);
            json.put("FakeCameraCloseStreamWorkaround", DEFAULT_FAKE_CAMERA_CLOSE_STREAM_WORKAROUND);
            json.put("FakeCameraFixOrientation", DEFAULT_FAKE_CAMERA_FIX_ORIENTATION);
            json.put("FlipHorizontally", DEFAULT_FLIP_HORIZONTALLY);
            json.put("FakeCameraOpenStreamWorkaround", DEFAULT_FAKE_CAMERA_OPEN_STREAM_WORKAROUND);
            json.put("RandomizeImage", DEFAULT_RANDOMIZE_IMAGE);
            json.put("RandomizeStrength", DEFAULT_RANDOMIZE_STRENGTH);
            json.put("ResizeImage", DEFAULT_RESIZE_IMAGE);
            json.put("FakeCameraRotation", DEFAULT_FAKE_CAMERA_ROTATION);
            json.put("FakeCameraUseOriginalImageFile", DEFAULT_FAKE_CAMERA_USE_ORIGINAL_IMAGE_FILE);
            json.put("FakeCameraFloatingMenu", DEFAULT_FAKE_CAMERA_FLOATING_MENU);
            // Legacy settings for backward compatibility
            json.put("AddExifAttributes", DEFAULT_ADD_EXIF_ATTRIBUTES);
            json.put("ForcedBackCamera", DEFAULT_FORCED_BACK_CAMERA_ENABLED);

            // Display
            json.put("AllowScreenshots", DEFAULT_ALLOW_SCREENSHOTS);

            // Privacy & Security
            json.put("disable_license_validation", DEFAULT_DISABLE_LICENSE_VALIDATION);
            json.put("hide_root", DEFAULT_HIDE_ROOT);
            json.put("hide_emulator", DEFAULT_HIDE_EMULATOR);
            json.put("DisableNetworkingWithoutVpn", DEFAULT_DISABLE_NETWORKING_WITHOUT_VPN);
            json.put("NoBackgroundServices", DEFAULT_NO_BACKGROUND_SERVICES);
            json.put("package_name_workaround", DEFAULT_PACKAGE_NAME_WORKAROUND);
            
            // Disable Background Networking
            json.put("disable_background_networking", DEFAULT_DISABLE_BACKGROUND_NETWORKING);
            json.put("disable_background_networking_delay", DEFAULT_DISABLE_BACKGROUND_NETWORKING_DELAY);
            json.put("disable_background_networking_silent", DEFAULT_DISABLE_BACKGROUND_NETWORKING_SILENT);
            
            // System User Agent
            json.put("change_system_user_agent", DEFAULT_CHANGE_SYSTEM_USER_AGENT);
            json.put("system_user_agent", DEFAULT_SYSTEM_USER_AGENT);
            
            // Hide CPU/GPU Info
            json.put("hide_cpu_info", DEFAULT_HIDE_CPU_INFO);
            json.put("hide_gpu_info", DEFAULT_HIDE_GPU_INFO);
            json.put("gpu_vendor", DEFAULT_GPU_VENDOR);
            json.put("gpu_renderer", DEFAULT_GPU_RENDERER);
            json.put("gpu_version", DEFAULT_GPU_VERSION);
            
            // Hide DNS Servers
            json.put("hide_dns_servers", DEFAULT_HIDE_DNS_SERVERS);
            json.put("hide_dns_servers_completely", DEFAULT_HIDE_DNS_SERVERS_COMPLETELY);
            
            // Hide SIM Operator
            json.put("hide_sim_operator", DEFAULT_HIDE_SIM_OPERATOR);
            json.put("spoofed_operator_name", DEFAULT_SPOOFED_OPERATOR_NAME);
            json.put("spoofed_operator_numeric", DEFAULT_SPOOFED_OPERATOR_NUMERIC);
            json.put("spoofed_sim_country_iso", DEFAULT_SPOOFED_SIM_COUNTRY_ISO);

            // Background Media
            json.put("background_media", DEFAULT_BACKGROUND_MEDIA);
            json.put("background_media_webview", DEFAULT_BACKGROUND_MEDIA_WEBVIEW);
            json.put("background_media_mediaplayer", DEFAULT_BACKGROUND_MEDIA_MEDIAPLAYER);
            json.put("background_media_exoplayer", DEFAULT_BACKGROUND_MEDIA_EXOPLAYER);
            json.put("background_media_audio_focus", DEFAULT_BACKGROUND_MEDIA_AUDIO_FOCUS);

            // SOCKS Proxy
            json.put("socks_proxy", DEFAULT_SOCKS_PROXY_ENABLED);
            json.put("socks_proxy_host", DEFAULT_SOCKS_PROXY_HOST);
            json.put("socks_proxy_port", DEFAULT_SOCKS_PROXY_PORT);
            json.put("socks_proxy_user", DEFAULT_SOCKS_PROXY_USER);
            json.put("socks_proxy_pass", DEFAULT_SOCKS_PROXY_PASS);

            // Location Spoofing
            json.put("SpoofLocation", DEFAULT_SPOOF_LOCATION);
            json.put("SpoofLocationLatitude", DEFAULT_SPOOF_LOCATION_LATITUDE);
            json.put("SpoofLocationLongitude", DEFAULT_SPOOF_LOCATION_LONGITUDE);
            json.put("SpoofLocationAltitude", DEFAULT_SPOOF_LOCATION_ALTITUDE);
            json.put("SpoofLocationAccuracy", DEFAULT_SPOOF_LOCATION_ACCURACY);
            json.put("SpoofLocationShowNotification", DEFAULT_SPOOF_LOCATION_SHOW_NOTIFICATION);
            json.put("SpoofLocationApi", DEFAULT_SPOOF_LOCATION_API);
            json.put("SpoofLocationUseIpLocation", DEFAULT_SPOOF_LOCATION_USE_IP_LOCATION);
            json.put("SpoofLocationInterval", DEFAULT_SPOOF_LOCATION_INTERVAL);
            json.put("SpoofLocationCompatibilityMode", DEFAULT_SPOOF_LOCATION_COMPATIBILITY_MODE);
            json.put("SpoofLocationSimulatePositionalUncertainty", DEFAULT_SPOOF_LOCATION_SIMULATE_POSITIONAL_UNCERTAINTY);
            json.put("SpoofLocationCalculateBearing", DEFAULT_SPOOF_LOCATION_CALCULATE_BEARING);
            json.put("SpoofLocationSpeed", DEFAULT_SPOOF_LOCATION_SPEED);
            json.put("SpoofLocationBearing", DEFAULT_SPOOF_LOCATION_BEARING);
            json.put("SpoofLocationRandomize", DEFAULT_SPOOF_LOCATION_RANDOMIZE);

            // Accessible Data Directory
            json.put("accessible_data_dir_internal", DEFAULT_ACCESSIBLE_DATA_DIR_INTERNAL_ENABLED);
            json.put("accessible_data_dir_external", DEFAULT_ACCESSIBLE_DATA_DIR_EXTERNAL_ENABLED);
            json.put("accessible_data_dir_mode", DEFAULT_ACCESSIBLE_DATA_DIR_MODE);
            json.put("accessible_data_dir_advanced_mode", DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_MODE);
            json.put("accessible_data_dir_advanced_interval", DEFAULT_ACCESSIBLE_DATA_DIR_ADVANCED_INTERVAL);
            json.put("bundle_app_data", DEFAULT_BUNDLE_APP_DATA);

            // Internal Browser
            json.put("internal_browser_enabled", DEFAULT_INTERNAL_BROWSER_ENABLED);

            // Screenshot Detection Blocker
            json.put("screenshot_detection_blocker", DEFAULT_SCREENSHOT_DETECTION_BLOCKER);

            // Skip Dialogs
            json.put("skip_dialogs", DEFAULT_SKIP_DIALOGS);
            json.put("skip_dialogs_stacktraces", DEFAULT_SKIP_DIALOGS_STACKTRACES);
            json.put("monitor_stacktraces", DEFAULT_MONITOR_STACKTRACES);

            // Keep Playing Media
            json.put("keep_playing_media", DEFAULT_KEEP_PLAYING_MEDIA);
            json.put("keep_playing_media_compatibility_mode", DEFAULT_KEEP_PLAYING_MEDIA_COMPATIBILITY_MODE);

            // Custom Build Props
            json.put("custom_build_props", DEFAULT_CUSTOM_BUILD_PROPS);

            // Mock Network
            json.put("mock_wifi_connection", DEFAULT_MOCK_WIFI_CONNECTION);
            json.put("mock_mobile_connection", DEFAULT_MOCK_MOBILE_CONNECTION);
            json.put("mock_ethernet_connection", DEFAULT_MOCK_ETHERNET_CONNECTION);

            // Monitoring
            json.put("local_web_console_enabled", DEFAULT_LOCAL_WEB_CONSOLE_ENABLED);
            json.put("host_monitor_enabled", DEFAULT_HOST_MONITOR_ENABLED);
            json.put("header_monitor_enabled", DEFAULT_HEADER_MONITOR_ENABLED);
            json.put("preferences_monitor_enabled", DEFAULT_PREFERENCES_MONITOR_ENABLED);

        } catch (JSONException e) {
            Log.e(TAG, "Error creating default settings JSON", e);
        }
        return json;
    }

    /**
     * Generates a cloner.json file with default settings at the specified location.
     * @param context The application context
     * @param outputFile The file where cloner.json should be written
     * @return true if file was created successfully, false otherwise
     */
    public static boolean generateClonerJson(Context context, File outputFile) {
        if (outputFile == null) {
            Log.e(TAG, "Output file cannot be null");
            return false;
        }

        try {
            JSONObject settings = createDefaultSettings();
            String jsonString = settings.toString(2);

            File parent = outputFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                Log.e(TAG, "Failed to create parent directory: " + parent);
                return false;
            }

            try (FileWriter writer = new FileWriter(outputFile)) {
                writer.write(jsonString);
                Log.d(TAG, "Successfully generated cloner.json at: " + outputFile.getAbsolutePath());
                return true;
            }
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error generating cloner.json", e);
            return false;
        }
    }

    /**
     * Generates a cloner.json file in the app's cache directory.
     * @param context The application context
     * @return The generated file, or null if generation failed
     */
    public static File generateClonerJsonInCache(Context context) {
        if (context == null) {
            Log.e(TAG, "Context cannot be null");
            return null;
        }

        File cacheFile = new File(context.getCacheDir(), "cloner.json");
        if (generateClonerJson(context, cacheFile)) {
            return cacheFile;
        }
        return null;
    }

    /**
     * Generates a random 16-character hexadecimal Android ID.
     * @return Random Android ID string
     */
    public static String generateRandomAndroidId() {
        return String.format(Locale.US, "%016X", random.nextLong());
    }

    /**
     * Generates a random locally-administered MAC address.
     * @return Random MAC address string in format XX:XX:XX:XX:XX:XX
     */
    public static String generateRandomMac() {
        byte[] macAddress = new byte[6];
        random.nextBytes(macAddress);
        // Set the locally administered bit and unicast bit
        macAddress[0] = (byte) ((macAddress[0] & (byte) 0xFC) | (byte) 0x02);
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < macAddress.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", macAddress[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Generates a random latitude value between -90 and 90 degrees.
     * @return Random latitude string
     */
    public static String generateRandomLatitude() {
        double lat = (random.nextDouble() * 180.0) - 90.0;
        return String.format(Locale.US, "%.6f", lat);
    }

    /**
     * Generates a random longitude value between -180 and 180 degrees.
     * @return Random longitude string
     */
    public static String generateRandomLongitude() {
        double lon = (random.nextDouble() * 360.0) - 180.0;
        return String.format(Locale.US, "%.6f", lon);
    }
    
    /**
     * Generates a random IMEI number (15 digits).
     * @return Random IMEI string
     */
    public static String generateRandomImei() {
        // IMEI is 15 digits: TAC (8 digits) + Serial (6 digits) + Luhn checksum (1 digit)
        StringBuilder sb = new StringBuilder();
        // TAC (Type Allocation Code) - use realistic prefixes
        String[] tacPrefixes = {"35", "86", "01", "49"};
        sb.append(tacPrefixes[random.nextInt(tacPrefixes.length)]);
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        // Add Luhn checksum digit
        sb.append(calculateLuhnChecksum(sb.toString()));
        return sb.toString();
    }
    
    /**
     * Generates a random IMSI number (15 digits).
     * @return Random IMSI string
     */
    public static String generateRandomImsi() {
        // IMSI is 15 digits: MCC (3) + MNC (2-3) + MSIN (9-10)
        StringBuilder sb = new StringBuilder();
        // MCC (Mobile Country Code) - use common codes
        String[] mccs = {"310", "311", "234", "262", "460"}; // US, UK, Germany, China
        sb.append(mccs[random.nextInt(mccs.length)]);
        // MNC (Mobile Network Code) - 2-3 digits
        sb.append(String.format("%02d", random.nextInt(100)));
        // MSIN - remaining digits
        for (int i = sb.length(); i < 15; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }
    
    /**
     * Generates a random serial number.
     * @return Random serial number string (alphanumeric, 10-16 chars)
     */
    public static String generateRandomSerialNumber() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 10 + random.nextInt(7); // 10-16 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    /**
     * Calculate Luhn checksum digit for IMEI validation.
     */
    private static int calculateLuhnChecksum(String digits) {
        int sum = 0;
        boolean alternate = true;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(digits.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }
    
    /**
     * Generate all random privacy data at once.
     * @return JSONObject containing all randomized identity values
     */
    public static JSONObject generateAllRandomPrivacyData() {
        JSONObject data = new JSONObject();
        try {
            data.put("android_id", generateRandomAndroidId());
            data.put("wifi_mac", generateRandomMac());
            data.put("bluetooth_mac", generateRandomMac());
            data.put("serial_number", generateRandomSerialNumber());
            data.put("imei", generateRandomImei());
            data.put("imsi", generateRandomImsi());
        } catch (JSONException e) {
            Log.e(TAG, "Error generating random privacy data", e);
        }
        return data;
    }

    /**
     * Category display names for settings UI.
     */
    public static class CategoryNames {
        public static final String CLONING = "Cloning Options";
        public static final String IDENTITY = "Device Identity";
        public static final String PRIVACY = "Privacy & Security";
        public static final String FAKE_CAMERA = "Fake Camera";
        public static final String LOCATION = "Location Spoofing";
        public static final String NETWORK = "Network & Proxy";
        public static final String DISPLAY = "Display & Window";
        public static final String MEDIA = "Background Media";
        public static final String DATA = "Data Management";
        public static final String OTHER = "Other Settings";
        public static final String BUILD_PROPS = "Device Profile";
    }
    
    /**
     * Available device presets for Build.props spoofing.
     * Returns array of [preset_key, display_name] pairs.
     */
    public static String[][] getDevicePresets() {
        return new String[][] {
            {"", "-- None (Keep Original) --"},
            {"samsung_s24_ultra", "Samsung Galaxy S24 Ultra"},
            {"pixel_8_pro", "Google Pixel 8 Pro"},
            {"oneplus_12", "OnePlus 12"},
            {"xiaomi_14_pro", "Xiaomi 14 Pro"},
            {"huawei_mate60_pro", "Huawei Mate 60 Pro"},
            {"sony_xperia_1v", "Sony Xperia 1 V"},
            {"oppo_find_x7_ultra", "OPPO Find X7 Ultra"},
            {"vivo_x100_pro", "Vivo X100 Pro"}
        };
    }

    private static final java.util.Map<String, DeviceProfile> DEVICE_PROFILES = new java.util.HashMap<>();
    static {
        // Samsung Galaxy S24 Ultra
        DEVICE_PROFILES.put("samsung_s24_ultra", new DeviceProfile(
            "Samsung", "SM-S928B", "Galaxy S24 Ultra", "samsung",
            "s24ultra", "samsung/s24ultrxx/s24ultra:14/UP1A.231005.007/S928BXXU1AWLM:user/release-keys",
            34, "14", "UP1A.231005.007"
        ));

        // Google Pixel 8 Pro
        DEVICE_PROFILES.put("pixel_8_pro", new DeviceProfile(
            "Google", "Pixel 8 Pro", "Pixel 8 Pro", "google",
            "husky", "google/husky/husky:14/UD1A.231105.004/11010374:user/release-keys",
            34, "14", "UD1A.231105.004"
        ));

        // OnePlus 12
        DEVICE_PROFILES.put("oneplus_12", new DeviceProfile(
            "OnePlus", "CPH2573", "OnePlus 12", "oneplus",
            "aston", "OnePlus/CPH2573/OP5913L1:14/UKQ1.230924.001/T.18d1b7f_17e7_19:user/release-keys",
            34, "14", "UKQ1.230924.001"
        ));

        // Xiaomi 14 Pro
        DEVICE_PROFILES.put("xiaomi_14_pro", new DeviceProfile(
            "Xiaomi", "23116PN5BC", "Xiaomi 14 Pro", "xiaomi",
            "shennong", "Xiaomi/shennong/shennong:14/UKQ1.231003.002/V816.0.5.0.UNACNXM:user/release-keys",
            34, "14", "UKQ1.231003.002"
        ));

        // Huawei Mate 60 Pro
        DEVICE_PROFILES.put("huawei_mate60_pro", new DeviceProfile(
            "HUAWEI", "ALN-AL00", "HUAWEI Mate 60 Pro", "huawei",
            "ALN", "HUAWEI/ALN-AL00/HWALN:12/HUAWEIALN-AL00/105.0.0.73C00:user/release-keys",
            31, "12", "HUAWEIALN-AL00"
        ));

        // Sony Xperia 1 V
        DEVICE_PROFILES.put("sony_xperia_1v", new DeviceProfile(
            "Sony", "XQ-DQ72", "Xperia 1 V", "sony",
            "pdx234", "Sony/XQ-DQ72/XQ-DQ72:14/67.2.A.2.118/067002A002011800301508470:user/release-keys",
            34, "14", "67.2.A.2.118"
        ));

        // OPPO Find X7 Ultra
        DEVICE_PROFILES.put("oppo_find_x7_ultra", new DeviceProfile(
            "OPPO", "PHZ110", "OPPO Find X7 Ultra", "oppo",
            "PHZ110", "OPPO/PHZ110/OP5D3BL1:14/UP1A.231005.007/S.17f2e97_1e89_8:user/release-keys",
            34, "14", "UP1A.231005.007"
        ));

        // Vivo X100 Pro
        DEVICE_PROFILES.put("vivo_x100_pro", new DeviceProfile(
            "vivo", "V2324A", "vivo X100 Pro", "vivo",
            "PD2324", "vivo/PD2324/PD2324:14/UP1A.231005.007/compiler11211512:user/release-keys",
            34, "14", "UP1A.231005.007"
        ));
    }

    public static class DeviceProfile {
        public final String manufacturer;
        public final String model;
        public final String product;
        public final String brand;
        public final String device;
        public final String fingerprint;
        public final int sdkInt;
        public final String release;
        public final String displayId;

        public DeviceProfile(String manufacturer, String model, String product, String brand,
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

    public static DeviceProfile getDeviceProfile(String key) {
        return DEVICE_PROFILES.get(key);
    }
    
    /**
     * Get human-readable display name for a setting key.
     * Converts internal keys like "FakeCameraAlternativeMode" to "Alternative Mode".
     */
    public static String getDisplayNameForKey(String key) {
        if (key == null) return "Unknown";
        
        // Define mappings for common settings
        switch (key) {
            // Action items
            case "new_identity_action": return "New Identity";
            
            // Fake Camera settings
            case "FakeCamera": return "Fake Camera";
            case "FakeCameraAlternativeMode": return "Alternative Mode";
            case "FakeCameraAppSupport": return "App Support";
            case "FakeCameraCloseStreamWorkaround": return "Close Stream Workaround";
            case "FakeCameraFixOrientation": return "Fix Orientation";
            case "FakeCameraOpenStreamWorkaround": return "Open Stream Workaround";
            case "FakeCameraRotation": return "Rotation";
            case "FakeCameraUseOriginalImageFile": return "Use Original Image File";
            case "FakeCameraFloatingMenu": return "Floating Camera Menu";
            case "FakeCameraImagePath": return "Image Path";
            case "FlipHorizontally": return "Flip Horizontally";
            case "RandomizeImage": return "Randomize Picture";
            case "RandomizeStrength": return "Randomize Strength";
            case "ResizeImage": return "Resize Picture";
            case "AddExifAttributes": return "Add EXIF Attributes";
            case "AddSpoofedLocation": return "Add Spoofed Location to EXIF";
            case "ForcedBackCamera": return "Forced Back Camera";
            
            // Location settings
            case "SpoofLocation": return "Spoof Location";
            case "SpoofLocationLatitude": return "Latitude";
            case "SpoofLocationLongitude": return "Longitude";
            case "SpoofLocationAltitude": return "Altitude";
            case "SpoofLocationAccuracy": return "Accuracy";
            case "SpoofLocationRandomize": return "Randomize Position";
            case "SpoofLocationUseIpLocation": return "Use IP-based Location";
            case "SpoofLocationShowNotification": return "Show Notification";
            case "SpoofLocationApi": return "Allow API Control";
            case "SpoofLocationInterval": return "Update Interval (ms)";
            case "SpoofLocationCompatibilityMode": return "Compatibility Mode";
            case "SpoofLocationSimulatePositionalUncertainty": return "Simulate Positional Uncertainty";
            case "SpoofLocationCalculateBearing": return "Calculate Bearing";
            case "SpoofLocationSpeed": return "Speed (m/s)";
            case "SpoofLocationBearing": return "Bearing (degrees)";
            
            // Device Identity settings
            case "android_id": return "Android ID";
            case "wifi_mac": return "Wi-Fi MAC";
            case "bluetooth_mac": return "Bluetooth MAC";
            case "serial_number": return "Serial Number";
            case "imei": return "IMEI";
            case "imsi": return "IMSI";
            case "user_agent": return "User Agent";
            
            // Identity Notifications settings
            case "identity_notifications": return "Identity Notifications";
            case "identity_notifications_clear_cache": return "Clear Cache on New Identity";
            case "identity_notifications_clear_data": return "Clear App Data on New Identity";
            case "identity_notifications_restart_app": return "Restart App on New Identity";
            
            // Build props settings
            case "build_props_enabled": return "Enable Device Spoofing";
            case "build_props_device_preset": return "Device Preset";
            case "build_props_randomize_fingerprint": return "Randomize Fingerprint";
            case "build_MANUFACTURER": return "Manufacturer";
            case "build_MODEL": return "Model";
            case "build_BRAND": return "Brand";
            case "build_DEVICE": return "Device";
            case "build_PRODUCT": return "Product";
            case "build_FINGERPRINT": return "Fingerprint";
            
            // Display settings
            case "AllowScreenshots": return "Allow Screenshots";
            
            // Background Media
            case "background_media": return "Background Media";
            case "background_media_webview": return "WebView Audio";
            case "background_media_mediaplayer": return "MediaPlayer Audio";
            case "background_media_exoplayer": return "ExoPlayer Audio";
            case "background_media_audio_focus": return "Audio Focus";
            
            // Data Access
            case "accessible_data_dir_internal": return "Internal Data Access";
            case "accessible_data_dir_external": return "External Data Access";
            case "accessible_data_dir_mode": return "Access Mode";
            case "accessible_data_dir_advanced_mode": return "Advanced Mode";
            case "bundle_app_data": return "Bundle App Data";
            
            // Dialog Blocker
            case "dialog_blocker_enabled": return "Enable Dialog Blocker";
            case "block_update_dialogs": return "Block Update Dialogs";
            case "block_rating_dialogs": return "Block Rating Dialogs";
            case "block_ad_dialogs": return "Block Ad Dialogs";
            case "block_subscription_dialogs": return "Block Subscription Dialogs";
            
            // Fake Calculator
            case "fake_calculator_enabled": return "Enable Fake Calculator";
            case "fake_calculator_passcode": return "Passcode";
            case "fake_calculator_ask_once": return "Ask Once Per Session";
            
            // SOCKS Proxy
            case "socks_proxy": return "Enable SOCKS Proxy";
            case "socks_proxy_host": return "Proxy Host";
            case "socks_proxy_port": return "Proxy Port";
            case "socks_proxy_user": return "Proxy Username";
            case "socks_proxy_pass": return "Proxy Password";
            
            // Disable Background Networking
            case "disable_background_networking": return "Disable Background Networking";
            case "disable_background_networking_delay": return "Enable Delay (ms)";
            case "disable_background_networking_silent": return "Silent Mode";
            
            // System User Agent
            case "change_system_user_agent": return "Change System User Agent";
            case "system_user_agent": return "System User Agent";
            
            // Hide CPU/GPU Info
            case "hide_cpu_info": return "Hide CPU Info";
            case "hide_gpu_info": return "Hide GPU Info";
            case "gpu_vendor": return "GPU Vendor";
            case "gpu_renderer": return "GPU Renderer";
            case "gpu_version": return "GPU Version";
            
            // Hide DNS Servers
            case "hide_dns_servers": return "Hide DNS Servers";
            case "hide_dns_servers_completely": return "Hide Completely";
            
            // Hide SIM Operator
            case "hide_sim_operator": return "Hide SIM Operator";
            case "spoofed_operator_name": return "Operator Name";
            case "spoofed_operator_numeric": return "Operator Numeric Code";
            case "spoofed_sim_country_iso": return "Country ISO";
            
            // Internal Browser
            case "internal_browser_enabled": return "Internal Browser";
            
            // Screenshot Detection Blocker
            case "screenshot_detection_blocker": return "Block Screenshot Detection";
            
            // Privacy & Security
            case "disable_license_validation": return "Disable License Validation";
            case "hide_root": return "Hide Root";
            case "hide_emulator": return "Hide Emulator";
            case "DisableNetworkingWithoutVpn": return "Disable Networking Without VPN";
            case "NoBackgroundServices": return "No Background Services";
            case "package_name_workaround": return "Package Name Workaround";

            default:
                // Convert camelCase or snake_case to Title Case
                return formatKeyAsDisplayName(key);
        }
    }
    
    /**
     * Format a raw key as a display name by converting camelCase/snake_case to Title Case.
     */
    private static String formatKeyAsDisplayName(String key) {
        if (key == null || key.isEmpty()) return "Unknown";
        
        // Replace underscores with spaces
        String result = key.replace("_", " ");
        
        // Insert spaces before capital letters (camelCase)
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(result.charAt(i - 1))) {
                sb.append(' ');
            }
            sb.append(c);
        }
        result = sb.toString();
        
        // Capitalize first letter of each word
        String[] words = result.split(" ");
        sb = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            if (i > 0) sb.append(' ');
            String word = words[i].toLowerCase();
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    sb.append(word.substring(1));
                }
            }
        }
        
        return sb.toString();
    }

    /**
     * Category descriptions for more descriptive subtitles.
     */
    public static class CategoryDescriptions {
        public static final String IDENTITY = "Spoof device identifiers like Android ID and MAC";
        public static final String PRIVACY = "Location spoofing and privacy hardening";
        public static final String NETWORK = "Proxy settings and mock connections";
        public static final String WEBVIEW = "Custom user agent and internal browser options";
        public static final String MEDIA = "Background playback, fake camera, and media tweaks";
        public static final String DATA = "App data directory access settings";
        public static final String DISPLAY = "App name, icon, and screenshot controls";
        public static final String DEVELOPER = "Build props, cloning mode, and monitoring tools";
        public static final String OTHER = "Additional configuration options";
    }

    /**
     * Gets a descriptive subtitle for a category based on its configured values.
     * @param categoryKey The category key
     * @param json The current settings JSON
     * @return A descriptive subtitle string
     */
    public static String getCategorySubtitle(String categoryKey, JSONObject json) {
        if (json == null || categoryKey == null) {
            return "Tap to configure";
        }

        try {
            switch (categoryKey) {
                case "cat_identity":
                    String androidId = json.optString("android_id", VALUE_MODE_NO_CHANGE);
                    String wifiMac = json.optString("wifi_mac", "");
                    String btMac = json.optString("bluetooth_mac", "");
                    String serial = json.optString("serial_number", "");
                    String imei = json.optString("imei", VALUE_MODE_NO_CHANGE);
                    String imsi = json.optString("imsi", VALUE_MODE_NO_CHANGE);
                    int configured = 0;
                    if (!androidId.isEmpty() && !VALUE_MODE_NO_CHANGE.equalsIgnoreCase(androidId)) configured++;
                    if (!wifiMac.isEmpty() && !wifiMac.equals(DEFAULT_WIFI_MAC)) configured++;
                    if (!btMac.isEmpty()) configured++;
                    if (!serial.isEmpty()) configured++;
                    if (!imei.isEmpty() && !VALUE_MODE_NO_CHANGE.equalsIgnoreCase(imei)) configured++;
                    if (!imsi.isEmpty() && !VALUE_MODE_NO_CHANGE.equalsIgnoreCase(imsi)) configured++;
                    return configured == 0 ? "Using default identifiers" : configured + " identifier(s) customized";

                case "cat_privacy":
                    boolean locationEnabled = json.optBoolean("SpoofLocation", DEFAULT_SPOOF_LOCATION);
                    int privacyCount = 0;
                    if (json.optBoolean("disable_license_validation", false)) privacyCount++;
                    if (json.optBoolean("hide_root", false)) privacyCount++;
                    if (json.optBoolean("hide_emulator", false)) privacyCount++;
                    if (json.optBoolean("DisableNetworkingWithoutVpn", DEFAULT_DISABLE_NETWORKING_WITHOUT_VPN)) privacyCount++;
                    if (json.optBoolean("NoBackgroundServices", DEFAULT_NO_BACKGROUND_SERVICES)) privacyCount++;
                    if (locationEnabled) {
                        double lat = json.optDouble("SpoofLocationLatitude", DEFAULT_SPOOF_LOCATION_LATITUDE);
                        double lon = json.optDouble("SpoofLocationLongitude", DEFAULT_SPOOF_LOCATION_LONGITUDE);
                        String suffix = privacyCount > 0 ? " + privacy hardening" : "";
                        return String.format(Locale.US, "Spoofing to %.4f, %.4f%s", lat, lon, suffix);
                    }
                    return privacyCount == 0 ? "Default privacy settings" : privacyCount + " privacy option(s) active";

                case "cat_network":
                    boolean proxyEnabled = json.optBoolean("socks_proxy", DEFAULT_SOCKS_PROXY_ENABLED);
                    if (proxyEnabled) {
                        String host = json.optString("socks_proxy_host", "");
                        return host.isEmpty() ? "Proxy enabled (no host)" : "Proxy: " + host;
                    }
                    boolean mockWifi = !json.optString("mock_wifi_connection", DEFAULT_MOCK_WIFI_CONNECTION).isEmpty();
                    boolean mockMobile = !json.optString("mock_mobile_connection", DEFAULT_MOCK_MOBILE_CONNECTION).isEmpty();
                    boolean mockEthernet = !json.optString("mock_ethernet_connection", DEFAULT_MOCK_ETHERNET_CONNECTION).isEmpty();
                    int mocked = (mockWifi ? 1 : 0) + (mockMobile ? 1 : 0) + (mockEthernet ? 1 : 0);
                    if (mocked > 0) {
                        return mocked == 1 ? "1 mock connection set" : mocked + " mock connections set";
                    }
                    return "Direct connection (no proxy)";

                case "cat_webview":
                    boolean uaHook = json.optBoolean("user_agent_hook_enabled", DEFAULT_USER_AGENT_HOOK_ENABLED);
                    boolean internalBrowser = json.optBoolean("internal_browser_enabled", DEFAULT_INTERNAL_BROWSER_ENABLED);
                    if (uaHook && internalBrowser) {
                        return "Internal browser with custom UA";
                    } else if (uaHook) {
                        String ua = json.optString("user_agent", DEFAULT_USER_AGENT);
                        return ua.isEmpty() ? "Custom user agent enabled" : "UA set: " + ua;
                    } else if (internalBrowser) {
                        return "Internal browser enabled";
                    }
                    return "Default WebView behavior";

                case "cat_media":
                    boolean mediaEnabled = json.optBoolean("background_media", DEFAULT_BACKGROUND_MEDIA);
                    boolean fakeCamera = json.optBoolean("FakeCamera", DEFAULT_FAKE_CAMERA_ENABLED);
                    boolean keepPlaying = json.optBoolean("keep_playing_media", DEFAULT_KEEP_PLAYING_MEDIA);
                    StringBuilder mediaSummary = new StringBuilder();
                    if (mediaEnabled) {
                        mediaSummary.append("Background playback");
                    }
                    if (fakeCamera) {
                        if (mediaSummary.length() > 0) mediaSummary.append(", ");
                        mediaSummary.append("Fake camera");
                    }
                    if (keepPlaying) {
                        if (mediaSummary.length() > 0) mediaSummary.append(", ");
                        mediaSummary.append("Keep media active");
                    }
                    return mediaSummary.length() == 0 ? "Default media behavior" : mediaSummary.toString();

                case "cat_display":
                    boolean screenshots = json.optBoolean("AllowScreenshots", DEFAULT_ALLOW_SCREENSHOTS);
                    boolean screenshotBlocker = json.optBoolean("screenshot_detection_blocker", DEFAULT_SCREENSHOT_DETECTION_BLOCKER);
                    if (!screenshots) {
                        return "Screenshots blocked";
                    } else if (screenshotBlocker) {
                        return "Screenshot detection blocked";
                    }
                    boolean appNameChanged = !json.optString("app_name", DEFAULT_APP_NAME).isEmpty();
                    boolean iconAdjusted = !DEFAULT_ICON_COLOR.equals(json.optString("icon_color", DEFAULT_ICON_COLOR))
                            || Double.compare(json.optDouble("icon_hue", DEFAULT_ICON_HUE), DEFAULT_ICON_HUE) != 0
                            || Double.compare(json.optDouble("icon_saturation", DEFAULT_ICON_SATURATION), DEFAULT_ICON_SATURATION) != 0
                            || Double.compare(json.optDouble("icon_lightness", DEFAULT_ICON_LIGHTNESS), DEFAULT_ICON_LIGHTNESS) != 0
                            || json.optBoolean("icon_auto_hue", DEFAULT_ICON_AUTO_HUE)
                            || json.optBoolean("icon_invert_colors", DEFAULT_ICON_INVERT_COLORS)
                            || json.optBoolean("icon_sepia", DEFAULT_ICON_SEPIA)
                            || json.optInt("icon_rotation", DEFAULT_ICON_ROTATION) != DEFAULT_ICON_ROTATION
                            || json.optBoolean("icon_flip_horizontal", DEFAULT_ICON_FLIP_HORIZONTAL)
                            || json.optBoolean("icon_flip_vertical", DEFAULT_ICON_FLIP_VERTICAL)
                            || !json.optString("icon_badge", DEFAULT_ICON_BADGE).isEmpty()
                            || json.optInt("icon_badge_position", DEFAULT_ICON_BADGE_POSITION) != DEFAULT_ICON_BADGE_POSITION;
                    if (appNameChanged || iconAdjusted) {
                        return "App name/icon customized";
                    }
                    return "Default display settings";

                case "cat_data":
                    boolean internalEnabled = json.optBoolean("accessible_data_dir_internal", false);
                    boolean externalEnabled = json.optBoolean("accessible_data_dir_external", false);
                    String modeValue = normalizeAccessibleDataDirMode(json.optString("accessible_data_dir_mode", DEFAULT_ACCESSIBLE_DATA_DIR_MODE));
                    String modeSuffix = "";
                    if ("READ_WRITE".equals(modeValue)) {
                        modeSuffix = " (read/write)";
                    } else if ("READ_ONLY".equals(modeValue)) {
                        modeSuffix = " (read only)";
                    }
                    if (internalEnabled && externalEnabled) {
                        return "Internal and external data accessible" + modeSuffix;
                    } else if (internalEnabled) {
                        return "Internal data accessible" + modeSuffix;
                    } else if (externalEnabled) {
                        return "External data accessible" + modeSuffix;
                    }
                    return "Data directory access restricted";

                case "cat_developer":
                    boolean buildPropsEnabled = json.optBoolean("build_props_enabled", DEFAULT_BUILD_PROPS_ENABLED);
                    String mode = json.optString("cloning_mode", DEFAULT_CLONING_MODE);
                    boolean dialogBlockerEnabled = json.optBoolean("dialog_blocker_enabled", false);
                    boolean fakeCalcEnabled = json.optBoolean("fake_calculator_enabled", false);
                    boolean monitoringEnabled = json.optBoolean("local_web_console_enabled", DEFAULT_LOCAL_WEB_CONSOLE_ENABLED)
                            || json.optBoolean("host_monitor_enabled", DEFAULT_HOST_MONITOR_ENABLED)
                            || json.optBoolean("header_monitor_enabled", DEFAULT_HEADER_MONITOR_ENABLED)
                            || json.optBoolean("preferences_monitor_enabled", DEFAULT_PREFERENCES_MONITOR_ENABLED);
                    boolean skipDialogsConfigured = !json.optString("skip_dialogs", "").isEmpty()
                            || !json.optString("skip_dialogs_stacktraces", "").isEmpty();
                    boolean customBuildProps = !json.optString("custom_build_props", "").isEmpty();
                    int devTweaks = 0;
                    if (!CLONING_MODE_REPLACE.equals(mode)) devTweaks++;
                    if (buildPropsEnabled || customBuildProps) devTweaks++;
                    if (dialogBlockerEnabled) devTweaks++;
                    if (fakeCalcEnabled) devTweaks++;
                    if (monitoringEnabled) devTweaks++;
                    if (skipDialogsConfigured) devTweaks++;

                    if (buildPropsEnabled) {
                        String preset = json.optString("build_props_device_preset", "");
                        if (!preset.isEmpty()) {
                            return "Device preset: " + preset;
                        }
                        String model = json.optString("build_MODEL", "");
                        if (!model.isEmpty()) {
                            return "Custom device: " + model;
                        }
                        return "Build props spoofing enabled";
                    }
                    if (!CLONING_MODE_REPLACE.equals(mode)) {
                        if (CLONING_MODE_GENERATE.equals(mode)) {
                            return "Generate new package variant";
                        } else if (CLONING_MODE_CUSTOM.equals(mode)) {
                            String customPkg = json.optString("custom_package_name", "");
                            return customPkg.isEmpty() ? "Custom package name (not set)" : "Custom: " + customPkg;
                        }
                        return "Custom cloning mode active";
                    }
                    if (devTweaks > 0) {
                        return devTweaks + " developer option(s) active";
                    }
                    return "Default developer settings";

                default:
                    return "Tap to configure";
            }
        } catch (Exception e) {
            Log.w(TAG, "Error generating category subtitle", e);
            return "Tap to configure";
        }
    }

    private static String normalizeAccessibleDataDirMode(String mode) {
        if (mode == null) {
            return DEFAULT_ACCESSIBLE_DATA_DIR_MODE;
        }
        String normalized = mode.trim().toUpperCase(Locale.US).replace('-', '_');
        if (normalized.isEmpty()) {
            return DEFAULT_ACCESSIBLE_DATA_DIR_MODE;
        }
        switch (normalized) {
            case "READ_ONLY":
            case "READ_WRITE":
            case "NORMAL":
                return normalized;
            default:
                return DEFAULT_ACCESSIBLE_DATA_DIR_MODE;
        }
    }
}
