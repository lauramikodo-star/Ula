package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

import com.applisto.appcloner.hooking.Hooking;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class CustomBuildProps {
    private static final String TAG = "CustomBuildProps";
    static final Map<String, String> sCustomBuildProps = new LinkedHashMap<>();

    public static void install(Context context, Map<String, String> map) {
        if (map == null || map.isEmpty()) return;

        sCustomBuildProps.putAll(map);

        for (Map.Entry<String, String> entry : map.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Override SystemProperties
            SystemPropertiesHook.overrideSystemProperty(key, value);

            // Override System.getProperty
            if (value != null) {
                System.setProperty(key, value);
            } else {
                System.clearProperty(key);
            }
        }

        installSystemHooks();
        ProcessOutputHook.install();
    }

    private static void installSystemHooks() {
        try {
            // Hook System.getProperty(String)
            Hooking.hookMethod(System.class, "getProperty", new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String key = (String) callFrame.args[0];
                    if (sCustomBuildProps.containsKey(key)) {
                        callFrame.setResult(sCustomBuildProps.get(key));
                    }
                }
            }, String.class);

            // Hook System.getProperty(String, String)
            Hooking.hookMethod(System.class, "getProperty", new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    String key = (String) callFrame.args[0];
                    if (sCustomBuildProps.containsKey(key)) {
                        callFrame.setResult(sCustomBuildProps.get(key));
                    }
                }
            }, String.class, String.class);

            // Hook System.getProperties()
            Hooking.hookMethod(System.class, "getProperties", new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Properties props = (Properties) callFrame.getResult();
                    if (props != null) {
                        for (Map.Entry<String, String> entry : sCustomBuildProps.entrySet()) {
                            props.setProperty(entry.getKey(), entry.getValue());
                        }
                    }
                }
            });

        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook System.getProperty", t);
        }
    }
}
