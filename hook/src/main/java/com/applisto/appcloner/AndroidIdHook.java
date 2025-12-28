package com.applisto.appcloner;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Locale;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class AndroidIdHook {

    private static final String TAG = "AndroidIdHook";
    private static final String MODE_NO_CHANGE = "nochange";
    private static final String MODE_RANDOM = "random";
    private static final SecureRandom random = new SecureRandom();
    private static String sFakeId = null;

    public void init(Context context) {
        String configValue = ClonerSettings.get(context).androidId();

        // Handle different modes: nochange, random, or custom value
        if (TextUtils.isEmpty(configValue) || MODE_NO_CHANGE.equalsIgnoreCase(configValue)) {
            Log.i(TAG, "android_id set to NO_CHANGE, using system value.");
            return;
        }

        if (MODE_RANDOM.equalsIgnoreCase(configValue)) {
            sFakeId = generateRandomAndroidId();
            Log.i(TAG, "android_id set to RANDOM, generated: " + sFakeId);
        } else {
            // Custom value provided
            sFakeId = configValue;
            Log.i(TAG, "android_id set to CUSTOM: " + sFakeId);
        }

        Log.i(TAG, "Installing Android-ID hook → " + sFakeId);

        hookSettingsMethod(Settings.Secure.class, sFakeId);
        hookSettingsMethod(Settings.System.class, sFakeId);
        hookSettingsMethod(Settings.Global.class, sFakeId);
    }

    /**
     * Generate a random 16-character hexadecimal Android ID.
     */
    public static String generateRandomAndroidId() {
        return String.format(Locale.US, "%016x", random.nextLong());
    }

    /**
     * Get the current fake Android ID
     */
    public static String getAndroidId() {
        return sFakeId;
    }

    private void hookSettingsMethod(Class<?> settingsClass, final String fakeId) {
        try {
            Method target = settingsClass.getDeclaredMethod(
                    "getString", ContentResolver.class, String.class);

            Hooking.pineHook(target, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    try {
                        String key = (String) frame.args[1];
                        if (Settings.Secure.ANDROID_ID.equals(key)) {
                            Log.d(TAG, "Returning fake ANDROID_ID for " + settingsClass.getSimpleName() + ": " + fakeId);
                            frame.setResult(fakeId);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error in getString hook", t);
                    }
                }
            });
            Log.d(TAG, "Hooked " + settingsClass.getSimpleName() + ".getString");

        } catch (Throwable t) {
            // Some classes might not have the method or might fail to hook
            Log.w(TAG, "Failed to hook " + settingsClass.getSimpleName() + ".getString: " + t.getMessage());
        }

        // Also hook getStringForUser if available (hidden API, used by system and some apps)
        try {
            Method targetForUser = settingsClass.getDeclaredMethod(
                    "getStringForUser", ContentResolver.class, String.class, int.class);

            Hooking.pineHook(targetForUser, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    try {
                        String key = (String) frame.args[1];
                        if (Settings.Secure.ANDROID_ID.equals(key)) {
                            Log.d(TAG, "Returning fake ANDROID_ID for " + settingsClass.getSimpleName() + ".getStringForUser: " + fakeId);
                            frame.setResult(fakeId);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error in getStringForUser hook", t);
                    }
                }
            });
            Log.d(TAG, "Hooked " + settingsClass.getSimpleName() + ".getStringForUser");
        } catch (Throwable t) {
            // Method might not exist on all Android versions
            Log.d(TAG, "Could not hook " + settingsClass.getSimpleName() + ".getStringForUser: " + t.getMessage());
        }
    }
}
