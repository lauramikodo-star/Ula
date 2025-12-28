package com.applisto.appcloner;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * HideDnsServersHook - Hides or spoofs DNS server information.
 * 
 * Based on secondary.jar HideDnsServers behavior:
 * - Hooks LinkProperties.getDnsServers() to return empty or spoofed list
 * - Hooks system property reads for net.dns* properties
 * - Prevents fingerprinting via DNS server addresses
 */
public class HideDnsServersHook {
    private static final String TAG = "HideDnsServers";
    
    private static boolean sInstalled = false;
    private static boolean sHideCompletely = true; // If true, return empty list
    private static List<InetAddress> sSpoofedDnsServers = new ArrayList<>();
    
    /**
     * Install the DNS servers hiding hook.
     * By default, hides DNS servers completely (returns empty list).
     */
    public static void install(Context context) {
        install(context, true, null);
    }
    
    /**
     * Install the DNS servers hiding hook with options.
     * 
     * @param context Application context
     * @param hideCompletely If true, return empty DNS list; if false, use spoofed list
     * @param spoofedDnsServers List of spoofed DNS addresses (e.g., ["8.8.8.8", "8.8.4.4"])
     */
    public static void install(Context context, boolean hideCompletely, List<String> spoofedDnsServers) {
        if (sInstalled || context == null) return;
        
        sHideCompletely = hideCompletely;
        
        // Parse spoofed DNS servers if provided
        if (spoofedDnsServers != null && !spoofedDnsServers.isEmpty()) {
            for (String dns : spoofedDnsServers) {
                try {
                    InetAddress addr = InetAddress.getByName(dns);
                    sSpoofedDnsServers.add(addr);
                } catch (Exception e) {
                    Log.w(TAG, "Invalid DNS address: " + dns);
                }
            }
        }
        
        try {
            hookLinkPropertiesGetDnsServers();
            hookSystemProperties();
            
            sInstalled = true;
            Log.i(TAG, "HideDnsServersHook installed (hideCompletely=" + hideCompletely + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install HideDnsServersHook", t);
        }
    }
    
    /**
     * Hook LinkProperties.getDnsServers() method.
     */
    private static void hookLinkPropertiesGetDnsServers() {
        try {
            Method getDnsServers = LinkProperties.class.getDeclaredMethod("getDnsServers");
            Hooking.pineHook(getDnsServers, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    if (sHideCompletely) {
                        frame.setResult(Collections.emptyList());
                        Log.d(TAG, "LinkProperties.getDnsServers() -> empty list");
                    } else if (!sSpoofedDnsServers.isEmpty()) {
                        frame.setResult(new ArrayList<>(sSpoofedDnsServers));
                        Log.d(TAG, "LinkProperties.getDnsServers() -> spoofed list");
                    }
                }
            });
            Log.d(TAG, "LinkProperties.getDnsServers hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook LinkProperties.getDnsServers", t);
        }
    }
    
    /**
     * Hook System.getProperty() for net.dns* properties.
     */
    private static void hookSystemProperties() {
        try {
            // Hook System.getProperty(String)
            Method getProperty = System.class.getDeclaredMethod("getProperty", String.class);
            Hooking.pineHook(getProperty, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String key = (String) frame.args[0];
                    if (key != null && key.startsWith("net.dns")) {
                        if (sHideCompletely) {
                            frame.setResult(null);
                            Log.d(TAG, "System.getProperty(" + key + ") -> null");
                        } else if (!sSpoofedDnsServers.isEmpty()) {
                            // Return spoofed DNS based on the property number
                            int index = extractDnsIndex(key);
                            if (index >= 0 && index < sSpoofedDnsServers.size()) {
                                String dns = sSpoofedDnsServers.get(index).getHostAddress();
                                frame.setResult(dns);
                                Log.d(TAG, "System.getProperty(" + key + ") -> " + dns);
                            } else {
                                frame.setResult(null);
                            }
                        }
                    }
                }
            });
            
            // Hook System.getProperty(String, String) with default
            Method getPropertyWithDefault = System.class.getDeclaredMethod("getProperty", String.class, String.class);
            Hooking.pineHook(getPropertyWithDefault, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String key = (String) frame.args[0];
                    if (key != null && key.startsWith("net.dns")) {
                        if (sHideCompletely) {
                            frame.setResult(frame.args[1]); // Return default
                            Log.d(TAG, "System.getProperty(" + key + ", default) -> default");
                        } else if (!sSpoofedDnsServers.isEmpty()) {
                            int index = extractDnsIndex(key);
                            if (index >= 0 && index < sSpoofedDnsServers.size()) {
                                String dns = sSpoofedDnsServers.get(index).getHostAddress();
                                frame.setResult(dns);
                            }
                        }
                    }
                }
            });
            
            Log.d(TAG, "System property hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook System.getProperty", t);
        }
        
        // Also try to hook android.os.SystemProperties if accessible
        try {
            Class<?> systemPropsClass = Class.forName("android.os.SystemProperties");
            
            Method get = systemPropsClass.getDeclaredMethod("get", String.class);
            Hooking.pineHook(get, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String key = (String) frame.args[0];
                    if (key != null && key.startsWith("net.dns")) {
                        if (sHideCompletely) {
                            frame.setResult("");
                            Log.d(TAG, "SystemProperties.get(" + key + ") -> empty");
                        }
                    }
                }
            });
            
            Method getWithDefault = systemPropsClass.getDeclaredMethod("get", String.class, String.class);
            Hooking.pineHook(getWithDefault, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String key = (String) frame.args[0];
                    if (key != null && key.startsWith("net.dns")) {
                        if (sHideCompletely) {
                            frame.setResult(frame.args[1]); // Return default
                        }
                    }
                }
            });
            
            Log.d(TAG, "SystemProperties hooks installed");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "SystemProperties class not accessible");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook SystemProperties", t);
        }
    }
    
    /**
     * Extract DNS server index from property name (e.g., "net.dns1" -> 0).
     */
    private static int extractDnsIndex(String key) {
        try {
            // net.dns1, net.dns2, etc.
            String numStr = key.replace("net.dns", "").trim();
            return Integer.parseInt(numStr) - 1; // Convert 1-based to 0-based
        } catch (Exception e) {
            return -1;
        }
    }
    
    /**
     * Set whether to hide DNS completely or use spoofed values.
     */
    public static void setHideCompletely(boolean hide) {
        sHideCompletely = hide;
    }
    
    /**
     * Add a spoofed DNS server.
     */
    public static void addSpoofedDns(String dns) {
        try {
            InetAddress addr = InetAddress.getByName(dns);
            sSpoofedDnsServers.add(addr);
        } catch (Exception e) {
            Log.w(TAG, "Invalid DNS address: " + dns);
        }
    }
    
    /**
     * Check if the hook is installed.
     */
    public static boolean isInstalled() {
        return sInstalled;
    }
}
