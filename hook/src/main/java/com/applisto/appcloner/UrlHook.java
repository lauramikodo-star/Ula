package com.applisto.appcloner;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public abstract class UrlHook {

    private static final String TAG = "UrlHook";
    private static final List<UrlHook> sHooks = new CopyOnWriteArrayList<>();
    private static boolean sInstalled;

    protected abstract void onUriString(AtomicReference<String> uriString);

    protected abstract void onUrlParts(String url, AtomicReference<String> protocol, AtomicReference<String> host,
                                       AtomicReference<Integer> port, AtomicReference<String> authority,
                                       AtomicReference<String> userInfo, AtomicReference<String> path,
                                       AtomicReference<String> query, AtomicReference<String> ref);

    public static synchronized void install() {
        Log.i(TAG, "install; ");

        if (!sInstalled) {
            try {
                // Hook Uri.parse(String)
                Hooking.pineHook(Uri.class.getDeclaredMethod("parse", String.class), new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        if (frame.args[0] instanceof String) {
                            AtomicReference<String> uriRef = new AtomicReference<>((String) frame.args[0]);
                            handleUriString(uriRef);
                            frame.args[0] = uriRef.get();
                        }
                    }
                });

                // Hook Uri.Builder.build() is tricky because it builds from parts.
                // Smali hooks build() but does it just inspect the result?
                // Smali for UrlHook$2 hooks Uri.Builder.build().
                // But typically modifications happen at parse time or when setting parts.
                // Let's stick to parsing for now as it's the most common entry point.

                // Hook URL constructors
                // URL(String spec)
                Hooking.pineHook(URL.class.getDeclaredConstructor(String.class), new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        if (frame.args[0] instanceof String) {
                            AtomicReference<String> uriRef = new AtomicReference<>((String) frame.args[0]);
                            handleUriString(uriRef);
                            frame.args[0] = uriRef.get();
                        }
                    }
                });

                // URL(String protocol, String host, int port, String file)
                Hooking.pineHook(URL.class.getDeclaredConstructor(String.class, String.class, int.class, String.class), new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        // This is complex to map to onUrlParts directly without a full URL string context
                        // But we can try to reconstruct or pass parts.
                        // For simplicity, let's focus on the string based constructors which are most common.
                    }
                });

                // URL(String protocol, String host, String file)
                Hooking.pineHook(URL.class.getDeclaredConstructor(String.class, String.class, String.class), new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame frame) throws Throwable {
                        // Same here
                    }
                });

            } catch (Throwable t) {
                Log.w(TAG, t);
            }

            sInstalled = true;
        }
    }

    public void register() {
        synchronized (UrlHook.class) {
            sHooks.add(this);
        }
    }

    private static void handleUriString(AtomicReference<String> uriStringRef) {
        for (UrlHook hook : sHooks) {
            try {
                hook.onUriString(uriStringRef);
            } catch (Throwable t) {
                Log.w(TAG, t);
            }
        }
    }

    // Helper to invoke onUrlParts if needed later
    private static void handleUrlParts(String url, AtomicReference<String> protocol, AtomicReference<String> host,
                                       AtomicReference<Integer> port, AtomicReference<String> authority,
                                       AtomicReference<String> userInfo, AtomicReference<String> path,
                                       AtomicReference<String> query, AtomicReference<String> ref) {
        for (UrlHook hook : sHooks) {
            try {
                hook.onUrlParts(url, protocol, host, port, authority, userInfo, path, query, ref);
            } catch (Throwable t) {
                Log.w(TAG, t);
            }
        }
    }
}
