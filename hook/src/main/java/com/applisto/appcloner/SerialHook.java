package com.applisto.appcloner;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * Hook for spoofing Android device serial number.
 * Supports both Build.getSerial() method and Build.SERIAL field.
 * 
 * Modes:
 * - "nochange" or empty: return system value (no hook)
 * - "random": generate random serial on each app launch
 * - custom value: use the provided serial number
 */
public final class SerialHook {
    private static final String TAG = "SerialHook";
    private static final String MODE_NO_CHANGE = "nochange";
    private static final String MODE_RANDOM = "random";
    private static String sFakeSerial = null;

    public void init(Context ctx) {
        // Load serial from settings
        String configSerial = ClonerSettings.get(ctx).serialNumber();
        
        // Handle different modes: nochange, random, or custom value
        if (TextUtils.isEmpty(configSerial) || MODE_NO_CHANGE.equalsIgnoreCase(configSerial)) {
            Log.i(TAG, "Serial set to NO_CHANGE, using system value.");
            return;
        }
        
        if (MODE_RANDOM.equalsIgnoreCase(configSerial)) {
            sFakeSerial = generateRandomSerial();
            Log.i(TAG, "Serial set to RANDOM, generated: " + sFakeSerial);
        } else {
            // Custom value provided
            sFakeSerial = configSerial;
            Log.i(TAG, "Serial set to CUSTOM: " + sFakeSerial);
        }
        
        Log.i(TAG, "Installing Serial hook → " + sFakeSerial);
        
        // Hook Build.getSerial() method (API 26+)
        try {
            Method m = Build.class.getDeclaredMethod("getSerial");
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeSerial);
                    Log.d(TAG, "Serial spoofed (getSerial) " + orig + " → " + sFakeSerial);
                }
            });
            Log.i(TAG, "✓ hooked Build.getSerial()");
        } catch (Throwable t) {
            Log.w(TAG, "Build.getSerial() not available", t);
        }
        
        // Also try to override the static Build.SERIAL field for older APIs
        try {
            Field serialField = Build.class.getDeclaredField("SERIAL");
            serialField.setAccessible(true);
            
            // Remove final modifier if present
            try {
                Field modifiersField = Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(serialField, serialField.getModifiers() & ~Modifier.FINAL);
            } catch (NoSuchFieldException ignored) {}
            
            Object oldValue = serialField.get(null);
            serialField.set(null, sFakeSerial);
            Log.i(TAG, "✓ overrode Build.SERIAL: " + oldValue + " → " + sFakeSerial);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to override Build.SERIAL field", t);
        }
    }
    
    /**
     * Set a custom serial number at runtime
     */
    public static void setSerial(String serial) {
        sFakeSerial = serial;
        Log.i(TAG, "Serial updated to: " + serial);
    }
    
    /**
     * Get the current fake serial
     */
    public static String getSerial() {
        return sFakeSerial;
    }
    
    /**
     * Generate a random serial number (alphanumeric, 10-16 chars)
     */
    public static String generateRandomSerial() {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        SecureRandom random = new SecureRandom();
        int length = 10 + random.nextInt(7); // 10-16 characters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
