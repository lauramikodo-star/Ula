package com.applisto.appcloner;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * Hook for spoofing IMSI (International Mobile Subscriber Identity).
 * Supports TelephonyManager.getSubscriberId() for all subscription variants.
 * 
 * Modes:
 * - "nochange" or empty: return system value (no hook)
 * - "random": generate random IMSI on each app launch
 * - custom value: use the provided IMSI
 */
public class ImsiHook {
    private static final String TAG = "ImsiHook";
    private static final String MODE_NO_CHANGE = "nochange";
    private static final String MODE_RANDOM = "random";
    private static String sFakeImsi = null;

    public void init(Context context) {
        Log.i(TAG, "Initializing IMSI hook...");
        
        // Load IMSI from settings
        String configImsi = ClonerSettings.get(context).imsi();
        
        // Handle different modes: nochange, random, or custom value
        if (TextUtils.isEmpty(configImsi) || MODE_NO_CHANGE.equalsIgnoreCase(configImsi)) {
            Log.i(TAG, "IMSI set to NO_CHANGE, using system value.");
            return;
        }
        
        if (MODE_RANDOM.equalsIgnoreCase(configImsi)) {
            sFakeImsi = generateRandomImsi("310", "26"); // Default US T-Mobile
            Log.i(TAG, "IMSI set to RANDOM, generated: " + sFakeImsi);
        } else {
            // Custom value provided
            sFakeImsi = configImsi;
            Log.i(TAG, "IMSI set to CUSTOM: " + sFakeImsi);
        }
        
        Log.i(TAG, "Installing IMSI hook → " + sFakeImsi);

        // Hook getSubscriberId() - no parameters version
        try {
            Method m = TelephonyManager.class.getDeclaredMethod("getSubscriberId");
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeImsi);
                    Log.d(TAG, "IMSI spoofed: " + orig + " → " + sFakeImsi);
                }
            });
            Log.i(TAG, "✓ hooked TelephonyManager.getSubscriberId()");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook getSubscriberId()", t);
        }
        
        // Hook getSubscriberId(int subId) - subscription variant (API 22+)
        try {
            Method m = TelephonyManager.class.getDeclaredMethod("getSubscriberId", int.class);
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeImsi);
                    Log.d(TAG, "IMSI spoofed (subId): " + orig + " → " + sFakeImsi);
                }
            });
            Log.i(TAG, "✓ hooked TelephonyManager.getSubscriberId(int)");
        } catch (Throwable t) {
            Log.w(TAG, "getSubscriberId(int) not available", t);
        }
    }
    
    /**
     * Set a custom IMSI at runtime
     */
    public static void setImsi(String imsi) {
        sFakeImsi = imsi;
        Log.i(TAG, "IMSI updated to: " + imsi);
    }
    
    /**
     * Get the current fake IMSI
     */
    public static String getImsi() {
        return sFakeImsi;
    }
    
    /**
     * Generate a random IMSI
     * @param mcc Mobile Country Code (3 digits)
     * @param mnc Mobile Network Code (2-3 digits)
     * @return Random IMSI string
     */
    public static String generateRandomImsi(String mcc, String mnc) {
        StringBuilder sb = new StringBuilder();
        sb.append(mcc);
        sb.append(mnc);
        // Fill remaining 10-11 digits randomly
        int remaining = 15 - sb.length();
        for (int i = 0; i < remaining; i++) {
            sb.append((int) (Math.random() * 10));
        }
        return sb.toString();
    }
}
