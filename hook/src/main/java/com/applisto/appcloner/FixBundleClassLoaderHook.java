package com.applisto.appcloner;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class FixBundleClassLoaderHook {
    private static final String TAG = "FixBundleClassLoaderHook";

    public void init(Context context) {
        Log.i(TAG, "Installing FixBundleClassLoaderHook");
        try {
            Method onCreate = Activity.class.getDeclaredMethod("onCreate", Bundle.class);
            Hooking.pineHook(onCreate, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) {
                    try {
                        Activity activity = (Activity) frame.thisObject;
                        ClassLoader classLoader = activity.getClassLoader();

                        // Fix Intent extras
                        Intent intent = activity.getIntent();
                        if (intent != null) {
                            intent.setExtrasClassLoader(classLoader);
                        }

                        // Fix savedInstanceState
                        if (frame.args.length > 0 && frame.args[0] != null) {
                            Bundle savedInstanceState = (Bundle) frame.args[0];
                            savedInstanceState.setClassLoader(classLoader);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error in FixBundleClassLoaderHook", t);
                    }
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook Activity.onCreate", t);
        }
    }
}
