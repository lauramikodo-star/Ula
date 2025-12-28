package com.applisto.appcloner.classes;

import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.widget.Toast;

public class Utils {
    public static boolean isX86() {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (abi.contains("x86")) return true;
        }
        return false;
    }

    public static Application getApplication() {
        // Stub implementation, user needs to provide actual implementation
        return null;
    }

    public static int showNotification(CharSequence text, boolean isError) {
        // Stub
        return 0;
    }
}
