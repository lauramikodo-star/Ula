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
 * Hook for spoofing IMEI (International Mobile Equipment Identity).
 * Supports multiple TelephonyManager methods:
 * - getDeviceId() - deprecated but still used
 * - getImei() - API 26+
 * - getImei(int slotIndex) - API 26+
 * - getMeid() - API 26+
 * 
 * Modes:
 * - "nochange" or empty: return system value (no hook)
 * - "random": generate random IMEI on each app launch
 * - custom value: use the provided IMEI
 */
public final class ImeiHook {
    private static final String TAG = "ImeiHook";
    private static final String MODE_NO_CHANGE = "nochange";
    private static final String MODE_RANDOM = "random";
    private static String sFakeImei = null;

    public void init(Context context) {
        Log.i(TAG, "Initializing IMEI hook...");
        
        // Load IMEI from settings
        String configImei = ClonerSettings.get(context).imei();
        
        // Handle different modes: nochange, random, or custom value
        if (TextUtils.isEmpty(configImei) || MODE_NO_CHANGE.equalsIgnoreCase(configImei)) {
            Log.i(TAG, "IMEI set to NO_CHANGE, using system value.");
            return;
        }
        
        if (MODE_RANDOM.equalsIgnoreCase(configImei)) {
            sFakeImei = generateRandomImei();
            Log.i(TAG, "IMEI set to RANDOM, generated: " + sFakeImei);
        } else {
            // Custom value provided
            sFakeImei = configImei;
            Log.i(TAG, "IMEI set to CUSTOM: " + sFakeImei);
        }
        
        Log.i(TAG, "Installing IMEI hook → " + sFakeImei);

        // Hook getDeviceId() - deprecated but still common
        hookMethod("getDeviceId");
        
        // Hook getDeviceId(int slotIndex)
        hookMethodWithInt("getDeviceId");
        
        // Hook getImei() - API 26+
        hookMethod("getImei");
        
        // Hook getImei(int slotIndex) - API 26+
        hookMethodWithInt("getImei");
        
        // Hook getMeid() - API 26+
        hookMethod("getMeid");
        
        // Hook getMeid(int slotIndex) - API 26+
        hookMethodWithInt("getMeid");
        
        // Hook getSubscriberId() is handled by ImsiHook
    }
    
    private void hookMethod(String methodName) {
        try {
            Method m = TelephonyManager.class.getDeclaredMethod(methodName);
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeImei);
                    Log.d(TAG, methodName + " spoofed: " + orig + " → " + sFakeImei);
                }
            });
            Log.i(TAG, "✓ hooked TelephonyManager." + methodName + "()");
        } catch (NoSuchMethodException e) {
            Log.d(TAG, methodName + "() not available on this API level");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook " + methodName + "()", t);
        }
    }
    
    private void hookMethodWithInt(String methodName) {
        try {
            Method m = TelephonyManager.class.getDeclaredMethod(methodName, int.class);
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeImei);
                    Log.d(TAG, methodName + "(int) spoofed: " + orig + " → " + sFakeImei);
                }
            });
            Log.i(TAG, "✓ hooked TelephonyManager." + methodName + "(int)");
        } catch (NoSuchMethodException e) {
            Log.d(TAG, methodName + "(int) not available on this API level");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook " + methodName + "(int)", t);
        }
    }
    
    /**
     * Set a custom IMEI at runtime
     */
    public static void setImei(String imei) {
        sFakeImei = imei;
        Log.i(TAG, "IMEI updated to: " + imei);
    }
    
    /**
     * Get the current fake IMEI
     */
    public static String getImei() {
        return sFakeImei;
    }
    
    /**
     * Generate a random valid IMEI
     * IMEI format: TAC (8 digits) + Serial (6 digits) + Check digit (1 digit) = 15 digits
     * @return Random IMEI string
     */
    public static String generateRandomImei() {
        StringBuilder sb = new StringBuilder();
        
        // TAC (Type Allocation Code) - 8 digits
        // Using a common TAC prefix (35824005)
        sb.append("35");
        for (int i = 0; i < 6; i++) {
            sb.append((int) (Math.random() * 10));
        }
        
        // Serial Number - 6 digits
        for (int i = 0; i < 6; i++) {
            sb.append((int) (Math.random() * 10));
        }
        
        // Calculate and append Luhn check digit
        sb.append(calculateLuhnCheckDigit(sb.toString()));
        
        return sb.toString();
    }
    
    /**
     * Calculate Luhn check digit for IMEI
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
    
    /**
     * Validate IMEI format and checksum
     */
    public static boolean isValidImei(String imei) {
        if (imei == null || imei.length() != 15) {
            return false;
        }
        
        try {
            // Check all digits
            for (char c : imei.toCharArray()) {
                if (!Character.isDigit(c)) {
                    return false;
                }
            }
            
            // Validate Luhn checksum
            String withoutCheck = imei.substring(0, 14);
            int calculatedCheck = calculateLuhnCheckDigit(withoutCheck);
            int actualCheck = Character.getNumericValue(imei.charAt(14));
            
            return calculatedCheck == actualCheck;
        } catch (Exception e) {
            return false;
        }
    }
}
