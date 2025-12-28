package com.appcloner.replica;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.DigitsKeyListener;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.security.SecureRandom;

/**
 * Activity that displays settings for a specific category.
 * Shows settings with toggles for booleans and dialogs for complex options.
 * Follows App Cloner's design pattern.
 */
public class CategorySettingsActivity extends AppCompatActivity {
    private static final String TAG = "CategorySettingsActivity";
    private static final SecureRandom random = new SecureRandom();
    
    public static final String EXTRA_CATEGORY_KEY = "category_key";
    public static final String EXTRA_CATEGORY_NAME = "category_name";
    public static final String EXTRA_JSON_PATH = "json_path";
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_APP_PACKAGE = "app_package";
    
    private String categoryKey;
    private String categoryName;
    private File jsonFile;
    private JSONObject settingsJson;
    private ListView settingsListView;
    private CategorySettingsAdapter adapter;
    private List<SettingItem> settingItems;
    
    // Category child settings mapping (same as MainActivity)
    private static final Map<String, List<String>> SETTINGS_CATEGORIES;
    static {
        Map<String, List<String>> categories = new LinkedHashMap<>();
        categories.put("cat_identity", Arrays.asList(
                "new_identity_action",
                "android_id",
                "wifi_mac",
                "bluetooth_mac",
                "serial_number",
                "imei",
                "imsi"
        ));
        categories.put("cat_privacy", Arrays.asList(
                "hide_root",
                "hide_emulator",
                "disable_license_validation",
                "hide_cpu_info",
                "hide_gpu_info",
                "gpu_vendor",
                "gpu_renderer",
                "gpu_version",
                "hide_sim_operator",
                "spoofed_operator_name",
                "spoofed_operator_numeric",
                "spoofed_sim_country_iso",
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
                "DisableNetworkingWithoutVpn",
                "disable_background_networking",
                "disable_background_networking_delay",
                "disable_background_networking_silent",
                "hide_dns_servers",
                "hide_dns_servers_completely",
                "change_system_user_agent",
                "system_user_agent",
                "socks_proxy",
                "socks_proxy_host",
                "socks_proxy_port",
                "socks_proxy_user",
                "socks_proxy_pass",
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
    }
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_category_settings);
        
        // Get intent extras
        Intent intent = getIntent();
        categoryKey = intent.getStringExtra(EXTRA_CATEGORY_KEY);
        categoryName = intent.getStringExtra(EXTRA_CATEGORY_NAME);
        String jsonPath = intent.getStringExtra(EXTRA_JSON_PATH);
        
        if (categoryKey == null || jsonPath == null) {
            Toast.makeText(this, "Error: Invalid category data", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        jsonFile = new File(jsonPath);
        if (!jsonFile.exists()) {
            Toast.makeText(this, "Error: Settings file not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Setup toolbar
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(categoryName != null ? categoryName : "Settings");
        }
        toolbar.setNavigationOnClickListener(v -> onBackPressed());
        
        // Setup ListView
        settingsListView = findViewById(R.id.settings_list);
        
        // Load settings
        loadSettings();
    }
    
    private void loadSettings() {
        try {
            String content = readFile(jsonFile);
            settingsJson = new JSONObject(content);
            
            // Build settings list for this category
            settingItems = buildSettingsList();
            
            adapter = new CategorySettingsAdapter(settingItems);
            settingsListView.setAdapter(adapter);
            
            settingsListView.setOnItemClickListener((parent, view, position, id) -> {
                SettingItem item = settingItems.get(position);
                if (item.isAction || !item.isBoolean) {
                    showSettingDialog(item);
                }
            });
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error loading settings", e);
            Toast.makeText(this, "Error loading settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
            finish();
        }
    }
    
    private List<SettingItem> buildSettingsList() {
        List<SettingItem> items = new ArrayList<>();
        List<String> keys = SETTINGS_CATEGORIES.get(categoryKey);
        
        if (keys == null) {
            // Fallback - try to get all keys from JSON
            Iterator<String> iterator = settingsJson.keys();
            while (iterator.hasNext()) {
                String key = iterator.next();
                Object value = settingsJson.opt(key);
                items.add(new SettingItem(key, value));
            }
        } else {
            for (String key : keys) {
                // Handle special action items
                if ("new_identity_action".equals(key)) {
                    SettingItem actionItem = new SettingItem(key, null);
                    actionItem.isAction = true;
                    items.add(actionItem);
                } else if (settingsJson.has(key)) {
                    Object value = settingsJson.opt(key);
                    items.add(new SettingItem(key, value));
                }
            }
        }
        
        return items;
    }
    
    private void showSettingDialog(SettingItem item) {
        // Handle special action items
        if ("new_identity_action".equals(item.key)) {
            showNewIdentityDialog();
            return;
        }
        
        // Handle special settings with custom floating window dialogs
        if ("SpoofLocation".equals(item.key)) {
            showSpoofLocationDialog();
            return;
        } else if ("socks_proxy".equals(item.key)) {
            showSocksProxyDialog();
            return;
        } else if ("FakeCamera".equals(item.key)) {
            showFakeCameraDialog();
            return;
        } else if ("identity_notifications".equals(item.key)) {
            showIdentityNotificationsSettingsDialog();
            return;
        }
        
        // Handle special settings with custom dialogs
        if ("android_id".equals(item.key)) {
            showIdentityModeDialog(item, "Android ID", 16, "[0-9A-Fa-f]");
        } else if ("wifi_mac".equals(item.key)) {
            showMacAddressDialog(item, "Wi-Fi MAC Address");
        } else if ("bluetooth_mac".equals(item.key)) {
            showMacAddressDialog(item, "Bluetooth MAC Address");
        } else if ("imei".equals(item.key)) {
            showIdentityModeDialog(item, "IMEI", 15, "[0-9]");
        } else if ("imsi".equals(item.key)) {
            showIdentityModeDialog(item, "IMSI", 15, "[0-9]");
        } else if ("serial_number".equals(item.key)) {
            showIdentityModeDialog(item, "Serial Number", 16, "[0-9A-Za-z]");
        } else if (item.key.startsWith("mock_") && item.key.endsWith("_connection")) {
            showMockConnectionDialog(item);
        } else if ("FakeCameraRotation".equals(item.key)) {
            showRotationDialog(item);
        } else if ("accessible_data_dir_mode".equals(item.key)) {
            showAccessModeDialog(item);
        } else if ("user_agent".equals(item.key)) {
            showUserAgentDialog(item);
        } else if ("SpoofLocationLatitude".equals(item.key) || "SpoofLocationLongitude".equals(item.key)) {
            showLocationCoordinatesDialog();
        } else if ("SpoofLocationInterval".equals(item.key)) {
            showIntervalDialog(item);
        } else if (item.value instanceof Number) {
            showNumberDialog(item);
        } else {
            showTextDialog(item);
        }
    }
    
    private void showIdentityModeDialog(SettingItem item, String title, int maxLength, String allowedChars) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(title);
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        
        // Radio group for mode selection
        final RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        
        RadioButton rbNoChange = new RadioButton(this);
        rbNoChange.setText("No change");
        rbNoChange.setId(View.generateViewId());
        radioGroup.addView(rbNoChange);
        
        RadioButton rbHide = new RadioButton(this);
        rbHide.setText("Hide");
        rbHide.setId(View.generateViewId());
        radioGroup.addView(rbHide);
        
        RadioButton rbRandom = new RadioButton(this);
        rbRandom.setText("Random");
        rbRandom.setId(View.generateViewId());
        radioGroup.addView(rbRandom);
        
        RadioButton rbCustom = new RadioButton(this);
        rbCustom.setText("Custom");
        rbCustom.setId(View.generateViewId());
        radioGroup.addView(rbCustom);
        
        container.addView(radioGroup);
        
        // Custom value input
        com.google.android.material.textfield.TextInputLayout inputLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint(title);
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText input =
            new com.google.android.material.textfield.TextInputEditText(this);
        input.setSingleLine(true);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLength)});
        inputLayout.addView(input);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = (int)(16 * density);
        inputLayout.setLayoutParams(inputParams);
        container.addView(inputLayout);
        
        // Randomize button
        com.google.android.material.button.MaterialButton randomizeBtn =
            new com.google.android.material.button.MaterialButton(this);
        randomizeBtn.setText("Randomize");
        randomizeBtn.setOnClickListener(v -> {
            String randomValue = generateRandomValue(item.key);
            input.setText(randomValue);
        });
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = (int)(8 * density);
        randomizeBtn.setLayoutParams(btnParams);
        container.addView(randomizeBtn);
        
        // Set current value
        String currentVal = item.value != null ? item.value.toString() : "";
        if ("nochange".equalsIgnoreCase(currentVal) || currentVal.isEmpty()) {
            radioGroup.check(rbNoChange.getId());
            input.setEnabled(false);
            randomizeBtn.setEnabled(false);
        } else if ("hide".equalsIgnoreCase(currentVal)) {
            radioGroup.check(rbHide.getId());
            input.setEnabled(false);
            randomizeBtn.setEnabled(false);
        } else if ("random".equalsIgnoreCase(currentVal)) {
            radioGroup.check(rbRandom.getId());
            input.setEnabled(false);
            randomizeBtn.setEnabled(false);
        } else {
            radioGroup.check(rbCustom.getId());
            input.setText(currentVal);
            input.setEnabled(true);
            randomizeBtn.setEnabled(true);
        }
        
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isCustom = checkedId == rbCustom.getId();
            input.setEnabled(isCustom);
            randomizeBtn.setEnabled(isCustom);
        });
        
        builder.setView(container);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                String newValue;
                int checkedId = radioGroup.getCheckedRadioButtonId();
                if (checkedId == rbNoChange.getId()) {
                    newValue = "nochange";
                } else if (checkedId == rbHide.getId()) {
                    newValue = "hide";
                } else if (checkedId == rbRandom.getId()) {
                    newValue = "random";
                } else {
                    newValue = input.getText() != null ? input.getText().toString().trim() : "";
                }
                
                item.value = newValue;
                settingsJson.put(item.key, newValue);
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving setting", e);
            }
        });
        
        builder.show();
    }
    
    private void showMacAddressDialog(SettingItem item, String title) {
        showIdentityModeDialog(item, title, 17, "[0-9A-Fa-f:]");
    }
    
    private void showMockConnectionDialog(SettingItem item) {
        String displayName = CloneSettings.getDisplayNameForKey(item.key);
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(displayName);
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, 0);
        
        final String[][] options = {
            {"No change", ""},
            {"Connected", "CONNECTED"},
            {"Disconnected", "DISCONNECTED"}
        };
        
        String currentVal = (item.value != null && !JSONObject.NULL.equals(item.value)) 
            ? item.value.toString() : "";
        
        final RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(RadioGroup.VERTICAL);
        
        for (String[] option : options) {
            RadioButton rb = new RadioButton(this);
            rb.setText(option[0]);
            rb.setTag(option[1]);
            rb.setTextSize(16);
            rb.setPadding((int)(8 * density), (int)(12 * density), (int)(8 * density), (int)(12 * density));
            radioGroup.addView(rb);
            
            if (option[1].equalsIgnoreCase(currentVal) ||
                (option[1].isEmpty() && (currentVal.isEmpty() || "null".equalsIgnoreCase(currentVal)))) {
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
                String val = "";
                if (selectedView != null && selectedView.getTag() != null) {
                    val = (String) selectedView.getTag();
                }
                
                if (val.isEmpty()) {
                    item.value = JSONObject.NULL;
                    settingsJson.put(item.key, JSONObject.NULL);
                } else {
                    item.value = val;
                    settingsJson.put(item.key, val);
                }
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving mock network setting", e);
            }
        });
        
        builder.show();
    }
    
    private void showRotationDialog(SettingItem item) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Rotation");
        
        String[] options = {"No change", "0°", "90°", "180°", "270°"};
        String[] values = {"NO_CHANGE", "0", "90", "180", "270"};
        
        String currentVal = item.value != null ? item.value.toString() : "NO_CHANGE";
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equalsIgnoreCase(currentVal)) {
                selectedIndex = i;
                break;
            }
        }
        
        final int[] selected = {selectedIndex};
        builder.setSingleChoiceItems(options, selectedIndex, (dialog, which) -> selected[0] = which);
        
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                item.value = values[selected[0]];
                settingsJson.put(item.key, values[selected[0]]);
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving rotation setting", e);
            }
        });
        
        builder.show();
    }
    
    private void showAccessModeDialog(SettingItem item) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Access Mode");
        
        String[] options = {"Read only (recommended)", "Read & write (full access)", "Normal"};
        String[] values = {"READ_ONLY", "READ_WRITE", "NORMAL"};
        
        String currentVal = item.value != null ? item.value.toString().toUpperCase() : "READ_ONLY";
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(currentVal)) {
                selectedIndex = i;
                break;
            }
        }
        
        final int[] selected = {selectedIndex};
        builder.setSingleChoiceItems(options, selectedIndex, (dialog, which) -> selected[0] = which);
        
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                item.value = values[selected[0]];
                settingsJson.put(item.key, values[selected[0]]);
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving access mode setting", e);
            }
        });
        
        builder.show();
    }
    
    private void showUserAgentDialog(SettingItem item) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("User Agent");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        
        // Enable checkbox
        final CheckBox enableCheckBox = new CheckBox(this);
        enableCheckBox.setText("Enable Custom User-Agent");
        boolean isEnabled = settingsJson.optBoolean("user_agent_hook_enabled", false);
        enableCheckBox.setChecked(isEnabled);
        container.addView(enableCheckBox);
        
        // User agent input
        com.google.android.material.textfield.TextInputLayout inputLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Custom User Agent");
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText input =
            new com.google.android.material.textfield.TextInputEditText(this);
        input.setMaxLines(3);
        String currentUA = item.value != null ? item.value.toString() : CloneSettings.DEFAULT_USER_AGENT;
        input.setText(currentUA);
        input.setEnabled(isEnabled);
        inputLayout.addView(input);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = (int)(16 * density);
        inputLayout.setLayoutParams(inputParams);
        container.addView(inputLayout);
        
        enableCheckBox.setOnCheckedChangeListener((buttonView, isChecked) -> input.setEnabled(isChecked));
        
        // Reset button
        Button resetBtn = new Button(this);
        resetBtn.setText("Reset to Default");
        resetBtn.setOnClickListener(v -> input.setText(CloneSettings.DEFAULT_USER_AGENT));
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btnParams.topMargin = (int)(8 * density);
        resetBtn.setLayoutParams(btnParams);
        container.addView(resetBtn);
        
        builder.setView(container);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                boolean enabled = enableCheckBox.isChecked();
                String uaString = input.getText() != null ? input.getText().toString().trim() : "";
                
                settingsJson.put("user_agent_hook_enabled", enabled);
                settingsJson.put("user_agent", uaString);
                item.value = uaString;
                
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving user agent setting", e);
            }
        });
        
        builder.show();
    }
    
    private void showLocationCoordinatesDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Spoof Location Coordinates");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        
        // Latitude input
        com.google.android.material.textfield.TextInputLayout latLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        latLayout.setHint("Latitude (-90 to 90)");
        latLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText latInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        latInput.setSingleLine(true);
        latInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        double currentLat = settingsJson.optDouble("SpoofLocationLatitude", CloneSettings.DEFAULT_SPOOF_LOCATION_LATITUDE);
        latInput.setText(String.valueOf(currentLat));
        latLayout.addView(latInput);
        container.addView(latLayout);
        
        // Longitude input
        com.google.android.material.textfield.TextInputLayout lngLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        lngLayout.setHint("Longitude (-180 to 180)");
        lngLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText lngInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        lngInput.setSingleLine(true);
        lngInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        double currentLng = settingsJson.optDouble("SpoofLocationLongitude", CloneSettings.DEFAULT_SPOOF_LOCATION_LONGITUDE);
        lngInput.setText(String.valueOf(currentLng));
        lngLayout.addView(lngInput);
        
        LinearLayout.LayoutParams lngParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lngParams.topMargin = (int)(12 * density);
        lngLayout.setLayoutParams(lngParams);
        container.addView(lngLayout);
        
        // Preset locations
        TextView presetLabel = new TextView(this);
        presetLabel.setText("Quick Presets:");
        presetLabel.setTextSize(14);
        LinearLayout.LayoutParams presetLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        presetLabelParams.topMargin = (int)(16 * density);
        presetLabel.setLayoutParams(presetLabelParams);
        container.addView(presetLabel);
        
        // Preset buttons in horizontal scroll
        android.widget.HorizontalScrollView scrollView = new android.widget.HorizontalScrollView(this);
        LinearLayout presetContainer = new LinearLayout(this);
        presetContainer.setOrientation(LinearLayout.HORIZONTAL);
        
        String[][] presets = {
            {"Paris", "48.8584", "2.2945"},
            {"New York", "40.7128", "-74.0060"},
            {"Tokyo", "35.6762", "139.6503"},
            {"London", "51.5074", "-0.1278"},
            {"Sydney", "-33.8688", "151.2093"},
            {"Random", "", ""}
        };
        
        for (String[] preset : presets) {
            com.google.android.material.button.MaterialButton btn =
                new com.google.android.material.button.MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(preset[0]);
            btn.setTextSize(12);
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            btnParams.setMarginEnd((int)(8 * density));
            btn.setLayoutParams(btnParams);
            
            btn.setOnClickListener(v -> {
                if ("Random".equals(preset[0])) {
                    latInput.setText(CloneSettings.generateRandomLatitude());
                    lngInput.setText(CloneSettings.generateRandomLongitude());
                } else {
                    latInput.setText(preset[1]);
                    lngInput.setText(preset[2]);
                }
            });
            presetContainer.addView(btn);
        }
        
        scrollView.addView(presetContainer);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        scrollParams.topMargin = (int)(8 * density);
        scrollView.setLayoutParams(scrollParams);
        container.addView(scrollView);
        
        builder.setView(container);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                String latText = latInput.getText() != null ? latInput.getText().toString().trim() : "";
                String lngText = lngInput.getText() != null ? lngInput.getText().toString().trim() : "";
                
                double lat = latText.isEmpty() ? 0 : Double.parseDouble(latText);
                double lng = lngText.isEmpty() ? 0 : Double.parseDouble(lngText);
                
                // Validate ranges
                if (lat < -90 || lat > 90) {
                    Toast.makeText(this, "Latitude must be between -90 and 90", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (lng < -180 || lng > 180) {
                    Toast.makeText(this, "Longitude must be between -180 and 180", Toast.LENGTH_SHORT).show();
                    return;
                }
                
                settingsJson.put("SpoofLocationLatitude", lat);
                settingsJson.put("SpoofLocationLongitude", lng);
                saveSettings();
                
                // Update the items list
                for (SettingItem item : settingItems) {
                    if ("SpoofLocationLatitude".equals(item.key)) {
                        item.value = lat;
                    } else if ("SpoofLocationLongitude".equals(item.key)) {
                        item.value = lng;
                    }
                }
                adapter.notifyDataSetChanged();
                
                Toast.makeText(this, String.format("Location set to %.4f, %.4f", lat, lng), Toast.LENGTH_SHORT).show();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid coordinate format", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving coordinates", e);
            }
        });
        
        builder.show();
    }
    
    private void showIntervalDialog(SettingItem item) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Update Interval");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, padding, padding, padding);
        
        // Description
        TextView description = new TextView(this);
        description.setText("How often to send fake location updates to the app (in milliseconds). Lower values = more frequent updates but higher battery usage.");
        description.setTextSize(14);
        container.addView(description);
        
        // Input field
        com.google.android.material.textfield.TextInputLayout inputLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Interval (ms)");
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setSuffixText("ms");
        
        final com.google.android.material.textfield.TextInputEditText input =
            new com.google.android.material.textfield.TextInputEditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        int currentVal = item.value instanceof Number ? ((Number) item.value).intValue() : 1000;
        input.setText(String.valueOf(currentVal));
        inputLayout.addView(input);
        
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = (int)(16 * density);
        inputLayout.setLayoutParams(inputParams);
        container.addView(inputLayout);
        
        // Quick preset buttons
        LinearLayout presetRow = new LinearLayout(this);
        presetRow.setOrientation(LinearLayout.HORIZONTAL);
        
        int[] presetValues = {100, 500, 1000, 2000, 5000};
        String[] presetLabels = {"100ms", "500ms", "1s", "2s", "5s"};
        
        for (int i = 0; i < presetValues.length; i++) {
            final int value = presetValues[i];
            com.google.android.material.button.MaterialButton btn =
                new com.google.android.material.button.MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            btn.setText(presetLabels[i]);
            btn.setTextSize(11);
            btn.setOnClickListener(v -> input.setText(String.valueOf(value)));
            
            LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            btnParams.setMarginEnd((int)(4 * density));
            btn.setLayoutParams(btnParams);
            presetRow.addView(btn);
        }
        
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = (int)(12 * density);
        presetRow.setLayoutParams(rowParams);
        container.addView(presetRow);
        
        builder.setView(container);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                String text = input.getText() != null ? input.getText().toString().trim() : "";
                int interval = text.isEmpty() ? 1000 : Integer.parseInt(text);
                
                // Minimum 100ms
                if (interval < 100) {
                    interval = 100;
                    Toast.makeText(this, "Minimum interval is 100ms", Toast.LENGTH_SHORT).show();
                }
                
                item.value = interval;
                settingsJson.put(item.key, interval);
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving interval", e);
            }
        });
        
        builder.show();
    }
    
    private void showNumberDialog(SettingItem item) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(CloneSettings.getDisplayNameForKey(item.key));
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding, padding, 0);
        
        com.google.android.material.textfield.TextInputLayout inputLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Enter value");
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText input =
            new com.google.android.material.textfield.TextInputEditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(item.value != null ? item.value.toString() : "");
        inputLayout.addView(input);
        container.addView(inputLayout);
        
        builder.setView(container);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                String text = input.getText() != null ? input.getText().toString().trim() : "";
                Object newValue;
                if (text.isEmpty()) {
                    newValue = 0;
                } else if (text.contains(".")) {
                    newValue = Double.parseDouble(text);
                } else {
                    newValue = Integer.parseInt(text);
                }
                item.value = newValue;
                settingsJson.put(item.key, newValue);
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (NumberFormatException | JSONException e) {
                Log.e(TAG, "Error saving number setting", e);
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.show();
    }
    
    private void showTextDialog(SettingItem item) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle(CloneSettings.getDisplayNameForKey(item.key));
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        FrameLayout container = new FrameLayout(this);
        container.setPadding(padding, padding, padding, 0);
        
        com.google.android.material.textfield.TextInputLayout inputLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        inputLayout.setHint("Enter value");
        inputLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        
        final com.google.android.material.textfield.TextInputEditText input =
            new com.google.android.material.textfield.TextInputEditText(this);
        input.setSingleLine(true);
        input.setText(item.value != null && !JSONObject.NULL.equals(item.value) ? item.value.toString() : "");
        inputLayout.addView(input);
        container.addView(inputLayout);
        
        builder.setView(container);
        builder.setNegativeButton("CANCEL", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                String text = input.getText() != null ? input.getText().toString().trim() : "";
                item.value = text.isEmpty() ? JSONObject.NULL : text;
                settingsJson.put(item.key, item.value);
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving text setting", e);
            }
        });
        
        builder.show();
    }
    
    private String generateRandomValue(String key) {
        if ("android_id".equals(key)) {
            return CloneSettings.generateRandomAndroidId();
        } else if ("wifi_mac".equals(key) || "bluetooth_mac".equals(key)) {
            return CloneSettings.generateRandomMac();
        } else if ("imei".equals(key)) {
            return CloneSettings.generateRandomImei();
        } else if ("imsi".equals(key)) {
            return CloneSettings.generateRandomImsi();
        } else if ("serial_number".equals(key)) {
            return CloneSettings.generateRandomSerialNumber();
        }
        return "";
    }
    
    private void saveSettings() {
        try (FileWriter writer = new FileWriter(jsonFile)) {
            writer.write(settingsJson.toString(2));
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error saving settings", e);
            Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
    
    @Override
    public void onBackPressed() {
        // Set result to indicate settings may have changed
        setResult(RESULT_OK);
        super.onBackPressed();
    }
    
    /**
     * Shows the New Identity floating dialog with all identity options
     * and generates random values when confirmed.
     */
    private void showNewIdentityDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("New Identity");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);
        
        // Main checkbox for enabling new identity
        final CheckBox mainCheckbox = new CheckBox(this);
        mainCheckbox.setText("New Identity");
        mainCheckbox.setTextSize(16);
        mainCheckbox.setTypeface(mainCheckbox.getTypeface(), android.graphics.Typeface.BOLD);
        container.addView(mainCheckbox);
        
        // Child options container (indented)
        LinearLayout childContainer = new LinearLayout(this);
        childContainer.setOrientation(LinearLayout.VERTICAL);
        childContainer.setPadding((int)(24 * density), 0, 0, 0);
        
        final CheckBox restartAppCheckbox = new CheckBox(this);
        restartAppCheckbox.setText("New Identity Automatically Restart App");
        restartAppCheckbox.setChecked(settingsJson.optBoolean("identity_notifications_restart_app", true));
        childContainer.addView(restartAppCheckbox);
        
        final CheckBox clearCacheCheckbox = new CheckBox(this);
        clearCacheCheckbox.setText("New Identity Clear Cache");
        clearCacheCheckbox.setChecked(settingsJson.optBoolean("identity_notifications_clear_cache", false));
        childContainer.addView(clearCacheCheckbox);
        
        final CheckBox clearDataCheckbox = new CheckBox(this);
        clearDataCheckbox.setText("New Identity Delete App Data");
        clearDataCheckbox.setChecked(settingsJson.optBoolean("identity_notifications_clear_data", false));
        childContainer.addView(clearDataCheckbox);
        
        final CheckBox forEachCloneCheckbox = new CheckBox(this);
        forEachCloneCheckbox.setText("New Identity For Each Cloning Process");
        forEachCloneCheckbox.setChecked(false);
        childContainer.addView(forEachCloneCheckbox);
        
        final CheckBox showNotificationCheckbox = new CheckBox(this);
        showNotificationCheckbox.setText("New Identity Show Notification");
        showNotificationCheckbox.setChecked(settingsJson.optBoolean("identity_notifications", true));
        childContainer.addView(showNotificationCheckbox);
        
        final CheckBox persistentNotificationCheckbox = new CheckBox(this);
        persistentNotificationCheckbox.setText("New Identity Persistent Notification");
        persistentNotificationCheckbox.setChecked(false);
        childContainer.addView(persistentNotificationCheckbox);
        
        container.addView(childContainer);
        
        // Enable/disable child options based on main checkbox
        mainCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < childContainer.getChildCount(); i++) {
                childContainer.getChildAt(i).setEnabled(isChecked);
            }
        });
        
        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            if (mainCheckbox.isChecked()) {
                // Generate new random identity values
                try {
                    settingsJson.put("android_id", CloneSettings.generateRandomAndroidId());
                    settingsJson.put("wifi_mac", CloneSettings.generateRandomMac());
                    settingsJson.put("bluetooth_mac", CloneSettings.generateRandomMac());
                    settingsJson.put("serial_number", CloneSettings.generateRandomSerialNumber());
                    settingsJson.put("imei", CloneSettings.generateRandomImei());
                    settingsJson.put("imsi", CloneSettings.generateRandomImsi());
                    
                    // Save notification settings
                    settingsJson.put("identity_notifications", showNotificationCheckbox.isChecked());
                    settingsJson.put("identity_notifications_restart_app", restartAppCheckbox.isChecked());
                    settingsJson.put("identity_notifications_clear_cache", clearCacheCheckbox.isChecked());
                    settingsJson.put("identity_notifications_clear_data", clearDataCheckbox.isChecked());
                    
                    saveSettings();
                    
                    // Reload settings list
                    settingItems = buildSettingsList();
                    adapter.notifyDataSetChanged();
                    
                    Toast.makeText(this, "New identity generated!", Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Log.e(TAG, "Error generating new identity", e);
                    Toast.makeText(this, "Error generating identity: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        });
        
        builder.show();
    }
    
    /**
     * Shows the Spoof Location floating dialog with parent/child checkbox pattern.
     */
    private void showSpoofLocationDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Spoof Location");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);
        
        // Main checkbox for enabling spoof location
        final CheckBox mainCheckbox = new CheckBox(this);
        mainCheckbox.setText("Spoof Location");
        mainCheckbox.setTextSize(16);
        mainCheckbox.setTypeface(mainCheckbox.getTypeface(), android.graphics.Typeface.BOLD);
        mainCheckbox.setChecked(settingsJson.optBoolean("SpoofLocation", false));
        container.addView(mainCheckbox);
        
        // Child options container (indented)
        LinearLayout childContainer = new LinearLayout(this);
        childContainer.setOrientation(LinearLayout.VERTICAL);
        childContainer.setPadding((int)(24 * density), (int)(8 * density), 0, 0);
        
        // Latitude input
        com.google.android.material.textfield.TextInputLayout latLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        latLayout.setHint("Latitude");
        latLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText latInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        latInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        latInput.setText(String.valueOf(settingsJson.optDouble("SpoofLocationLatitude", 76.387236)));
        latLayout.addView(latInput);
        LinearLayout.LayoutParams latParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        latParams.bottomMargin = (int)(8 * density);
        latLayout.setLayoutParams(latParams);
        childContainer.addView(latLayout);
        
        // Longitude input
        com.google.android.material.textfield.TextInputLayout lonLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        lonLayout.setHint("Longitude");
        lonLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText lonInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        lonInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        lonInput.setText(String.valueOf(settingsJson.optDouble("SpoofLocationLongitude", -154.761262)));
        lonLayout.addView(lonInput);
        LinearLayout.LayoutParams lonParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lonParams.bottomMargin = (int)(8 * density);
        lonLayout.setLayoutParams(lonParams);
        childContainer.addView(lonLayout);
        
        // Altitude input
        com.google.android.material.textfield.TextInputLayout altLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        altLayout.setHint("Altitude");
        altLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText altInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        altInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        altInput.setText(String.valueOf(settingsJson.optDouble("SpoofLocationAltitude", 35)));
        altLayout.addView(altInput);
        LinearLayout.LayoutParams altParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        altParams.bottomMargin = (int)(8 * density);
        altLayout.setLayoutParams(altParams);
        childContainer.addView(altLayout);
        
        // Accuracy input
        com.google.android.material.textfield.TextInputLayout accLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        accLayout.setHint("Accuracy");
        accLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText accInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        accInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        accInput.setText(String.valueOf(settingsJson.optDouble("SpoofLocationAccuracy", 5)));
        accLayout.addView(accInput);
        childContainer.addView(accLayout);
        
        container.addView(childContainer);
        
        // Enable/disable child options based on main checkbox
        boolean initialEnabled = mainCheckbox.isChecked();
        for (int i = 0; i < childContainer.getChildCount(); i++) {
            childContainer.getChildAt(i).setEnabled(initialEnabled);
        }
        
        mainCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < childContainer.getChildCount(); i++) {
                childContainer.getChildAt(i).setEnabled(isChecked);
            }
        });
        
        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                settingsJson.put("SpoofLocation", mainCheckbox.isChecked());
                
                String latText = latInput.getText() != null ? latInput.getText().toString().trim() : "";
                String lonText = lonInput.getText() != null ? lonInput.getText().toString().trim() : "";
                String altText = altInput.getText() != null ? altInput.getText().toString().trim() : "";
                String accText = accInput.getText() != null ? accInput.getText().toString().trim() : "";
                
                if (!latText.isEmpty()) settingsJson.put("SpoofLocationLatitude", Double.parseDouble(latText));
                if (!lonText.isEmpty()) settingsJson.put("SpoofLocationLongitude", Double.parseDouble(lonText));
                if (!altText.isEmpty()) settingsJson.put("SpoofLocationAltitude", Double.parseDouble(altText));
                if (!accText.isEmpty()) settingsJson.put("SpoofLocationAccuracy", Double.parseDouble(accText));
                
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException | NumberFormatException e) {
                Log.e(TAG, "Error saving spoof location settings", e);
                Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        builder.show();
    }
    
    /**
     * Shows the SOCKS Proxy floating dialog with parent/child checkbox pattern.
     */
    private void showSocksProxyDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("SOCKS Proxy");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);
        
        // Main checkbox for enabling SOCKS proxy
        final CheckBox mainCheckbox = new CheckBox(this);
        mainCheckbox.setText("Enable SOCKS Proxy");
        mainCheckbox.setTextSize(16);
        mainCheckbox.setTypeface(mainCheckbox.getTypeface(), android.graphics.Typeface.BOLD);
        mainCheckbox.setChecked(settingsJson.optBoolean("socks_proxy", false));
        container.addView(mainCheckbox);
        
        // Child options container (indented)
        LinearLayout childContainer = new LinearLayout(this);
        childContainer.setOrientation(LinearLayout.VERTICAL);
        childContainer.setPadding((int)(24 * density), (int)(8 * density), 0, 0);
        
        // Host input
        com.google.android.material.textfield.TextInputLayout hostLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        hostLayout.setHint("Proxy Host");
        hostLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText hostInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        hostInput.setSingleLine(true);
        hostInput.setText(settingsJson.optString("socks_proxy_host", ""));
        hostLayout.addView(hostInput);
        LinearLayout.LayoutParams hostParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hostParams.bottomMargin = (int)(8 * density);
        hostLayout.setLayoutParams(hostParams);
        childContainer.addView(hostLayout);
        
        // Port input
        com.google.android.material.textfield.TextInputLayout portLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        portLayout.setHint("Proxy Port");
        portLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText portInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        portInput.setText(String.valueOf(settingsJson.optInt("socks_proxy_port", 1080)));
        portLayout.addView(portInput);
        LinearLayout.LayoutParams portParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        portParams.bottomMargin = (int)(8 * density);
        portLayout.setLayoutParams(portParams);
        childContainer.addView(portLayout);
        
        // Username input
        com.google.android.material.textfield.TextInputLayout userLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        userLayout.setHint("Username (optional)");
        userLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        final com.google.android.material.textfield.TextInputEditText userInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        userInput.setSingleLine(true);
        userInput.setText(settingsJson.optString("socks_proxy_user", ""));
        userLayout.addView(userInput);
        LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        userParams.bottomMargin = (int)(8 * density);
        userLayout.setLayoutParams(userParams);
        childContainer.addView(userLayout);
        
        // Password input
        com.google.android.material.textfield.TextInputLayout passLayout =
            new com.google.android.material.textfield.TextInputLayout(this);
        passLayout.setHint("Password (optional)");
        passLayout.setBoxBackgroundMode(com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE);
        passLayout.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        final com.google.android.material.textfield.TextInputEditText passInput =
            new com.google.android.material.textfield.TextInputEditText(this);
        passInput.setSingleLine(true);
        passInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passInput.setText(settingsJson.optString("socks_proxy_pass", ""));
        passLayout.addView(passInput);
        childContainer.addView(passLayout);
        
        container.addView(childContainer);
        
        // Enable/disable child options based on main checkbox
        boolean initialEnabled = mainCheckbox.isChecked();
        for (int i = 0; i < childContainer.getChildCount(); i++) {
            childContainer.getChildAt(i).setEnabled(initialEnabled);
        }
        
        mainCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < childContainer.getChildCount(); i++) {
                childContainer.getChildAt(i).setEnabled(isChecked);
            }
        });
        
        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                settingsJson.put("socks_proxy", mainCheckbox.isChecked());
                
                String hostText = hostInput.getText() != null ? hostInput.getText().toString().trim() : "";
                String portText = portInput.getText() != null ? portInput.getText().toString().trim() : "";
                String userText = userInput.getText() != null ? userInput.getText().toString().trim() : "";
                String passText = passInput.getText() != null ? passInput.getText().toString().trim() : "";
                
                settingsJson.put("socks_proxy_host", hostText);
                if (!portText.isEmpty()) settingsJson.put("socks_proxy_port", Integer.parseInt(portText));
                settingsJson.put("socks_proxy_user", userText);
                settingsJson.put("socks_proxy_pass", passText);
                
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException | NumberFormatException e) {
                Log.e(TAG, "Error saving SOCKS proxy settings", e);
                Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        builder.show();
    }
    
    /**
     * Shows the Fake Camera floating dialog with parent/child checkbox pattern.
     */
    private void showFakeCameraDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Fake Camera");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);
        
        // Main checkbox for enabling fake camera
        final CheckBox mainCheckbox = new CheckBox(this);
        mainCheckbox.setText("Fake Camera");
        mainCheckbox.setTextSize(16);
        mainCheckbox.setTypeface(mainCheckbox.getTypeface(), android.graphics.Typeface.BOLD);
        mainCheckbox.setChecked(settingsJson.optBoolean("FakeCamera", true));
        container.addView(mainCheckbox);
        
        // Child options container (indented)
        LinearLayout childContainer = new LinearLayout(this);
        childContainer.setOrientation(LinearLayout.VERTICAL);
        childContainer.setPadding((int)(24 * density), 0, 0, 0);
        
        final CheckBox addPictureAttrsCheckbox = new CheckBox(this);
        addPictureAttrsCheckbox.setText("Fake Camera Add Picture Attributes");
        addPictureAttrsCheckbox.setChecked(settingsJson.optBoolean("AddExifAttributes", true));
        childContainer.addView(addPictureAttrsCheckbox);
        
        final CheckBox addSpoofedLocationCheckbox = new CheckBox(this);
        addSpoofedLocationCheckbox.setText("Fake Camera Add Spoofed Location");
        addSpoofedLocationCheckbox.setChecked(settingsJson.optBoolean("AddSpoofedLocation", false));
        childContainer.addView(addSpoofedLocationCheckbox);
        
        final CheckBox alternativeModeCheckbox = new CheckBox(this);
        alternativeModeCheckbox.setText("Fake Camera Alternative Mode");
        alternativeModeCheckbox.setChecked(settingsJson.optBoolean("FakeCameraAlternativeMode", true));
        childContainer.addView(alternativeModeCheckbox);
        
        final CheckBox appSupportCheckbox = new CheckBox(this);
        appSupportCheckbox.setText("Fake Camera App Support");
        appSupportCheckbox.setChecked(settingsJson.optBoolean("FakeCameraAppSupport", true));
        childContainer.addView(appSupportCheckbox);
        
        final CheckBox closeStreamCheckbox = new CheckBox(this);
        closeStreamCheckbox.setText("Fake Camera Close Stream Workaround");
        closeStreamCheckbox.setChecked(settingsJson.optBoolean("FakeCameraCloseStreamWorkaround", true));
        childContainer.addView(closeStreamCheckbox);
        
        final CheckBox fixOrientationCheckbox = new CheckBox(this);
        fixOrientationCheckbox.setText("Fake Camera Fix Orientation");
        fixOrientationCheckbox.setChecked(settingsJson.optBoolean("FakeCameraFixOrientation", true));
        childContainer.addView(fixOrientationCheckbox);
        
        final CheckBox flipHorizontallyCheckbox = new CheckBox(this);
        flipHorizontallyCheckbox.setText("Fake Camera Flip Horizontally");
        flipHorizontallyCheckbox.setChecked(settingsJson.optBoolean("FlipHorizontally", false));
        childContainer.addView(flipHorizontallyCheckbox);
        
        final CheckBox openStreamCheckbox = new CheckBox(this);
        openStreamCheckbox.setText("Fake Camera Open Stream Workaround");
        openStreamCheckbox.setChecked(settingsJson.optBoolean("FakeCameraOpenStreamWorkaround", true));
        childContainer.addView(openStreamCheckbox);
        
        final CheckBox randomizePictureCheckbox = new CheckBox(this);
        randomizePictureCheckbox.setText("Fake Camera Randomize Picture");
        randomizePictureCheckbox.setChecked(settingsJson.optBoolean("RandomizeImage", false));
        childContainer.addView(randomizePictureCheckbox);
        
        // Randomize Picture Strength (slider or input)
        TextView strengthLabel = new TextView(this);
        strengthLabel.setText("Fake Camera Randomize Picture Strength");
        strengthLabel.setTextSize(14);
        LinearLayout.LayoutParams strengthLabelParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        strengthLabelParams.topMargin = (int)(8 * density);
        strengthLabel.setLayoutParams(strengthLabelParams);
        childContainer.addView(strengthLabel);
        
        final EditText strengthInput = new EditText(this);
        strengthInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        strengthInput.setText(String.valueOf(settingsJson.optInt("RandomizeStrength", 25)));
        strengthInput.setHint("25");
        LinearLayout.LayoutParams strengthParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        strengthParams.bottomMargin = (int)(8 * density);
        strengthInput.setLayoutParams(strengthParams);
        childContainer.addView(strengthInput);
        
        final CheckBox resizePictureCheckbox = new CheckBox(this);
        resizePictureCheckbox.setText("Fake Camera Resize Picture");
        resizePictureCheckbox.setChecked(settingsJson.optBoolean("ResizeImage", false));
        childContainer.addView(resizePictureCheckbox);
        
        container.addView(childContainer);
        
        // Enable/disable child options based on main checkbox
        boolean initialEnabled = mainCheckbox.isChecked();
        for (int i = 0; i < childContainer.getChildCount(); i++) {
            childContainer.getChildAt(i).setEnabled(initialEnabled);
        }
        
        mainCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < childContainer.getChildCount(); i++) {
                childContainer.getChildAt(i).setEnabled(isChecked);
            }
        });
        
        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                settingsJson.put("FakeCamera", mainCheckbox.isChecked());
                settingsJson.put("AddExifAttributes", addPictureAttrsCheckbox.isChecked());
                settingsJson.put("AddSpoofedLocation", addSpoofedLocationCheckbox.isChecked());
                settingsJson.put("FakeCameraAlternativeMode", alternativeModeCheckbox.isChecked());
                settingsJson.put("FakeCameraAppSupport", appSupportCheckbox.isChecked());
                settingsJson.put("FakeCameraCloseStreamWorkaround", closeStreamCheckbox.isChecked());
                settingsJson.put("FakeCameraFixOrientation", fixOrientationCheckbox.isChecked());
                settingsJson.put("FlipHorizontally", flipHorizontallyCheckbox.isChecked());
                settingsJson.put("FakeCameraOpenStreamWorkaround", openStreamCheckbox.isChecked());
                settingsJson.put("RandomizeImage", randomizePictureCheckbox.isChecked());
                settingsJson.put("ResizeImage", resizePictureCheckbox.isChecked());
                
                String strengthText = strengthInput.getText() != null ? strengthInput.getText().toString().trim() : "25";
                if (!strengthText.isEmpty()) {
                    settingsJson.put("RandomizeStrength", Integer.parseInt(strengthText));
                }
                
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException | NumberFormatException e) {
                Log.e(TAG, "Error saving fake camera settings", e);
                Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        builder.show();
    }
    
    /**
     * Shows the Identity Notifications settings floating dialog with parent/child checkbox pattern.
     */
    private void showIdentityNotificationsSettingsDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder builder =
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this);
        builder.setTitle("Identity Notifications");
        
        float density = getResources().getDisplayMetrics().density;
        int padding = (int) (20 * density);
        
        ScrollView scrollView = new ScrollView(this);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(padding, (int)(12 * density), padding, padding);
        scrollView.addView(container);
        
        // Main checkbox for enabling identity notifications
        final CheckBox mainCheckbox = new CheckBox(this);
        mainCheckbox.setText("Identity Notifications");
        mainCheckbox.setTextSize(16);
        mainCheckbox.setTypeface(mainCheckbox.getTypeface(), android.graphics.Typeface.BOLD);
        mainCheckbox.setChecked(settingsJson.optBoolean("identity_notifications", true));
        container.addView(mainCheckbox);
        
        // Child options container (indented)
        LinearLayout childContainer = new LinearLayout(this);
        childContainer.setOrientation(LinearLayout.VERTICAL);
        childContainer.setPadding((int)(24 * density), 0, 0, 0);
        
        final CheckBox clearCacheCheckbox = new CheckBox(this);
        clearCacheCheckbox.setText("Clear Cache on New Identity");
        clearCacheCheckbox.setChecked(settingsJson.optBoolean("identity_notifications_clear_cache", true));
        childContainer.addView(clearCacheCheckbox);
        
        final CheckBox clearDataCheckbox = new CheckBox(this);
        clearDataCheckbox.setText("Clear App Data on New Identity");
        clearDataCheckbox.setChecked(settingsJson.optBoolean("identity_notifications_clear_data", true));
        childContainer.addView(clearDataCheckbox);
        
        final CheckBox restartAppCheckbox = new CheckBox(this);
        restartAppCheckbox.setText("Restart App on New Identity");
        restartAppCheckbox.setChecked(settingsJson.optBoolean("identity_notifications_restart_app", true));
        childContainer.addView(restartAppCheckbox);
        
        container.addView(childContainer);
        
        // Enable/disable child options based on main checkbox
        boolean initialEnabled = mainCheckbox.isChecked();
        for (int i = 0; i < childContainer.getChildCount(); i++) {
            childContainer.getChildAt(i).setEnabled(initialEnabled);
        }
        
        mainCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (int i = 0; i < childContainer.getChildCount(); i++) {
                childContainer.getChildAt(i).setEnabled(isChecked);
            }
        });
        
        builder.setView(scrollView);
        builder.setNegativeButton("Cancel", null);
        builder.setPositiveButton("OK", (dialog, which) -> {
            try {
                settingsJson.put("identity_notifications", mainCheckbox.isChecked());
                settingsJson.put("identity_notifications_clear_cache", clearCacheCheckbox.isChecked());
                settingsJson.put("identity_notifications_clear_data", clearDataCheckbox.isChecked());
                settingsJson.put("identity_notifications_restart_app", restartAppCheckbox.isChecked());
                
                saveSettings();
                adapter.notifyDataSetChanged();
            } catch (JSONException e) {
                Log.e(TAG, "Error saving identity notifications settings", e);
                Toast.makeText(this, "Error saving settings: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        
        builder.show();
    }
    
    // Data class for setting items
    private static class SettingItem {
        String key;
        Object value;
        boolean isBoolean;
        boolean isAction;
        
        SettingItem(String key, Object value) {
            this.key = key;
            this.value = value;
            this.isBoolean = value instanceof Boolean;
            this.isAction = false;
        }
    }
    
    // Adapter for settings list
    private class CategorySettingsAdapter extends BaseAdapter {
        private final List<SettingItem> items;
        
        CategorySettingsAdapter(List<SettingItem> items) {
            this.items = items;
        }
        
        @Override
        public int getCount() {
            return items.size();
        }
        
        @Override
        public Object getItem(int position) {
            return items.get(position);
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
            
            SettingItem item = items.get(position);
            
            TextView nameView = convertView.findViewById(R.id.setting_name);
            TextView valueView = convertView.findViewById(R.id.setting_value);
            View iconContainer = convertView.findViewById(R.id.icon_container);
            ImageView chevron = convertView.findViewById(R.id.setting_chevron);
            MaterialSwitch toggle = convertView.findViewById(R.id.setting_switch);
            View generateBtn = convertView.findViewById(R.id.generate_button);
            TextView optionValue = convertView.findViewById(R.id.setting_option_value);
            
            // Reset views
            iconContainer.setVisibility(View.GONE);
            chevron.setVisibility(View.GONE);
            toggle.setVisibility(View.GONE);
            generateBtn.setVisibility(View.GONE);
            optionValue.setVisibility(View.GONE);
            valueView.setVisibility(View.GONE);
            toggle.setOnCheckedChangeListener(null);
            
            // Handle action items specially
            if (item.isAction) {
                if ("new_identity_action".equals(item.key)) {
                    nameView.setText("New Identity");
                    valueView.setVisibility(View.VISIBLE);
                    valueView.setText("Generate random device identifiers");
                    chevron.setVisibility(View.VISIBLE);
                }
                return convertView;
            }
            
            // Set name
            nameView.setText(CloneSettings.getDisplayNameForKey(item.key));
            
            if (item.isBoolean) {
                // Boolean setting - show toggle
                toggle.setVisibility(View.VISIBLE);
                toggle.setChecked(item.value instanceof Boolean && (Boolean) item.value);
                
                toggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    try {
                        item.value = isChecked;
                        settingsJson.put(item.key, isChecked);
                        saveSettings();
                    } catch (JSONException e) {
                        Log.e(TAG, "Error saving boolean setting", e);
                    }
                });
            } else if (item.key.startsWith("mock_") && item.key.endsWith("_connection")) {
                // Mock connection setting - show current option
                String currentVal = (item.value != null && !JSONObject.NULL.equals(item.value)) 
                    ? item.value.toString() : "";
                String displayVal = "No change";
                if ("CONNECTED".equalsIgnoreCase(currentVal)) displayVal = "Connected";
                else if ("DISCONNECTED".equalsIgnoreCase(currentVal)) displayVal = "Disconnected";
                
                optionValue.setVisibility(View.VISIBLE);
                optionValue.setText(displayVal);
                chevron.setVisibility(View.VISIBLE);
            } else {
                // Other settings - show value and chevron
                chevron.setVisibility(View.VISIBLE);
                valueView.setVisibility(View.VISIBLE);
                
                if (item.value == null || JSONObject.NULL.equals(item.value) || item.value.toString().trim().isEmpty()) {
                    valueView.setText("Tap to set a value");
                } else if (isNoChangeValue(item.value)) {
                    valueView.setText("System value (no change)");
                } else if (isRandomValue(item.value)) {
                    valueView.setText("Randomized on each launch");
                } else if (isHideValue(item.value)) {
                    valueView.setText("Hidden");
                } else {
                    String valueStr = item.value.toString();
                    if (valueStr.length() > 30) {
                        valueStr = valueStr.substring(0, 27) + "...";
                    }
                    valueView.setText(valueStr);
                }
            }
            
            return convertView;
        }
        
        private boolean isNoChangeValue(Object value) {
            return value != null && "nochange".equalsIgnoreCase(value.toString());
        }
        
        private boolean isRandomValue(Object value) {
            return value != null && "random".equalsIgnoreCase(value.toString());
        }
        
        private boolean isHideValue(Object value) {
            return value != null && "hide".equalsIgnoreCase(value.toString());
        }
    }
}
