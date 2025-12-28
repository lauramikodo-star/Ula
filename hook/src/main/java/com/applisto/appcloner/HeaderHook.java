package com.applisto.appcloner;

import android.util.Log;

import java.lang.reflect.Method;
import java.net.URLConnection;

import com.applisto.appcloner.hooking.Hooking;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class HeaderHook {
    private static final String TAG = "HeaderHook";

    public void install(Object ignored) {
        Log.i(TAG, "Installing HeaderHook...");
        installHooks();
    }

    private void installHooks() {
        try {
            Method addRequestProperty = URLConnection.class.getDeclaredMethod("addRequestProperty", String.class, String.class);
            Hooking.pineHook(addRequestProperty, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    try {
                        String key = (String) callFrame.args[0];
                        String value = (String) callFrame.args[1];
                        HeaderMonitor.addEntry(key, value);
                    } catch (Throwable t) {
                        Log.w(TAG, "Error logging header (add)", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook addRequestProperty", t);
        }

        try {
            Method setRequestProperty = URLConnection.class.getDeclaredMethod("setRequestProperty", String.class, String.class);
            Hooking.pineHook(setRequestProperty, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    try {
                        String key = (String) callFrame.args[0];
                        String value = (String) callFrame.args[1];
                        HeaderMonitor.addEntry(key, value);
                    } catch (Throwable t) {
                        Log.w(TAG, "Error logging header (set)", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook setRequestProperty", t);
        }
    }
}
