package com.applisto.appcloner;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * HideSimOperatorHook - Hides or spoofs SIM operator information.
 * 
 * Based on secondary.jar ChangeImeiImsiHideSimOperatorInfo behavior:
 * - Hooks TelephonyManager methods to hide/spoof operator info
 * - Can hide: operator name, numeric code, country ISO, network operator
 * - Prevents fingerprinting via carrier/SIM information
 */
public class HideSimOperatorHook {
    private static final String TAG = "HideSimOperator";
    
    private static boolean sInstalled = false;
    
    // Spoofed values (null means hide/return empty)
    private static String sSpoofedOperatorName = "";
    private static String sSpoofedOperatorNumeric = "";
    private static String sSpoofedSimOperatorName = "";
    private static String sSpoofedSimOperatorNumeric = "";
    private static String sSpoofedSimCountryIso = "";
    private static String sSpoofedNetworkCountryIso = "";
    private static String sSpoofedNetworkOperatorName = "";
    private static String sSpoofedNetworkOperator = "";
    
    /**
     * Install the SIM operator hiding hook.
     * By default, hides all operator information (returns empty strings).
     */
    public static void install(Context context) {
        install(context, true);
    }
    
    /**
     * Install the SIM operator hiding hook.
     * 
     * @param context Application context
     * @param hideAll If true, hide all operator info; if false, use spoofed values
     */
    public static void install(Context context, boolean hideAll) {
        if (sInstalled || context == null) return;
        
        try {
            hookTelephonyManager();
            sInstalled = true;
            Log.i(TAG, "HideSimOperatorHook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install HideSimOperatorHook", t);
        }
    }
    
    /**
     * Install with custom spoofed values.
     */
    public static void install(Context context, 
                               String operatorName, 
                               String operatorNumeric,
                               String simOperatorName,
                               String simCountryIso,
                               String networkOperatorName,
                               String networkCountryIso) {
        if (operatorName != null) sSpoofedOperatorName = operatorName;
        if (operatorNumeric != null) sSpoofedOperatorNumeric = operatorNumeric;
        if (simOperatorName != null) sSpoofedSimOperatorName = simOperatorName;
        if (simCountryIso != null) sSpoofedSimCountryIso = simCountryIso;
        if (networkOperatorName != null) sSpoofedNetworkOperatorName = networkOperatorName;
        if (networkCountryIso != null) sSpoofedNetworkCountryIso = networkCountryIso;
        
        install(context, false);
    }
    
    /**
     * Hook TelephonyManager methods that return operator information.
     */
    private static void hookTelephonyManager() {
        try {
            Class<?> tmClass = TelephonyManager.class;
            
            // Hook getSimOperatorName()
            hookMethod(tmClass, "getSimOperatorName", () -> sSpoofedSimOperatorName);
            
            // Hook getSimOperator()
            hookMethod(tmClass, "getSimOperator", () -> sSpoofedSimOperatorNumeric);
            
            // Hook getSimCountryIso()
            hookMethod(tmClass, "getSimCountryIso", () -> sSpoofedSimCountryIso);
            
            // Hook getNetworkOperatorName()
            hookMethod(tmClass, "getNetworkOperatorName", () -> sSpoofedNetworkOperatorName);
            
            // Hook getNetworkOperator()
            hookMethod(tmClass, "getNetworkOperator", () -> sSpoofedNetworkOperator);
            
            // Hook getNetworkCountryIso()
            hookMethod(tmClass, "getNetworkCountryIso", () -> sSpoofedNetworkCountryIso);
            
            // Hook getCarrierName() (API 29+)
            try {
                hookMethod(tmClass, "getSimCarrierIdName", () -> sSpoofedOperatorName);
            } catch (NoSuchMethodException ignored) {}
            
            Log.d(TAG, "TelephonyManager hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook TelephonyManager", t);
        }
    }
    
    /**
     * Helper to hook a no-arg method that returns String.
     */
    private static void hookMethod(Class<?> clazz, String methodName, 
                                   java.util.function.Supplier<String> valueSupplier) throws NoSuchMethodException {
        try {
            Method method = clazz.getDeclaredMethod(methodName);
            Hooking.pineHook(method, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String spoofed = valueSupplier.get();
                    if (spoofed != null) {
                        frame.setResult(spoofed);
                        Log.d(TAG, methodName + "() -> " + (spoofed.isEmpty() ? "(empty)" : spoofed));
                    }
                }
            });
            Log.d(TAG, "Hooked " + methodName);
        } catch (NoSuchMethodException e) {
            Log.d(TAG, "Method not found: " + methodName);
            throw e;
        }
    }
    
    /**
     * Set spoofed operator name.
     */
    public static void setSpoofedOperatorName(String name) {
        sSpoofedOperatorName = name;
        sSpoofedSimOperatorName = name;
        sSpoofedNetworkOperatorName = name;
    }
    
    /**
     * Set spoofed operator numeric code.
     */
    public static void setSpoofedOperatorNumeric(String numeric) {
        sSpoofedOperatorNumeric = numeric;
        sSpoofedSimOperatorNumeric = numeric;
        sSpoofedNetworkOperator = numeric;
    }
    
    /**
     * Set spoofed country ISO.
     */
    public static void setSpoofedCountryIso(String iso) {
        sSpoofedSimCountryIso = iso;
        sSpoofedNetworkCountryIso = iso;
    }
    
    /**
     * Configure to hide all operator info (return empty strings).
     */
    public static void hideAll() {
        sSpoofedOperatorName = "";
        sSpoofedOperatorNumeric = "";
        sSpoofedSimOperatorName = "";
        sSpoofedSimOperatorNumeric = "";
        sSpoofedSimCountryIso = "";
        sSpoofedNetworkCountryIso = "";
        sSpoofedNetworkOperatorName = "";
        sSpoofedNetworkOperator = "";
    }
    
    /**
     * Configure to spoof as a specific carrier.
     * Example carriers: T-Mobile US, Verizon, AT&T, etc.
     */
    public static void spoofAsCarrier(String carrierName, String mcc, String mnc, String countryIso) {
        String numeric = mcc + mnc;
        sSpoofedOperatorName = carrierName;
        sSpoofedOperatorNumeric = numeric;
        sSpoofedSimOperatorName = carrierName;
        sSpoofedSimOperatorNumeric = numeric;
        sSpoofedSimCountryIso = countryIso;
        sSpoofedNetworkCountryIso = countryIso;
        sSpoofedNetworkOperatorName = carrierName;
        sSpoofedNetworkOperator = numeric;
    }
    
    /**
     * Pre-configured carrier profiles.
     */
    public static void spoofAsTMobileUS() {
        spoofAsCarrier("T-Mobile", "310", "260", "us");
    }
    
    public static void spoofAsVerizonUS() {
        spoofAsCarrier("Verizon", "311", "480", "us");
    }
    
    public static void spoofAsATTUS() {
        spoofAsCarrier("AT&T", "310", "410", "us");
    }
    
    /**
     * Check if the hook is installed.
     */
    public static boolean isInstalled() {
        return sInstalled;
    }
}
