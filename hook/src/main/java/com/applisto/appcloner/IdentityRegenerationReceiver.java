package com.applisto.appcloner;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * BroadcastReceiver that handles identity regeneration requests from the app cloner.
 * When triggered, it generates new random device identifiers (Android ID, IMEI, IMSI, etc.),
 * optionally clears app cache/data, and restarts the app.
 */
public class IdentityRegenerationReceiver extends BroadcastReceiver {
    private static final String TAG = "IdentityRegenerationRcv";
    public static final String ACTION_REGENERATE_IDENTITY = "com.applisto.appcloner.ACTION_REGENERATE_IDENTITY";
    private static final String IPC_PERMISSION = "com.appcloner.replica.permission.REPLICA_IPC";
    private static final SecureRandom random = new SecureRandom();
    private static final String NOTIFICATION_CHANNEL_ID = "identity_regeneration";
    private static final int NOTIFICATION_ID = 0x4944; // "ID" in hex for uniqueness
    
    // Persistent notification settings
    private static final String PREFS_NAME = "identity_notification_prefs";
    private static final String PREF_NOTIFICATION_SHOWN = "notification_shown";

    // Keys for extras
    public static final String EXTRA_CLEAR_CACHE = "clear_cache";
    public static final String EXTRA_CLEAR_DATA = "clear_data";
    public static final String EXTRA_RESTART_APP = "restart_app";
    public static final String EXTRA_ANDROID_ID = "android_id";
    public static final String EXTRA_IMEI = "imei";
    public static final String EXTRA_IMSI = "imsi";
    public static final String EXTRA_SERIAL = "serial";
    public static final String EXTRA_WIFI_MAC = "wifi_mac";
    public static final String EXTRA_BLUETOOTH_MAC = "bluetooth_mac";

    private static final Object LOCK = new Object();
    private static boolean sIsProcessing = false;

    /**
     * Displays a notification inside the cloned app that triggers identity regeneration when tapped.
     * This notification is shown on every app launch to provide persistent access to identity regeneration.
     * 
     * @param context Application context
     * @param clearCache Whether to clear cache on regeneration
     * @param clearData Whether to clear app data on regeneration  
     * @param restartApp Whether to restart app after regeneration
     */
    public static void showIdentityNotification(Context context, boolean clearCache, boolean clearData,
            boolean restartApp) {
        showIdentityNotification(context, clearCache, clearData, restartApp, false);
    }
    
    /**
     * Displays a persistent notification inside the cloned app that triggers identity regeneration when tapped.
     * The notification persists and is re-shown on every app launch.
     * 
     * @param context Application context
     * @param clearCache Whether to clear cache on regeneration
     * @param clearData Whether to clear app data on regeneration
     * @param restartApp Whether to restart app after regeneration
     * @param persistent If true, notification will be ongoing and cannot be dismissed
     */
    public static void showIdentityNotification(Context context, boolean clearCache, boolean clearData,
            boolean restartApp, boolean persistent) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) {
                Log.w(TAG, "NotificationManager unavailable; cannot show identity notification");
                return;
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        NOTIFICATION_CHANNEL_ID,
                        "Identity Regeneration",
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription("Generate a fresh device identity for this cloned app.");
                nm.createNotificationChannel(channel);
            }

            Intent regenerateIntent = new Intent(context, IdentityRegenerationReceiver.class);
            regenerateIntent.setAction(ACTION_REGENERATE_IDENTITY);
            regenerateIntent.putExtra(EXTRA_CLEAR_CACHE, clearCache);
            regenerateIntent.putExtra(EXTRA_CLEAR_DATA, clearData);
            regenerateIntent.putExtra(EXTRA_RESTART_APP, restartApp);
            // Flag to indicate build props should be randomized
            regenerateIntent.putExtra("randomize_build_props", true);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    regenerateIntent,
                    flags
            );

            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Generate new identity")
                    .setContentText("Tap to refresh identity, randomize device profile and restart.")
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText("Tap to generate a new device identity with randomized build properties, clear app data, and restart this clone."))
                    .setSmallIcon(context.getApplicationInfo().icon)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent);
            
            // Make notification persistent if requested
            if (persistent) {
                builder.setOngoing(true)
                       .setAutoCancel(false);
            } else {
                builder.setAutoCancel(true);
            }

            nm.notify(NOTIFICATION_ID, builder.build());
            
            // Mark notification as shown
            markNotificationShown(context);
            
            Log.i(TAG, "Identity notification displayed (persistent=" + persistent + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to show identity notification", t);
        }
    }
    
    /**
     * Shows a persistent notification that cannot be dismissed.
     * This is called on every app launch to ensure the notification is always visible.
     */
    public static void showPersistentIdentityNotification(Context context, boolean clearCache, 
            boolean clearData, boolean restartApp) {
        showIdentityNotification(context, clearCache, clearData, restartApp, true);
    }
    
    /**
     * Check if the identity notification should be shown (always true for persistent mode).
     */
    public static boolean shouldShowNotification(Context context) {
        // Always show on app launch for persistent notification
        return true;
    }
    
    /**
     * Mark that notification was shown (for tracking purposes).
     */
    private static void markNotificationShown(Context context) {
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                   .edit()
                   .putLong(PREF_NOTIFICATION_SHOWN, System.currentTimeMillis())
                   .apply();
        } catch (Throwable t) {
            Log.w(TAG, "Failed to mark notification shown", t);
        }
    }
    
    /**
     * Cancel the identity notification.
     */
    public static void cancelNotification(Context context) {
        try {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.cancel(NOTIFICATION_ID);
                Log.d(TAG, "Identity notification cancelled");
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to cancel notification", t);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!ACTION_REGENERATE_IDENTITY.equals(intent.getAction())) {
            return;
        }

        Log.i(TAG, "Received identity regeneration request.");

        synchronized (LOCK) {
            if (sIsProcessing) {
                Log.w(TAG, "Identity regeneration already in progress, skipping duplicate request.");
                return;
            }
            sIsProcessing = true;
        }

        final boolean clearCache = intent.getBooleanExtra(EXTRA_CLEAR_CACHE, false);
        final boolean clearData = intent.getBooleanExtra(EXTRA_CLEAR_DATA, false);
        final boolean restartApp = intent.getBooleanExtra(EXTRA_RESTART_APP, true);
        final boolean randomizeBuildProps = intent.getBooleanExtra("randomize_build_props", true);
        
        // Get new identity values from intent, or generate new ones
        final String newAndroidId = intent.getStringExtra(EXTRA_ANDROID_ID);
        final String newImei = intent.getStringExtra(EXTRA_IMEI);
        final String newImsi = intent.getStringExtra(EXTRA_IMSI);
        final String newSerial = intent.getStringExtra(EXTRA_SERIAL);
        final String newWifiMac = intent.getStringExtra(EXTRA_WIFI_MAC);
        final String newBluetoothMac = intent.getStringExtra(EXTRA_BLUETOOTH_MAC);
        
        final String senderPackage = intent.getStringExtra("sender_package");
        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            boolean success = false;
            String errorMessage = null;
            
            try {
                // 1. Update the identity values in cloner.json (runtime storage)
                success = updateIdentityValues(context,
                    newAndroidId, newImei, newImsi, newSerial, newWifiMac, newBluetoothMac);

                if (success) {
                    // 2. Clear cache if requested
                    if (clearCache) {
                        clearAppCache(context);
                    }
                    
                    // 3. Clear app data if requested (this will also clear SharedPreferences)
                    if (clearData) {
                        clearAppData(context);
                    }
                    
                    // 4. Update hook instances with new values
                    updateHookInstances(context, newAndroidId, newImei, newImsi,
                        newSerial, newWifiMac, newBluetoothMac);

                    // 5. Randomize build props so device profile changes with new identity
                    if (randomizeBuildProps) {
                        randomizeBuildProps(context);
                    }
                }
            } catch (Throwable t) {
                Log.e(TAG, "Error during identity regeneration", t);
                errorMessage = t.getMessage();
                success = false;
            } finally {
                synchronized (LOCK) {
                    sIsProcessing = false;
                }
                
                // Send result broadcast
                sendResultBroadcast(context, senderPackage, success, errorMessage);
                
                // 6. Restart app if requested
                if (restartApp && success) {
                    restartApplication(context);
                }
                
                pendingResult.finish();
            }
        }).start();
    }

    /**
     * Updates the identity values in the runtime cloner.json file.
     */
    private boolean updateIdentityValues(Context context, String androidId, String imei,
            String imsi, String serial, String wifiMac, String bluetoothMac) {
        
        File filesDir = context.getFilesDir();
        File clonerJsonFile = new File(filesDir, "cloner.json");
        
        try {
            JSONObject json;
            
            // Read existing file or create new one
            if (clonerJsonFile.exists()) {
                byte[] buf = new byte[(int) clonerJsonFile.length()];
                try (FileInputStream fis = new FileInputStream(clonerJsonFile)) {
                    int read = fis.read(buf);
                    if (read > 0) {
                        json = new JSONObject(new String(buf, 0, read));
                    } else {
                        json = new JSONObject();
                    }
                }
            } else {
                json = new JSONObject();
            }
            
            // Update values (use provided or generate new)
            json.put("android_id", androidId != null ? androidId : generateRandomAndroidId());
            json.put("imei", imei != null ? imei : generateRandomImei());
            json.put("imsi", imsi != null ? imsi : generateRandomImsi());
            json.put("serial_number", serial != null ? serial : generateRandomSerial());
            json.put("wifi_mac", wifiMac != null ? wifiMac : generateRandomMac());
            json.put("bluetooth_mac", bluetoothMac != null ? bluetoothMac : generateRandomMac());
            
            // Write back to file
            try (FileOutputStream fos = new FileOutputStream(clonerJsonFile)) {
                fos.write(json.toString(2).getBytes("UTF-8"));
            }
            
            Log.i(TAG, "Identity values updated successfully in cloner.json");
            return true;
            
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to update identity values", e);
            return false;
        }
    }

    /**
     * Updates the static values in hook instances.
     */
    private void updateHookInstances(Context context, String androidId, String imei,
            String imsi, String serial, String wifiMac, String bluetoothMac) {
        try {
            // Update AndroidIdHook
            if (androidId != null && !androidId.isEmpty()) {
                // The hooks use static fields, so we need to reinitialize them
                // For now, just log - actual update requires app restart
                Log.d(TAG, "New Android ID set: " + androidId);
            }
            
            // Update ImeiHook
            if (imei != null && !imei.isEmpty()) {
                ImeiHook.setImei(imei);
            }
            
            // Update ImsiHook
            if (imsi != null && !imsi.isEmpty()) {
                ImsiHook.setImsi(imsi);
            }
            
            // Update SerialHook
            if (serial != null && !serial.isEmpty()) {
                SerialHook.setSerial(serial);
            }
            
            // Update WifiMacHook
            if (wifiMac != null && !wifiMac.isEmpty()) {
                WifiMacHook.setMac(wifiMac);
            }
            
            // Update BtMacHook
            if (bluetoothMac != null && !bluetoothMac.isEmpty()) {
                BtMacHook.setMac(bluetoothMac);
            }
            
            Log.i(TAG, "Hook instances updated with new identity values");
        } catch (Throwable t) {
            Log.e(TAG, "Error updating hook instances", t);
        }
    }

    /**
     * Randomizes build properties so device profile changes together with identity.
     * This selects a random device preset and generates a new fingerprint.
     */
    private void randomizeBuildProps(Context context) {
        try {
            Log.i(TAG, "Randomizing build properties for new identity...");
            
            // Always randomize build props when called (don't check settings here,
            // caller decides whether to call this method)
            String selectedPreset = BuildPropsHook.getRandomDevicePreset();
            if (selectedPreset != null) {
                Log.i(TAG, "Selected random device preset: " + selectedPreset);
            }
            
            BuildPropsHook.randomizeBuildPropsRuntime();
            
            // Log the new build properties
            Log.i(TAG, "Build properties randomized successfully:");
            Log.i(TAG, "  MANUFACTURER: " + android.os.Build.MANUFACTURER);
            Log.i(TAG, "  MODEL: " + android.os.Build.MODEL);
            Log.i(TAG, "  BRAND: " + android.os.Build.BRAND);
            Log.i(TAG, "  DEVICE: " + android.os.Build.DEVICE);
            Log.i(TAG, "  FINGERPRINT: " + android.os.Build.FINGERPRINT);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to randomize build props", t);
        }
    }

    /**
     * Clears the app cache.
     */
    private void clearAppCache(Context context) {
        try {
            File cacheDir = context.getCacheDir();
            deleteDirectory(cacheDir);
            
            File externalCacheDir = context.getExternalCacheDir();
            if (externalCacheDir != null) {
                deleteDirectory(externalCacheDir);
            }
            
            Log.i(TAG, "App cache cleared");
        } catch (Throwable t) {
            Log.e(TAG, "Error clearing cache", t);
        }
    }

    /**
     * Clears app data (SharedPreferences, databases, etc.) but keeps files directory.
     */
    private void clearAppData(Context context) {
        try {
            // Clear SharedPreferences
            File sharedPrefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            if (sharedPrefsDir.exists()) {
                File[] prefs = sharedPrefsDir.listFiles();
                if (prefs != null) {
                    for (File pref : prefs) {
                        // Keep cloner-related preferences
                        if (!pref.getName().contains("cloner")) {
                            pref.delete();
                        }
                    }
                }
            }
            
            // Clear databases (except SQLite journals)
            File dbDir = new File(context.getApplicationInfo().dataDir, "databases");
            if (dbDir.exists()) {
                File[] dbs = dbDir.listFiles();
                if (dbs != null) {
                    for (File db : dbs) {
                        db.delete();
                    }
                }
            }
            
            Log.i(TAG, "App data cleared");
        } catch (Throwable t) {
            Log.e(TAG, "Error clearing app data", t);
        }
    }

    /**
     * Recursively deletes a directory and its contents.
     */
    private void deleteDirectory(File dir) {
        if (dir == null || !dir.exists()) return;
        
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
    }

    /**
     * Sends result broadcast back to the app cloner.
     */
    private void sendResultBroadcast(Context context, String senderPackage, 
            boolean success, String errorMessage) {
        try {
            Intent resultIntent = new Intent("com.appcloner.replica.IDENTITY_REGENERATED");
            if (senderPackage != null && !senderPackage.isEmpty()) {
                resultIntent.setPackage(senderPackage);
            } else {
                resultIntent.setPackage("com.appcloner.replica");
            }
            resultIntent.putExtra("package", context.getPackageName());
            resultIntent.putExtra("success", success);
            if (errorMessage != null) {
                resultIntent.putExtra("error_message", errorMessage);
            }
            
            context.sendBroadcast(resultIntent, IPC_PERMISSION);
            Log.i(TAG, "Result broadcast sent: success=" + success);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to send result broadcast", t);
        }
    }

    /**
     * Restarts the application.
     */
    private void restartApplication(Context context) {
        try {
            // Schedule restart with a small delay
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    Intent launchIntent = context.getPackageManager()
                        .getLaunchIntentForPackage(context.getPackageName());
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        context.startActivity(launchIntent);
                    }
                    
                    // Kill the current process
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(0);
                } catch (Throwable t) {
                    Log.e(TAG, "Error restarting app", t);
                }
            }, 500);
            
            Log.i(TAG, "App restart scheduled");
        } catch (Throwable t) {
            Log.e(TAG, "Error scheduling restart", t);
        }
    }

    // ===== Random Value Generation Methods =====
    
    /**
     * Generate a random Android ID (16 hex characters).
     */
    public static String generateRandomAndroidId() {
        return String.format(Locale.US, "%016X", random.nextLong());
    }

    /**
     * Generate a random IMEI (15 digits with Luhn checksum).
     */
    public static String generateRandomImei() {
        StringBuilder sb = new StringBuilder();
        // TAC prefix
        sb.append("35");
        for (int i = 0; i < 12; i++) {
            sb.append(random.nextInt(10));
        }
        // Add Luhn checksum
        sb.append(calculateLuhnCheckDigit(sb.toString()));
        return sb.toString();
    }

    /**
     * Generate a random IMSI (15 digits).
     */
    public static String generateRandomImsi() {
        StringBuilder sb = new StringBuilder();
        // MCC (3 digits)
        String[] mccs = {"310", "311", "234", "262", "460"};
        sb.append(mccs[random.nextInt(mccs.length)]);
        // MNC (2 digits)
        sb.append(String.format("%02d", random.nextInt(100)));
        // MSIN (10 digits)
        for (int i = sb.length(); i < 15; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /**
     * Generate a random serial number (10-16 alphanumeric characters).
     */
    public static String generateRandomSerial() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        int length = 10 + random.nextInt(7);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    /**
     * Generate a random MAC address.
     */
    public static String generateRandomMac() {
        byte[] macBytes = new byte[6];
        random.nextBytes(macBytes);
        // Set locally administered bit and clear multicast bit
        macBytes[0] = (byte) ((macBytes[0] & 0xFC) | 0x02);
        StringBuilder sb = new StringBuilder(17);
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", macBytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Calculate Luhn check digit for IMEI.
     */
    private static int calculateLuhnCheckDigit(String digits) {
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
}
