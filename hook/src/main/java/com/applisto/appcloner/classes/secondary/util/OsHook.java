package com.applisto.appcloner.classes.secondary.util;

import android.util.Log;
import libcore.io.ForwardingOs;
import libcore.io.Os;

public class OsHook extends ForwardingOs {
    private static final String TAG = OsHook.class.getSimpleName();

    static {
        // Static initializer block
    }

    public OsHook() {
        super(getOriginalOs());
        install();
        Log.i(TAG, "OsHook; this: " + this);
    }

    private static Os getOriginalOs() {
        try {
            Class<?> libcoreClass = Class.forName("libcore.io.Libcore");
            return (Os) ReflectionUtil.getStaticFieldValue(libcoreClass, "os");
        } catch (Exception e) {
            Log.w(TAG, e);
            return null; // This will likely crash if null, but better than linking to stub
        }
    }

    private void install() {
        try {
            Class<?> libcoreClass = Class.forName("libcore.io.Libcore");
            ReflectionUtil.setStaticFieldValue(libcoreClass, "os", this);
        } catch (Exception e) {
            Log.w(TAG, e);
        }
    }
}
