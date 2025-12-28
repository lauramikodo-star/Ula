package com.applisto.appcloner;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * ChangeSystemUserAgentHook - Spoofs the system-level user agent string.
 * 
 * Based on secondary.jar ChangeSystemUserAgent behavior:
 * - Hooks System.getProperty() to intercept "http.agent" queries
 * - Hooks Build fields related to user agent
 * - Provides a custom user agent string for all HTTP requests
 * 
 * This differs from UserAgentHook which only hooks WebView and HTTP connections.
 * This hook changes the system-wide default user agent.
 */
public class ChangeSystemUserAgentHook {
    private static final String TAG = "ChangeSystemUA";
    private static final String HTTP_AGENT_PROPERTY = "http.agent";
    
    private static boolean sInstalled = false;
    private static String sCustomUserAgent;
    
    /**
     * Install the system user agent hook.
     * 
     * @param context Application context
     * @param userAgent Custom user agent string to use
     */
    public static void install(Context context, String userAgent) {
        if (sInstalled || context == null || userAgent == null || userAgent.isEmpty()) {
            return;
        }
        
        sCustomUserAgent = userAgent;
        
        try {
            hookSystemGetProperty();
            hookBuildFields();
            sInstalled = true;
            Log.i(TAG, "ChangeSystemUserAgentHook installed. UA: " + 
                  (userAgent.length() > 50 ? userAgent.substring(0, 50) + "..." : userAgent));
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install ChangeSystemUserAgentHook", t);
        }
    }
    
    /**
     * Hook System.getProperty() to intercept http.agent queries.
     */
    private static void hookSystemGetProperty() {
        try {
            // Hook System.getProperty(String)
            Method getProperty = System.class.getDeclaredMethod("getProperty", String.class);
            Hooking.pineHook(getProperty, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String key = (String) frame.args[0];
                    if (HTTP_AGENT_PROPERTY.equals(key)) {
                        frame.setResult(sCustomUserAgent);
                        Log.d(TAG, "Intercepted System.getProperty(http.agent)");
                    }
                }
            });
            
            // Hook System.getProperty(String, String) with default value
            Method getPropertyWithDefault = System.class.getDeclaredMethod("getProperty", String.class, String.class);
            Hooking.pineHook(getPropertyWithDefault, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    String key = (String) frame.args[0];
                    if (HTTP_AGENT_PROPERTY.equals(key)) {
                        frame.setResult(sCustomUserAgent);
                        Log.d(TAG, "Intercepted System.getProperty(http.agent, default)");
                    }
                }
            });
            
            Log.d(TAG, "System.getProperty hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook System.getProperty", t);
        }
    }
    
    /**
     * Modify Build fields that contribute to user agent generation.
     */
    private static void hookBuildFields() {
        // Note: Build fields are final and can't be directly modified in all cases.
        // Instead, we hook methods that read these fields for UA construction.
        
        try {
            // Try to hook any WebSettings class methods that construct user agent
            Class<?> webSettingsClass = Class.forName("android.webkit.WebSettings");
            
            // Hook getDefaultUserAgent if available
            try {
                Method getDefaultUA = webSettingsClass.getDeclaredMethod("getDefaultUserAgent", Context.class);
                Hooking.pineHook(getDefaultUA, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame frame) throws Throwable {
                        frame.setResult(sCustomUserAgent);
                        Log.d(TAG, "Intercepted WebSettings.getDefaultUserAgent()");
                    }
                });
            } catch (NoSuchMethodException ignored) {}
            
            Log.d(TAG, "Build field hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook Build fields", t);
        }
        
        // Also try to set the system property directly
        try {
            System.setProperty(HTTP_AGENT_PROPERTY, sCustomUserAgent);
            Log.d(TAG, "Set http.agent system property directly");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to set http.agent property", t);
        }
    }
    
    /**
     * Get the current custom user agent.
     */
    public static String getCustomUserAgent() {
        return sCustomUserAgent;
    }
    
    /**
     * Check if the hook is installed.
     */
    public static boolean isInstalled() {
        return sInstalled;
    }
}
