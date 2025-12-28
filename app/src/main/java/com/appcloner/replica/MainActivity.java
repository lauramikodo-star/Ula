package com.appcloner.replica;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.SharedPreferences;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import com.android.apksig.ApkSigner;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String INJECTED_PROVIDER_SUFFIX = ".com.applisto.appcloner.DefaultProvider";
    private static final Pattern SIG_PATH = Pattern.compile(
            "^META-INF/(.+\\.(RSA|DSA|EC|SF)|MANIFEST\\.MF)$", Pattern.CASE_INSENSITIVE);
    private static final String ANDROID_MANIFEST = "AndroidManifest.xml";
    private static final String KEYSTORE_ASSET = "debug.keystore";
    private static final String STORE_PWD      = "appklown";
    private static final String KEY_PWD        = "appklown";
    private static final String ALIAS          = "Harrygp";
    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";
    private static final String BUNDLE_DATA_SETTING_KEY = "bundle_app_data";
    private static final String LIVE_VIDEO_SETTING_KEY = "live_video_file";
    private static final String CLONING_MODE_KEY = "cloning_mode";
    private static final String CLONING_MODE_REPLACE = "replace_original";
    private static final String CLONING_MODE_GENERATE = "generate_new_package";
    private static final String CLONING_MODE_CUSTOM = "custom_package";
    private static final String CUSTOM_PACKAGE_NAME_KEY = "custom_package_name";
    private static final int WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 101;
    private static final String PREFS_NAME = "AppClonerPrefs";
    // Signing note dialog removed - proceed directly with installation
    private static final Map<String, String> SPECIAL_TOKENS;
    private static final Map<String, List<String>> GROUP_CHILD_ORDER;

    // Settings category definitions for parent-child organization
    private static final Map<String, List<String>> SETTINGS_CATEGORIES;
    private static final Map<String, String> CATEGORY_DISPLAY_NAMES;

    static {
        Map<String, String> tokens = new HashMap<>();
        tokens.put("id", "ID");
        tokens.put("mac", "MAC");
        tokens.put("ua", "UA");
        tokens.put("api", "API");
        tokens.put("gps", "GPS");
        tokens.put("imei", "IMEI");
        tokens.put("imsi", "IMSI");
        tokens.put("bt", "BT");
        tokens.put("wifi", "Wi-Fi");
        tokens.put("dns", "DNS");
        tokens.put("ip", "IP");
        SPECIAL_TOKENS = Collections.unmodifiableMap(tokens);

        Map<String, List<String>> order = new HashMap<>();
        order.put("build", Arrays.asList("enabled", "name", "manufacturer", "brand", "model", "product", "device", "hardware", "fingerprint"));
        order.put("socks_proxy", Arrays.asList("enabled", "host", "port", "user", "pass", "alternative_mode", "api", "enable_placeholders", "list_random_proxy"));
        order.put("FakeCamera", Arrays.asList("Enabled"));
        order.put("floating", Arrays.asList("enabled", "window_width", "window_height", "window_x", "window_y"));
        order.put("background_media", Arrays.asList("webview", "mediaplayer", "exoplayer", "audio_focus"));
        order.put("SpoofLocation", Arrays.asList("Latitude", "Longitude", "Altitude", "Accuracy", "ShowNotification", "Api", "UseIpLocation", "Interval", "CompatibilityMode", "SimulatePositionalUncertainty", "CalculateBearing", "Speed", "Bearing", "Randomize"));
        order.put("accessible_data_dir", Arrays.asList("internal_enabled", "external_enabled", "mode", "advanced_mode", "advanced_interval"));
        GROUP_CHILD_ORDER = Collections.unmodifiableMap(order);

        // Define categories with their child settings
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("cat_identity", Arrays.asList(
                "new_identity_action",
                "android_id",
                "wifi_mac",
                "bluetooth_mac",
                "serial_number",
                "imei",
                "imsi",
                "hide_sim_operator",
                "spoofed_operator_name",
                "spoofed_operator_numeric",
                "spoofed_sim_country_iso"
        ));
        categories.put("cat_privacy", Arrays.asList(
                "hide_root",
                "hide_emulator",
                "DisableNetworkingWithoutVpn",
                "NoBackgroundServices",
                "disable_license_validation",
                "hide_cpu_info",
                "hide_gpu_info",
                "gpu_vendor",
                "gpu_renderer",
                "gpu_version",
                "SpoofLocation",
                "SpoofLocationLatitude",
                "SpoofLocationLongitude",
                "SpoofLocationAltitude",
                "SpoofLocationAccuracy",
                "SpoofLocationShowNotification",
                "SpoofLocationApi",
                "SpoofLocationUseIpLocation",
                "SpoofLocationInterval",
                "SpoofLocationCompatibilityMode",
                "SpoofLocationSimulatePositionalUncertainty",
                "SpoofLocationCalculateBearing",
                "SpoofLocationSpeed",
                "SpoofLocationBearing",
                "SpoofLocationRandomize"
        ));
        categories.put("cat_network", Arrays.asList(
                "socks_proxy",
                "socks_proxy_host",
                "socks_proxy_port",
                "socks_proxy_user",
                "socks_proxy_pass",
                "hide_dns_servers",
                "hide_dns_servers_completely",
                "disable_background_networking",
                "disable_background_networking_delay",
                "disable_background_networking_silent",
                "mock_wifi_connection",
                "mock_mobile_connection",
                "mock_ethernet_connection"
        ));
        categories.put("cat_webview", Arrays.asList(
                "user_agent_hook_enabled",
                "user_agent",
                "internal_browser_enabled"
        ));
        categories.put("cat_media", Arrays.asList(
                "background_media",
                "background_media_webview",
                "background_media_mediaplayer",
                "background_media_exoplayer",
                "background_media_audio_focus",
                "keep_playing_media",
                "keep_playing_media_compatibility_mode",
                "FakeCamera",
                "FakeCameraRotation",
                "FakeCameraAlternativeMode",
                "FakeCameraAppSupport",
                "FakeCameraOpenStreamWorkaround",
                "FakeCameraCloseStreamWorkaround",
                "FakeCameraFixOrientation",
                "FakeCameraUseOriginalImageFile",
                "FakeCameraFloatingMenu",
                "FlipHorizontally",
                "RandomizeImage",
                "RandomizeStrength",
                "ResizeImage",
                "AddExifAttributes",
                "ForcedBackCamera"
        ));
        categories.put("cat_data", Arrays.asList(
                "accessible_data_dir_internal",
                "accessible_data_dir_external",
                "accessible_data_dir_mode",
                "accessible_data_dir_advanced_mode",
                "accessible_data_dir_advanced_interval",
                "bundle_app_data"
        ));
        categories.put("cat_display", Arrays.asList(
                "AllowScreenshots",
                "screenshot_detection_blocker",
                "app_name",
                "icon_color",
                "icon_hue",
                "icon_saturation",
                "icon_lightness",
                "icon_auto_hue",
                "icon_invert_colors",
                "icon_sepia",
                "icon_rotation",
                "icon_flip_horizontal",
                "icon_flip_vertical",
                "icon_badge",
                "icon_badge_position"
        ));
        categories.put("cat_developer", Arrays.asList(
                "cloning_mode",
                "custom_package_name",
                "package_name_workaround",
                "build_props_enabled",
                "build_props_device_preset",
                "build_props_randomize_fingerprint",
                "build_MANUFACTURER",
                "build_MODEL",
                "build_BRAND",
                "build_DEVICE",
                "build_PRODUCT",
                "build_FINGERPRINT",
                "custom_build_props",
                "dialog_blocker_enabled",
                "block_update_dialogs",
                "block_rating_dialogs",
                "block_ad_dialogs",
                "block_subscription_dialogs",
                "dialog_block_keywords",
                "fake_calculator_enabled",
                "fake_calculator_passcode",
                "fake_calculator_ask_once",
                "skip_dialogs",
                "skip_dialogs_stacktraces",
                "monitor_stacktraces",
                "local_web_console_enabled",
                "host_monitor_enabled",
                "header_monitor_enabled",
                "preferences_monitor_enabled"
        ));
        SETTINGS_CATEGORIES = Collections.unmodifiableMap(categories);

        // Category display names
        Map<String, String> catNames = new LinkedHashMap<>();
        catNames.put("cat_identity", "Identity & tracking options");
        catNames.put("cat_privacy", "Privacy options");
        catNames.put("cat_network", "Network options");
        catNames.put("cat_webview", "WebView options");
        catNames.put("cat_media", "Media options");
        catNames.put("cat_data", "Storage options");
        catNames.put("cat_display", "Display options");
        catNames.put("cat_developer", "Developer options");
        CATEGORY_DISPLAY_NAMES = Collections.unmodifiableMap(catNames);
    }
    private ExtendedFloatingActionButton selectApkBtn;
    private ExtendedFloatingActionButton processApkBtn;
    private TextView selectedTxt, statusTxt;
    private ListView installedAppsList, clonedAppsList;
    private TabLayout tabLayout;
    private FrameLayout settingsEditorContainer;
    private View currentSettingsEditorView = null;
    private File tempClonerJsonForEditor = null;
    private File sourceApkFileForEditor = null;
    private File bundledDataFileForCloning = null;
    private String bundledDataDisplayName = null;
    private Setting bundleDataSetting = null;
    private File liveVideoFileForCloning = null;
    private String liveVideoDisplayName = null;
    private Setting liveVideoSetting = null;
    private File processedIconFile = null;
    private SettingsAdapter currentSettingsAdapter = null;
    private TextView bundleDataDialogSummary = null;
    private Button bundleDataDialogClearButton = null;
    private JSONObject currentSettingsJson = null;
    private File currentSettingsFile = null;
    private boolean currentSettingsIsClonedApp = false;
    private AppInfo currentSettingsApp = null;
    private Uri inputApkUri, outputApkUri;
    private List<AppInfo> allApps = new ArrayList<>();
    private List<AppInfo> clonedApps = new ArrayList<>();
    private AppListAdapter allAppsAdapter, clonedAppsAdapter;
    private SelectedAppInfo selectedAppInfo = null;
    private String pendingUninstallPackageName = null;
    private String pendingUninstallAppName = null;
    private File clonerJsonFile;
    private SecureRandom random = new SecureRandom();
    private BroadcastReceiver exportResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if ("com.appcloner.replica.EXPORT_COMPLETED".equals(action)) {
                String packageName = intent.getStringExtra("exported_package");
                String exportPath = intent.getStringExtra("export_path");
                boolean success = intent.getBooleanExtra("export_success", false);
                String errorMessage = intent.getStringExtra("error_message");
                runOnUiThread(() -> {
                    if (success && exportPath != null) {
                        statusTxt.setText("Data export completed for " + packageName);
                        Toast.makeText(MainActivity.this, "Data exported to: " + exportPath, Toast.LENGTH_LONG).show();
                    } else {
                        statusTxt.setText("Data export failed for " + packageName);
                        String message = "Export failed";
                        if (errorMessage != null) {
                            message += ": " + errorMessage;
                        }
                        Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }
    };
    private final ActivityResultLauncher<Intent> pickApk =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                    inputApkUri = res.getData().getData();
                    if (inputApkUri != null) {
                        if (!isValidApk(inputApkUri)) {
                             statusTxt.setText("Error: Invalid APK file selected");
                             inputApkUri = null;
                             selectedTxt.setText("Select an APK");
                             processApkBtn.setEnabled(false);
                             selectedAppInfo = null;
                             hideSettingsEditor();
                             return;
                        }
                        selectedTxt.setText("Selected: " + inputApkUri.getLastPathSegment());
                        statusTxt.setText("APK selected. Ready to process.");
                        selectedAppInfo = null;
                        
                        // Extract app info (name, icon, package) from APK
                        AppInfo externalApkInfo = extractAppInfoFromApk(inputApkUri);
                        if (externalApkInfo == null) {
                            externalApkInfo = new AppInfo();
                            externalApkInfo.appName = "External APK: " + inputApkUri.getLastPathSegment();
                            externalApkInfo.packageName = "external.apk";
                            externalApkInfo.icon = null;
                        }
                        showSettingsEditorForApp(externalApkInfo, false);
                    }
                }
            });
    private final ActivityResultLauncher<Intent> createApk =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                    outputApkUri = res.getData().getData();
                    if (outputApkUri != null) {
                        statusTxt.setText("Processing...");
                        startProcessing();
                    }
                }
            });
    private final ActivityResultLauncher<Intent> pickDataFile =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                    Uri dataUri = res.getData().getData();
                    if (dataUri != null) {
                        File targetFile = null;
                        try {
                            File cacheDir = getCacheDir();
                            targetFile = new File(cacheDir, "bundled_data_for_cloning.zip");
                            try (InputStream is = getContentResolver().openInputStream(dataUri);
                                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            bundledDataFileForCloning = targetFile;
                            String displayName = dataUri.getLastPathSegment();
                            if (displayName == null || displayName.trim().isEmpty()) {
                                displayName = targetFile.getName();
                            }
                            bundledDataDisplayName = displayName;
                            Toast.makeText(this, "Data file selected: " + displayName, Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Log.e(TAG, "Error copying selected data file", e);
                            Toast.makeText(this, "Error selecting data file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
                                Log.w(TAG, "Failed to delete temporary bundle data file after error: " + targetFile);
                            }
                            bundledDataFileForCloning = null;
                            bundledDataDisplayName = null;
                        }
                        refreshBundleDataUi();
                    }
                }
            });
    private final ActivityResultLauncher<Intent> pickVideoFile =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                if (res.getResultCode() == Activity.RESULT_OK && res.getData() != null) {
                    Uri dataUri = res.getData().getData();
                    if (dataUri != null) {
                        File targetFile = null;
                        try {
                            File cacheDir = getCacheDir();
                            targetFile = new File(cacheDir, "fake_video_temp.mp4");
                            try (InputStream is = getContentResolver().openInputStream(dataUri);
                                 FileOutputStream fos = new FileOutputStream(targetFile)) {
                                byte[] buffer = new byte[8192];
                                int len;
                                while ((len = is.read(buffer)) > 0) {
                                    fos.write(buffer, 0, len);
                                }
                            }
                            liveVideoFileForCloning = targetFile;
                            String displayName = dataUri.getLastPathSegment();
                            if (displayName == null || displayName.trim().isEmpty()) {
                                displayName = targetFile.getName();
                            }
                            liveVideoDisplayName = displayName;
                            Toast.makeText(this, "Video file selected: " + displayName, Toast.LENGTH_SHORT).show();
                        } catch (IOException e) {
                            Log.e(TAG, "Error copying selected video file", e);
                            Toast.makeText(this, "Error selecting video file: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            if (targetFile != null && targetFile.exists() && !targetFile.delete()) {
                                Log.w(TAG, "Failed to delete temporary video file after error: " + targetFile);
                            }
                            liveVideoFileForCloning = null;
                            liveVideoDisplayName = null;
                        }
                        refreshLiveVideoUi();
                    }
                }
            });
    private final ActivityResultLauncher<Intent> uninstallLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                int resultCode = res.getResultCode();
                Intent data = res.getData();
                String reportedPackage = data != null ? data.getStringExtra(Intent.EXTRA_PACKAGE_NAME) : null;
                String targetPackage = reportedPackage != null ? reportedPackage : pendingUninstallPackageName;
                String targetLabel = pendingUninstallAppName;

                pendingUninstallPackageName = null;
                pendingUninstallAppName = null;

                if (resultCode == Activity.RESULT_OK) {
                    String displayName = targetLabel != null ? targetLabel : targetPackage;
                    if (statusTxt != null) {
                        statusTxt.setText(displayName != null
                                ? "Uninstalled " + displayName
                                : "Uninstall completed");
                    }
                    Toast.makeText(MainActivity.this,
                            displayName != null ? "Uninstalled " + displayName : "Uninstall completed",
                            Toast.LENGTH_SHORT).show();
                } else if (resultCode == Activity.RESULT_CANCELED) {
                    String displayName = targetLabel != null ? targetLabel : targetPackage;
                    if (statusTxt != null) {
                        statusTxt.setText("Uninstall canceled");
                    }
                    Toast.makeText(MainActivity.this,
                            displayName != null ? "Uninstall canceled for " + displayName : "Uninstall canceled",
                            Toast.LENGTH_SHORT).show();
                } else {
                    String displayName = targetLabel != null ? targetLabel : targetPackage;
                    if (statusTxt != null) {
                        statusTxt.setText(displayName != null
                                ? "Uninstall failed for " + displayName
                                : "Uninstall failed");
                    }
                    Toast.makeText(MainActivity.this,
                            displayName != null ? "Failed to uninstall " + displayName : "Uninstall failed",
                            Toast.LENGTH_LONG).show();
                }

                loadInstalledApplications();
            });
    private final ActivityResultLauncher<Intent> categorySettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                // Reload settings after returning from category settings
                if (res.getResultCode() == Activity.RESULT_OK && currentSettingsFile != null && currentSettingsFile.exists()) {
                    try {
                        currentSettingsJson = new JSONObject(readString(currentSettingsFile));
                        if (currentSettingsAdapter != null) {
                            currentSettingsAdapter.notifyDataSetChanged();
                        }
                    } catch (IOException | JSONException e) {
                        Log.e(TAG, "Error reloading settings after category edit", e);
                    }
                }
            });
    private Uri pendingInstallApkUri = null;
    private String pendingUninstallForInstallPackage = null;
    private final ActivityResultLauncher<Intent> installAfterUninstallLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), res -> {
                // After uninstall completes, proceed with installation
                if (pendingInstallApkUri != null) {
                    proceedWithInstallation(pendingInstallApkUri);
                    pendingInstallApkUri = null;
                }
                pendingUninstallForInstallPackage = null;
            });
    private boolean receiverRegistered = false;
    
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        initViews();
        setupTabs();
        loadInstalledApplications();
        setupClickListeners();
        clonerJsonFile = new File(getCacheDir(), "cloner.json");
        IntentFilter filter = new IntentFilter("com.appcloner.replica.EXPORT_COMPLETED");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(exportResultReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(exportResultReceiver, filter);
        }
        receiverRegistered = true;
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiverRegistered) {
            try {
                unregisterReceiver(exportResultReceiver);
                receiverRegistered = false;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Receiver not registered", e);
            }
        }
    }

    private void showEditorDialogForSetting(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        if (setting == null) {
            return;
        }

        if (BUNDLE_DATA_SETTING_KEY.equals(setting.key)) {
            showBundleDataDialog(setting);
            return;
        }

        if (LIVE_VIDEO_SETTING_KEY.equals(setting.key)) {
            showLiveVideoDialog(setting);
            return;
        }

        if (CLONING_MODE_KEY.equals(setting.key)) {
            showCloningModeDialog(setting, json, adapter);
            return;
        }

        if (setting.isGroup()) {
            showGroupedSettingsDialog(setting, json, adapter);
            return;
        }

        if ("android_id".equals(setting.key)) {
            showAndroidIdDialog(setting, json, adapter);
            return;
        }

        if ("wifi_mac".equals(setting.key)) {
            showWifiMacDialog(setting, json, adapter);
            return;
        }
        
        if ("icon_color".equals(setting.key)) {
            showIconColorDialog(setting, json, adapter);
            return;
        }
        
        if ("icon_rotation".equals(setting.key)) {
            showIconRotationDialog(setting, json, adapter);
            return;
        }
        
        if ("icon_badge".equals(setting.key)) {
            showIconBadgeDialog(setting, json, adapter);
            return;
        }

        if ("build_props_device_preset".equals(setting.key)) {
            showDevicePresetDialog(setting, json, adapter);
            return;
        }

        if ("bluetooth_mac".equals(setting.key)) {
            showBluetoothMacDialog(setting, json, adapter);
            return;
        }

        if ("imei".equals(setting.key)) {
            showImeiDialog(setting, json, adapter);
            return;
        }

        if ("serial_number".equals(setting.key)) {
            showSerialDialog(setting, json, adapter);
            return;
        }

        if ("user_agent".equals(setting.key)) {
            showUserAgentDialog(setting, json, adapter);
            return;
        }

        if ("imsi".equals(setting.key)) {
            showImsiDialog(setting, json, adapter);
            return;
        }

        if (setting.key != null && setting.key.startsWith("mock_") && setting.key.endsWith("_connection")) {
            showMockNetworkDialog(setting, json, adapter);
            return;
        }

        Object value = setting.value;
        if (value instanceof Boolean) {
            showBooleanEditorDialog(setting, json, adapter);
        } else if (value instanceof JSONObject) {
            showJsonObjectEditorDialog(setting, json, adapter);
        } else {
            showTextEditorDialog(setting, json, adapter);
        }
    }

    /**
     * Shows a modern options selection dialog with radio buttons like App Cloner.
     * Used for mock network connection settings and similar options.
     */
    private void showMockNetworkDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        String displayName = formatSettingName(setting.key);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(displayName);

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        // Create container with radio buttons like App Cloner style
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, 0);

        // Options array with display names and values
        final String[][] options = {
            {"No change", ""},
            {"Connected", "CONNECTED"},
            {"Disconnected", "DISCONNECTED"}
        };

        String currentVal = (setting.value != null && !JSONObject.NULL.equals(setting.value)) ? setting.value.toString() : "";

        final RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        for (int i = 0; i < options.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(options[i][0]);
            rb.setTag(options[i][1]);
            rb.setTextSize(16);
            rb.setPadding((int)(8 * density), (int)(12 * density), (int)(8 * density), (int)(12 * density));
            radioGroup.addView(rb);
            
            // Check the current selection
            if (options[i][1].equalsIgnoreCase(currentVal) || 
                (options[i][1].isEmpty() && (currentVal.isEmpty() || "null".equalsIgnoreCase(currentVal)))) {
                radioGroup.check(rb.getId());
            }
        }

        container.addView(radioGroup);
        builder.setView(container);

        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                int checkedId = radioGroup.getCheckedRadioButtonId();
                View selectedView = radioGroup.findViewById(checkedId);
                String val = null;
                if (selectedView != null && selectedView.getTag() != null) {
                    String tagVal = (String) selectedView.getTag();
                    if (!tagVal.isEmpty()) {
                        val = tagVal;
                    }
                }

                if (val == null || val.isEmpty()) {
                    setting.value = JSONObject.NULL;
                    json.put(setting.key, JSONObject.NULL);
                } else {
                    setting.value = val;
                    setting.valueClass = String.class;
                    json.put(setting.key, val);
                }
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving mock network setting", e);
            }
        });
        builder.show();
    }

    private void showBooleanEditorDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        String displayName = formatSettingName(setting.key);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(displayName);

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        final com.google.android.material.checkbox.MaterialCheckBox checkBox = new com.google.android.material.checkbox.MaterialCheckBox(this);
        checkBox.setText(displayName);
        boolean current = setting.value instanceof Boolean && (Boolean) setting.value;
        checkBox.setChecked(current);
        
        container.addView(checkBox);
        builder.setView(container);

        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                boolean newValue = checkBox.isChecked();
                setting.value = newValue;
                setting.valueClass = Boolean.class;
                json.put(setting.key, newValue);
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving boolean setting: " + setting.key, e);
            }
        });

        builder.show();
    }

    private void showTextEditorDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        String displayName = formatSettingName(setting.key);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(displayName);

        FrameLayout container = new FrameLayout(this);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding / 2, padding, 0);

        final com.google.android.material.textfield.TextInputLayout inputLayout = new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Enter value for " + displayName);
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(this);
        Object value = setting.value;
        String currentValue = "";
        if (value != null && !JSONObject.NULL.equals(value)) {
            currentValue = value.toString();
        }
        input.setText(currentValue);

        if ("custom_build_props".equals(setting.key)) {
            input.setSingleLine(false);
            input.setMaxLines(20);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            input.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            input.setMinLines(5);
        } else {
            input.setSingleLine(true);
        }
        
        final Class<?> valueClass = setting.valueClass != null ? setting.valueClass : inferValueClass(value);
        if (Number.class.isAssignableFrom(valueClass)) {
            if (valueClass == Integer.class || valueClass == Long.class) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            } else {
                input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            }
        }
        
        inputLayout.addView(input);
        container.addView(inputLayout);
        builder.setView(container);

        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                String text = input.getText() != null ? input.getText().toString().trim() : "";
                Object newValue;
                if (text.isEmpty()) {
                    newValue = JSONObject.NULL;
                } else if (Number.class.isAssignableFrom(valueClass)) {
                    if (valueClass == Integer.class) {
                        newValue = Integer.parseInt(text);
                    } else if (valueClass == Long.class) {
                        newValue = Long.parseLong(text);
                    } else if (valueClass == Float.class) {
                        newValue = Float.parseFloat(text);
                    } else {
                        newValue = Double.parseDouble(text);
                    }
                } else {
                    newValue = text;
                }

                setting.value = newValue;
                if (newValue != JSONObject.NULL) {
                    setting.valueClass = newValue.getClass();
                    json.put(setting.key, newValue);
                } else {
                    json.put(setting.key, JSONObject.NULL);
                }
                adapter.notifyDataSetChanged();
            } catch (NumberFormatException e) {
                Toast.makeText(MainActivity.this, "Invalid value. Please check your input.", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving text setting: " + setting.key, e);
            }
        });
        
        builder.show();
    }

    private void showGroupedSettingsDialog(final Setting groupSetting, final JSONObject json, final BaseAdapter adapter) {
        if (groupSetting == null || !groupSetting.isGroup()) {
            return;
        }

        // Category groups are shown via a dedicated list dialog for App Cloner-like UX
        if (groupSetting.key != null && groupSetting.key.startsWith("cat_")) {
            showCategoryDetailDialog(groupSetting, json, adapter);
            return;
        }

        // Special handling for Network & Proxy category
        if ("cat_network".equals(groupSetting.key)) {
            showNetworkProxyDialog(groupSetting, json, adapter);
            return;
        }

        final String title = formatGroupTitle(groupSetting.key);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (24 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding / 2, padding, 0);
        scrollView.addView(container);

        final Map<String, View> inputs = new LinkedHashMap<>();

        List<Setting> orderedChildren = new ArrayList<>(groupSetting.children);
        sortGroupChildren(groupSetting.key, orderedChildren);

        Setting toggleSetting = null;
        for (Setting child : orderedChildren) {
            if (isToggleKey(groupSetting.key, child.key)) {
                toggleSetting = child;
                break;
            }
        }

        if (toggleSetting != null) {
            com.google.android.material.checkbox.MaterialCheckBox enableCheckBox = new com.google.android.material.checkbox.MaterialCheckBox(this);
            enableCheckBox.setText("Enable " + title);
            boolean checked = toggleSetting.value instanceof Boolean && (Boolean) toggleSetting.value;
            enableCheckBox.setChecked(checked);
            container.addView(enableCheckBox);
            inputs.put(toggleSetting.key, enableCheckBox);
        }

        for (Setting child : orderedChildren) {
            if (toggleSetting != null && toggleSetting.key.equals(child.key)) {
                continue;
            }

            Object rawValue = child.value;
            String suffix = getChildSuffix(groupSetting.key, child.key);
            String labelText = formatChildLabel(groupSetting.key, suffix);

            // Special handling for cloning_mode
            if ("cloning_mode".equals(child.key)) {
                TextView modeLabel = new TextView(this);
                modeLabel.setText(labelText);
                modeLabel.setPadding(0, (int)(16*density), 0, (int)(8*density));
                container.addView(modeLabel);

                RadioGroup radioGroup = new RadioGroup(this);
                radioGroup.setOrientation(RadioGroup.VERTICAL);

                String[] modes = {CLONING_MODE_REPLACE, CLONING_MODE_GENERATE, CLONING_MODE_CUSTOM};
                String[] labels = {"Replace original", "Generate new package", "Custom package name"};

                String currentMode = rawValue instanceof String ? (String) rawValue : CLONING_MODE_REPLACE;

                for (int i=0; i<modes.length; i++) {
                    RadioButton rb = new RadioButton(this);
                    rb.setText(labels[i]);
                    rb.setTag(modes[i]);
                    radioGroup.addView(rb);
                    if (modes[i].equals(currentMode)) {
                        radioGroup.check(rb.getId());
                    }
                }
                container.addView(radioGroup);
                inputs.put(child.key, radioGroup);
                continue;
            }

            if ("accessible_data_dir_mode".equals(child.key)) {
                TextView modeLabel = new TextView(this);
                modeLabel.setText(labelText);
                modeLabel.setPadding(0, (int) (16 * density), 0, (int) (4 * density));
                container.addView(modeLabel);

                Spinner modeSpinner = new Spinner(this);
                String[] modeValues = {"READ_ONLY", "READ_WRITE", "NORMAL"};
                String[] modeLabels = {
                        "Read only (recommended)",
                        "Read & write (full access)",
                        "Normal (no permission changes)"
                };
                ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                        android.R.layout.simple_spinner_item,
                        Arrays.asList(modeLabels));
                modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                modeSpinner.setAdapter(modeAdapter);

                String currentModeValue = rawValue instanceof String
                        ? (String) rawValue
                        : CloneSettings.DEFAULT_ACCESSIBLE_DATA_DIR_MODE;
                String normalizedMode = currentModeValue == null ? ""
                        : currentModeValue.trim().toUpperCase(Locale.US).replace('-', '_');
                int selectedIndex = 0;
                for (int i = 0; i < modeValues.length; i++) {
                    if (modeValues[i].equals(normalizedMode)) {
                        selectedIndex = i;
                        break;
                    }
                }
                modeSpinner.setSelection(selectedIndex);
                modeSpinner.setTag(modeValues);
                container.addView(modeSpinner);
                inputs.put(child.key, modeSpinner);
                continue;
            }

            if (rawValue instanceof Boolean) {
                com.google.android.material.checkbox.MaterialCheckBox checkBox = new com.google.android.material.checkbox.MaterialCheckBox(this);
                checkBox.setText(labelText);
                boolean checked = (Boolean) rawValue;
                checkBox.setChecked(checked);
                container.addView(checkBox);
                inputs.put(child.key, checkBox);
            } else {
                LinearLayout inputRow = new LinearLayout(this);
                inputRow.setOrientation(LinearLayout.HORIZONTAL);
                inputRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.topMargin = (int) (8 * density);
                inputRow.setLayoutParams(rowParams);

                com.google.android.material.textfield.TextInputLayout inputLayout = new com.google.android.material.textfield.TextInputLayout(this);
                inputLayout.setHint(labelText);
                inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                        0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                inputLayout.setLayoutParams(layoutParams);

                com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(this);
                input.setSingleLine(true);
                String text = "";
                if (rawValue != null && !JSONObject.NULL.equals(rawValue)) {
                    text = rawValue.toString();
                }
                input.setText(text);
                Class<?> valueClass = child.valueClass != null ? child.valueClass : inferValueClass(rawValue);
                if (Number.class.isAssignableFrom(valueClass)) {
                    if (valueClass == Integer.class || valueClass == Long.class) {
                        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    } else {
                        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                    }
                }
                inputLayout.addView(input);
                inputRow.addView(inputLayout);

                // Add generator buttons for specific fields
                if ("android_id".equals(child.key) || "wifi_mac".equals(child.key) ||
                    "bluetooth_mac".equals(child.key) ||
                    "imei".equals(child.key) || "imsi".equals(child.key) ||
                    "serial_number".equals(child.key) ||
                    "SpoofLocationLatitude".equals(child.key) || "SpoofLocationLongitude".equals(child.key) ||
                    "latitude".equals(child.key) || "longitude".equals(child.key)) {

                    ImageButton generateBtn = new ImageButton(this);
                    generateBtn.setImageResource(android.R.drawable.ic_menu_rotate);
                    generateBtn.setBackground(null);
                    generateBtn.setPadding((int)(8*density), 0, (int)(8*density), 0);
                    generateBtn.setOnClickListener(v -> {
                        String newVal = "";
                        if ("android_id".equals(child.key)) newVal = randomAndroidId().toUpperCase(Locale.US);
                        else if ("wifi_mac".equals(child.key)) newVal = randomMac().toUpperCase(Locale.US);
                        else if ("bluetooth_mac".equals(child.key)) newVal = CloneSettings.generateRandomMac().toUpperCase(Locale.US);
                        else if ("imei".equals(child.key)) newVal = CloneSettings.generateRandomImei();
                        else if ("imsi".equals(child.key)) newVal = CloneSettings.generateRandomImsi();
                        else if ("serial_number".equals(child.key)) newVal = CloneSettings.generateRandomSerialNumber().toUpperCase(Locale.US);
                        else if (child.key.toLowerCase(Locale.US).contains("latitude")) newVal = randomLatitude();
                        else if (child.key.toLowerCase(Locale.US).contains("longitude")) newVal = randomLongitude();

                        input.setText(newVal);
                    });
                    inputRow.addView(generateBtn);
                }

                // Add preset selection button for Device Profile
                if ("build_props_device_preset".equals(child.key)) {
                    ImageButton presetBtn = new ImageButton(this);
                    presetBtn.setImageResource(android.R.drawable.ic_menu_more);
                    presetBtn.setBackground(null);
                    presetBtn.setPadding((int)(8*density), 0, (int)(8*density), 0);
                    presetBtn.setOnClickListener(v -> {
                        String[][] presets = CloneSettings.getDevicePresets();
                        String[] displayNames = new String[presets.length];
                        for (int i = 0; i < presets.length; i++) {
                            displayNames[i] = presets[i][1];
                        }

                        new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Select Device Preset")
                            .setSingleChoiceItems(displayNames, -1, (dialog, which) -> {
                                String selectedKey = presets[which][0];
                                input.setText(selectedKey);

                                // Auto-fill other fields based on preset
                                CloneSettings.DeviceProfile profile = CloneSettings.getDeviceProfile(selectedKey);
                                if (profile != null) {
                                    try {
                                        json.put("build_MANUFACTURER", profile.manufacturer);
                                        json.put("build_MODEL", profile.model);
                                        json.put("build_BRAND", profile.brand);
                                        json.put("build_DEVICE", profile.device);
                                        json.put("build_PRODUCT", profile.product);
                                        json.put("build_FINGERPRINT", profile.fingerprint);

                                        // Update the setting objects in the adapter list as well
                                        // This ensures that when the adapter refreshes, it has the new values
                                        for (Setting s : groupSetting.children) {
                                            if ("build_MANUFACTURER".equals(s.key)) { s.value = profile.manufacturer; s.valueClass = String.class; }
                                            else if ("build_MODEL".equals(s.key)) { s.value = profile.model; s.valueClass = String.class; }
                                            else if ("build_BRAND".equals(s.key)) { s.value = profile.brand; s.valueClass = String.class; }
                                            else if ("build_DEVICE".equals(s.key)) { s.value = profile.device; s.valueClass = String.class; }
                                            else if ("build_PRODUCT".equals(s.key)) { s.value = profile.product; s.valueClass = String.class; }
                                            else if ("build_FINGERPRINT".equals(s.key)) { s.value = profile.fingerprint; s.valueClass = String.class; }
                                        }

                                        // Try to update visible input fields immediately if they exist in this dialog
                                        if (inputs.containsKey("build_MANUFACTURER")) ((EditText)inputs.get("build_MANUFACTURER")).setText(profile.manufacturer);
                                        if (inputs.containsKey("build_MODEL")) ((EditText)inputs.get("build_MODEL")).setText(profile.model);
                                        if (inputs.containsKey("build_BRAND")) ((EditText)inputs.get("build_BRAND")).setText(profile.brand);
                                        if (inputs.containsKey("build_DEVICE")) ((EditText)inputs.get("build_DEVICE")).setText(profile.device);
                                        if (inputs.containsKey("build_PRODUCT")) ((EditText)inputs.get("build_PRODUCT")).setText(profile.product);
                                        if (inputs.containsKey("build_FINGERPRINT")) ((EditText)inputs.get("build_FINGERPRINT")).setText(profile.fingerprint);

                                        adapter.notifyDataSetChanged();
                                    } catch (JSONException e) {
                                        Log.e(TAG, "Error applying device preset", e);
                                    }
                                }

                                dialog.dismiss();
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    });
                    inputRow.addView(presetBtn);
                }

                container.addView(inputRow);
                inputs.put(child.key, input);
            }
        }

        if (inputs.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No configurable values available.");
            emptyView.setPadding(0, (int)(16 * density), 0, (int)(16 * density));
            container.addView(emptyView);
        }

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                for (Setting child : groupSetting.children) {
                    View inputView = inputs.get(child.key);
                    if (inputView == null) {
                        continue;
                    }
                    if (inputView instanceof CheckBox) {
                        boolean checked = ((CheckBox) inputView).isChecked();
                        child.value = checked;
                        child.valueClass = Boolean.class;
                        json.put(child.key, checked);
                    } else if (inputView instanceof RadioGroup) {
                        RadioGroup rg = (RadioGroup) inputView;
                        int id = rg.getCheckedRadioButtonId();
                        View rb = rg.findViewById(id);
                        if (rb != null && rb.getTag() != null) {
                            String val = (String) rb.getTag();
                            child.value = val;
                            child.valueClass = String.class;
                            json.put(child.key, val);
                        }
                    } else if (inputView instanceof Spinner) {
                        Spinner spinner = (Spinner) inputView;
                        String value = null;
                        Object tag = spinner.getTag();
                        if (tag instanceof String[]) {
                            String[] values = (String[]) tag;
                            int pos = spinner.getSelectedItemPosition();
                            if (pos >= 0 && pos < values.length) {
                                value = values[pos];
                            }
                        }
                        if (value == null) {
                            Object selectedItem = spinner.getSelectedItem();
                            value = selectedItem != null ? selectedItem.toString() : "";
                        }
                        if (value == null) {
                            value = "";
                        }
                        child.value = value;
                        child.valueClass = String.class;
                        json.put(child.key, value);
                    } else if (inputView instanceof EditText) {
                        EditText editText = (EditText) inputView;
                        String text = editText.getText() != null ? editText.getText().toString().trim() : "";
                        Class<?> targetClass = child.valueClass != null ? child.valueClass : inferValueClass(child.value);
                        Object newValue;
                        if (text.isEmpty()) {
                            newValue = JSONObject.NULL;
                        } else if (Number.class.isAssignableFrom(targetClass)) {
                            if (targetClass == Integer.class) {
                                newValue = Integer.parseInt(text);
                            } else if (targetClass == Long.class) {
                                newValue = Long.parseLong(text);
                            } else if (targetClass == Float.class) {
                                newValue = Float.parseFloat(text);
                            } else {
                                newValue = Double.parseDouble(text);
                            }
                        } else {
                            newValue = text;
                        }

                        child.value = newValue;
                        if (newValue != JSONObject.NULL) {
                            child.valueClass = newValue.getClass();
                            json.put(child.key, newValue);
                        } else {
                            json.put(child.key, JSONObject.NULL);
                        }
                    }
                }
                adapter.notifyDataSetChanged();
            } catch (NumberFormatException ex) {
                Toast.makeText(MainActivity.this, "Invalid value. Please check your input.", Toast.LENGTH_SHORT).show();
            } catch (JSONException ex) {
                Log.e(TAG, "Error saving grouped setting: " + groupSetting.key, ex);
                Toast.makeText(MainActivity.this, "Failed to save settings.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    /**
     * Shows the Identity Notifications dialog that allows users to generate
     * random values for Android ID, IMEI, IMSI, Serial Number, and MAC addresses.
     * When confirmed, the values are applied to the current clone settings.
     * For cloned apps, the dialog also provides options to clear cache/data
     * and restart the app immediately.
     */
    private void showIdentityNotificationsDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Generate New Device Identity");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        // Description text
        TextView descText = new TextView(this);
        String descMessage = currentSettingsIsClonedApp 
            ? "Generate random device identifiers and apply them to the cloned app. You can also clear cache/data and restart the app."
            : "Generate random device identifiers to create a new virtual identity for the cloned app. These values will be used when the app is rebuilt.";
        descText.setText(descMessage);
        descText.setTextSize(14);
        descText.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        descParams.bottomMargin = (int)(16 * density);
        descText.setLayoutParams(descParams);
        container.addView(descText);

        // Generate new random values
        final String newAndroidId = CloneSettings.generateRandomAndroidId();
        final String newImei = CloneSettings.generateRandomImei();
        final String newImsi = CloneSettings.generateRandomImsi();
        final String newSerial = CloneSettings.generateRandomSerialNumber();
        final String newWifiMac = CloneSettings.generateRandomMac();
        final String newBluetoothMac = CloneSettings.generateRandomMac();

        // Create identity items display
        String[][] identityItems = {
            {"Android ID", newAndroidId},
            {"IMEI", newImei},
            {"IMSI", newImsi},
            {"Serial Number", newSerial},
            {"Wi-Fi MAC", newWifiMac},
            {"Bluetooth MAC", newBluetoothMac}
        };

        for (String[] item : identityItems) {
            LinearLayout itemLayout = new LinearLayout(this);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            itemParams.bottomMargin = (int)(12 * density);
            itemLayout.setLayoutParams(itemParams);

            // Label
            TextView labelView = new TextView(this);
            labelView.setText(item[0]);
            labelView.setTextSize(12);
            labelView.setTextColor(getResources().getColor(R.color.colorPrimary, null));
            labelView.setTypeface(labelView.getTypeface(), android.graphics.Typeface.BOLD);
            itemLayout.addView(labelView);

            // Value
            TextView valueView = new TextView(this);
            valueView.setText(item[1]);
            valueView.setTextSize(14);
            valueView.setTextColor(getResources().getColor(android.R.color.primary_text_light, null));
            valueView.setTypeface(android.graphics.Typeface.MONOSPACE);
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            valueParams.topMargin = (int)(2 * density);
            valueView.setLayoutParams(valueParams);
            itemLayout.addView(valueView);

            container.addView(itemLayout);
        }

        // Divider
        View divider = new View(this);
        divider.setBackgroundColor(getResources().getColor(R.color.divider, null));
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, (int)(1 * density));
        dividerParams.topMargin = (int)(8 * density);
        dividerParams.bottomMargin = (int)(16 * density);
        divider.setLayoutParams(dividerParams);
        container.addView(divider);

        // Options section (for cloned apps)
        final CheckBox clearCacheCheckBox;
        final CheckBox clearDataCheckBox;
        final CheckBox restartAppCheckBox;

        if (currentSettingsIsClonedApp && currentSettingsApp != null) {
            // Section header for options
            TextView optionsHeader = new TextView(this);
            optionsHeader.setText("Options for Cloned App");
            optionsHeader.setTextSize(14);
            optionsHeader.setTypeface(optionsHeader.getTypeface(), android.graphics.Typeface.BOLD);
            optionsHeader.setTextColor(getResources().getColor(R.color.colorPrimary, null));
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            headerParams.bottomMargin = (int)(8 * density);
            optionsHeader.setLayoutParams(headerParams);
            container.addView(optionsHeader);

            // Clear Cache checkbox
            clearCacheCheckBox = new CheckBox(this);
            clearCacheCheckBox.setText("Clear app cache");
            clearCacheCheckBox.setChecked(true);
            container.addView(clearCacheCheckBox);

            // Clear Data checkbox
            clearDataCheckBox = new CheckBox(this);
            clearDataCheckBox.setText("Clear app data (SharedPreferences, databases)");
            clearDataCheckBox.setChecked(false);
            container.addView(clearDataCheckBox);

            // Restart App checkbox
            restartAppCheckBox = new CheckBox(this);
            restartAppCheckBox.setText("Restart app after applying");
            restartAppCheckBox.setChecked(true);
            container.addView(restartAppCheckBox);

            // Warning text for clear data
            TextView warningText = new TextView(this);
            warningText.setText("⚠️ Clearing app data will reset all app settings and login state.");
            warningText.setTextSize(11);
            warningText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark, null));
            LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            warningParams.topMargin = (int)(8 * density);
            warningParams.bottomMargin = (int)(8 * density);
            warningText.setLayoutParams(warningParams);
            container.addView(warningText);
        } else {
            clearCacheCheckBox = null;
            clearDataCheckBox = null;
            restartAppCheckBox = null;
        }

        // Info text about when changes take effect
        TextView infoText = new TextView(this);
        if (currentSettingsIsClonedApp) {
            infoText.setText("Note: Identity values will be sent to the cloned app. The app needs to be restarted for changes to take full effect.");
        } else {
            infoText.setText("Note: These values will be applied to the Device Identity settings. Rebuild the app to use the new identity.");
        }
        infoText.setTextSize(12);
        infoText.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
        infoText.setAlpha(0.8f);
        container.addView(infoText);

        builder.setView(scrollView);
        builder.setIcon(R.drawable.ic_identity);
        
        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Regenerate", null); // Will be handled separately
        builder.setPositiveButton("Apply", (dialog, which) -> {
            // Apply the generated values to current settings
            applyGeneratedIdentity(newAndroidId, newImei, newImsi, newSerial, newWifiMac, newBluetoothMac);
            
            // If this is a cloned app, send regeneration request via broadcast
            if (currentSettingsIsClonedApp && currentSettingsApp != null) {
                boolean clearCache = clearCacheCheckBox != null && clearCacheCheckBox.isChecked();
                boolean clearData = clearDataCheckBox != null && clearDataCheckBox.isChecked();
                boolean restartApp = restartAppCheckBox != null && restartAppCheckBox.isChecked();
                
                sendIdentityRegenerationBroadcast(
                    currentSettingsApp.packageName,
                    newAndroidId, newImei, newImsi, newSerial, newWifiMac, newBluetoothMac,
                    clearCache, clearData, restartApp
                );
                
                Toast.makeText(MainActivity.this, "Identity regeneration request sent to " + currentSettingsApp.appName, Toast.LENGTH_SHORT).show();
                statusTxt.setText("Identity regeneration sent - app will restart");
            } else {
                Toast.makeText(MainActivity.this, "New identity values applied to settings!", Toast.LENGTH_SHORT).show();
                statusTxt.setText("Identity values updated - rebuild app to apply");
            }
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.setOnShowListener(d -> {
            // Handle Regenerate button to regenerate all values
            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                alertDialog.dismiss();
                showIdentityNotificationsDialog(); // Recursively show new dialog with new values
            });
        });
        alertDialog.show();
    }

    /**
     * Sends a broadcast to the cloned app to regenerate its device identity.
     */
    private void sendIdentityRegenerationBroadcast(String targetPackage, String androidId, 
            String imei, String imsi, String serial, String wifiMac, String bluetoothMac,
            boolean clearCache, boolean clearData, boolean restartApp) {
        Log.d(TAG, "Sending identity regeneration broadcast to: " + targetPackage);
        
        Intent intent = new Intent("com.applisto.appcloner.ACTION_REGENERATE_IDENTITY");
        intent.setPackage(targetPackage);
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        
        // Add identity values
        intent.putExtra("android_id", androidId);
        intent.putExtra("imei", imei);
        intent.putExtra("imsi", imsi);
        intent.putExtra("serial", serial);
        intent.putExtra("wifi_mac", wifiMac);
        intent.putExtra("bluetooth_mac", bluetoothMac);
        
        // Add options
        intent.putExtra("clear_cache", clearCache);
        intent.putExtra("clear_data", clearData);
        intent.putExtra("restart_app", restartApp);
        intent.putExtra("sender_package", getPackageName());
        
        try {
            sendBroadcast(intent, IPC_PERMISSION);
            Log.d(TAG, "Identity regeneration broadcast sent successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error sending identity regeneration broadcast", e);
            Toast.makeText(this, "Failed to send regeneration request: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Applies the generated identity values to the current clone settings.
     */
    private void applyGeneratedIdentity(String androidId, String imei, String imsi, 
                                         String serial, String wifiMac, String bluetoothMac) {
        try {
            // If we have current settings open, apply to that
            if (currentSettingsJson != null) {
                currentSettingsJson.put("android_id", androidId);
                currentSettingsJson.put("imei", imei);
                currentSettingsJson.put("imsi", imsi);
                currentSettingsJson.put("serial_number", serial);
                currentSettingsJson.put("wifi_mac", wifiMac);
                currentSettingsJson.put("bluetooth_mac", bluetoothMac);
                
                // Persist to file if available
                if (currentSettingsFile != null && currentSettingsFile.exists()) {
                    try (FileWriter fw = new FileWriter(currentSettingsFile)) {
                        fw.write(currentSettingsJson.toString(2));
                    }
                }
                
                // Notify adapter to refresh
                if (currentSettingsAdapter != null) {
                    currentSettingsAdapter.notifyDataSetChanged();
                }
            }
            
            // Also update the default clonerJsonFile
            if (clonerJsonFile != null) {
                JSONObject json;
                if (clonerJsonFile.exists()) {
                    json = new JSONObject(readString(clonerJsonFile));
                } else {
                    json = CloneSettings.createDefaultSettings();
                }
                
                json.put("android_id", androidId);
                json.put("imei", imei);
                json.put("imsi", imsi);
                json.put("serial_number", serial);
                json.put("wifi_mac", wifiMac);
                json.put("bluetooth_mac", bluetoothMac);
                
                try (FileWriter fw = new FileWriter(clonerJsonFile)) {
                    fw.write(json.toString(2));
                }
            }
            
            Log.d(TAG, "Applied new identity values - Android ID: " + androidId);
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error applying generated identity", e);
            Toast.makeText(this, "Error applying identity: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showAndroidIdDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Android ID");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        container.addView(modeLabel);

        final String[] modes = {"Keep Original", "Custom", "Random"};
        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        container.addView(modeSpinner);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        inputRow.setLayoutParams(rowParams);

        final EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setSingleLine(true);
        input.setHint("Enter Android ID");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(16)});
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789ABCDEF"));
        inputRow.addView(input);

        final Button generateButton = new Button(this);
        generateButton.setText("Generate");
        inputRow.addView(generateButton);

        container.addView(inputRow);

        Object rawValue = setting.value;
        String currentValue = (rawValue == null || JSONObject.NULL.equals(rawValue))
                ? CloneSettings.VALUE_MODE_NO_CHANGE
                : rawValue.toString();
        input.setText(currentValue);

        final int[] selectedMode = new int[1];
        final String[] customHolder = new String[]{""};

        if (CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 2;
        } else if (CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 0;
        } else {
            selectedMode[0] = 1;
            customHolder[0] = currentValue.toUpperCase(Locale.US);
        }
        modeSpinner.setSelection(selectedMode[0]);

        generateButton.setOnClickListener(v -> {
            String generated = randomAndroidId().toUpperCase(Locale.US);
            input.setText(generated);
            input.setSelection(generated.length());
            customHolder[0] = generated;
        });

        final boolean[] firstSelection = {true};
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int previous = selectedMode[0];
                if (!firstSelection[0] && previous == 1) {
                    customHolder[0] = input.getText().toString().trim();
                }
                selectedMode[0] = position;

                if (position == 0) {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    input.setText(CloneSettings.VALUE_MODE_NO_CHANGE);
                } else if (position == 1) {
                    input.setEnabled(true);
                    generateButton.setEnabled(true);
                    input.setText(customHolder[0]);
                    input.setSelection(input.getText().length());
                } else {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    String generated = randomAndroidId().toUpperCase(Locale.US);
                    input.setText(generated + " (random mode)");
                }
                firstSelection[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int mode = selectedMode[0];
                    if (mode == 0) {
                        setting.value = CloneSettings.VALUE_MODE_NO_CHANGE;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_NO_CHANGE);
                    } else if (mode == 2) {
                        setting.value = CloneSettings.VALUE_MODE_RANDOM;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_RANDOM);
                    } else {
                        String valueText = input.getText().toString().trim();
                        if (valueText.length() != 16) {
                            Toast.makeText(MainActivity.this, "Android ID must be 16 hex characters.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        setting.value = valueText;
                        setting.valueClass = String.class;
                        json.put(setting.key, valueText);
                    }
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving android_id", e);
                    Toast.makeText(MainActivity.this, "Failed to save Android ID.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showUserAgentDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("User Agent Settings");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        // Checkbox for "Enable Custom User-Agent"
        final CheckBox enableCheckBox = new CheckBox(this);
        enableCheckBox.setText("Enable Custom User-Agent");
        boolean isEnabled = json.optBoolean("user_agent_hook_enabled", CloneSettings.DEFAULT_USER_AGENT_HOOK_ENABLED);
        enableCheckBox.setChecked(isEnabled);
        container.addView(enableCheckBox);

        // Text input for User Agent string
        TextView label = new TextView(this);
        label.setText("Custom User Agent:");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (12 * density);
        label.setLayoutParams(labelParams);
        container.addView(label);

        final EditText input = new EditText(this);
        input.setHint("Enter User Agent String");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMaxLines(3);
        String currentUA = json.optString("user_agent", "");
        if (currentUA.isEmpty()) {
            currentUA = CloneSettings.DEFAULT_USER_AGENT;
        }
        input.setText(currentUA);
        container.addView(input);

        // Enable/disable text input based on checkbox
        input.setEnabled(isEnabled);
        label.setEnabled(isEnabled);

        enableCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            input.setEnabled(isChecked);
            label.setEnabled(isChecked);
        });

        // Add "Reset to Default" button
        Button resetButton = new Button(this);
        resetButton.setText("Reset to Default");
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.gravity = android.view.Gravity.END;
        btnParams.topMargin = (int) (8 * density);
        resetButton.setLayoutParams(btnParams);
        resetButton.setOnClickListener(v -> input.setText(CloneSettings.DEFAULT_USER_AGENT));
        container.addView(resetButton);

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                boolean enabled = enableCheckBox.isChecked();
                String uaString = input.getText().toString().trim();

                // Update user_agent_hook_enabled
                json.put("user_agent_hook_enabled", enabled);

                // Update user_agent
                json.put("user_agent", uaString);

                // Update the Setting object itself so the list reflects changes immediately
                setting.value = uaString;
                setting.valueClass = String.class;

                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving User Agent settings", e);
                Toast.makeText(MainActivity.this, "Failed to save User Agent settings.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    private void showImsiDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change IMSI");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        container.addView(modeLabel);

        final String[] modes = {"Keep Original", "Custom", "Random"};
        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        container.addView(modeSpinner);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        inputRow.setLayoutParams(rowParams);

        final EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setSingleLine(true);
        input.setHint("Enter IMSI (15 digits)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        inputRow.addView(input);

        final Button generateButton = new Button(this);
        generateButton.setText("Generate");
        inputRow.addView(generateButton);

        container.addView(inputRow);

        Object rawValue = setting.value;
        String currentValue = (rawValue == null) ? CloneSettings.VALUE_MODE_NO_CHANGE : rawValue.toString();
        input.setText(currentValue);

        final int[] selectedMode = new int[1];
        final String[] customHolder = new String[]{""};

        if (CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 2;
        } else if (CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 0;
        } else {
            selectedMode[0] = 1;
            customHolder[0] = currentValue;
        }
        modeSpinner.setSelection(selectedMode[0]);

        generateButton.setOnClickListener(v -> {
            String generated = CloneSettings.generateRandomImsi();
            input.setText(generated);
            input.setSelection(generated.length());
            customHolder[0] = generated;
        });

        final boolean[] firstSelection = {true};
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int previous = selectedMode[0];
                if (!firstSelection[0] && previous == 1) {
                    customHolder[0] = input.getText().toString().trim();
                }
                selectedMode[0] = position;

                if (position == 0) {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    input.setText(CloneSettings.VALUE_MODE_NO_CHANGE);
                } else if (position == 1) {
                    input.setEnabled(true);
                    generateButton.setEnabled(true);
                    input.setText(customHolder[0]);
                    input.setSelection(input.getText().length());
                } else {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    String generated = CloneSettings.generateRandomImsi();
                    input.setText(generated + " (random mode)");
                }
                firstSelection[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int mode = selectedMode[0];
                    if (mode == 0) {
                        setting.value = CloneSettings.VALUE_MODE_NO_CHANGE;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_NO_CHANGE);
                    } else if (mode == 2) {
                        setting.value = CloneSettings.VALUE_MODE_RANDOM;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_RANDOM);
                    } else {
                        String valueText = input.getText().toString().trim();
                        if (valueText.length() != 15 || !valueText.matches("\\d{15}")) {
                            Toast.makeText(MainActivity.this, "IMSI must be 15 digits.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        setting.value = valueText;
                        setting.valueClass = String.class;
                        json.put(setting.key, valueText);
                    }
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving IMSI", e);
                    Toast.makeText(MainActivity.this, "Failed to save IMSI.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showWifiMacDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change WiFi MAC");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        container.addView(modeLabel);

        final String[] modes = {"Keep Original", "Custom", "Random"};
        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        container.addView(modeSpinner);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        inputRow.setLayoutParams(rowParams);

        final EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setSingleLine(true);
        input.setHint("Enter WiFi MAC (XX:XX:XX:XX:XX:XX)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(17)});
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789ABCDEF:"));
        inputRow.addView(input);

        final Button generateButton = new Button(this);
        generateButton.setText("Generate");
        inputRow.addView(generateButton);

        container.addView(inputRow);

        Object rawValue = setting.value;
        String currentValue = (rawValue == null) ? CloneSettings.VALUE_MODE_NO_CHANGE : rawValue.toString();
        input.setText(currentValue.toUpperCase(Locale.US));

        final int[] selectedMode = new int[1];
        final String[] customHolder = new String[]{""};

        if (CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 2;
        } else if (CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 0;
        } else {
            selectedMode[0] = 1;
            customHolder[0] = currentValue.toUpperCase(Locale.US);
        }
        modeSpinner.setSelection(selectedMode[0]);

        generateButton.setOnClickListener(v -> {
            String generated = randomMac().toUpperCase(Locale.US);
            input.setText(generated);
            input.setSelection(generated.length());
            customHolder[0] = generated;
        });

        final boolean[] firstSelection = {true};
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int previous = selectedMode[0];
                if (!firstSelection[0] && previous == 1) {
                    customHolder[0] = input.getText().toString().trim();
                }
                selectedMode[0] = position;

                if (position == 0) {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    input.setText(CloneSettings.VALUE_MODE_NO_CHANGE);
                } else if (position == 1) {
                    input.setEnabled(true);
                    generateButton.setEnabled(true);
                    input.setText(customHolder[0]);
                    input.setSelection(input.getText().length());
                } else {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    String generated = randomMac().toUpperCase(Locale.US);
                    input.setText(generated + " (random mode)");
                }
                firstSelection[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // no-op
            }
        });

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int mode = selectedMode[0];
                    if (mode == 0) {
                        setting.value = CloneSettings.VALUE_MODE_NO_CHANGE;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_NO_CHANGE);
                    } else if (mode == 2) {
                        setting.value = CloneSettings.VALUE_MODE_RANDOM;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_RANDOM);
                    } else {
                        String valueText = input.getText().toString().trim().toUpperCase(Locale.US);
                        if (valueText.length() != 17 || !valueText.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
                            Toast.makeText(MainActivity.this, "WiFi MAC must be 17 characters in format XX:XX:XX:XX:XX:XX (hex).", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        setting.value = valueText;
                        setting.valueClass = String.class;
                        json.put(setting.key, valueText);
                    }
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving wifi_mac", e);
                    Toast.makeText(MainActivity.this, "Failed to save WiFi MAC.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showDevicePresetDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Device Profile");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        TextView infoText = new TextView(this);
        infoText.setText("Select a device profile to quickly configure build properties:");
        infoText.setPadding(0, 0, 0, (int)(12 * density));
        container.addView(infoText);

        // Device presets - same as BuildPropsHook
        final String[] presetKeys = {
            "",  // None/Custom
            "samsung_s24_ultra",
            "pixel_8_pro",
            "oneplus_12",
            "xiaomi_14_pro",
            "huawei_mate60_pro",
            "sony_xperia_1v",
            "oppo_find_x7_ultra",
            "vivo_x100_pro"
        };
        final String[] presetNames = {
            "None (Custom Values)",
            "Samsung Galaxy S24 Ultra",
            "Google Pixel 8 Pro",
            "OnePlus 12",
            "Xiaomi 14 Pro",
            "Huawei Mate 60 Pro",
            "Sony Xperia 1 V",
            "OPPO Find X7 Ultra",
            "Vivo X100 Pro"
        };

        String currentValue = setting.value != null ? setting.value.toString() : "";

        final RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);

        for (int i = 0; i < presetKeys.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(presetNames[i]);
            rb.setTag(presetKeys[i]);
            radioGroup.addView(rb);
            if (presetKeys[i].equals(currentValue)) {
                radioGroup.check(rb.getId());
            }
        }
        // Select first if no match
        if (radioGroup.getCheckedRadioButtonId() == -1 && radioGroup.getChildCount() > 0) {
            radioGroup.check(radioGroup.getChildAt(0).getId());
        }
        container.addView(radioGroup);

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                int checkedId = radioGroup.getCheckedRadioButtonId();
                View selectedView = radioGroup.findViewById(checkedId);
                String newValue = "";
                if (selectedView != null && selectedView.getTag() != null) {
                    newValue = (String) selectedView.getTag();
                }
                setting.value = newValue;
                setting.valueClass = String.class;
                json.put(setting.key, newValue);
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving device preset", e);
            }
        });

        builder.show();
    }

    private void showBluetoothMacDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Bluetooth MAC");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        container.addView(modeLabel);

        final String[] modes = {"Keep Original", "Custom", "Random"};
        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        container.addView(modeSpinner);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        inputRow.setLayoutParams(rowParams);

        final EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setSingleLine(true);
        input.setHint("Enter Bluetooth MAC (XX:XX:XX:XX:XX:XX)");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps(), new InputFilter.LengthFilter(17)});
        input.setKeyListener(DigitsKeyListener.getInstance("0123456789ABCDEF:"));
        inputRow.addView(input);

        final Button generateButton = new Button(this);
        generateButton.setText("Generate");
        inputRow.addView(generateButton);

        container.addView(inputRow);

        Object rawValue = setting.value;
        String currentValue = (rawValue == null) ? CloneSettings.VALUE_MODE_NO_CHANGE : rawValue.toString();
        input.setText(currentValue.toUpperCase(Locale.US));

        final int[] selectedMode = new int[1];
        final String[] customHolder = new String[]{""};

        if (CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 2;
        } else if (CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 0;
        } else {
            selectedMode[0] = 1;
            customHolder[0] = currentValue.toUpperCase(Locale.US);
        }
        modeSpinner.setSelection(selectedMode[0]);

        generateButton.setOnClickListener(v -> {
            String generated = generateRandomBluetoothMac().toUpperCase(Locale.US);
            input.setText(generated);
            input.setSelection(generated.length());
            customHolder[0] = generated;
        });

        final boolean[] firstSelection = {true};
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int previous = selectedMode[0];
                if (!firstSelection[0] && previous == 1) {
                    customHolder[0] = input.getText().toString().trim();
                }
                selectedMode[0] = position;

                if (position == 0) {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    input.setText(CloneSettings.VALUE_MODE_NO_CHANGE);
                } else if (position == 1) {
                    input.setEnabled(true);
                    generateButton.setEnabled(true);
                    input.setText(customHolder[0]);
                    input.setSelection(input.getText().length());
                } else {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    String generated = generateRandomBluetoothMac().toUpperCase(Locale.US);
                    input.setText(generated + " (random mode)");
                }
                firstSelection[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int mode = selectedMode[0];
                    if (mode == 0) {
                        setting.value = CloneSettings.VALUE_MODE_NO_CHANGE;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_NO_CHANGE);
                    } else if (mode == 2) {
                        setting.value = CloneSettings.VALUE_MODE_RANDOM;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_RANDOM);
                    } else {
                        String valueText = input.getText().toString().trim().toUpperCase(Locale.US);
                        if (valueText.length() != 17 || !valueText.matches("([0-9A-F]{2}:){5}[0-9A-F]{2}")) {
                            Toast.makeText(MainActivity.this, "Bluetooth MAC must be 17 characters in format XX:XX:XX:XX:XX:XX (hex).", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        setting.value = valueText;
                        setting.valueClass = String.class;
                        json.put(setting.key, valueText);
                    }
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving bluetooth_mac", e);
                    Toast.makeText(MainActivity.this, "Failed to save Bluetooth MAC.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showImeiDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change IMEI");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        container.addView(modeLabel);

        final String[] modes = {"Keep Original", "Custom", "Random"};
        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        container.addView(modeSpinner);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        inputRow.setLayoutParams(rowParams);

        final EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setSingleLine(true);
        input.setHint("Enter IMEI (15 digits)");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(15)});
        inputRow.addView(input);

        final Button generateButton = new Button(this);
        generateButton.setText("Generate");
        inputRow.addView(generateButton);

        container.addView(inputRow);

        Object rawValue = setting.value;
        String currentValue = (rawValue == null) ? CloneSettings.VALUE_MODE_NO_CHANGE : rawValue.toString();
        input.setText(currentValue);

        final int[] selectedMode = new int[1];
        final String[] customHolder = new String[]{""};

        if (CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 2;
        } else if (CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 0;
        } else {
            selectedMode[0] = 1;
            customHolder[0] = currentValue;
        }
        modeSpinner.setSelection(selectedMode[0]);

        generateButton.setOnClickListener(v -> {
            String generated = generateRandomImei();
            input.setText(generated);
            input.setSelection(generated.length());
            customHolder[0] = generated;
        });

        final boolean[] firstSelection = {true};
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int previous = selectedMode[0];
                if (!firstSelection[0] && previous == 1) {
                    customHolder[0] = input.getText().toString().trim();
                }
                selectedMode[0] = position;

                if (position == 0) {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    input.setText(CloneSettings.VALUE_MODE_NO_CHANGE);
                } else if (position == 1) {
                    input.setEnabled(true);
                    generateButton.setEnabled(true);
                    input.setText(customHolder[0]);
                    input.setSelection(input.getText().length());
                } else {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    String generated = generateRandomImei();
                    input.setText(generated + " (random mode)");
                }
                firstSelection[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int mode = selectedMode[0];
                    if (mode == 0) {
                        setting.value = CloneSettings.VALUE_MODE_NO_CHANGE;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_NO_CHANGE);
                    } else if (mode == 2) {
                        setting.value = CloneSettings.VALUE_MODE_RANDOM;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_RANDOM);
                    } else {
                        String valueText = input.getText().toString().trim();
                        if (valueText.length() != 15 || !valueText.matches("\\d{15}")) {
                            Toast.makeText(MainActivity.this, "IMEI must be 15 digits.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        setting.value = valueText;
                        setting.valueClass = String.class;
                        json.put(setting.key, valueText);
                    }
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving imei", e);
                    Toast.makeText(MainActivity.this, "Failed to save IMEI.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showSerialDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Serial Number");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView modeLabel = new TextView(this);
        modeLabel.setText("Mode");
        container.addView(modeLabel);

        final String[] modes = {"Keep Original", "Custom", "Random"};
        Spinner modeSpinner = new Spinner(this);
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(modeAdapter);
        container.addView(modeSpinner);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int) (12 * density);
        inputRow.setLayoutParams(rowParams);

        final EditText input = new EditText(this);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        input.setSingleLine(true);
        input.setHint("Enter Serial Number");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        inputRow.addView(input);

        final Button generateButton = new Button(this);
        generateButton.setText("Generate");
        inputRow.addView(generateButton);

        container.addView(inputRow);

        Object rawValue = setting.value;
        String currentValue = (rawValue == null) ? CloneSettings.VALUE_MODE_NO_CHANGE : rawValue.toString();
        input.setText(currentValue.toUpperCase(Locale.US));

        final int[] selectedMode = new int[1];
        final String[] customHolder = new String[]{""};

        if (CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 2;
        } else if (CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(currentValue)) {
            selectedMode[0] = 0;
        } else {
            selectedMode[0] = 1;
            customHolder[0] = currentValue.toUpperCase(Locale.US);
        }
        modeSpinner.setSelection(selectedMode[0]);

        generateButton.setOnClickListener(v -> {
            String generated = generateRandomSerial().toUpperCase(Locale.US);
            input.setText(generated);
            input.setSelection(generated.length());
            customHolder[0] = generated;
        });

        final boolean[] firstSelection = {true};
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int previous = selectedMode[0];
                if (!firstSelection[0] && previous == 1) {
                    customHolder[0] = input.getText().toString().trim();
                }
                selectedMode[0] = position;

                if (position == 0) {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    input.setText(CloneSettings.VALUE_MODE_NO_CHANGE);
                } else if (position == 1) {
                    input.setEnabled(true);
                    generateButton.setEnabled(true);
                    input.setText(customHolder[0]);
                    input.setSelection(input.getText().length());
                } else {
                    input.setEnabled(false);
                    generateButton.setEnabled(false);
                    String generated = generateRandomSerial().toUpperCase(Locale.US);
                    input.setText(generated + " (random mode)");
                }
                firstSelection[0] = false;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        builder.setView(container);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int mode = selectedMode[0];
                    if (mode == 0) {
                        setting.value = CloneSettings.VALUE_MODE_NO_CHANGE;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_NO_CHANGE);
                    } else if (mode == 2) {
                        setting.value = CloneSettings.VALUE_MODE_RANDOM;
                        setting.valueClass = String.class;
                        json.put(setting.key, CloneSettings.VALUE_MODE_RANDOM);
                    } else {
                        String valueText = input.getText().toString().trim().toUpperCase(Locale.US);
                        if (valueText.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Serial number cannot be empty.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        setting.value = valueText;
                        setting.valueClass = String.class;
                        json.put(setting.key, valueText);
                    }
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving serial_number", e);
                    Toast.makeText(MainActivity.this, "Failed to save Serial Number.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void showLiveVideoDialog(final Setting setting) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Live Video Hook File");

        liveVideoSetting = setting;

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView statusText = new TextView(this);
        container.addView(statusText);

        if (liveVideoDisplayName != null) {
            statusText.setText("Selected: " + liveVideoDisplayName);
        } else {
            statusText.setText("No video selected");
        }

        TextView hintText = new TextView(this);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = (int) (8 * density);
        hintText.setLayoutParams(hintParams);
        hintText.setText("Select an MP4 video to be used by the Live Video Hook.");
        container.addView(hintText);

        Button selectButton = new Button(this);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        selectParams.topMargin = (int) (16 * density);
        selectButton.setLayoutParams(selectParams);
        selectButton.setText("Select Video");
        container.addView(selectButton);

        Button clearButton = new Button(this);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clearParams.topMargin = (int) (12 * density);
        clearButton.setLayoutParams(clearParams);
        clearButton.setText("Remove Selection");
        container.addView(clearButton);
        clearButton.setEnabled(liveVideoFileForCloning != null);

        selectButton.setOnClickListener(v -> {
             pickVideoFile();
             // We can't easily update dialog content live without re-showing or custom view ref
             // For simplicity, dismiss and user can check main list, or rely on toast
             // But let's try to update statusText if we are inside
             // Actually, pickVideoFile is async. We rely on refreshLiveVideoUi to update adapter.
             // We'll close the dialog.
             // Or we could use a listener. For now, let's close.
             // dialog.dismiss();
             // Better: just launch picker. The onActivityResult updates state.
             // The user has to close and re-open to see change or we update the text view if we keep ref.
             // Let's just launch picker.
        });

        clearButton.setOnClickListener(v -> {
            if (liveVideoFileForCloning != null && liveVideoFileForCloning.exists() && !liveVideoFileForCloning.delete()) {
                Log.w(TAG, "Failed to delete live video file when clearing selection");
            }
            liveVideoFileForCloning = null;
            liveVideoDisplayName = null;
            Toast.makeText(MainActivity.this, "Video selection cleared", Toast.LENGTH_SHORT).show();
            statusText.setText("No video selected");
            clearButton.setEnabled(false);
            refreshLiveVideoUi();
        });

        builder.setView(container);
        builder.setNegativeButton("Close", null);

        builder.show();
    }

    private void pickVideoFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            intent.putExtra(Intent.EXTRA_TITLE, "Select Video for Hook");
            pickVideoFile.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching file picker", e);
            Toast.makeText(this, "Error opening file picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshLiveVideoUi() {
        if (liveVideoSetting != null) {
            liveVideoSetting.value = liveVideoDisplayName;
            liveVideoSetting.valueClass = String.class;
        }
        if (currentSettingsAdapter != null) {
            currentSettingsAdapter.notifyDataSetChanged();
        }
    }

    private void showBundleDataDialog(final Setting setting) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Bundle App Data");

        bundleDataSetting = setting;

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        TextView statusText = new TextView(this);
        container.addView(statusText);

        TextView hintText = new TextView(this);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = (int) (8 * density);
        hintText.setLayoutParams(hintParams);
        hintText.setText("Select a ZIP archive to package alongside the cloned APK.");
        container.addView(hintText);

        Button selectButton = new Button(this);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        selectParams.topMargin = (int) (16 * density);
        selectButton.setLayoutParams(selectParams);
        selectButton.setText("Select Data ZIP");
        container.addView(selectButton);

        Button clearButton = new Button(this);
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        clearParams.topMargin = (int) (12 * density);
        clearButton.setLayoutParams(clearParams);
        clearButton.setText("Remove Selection");
        container.addView(clearButton);

        bundleDataDialogSummary = statusText;
        bundleDataDialogClearButton = clearButton;
        statusText.setText(getBundleDataDialogStatusText());
        clearButton.setEnabled(bundledDataFileForCloning != null);

        selectButton.setOnClickListener(v -> {
            bundleDataDialogSummary = statusText;
            bundleDataDialogClearButton = clearButton;
            pickDataFile();
        });

        clearButton.setOnClickListener(v -> {
            if (bundledDataFileForCloning != null && bundledDataFileForCloning.exists() && !bundledDataFileForCloning.delete()) {
                Log.w(TAG, "Failed to delete bundled data file when clearing selection");
            }
            bundledDataFileForCloning = null;
            bundledDataDisplayName = null;
            Toast.makeText(MainActivity.this, "Bundled data cleared", Toast.LENGTH_SHORT).show();
            refreshBundleDataUi();
        });

        builder.setView(container);
        builder.setNegativeButton("Close", (dialog, which) -> {
            bundleDataDialogSummary = null;
            bundleDataDialogClearButton = null;
        });

        AlertDialog dialog = builder.create();
        dialog.setOnDismissListener(d -> {
            bundleDataDialogSummary = null;
            bundleDataDialogClearButton = null;
        });
        dialog.show();
    }

    private void showIconColorDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Icon Color & Effects");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        final Drawable originalIcon = currentSettingsApp != null ? currentSettingsApp.icon : null;
        final ImageView iconPreview;
        
        // Add live preview card
        if (originalIcon != null) {
            com.google.android.material.card.MaterialCardView previewCard = new com.google.android.material.card.MaterialCardView(this);
            previewCard.setCardElevation(4 * density);
            previewCard.setRadius(12 * density);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, (int)(16 * density));
            previewCard.setLayoutParams(cardParams);
            
            LinearLayout previewLayout = new LinearLayout(this);
            previewLayout.setOrientation(LinearLayout.VERTICAL);
            previewLayout.setGravity(android.view.Gravity.CENTER);
            previewLayout.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(12 * density));
            
            iconPreview = new ImageView(this);
            int iconSize = (int) (80 * density);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            iconPreview.setLayoutParams(iconParams);
            iconPreview.setImageDrawable(originalIcon);
            previewLayout.addView(iconPreview);
            
            TextView previewLabel = new TextView(this);
            previewLabel.setText("Live Preview");
            previewLabel.setTextSize(12);
            previewLabel.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
            previewLabel.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = (int)(8 * density);
            labelParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            previewLabel.setLayoutParams(labelParams);
            previewLayout.addView(previewLabel);
            
            previewCard.addView(previewLayout);
            container.addView(previewCard);
        } else {
            iconPreview = null;
        }

        // Color section header
        TextView colorHeader = new TextView(this);
        colorHeader.setText("Tint Color");
        colorHeader.setTypeface(colorHeader.getTypeface(), android.graphics.Typeface.BOLD);
        colorHeader.setTextSize(14);
        colorHeader.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        container.addView(colorHeader);
        
        // Color input row
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colorRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colorRowParams.topMargin = (int)(8 * density);
        colorRow.setLayoutParams(colorRowParams);
        
        // Color preview circle
        final View colorPreviewCircle = new View(this);
        int circleSize = (int)(32 * density);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(circleSize, circleSize);
        circleParams.setMarginEnd((int)(12 * density));
        colorPreviewCircle.setLayoutParams(circleParams);
        colorPreviewCircle.setBackgroundResource(R.drawable.icon_background_circle);
        String currentValue = setting.value != null ? setting.value.toString() : "#FFFFFF";
        try {
            colorPreviewCircle.getBackground().setTint(android.graphics.Color.parseColor(currentValue));
        } catch (Exception e) {
            colorPreviewCircle.getBackground().setTint(android.graphics.Color.WHITE);
        }
        colorRow.addView(colorPreviewCircle);
        
        final com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(this);
        input.setHint("#FFFFFF");
        input.setSingleLine(true);
        input.setText(currentValue);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        input.setLayoutParams(inputParams);
        colorRow.addView(input);
        container.addView(colorRow);
        
        // Color palette
        LinearLayout colorPalette = new LinearLayout(this);
        colorPalette.setOrientation(LinearLayout.HORIZONTAL);
        colorPalette.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paletteParams.topMargin = (int)(8 * density);
        paletteParams.bottomMargin = (int)(16 * density);
        colorPalette.setLayoutParams(paletteParams);
        
        String[] presetColors = {"#FFFFFF", "#FF5252", "#FF9800", "#FFEB3B", "#4CAF50", "#2196F3", "#9C27B0", "#000000"};
        for (String presetColor : presetColors) {
            View colorBtn = new View(this);
            int btnSize = (int)(28 * density);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(btnSize, btnSize);
            btnParams.setMargins((int)(4 * density), 0, (int)(4 * density), 0);
            colorBtn.setLayoutParams(btnParams);
            colorBtn.setBackgroundResource(R.drawable.icon_background_circle);
            try {
                colorBtn.getBackground().setTint(android.graphics.Color.parseColor(presetColor));
            } catch (Exception e) { }
            colorBtn.setOnClickListener(v -> {
                input.setText(presetColor);
                try {
                    colorPreviewCircle.getBackground().setTint(android.graphics.Color.parseColor(presetColor));
                } catch (Exception e) { }
            });
            colorPalette.addView(colorBtn);
        }
        container.addView(colorPalette);

        // Effects section
        TextView effectsHeader = new TextView(this);
        effectsHeader.setText("Effects");
        effectsHeader.setTypeface(effectsHeader.getTypeface(), android.graphics.Typeface.BOLD);
        effectsHeader.setTextSize(14);
        effectsHeader.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        LinearLayout.LayoutParams effectsParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        effectsParams.topMargin = (int)(8 * density);
        effectsHeader.setLayoutParams(effectsParams);
        container.addView(effectsHeader);

        final com.google.android.material.checkbox.MaterialCheckBox autoHueSwitch = new com.google.android.material.checkbox.MaterialCheckBox(this);
        autoHueSwitch.setText("Auto Hue (derive from color)");
        autoHueSwitch.setChecked(json.optBoolean("icon_auto_hue", CloneSettings.DEFAULT_ICON_AUTO_HUE));
        container.addView(autoHueSwitch);

        final com.google.android.material.checkbox.MaterialCheckBox invertColorsSwitch = new com.google.android.material.checkbox.MaterialCheckBox(this);
        invertColorsSwitch.setText("Invert Colors");
        invertColorsSwitch.setChecked(json.optBoolean("icon_invert_colors", CloneSettings.DEFAULT_ICON_INVERT_COLORS));
        container.addView(invertColorsSwitch);

        final com.google.android.material.checkbox.MaterialCheckBox sepiaSwitch = new com.google.android.material.checkbox.MaterialCheckBox(this);
        sepiaSwitch.setText("Sepia Tone");
        sepiaSwitch.setChecked(json.optBoolean("icon_sepia", CloneSettings.DEFAULT_ICON_SEPIA));
        container.addView(sepiaSwitch);

        // Adjustments section header
        TextView adjustHeader = new TextView(this);
        adjustHeader.setText("Adjustments");
        adjustHeader.setTypeface(adjustHeader.getTypeface(), android.graphics.Typeface.BOLD);
        adjustHeader.setTextSize(14);
        adjustHeader.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        LinearLayout.LayoutParams adjustParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        adjustParams.topMargin = (int)(16 * density);
        adjustHeader.setLayoutParams(adjustParams);
        container.addView(adjustHeader);

        Runnable updatePreview = () -> {
            if (iconPreview == null || originalIcon == null) {
                return;
            }

            try {
                String colorText = input.getText() != null ? input.getText().toString().trim() : "#FFFFFF";
                if (colorText.isEmpty()) {
                    colorText = "#FFFFFF";
                }
                if (!colorText.startsWith("#")) {
                    colorText = "#" + colorText;
                }
                
                // Update color preview circle
                try {
                    colorPreviewCircle.getBackground().setTint(android.graphics.Color.parseColor(colorText));
                } catch (Exception e) { }

                Number hueValue = (Number) scrollView.getTag(R.id.icon_hue_value);
                Number saturationValue = (Number) scrollView.getTag(R.id.icon_saturation_value);
                Number lightnessValue = (Number) scrollView.getTag(R.id.icon_lightness_value);

                float hue = hueValue != null ? hueValue.floatValue() : CloneSettings.DEFAULT_ICON_HUE;
                float saturation = saturationValue != null ? saturationValue.floatValue() : CloneSettings.DEFAULT_ICON_SATURATION;
                float lightness = lightnessValue != null ? lightnessValue.floatValue() : CloneSettings.DEFAULT_ICON_LIGHTNESS;

                Bitmap previewBitmap = IconProcessor.processIconDrawable(
                        originalIcon,
                        colorText,
                        json.optInt("icon_rotation", 0),
                        json.optBoolean("icon_flip_horizontal", false),
                        json.optBoolean("icon_flip_vertical", false),
                        hue,
                        saturation,
                        lightness,
                        autoHueSwitch.isChecked(),
                        invertColorsSwitch.isChecked(),
                        sepiaSwitch.isChecked(),
                        json.optString("icon_badge", ""),
                        json.optInt("icon_badge_position", 0)
                );
                iconPreview.setImageBitmap(previewBitmap);
            } catch (Exception e) {
                Log.w(TAG, "Failed to build icon preview", e);
                iconPreview.setImageDrawable(originalIcon);
            }
        };

        // Hue controls
        addSlider(container, "Hue", -180, 180,
                (float) json.optDouble("icon_hue", CloneSettings.DEFAULT_ICON_HUE), updatePreview);

        // Saturation controls
        addSlider(container, "Saturation", -100, 100,
                (float) json.optDouble("icon_saturation", CloneSettings.DEFAULT_ICON_SATURATION), updatePreview);

        // Lightness controls
        addSlider(container, "Lightness", -100, 100,
                (float) json.optDouble("icon_lightness", CloneSettings.DEFAULT_ICON_LIGHTNESS), updatePreview);

        autoHueSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());
        invertColorsSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());
        sepiaSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                updatePreview.run();
            }
        });

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String colorText = input.getText() != null ? input.getText().toString().trim() : "#FFFFFF";
                if (colorText.isEmpty()) {
                    colorText = "#FFFFFF";
                }
                if (!colorText.startsWith("#")) {
                    colorText = "#" + colorText;
                }
                Number hueValue = (Number) scrollView.getTag(R.id.icon_hue_value);
                Number saturationValue = (Number) scrollView.getTag(R.id.icon_saturation_value);
                Number lightnessValue = (Number) scrollView.getTag(R.id.icon_lightness_value);
                float hue = hueValue != null ? hueValue.floatValue() : CloneSettings.DEFAULT_ICON_HUE;
                float saturation = saturationValue != null ? saturationValue.floatValue() : CloneSettings.DEFAULT_ICON_SATURATION;
                float lightness = lightnessValue != null ? lightnessValue.floatValue() : CloneSettings.DEFAULT_ICON_LIGHTNESS;
                setting.value = colorText;
                setting.valueClass = String.class;
                json.put(setting.key, colorText);
                json.put("icon_hue", hue);
                json.put("icon_saturation", saturation);
                json.put("icon_lightness", lightness);
                json.put("icon_auto_hue", autoHueSwitch.isChecked());
                json.put("icon_invert_colors", invertColorsSwitch.isChecked());
                json.put("icon_sepia", sepiaSwitch.isChecked());
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving icon color", e);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> updatePreview.run());
        dialog.show();
    }

    private void addSlider(LinearLayout container, String labelText, int min, int max, float currentValue, Runnable onValueChanged) {
        float density = getResources().getDisplayMetrics().density;
        TextView labelView = new TextView(this);
        labelView.setText(labelText);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (16 * density);
        labelView.setLayoutParams(labelParams);
        container.addView(labelView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        final TextView valueLabel = new TextView(this);
        valueLabel.setText(String.format(Locale.getDefault(), "%d", (int) currentValue));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                (int) (48 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        row.addView(valueLabel, valueParams);

        final SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress((int) (currentValue - min));
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int value = progress + min;
                valueLabel.setText(String.valueOf(value));
                if (container.getParent() instanceof ScrollView) {
                    ScrollView scrollView = (ScrollView) container.getParent();
                    if ("Hue".equals(labelText)) {
                        scrollView.setTag(R.id.icon_hue_value, value);
                    } else if ("Saturation".equals(labelText)) {
                        scrollView.setTag(R.id.icon_saturation_value, value);
                    } else if ("Lightness".equals(labelText)) {
                        scrollView.setTag(R.id.icon_lightness_value, value);
                    }
                }

                if (onValueChanged != null) {
                    onValueChanged.run();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seekParams.leftMargin = (int) (8 * density);
        row.addView(seekBar, seekParams);

        container.addView(row);

        // Persist initial values in the ScrollView tag for retrieval when saving
        if (container.getParent() instanceof ScrollView) {
            ScrollView scrollView = (ScrollView) container.getParent();
            if ("Hue".equals(labelText) && scrollView.getTag(R.id.icon_hue_value) == null) {
                scrollView.setTag(R.id.icon_hue_value, currentValue);
            } else if ("Saturation".equals(labelText) && scrollView.getTag(R.id.icon_saturation_value) == null) {
                scrollView.setTag(R.id.icon_saturation_value, currentValue);
            } else if ("Lightness".equals(labelText) && scrollView.getTag(R.id.icon_lightness_value) == null) {
                scrollView.setTag(R.id.icon_lightness_value, currentValue);
            }
        }
    }

    private void showIconRotationDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Rotate & Flip Icon");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        final Drawable originalIcon = currentSettingsApp != null ? currentSettingsApp.icon : null;
        final ImageView iconPreview;
        
        // Add live preview card
        if (originalIcon != null) {
            com.google.android.material.card.MaterialCardView previewCard = new com.google.android.material.card.MaterialCardView(this);
            previewCard.setCardElevation(4 * density);
            previewCard.setRadius(12 * density);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, (int)(16 * density));
            previewCard.setLayoutParams(cardParams);
            
            LinearLayout previewLayout = new LinearLayout(this);
            previewLayout.setOrientation(LinearLayout.VERTICAL);
            previewLayout.setGravity(android.view.Gravity.CENTER);
            previewLayout.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(12 * density));
            
            iconPreview = new ImageView(this);
            int iconSize = (int) (80 * density);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            iconPreview.setLayoutParams(iconParams);
            iconPreview.setImageDrawable(originalIcon);
            previewLayout.addView(iconPreview);
            
            TextView previewLabel = new TextView(this);
            previewLabel.setText("Live Preview");
            previewLabel.setTextSize(12);
            previewLabel.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
            previewLabel.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = (int)(8 * density);
            labelParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            previewLabel.setLayoutParams(labelParams);
            previewLayout.addView(previewLabel);
            
            previewCard.addView(previewLayout);
            container.addView(previewCard);
        } else {
            iconPreview = null;
        }

        // Rotation slider
        TextView label = new TextView(this);
        label.setText("Rotation");
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextSize(14);
        label.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        container.addView(label);

        int currentValue = 0;
        try {
            if (setting.value instanceof Integer) {
                currentValue = (Integer) setting.value;
            } else if (setting.value != null) {
                currentValue = Integer.parseInt(setting.value.toString());
            }
        } catch (NumberFormatException e) {
            currentValue = 0;
        }

        // Rotation slider row
        LinearLayout sliderRow = new LinearLayout(this);
        sliderRow.setOrientation(LinearLayout.HORIZONTAL);
        sliderRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sliderRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sliderRowParams.topMargin = (int)(8 * density);
        sliderRowParams.bottomMargin = (int)(16 * density);
        sliderRow.setLayoutParams(sliderRowParams);
        
        final SeekBar rotationSeekBar = new SeekBar(this);
        rotationSeekBar.setMax(360);
        rotationSeekBar.setProgress(currentValue);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        rotationSeekBar.setLayoutParams(seekParams);
        
        final TextView rotationValue = new TextView(this);
        rotationValue.setText(currentValue + "°");
        rotationValue.setTextSize(14);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            (int)(48 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        valueParams.setMarginStart((int)(8 * density));
        rotationValue.setLayoutParams(valueParams);
        rotationValue.setGravity(android.view.Gravity.END);
        
        sliderRow.addView(rotationSeekBar);
        sliderRow.addView(rotationValue);
        container.addView(sliderRow);

        // Add flip options
        TextView flipLabel = new TextView(this);
        flipLabel.setText("Flip Options");
        flipLabel.setTypeface(flipLabel.getTypeface(), android.graphics.Typeface.BOLD);
        flipLabel.setTextSize(14);
        flipLabel.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        LinearLayout.LayoutParams flipLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT);
        flipLabelParams.topMargin = (int)(8 * density);
        flipLabel.setLayoutParams(flipLabelParams);
        container.addView(flipLabel);

        final com.google.android.material.checkbox.MaterialCheckBox flipHorizontalCheckbox = new com.google.android.material.checkbox.MaterialCheckBox(this);
        flipHorizontalCheckbox.setText("Flip horizontally");
        boolean flipHorizontal = json.optBoolean("icon_flip_horizontal", false);
        flipHorizontalCheckbox.setChecked(flipHorizontal);
        container.addView(flipHorizontalCheckbox);

        final com.google.android.material.checkbox.MaterialCheckBox flipVerticalCheckbox = new com.google.android.material.checkbox.MaterialCheckBox(this);
        flipVerticalCheckbox.setText("Flip vertically");
        boolean flipVertical = json.optBoolean("icon_flip_vertical", false);
        flipVerticalCheckbox.setChecked(flipVertical);
        container.addView(flipVerticalCheckbox);

        // Live preview update handler
        Runnable updatePreview = () -> {
            if (iconPreview == null || originalIcon == null) return;
            try {
                int rotation = rotationSeekBar.getProgress();
                rotationValue.setText(rotation + "°");
                
                Bitmap previewBitmap = IconProcessor.processIconDrawable(
                    originalIcon,
                    json.optString("icon_color", "#FFFFFF"),
                    rotation,
                    flipHorizontalCheckbox.isChecked(),
                    flipVerticalCheckbox.isChecked(),
                    (float) json.optDouble("icon_hue", CloneSettings.DEFAULT_ICON_HUE),
                    (float) json.optDouble("icon_saturation", CloneSettings.DEFAULT_ICON_SATURATION),
                    (float) json.optDouble("icon_lightness", CloneSettings.DEFAULT_ICON_LIGHTNESS),
                    json.optBoolean("icon_auto_hue", CloneSettings.DEFAULT_ICON_AUTO_HUE),
                    json.optBoolean("icon_invert_colors", CloneSettings.DEFAULT_ICON_INVERT_COLORS),
                    json.optBoolean("icon_sepia", CloneSettings.DEFAULT_ICON_SEPIA),
                    json.optString("icon_badge", ""),
                    json.optInt("icon_badge_position", 0)
                );
                iconPreview.setImageBitmap(previewBitmap);
            } catch (Exception e) {
                Log.w(TAG, "Failed to build rotation preview", e);
                iconPreview.setImageDrawable(originalIcon);
            }
        };
        
        rotationSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updatePreview.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        
        flipHorizontalCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());
        flipVerticalCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> updatePreview.run());

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                int newValue = rotationSeekBar.getProgress();
                setting.value = newValue;
                setting.valueClass = Integer.class;
                json.put(setting.key, newValue);
                json.put("icon_flip_horizontal", flipHorizontalCheckbox.isChecked());
                json.put("icon_flip_vertical", flipVerticalCheckbox.isChecked());
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving icon rotation", e);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> updatePreview.run());
        dialog.show();
    }

    private void showIconBadgeDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Icon Badge");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        final Drawable originalIcon = currentSettingsApp != null ? currentSettingsApp.icon : null;
        final ImageView iconPreview;
        
        // Add live preview card
        if (originalIcon != null) {
            com.google.android.material.card.MaterialCardView previewCard = new com.google.android.material.card.MaterialCardView(this);
            previewCard.setCardElevation(4 * density);
            previewCard.setRadius(12 * density);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, (int)(16 * density));
            previewCard.setLayoutParams(cardParams);
            
            LinearLayout previewLayout = new LinearLayout(this);
            previewLayout.setOrientation(LinearLayout.VERTICAL);
            previewLayout.setGravity(android.view.Gravity.CENTER);
            previewLayout.setPadding((int)(16 * density), (int)(16 * density), (int)(16 * density), (int)(12 * density));
            
            iconPreview = new ImageView(this);
            int iconSize = (int) (80 * density);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
            iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            iconPreview.setLayoutParams(iconParams);
            iconPreview.setImageDrawable(originalIcon);
            previewLayout.addView(iconPreview);
            
            TextView previewLabel = new TextView(this);
            previewLabel.setText("Live Preview");
            previewLabel.setTextSize(12);
            previewLabel.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
            previewLabel.setGravity(android.view.Gravity.CENTER);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = (int)(8 * density);
            labelParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
            previewLabel.setLayoutParams(labelParams);
            previewLayout.addView(previewLabel);
            
            previewCard.addView(previewLayout);
            container.addView(previewCard);
        } else {
            iconPreview = null;
        }

        // Badge text section
        TextView label = new TextView(this);
        label.setText("Badge Text");
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setTextSize(14);
        label.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        container.addView(label);
        
        com.google.android.material.textfield.TextInputLayout inputLayout = new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Badge text (max 3 characters)");
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        inputLayout.setCounterEnabled(true);
        inputLayout.setCounterMaxLength(3);
        
        final com.google.android.material.textfield.TextInputEditText input = new com.google.android.material.textfield.TextInputEditText(this);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        String currentValue = setting.value != null ? setting.value.toString() : "";
        input.setText(currentValue);
        inputLayout.addView(input);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = (int)(8 * density);
        inputParams.bottomMargin = (int)(16 * density);
        inputLayout.setLayoutParams(inputParams);
        container.addView(inputLayout);

        // Badge position section
        TextView posLabel = new TextView(this);
        posLabel.setText("Badge Position");
        posLabel.setTypeface(posLabel.getTypeface(), android.graphics.Typeface.BOLD);
        posLabel.setTextSize(14);
        posLabel.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        container.addView(posLabel);

        // Position buttons row
        LinearLayout positionRow = new LinearLayout(this);
        positionRow.setOrientation(LinearLayout.HORIZONTAL);
        positionRow.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams posRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        posRowParams.topMargin = (int)(12 * density);
        positionRow.setLayoutParams(posRowParams);
        
        String[] positions = {"TL", "TR", "BL", "BR"};
        String[] positionNames = {"Top Left", "Top Right", "Bottom Left", "Bottom Right"};
        int[] positionValues = {1, 0, 3, 2}; // Maps to internal position values: 0=TR, 1=TL, 2=BR, 3=BL
        final int[] selectedPosition = {json.optInt("icon_badge_position", 0)};
        
        final com.google.android.material.button.MaterialButton[] posButtons = new com.google.android.material.button.MaterialButton[4];
        
        for (int i = 0; i < positions.length; i++) {
            final int posIndex = i;
            final int posValue = positionValues[i];
            
            com.google.android.material.button.MaterialButton btn = new com.google.android.material.button.MaterialButton(this, null, 
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(positions[i]);
            btn.setTextSize(12);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                (int)(56 * density), (int)(48 * density));
            btnParams.setMargins((int)(4 * density), 0, (int)(4 * density), 0);
            btn.setLayoutParams(btnParams);
            btn.setPadding(0, 0, 0, 0);
            
            if (posValue == selectedPosition[0]) {
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    getResources().getColor(R.color.colorPrimary, null)));
                btn.setTextColor(android.graphics.Color.WHITE);
            }
            
            btn.setOnClickListener(v -> {
                selectedPosition[0] = posValue;
                for (int j = 0; j < posButtons.length; j++) {
                    if (positionValues[j] == posValue) {
                        posButtons[j].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.colorPrimary, null)));
                        posButtons[j].setTextColor(android.graphics.Color.WHITE);
                    } else {
                        posButtons[j].setBackgroundTintList(null);
                        posButtons[j].setTextColor(getResources().getColor(R.color.colorPrimary, null));
                    }
                }
            });
            
            posButtons[i] = btn;
            positionRow.addView(btn);
        }
        container.addView(positionRow);

        // Live preview update handler
        Runnable updatePreview = () -> {
            if (iconPreview == null || originalIcon == null) return;
            try {
                String badgeText = input.getText() != null ? input.getText().toString().trim() : "";
                
                Bitmap previewBitmap = IconProcessor.processIconDrawable(
                    originalIcon,
                    json.optString("icon_color", "#FFFFFF"),
                    json.optInt("icon_rotation", 0),
                    json.optBoolean("icon_flip_horizontal", false),
                    json.optBoolean("icon_flip_vertical", false),
                    (float) json.optDouble("icon_hue", CloneSettings.DEFAULT_ICON_HUE),
                    (float) json.optDouble("icon_saturation", CloneSettings.DEFAULT_ICON_SATURATION),
                    (float) json.optDouble("icon_lightness", CloneSettings.DEFAULT_ICON_LIGHTNESS),
                    json.optBoolean("icon_auto_hue", CloneSettings.DEFAULT_ICON_AUTO_HUE),
                    json.optBoolean("icon_invert_colors", CloneSettings.DEFAULT_ICON_INVERT_COLORS),
                    json.optBoolean("icon_sepia", CloneSettings.DEFAULT_ICON_SEPIA),
                    badgeText,
                    selectedPosition[0]
                );
                iconPreview.setImageBitmap(previewBitmap);
            } catch (Exception e) {
                Log.w(TAG, "Failed to build badge preview", e);
                iconPreview.setImageDrawable(originalIcon);
            }
        };
        
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) { updatePreview.run(); }
        });
        
        // Add position change listeners
        for (com.google.android.material.button.MaterialButton btn : posButtons) {
            btn.setOnClickListener(v -> {
                for (int j = 0; j < posButtons.length; j++) {
                    if (posButtons[j] == v) {
                        selectedPosition[0] = positionValues[j];
                        posButtons[j].setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            getResources().getColor(R.color.colorPrimary, null)));
                        posButtons[j].setTextColor(android.graphics.Color.WHITE);
                    } else {
                        posButtons[j].setBackgroundTintList(null);
                        posButtons[j].setTextColor(getResources().getColor(R.color.colorPrimary, null));
                    }
                }
                updatePreview.run();
            });
        }

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                String badgeText = input.getText() != null ? input.getText().toString().trim() : "";
                setting.value = badgeText;
                setting.valueClass = String.class;
                json.put(setting.key, badgeText);
                json.put("icon_badge_position", selectedPosition[0]);
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving icon badge", e);
            }
        });

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> updatePreview.run());
        dialog.show();
    }

    /**
     * Shows a unified "App Name & Icon" dialog with improved Material Design sliders,
     * color picker, and live preview of the modified icon.
     */
    private void showAppNameAndIconDialog(final Setting groupSetting, final JSONObject json, final BaseAdapter adapter) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("App Name & Icon");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        // Get original icon
        final Drawable originalIcon = currentSettingsApp != null ? currentSettingsApp.icon : null;
        
        // ===== LIVE PREVIEW SECTION =====
        com.google.android.material.card.MaterialCardView previewCard = new com.google.android.material.card.MaterialCardView(this);
        previewCard.setCardElevation(4 * density);
        previewCard.setRadius(16 * density);
        previewCard.setStrokeWidth(0);
        LinearLayout.LayoutParams previewCardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        previewCardParams.setMargins(0, 0, 0, (int)(16 * density));
        previewCard.setLayoutParams(previewCardParams);
        
        LinearLayout previewLayout = new LinearLayout(this);
        previewLayout.setOrientation(LinearLayout.VERTICAL);
        previewLayout.setGravity(android.view.Gravity.CENTER);
        previewLayout.setPadding((int)(16 * density), (int)(20 * density), (int)(16 * density), (int)(16 * density));
        
        final ImageView iconPreview = new ImageView(this);
        int iconSize = (int) (96 * density);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        iconPreview.setLayoutParams(iconParams);
        if (originalIcon != null) {
            iconPreview.setImageDrawable(originalIcon);
        } else {
            iconPreview.setImageResource(android.R.drawable.sym_def_app_icon);
        }
        previewLayout.addView(iconPreview);
        
        TextView previewLabel = new TextView(this);
        previewLabel.setText("Live Preview");
        previewLabel.setTextSize(12);
        previewLabel.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
        previewLabel.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int)(8 * density);
        labelParams.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        previewLabel.setLayoutParams(labelParams);
        previewLayout.addView(previewLabel);
        
        previewCard.addView(previewLayout);
        container.addView(previewCard);

        // ===== APP NAME SECTION =====
        addSectionHeader(container, "App Name", density);
        
        com.google.android.material.textfield.TextInputLayout appNameLayout = new com.google.android.material.textfield.TextInputLayout(this);
        appNameLayout.setHint("App Name (leave empty for original)");
        appNameLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        appNameLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        
        final com.google.android.material.textfield.TextInputEditText appNameInput = new com.google.android.material.textfield.TextInputEditText(this);
        appNameInput.setSingleLine(true);
        String currentAppName = json.optString("app_name", "");
        appNameInput.setText(currentAppName);
        appNameLayout.addView(appNameInput);
        
        LinearLayout.LayoutParams appNameParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        appNameParams.bottomMargin = (int)(16 * density);
        appNameLayout.setLayoutParams(appNameParams);
        container.addView(appNameLayout);

        // ===== ICON COLOR SECTION =====
        addSectionHeader(container, "Icon", density);
        
        // Color picker row
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams colorRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        colorRowParams.bottomMargin = (int)(12 * density);
        colorRow.setLayoutParams(colorRowParams);
        
        TextView colorLabel = new TextView(this);
        colorLabel.setText("Tint Color");
        colorLabel.setTextSize(14);
        LinearLayout.LayoutParams colorLabelParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        colorLabel.setLayoutParams(colorLabelParams);
        colorRow.addView(colorLabel);
        
        // Color preview circle
        final View colorPreviewCircle = new View(this);
        int circleSize = (int)(32 * density);
        LinearLayout.LayoutParams circleParams = new LinearLayout.LayoutParams(circleSize, circleSize);
        circleParams.setMarginEnd((int)(8 * density));
        colorPreviewCircle.setLayoutParams(circleParams);
        colorPreviewCircle.setBackgroundResource(R.drawable.icon_background_circle);
        String currentColor = json.optString("icon_color", "#FFFFFF");
        try {
            colorPreviewCircle.getBackground().setTint(android.graphics.Color.parseColor(currentColor));
        } catch (Exception e) {
            colorPreviewCircle.getBackground().setTint(android.graphics.Color.WHITE);
        }
        colorRow.addView(colorPreviewCircle);
        
        // Color input
        final com.google.android.material.textfield.TextInputEditText colorInput = new com.google.android.material.textfield.TextInputEditText(this);
        colorInput.setSingleLine(true);
        colorInput.setText(currentColor);
        colorInput.setHint("#FFFFFF");
        colorInput.setTextSize(14);
        LinearLayout.LayoutParams colorInputParams = new LinearLayout.LayoutParams(
            (int)(100 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        colorInput.setLayoutParams(colorInputParams);
        colorRow.addView(colorInput);
        
        container.addView(colorRow);

        // Color palette buttons
        LinearLayout colorPalette = new LinearLayout(this);
        colorPalette.setOrientation(LinearLayout.HORIZONTAL);
        colorPalette.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        paletteParams.bottomMargin = (int)(16 * density);
        colorPalette.setLayoutParams(paletteParams);
        
        String[] presetColors = {"#FFFFFF", "#FF5252", "#FF9800", "#FFEB3B", "#4CAF50", "#2196F3", "#9C27B0", "#000000"};
        for (String presetColor : presetColors) {
            View colorBtn = new View(this);
            int btnSize = (int)(28 * density);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(btnSize, btnSize);
            btnParams.setMargins((int)(4 * density), 0, (int)(4 * density), 0);
            colorBtn.setLayoutParams(btnParams);
            colorBtn.setBackgroundResource(R.drawable.icon_background_circle);
            try {
                colorBtn.getBackground().setTint(android.graphics.Color.parseColor(presetColor));
            } catch (Exception e) { }
            colorBtn.setOnClickListener(v -> {
                colorInput.setText(presetColor);
                try {
                    colorPreviewCircle.getBackground().setTint(android.graphics.Color.parseColor(presetColor));
                } catch (Exception e) { }
            });
            colorPalette.addView(colorBtn);
        }
        container.addView(colorPalette);

        // ===== SLIDERS SECTION =====
        // Hue Slider
        final SeekBar hueSeekBar = addMaterialSlider(container, "Hue", -180, 180, 
            (int) json.optDouble("icon_hue", CloneSettings.DEFAULT_ICON_HUE), density);
        
        // Saturation Slider
        final SeekBar saturationSeekBar = addMaterialSlider(container, "Saturation", -100, 100, 
            (int) json.optDouble("icon_saturation", CloneSettings.DEFAULT_ICON_SATURATION), density);
        
        // Lightness Slider
        final SeekBar lightnessSeekBar = addMaterialSlider(container, "Lightness", -100, 100, 
            (int) json.optDouble("icon_lightness", CloneSettings.DEFAULT_ICON_LIGHTNESS), density);

        // ===== ROTATION SECTION =====
        addSectionHeader(container, "Transform", density);
        
        // Rotation slider (0-360)
        final SeekBar rotationSeekBar = addMaterialSlider(container, "Rotation", 0, 360, 
            json.optInt("icon_rotation", CloneSettings.DEFAULT_ICON_ROTATION), density);
        
        // Flip options
        LinearLayout flipRow = new LinearLayout(this);
        flipRow.setOrientation(LinearLayout.HORIZONTAL);
        flipRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams flipRowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        flipRowParams.topMargin = (int)(8 * density);
        flipRow.setLayoutParams(flipRowParams);
        
        final com.google.android.material.checkbox.MaterialCheckBox flipHCheckBox = new com.google.android.material.checkbox.MaterialCheckBox(this);
        flipHCheckBox.setText("Flip Horizontal");
        flipHCheckBox.setChecked(json.optBoolean("icon_flip_horizontal", CloneSettings.DEFAULT_ICON_FLIP_HORIZONTAL));
        LinearLayout.LayoutParams flipHParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        flipHCheckBox.setLayoutParams(flipHParams);
        flipRow.addView(flipHCheckBox);
        
        final com.google.android.material.checkbox.MaterialCheckBox flipVCheckBox = new com.google.android.material.checkbox.MaterialCheckBox(this);
        flipVCheckBox.setText("Flip Vertical");
        flipVCheckBox.setChecked(json.optBoolean("icon_flip_vertical", CloneSettings.DEFAULT_ICON_FLIP_VERTICAL));
        flipRow.addView(flipVCheckBox);
        
        container.addView(flipRow);

        // ===== EFFECTS SECTION =====
        addSectionHeader(container, "Effects", density);
        
        final com.google.android.material.checkbox.MaterialCheckBox autoHueSwitch = new com.google.android.material.checkbox.MaterialCheckBox(this);
        autoHueSwitch.setText("Auto Hue (derive from color)");
        autoHueSwitch.setChecked(json.optBoolean("icon_auto_hue", CloneSettings.DEFAULT_ICON_AUTO_HUE));
        container.addView(autoHueSwitch);
        
        final com.google.android.material.checkbox.MaterialCheckBox invertColorsSwitch = new com.google.android.material.checkbox.MaterialCheckBox(this);
        invertColorsSwitch.setText("Invert Colors");
        invertColorsSwitch.setChecked(json.optBoolean("icon_invert_colors", CloneSettings.DEFAULT_ICON_INVERT_COLORS));
        container.addView(invertColorsSwitch);
        
        final com.google.android.material.checkbox.MaterialCheckBox sepiaSwitch = new com.google.android.material.checkbox.MaterialCheckBox(this);
        sepiaSwitch.setText("Sepia Tone");
        sepiaSwitch.setChecked(json.optBoolean("icon_sepia", CloneSettings.DEFAULT_ICON_SEPIA));
        container.addView(sepiaSwitch);

        // ===== BADGE SECTION =====
        addSectionHeader(container, "Badge", density);
        
        LinearLayout badgeRow = new LinearLayout(this);
        badgeRow.setOrientation(LinearLayout.HORIZONTAL);
        badgeRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        
        com.google.android.material.textfield.TextInputLayout badgeLayout = new com.google.android.material.textfield.TextInputLayout(this);
        badgeLayout.setHint("Badge text (e.g., 2, +)");
        badgeLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        badgeLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        badgeLayout.setCounterEnabled(true);
        badgeLayout.setCounterMaxLength(3);
        
        final com.google.android.material.textfield.TextInputEditText badgeInput = new com.google.android.material.textfield.TextInputEditText(this);
        badgeInput.setSingleLine(true);
        badgeInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        String currentBadge = json.optString("icon_badge", "");
        badgeInput.setText(currentBadge);
        badgeLayout.addView(badgeInput);
        
        LinearLayout.LayoutParams badgeLayoutParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        badgeLayoutParams.setMarginEnd((int)(12 * density));
        badgeLayout.setLayoutParams(badgeLayoutParams);
        badgeRow.addView(badgeLayout);
        
        // Badge position spinner
        final Spinner badgePosSpinner = new Spinner(this);
        String[] positions = {"Top Right", "Top Left", "Bottom Right", "Bottom Left"};
        ArrayAdapter<String> posAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, positions);
        posAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        badgePosSpinner.setAdapter(posAdapter);
        badgePosSpinner.setSelection(json.optInt("icon_badge_position", 0));
        badgeRow.addView(badgePosSpinner);
        
        container.addView(badgeRow);

        // ===== LIVE PREVIEW UPDATE HANDLER =====
        Runnable updatePreview = () -> {
            if (originalIcon == null) {
                return;
            }
            try {
                String colorText = colorInput.getText() != null ? colorInput.getText().toString().trim() : "#FFFFFF";
                if (colorText.isEmpty()) colorText = "#FFFFFF";
                if (!colorText.startsWith("#")) colorText = "#" + colorText;
                
                // Update color preview circle
                try {
                    colorPreviewCircle.getBackground().setTint(android.graphics.Color.parseColor(colorText));
                } catch (Exception e) { }
                
                int hue = hueSeekBar.getProgress() - 180;
                int saturation = saturationSeekBar.getProgress() - 100;
                int lightness = lightnessSeekBar.getProgress() - 100;
                int rotation = rotationSeekBar.getProgress();
                
                String badgeText = badgeInput.getText() != null ? badgeInput.getText().toString().trim() : "";
                int badgePosition = badgePosSpinner.getSelectedItemPosition();
                
                Bitmap previewBitmap = IconProcessor.processIconDrawable(
                    originalIcon,
                    colorText,
                    rotation,
                    flipHCheckBox.isChecked(),
                    flipVCheckBox.isChecked(),
                    hue,
                    saturation,
                    lightness,
                    autoHueSwitch.isChecked(),
                    invertColorsSwitch.isChecked(),
                    sepiaSwitch.isChecked(),
                    badgeText,
                    badgePosition
                );
                iconPreview.setImageBitmap(previewBitmap);
            } catch (Exception e) {
                Log.w(TAG, "Failed to build icon preview", e);
                iconPreview.setImageDrawable(originalIcon);
            }
        };

        // Add listeners for live preview
        android.text.TextWatcher textWatcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable s) { updatePreview.run(); }
        };
        
        colorInput.addTextChangedListener(textWatcher);
        badgeInput.addTextChangedListener(textWatcher);
        
        SeekBar.OnSeekBarChangeListener seekBarListener = new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { 
                updatePreview.run(); 
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        };
        
        hueSeekBar.setOnSeekBarChangeListener(seekBarListener);
        saturationSeekBar.setOnSeekBarChangeListener(seekBarListener);
        lightnessSeekBar.setOnSeekBarChangeListener(seekBarListener);
        rotationSeekBar.setOnSeekBarChangeListener(seekBarListener);
        
        android.widget.CompoundButton.OnCheckedChangeListener checkListener = (buttonView, isChecked) -> updatePreview.run();
        flipHCheckBox.setOnCheckedChangeListener(checkListener);
        flipVCheckBox.setOnCheckedChangeListener(checkListener);
        autoHueSwitch.setOnCheckedChangeListener(checkListener);
        invertColorsSwitch.setOnCheckedChangeListener(checkListener);
        sepiaSwitch.setOnCheckedChangeListener(checkListener);
        
        badgePosSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { updatePreview.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save", (dialog, which) -> {
            try {
                // Save app name
                String appName = appNameInput.getText() != null ? appNameInput.getText().toString().trim() : "";
                json.put("app_name", appName);
                
                // Save color
                String colorText = colorInput.getText() != null ? colorInput.getText().toString().trim() : "#FFFFFF";
                if (colorText.isEmpty()) colorText = "#FFFFFF";
                if (!colorText.startsWith("#")) colorText = "#" + colorText;
                json.put("icon_color", colorText);
                
                // Save sliders
                json.put("icon_hue", hueSeekBar.getProgress() - 180);
                json.put("icon_saturation", saturationSeekBar.getProgress() - 100);
                json.put("icon_lightness", lightnessSeekBar.getProgress() - 100);
                json.put("icon_rotation", rotationSeekBar.getProgress());
                
                // Save flip options
                json.put("icon_flip_horizontal", flipHCheckBox.isChecked());
                json.put("icon_flip_vertical", flipVCheckBox.isChecked());
                
                // Save effects
                json.put("icon_auto_hue", autoHueSwitch.isChecked());
                json.put("icon_invert_colors", invertColorsSwitch.isChecked());
                json.put("icon_sepia", sepiaSwitch.isChecked());
                
                // Save badge
                String badgeText = badgeInput.getText() != null ? badgeInput.getText().toString().trim() : "";
                json.put("icon_badge", badgeText);
                json.put("icon_badge_position", badgePosSpinner.getSelectedItemPosition());
                
                // Update settings in group children
                for (Setting child : groupSetting.children) {
                    switch (child.key) {
                        case "app_name": child.value = appName; break;
                        case "icon_color": child.value = colorText; break;
                        case "icon_rotation": child.value = rotationSeekBar.getProgress(); break;
                        case "icon_flip_horizontal": child.value = flipHCheckBox.isChecked(); break;
                        case "icon_flip_vertical": child.value = flipVCheckBox.isChecked(); break;
                        case "icon_badge": child.value = badgeText; break;
                        case "icon_badge_position": child.value = badgePosSpinner.getSelectedItemPosition(); break;
                    }
                }
                
                adapter.notifyDataSetChanged();
                Toast.makeText(MainActivity.this, "Icon settings saved", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving app name and icon settings", e);
                Toast.makeText(MainActivity.this, "Error saving settings", Toast.LENGTH_SHORT).show();
            }
        });
        
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> updatePreview.run());
        dialog.show();
    }
    
    private void addSectionHeader(LinearLayout container, String title, float density) {
        TextView header = new TextView(this);
        header.setText(title);
        header.setTextSize(14);
        header.setTypeface(header.getTypeface(), android.graphics.Typeface.BOLD);
        header.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = (int)(16 * density);
        headerParams.bottomMargin = (int)(8 * density);
        header.setLayoutParams(headerParams);
        container.addView(header);
    }
    
    private SeekBar addMaterialSlider(LinearLayout container, String label, int min, int max, int currentValue, float density) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = (int)(8 * density);
        row.setLayoutParams(rowParams);
        
        TextView labelView = new TextView(this);
        labelView.setText(label);
        labelView.setTextSize(14);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            (int)(80 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        labelView.setLayoutParams(labelParams);
        row.addView(labelView);
        
        final TextView valueView = new TextView(this);
        valueView.setText(String.valueOf(currentValue));
        valueView.setTextSize(14);
        valueView.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
            (int)(40 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
        valueView.setLayoutParams(valueParams);
        
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(max - min);
        seekBar.setProgress(currentValue - min);
        LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        seekParams.setMargins((int)(8 * density), 0, (int)(8 * density), 0);
        seekBar.setLayoutParams(seekParams);
        
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                valueView.setText(String.valueOf(progress + min));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        
        row.addView(seekBar);
        row.addView(valueView);
        container.addView(row);
        
        return seekBar;
    }

    /**
     * Shows a complex Fake Camera settings dialog matching App Cloner's style.
     * Features checkboxes for multiple options, dropdown for rotation, and text inputs.
     */
    private void showFakeCameraDialog(final Setting groupSetting, final JSONObject json, final BaseAdapter adapter) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Fake camera");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);

        // Main Enable checkbox
        final com.google.android.material.checkbox.MaterialCheckBox enabledCheckbox = 
            new com.google.android.material.checkbox.MaterialCheckBox(this);
        enabledCheckbox.setText("Enabled");
        enabledCheckbox.setTextSize(16);
        enabledCheckbox.setChecked(json.optBoolean("FakeCamera", false));
        container.addView(enabledCheckbox);

        // Rotation dropdown
        TextView rotationLabel = new TextView(this);
        rotationLabel.setText("Rotation");
        rotationLabel.setTextSize(14);
        rotationLabel.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
        LinearLayout.LayoutParams rotLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rotLabelParams.topMargin = (int)(16 * density);
        rotationLabel.setLayoutParams(rotLabelParams);
        container.addView(rotationLabel);

        // Rotation spinner styled like App Cloner
        com.google.android.material.textfield.TextInputLayout rotationLayout = 
            new com.google.android.material.textfield.TextInputLayout(this, null, 
                com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle);
        rotationLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        rotationLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        
        final android.widget.AutoCompleteTextView rotationDropdown = 
            new android.widget.AutoCompleteTextView(rotationLayout.getContext());
        rotationDropdown.setInputType(InputType.TYPE_NULL);
        
        String[] rotationOptions = {"No change", "0°", "90°", "180°", "270°"};
        ArrayAdapter<String> rotationAdapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_dropdown_item_1line, rotationOptions);
        rotationDropdown.setAdapter(rotationAdapter);
        
        String currentRotation = json.optString("FakeCameraRotation", "No change");
        rotationDropdown.setText(currentRotation, false);
        
        rotationLayout.addView(rotationDropdown);
        LinearLayout.LayoutParams rotLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rotLayoutParams.topMargin = (int)(4 * density);
        rotationLayout.setLayoutParams(rotLayoutParams);
        container.addView(rotationLayout);

        // Options checkboxes - only settings used by FakeCameraHook
        String[][] options = {
            {"FlipHorizontally", "Flip horizontally", "Mirrors the selected picture horizontally. May be required for some apps when using selfies."},
            {"ResizeImage", "Resize picture", "Resizes the selected picture to match the camera resolution for more credible results."},
            {"AddExifAttributes", "Add EXIF attributes", "Adds camera make/model, timestamp and other EXIF metadata to the captured image."},
            {"RandomizeImage", "Randomize picture", "Slightly adjusts the rotation, crop and color each time a picture is taken."},
            {"FakeCameraAlternativeMode", "Alternative mode", "Enable this option if you're encountering difficulties with camera capture."},
            {"FakeCameraOpenStreamWorkaround", "Open Stream workaround", "Enable this if apps have issues reading the fake camera image."},
            {"FakeCameraCloseStreamWorkaround", "Close Stream workaround", "Enable this for additional compatibility with certain apps."},
            {"FakeCameraFloatingMenu", "Floating camera menu", "Shows a floating overlay to control fake camera settings while using the app."}
        };

        // Track the randomize strength seekbar for saving
        final SeekBar[] randomizeStrengthSeekBar = new SeekBar[1];
        final com.google.android.material.checkbox.MaterialCheckBox[] randomizeCheckboxRef = 
            new com.google.android.material.checkbox.MaterialCheckBox[1];

        for (String[] option : options) {
            final com.google.android.material.checkbox.MaterialCheckBox optionCheckbox = 
                new com.google.android.material.checkbox.MaterialCheckBox(this);
            optionCheckbox.setText(option[1]);
            optionCheckbox.setTextSize(16);
            optionCheckbox.setChecked(json.optBoolean(option[0], false));
            optionCheckbox.setTag(option[0]);
            
            LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            cbParams.topMargin = (int)(8 * density);
            optionCheckbox.setLayoutParams(cbParams);
            
            // Add description below checkbox
            if (option.length > 2 && !option[2].isEmpty()) {
                LinearLayout optionContainer = new LinearLayout(this);
                optionContainer.setOrientation(LinearLayout.VERTICAL);
                optionContainer.setLayoutParams(cbParams);
                
                optionCheckbox.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                optionContainer.addView(optionCheckbox);
                
                TextView descText = new TextView(this);
                descText.setText(option[2]);
                descText.setTextSize(12);
                descText.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
                LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                descParams.setMarginStart((int)(40 * density));
                descText.setLayoutParams(descParams);
                optionContainer.addView(descText);
                
                // Add randomize strength slider if this is the RandomizeImage checkbox
                if ("RandomizeImage".equals(option[0])) {
                    randomizeCheckboxRef[0] = optionCheckbox;
                    
                    LinearLayout strengthRow = new LinearLayout(this);
                    strengthRow.setOrientation(LinearLayout.HORIZONTAL);
                    strengthRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams strengthParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    strengthParams.setMarginStart((int)(40 * density));
                    strengthParams.topMargin = (int)(8 * density);
                    strengthRow.setLayoutParams(strengthParams);
                    
                    TextView strengthLabel = new TextView(this);
                    strengthLabel.setText("Strength");
                    strengthLabel.setTextSize(14);
                    LinearLayout.LayoutParams strengthLabelParams = new LinearLayout.LayoutParams(
                        (int)(70 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                    strengthLabel.setLayoutParams(strengthLabelParams);
                    strengthRow.addView(strengthLabel);
                    
                    SeekBar strengthSeekBar = new SeekBar(this);
                    strengthSeekBar.setMax(100);
                    strengthSeekBar.setProgress(json.optInt("RandomizeStrength", 25));
                    LinearLayout.LayoutParams seekParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    seekParams.setMargins((int)(8 * density), 0, (int)(8 * density), 0);
                    strengthSeekBar.setLayoutParams(seekParams);
                    randomizeStrengthSeekBar[0] = strengthSeekBar;
                    strengthRow.addView(strengthSeekBar);
                    
                    final TextView strengthValue = new TextView(this);
                    strengthValue.setText(String.valueOf(json.optInt("RandomizeStrength", 25)));
                    strengthValue.setTextSize(14);
                    LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(
                        (int)(36 * density), LinearLayout.LayoutParams.WRAP_CONTENT);
                    strengthValue.setLayoutParams(valueParams);
                    strengthRow.addView(strengthValue);
                    
                    strengthSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                            strengthValue.setText(String.valueOf(progress));
                        }
                        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
                    });
                    
                    // Enable/disable based on checkbox state
                    strengthSeekBar.setEnabled(optionCheckbox.isChecked());
                    strengthLabel.setAlpha(optionCheckbox.isChecked() ? 1.0f : 0.5f);
                    optionCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        strengthSeekBar.setEnabled(isChecked);
                        strengthLabel.setAlpha(isChecked ? 1.0f : 0.5f);
                    });
                    
                    optionContainer.addView(strengthRow);
                }
                
                container.addView(optionContainer);
            } else {
                container.addView(optionCheckbox);
            }
        }

        builder.setView(scrollView);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                // Save main enabled state
                json.put("FakeCamera", enabledCheckbox.isChecked());
                
                // Save rotation
                String selectedRotation = rotationDropdown.getText().toString();
                json.put("FakeCameraRotation", selectedRotation);
                
                // Save randomize strength
                if (randomizeStrengthSeekBar[0] != null) {
                    json.put("RandomizeStrength", randomizeStrengthSeekBar[0].getProgress());
                }
                
                // Save all checkbox options
                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    if (child instanceof com.google.android.material.checkbox.MaterialCheckBox) {
                        com.google.android.material.checkbox.MaterialCheckBox cb = 
                            (com.google.android.material.checkbox.MaterialCheckBox) child;
                        if (cb.getTag() != null) {
                            json.put((String) cb.getTag(), cb.isChecked());
                        }
                    } else if (child instanceof LinearLayout) {
                        LinearLayout ll = (LinearLayout) child;
                        for (int j = 0; j < ll.getChildCount(); j++) {
                            View subChild = ll.getChildAt(j);
                            if (subChild instanceof com.google.android.material.checkbox.MaterialCheckBox) {
                                com.google.android.material.checkbox.MaterialCheckBox cb = 
                                    (com.google.android.material.checkbox.MaterialCheckBox) subChild;
                                if (cb.getTag() != null) {
                                    json.put((String) cb.getTag(), cb.isChecked());
                                }
                            }
                        }
                    }
                }
                
                // Update the groupSetting children values
                for (Setting child : groupSetting.children) {
                    if (json.has(child.key)) {
                        child.value = json.opt(child.key);
                        if (child.value instanceof Boolean) {
                            child.valueClass = Boolean.class;
                        } else {
                            child.valueClass = String.class;
                        }
                    }
                }
                
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving Fake Camera settings", e);
                Toast.makeText(MainActivity.this, "Failed to save settings.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    /**
     * Shows a well-organized Network & Proxy settings dialog with sections for:
     * - SOCKS5 Proxy settings (host, port, authentication)
     * - Spoof Network Connection (mock Wi-Fi, Mobile, Ethernet states)
     */
    private void showNetworkProxyDialog(final Setting groupSetting, final JSONObject json, final BaseAdapter adapter) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder = 
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Network & Proxy");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);

        // ===== SOCKS5 PROXY SECTION =====
        TextView proxyHeader = new TextView(this);
        proxyHeader.setText("SOCKS5 Proxy");
        proxyHeader.setTextSize(14);
        proxyHeader.setTypeface(proxyHeader.getTypeface(), android.graphics.Typeface.BOLD);
        proxyHeader.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        container.addView(proxyHeader);

        // Proxy Enable checkbox
        final com.google.android.material.checkbox.MaterialCheckBox proxyEnabledCheckbox = 
            new com.google.android.material.checkbox.MaterialCheckBox(this);
        proxyEnabledCheckbox.setText("Enable SOCKS5 Proxy");
        proxyEnabledCheckbox.setTextSize(16);
        proxyEnabledCheckbox.setChecked(json.optBoolean("socks_proxy", false));
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cbParams.topMargin = (int)(8 * density);
        proxyEnabledCheckbox.setLayoutParams(cbParams);
        container.addView(proxyEnabledCheckbox);

        // Proxy host
        com.google.android.material.textfield.TextInputLayout hostLayout = 
            new com.google.android.material.textfield.TextInputLayout(this);
        hostLayout.setHint("Proxy Host");
        hostLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        hostLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        final com.google.android.material.textfield.TextInputEditText hostInput = 
            new com.google.android.material.textfield.TextInputEditText(this);
        hostInput.setSingleLine(true);
        hostInput.setText(json.optString("socks_proxy_host", ""));
        hostLayout.addView(hostInput);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hostParams.topMargin = (int)(8 * density);
        hostLayout.setLayoutParams(hostParams);
        container.addView(hostLayout);

        // Proxy port
        com.google.android.material.textfield.TextInputLayout portLayout = 
            new com.google.android.material.textfield.TextInputLayout(this);
        portLayout.setHint("Proxy Port");
        portLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        portLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        final com.google.android.material.textfield.TextInputEditText portInput = 
            new com.google.android.material.textfield.TextInputEditText(this);
        portInput.setSingleLine(true);
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(json.optInt("socks_proxy_port", 1080)));
        portLayout.addView(portInput);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        portParams.topMargin = (int)(8 * density);
        portLayout.setLayoutParams(portParams);
        container.addView(portLayout);

        // Proxy username
        com.google.android.material.textfield.TextInputLayout userLayout = 
            new com.google.android.material.textfield.TextInputLayout(this);
        userLayout.setHint("Username (optional)");
        userLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        userLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        final com.google.android.material.textfield.TextInputEditText userInput = 
            new com.google.android.material.textfield.TextInputEditText(this);
        userInput.setSingleLine(true);
        userInput.setText(json.optString("socks_proxy_user", ""));
        userLayout.addView(userInput);
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        userParams.topMargin = (int)(8 * density);
        userLayout.setLayoutParams(userParams);
        container.addView(userLayout);

        // Proxy password
        com.google.android.material.textfield.TextInputLayout passLayout = 
            new com.google.android.material.textfield.TextInputLayout(this);
        passLayout.setHint("Password (optional)");
        passLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        passLayout.setBoxCornerRadii(8 * density, 8 * density, 8 * density, 8 * density);
        passLayout.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final com.google.android.material.textfield.TextInputEditText passInput = 
            new com.google.android.material.textfield.TextInputEditText(this);
        passInput.setSingleLine(true);
        passInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setText(json.optString("socks_proxy_pass", ""));
        passLayout.addView(passInput);
        LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        passParams.topMargin = (int)(8 * density);
        passLayout.setLayoutParams(passParams);
        container.addView(passLayout);

        // ===== SPOOF NETWORK CONNECTION SECTION =====
        TextView spoofHeader = new TextView(this);
        spoofHeader.setText("Spoof Network Connection");
        spoofHeader.setTextSize(14);
        spoofHeader.setTypeface(spoofHeader.getTypeface(), android.graphics.Typeface.BOLD);
        spoofHeader.setTextColor(getResources().getColor(R.color.colorPrimary, null));
        LinearLayout.LayoutParams spoofHeaderParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spoofHeaderParams.topMargin = (int)(24 * density);
        spoofHeader.setLayoutParams(spoofHeaderParams);
        container.addView(spoofHeader);

        TextView spoofDesc = new TextView(this);
        spoofDesc.setText("Make apps believe the device has specific network connectivity.");
        spoofDesc.setTextSize(12);
        spoofDesc.setTextColor(getResources().getColor(R.color.colorOnSurfaceVariant, null));
        LinearLayout.LayoutParams spoofDescParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        spoofDescParams.topMargin = (int)(4 * density);
        spoofDescParams.bottomMargin = (int)(8 * density);
        spoofDesc.setLayoutParams(spoofDescParams);
        container.addView(spoofDesc);

        // Network connection options - each with radio group like screenshot
        String[][] networkOptions = {
            {"mock_wifi_connection", "Mock Wi-Fi Connection"},
            {"mock_mobile_connection", "Mock Mobile Connection"},
            {"mock_ethernet_connection", "Mock Ethernet Connection"}
        };

        final Map<String, RadioGroup> networkRadioGroups = new LinkedHashMap<>();

        for (String[] option : networkOptions) {
            TextView optionLabel = new TextView(this);
            optionLabel.setText(option[1]);
            optionLabel.setTextSize(14);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            labelParams.topMargin = (int)(12 * density);
            optionLabel.setLayoutParams(labelParams);
            container.addView(optionLabel);

            RadioGroup radioGroup = new RadioGroup(this);
            radioGroup.setOrientation(RadioGroup.HORIZONTAL);

            String[] choices = {"No change", "Connected", "Disconnected"};
            String[] values = {"", "CONNECTED", "DISCONNECTED"};
            String currentVal = json.optString(option[0], "");

            for (int i = 0; i < choices.length; i++) {
                RadioButton rb = new RadioButton(this);
                rb.setText(choices[i]);
                rb.setTextSize(12);
                rb.setTag(values[i]);
                radioGroup.addView(rb);
                
                if (values[i].equalsIgnoreCase(currentVal) || 
                    (values[i].isEmpty() && (currentVal.isEmpty() || "null".equalsIgnoreCase(currentVal)))) {
                    radioGroup.check(rb.getId());
                }
            }

            LinearLayout.LayoutParams rgParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rgParams.topMargin = (int)(4 * density);
            radioGroup.setLayoutParams(rgParams);
            container.addView(radioGroup);
            networkRadioGroups.put(option[0], radioGroup);
        }

        builder.setView(scrollView);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                // Save SOCKS proxy settings
                json.put("socks_proxy", proxyEnabledCheckbox.isChecked());
                json.put("socks_proxy_host", hostInput.getText() != null ? hostInput.getText().toString().trim() : "");
                
                String portText = portInput.getText() != null ? portInput.getText().toString().trim() : "1080";
                try {
                    json.put("socks_proxy_port", Integer.parseInt(portText));
                } catch (NumberFormatException e) {
                    json.put("socks_proxy_port", 1080);
                }
                
                json.put("socks_proxy_user", userInput.getText() != null ? userInput.getText().toString().trim() : "");
                json.put("socks_proxy_pass", passInput.getText() != null ? passInput.getText().toString().trim() : "");

                // Save mock network connection settings
                for (Map.Entry<String, RadioGroup> entry : networkRadioGroups.entrySet()) {
                    String key = entry.getKey();
                    RadioGroup rg = entry.getValue();
                    int checkedId = rg.getCheckedRadioButtonId();
                    View checkedView = rg.findViewById(checkedId);
                    if (checkedView != null && checkedView.getTag() != null) {
                        String val = (String) checkedView.getTag();
                        if (val.isEmpty()) {
                            json.put(key, JSONObject.NULL);
                        } else {
                            json.put(key, val);
                        }
                    }
                }

                // Update the groupSetting children values
                for (Setting child : groupSetting.children) {
                    if (json.has(child.key)) {
                        Object newVal = json.opt(child.key);
                        if (JSONObject.NULL.equals(newVal)) {
                            child.value = null;
                        } else {
                            child.value = newVal;
                        }
                        if (child.value instanceof Boolean) {
                            child.valueClass = Boolean.class;
                        } else if (child.value instanceof Integer) {
                            child.valueClass = Integer.class;
                        } else {
                            child.valueClass = String.class;
                        }
                    }
                }

                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving Network & Proxy settings", e);
                Toast.makeText(MainActivity.this, "Failed to save settings.", Toast.LENGTH_SHORT).show();
            }
        });

        builder.show();
    }

    private void showJsonObjectEditorDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        JSONObject object = (JSONObject) setting.value;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit " + formatSettingName(setting.key));

        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        scrollView.addView(container);

        List<String> childKeys = new ArrayList<>();
        Iterator<String> iterator = object.keys();
        while (iterator.hasNext()) {
            childKeys.add(iterator.next());
        }
        Collections.sort(childKeys, String.CASE_INSENSITIVE_ORDER);

        final List<JsonObjectField> fields = new ArrayList<>();

        if (childKeys.isEmpty()) {
            TextView emptyView = new TextView(this);
            emptyView.setText("No configurable values available.");
            container.addView(emptyView);
        } else {
            for (int i = 0; i < childKeys.size(); i++) {
                String childKey = childKeys.get(i);
                Object rawValue = object.opt(childKey);
                if (JSONObject.NULL.equals(rawValue)) {
                    rawValue = null;
                }

                if (rawValue instanceof Boolean) {
                    CheckBox checkBox = new CheckBox(this);
                    checkBox.setText(formatSettingName(childKey));
                    checkBox.setChecked((Boolean) rawValue);
                    container.addView(checkBox);
                    fields.add(new JsonObjectField(childKey, checkBox, Boolean.class));
                } else {
                    TextView label = new TextView(this);
                    label.setText(formatSettingName(childKey));
                    if (i > 0) {
                        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                        labelParams.topMargin = (int) (12 * getResources().getDisplayMetrics().density);
                        label.setLayoutParams(labelParams);
                    }
                    container.addView(label);

                    EditText input = new EditText(this);
                    input.setText(rawValue != null ? String.valueOf(rawValue) : "");
                    Class<?> valueClass = (rawValue != null) ? rawValue.getClass() : String.class;
                    if (Number.class.isAssignableFrom(valueClass)) {
                        if (valueClass == Integer.class || valueClass == Long.class) {
                            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
                        } else {
                            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                        }
                    }
                    container.addView(input);
                    fields.add(new JsonObjectField(childKey, input, valueClass));
                }
            }
        }

        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("Save", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    for (JsonObjectField field : fields) {
                        if (field.valueClass == Boolean.class && field.inputView instanceof CheckBox) {
                            object.put(field.key, ((CheckBox) field.inputView).isChecked());
                        } else if (field.inputView instanceof EditText) {
                            EditText editText = (EditText) field.inputView;
                            String text = editText.getText().toString().trim();
                            if (text.isEmpty()) {
                                object.put(field.key, JSONObject.NULL);
                            } else if (field.valueClass == Integer.class) {
                                object.put(field.key, Integer.parseInt(text));
                            } else if (field.valueClass == Long.class) {
                                object.put(field.key, Long.parseLong(text));
                            } else if (Number.class.isAssignableFrom(field.valueClass)) {
                                object.put(field.key, Double.parseDouble(text));
                            } else {
                                object.put(field.key, text);
                            }
                        }
                    }
                    setting.value = object;
                    json.put(setting.key, object);
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (NumberFormatException | JSONException e) {
                    Log.e(TAG, "Error saving JSON object setting: " + setting.key, e);
                    Toast.makeText(MainActivity.this, "Invalid value. Please check your input.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        dialog.show();
    }

    private void refreshBundleDataUi() {
        if (bundleDataSetting != null) {
            bundleDataSetting.value = bundledDataDisplayName;
            bundleDataSetting.valueClass = String.class;
        }
        if (bundleDataDialogSummary != null) {
            bundleDataDialogSummary.setText(getBundleDataDialogStatusText());
        }
        if (bundleDataDialogClearButton != null) {
            bundleDataDialogClearButton.setEnabled(bundledDataFileForCloning != null);
        }
        if (currentSettingsAdapter != null) {
            currentSettingsAdapter.notifyDataSetChanged();
        }
    }

    private String getBundleDataSummaryText() {
        if (bundledDataFileForCloning != null) {
            String name = bundledDataDisplayName;
            if (name == null || name.trim().isEmpty()) {
                name = bundledDataFileForCloning.getName();
            }
            return "Selected: " + name;
        }
        return "Tap to select data ZIP";
    }

    private String getBundleDataDialogStatusText() {
        if (bundledDataFileForCloning != null) {
            String name = bundledDataDisplayName;
            if (name == null || name.trim().isEmpty()) {
                name = bundledDataFileForCloning.getName();
            }
            return "Selected file: " + name;
        }
        return "No ZIP selected.";
    }

    private void showCloningModeDialog(final Setting setting, final JSONObject json, final BaseAdapter adapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Cloning Mode");

        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (16 * density);

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);

        final String[] values = {CLONING_MODE_REPLACE, CLONING_MODE_GENERATE, CLONING_MODE_CUSTOM};
        final String[] labels = {
                "Replace original (keep package)",
                "Generate new package variant",
                "Custom package name"
        };

        String currentValue = setting.value instanceof String ? (String) setting.value : CLONING_MODE_REPLACE;
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentValue)) {
                selectedIndex = i;
                break;
            }
        }

        final RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < labels.length; i++) {
            RadioButton radioButton = new RadioButton(this);
            radioButton.setText(labels[i]);
            radioButton.setId(i);
            radioGroup.addView(radioButton);
        }
        radioGroup.check(selectedIndex);
        container.addView(radioGroup);

        // Custom package input field
        TextView customLabel = new TextView(this);
        customLabel.setText("Custom Package Name:");
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.topMargin = (int) (12 * density);
        customLabel.setLayoutParams(labelParams);
        container.addView(customLabel);

        final EditText customPackageInput = new EditText(this);
        customPackageInput.setHint("e.g., com.example.myapp");
        customPackageInput.setSingleLine(true);
        customPackageInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        String currentCustomPackage = json.optString(CUSTOM_PACKAGE_NAME_KEY, "");
        if (currentCustomPackage.isEmpty() && currentSettingsApp != null && currentSettingsApp.packageName != null) {
            currentCustomPackage = currentSettingsApp.packageName;
        }
        customPackageInput.setText(currentCustomPackage);
        container.addView(customPackageInput);

        // Show/hide custom package input based on selection
        boolean isCustomMode = selectedIndex == 2;
        customLabel.setVisibility(isCustomMode ? View.VISIBLE : View.GONE);
        customPackageInput.setVisibility(isCustomMode ? View.VISIBLE : View.GONE);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean showCustomInput = checkedId == 2;
            customLabel.setVisibility(showCustomInput ? View.VISIBLE : View.GONE);
            customPackageInput.setVisibility(showCustomInput ? View.VISIBLE : View.GONE);
            
            // Auto-fill the custom package input with current package name when switching to custom mode
            if (showCustomInput && customPackageInput.getText().toString().trim().isEmpty()) {
                if (currentSettingsApp != null && currentSettingsApp.packageName != null && !currentSettingsApp.packageName.isEmpty()) {
                    customPackageInput.setText(currentSettingsApp.packageName);
                    customPackageInput.setSelection(customPackageInput.getText().length());
                }
            }
        });

        builder.setView(container);
        builder.setPositiveButton("OK", null);
        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            Button saveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            saveButton.setOnClickListener(v -> {
                try {
                    int checkedId = radioGroup.getCheckedRadioButtonId();
                    String newValue = values[checkedId];

                    // Validate custom package if custom mode is selected
                    if (CLONING_MODE_CUSTOM.equals(newValue)) {
                        String customPkg = customPackageInput.getText().toString().trim();
                        if (customPkg.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Please enter a custom package name.", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (!isValidPackageName(customPkg)) {
                            Toast.makeText(MainActivity.this, "Invalid package name. Use format like: com.example.app", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        json.put(CUSTOM_PACKAGE_NAME_KEY, customPkg);
                    }

                    setting.value = newValue;
                    setting.valueClass = String.class;
                    json.put(CLONING_MODE_KEY, newValue);
                    adapter.notifyDataSetChanged();
                    dialog.dismiss();
                } catch (JSONException e) {
                    Log.e(TAG, "Error saving cloning mode", e);
                }
            });
        });
        dialog.show();
    }

    private boolean isValidPackageName(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            return false;
        }
        if (!packageName.contains(".")) {
            return false;
        }
        String[] segments = packageName.split("\\.");
        if (segments.length < 2) {
            return false;
        }
        for (String segment : segments) {
            if (segment.isEmpty()) {
                return false;
            }
            char first = segment.charAt(0);
            if (!Character.isLetter(first) && first != '_') {
                return false;
            }
            for (int i = 1; i < segment.length(); i++) {
                char c = segment.charAt(i);
                if (!Character.isLetterOrDigit(c) && c != '_') {
                    return false;
                }
            }
        }
        return true;
    }

    private List<Setting> buildSettingsList(JSONObject json) throws JSONException {
        List<String> keys = new ArrayList<>();
        Iterator<String> iterator = json.keys();
        while (iterator.hasNext()) {
            keys.add(iterator.next());
        }

        // Build a map of all settings by key
        Map<String, Setting> settingsByKey = new LinkedHashMap<>();
        for (String key : keys) {
            if ("user_agent_hook_enabled".equals(key)) {
                // Managed together with the user_agent field
                continue;
            }
            Object value = json.get(key);
            Setting setting = new Setting(key, value, inferValueClass(value));
            settingsByKey.put(key, setting);
        }

        // Create category groups
        List<Setting> result = new ArrayList<>();
        Set<String> processedKeys = new HashSet<>();

        // Process each category in order
        for (Map.Entry<String, List<String>> categoryEntry : SETTINGS_CATEGORIES.entrySet()) {
            String categoryKey = categoryEntry.getKey();
            List<String> childKeys = categoryEntry.getValue();
            
            List<Setting> categoryChildren = new ArrayList<>();
            for (String childKey : childKeys) {
                Setting setting = settingsByKey.get(childKey);
                if (setting != null) {
                    categoryChildren.add(setting);
                    processedKeys.add(childKey);
                }
            }

            if (!categoryChildren.isEmpty()) {
                // Create category parent setting
                Setting categoryGroup = new Setting(categoryKey, null, null);
                // categoryGroup.isCategory = true; // CHANGED: Treat categories as normal groups
                for (Setting child : categoryChildren) {
                    child.parent = categoryGroup;
                }
                categoryGroup.children = categoryChildren;
                result.add(categoryGroup);
            }
        }

        // Process remaining settings with prefix-based grouping (legacy behavior)
        Map<String, List<Setting>> prefixGrouped = new LinkedHashMap<>();
        List<Setting> ungroupedSingles = new ArrayList<>();

        for (String key : keys) {
            if (processedKeys.contains(key)) {
                continue;
            }
            Setting setting = settingsByKey.get(key);
            if (setting == null) continue;

            Object value = setting.value;
            if (value instanceof JSONObject) {
                ungroupedSingles.add(setting);
                processedKeys.add(key);
                continue;
            }
            
            String base = getGroupBase(key);
            if (base != null) {
                prefixGrouped.computeIfAbsent(base, k -> new ArrayList<>()).add(setting);
            } else {
                ungroupedSingles.add(setting);
            }
            processedKeys.add(key);
        }

        // Add prefix-grouped settings
        for (Map.Entry<String, List<Setting>> entry : prefixGrouped.entrySet()) {
            List<Setting> children = entry.getValue();
            if (children.size() > 1) {
                sortGroupChildren(entry.getKey(), children);
                Setting group = new Setting(entry.getKey(), null, null);
                for (Setting child : children) {
                    child.parent = group;
                }
                group.children = children;
                result.add(group);
            } else if (!children.isEmpty()) {
                ungroupedSingles.add(children.get(0));
            }
        }

        // Add remaining ungrouped settings under "Other Settings" category
        if (!ungroupedSingles.isEmpty()) {
            Collections.sort(ungroupedSingles, (a, b) -> getDisplayName(a).compareToIgnoreCase(getDisplayName(b)));
            Setting otherCategory = new Setting("cat_other", null, null);
            // otherCategory.isCategory = true; // CHANGED: Treat categories as normal groups
            for (Setting child : ungroupedSingles) {
                child.parent = otherCategory;
            }
            otherCategory.children = ungroupedSingles;
            result.add(otherCategory);
        }

        return result;
    }

    private String getGroupBase(String key) {
        if (key == null) {
            return null;
        }
        int index = key.lastIndexOf('_');
        if (index <= 0) {
            return null;
        }
        return key.substring(0, index);
    }

    private void sortGroupChildren(String groupKey, List<Setting> children) {
        List<String> order = GROUP_CHILD_ORDER.get(groupKey);
        Collections.sort(children, (a, b) -> {
            String suffixA = getChildSuffix(groupKey, a.key).toLowerCase(Locale.US);
            String suffixB = getChildSuffix(groupKey, b.key).toLowerCase(Locale.US);
            if (order != null) {
                int indexA = order.indexOf(suffixA);
                int indexB = order.indexOf(suffixB);
                if (indexA != indexB) {
                    if (indexA == -1) indexA = Integer.MAX_VALUE;
                    if (indexB == -1) indexB = Integer.MAX_VALUE;
                    return Integer.compare(indexA, indexB);
                }
            }
            return suffixA.compareToIgnoreCase(suffixB);
        });
    }

    private String getDisplayName(Setting setting) {
        if (setting == null) {
            return "";
        }
        if (setting.isCategoryHeader()) {
            return getCategoryDisplayName(setting.key);
        }
        return setting.isGroup() ? formatGroupTitle(setting.key) : formatSettingName(setting.key);
    }

    private String getCategoryDisplayName(String categoryKey) {
        String displayName = CATEGORY_DISPLAY_NAMES.get(categoryKey);
        if (displayName != null) {
            return displayName;
        }
        // Fallback for "cat_other" or unknown categories
        if ("cat_other".equals(categoryKey)) {
            return "Other Settings";
        }
        return formatSettingName(categoryKey.replace("cat_", ""));
    }

    private String formatGroupTitle(String key) {
        if (key == null) {
            return "";
        }
        // Check if it's a category first
        if (key.startsWith("cat_")) {
            return getCategoryDisplayName(key);
        }
        String normalized = key.toLowerCase(Locale.US);
        switch (normalized) {
            case "build":
                return "Builds Props";
            case "socks_proxy":
                return "Socks Proxy";
            case "floating":
                return "Floating Window";
            case "background_media":
                return "Background Media";
            case "spoofLocation":
            case "spooflocation":
                return "Location Spoofing";
            case "fakecamera":
                return "Fake Camera";
            case "accessible_data_dir":
                return "Accessible Data Directory";
            case "privacy":
                return "Privacy & Security";
            default:
                return formatSettingName(key);
        }
    }

    private String getGroupSummary(Setting groupSetting) {
        if (groupSetting == null || !groupSetting.isGroup()) {
            return "";
        }

        Setting toggleSetting = null;
        int configured = 0;

        for (Setting child : groupSetting.children) {
            if (isToggleKey(groupSetting.key, child.key)) {
                if (child.value instanceof Boolean) {
                    toggleSetting = child;
                }
                continue;
            }
            Object value = child.value;
            if (value instanceof Boolean) {
                if ((Boolean) value) {
                    configured++;
                }
            } else if (value != null && !JSONObject.NULL.equals(value) && !value.toString().trim().isEmpty()) {
                configured++;
            }
        }

        if (toggleSetting != null && toggleSetting.value instanceof Boolean) {
            return (Boolean) toggleSetting.value ? "Enabled" : "Disabled";
        }

        if (configured == 0) {
            return "Tap to configure";
        }
        return configured == 1 ? "1 value set" : configured + " values set";
    }

    private boolean isToggleKey(String groupKey, String childKey) {
        String suffix = getChildSuffix(groupKey, childKey).toLowerCase(Locale.US);
        return suffix.equals("enabled") || suffix.equals("enable") || suffix.equals("active");
    }

    private String getChildSuffix(String groupKey, String fullKey) {
        if (groupKey == null || fullKey == null) {
            return "";
        }
        String prefix = groupKey + "_";
        if (fullKey.startsWith(prefix)) {
            return fullKey.substring(prefix.length());
        }
        return fullKey;
    }

    private String formatChildLabel(String groupKey, String suffix) {
        if (suffix == null || suffix.isEmpty()) {
            return formatGroupTitle(groupKey);
        }
        String groupTitle = formatGroupTitle(groupKey);
        if (isToggleSuffix(suffix)) {
            return groupTitle.isEmpty() ? "Enable" : "Enable " + groupTitle;
        }
        return formatSettingName(suffix);
    }

    private boolean isToggleSuffix(String suffix) {
        String normalized = suffix.toLowerCase(Locale.US);
        return normalized.equals("enabled") || normalized.equals("enable") || normalized.equals("active");
    }

    private Class<?> inferValueClass(Object value) {
        if (value == null || JSONObject.NULL.equals(value)) {
            return String.class;
        }
        if (value instanceof Integer) {
            return Integer.class;
        }
        if (value instanceof Long) {
            return Long.class;
        }
        if (value instanceof Float) {
            return Float.class;
        }
        if (value instanceof Double) {
            return Double.class;
        }
        if (value instanceof Number) {
            return value.getClass();
        }
        if (value instanceof Boolean) {
            return Boolean.class;
        }
        return String.class;
    }

    private String formatSettingName(String key) {
        if (key == null || key.isEmpty()) {
            return "";
        }

        // Pre-process: insert spaces at CamelCase boundaries and replace underscores with spaces
        StringBuilder processed = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            if (c == '_') {
                processed.append(' ');
            } else {
                if (i > 0 && Character.isUpperCase(c) && (!Character.isUpperCase(key.charAt(i - 1)) && key.charAt(i - 1) != '_')) {
                    processed.append(' ');
                }
                processed.append(c);
            }
        }

        String[] parts = processed.toString().split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String token = part.toLowerCase(Locale.getDefault());
            String replacement = SPECIAL_TOKENS.get(token);
            if (replacement != null) {
                builder.append(replacement);
            } else if (token.length() <= 2) {
                builder.append(token.toUpperCase(Locale.getDefault()));
            } else {
                builder.append(Character.toUpperCase(token.charAt(0))).append(token.substring(1));
            }
        }
        return builder.toString();
    }

    private void initViews() {
        selectApkBtn  = findViewById(R.id.selectApkButton);
        processApkBtn = findViewById(R.id.processApkButton);
        selectedTxt   = findViewById(R.id.selectedApkText);
        statusTxt     = findViewById(R.id.statusText);
        installedAppsList = findViewById(R.id.installedAppsList);
        clonedAppsList = findViewById(R.id.clonedAppsList);
        tabLayout = findViewById(R.id.tabLayout);
        settingsEditorContainer = findViewById(R.id.settingsEditorContainer);
        processApkBtn.setEnabled(false);
        processApkBtn.hide();
        selectApkBtn.show();
    }
    private void setupTabs() {
        // Create tabs with icons for a modern App Cloner-like appearance
        TabLayout.Tab installedTab = tabLayout.newTab()
            .setText("Installed Apps")
            .setIcon(R.drawable.ic_tab_installed);
        TabLayout.Tab clonedTab = tabLayout.newTab()
            .setText("Cloned Apps")
            .setIcon(R.drawable.ic_tab_cloned);
            
        tabLayout.addTab(installedTab);
        tabLayout.addTab(clonedTab);
        
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0:
                        installedAppsList.setVisibility(View.VISIBLE);
                        clonedAppsList.setVisibility(View.GONE);
                        break;
                    case 1:
                        installedAppsList.setVisibility(View.GONE);
                        clonedAppsList.setVisibility(View.VISIBLE);
                        break;
                }
                hideSettingsEditor();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }
    private void setupClickListeners() {
        selectApkBtn.setOnClickListener(v -> pickInput());
        processApkBtn.setOnClickListener(v -> handleProcessAction());
        installedAppsList.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = allApps.get(position);
            hideSettingsEditor();
            showSettingsEditorForApp(app, false);
            try {
                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(app.packageName, 0);
                String sourceApkPath = appInfo.sourceDir;
                if (sourceApkPath == null || sourceApkPath.isEmpty()) {
                    Log.e(TAG, "Could not get source APK path for: " + app.packageName);
                    Toast.makeText(MainActivity.this, "Error: Cannot access APK for " + app.appName, Toast.LENGTH_SHORT).show();
                    clearSelection();
                    return;
                }
                File sourceApkFile = new File(sourceApkPath);
                if (!sourceApkFile.exists()) {
                    Log.e(TAG, "Source APK file does not exist: " + sourceApkPath);
                    Toast.makeText(MainActivity.this, "Error: APK file not found on disk.", Toast.LENGTH_SHORT).show();
                    clearSelection();
                    return;
                }
                File cachedApkDir = new File(getCacheDir(), "apk_cache");
                cachedApkDir.mkdirs();
                File cachedApkFile = new File(cachedApkDir, app.packageName + "_source.apk");
                try (FileInputStream fis = new FileInputStream(sourceApkFile);
                     FileOutputStream fos = new FileOutputStream(cachedApkFile);
                     FileChannel inChannel = fis.getChannel();
                     FileChannel outChannel = fos.getChannel()) {
                    inChannel.transferTo(0, inChannel.size(), outChannel);
                    inputApkUri = Uri.fromFile(cachedApkFile);
                    selectedAppInfo = new SelectedAppInfo(app.packageName, app.appName, cachedApkFile);
                    selectedTxt.setText("Selected App: " + app.appName);
                    statusTxt.setText("App selected. Ready to process.");
                    Toast.makeText(MainActivity.this, "Selected: " + app.appName, Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to copy APK to cache", e);
                    Toast.makeText(MainActivity.this, "Error: Failed to access app APK.", Toast.LENGTH_SHORT).show();
                    clearSelection();
                    cachedApkFile.delete();
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "App not found: " + app.packageName, e);
                Toast.makeText(MainActivity.this, "Error: App not found", Toast.LENGTH_SHORT).show();
                clearSelection();
            }
        });
        clonedAppsList.setOnItemClickListener((parent, view, position, id) -> {
            AppInfo app = clonedApps.get(position);
            hideSettingsEditor();
            showSettingsEditorForApp(app, true);
        });
    }
    private void showSettingsEditorForApp(AppInfo app, boolean isClonedApp) {
        hideSettingsEditor();
        File clonerJsonToUse = null;
        if (isClonedApp) {
            try {
                ApplicationInfo appInfo = getPackageManager().getApplicationInfo(app.packageName, 0);
                String sourceApkPath = appInfo.sourceDir;
                if (sourceApkPath != null && !sourceApkPath.isEmpty()) {
                    File sourceApkFile = new File(sourceApkPath);
                    if (sourceApkFile.exists()) {
                        tempClonerJsonForEditor = new File(getCacheDir(), app.packageName + "_temp_cloner.json");
                        boolean jsonExtracted = false;
                        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceApkFile))) {
                            ZipEntry entry;
                            while ((entry = zis.getNextEntry()) != null) {
                                if ("assets/cloner.json".equals(entry.getName())) {
                                    try (FileOutputStream fos = new FileOutputStream(tempClonerJsonForEditor)) {
                                        copyStream(zis, fos);
                                        jsonExtracted = true;
                                    }
                                    break;
                                }
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error extracting cloner.json from cloned APK: " + app.packageName, e);
                        }
                        if (jsonExtracted) {
                            clonerJsonToUse = tempClonerJsonForEditor;
                            sourceApkFileForEditor = sourceApkFile;
                        } else {
                            if (CloneSettings.generateClonerJson(this, tempClonerJsonForEditor)) {
                                clonerJsonToUse = tempClonerJsonForEditor;
                                sourceApkFileForEditor = sourceApkFile;
                            } else {
                                Log.e(TAG, "Failed to generate default cloner.json for cloned app: " + app.packageName);
                                Toast.makeText(this, "Error: Could not prepare settings file for " + app.appName, Toast.LENGTH_LONG).show();
                                tempClonerJsonForEditor.delete();
                                tempClonerJsonForEditor = null;
                                return;
                            }
                        }
                    }
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Cloned app not found: " + app.packageName, e);
                Toast.makeText(this, "Error: Cloned app not found: " + app.appName, Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            clonerJsonToUse = clonerJsonFile;
            if (!clonerJsonToUse.exists()) {
                if (!CloneSettings.generateClonerJson(this, clonerJsonToUse)) {
                    Log.e(TAG, "Failed to generate default cloner.json");
                    Toast.makeText(this, "Error: Could not prepare settings file.", Toast.LENGTH_LONG).show();
                    return;
                }
            }
            // Load app name from target app and populate cloner.json
            try {
                String jsonContent = readString(clonerJsonToUse);
                JSONObject json = new JSONObject(jsonContent);
                if (app != null && app.appName != null && !app.appName.isEmpty()) {
                    // Pre-populate app_name field with the original app's name
                    json.put("app_name", app.appName);
                    try (FileWriter fw = new FileWriter(clonerJsonToUse)) {
                        fw.write(json.toString(2));
                    }
                    Log.d(TAG, "Loaded app name from target app: " + app.appName);
                }
            } catch (JSONException | IOException e) {
                Log.e(TAG, "Error loading app name into cloner.json", e);
            }
        }
        if (clonerJsonToUse == null || !clonerJsonToUse.exists()) {
            Toast.makeText(this, "Settings file not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            final JSONObject json = new JSONObject(readString(clonerJsonToUse));
            if (!json.has(CLONING_MODE_KEY)) {
                json.put(CLONING_MODE_KEY, CLONING_MODE_REPLACE);
            }

            // Ensure all default settings are present (merging defaults into loaded JSON)
            JSONObject defaults = CloneSettings.createDefaultSettings();
            Iterator<String> keys = defaults.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!json.has(key)) {
                    json.put(key, defaults.get(key));
                }
            }

            ListView settingsListView = new ListView(this);
            final List<Setting> settingsList = buildSettingsList(json);
            if (!isClonedApp) {
                bundleDataSetting = new Setting(BUNDLE_DATA_SETTING_KEY, bundledDataDisplayName, String.class);
                settingsList.add(bundleDataSetting);

                liveVideoSetting = new Setting(LIVE_VIDEO_SETTING_KEY, liveVideoDisplayName, String.class);
                settingsList.add(liveVideoSetting);
            } else {
                bundleDataSetting = null;
                liveVideoSetting = null;
            }
            final SettingsAdapter adapter = new SettingsAdapter(settingsList, json);
            settingsListView.setAdapter(adapter);
            currentSettingsAdapter = isClonedApp ? null : adapter;
            if (!isClonedApp) {
                refreshBundleDataUi();
                refreshLiveVideoUi();
            }

            settingsListView.setOnItemClickListener((parent, view, position, id) -> {
                Setting setting = (Setting) adapter.getItem(position);
                if (setting != null && setting.key != null && setting.key.startsWith("cat_")) {
                    showCategoryDetailDialog(setting, json, adapter);
                } else if (setting != null) {
                    showEditorDialogForSetting(setting, json, adapter);
                }
            });

            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(android.view.Gravity.CENTER_HORIZONTAL);

            // Add app icon preview at the top (for both cloned and non-cloned apps)
            if (app != null && app.icon != null) {
                com.google.android.material.card.MaterialCardView iconCard = new com.google.android.material.card.MaterialCardView(this);
                float density = getResources().getDisplayMetrics().density;
                int cardMargin = (int) (12 * density);
                int cardPadding = (int) (16 * density);
                
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                cardParams.setMargins(cardMargin, cardMargin, cardMargin, (int)(8 * density));
                iconCard.setLayoutParams(cardParams);
                iconCard.setCardElevation(2 * density);
                iconCard.setRadius(8 * density);
                
                LinearLayout iconLayout = new LinearLayout(this);
                iconLayout.setOrientation(LinearLayout.HORIZONTAL);
                iconLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);
                iconLayout.setPadding(cardPadding, cardPadding, cardPadding, cardPadding);
                
                ImageView appIconView = new ImageView(this);
                int iconSize = (int) (56 * density);
                LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
                iconParams.setMarginEnd((int)(12 * density));
                appIconView.setLayoutParams(iconParams);
                appIconView.setImageDrawable(app.icon);
                iconLayout.addView(appIconView);
                
                LinearLayout textLayout = new LinearLayout(this);
                textLayout.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f);
                textLayout.setLayoutParams(textParams);
                
                TextView appNameView = new TextView(this);
                appNameView.setText(app.appName != null ? app.appName : "Unknown App");
                appNameView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16);
                appNameView.setTypeface(appNameView.getTypeface(), android.graphics.Typeface.BOLD);
                appNameView.setTextColor(getResources().getColor(android.R.color.primary_text_light, null));
                textLayout.addView(appNameView);
                
                TextView packageView = new TextView(this);
                packageView.setText(app.packageName != null ? app.packageName : "");
                packageView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
                packageView.setTextColor(getResources().getColor(android.R.color.secondary_text_light, null));
                packageView.setAlpha(0.75f);
                LinearLayout.LayoutParams packageParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                packageParams.topMargin = (int)(4 * density);
                packageView.setLayoutParams(packageParams);
                textLayout.addView(packageView);
                
                iconLayout.addView(textLayout);
                iconCard.addView(iconLayout);
                container.addView(iconCard);
            }

            LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
            settingsListView.setLayoutParams(listParams);
            container.addView(settingsListView);

            currentSettingsJson = json;
            currentSettingsFile = clonerJsonToUse;
            currentSettingsIsClonedApp = isClonedApp;
            currentSettingsApp = app;

            currentSettingsEditorView = container;
            settingsEditorContainer.removeAllViews();
            settingsEditorContainer.addView(currentSettingsEditorView);
            settingsEditorContainer.setVisibility(View.VISIBLE);
            processApkBtn.show();
            processApkBtn.setEnabled(true);
            processApkBtn.setContentDescription(isClonedApp ? "Save and install updated clone" : "Process and patch APK");
            selectApkBtn.hide();
        } catch (JSONException | IOException e) {
            Log.e(TAG, "Error reading or parsing cloner.json for app: " + app.packageName, e);
            Toast.makeText(this, "Error opening settings editor: " + e.getMessage(), Toast.LENGTH_LONG).show();
            if (tempClonerJsonForEditor != null) {
                tempClonerJsonForEditor.delete();
                tempClonerJsonForEditor = null;
            }
            sourceApkFileForEditor = null;
        }
    }
    private void hideSettingsEditor() {
        if (currentSettingsEditorView != null) {
            settingsEditorContainer.removeView(currentSettingsEditorView);
            currentSettingsEditorView = null;
        }
        settingsEditorContainer.setVisibility(View.GONE);
        processApkBtn.hide();
        processApkBtn.setEnabled(false);
        selectApkBtn.show();
        if (tempClonerJsonForEditor != null && tempClonerJsonForEditor.exists()) {
            tempClonerJsonForEditor.delete();
        }
        tempClonerJsonForEditor = null;
        sourceApkFileForEditor = null;
        if (bundledDataFileForCloning != null && bundledDataFileForCloning.exists()) {
            bundledDataFileForCloning.delete();
        }
        bundledDataFileForCloning = null;
        bundledDataDisplayName = null;
        bundleDataSetting = null;

        if (liveVideoFileForCloning != null && liveVideoFileForCloning.exists()) {
            liveVideoFileForCloning.delete();
        }
        liveVideoFileForCloning = null;
        liveVideoDisplayName = null;
        liveVideoSetting = null;

        currentSettingsAdapter = null;
        bundleDataDialogSummary = null;
        bundleDataDialogClearButton = null;
        currentSettingsJson = null;
        currentSettingsFile = null;
        currentSettingsApp = null;
        currentSettingsIsClonedApp = false;
    }
    private void loadInstalledApplications() {
        statusTxt.setText("Loading applications...");
        new Thread(() -> {
            try {
                allApps.clear();
                clonedApps.clear();
                PackageManager pm = getPackageManager();
                List<PackageInfo> packages = pm.getInstalledPackages(
                    PackageManager.GET_PROVIDERS | PackageManager.GET_META_DATA);
                int clonedCount = 0;
                for (PackageInfo packageInfo : packages) {
                if ((packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    continue;
                }
                boolean isCloned = hasInjectedProvider(packageInfo);
                AppInfo appInfo = new AppInfo();
                appInfo.packageName = packageInfo.packageName;
                appInfo.appName = packageInfo.applicationInfo.loadLabel(pm).toString();
                appInfo.icon = packageInfo.applicationInfo.loadIcon(pm);
                if (isCloned) {
                    clonedApps.add(appInfo);
                    clonedCount++;
                    continue;
                }
                allApps.add(appInfo);
            }
                final int finalClonedCount = clonedCount;
                runOnUiThread(() -> {
                    updateAppLists();
                    statusTxt.setText("Loaded " + allApps.size() + " apps (" + finalClonedCount + " cloned)");
                });
            } catch (Exception e) {
                Log.e(TAG, "Error loading applications", e);
                runOnUiThread(() -> {
                    statusTxt.setText("Error loading applications: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Failed to load apps", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    private boolean hasInjectedProvider(PackageInfo packageInfo) {
        try {
            if (packageInfo.providers != null) {
                for (android.content.pm.ProviderInfo provider : packageInfo.providers) {
                    if (provider != null && provider.authority != null) {
                        if (provider.authority.endsWith(INJECTED_PROVIDER_SUFFIX)) {
                            return true;
                        }
                    }
                }
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking providers for " + packageInfo.packageName, e);
            return false;
        }
    }
    
    /**
     * Extracts app information (name, icon, package) from an APK file via Uri.
     * This is used when importing APK files via SAF (Storage Access Framework).
     * @param apkUri The URI of the APK file
     * @return AppInfo object with extracted data, or null if extraction fails
     */
    private AppInfo extractAppInfoFromApk(Uri apkUri) {
        if (apkUri == null) {
            return null;
        }
        
        try {
            // Copy APK to cache for PackageManager to read
            File tempApkFile = new File(getCacheDir(), "temp_import_" + System.currentTimeMillis() + ".apk");
            try (InputStream is = getContentResolver().openInputStream(apkUri);
                 FileOutputStream fos = new FileOutputStream(tempApkFile)) {
                if (is == null) {
                    Log.e(TAG, "Failed to open input stream for APK: " + apkUri);
                    return null;
                }
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }
            
            // Use PackageManager to extract app info from APK
            PackageManager pm = getPackageManager();
            PackageInfo packageInfo = pm.getPackageArchiveInfo(tempApkFile.getAbsolutePath(), 
                PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
            
            if (packageInfo == null) {
                Log.e(TAG, "Failed to get package info from APK: " + apkUri);
                tempApkFile.delete();
                return null;
            }
            
            // Set application info source directory so icons can be loaded
            packageInfo.applicationInfo.sourceDir = tempApkFile.getAbsolutePath();
            packageInfo.applicationInfo.publicSourceDir = tempApkFile.getAbsolutePath();
            
            AppInfo appInfo = new AppInfo();
            appInfo.packageName = packageInfo.packageName;
            appInfo.appName = packageInfo.applicationInfo.loadLabel(pm).toString();
            appInfo.icon = packageInfo.applicationInfo.loadIcon(pm);
            
            Log.d(TAG, "Successfully extracted app info from APK: " + appInfo.appName + " (" + appInfo.packageName + ")");
            
            // Clean up temp file after a delay (keep it for now so icon can be loaded)
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    if (tempApkFile.exists()) {
                        tempApkFile.delete();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            return appInfo;
        } catch (Exception e) {
            Log.e(TAG, "Error extracting app info from APK: " + apkUri, e);
            return null;
        }
    }
    private void updateAppLists() {
        allAppsAdapter = new AppListAdapter(allApps);
        clonedAppsAdapter = new AppListAdapter(clonedApps);
        installedAppsList.setAdapter(allAppsAdapter);
        clonedAppsList.setAdapter(clonedAppsAdapter);
        installedAppsList.setVisibility(View.VISIBLE);
        clonedAppsList.setVisibility(View.GONE);
        if (allAppsAdapter != null) allAppsAdapter.notifyDataSetChanged();
        if (clonedAppsAdapter != null) clonedAppsAdapter.notifyDataSetChanged();
    }
    private void pickInput() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/vnd.android.package-archive");
            pickApk.launch(i);
        } catch (Exception e) {
            Log.e(TAG, "Error launching APK picker", e);
            Toast.makeText(this, "Error opening file picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void pickOutput() {
        try {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/vnd.android.package-archive")
                    .putExtra(Intent.EXTRA_TITLE, "patched.apk");
            createApk.launch(i);
        } catch (Exception e) {
            Log.e(TAG, "Error launching output file picker", e);
            Toast.makeText(this, "Error opening file picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void handleProcessAction() {
        if (currentSettingsIsClonedApp) {
            if (!persistCurrentSettings()) {
                return;
            }
            if (currentSettingsApp == null || tempClonerJsonForEditor == null || sourceApkFileForEditor == null) {
                Toast.makeText(MainActivity.this, "Unable to update clone. Please reopen the editor and try again.", Toast.LENGTH_LONG).show();
                return;
            }
            injectUpdatedJsonAndInstall(tempClonerJsonForEditor, sourceApkFileForEditor, currentSettingsApp);
            hideSettingsEditor();
        } else {
            if (!persistCurrentSettings()) {
                return;
            }
            pickOutput();
        }
    }
    private boolean persistCurrentSettings() {
        if (currentSettingsJson == null || currentSettingsFile == null) {
            return true;
        }
        try (FileWriter fw = new FileWriter(currentSettingsFile)) {
            fw.write(currentSettingsJson.toString(2));
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Error writing settings file", e);
            Toast.makeText(MainActivity.this, "Failed to save settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        } catch (JSONException e) {
            Log.e(TAG, "Error serializing settings JSON", e);
            Toast.makeText(MainActivity.this, "Failed to prepare settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return false;
    }
    private void pickDataFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            intent.putExtra(Intent.EXTRA_TITLE, "Select Data ZIP to Bundle");
            pickDataFile.launch(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error launching file picker", e);
            Toast.makeText(this, "Error opening file picker: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    private void clearSelection() {
        if (selectedAppInfo != null && selectedAppInfo.cachedApkFile != null && selectedAppInfo.cachedApkFile.exists()) {
            selectedAppInfo.cachedApkFile.delete();
        }
        inputApkUri = null;
        selectedAppInfo = null;
        selectedTxt.setText("Select an APK");
        processApkBtn.setEnabled(false);
        hideSettingsEditor();
    }
    private void startProcessing() {
        if (inputApkUri == null || outputApkUri == null) {
            Toast.makeText(this, "Pick input & output first", Toast.LENGTH_LONG).show();
            return;
        }
        if (!isValidApk(inputApkUri)) {
            statusTxt.setText("Error: Invalid APK file");
            return;
        }
        try {
            File hookDex    = new File(getCacheDir(), "hook.dex");
            File libWorkDir = new File(getCacheDir(), "hook_libs");
            libWorkDir.mkdirs();
            copyAsset("dash", hookDex);
            if (!clonerJsonFile.exists()) {
                CloneSettings.generateClonerJson(this, clonerJsonFile);
            }

            // Update bundle_app_data boolean based on whether we have a file
            // This ensures cloner.json has the correct boolean trigger for AppDataManager
            // We do this AFTER ensuring clonerJsonFile exists (via generateClonerJson above)
            if (clonerJsonFile.exists()) {
                try {
                    String content = readString(clonerJsonFile);
                    JSONObject json = new JSONObject(content);
                    boolean hasBundle = bundledDataFileForCloning != null || bundledDataDisplayName != null;
                    json.put(BUNDLE_DATA_SETTING_KEY, hasBundle);
                    try (FileWriter fw = new FileWriter(clonerJsonFile)) {
                        fw.write(json.toString(2));
                    }
                } catch (Exception e) {
                     Log.e(TAG, "Failed to update bundle_app_data flag in cloner.json", e);
                }
            }

            // Copy arm64-v8a libs
            File arm64Dir = new File(libWorkDir, "arm64-v8a");
            arm64Dir.mkdirs();
            copyAsset("lib/arm64-v8a/libaliuhook.so",   new File(arm64Dir, "libaliuhook.so"));
            copyAsset("lib/arm64-v8a/liblsplant.so",    new File(arm64Dir, "liblsplant.so"));
            copyAsset("lib/arm64-v8a/libc++_shared.so", new File(arm64Dir, "libc++_shared.so"));

            // Copy armeabi-v7a libs
            File armDir = new File(libWorkDir, "armeabi-v7a");
            armDir.mkdirs();
            copyAsset("lib/armeabi-v7a/libaliuhook.so",   new File(armDir, "libaliuhook.so"));
            copyAsset("lib/armeabi-v7a/liblsplant.so",    new File(armDir, "liblsplant.so"));
            copyAsset("lib/armeabi-v7a/libc++_shared.so", new File(armDir, "libc++_shared.so"));

            new Thread(() -> {
                try {
                    // Process Icon (inside thread)
                    File processedIconFile = new File(getCacheDir(), "_launcher_icon.png");
                    if (processedIconFile.exists()) {
                        processedIconFile.delete();
                    }

                    try {
                        // Read settings from cloner.json or current settings
                        JSONObject json = new JSONObject(readString(clonerJsonFile));
                        String color = json.optString("icon_color", CloneSettings.DEFAULT_ICON_COLOR);
                        float hue = (float) json.optDouble("icon_hue", CloneSettings.DEFAULT_ICON_HUE);
                        float saturation = (float) json.optDouble("icon_saturation", CloneSettings.DEFAULT_ICON_SATURATION);
                        float lightness = (float) json.optDouble("icon_lightness", CloneSettings.DEFAULT_ICON_LIGHTNESS);
                        boolean autoHue = json.optBoolean("icon_auto_hue", CloneSettings.DEFAULT_ICON_AUTO_HUE);
                        boolean invertColors = json.optBoolean("icon_invert_colors", CloneSettings.DEFAULT_ICON_INVERT_COLORS);
                        boolean sepia = json.optBoolean("icon_sepia", CloneSettings.DEFAULT_ICON_SEPIA);
                        int rotation = json.optInt("icon_rotation", CloneSettings.DEFAULT_ICON_ROTATION);
                        boolean flipH = json.optBoolean("icon_flip_horizontal", CloneSettings.DEFAULT_ICON_FLIP_HORIZONTAL);
                        boolean flipV = json.optBoolean("icon_flip_vertical", CloneSettings.DEFAULT_ICON_FLIP_VERTICAL);
                        String badge = json.optString("icon_badge", CloneSettings.DEFAULT_ICON_BADGE);
                        int badgePos = json.optInt("icon_badge_position", CloneSettings.DEFAULT_ICON_BADGE_POSITION);

                        Drawable originalIcon = null;
                        if (selectedAppInfo != null) {
                            // Re-load full res icon if possible
                            try {
                                // Check if we have the cached source APK
                                if (selectedAppInfo.cachedApkFile != null && selectedAppInfo.cachedApkFile.exists()) {
                                    android.content.pm.PackageManager pm = getPackageManager();
                                    android.content.pm.PackageInfo pi = pm.getPackageArchiveInfo(selectedAppInfo.cachedApkFile.getAbsolutePath(), 0);
                                    if (pi != null) {
                                        pi.applicationInfo.sourceDir = selectedAppInfo.cachedApkFile.getAbsolutePath();
                                        pi.applicationInfo.publicSourceDir = selectedAppInfo.cachedApkFile.getAbsolutePath();
                                        originalIcon = pi.applicationInfo.loadIcon(pm);
                                    }
                                }

                                // Fallback to memory
                                if (originalIcon == null && selectedAppInfo.packageName != null) {
                                    android.content.pm.ApplicationInfo ai = getPackageManager().getApplicationInfo(selectedAppInfo.packageName, 0);
                                    originalIcon = ai.loadIcon(getPackageManager());
                                }
                            } catch (Exception e) {
                                Log.w(TAG, "Failed to load high-res icon, using default", e);
                            }
                        }

                        if (originalIcon != null) {
                            IconProcessor.processAndSaveIcon(MainActivity.this, originalIcon, color, rotation, flipH, flipV,
                                    hue, saturation, lightness, autoHue, invertColors, sepia,
                                    badge, badgePos, processedIconFile);
                        } else {
                            processedIconFile = null; // No icon to process
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Icon processing failed", e);
                        processedIconFile = null;
                    }

                    // Ensure ApkProcessor.java is present in the correct package location
                    new ApkProcessor(MainActivity.this).injectHook(
                            inputApkUri, outputApkUri, hookDex, clonerJsonFile, libWorkDir, bundledDataFileForCloning, liveVideoFileForCloning, processedIconFile);

                    File finalProcessedIconFile = processedIconFile;
                    runOnUiThread(() -> {
                        statusTxt.setText("Done");
                        Toast.makeText(this, "APK patched successfully!", Toast.LENGTH_LONG).show();
                        boolean hadBundledData = bundledDataFileForCloning != null || bundledDataDisplayName != null;
                        if (bundledDataFileForCloning != null && bundledDataFileForCloning.exists()) {
                            bundledDataFileForCloning.delete();
                        }
                        if (hadBundledData) {
                            bundledDataFileForCloning = null;
                            bundledDataDisplayName = null;
                            refreshBundleDataUi();
                        }

                        boolean hadLiveVideo = liveVideoFileForCloning != null || liveVideoDisplayName != null;
                        if (liveVideoFileForCloning != null && liveVideoFileForCloning.exists()) {
                            liveVideoFileForCloning.delete();
                        }
                        if (hadLiveVideo) {
                            liveVideoFileForCloning = null;
                            liveVideoDisplayName = null;
                            refreshLiveVideoUi();
                        }

                        if (finalProcessedIconFile != null && finalProcessedIconFile.exists()) {
                            finalProcessedIconFile.delete();
                        }
                        // Proceed directly with installation (no signing note dialog)
                        proceedWithInstallation(outputApkUri);
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Patch error", e);
                    runOnUiThread(() -> {
                        statusTxt.setText("Error: " + e.getMessage());
                        Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        boolean hadBundledData = bundledDataFileForCloning != null || bundledDataDisplayName != null;
                        if (bundledDataFileForCloning != null && bundledDataFileForCloning.exists()) {
                            bundledDataFileForCloning.delete();
                        }
                        if (hadBundledData) {
                            bundledDataFileForCloning = null;
                            bundledDataDisplayName = null;
                            refreshBundleDataUi();
                        }
                        clearSelection();
                        outputApkUri = null;
                    });
                }
            }).start();
        } catch (IOException e) {
            Log.e(TAG, "Asset copy error", e);
            statusTxt.setText("Asset error: " + e.getMessage());
            boolean hadBundledData = bundledDataFileForCloning != null || bundledDataDisplayName != null;
            if (bundledDataFileForCloning != null && bundledDataFileForCloning.exists()) {
                bundledDataFileForCloning.delete();
            }
            if (hadBundledData) {
                bundledDataFileForCloning = null;
                bundledDataDisplayName = null;
                refreshBundleDataUi();
            }
            clearSelection();
            outputApkUri = null;
        }
    }
    private void proceedWithInstallation(Uri apkUri) {
        if (apkUri == null) {
            Log.e(TAG, "proceedWithInstallation: APK URI is null");
            Toast.makeText(this, "Error: APK file not found", Toast.LENGTH_LONG).show();
            return;
        }

        // For content:// URIs from SAF, we need to copy the APK to a location accessible by FileProvider
        // to ensure the package installer can access it
        if ("content".equals(apkUri.getScheme())) {
            statusTxt.setText("Preparing installation...");
            new Thread(() -> {
                File tempApk = null;
                try {
                    // Copy APK from SAF to cache directory
                    File cacheDir = getCacheDir();
                    tempApk = new File(cacheDir, "install_" + System.currentTimeMillis() + ".apk");
                    
                    try (InputStream is = getContentResolver().openInputStream(apkUri);
                         FileOutputStream fos = new FileOutputStream(tempApk)) {
                        if (is == null) {
                            throw new IOException("Could not open input stream for APK");
                        }
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, bytesRead);
                        }
                    }
                    
                    // Check for signature mismatch BEFORE launching installer
                    final File finalTempApk = tempApk;
                    final String apkPackageName = getPackageNameFromApk(finalTempApk);
                    
                    runOnUiThread(() -> {
                        try {
                            // Check if app is already installed and has signature mismatch
                            if (apkPackageName != null && isAppInstalled(apkPackageName)) {
                                if (hasSignatureMismatch(apkPackageName, finalTempApk)) {
                                    // Signature mismatch detected - need to uninstall first
                                    showSignatureMismatchDialog(apkPackageName, finalTempApk);
                                    return;
                                }
                            }
                            
                            String authority = getPackageName() + ".fileprovider";
                            Uri fileProviderUri = FileProvider.getUriForFile(
                                    MainActivity.this, authority, finalTempApk);
                            
                            boolean installerLaunched = launchInstallerForUri(
                                    fileProviderUri,
                                    "Launching installer...",
                                    "Installer launched. Confirm installation to finish."
                            );
                            
                            if (installerLaunched) {
                                loadInstalledApplications();
                                clearSelection();
                                outputApkUri = null;
                            } else {
                                // Clean up on failure
                                if (finalTempApk.exists()) {
                                    finalTempApk.delete();
                                }
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error creating FileProvider URI", e);
                            statusTxt.setText("Install Error: " + e.getMessage());
                            Toast.makeText(MainActivity.this, 
                                    "Error preparing installation: " + e.getMessage(), 
                                    Toast.LENGTH_LONG).show();
                            if (finalTempApk.exists()) {
                                finalTempApk.delete();
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error copying APK for installation", e);
                    final File apkToDelete = tempApk;
                    runOnUiThread(() -> {
                        statusTxt.setText("Install Error: " + e.getMessage());
                        Toast.makeText(MainActivity.this, 
                                "Error preparing installation: " + e.getMessage(), 
                                Toast.LENGTH_LONG).show();
                        if (apkToDelete != null && apkToDelete.exists()) {
                            apkToDelete.delete();
                        }
                    });
                }
            }).start();
        } else {
            // For file:// URIs, wrap with FileProvider to avoid FileUriExposed crashes
            Uri installableUri = apkUri;
            File apkFile = null;
            if ("file".equals(apkUri.getScheme())) {
                try {
                    apkFile = new File(apkUri.getPath());
                    
                    // Check for signature mismatch BEFORE launching installer
                    String apkPackageName = getPackageNameFromApk(apkFile);
                    if (apkPackageName != null && isAppInstalled(apkPackageName)) {
                        if (hasSignatureMismatch(apkPackageName, apkFile)) {
                            // Signature mismatch detected - need to uninstall first
                            showSignatureMismatchDialog(apkPackageName, apkFile);
                            return;
                        }
                    }
                    
                    String authority = getPackageName() + ".fileprovider";
                    installableUri = FileProvider.getUriForFile(this, authority, apkFile);
                } catch (Exception e) {
                    Log.e(TAG, "Error converting file URI for installer", e);
                    statusTxt.setText("Installer Error: " + e.getMessage());
                    Toast.makeText(this, "Error preparing installer URI: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    return;
                }
            }

            boolean installerLaunched = launchInstallerForUri(
                    installableUri,
                    "Launching installer...",
                    "Installer launched. Confirm installation to finish."
            );
            if (installerLaunched) {
                loadInstalledApplications();
                clearSelection();
                outputApkUri = null;
            }
        }
    }
    
    /**
     * Gets the package name from an APK file.
     */
    private String getPackageNameFromApk(File apkFile) {
        try {
            PackageManager pm = getPackageManager();
            PackageInfo info = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), 0);
            if (info != null) {
                return info.packageName;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting package name from APK", e);
        }
        return null;
    }
    
    /**
     * Checks if an app is already installed.
     */
    private boolean isAppInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }
    
    /**
     * Checks if the APK has a signature mismatch with the installed app.
     */
    private boolean hasSignatureMismatch(String packageName, File apkFile) {
        try {
            PackageManager pm = getPackageManager();
            
            // Get signatures from installed app
            android.content.pm.Signature[] installedSigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo installedInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
                if (installedInfo.signingInfo == null) {
                    return false;
                }
                installedSigs = installedInfo.signingInfo.getApkContentsSigners();
            } else {
                PackageInfo installedInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES);
                installedSigs = installedInfo.signatures;
            }
            
            if (installedSigs == null || installedSigs.length == 0) {
                return false;
            }
            
            // Get signatures from APK file
            android.content.pm.Signature[] apkSigs;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageInfo apkInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES);
                if (apkInfo == null || apkInfo.signingInfo == null) {
                    return false;
                }
                apkSigs = apkInfo.signingInfo.getApkContentsSigners();
            } else {
                PackageInfo apkInfo = pm.getPackageArchiveInfo(apkFile.getAbsolutePath(), PackageManager.GET_SIGNATURES);
                if (apkInfo == null) {
                    return false;
                }
                apkSigs = apkInfo.signatures;
            }
            
            if (apkSigs == null || apkSigs.length == 0) {
                return false;
            }
            
            // Compare signatures
            if (installedSigs.length != apkSigs.length) {
                return true; // Different number of signatures = mismatch
            }
            
            for (int i = 0; i < installedSigs.length; i++) {
                if (!installedSigs[i].equals(apkSigs[i])) {
                    return true; // Signature mismatch
                }
            }
            
            return false; // Signatures match
        } catch (PackageManager.NameNotFoundException e) {
            // App not installed, no mismatch
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error checking signature mismatch", e);
            return false;
        }
    }
    
    /**
     * Shows a dialog when signature mismatch is detected, offering to uninstall first.
     */
    private void showSignatureMismatchDialog(String packageName, File apkFile) {
        String appName = packageName;
        try {
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(packageName, 0);
            appName = getPackageManager().getApplicationLabel(appInfo).toString();
        } catch (PackageManager.NameNotFoundException e) {
            // Use package name as fallback
        }
        
        final String finalAppName = appName;
        new AlertDialog.Builder(this)
            .setTitle("Signature Mismatch")
            .setMessage("The app \"" + finalAppName + "\" is already installed with a different signature. " +
                       "To install this version, the existing app must be uninstalled first.\n\n" +
                       "Note: All app data will be lost.\n\n" +
                       "Do you want to uninstall the existing app and then install this version?")
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setPositiveButton("Uninstall & Install", (dialog, which) -> {
                // Store pending install APK
                try {
                    String authority = getPackageName() + ".fileprovider";
                    pendingInstallApkUri = FileProvider.getUriForFile(this, authority, apkFile);
                    pendingUninstallForInstallPackage = packageName;
                    
                    // Launch uninstall
                    Uri packageUri = Uri.parse("package:" + packageName);
                    Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
                    uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    
                    if (uninstallIntent.resolveActivity(getPackageManager()) != null) {
                        statusTxt.setText("Uninstalling " + finalAppName + "...");
                        installAfterUninstallLauncher.launch(uninstallIntent);
                    } else {
                        Toast.makeText(this, "Cannot uninstall: No system handler found", Toast.LENGTH_LONG).show();
                        pendingInstallApkUri = null;
                        pendingUninstallForInstallPackage = null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error starting uninstall for signature mismatch", e);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    pendingInstallApkUri = null;
                    pendingUninstallForInstallPackage = null;
                }
            })
            .setNegativeButton("Cancel", (dialog, which) -> {
                statusTxt.setText("Installation canceled due to signature mismatch");
                if (apkFile.exists()) {
                    apkFile.delete();
                }
            })
            .setCancelable(false)
            .show();
    }

    private boolean launchInstallerForUri(Uri apkUri, String successToast, String successStatus) {
        if (apkUri == null) {
            Log.e(TAG, "launchInstallerForUri: APK URI is null");
            statusTxt.setText("Installer Error: Missing APK");
            Toast.makeText(this, "Installer error: missing APK output.", Toast.LENGTH_LONG).show();
            return false;
        }
        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        installIntent.putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true);
        installIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            installIntent.putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, getPackageName());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            installIntent.setClipData(ClipData.newRawUri("processed_apk", apkUri));
        }
        if (getPackageManager().queryIntentActivities(installIntent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
            Log.w(TAG, "No handler for ACTION_INSTALL_PACKAGE, falling back to ACTION_VIEW");
            Intent fallback = new Intent(Intent.ACTION_VIEW);
            fallback.setDataAndType(apkUri, "application/vnd.android.package-archive");
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                fallback.setClipData(ClipData.newRawUri("processed_apk", apkUri));
            }
            if (getPackageManager().queryIntentActivities(fallback, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
                Log.e(TAG, "No application available to handle APK install for URI: " + apkUri);
                statusTxt.setText("Installer Error: App not found");
                Toast.makeText(this, "Error: Cannot find app to install APK. Check file manager.", Toast.LENGTH_LONG).show();
                return false;
            }
            installIntent = fallback;
        }
        try {
            startActivity(installIntent);
            if (successToast != null && !successToast.isEmpty()) {
                Toast.makeText(this, successToast, Toast.LENGTH_SHORT).show();
            }
            if (successStatus != null && !successStatus.isEmpty()) {
                statusTxt.setText(successStatus);
            }
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Installer activity not found for URI: " + apkUri, e);
            statusTxt.setText("Installer Error: App not found");
            Toast.makeText(this, "Error: Cannot find app to install APK. Check file manager.", Toast.LENGTH_LONG).show();
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while launching installer for URI: " + apkUri, e);
            statusTxt.setText("Installer Error: Permission denied");
            Toast.makeText(this, "Error launching installer: permission denied.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error launching installer for URI: " + apkUri, e);
            statusTxt.setText("Installer Error: " + e.getMessage());
            Toast.makeText(this, "Error launching installer: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return false;
    }
    private boolean isValidApk(Uri apkUri) {
        try {
            String mimeType = getContentResolver().getType(apkUri);
            return "application/vnd.android.package-archive".equals(mimeType) ||
                   (apkUri.getLastPathSegment() != null && apkUri.getLastPathSegment().endsWith(".apk"));
        } catch (Exception e) {
            Log.w(TAG, "Error validating APK URI", e);
            return false;
        }
    }
    private void copyAsset(String assetPath, File dst) throws IOException {
        if (assetPath == null || assetPath.isEmpty()) {
            throw new IllegalArgumentException("Asset path cannot be null or empty");
        }
        if (dst == null) {
            throw new IllegalArgumentException("Destination file cannot be null");
        }
        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8_192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
    }

    private static void copyFile(File source, File dest) throws IOException {
        if (source == null || dest == null) {
            throw new IllegalArgumentException("Source and destination files cannot be null");
        }
        if (!source.exists() || !source.isFile()) {
            throw new IOException("Source file does not exist: " + source);
        }
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create directory: " + parent);
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
        }
    }

    private static byte[] readAllBytes(File file) throws IOException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new IOException("File does not exist: " + file);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             InputStream in = new FileInputStream(file)) {
            copyStream(in, baos);
            return baos.toByteArray();
        }
    }
    private String readString(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new IOException("File does not exist: " + file);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        if (sb.length() > 0 && sb.charAt(sb.length() - 1) == '\n') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }
    private String randomAndroidId() {
        return String.format(Locale.US, "%016X", random.nextLong());
    }

    // FIXED: Correctly initialize the byte array with size 6
    private String randomMac() {
        byte[] macAddress = new byte[6]; // MAC addresses are 6 bytes
        random.nextBytes(macAddress);
        // Set the locally administered bit and unicast bit
        macAddress[0] = (byte) ((macAddress[0] & (byte) 252) | (byte) 2);
        StringBuilder sb = new StringBuilder(18);
        for (int i = 0; i < macAddress.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02x", macAddress[i] & 0xFF));
        }
        return sb.toString();
    }

    private String randomLatitude() {
        double lat = (random.nextDouble() * 180.0) - 90.0;
        return String.format("%.6f", lat);
    }
    private String randomLongitude() {
        double lon = (random.nextDouble() * 360.0) - 180.0;
        return String.format("%.6f", lon);
    }

    private boolean usesIdentityModes(String key) {
        return "android_id".equals(key) || "imei".equals(key) || "imsi".equals(key)
                || "wifi_mac".equals(key) || "bluetooth_mac".equals(key)
                || "serial_number".equals(key);
    }

    private boolean isNoChangeValue(Object value) {
        return value != null && CloneSettings.VALUE_MODE_NO_CHANGE.equalsIgnoreCase(value.toString());
    }

    private boolean isRandomValue(Object value) {
        return value != null && CloneSettings.VALUE_MODE_RANDOM.equalsIgnoreCase(value.toString());
    }

    /**
     * Generate a random Bluetooth MAC address.
     * Uses locally administered bit like WiFi MAC.
     */
    private String generateRandomBluetoothMac() {
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
     * Generate a random valid IMEI.
     * IMEI format: TAC (8 digits) + Serial (6 digits) + Check digit (1 digit) = 15 digits
     */
    private String generateRandomImei() {
        StringBuilder sb = new StringBuilder();
        
        // TAC (Type Allocation Code) - 8 digits, use common prefix
        sb.append("35");  // Common TAC prefix
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        
        // Serial Number - 6 digits
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        
        // Calculate and append Luhn check digit
        sb.append(calculateLuhnCheckDigit(sb.toString()));
        
        return sb.toString();
    }

    /**
     * Calculate Luhn check digit for IMEI validation.
     */
    private int calculateLuhnCheckDigit(String digits) {
        int sum = 0;
        boolean alternate = true;
        
        for (int i = digits.length() - 1; i >= 0; i--) {
            int digit = Character.getNumericValue(digits.charAt(i));
            
            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit = (digit / 10) + (digit % 10);
                }
            }
            
            sum += digit;
            alternate = !alternate;
        }
        
        return (10 - (sum % 10)) % 10;
    }

    /**
     * Generate a random serial number.
     * Format: Alphanumeric, typically 8-16 characters.
     */
    private String generateRandomSerial() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 10 + random.nextInt(6);  // 10-15 characters
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private void injectUpdatedJsonAndInstall(File updatedClonerJson, File sourceApkFile, AppInfo clonedApp) {
        statusTxt.setText("Updating & Installing...");
        new Thread(() -> {
            File tempRoot = new File(getCacheDir(), "update_" + System.currentTimeMillis());
            try {
                if (!tempRoot.mkdirs()) throw new IOException("Failed to create temp directory: " + tempRoot);
                String basePath = tempRoot.getCanonicalPath() + File.separator;
                boolean manifestFound = false;
                try (ZipInputStream zis = new ZipInputStream(new FileInputStream(sourceApkFile))) {
                    ZipEntry ze;
                    while ((ze = zis.getNextEntry()) != null) {
                        String name = ze.getName();
                        if (name == null || name.isEmpty()) continue;
                        if (SIG_PATH.matcher(name).matches()) continue;
                        if ("assets/cloner.json".equals(name)) continue;
                        if (ANDROID_MANIFEST.equals(name)) {
                            manifestFound = true;
                        }
                        File outFile = safeResolve(tempRoot, basePath, name);
                        if (ze.isDirectory()) {
                            if (!outFile.exists() && !outFile.mkdirs()) {
                                throw new IOException("Failed to mkdirs: " + outFile);
                            }
                            continue;
                        }
                        File parent = outFile.getParentFile();
                        if (parent != null && !parent.exists() && !parent.mkdirs()) {
                            throw new IOException("Failed to create parent: " + parent);
                        }
                        try (FileOutputStream fos = new FileOutputStream(outFile)) {
                            copyStream(zis, fos);
                        }
                    }
                }
                if (!manifestFound) {
                    throw new IOException("AndroidManifest.xml not found in source APK");
                }
                File assetsDir = new File(tempRoot, "assets");
                if (!assetsDir.exists() && !assetsDir.mkdirs()) {
                    throw new IOException("Failed to create assets dir");
                }
                copyFile(updatedClonerJson, new File(assetsDir, "cloner.json"));
                File unsignedApk = new File(getCacheDir(), "unsigned_updated_" + System.nanoTime() + ".apk");
                File manifestFile = new File(tempRoot, ANDROID_MANIFEST);
                byte[] manifestBytes = readAllBytes(manifestFile);
                zipDir(tempRoot, unsignedApk, manifestBytes);
                File signedApk = new File(getCacheDir(), "signed_updated_" + System.nanoTime() + ".apk");
                try {
                    signUpdatedApk(unsignedApk, signedApk);
                } finally {
                    unsignedApk.delete();
                    deleteRec(tempRoot);
                }
                runOnUiThread(() -> installApk(signedApk, clonedApp));
            } catch (Exception e) {
                Log.e(TAG, "Error updating/cloning app: " + clonedApp.packageName, e);
                runOnUiThread(() -> {
                    statusTxt.setText("Update Error: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Failed to update " + clonedApp.appName + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
                    if (updatedClonerJson != null && updatedClonerJson.exists()) {
                        updatedClonerJson.delete();
                    }
                    hideSettingsEditor();
                });
                deleteRec(tempRoot);
            }
        }).start();
    }
    private void signUpdatedApk(File unsignedApk, File signedApk) throws Exception {
        ApkSigner.SignerConfig signer = loadSignerConfigForUpdate();
        ApkSigner apkSigner = new ApkSigner.Builder(Collections.singletonList(signer))
                .setInputApk(unsignedApk)
                .setOutputApk(signedApk)
                .setV1SigningEnabled(true)
                .setV2SigningEnabled(true)
                .setV3SigningEnabled(false)  // Disabled for better compatibility
                .build();
        apkSigner.sign();
        try {
            Class<?> builderClass = Class.forName("com.android.apksig.ApkVerifier$Builder");
            Object builderInstance = builderClass.getConstructor(File.class).newInstance(signedApk);
            Object apkVerifierInstance = builderClass.getMethod("build").invoke(builderInstance);
            Object vRes = apkVerifierInstance.getClass().getMethod("verify").invoke(apkVerifierInstance);
            Boolean isVerified = (Boolean) vRes.getClass().getMethod("isVerified").invoke(vRes);
            if (isVerified != null && !isVerified) {
                Log.w(TAG, "Updated APK verification result: NOT VERIFIED (continuing)");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Verification skipped due to error", t);
        }
    }
    private void installApk(File apkToInstall, AppInfo clonedApp) {
        Log.d(TAG, "Preparing to install updated APK: " + apkToInstall.getName());
        try {
            String authority = getPackageName() + ".fileprovider";
            Uri apkUri = FileProvider.getUriForFile(this, authority, apkToInstall);
            boolean launched = launchInstallerForUri(
                    apkUri,
                    "Launching installer for updated " + clonedApp.appName,
                    "Installer launched for " + clonedApp.appName
            );
            if (!launched) {
                apkToInstall.delete();
            }
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid FileProvider configuration for APK installation", e);
            Toast.makeText(this, "Installer error: invalid output location.", Toast.LENGTH_LONG).show();
            statusTxt.setText("Installer Error: Invalid output");
            apkToInstall.delete();
        }
    }
    private ApkSigner.SignerConfig loadSignerConfigForUpdate() throws Exception {
        try (InputStream ksStream = getAssets().open(KEYSTORE_ASSET)) {
            KeyStore ks = KeyStore.getInstance("BKS");
            ks.load(ksStream, STORE_PWD.toCharArray());
            PrivateKey key = (PrivateKey) ks.getKey(ALIAS, KEY_PWD.toCharArray());
            if (key == null) throw new IllegalStateException("Private key is null for alias: " + ALIAS);
            X509Certificate cert = (X509Certificate) ks.getCertificate(ALIAS);
            if (cert == null) throw new IllegalStateException("Certificate is null for alias: " + ALIAS);
            return new ApkSigner.SignerConfig.Builder(ALIAS, key, Collections.singletonList(cert)).build();
        }
    }
    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
    }
    private static void deleteRec(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRec(k);
        }
        f.delete();
    }
    private void zipDir(File root, File outFile, byte[] manifestBytes) throws IOException {
        try (OutputStream os = new FileOutputStream(outFile);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {
            zos.setLevel(9);
            writeZip(root, zos, manifestBytes);
        }
    }
    private void writeZip(File root, ZipOutputStream zos, byte[] manifestBytes) throws IOException {
        ZipEntry manifestEntry = createZipEntry(ANDROID_MANIFEST, manifestBytes);
        zos.putNextEntry(manifestEntry);
        zos.write(manifestBytes);
        zos.closeEntry();
        addRec(root, root.getAbsolutePath(), zos);
    }
    private void addRec(File node, String base, ZipOutputStream zos) throws IOException {
        if (node.isDirectory()) {
            File[] kids = node.listFiles();
            if (kids != null) for (File k : kids) addRec(k, base, zos);
            return;
        }
        String rel = node.getAbsolutePath().substring(base.length() + 1).replace(File.separatorChar, '/');
        if (ANDROID_MANIFEST.equals(rel)) return;
        ZipEntry entry = createZipEntry(rel, node);
        zos.putNextEntry(entry);
        try (FileInputStream fis = new FileInputStream(node)) {
            copyStream(fis, zos);
        }
        zos.closeEntry();
    }
    private ZipEntry createZipEntry(String name, byte[] data) {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        return e;
    }
    private ZipEntry createZipEntry(String name, File file) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setTime(0L);
        String lower = name.toLowerCase(Locale.US);
        boolean store = lower.endsWith(".so") || lower.endsWith(".arsc") || lower.endsWith(".dex");
        if (store) {
            e.setMethod(ZipEntry.STORED);
            e.setSize(file.length());
            CRC32 crc = new CRC32();
            try (InputStream in = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) != -1) {
                    crc.update(buf, 0, r);
                }
            }
            e.setCrc(crc.getValue());
        } else {
            e.setMethod(ZipEntry.DEFLATED);
        }
        return e;
    }
    private static File safeResolve(File root, String basePath, String entryName) throws IOException {
        File out = new File(root, entryName);
        String outPath = out.getCanonicalPath();
        if (!outPath.startsWith(basePath)) {
            throw new IOException("Blocked zip path traversal: " + entryName);
        }
        return out;
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted, try the export again
                if (currentSettingsApp != null) {
                    triggerExportData(currentSettingsApp.packageName);
                }
            } else {
                Toast.makeText(this, "Write permission is required to export data.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void triggerExportData(String targetPackageName) {
        // On Android 10+ the export uses MediaStore and does not require storage permissions.
        // Request WRITE_EXTERNAL_STORAGE only on legacy devices where direct file writes are used.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST_CODE);
                return;
            }
        }

        Log.d(TAG, "Triggering data export for: " + targetPackageName);
        statusTxt.setText("Requesting data export for " + targetPackageName + "...");
        Intent exportIntent = new Intent("com.applisto.appcloner.ACTION_EXPORT_DATA");
        exportIntent.setPackage(targetPackageName);
        exportIntent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        exportIntent.putExtra("sender_package", getPackageName());
        try {
            sendBroadcast(exportIntent, IPC_PERMISSION);
            Toast.makeText(this, "Export request sent to " + targetPackageName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Error sending export broadcast to " + targetPackageName, e);
            statusTxt.setText("Export Error: Failed to contact app " + targetPackageName);
            Toast.makeText(this, "Failed to request export from " + targetPackageName + ". Is the app correctly cloned?", Toast.LENGTH_LONG).show();
        }
    }
    private void confirmAndUninstallApp(AppInfo appInfo) {
        if (appInfo == null || appInfo.packageName == null || appInfo.packageName.trim().isEmpty()) {
            Toast.makeText(this, "Unable to determine which package to uninstall.", Toast.LENGTH_LONG).show();
            return;
        }
        final String appDisplayName = appInfo.appName != null && !appInfo.appName.trim().isEmpty()
                ? appInfo.appName
                : appInfo.packageName;

        new AlertDialog.Builder(this)
                .setTitle("Uninstall App")
                .setMessage("Are you sure you want to uninstall '" + appDisplayName + "'?")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton("Uninstall", (dialog, which) -> {
                    Uri packageUri = Uri.parse("package:" + appInfo.packageName);
                    Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
                    uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
                    if (uninstallIntent.resolveActivity(getPackageManager()) != null) {
                        pendingUninstallPackageName = appInfo.packageName;
                        pendingUninstallAppName = appInfo.appName;
                        try {
                            uninstallLauncher.launch(uninstallIntent);
                            if (statusTxt != null) {
                                statusTxt.setText("Opening uninstall dialog for " + appDisplayName + "...");
                            }
                            Toast.makeText(MainActivity.this,
                                    "Opening uninstall dialog for " + appDisplayName,
                                    Toast.LENGTH_SHORT).show();
                        } catch (ActivityNotFoundException | SecurityException e) {
                            pendingUninstallPackageName = null;
                            pendingUninstallAppName = null;
                            Log.e(TAG, "Unable to launch uninstall intent for " + appInfo.packageName, e);
                            Toast.makeText(MainActivity.this,
                                    "Cannot uninstall " + appDisplayName + ": " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Cannot uninstall " + appDisplayName + ". No system handler found.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    private static class SelectedAppInfo {
        String packageName;
        String appName;
        File cachedApkFile;
        SelectedAppInfo(String packageName, String appName, File cachedApkFile) {
            this.packageName = packageName;
            this.appName = appName;
            this.cachedApkFile = cachedApkFile;
        }
    }
    private static class AppInfo {
        String appName;
        String packageName;
        Drawable icon;
    }

    private class MenuAdapter extends ArrayAdapter<MenuItem> {
        public MenuAdapter(Context context, List<MenuItem> items) {
            super(context, 0, items);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_menu_item, parent, false);
            }

            MenuItem menuItem = getItem(position);

            ImageView iconView = convertView.findViewById(R.id.menu_item_icon);
            TextView titleView = convertView.findViewById(R.id.menu_item_title);

            iconView.setImageDrawable(menuItem.getIcon());
            titleView.setText(menuItem.getTitle());

            return convertView;
        }
    }
    private class AppListAdapter extends BaseAdapter {
        private final List<AppInfo> apps;
        private final boolean isClonedAppsAdapter;
        public AppListAdapter(List<AppInfo> apps) {
            this.apps = apps;
            this.isClonedAppsAdapter = (apps == clonedApps);
        }
        @Override
        public int getCount() {
            return apps != null ? apps.size() : 0;
        }
        @Override
        public Object getItem(int position) {
            return apps != null && position < apps.size() ? apps.get(position) : null;
        }
        @Override
        public long getItemId(int position) {
            return position;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.app_list_item, parent, false);
            }
            if (apps != null && position >= 0 && position < apps.size()) {
                AppInfo app = apps.get(position);
                TextView appNameText = convertView.findViewById(R.id.appName);
                TextView packageNameText = convertView.findViewById(R.id.packageName);
                ImageView appIcon = convertView.findViewById(R.id.appIcon);
                ImageButton menuButton = convertView.findViewById(R.id.menuButton);
                appNameText.setText(app.appName != null ? app.appName : "Unknown");
                packageNameText.setText(app.packageName != null ? app.packageName : "Unknown");
                appIcon.setImageDrawable(app.icon);
                if (isClonedAppsAdapter) {
                    menuButton.setVisibility(View.VISIBLE);
                    menuButton.setTag(app);
                    menuButton.setOnClickListener(v -> {
                        AppInfo clickedApp = (AppInfo) v.getTag();
                        if (clickedApp != null) {
                            PopupMenu tempPopup = new PopupMenu(MainActivity.this, v);
                            Menu menu = tempPopup.getMenu();
                            getMenuInflater().inflate(R.menu.cloned_app_menu, menu);

                            List<MenuItem> menuItems = new ArrayList<>();
                            for (int i = 0; i < menu.size(); i++) {
                                menuItems.add(menu.getItem(i));
                            }

                            MenuAdapter adapter = new MenuAdapter(MainActivity.this, menuItems);

                            new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                                    .setTitle(clickedApp.appName)
                                    .setAdapter(adapter, (dialog, which) -> {
                                        MenuItem selectedItem = menuItems.get(which);
                                        int itemId = selectedItem.getItemId();
                                        if (itemId == R.id.action_edit_prefs) {
                                            Intent i = new Intent(MainActivity.this, PrefsEditorActivity.class);
                                            i.putExtra("pkg", clickedApp.packageName);
                                            i.putExtra("appName", clickedApp.appName);
                                            startActivity(i);
                                        } else if (itemId == R.id.action_export_data) {
                                            triggerExportData(clickedApp.packageName);
                                        } else if (itemId == R.id.action_uninstall) {
                                            confirmAndUninstallApp(clickedApp);
                                        }
                                    })
                                    .show();
                        }
                    });
                } else {
                    menuButton.setVisibility(View.GONE);
                    menuButton.setOnClickListener(null);
                    menuButton.setTag(null);
                }
            }
            return convertView;
        }
    }

    private static class JsonObjectField {
        final String key;
        final View inputView;
        final Class<?> valueClass;

        JsonObjectField(String key, View inputView, Class<?> valueClass) {
            this.key = key;
            this.inputView = inputView;
            this.valueClass = valueClass;
        }
    }

    private static class Setting {
        final String key;
        Object value;
        Class<?> valueClass;
        Setting parent;
        List<Setting> children;
        boolean isCategory = false;
        boolean isExpanded = false;

        Setting(String key, Object value, Class<?> valueClass) {
            this.key = key;
            this.value = value;
            this.valueClass = valueClass;
        }

        boolean isGroup() {
            return children != null && !children.isEmpty();
        }

        boolean isCategoryHeader() {
            return isCategory && isGroup();
        }
    }

    private class SettingsAdapter extends BaseAdapter {
        private final List<Setting> rootSettings;
        private final JSONObject json;
        private List<Setting> flattenedSettings;

        public SettingsAdapter(List<Setting> settings, JSONObject json) {
            this.rootSettings = settings;
            this.json = json;
            rebuildFlattenedList();
        }

        private void rebuildFlattenedList() {
            flattenedSettings = new ArrayList<>();
            for (Setting setting : rootSettings) {
                flattenedSettings.add(setting);
                if (setting.isCategoryHeader() && setting.isExpanded) {
                    for (Setting child : setting.children) {
                        flattenedSettings.add(child);
                    }
                }
            }
        }

        @Override
        public int getCount() {
            return flattenedSettings.size();
        }

        @Override
        public Object getItem(int position) {
            return flattenedSettings.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.setting_list_item, parent, false);
            }

            final Setting setting = flattenedSettings.get(position);

            TextView nameTextView = convertView.findViewById(R.id.setting_name);
            TextView valueTextView = convertView.findViewById(R.id.setting_value);
            ImageView iconView = convertView.findViewById(R.id.setting_icon);
            View iconContainer = convertView.findViewById(R.id.icon_container);
            ImageView chevronView = convertView.findViewById(R.id.setting_chevron);
            View generateButton = convertView.findViewById(R.id.generate_button);
            com.google.android.material.materialswitch.MaterialSwitch toggleSwitch = convertView.findViewById(R.id.setting_switch);
            TextView optionValueText = convertView.findViewById(R.id.setting_option_value);

            // Handle indentation for child items
            if (convertView instanceof androidx.cardview.widget.CardView) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) convertView.getLayoutParams();
                float density = convertView.getResources().getDisplayMetrics().density;
                int marginStart = (int) (8 * density); // Default margin
                if (setting.parent != null) {
                    marginStart = (int) (32 * density); // Indented margin for children
                }
                params.setMarginStart(marginStart);
                convertView.setLayoutParams(params);
            }

            String displayName = getDisplayName(setting);
            
            // Reset all views to default state
            iconContainer.setVisibility(View.GONE);
            iconView.setVisibility(View.GONE);
            chevronView.setVisibility(View.GONE);
            generateButton.setVisibility(View.GONE);
            toggleSwitch.setVisibility(View.GONE);
            optionValueText.setVisibility(View.GONE);
            valueTextView.setVisibility(View.GONE);
            toggleSwitch.setOnCheckedChangeListener(null);
            if (generateButton instanceof View) {
                generateButton.setOnClickListener(null);
            }
            
            if (setting.isCategoryHeader()) {
                // Legacy category header check (should not be hit if isCategory=false)
                nameTextView.setText(displayName);
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(getCategoryIcon(setting.key));
                chevronView.setVisibility(View.VISIBLE);
                return convertView;
            } else if (setting.key != null && setting.key.startsWith("cat_")) {
                // Render category groups as clickable items
                nameTextView.setText(displayName);
                nameTextView.setTypeface(nameTextView.getTypeface(), android.graphics.Typeface.BOLD);
                nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 15);
                
                // Show category icon
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(getCategoryIcon(setting.key));
                
                // Use descriptive subtitle from CloneSettings
                valueTextView.setText(CloneSettings.getCategorySubtitle(setting.key, json));
                valueTextView.setVisibility(View.VISIBLE);
                chevronView.setVisibility(View.VISIBLE);
                return convertView;
            } else if (setting.parent != null && setting.parent.isCategory) {
                // Child setting under category (should not be hit ideally)
                nameTextView.setText(displayName);
                nameTextView.setTypeface(nameTextView.getTypeface(), android.graphics.Typeface.NORMAL);
                nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            } else if (setting.isGroup()) {
                // Non-category group header
                nameTextView.setText(displayName);
                nameTextView.setTypeface(nameTextView.getTypeface(), android.graphics.Typeface.BOLD);
                nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_settings);
                chevronView.setVisibility(View.VISIBLE);
            } else {
                // Regular setting
                nameTextView.setText(displayName);
                nameTextView.setTypeface(nameTextView.getTypeface(), android.graphics.Typeface.NORMAL);
                nameTextView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
            }

            // Handle boolean settings with toggle switch (like App Cloner)
            Object value = setting.value;
            if (value instanceof Boolean && !setting.isGroup()) {
                // Show toggle switch for boolean settings
                toggleSwitch.setVisibility(View.VISIBLE);
                toggleSwitch.setChecked((Boolean) value);
                chevronView.setVisibility(View.GONE);
                
                // Handle toggle changes directly
                final View finalConvertView = convertView;
                toggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    try {
                        setting.value = isChecked;
                        setting.valueClass = Boolean.class;
                        json.put(setting.key, isChecked);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error saving boolean setting: " + setting.key, e);
                    }
                });
                return convertView;
            }

            // Handle mock network connection settings with option display
            if (setting.key != null && setting.key.startsWith("mock_") && setting.key.endsWith("_connection")) {
                String currentVal = (value != null && !JSONObject.NULL.equals(value)) ? value.toString() : "";
                String displayVal = "No change";
                if ("CONNECTED".equalsIgnoreCase(currentVal)) displayVal = "Connected";
                else if ("DISCONNECTED".equalsIgnoreCase(currentVal)) displayVal = "Disconnected";
                
                optionValueText.setVisibility(View.VISIBLE);
                optionValueText.setText(displayVal);
                chevronView.setVisibility(View.VISIBLE);
                return convertView;
            }

            valueTextView.setVisibility(View.VISIBLE);
            if (setting.isGroup() && !setting.isCategoryHeader()) {
                valueTextView.setText(getGroupSummary(setting));
                return convertView;
            }

            if (BUNDLE_DATA_SETTING_KEY.equals(setting.key)) {
                valueTextView.setText(getBundleDataSummaryText());
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_data);
                chevronView.setVisibility(View.VISIBLE);
                return convertView;
            }

            if (LIVE_VIDEO_SETTING_KEY.equals(setting.key)) {
                String name = liveVideoDisplayName;
                if (name == null || name.isEmpty()) {
                     valueTextView.setText("Tap to select video file");
                } else {
                     valueTextView.setText("Selected: " + name);
                }
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_media);
                chevronView.setVisibility(View.VISIBLE);
                return convertView;
            }

            if (value instanceof JSONObject) {
                valueTextView.setText("Tap to configure advanced options");
                chevronView.setVisibility(View.VISIBLE);
            } else if (CLONING_MODE_KEY.equals(setting.key)) {
                valueTextView.setText(getCloningModeSummary(value));
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_clone);
                chevronView.setVisibility(View.VISIBLE);
            } else if ("user_agent".equals(setting.key)) {
                boolean enabled = json.optBoolean("user_agent_hook_enabled", CloneSettings.DEFAULT_USER_AGENT_HOOK_ENABLED);
                String uaValue;
                if (value == null || JSONObject.NULL.equals(value) || value.toString().trim().isEmpty()) {
                    uaValue = CloneSettings.DEFAULT_USER_AGENT;
                } else {
                    uaValue = value.toString().trim();
                }
                if (uaValue.length() > 50) {
                    uaValue = uaValue.substring(0, 47) + "...";
                }
                valueTextView.setText(enabled ? "Enabled: " + uaValue : "Disabled (system default)");
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_identity);
                chevronView.setVisibility(View.VISIBLE);
                return convertView;
            } else if (value == null || JSONObject.NULL.equals(value) || value.toString().trim().isEmpty()) {
                valueTextView.setText("Tap to set a value");
                chevronView.setVisibility(View.VISIBLE);
            } else if (usesIdentityModes(setting.key) && isNoChangeValue(value)) {
                valueTextView.setText("System value (no change)");
                chevronView.setVisibility(View.VISIBLE);
            } else if (usesIdentityModes(setting.key) && isRandomValue(value)) {
                valueTextView.setText("Randomized on each launch");
                chevronView.setVisibility(View.VISIBLE);
            } else {
                String valueStr = value.toString();
                if (valueStr.length() > 30) {
                    valueStr = valueStr.substring(0, 27) + "...";
                }
                valueTextView.setText(valueStr);
                chevronView.setVisibility(View.VISIBLE);
            }

            if ("android_id".equals(setting.key) || "wifi_mac".equals(setting.key)) {
                generateButton.setVisibility(View.GONE);
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_identity);
            } else if ("latitude".equals(setting.key) || "longitude".equals(setting.key) ||
                       "SpoofLocationLatitude".equals(setting.key) || "SpoofLocationLongitude".equals(setting.key)) {
                generateButton.setVisibility(View.VISIBLE);
                iconContainer.setVisibility(View.VISIBLE);
                iconView.setImageResource(R.drawable.ic_location);
                chevronView.setVisibility(View.GONE);
                generateButton.setOnClickListener(v -> {
                    String newValue = "";
                    if ("latitude".equals(setting.key) || "SpoofLocationLatitude".equals(setting.key)) {
                        newValue = CloneSettings.generateRandomLatitude();
                    } else if ("longitude".equals(setting.key) || "SpoofLocationLongitude".equals(setting.key)) {
                        newValue = CloneSettings.generateRandomLongitude();
                    }
                    setting.value = newValue;
                    setting.valueClass = String.class;
                    try {
                        json.put(setting.key, newValue);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error saving generated value", e);
                    }
                    notifyDataSetChanged();
                });
            }

            return convertView;
        }
        
        private int getCategoryIcon(String categoryKey) {
            if (categoryKey == null) return R.drawable.ic_settings;
            switch (categoryKey) {
                case "cat_identity": return R.drawable.ic_identity;
                case "cat_privacy": return R.drawable.ic_identity;
                case "cat_network": return R.drawable.ic_network;
                case "cat_webview": return R.drawable.ic_webview;
                case "cat_media": return R.drawable.ic_media;
                case "cat_data": return R.drawable.ic_data;
                case "cat_display": return R.drawable.ic_display;
                case "cat_developer": return R.drawable.ic_settings;
                default: return R.drawable.ic_settings;
            }
        }

        public void toggleCategory(Setting category) {
            if (category.isCategoryHeader()) {
                category.isExpanded = !category.isExpanded;
                rebuildFlattenedList();
                notifyDataSetChanged();
            }
        }

        @Override
        public void notifyDataSetChanged() {
            rebuildFlattenedList();
            super.notifyDataSetChanged();
        }
    }

    private String getCloningModeSummary(Object value) {
        String mode = value instanceof String ? (String) value : CLONING_MODE_REPLACE;
        if (CLONING_MODE_GENERATE.equals(mode)) {
            return "Generate new package variant";
        }
        if (CLONING_MODE_CUSTOM.equals(mode)) {
            return "Custom package name";
        }
        return "Replace original (keep package)";
    }

    private void showCategoryDetailDialog(final Setting category, final JSONObject json, final BaseAdapter rootAdapter) {
        if (category == null || !category.isGroup()) {
            return;
        }
        
        // Navigate to CategorySettingsActivity for a full-page experience
        if (currentSettingsFile != null && currentSettingsFile.exists()) {
            // Save current settings first
            if (!persistCurrentSettings()) {
                return;
            }
            
            Intent intent = new Intent(this, CategorySettingsActivity.class);
            intent.putExtra(CategorySettingsActivity.EXTRA_CATEGORY_KEY, category.key);
            intent.putExtra(CategorySettingsActivity.EXTRA_CATEGORY_NAME, getDisplayName(category));
            intent.putExtra(CategorySettingsActivity.EXTRA_JSON_PATH, currentSettingsFile.getAbsolutePath());
            if (currentSettingsApp != null) {
                intent.putExtra(CategorySettingsActivity.EXTRA_APP_NAME, currentSettingsApp.appName);
                intent.putExtra(CategorySettingsActivity.EXTRA_APP_PACKAGE, currentSettingsApp.packageName);
            }
            
            categorySettingsLauncher.launch(intent);
        } else {
            Toast.makeText(this, "Error: Settings file not found", Toast.LENGTH_SHORT).show();
        }
    }
}
