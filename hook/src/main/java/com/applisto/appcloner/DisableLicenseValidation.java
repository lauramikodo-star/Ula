package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import java.lang.reflect.Method;
import java.security.PublicKey;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class DisableLicenseValidation {
    private static final String TAG = "DisableLicenseValidation";

    public static void install(Context context) {
        Log.i(TAG, "Installing DisableLicenseValidation hook...");

        installGoogleLicenseHook();
        installPairipLicenseHook();
    }

    private static void installGoogleLicenseHook() {
        try {
            // Find com.google.android.vending.licensing.LicenseValidator.verify(...)
            Method verifyMethod = ReflectionUtil.findMethodByParameterTypes(
                    "com.google.android.vending.licensing.LicenseValidator",
                    "verify",
                    new Class<?>[]{PublicKey.class, int.class, String.class, String.class}
            );

            if (verifyMethod != null) {
                Hooking.hookMethod(verifyMethod, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame callFrame) {
                        Log.i(TAG, "Intercepted LicenseValidator.verify");

                        Object thisObject = callFrame.thisObject;
                        // The LicenseValidator has a field 'mCallback' (ILicenseResultListener or similar)
                        Object callback = ReflectionUtil.getFieldValue(thisObject, "mCallback");

                        if (callback != null) {
                            Log.i(TAG, "Found callback, invoking allow()");
                            // Invoke 'allow(int reason)' on the callback. usually reason is access policy.
                            ReflectionUtil.invokeMethod(callback, "allow", 0);
                        } else {
                            Log.w(TAG, "Callback field 'mCallback' not found or null");
                        }

                        callFrame.setResult(null);
                    }
                });
                Log.i(TAG, "Hooked LicenseValidator.verify");
            } else {
                Log.w(TAG, "LicenseValidator.verify method not found. App might not use LVL or uses obfuscated names.");
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to install Google DisableLicenseValidation hook", e);
        }
    }

    private static void installPairipLicenseHook() {
        try {
            Log.i(TAG, "Attempting to hook com.pairip.licensecheck.LicenseClient...");
            Class<?> clientClass = Class.forName("com.pairip.licensecheck.LicenseClient");

            // We search for processResponse method by name as we don't know the exact signature
            Method processResponseMethod = null;
            for (Method method : clientClass.getDeclaredMethods()) {
                if (method.getName().equals("processResponse")) {
                    processResponseMethod = method;
                    break;
                }
            }

            if (processResponseMethod != null) {
                Hooking.hookMethod(processResponseMethod, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame callFrame) {
                        Log.i(TAG, "Intercepted LicenseClient.processResponse - suppressing exception");
                        // Suppress the method body which throws LicenseCheckException
                        // Assuming the method is void or its result is ignored on success path logic check
                        callFrame.setResult(null);
                    }
                });
                Log.i(TAG, "Hooked LicenseClient.processResponse");
            } else {
                Log.w(TAG, "LicenseClient.processResponse method not found.");
            }

        } catch (ClassNotFoundException e) {
            Log.i(TAG, "com.pairip.licensecheck.LicenseClient not found (not used by this app).");
        } catch (Exception e) {
            Log.w(TAG, "Failed to install Pairip DisableLicenseValidation hook", e);
        }
    }
}
