package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class PackageNameWorkaround {
    private static final String TAG = "PackageNameWorkaround";

    public static void install(Context context) {
        Log.i(TAG, "Installing PackageNameWorkaround hook...");

        final String originalPackageName = ClonerSettings.get(context).originalPackageName();
        if (originalPackageName == null || originalPackageName.isEmpty()) {
            Log.w(TAG, "Original package name not found, skipping PackageNameWorkaround");
            return;
        }

        try {
            // Hook ContextWrapper.getPackageName()
            Hooking.hookMethod(
                    android.content.ContextWrapper.class,
                    "getPackageName",
                    new MethodHook() {
                        @Override
                        public void beforeCall(Pine.CallFrame callFrame) {
                            // No-op
                        }

                        @Override
                        public void afterCall(Pine.CallFrame callFrame) {
                            String currentResult = (String) callFrame.getResult();
                            // Only replace if it matches the current (cloned) package name
                            // to avoid interfering with other contexts if possible, though
                            // usually getPackageName() returns the app's package.
                            if (currentResult != null && !currentResult.equals(originalPackageName)) {
                                Log.v(TAG, "Spoofing getPackageName: " + currentResult + " -> " + originalPackageName);
                                callFrame.setResult(originalPackageName);
                            }
                        }
                    }
            );
            Log.i(TAG, "Hooked ContextWrapper.getPackageName()");

        } catch (Exception e) {
            Log.e(TAG, "Failed to install PackageNameWorkaround hook", e);
        }
    }
}
