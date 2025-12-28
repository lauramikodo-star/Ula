package com.applisto.appcloner;

import android.util.Log;
import java.util.HashMap;
import java.util.Map;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class SystemPropertiesHook {
    private static final String TAG = "SystemPropertiesHook";
    private static final Map<String, String> sOverrides = new HashMap<>();

    public static void overrideSystemProperty(String key, String value) {
        if (key == null) return;
        synchronized (sOverrides) {
            sOverrides.put(key, value);
        }
        ensureInstalled();
    }

    private static boolean sInstalled = false;

    private static synchronized void ensureInstalled() {
        if (sInstalled) return;

        try {
            Class<?> sysProps = Class.forName("android.os.SystemProperties");

            // Hook get(String)
            Hooking.hookMethod(sysProps, "get", new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String key = (String) callFrame.args[0];
                    if (sOverrides.containsKey(key)) {
                        String val = sOverrides.get(key);
                        // Log.d(TAG, "Overriding SystemProperties.get(" + key + ") -> " + val);
                        callFrame.setResult(val);
                    }
                }
            }, String.class);

            // Hook get(String, String)
            Hooking.hookMethod(sysProps, "get", new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String key = (String) callFrame.args[0];
                    if (sOverrides.containsKey(key)) {
                        String val = sOverrides.get(key);
                        // Log.d(TAG, "Overriding SystemProperties.get(" + key + ", def) -> " + val);
                        callFrame.setResult(val);
                    }
                }
            }, String.class, String.class);

            sInstalled = true;
            Log.i(TAG, "SystemProperties hooks installed.");

        } catch (ClassNotFoundException e) {
            Log.e(TAG, "SystemProperties class not found", e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook SystemProperties", t);
        }
    }
}
