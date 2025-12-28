package com.applisto.appcloner;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicReference;

import com.applisto.appcloner.hooking.Hooking;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class UserAgentWorkaround {

    private static final String TAG = "UserAgentWorkaround";

    // ThreadLocal to prevent recursion if needed, though less likely with Pine than Xposed
    public static final ThreadLocal<Boolean> sOnUriStringDisabled = new ThreadLocal<Boolean>() {
        @Override
        protected Boolean initialValue() {
            return false;
        }
    };

    public static void install(Context context, boolean uriSchemeWorkaround) {
        Log.i(TAG, "install; uriSchemeWorkaround: " + uriSchemeWorkaround);

        // Initialize base hooks
        HeaderHook headerHook = new HeaderHook();
        headerHook.install(null);
        UrlHook.install();

        // Hook for Header replacements
        try {
            // Re-hook or augment existing HeaderHook logic specifically for UserAgentWorkaround?
            // The original code used HeaderHook.register() which implies HeaderHook had a registry list.
            // Our new HeaderHook doesn't support registry, it just logs to HeaderMonitor.
            // We need to implement the replacement logic directly here via Pine.

            MethodHook replaceHeadersHook = new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    try {
                        String key = (String) callFrame.args[0];
                        String value = (String) callFrame.args[1];

                        String newKey = replaceValue(context, key);
                        if (!TextUtils.equals(key, newKey)) {
                            callFrame.args[0] = newKey;
                            Log.i(TAG, "onHeader; name: " + key + " -> " + newKey);
                        }

                        String newValue = replaceValue(context, value);
                        if (!TextUtils.equals(value, newValue)) {
                            callFrame.args[1] = newValue;
                            Log.i(TAG, "onHeader; value: " + value + " -> " + newValue);
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "Error replacing headers", t);
                    }
                }
            };

            Hooking.pineHook(URLConnection.class.getDeclaredMethod("addRequestProperty", String.class, String.class), replaceHeadersHook);
            Hooking.pineHook(URLConnection.class.getDeclaredMethod("setRequestProperty", String.class, String.class), replaceHeadersHook);

        } catch (Throwable t) {
             Log.w(TAG, "Failed to install header replacement hooks", t);
        }


        // Register UrlHook implementation
        new UrlHook() {
            @Override
            protected void onUriString(AtomicReference<String> uriStringRef) {
                if (sOnUriStringDisabled.get()) return;

                String uriString = uriStringRef.get();
                if (!TextUtils.isEmpty(uriString)) {
                    if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                        String newUriString = replaceValue(context, uriString);
                        if (!TextUtils.isEmpty(newUriString) && !TextUtils.equals(uriString, newUriString)) {
                            Log.i(TAG, "onUriString; uriString: " + uriString + ", newUriString: " + newUriString);
                            uriStringRef.set(newUriString);
                        }
                    }
                }
            }

            @Override
            protected void onUrlParts(String url, AtomicReference<String> protocol, AtomicReference<String> host,
                                      AtomicReference<Integer> port, AtomicReference<String> authority,
                                      AtomicReference<String> userInfo, AtomicReference<String> path,
                                      AtomicReference<String> query, AtomicReference<String> ref) {
                // Implement if granular URL part replacement is needed
            }
        }.register();

        // URI Scheme Workaround
        if (uriSchemeWorkaround) {
            new UrlHook() {
                @Override
                protected void onUriString(AtomicReference<String> uriStringRef) {
                    String uriString = uriStringRef.get();
                    String packageName = context.getPackageName();

                    if (uriString.startsWith(packageName + "://") && uriString.contains("/auth/")) {
                        Log.i(TAG, "onUriString; uriString: " + uriString);

                        String originalPackageName = ClonerSettings.get(context).originalPackageName();
                        if (originalPackageName != null) {
                            String newUriString = uriString.replace(packageName + "://", originalPackageName + "://");
                            Log.i(TAG, "onUriString; newUriString: " + newUriString);
                            uriStringRef.set(newUriString);
                        }
                    }
                }

                @Override
                protected void onUrlParts(String url, AtomicReference<String> protocol, AtomicReference<String> host,
                                          AtomicReference<Integer> port, AtomicReference<String> authority,
                                          AtomicReference<String> userInfo, AtomicReference<String> path,
                                          AtomicReference<String> query, AtomicReference<String> ref) {
                     String p = protocol.get();
                     String packageName = context.getPackageName();
                     if (packageName.equals(p)) {
                         Log.i(TAG, "onUrlParts; protocol: " + p);
                         String originalPackageName = ClonerSettings.get(context).originalPackageName();
                         if (originalPackageName != null) {
                             Log.i(TAG, "onUrlParts; newProtocol: " + originalPackageName);
                             protocol.set(originalPackageName);
                         }
                     }
                }
            }.register();
        }
    }

    public static String replaceValue(Context context, String value) {
        if (TextUtils.isEmpty(value)) return value;

        // Skip if already App Cloner User-Agent (custom one)
        if (value.startsWith("App Cloner Clone/")) return value;

        String packageName = context.getPackageName();
        String originalPackageName = ClonerSettings.get(context).originalPackageName();

        if (originalPackageName != null && !originalPackageName.equals(packageName)) {
            value = value.replace(packageName, originalPackageName);
        }

        return value;
    }
}
