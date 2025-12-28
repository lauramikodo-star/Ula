package com.applisto.appcloner;

import android.content.SharedPreferences;
import android.util.Log;

import java.io.File;
import java.lang.reflect.Method;

import com.applisto.appcloner.hooking.Hooking;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class SharedPreferencesHook {
    private static final String TAG = "SharedPreferencesHook";

    public void install(Object ignored) {
        Log.i(TAG, "Installing SharedPreferencesHook...");
        installHooks();
    }

    private void installHooks() {
        try {
            Class<?> contextImplClass = Class.forName("android.app.ContextImpl");

            // Hook getSharedPreferences(String, int)
            try {
                Method getSharedPreferences = contextImplClass.getDeclaredMethod("getSharedPreferences", String.class, int.class);
                Hooking.pineHook(getSharedPreferences, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame callFrame) {
                        try {
                            if (callFrame.getResult() != null) {
                                String name = (String) callFrame.args[0];
                                SharedPreferences sp = (SharedPreferences) callFrame.getResult();
                                // Wrap the result in our monitoring proxy
                                callFrame.setResult(PreferencesMonitor.wrap(name, sp));
                            }
                        } catch (Throwable t) {
                            Log.w(TAG, "Error wrapping SharedPreferences", t);
                        }
                    }
                });
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook getSharedPreferences(String, int)", t);
            }

            // Hook getSharedPreferences(File, int)
            try {
                Method getSharedPreferencesFile = contextImplClass.getDeclaredMethod("getSharedPreferences", File.class, int.class);
                Hooking.pineHook(getSharedPreferencesFile, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame callFrame) {
                        try {
                            if (callFrame.getResult() != null) {
                                File file = (File) callFrame.args[0];
                                String name = file != null ? file.getName() : "null";
                                SharedPreferences sp = (SharedPreferences) callFrame.getResult();
                                // Wrap the result in our monitoring proxy
                                callFrame.setResult(PreferencesMonitor.wrap(name, sp));
                            }
                        } catch (Throwable t) {
                            // Method might not exist on older Android versions or signature differs, ignore
                        }
                    }
                });
            } catch (Throwable t) {
                // Ignore missing method
            }

        } catch (ClassNotFoundException e) {
            Log.e(TAG, "ContextImpl class not found", e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install SharedPreferencesHook", t);
        }
    }
}
