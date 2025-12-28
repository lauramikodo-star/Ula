package com.applisto.appcloner;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Random;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * Hook for spoofing WiFi MAC address.
 * Supports:
 * - WifiInfo.getMacAddress()
 * - WifiManager.getConnectionInfo() - modifies returned WifiInfo
 * - NetworkInterface MAC addresses
 * 
 * Modes:
 * - "nochange" or empty: return system value (no hook)
 * - "random": generate random MAC on each app launch
 * - custom value: use the provided MAC address
 */
public final class WifiMacHook {
    private static final String TAG = "WifiMacHook";
    private static final String MODE_NO_CHANGE = "nochange";
    private static final String MODE_RANDOM = "random";
    private static final String DEFAULT_FAKE_MAC = "02:00:00:00:00:00";
    private static String sFakeMac = null;

    public void init(Context ctx) {
        // Load MAC from settings
        String configMac = ClonerSettings.get(ctx).wifiMac();
        
        // Handle different modes: nochange, random, or custom value
        if (TextUtils.isEmpty(configMac) || MODE_NO_CHANGE.equalsIgnoreCase(configMac) || "NO_CHANGE".equals(configMac)) {
            Log.i(TAG, "WiFi MAC set to NO_CHANGE, using system value.");
            return;
        }
        
        if (MODE_RANDOM.equalsIgnoreCase(configMac)) {
            sFakeMac = generateRandomMac();
            Log.i(TAG, "WiFi MAC set to RANDOM, generated: " + sFakeMac);
        } else {
            // Custom value provided
            sFakeMac = configMac.toUpperCase();
            Log.i(TAG, "WiFi MAC set to CUSTOM: " + sFakeMac);
        }
        
        Log.i(TAG, "Installing WiFi MAC hook → " + sFakeMac);

        // WifiInfo.getMacAddress()
        hook(WifiInfo.class, "getMacAddress", new Class<?>[0]);

        // WifiManager.getConnectionInfo() – tweak the returned WifiInfo
        try {
            Method m = WifiManager.class.getDeclaredMethod("getConnectionInfo");
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    WifiInfo info = (WifiInfo) cf.getResult();
                    if (info != null) {
                        Log.d(TAG, "Spoofing MAC inside WifiInfo object");
                        // reflect into the private mMacAddress field
                        try {
                            Field f = WifiInfo.class.getDeclaredField("mMacAddress");
                            f.setAccessible(true);
                            f.set(info, sFakeMac);
                        } catch (Throwable t) {
                            Log.w(TAG, "Failed to set mMacAddress field", t);
                        }
                    }
                }
            });
            Log.i(TAG, "✓ hooked WifiManager.getConnectionInfo()");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook getConnectionInfo()", t);
        }
        
        // Hook NetworkInterface.getHardwareAddress() for more comprehensive spoofing
        hookNetworkInterface();
    }
    
    private void hookNetworkInterface() {
        try {
            Class<?> niClass = Class.forName("java.net.NetworkInterface");
            Method m = niClass.getDeclaredMethod("getHardwareAddress");
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    byte[] orig = (byte[]) cf.getResult();
                    if (orig != null && orig.length == 6) {
                        // Convert fake MAC string to bytes
                        byte[] fakeMacBytes = macStringToBytes(sFakeMac);
                        if (fakeMacBytes != null) {
                            cf.setResult(fakeMacBytes);
                            Log.d(TAG, "NetworkInterface MAC spoofed");
                        }
                    }
                }
            });
            Log.i(TAG, "✓ hooked NetworkInterface.getHardwareAddress()");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook NetworkInterface.getHardwareAddress()", t);
        }
    }

    private void hook(Class<?> cls, String name, Class<?>[] params) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {}
                @Override public void afterCall(Pine.CallFrame cf) {
                    Object orig = cf.getResult();
                    cf.setResult(sFakeMac);
                    Log.d(TAG, "Wi-Fi MAC spoofed " + orig + " → " + sFakeMac);
                }
            });
            Log.i(TAG, "✓ hooked " + cls.getSimpleName() + '.' + name);
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook " + cls.getSimpleName() + "." + name, t);
        }
    }
    
    /**
     * Convert MAC address string to byte array
     */
    private static byte[] macStringToBytes(String mac) {
        if (mac == null) return null;
        String[] parts = mac.split(":");
        if (parts.length != 6) return null;
        
        byte[] bytes = new byte[6];
        try {
            for (int i = 0; i < 6; i++) {
                bytes[i] = (byte) Integer.parseInt(parts[i], 16);
            }
            return bytes;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Set a custom WiFi MAC at runtime
     */
    public static void setMac(String mac) {
        sFakeMac = mac != null ? mac.toUpperCase() : DEFAULT_FAKE_MAC;
        Log.i(TAG, "WiFi MAC updated to: " + sFakeMac);
    }
    
    /**
     * Get the current fake WiFi MAC
     */
    public static String getMac() {
        return sFakeMac;
    }
    
    /**
     * Generate a random locally-administered MAC address
     */
    public static String generateRandomMac() {
        Random random = new Random();
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
}
