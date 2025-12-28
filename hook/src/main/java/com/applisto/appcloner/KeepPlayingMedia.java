package com.applisto.appcloner;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public final class KeepPlayingMedia {
    private static final String TAG = "KeepPlayingMedia";

    private KeepPlayingMedia() {}

    public static void install(Context context, boolean compatibilityMode) {
        Log.i(TAG, "install; compatibilityMode: " + compatibilityMode);

        // Make sure your hooking engine is initialized
        Hooking.initHooking(context);

        // 1) Audio focus always granted
        MethodHook focusGranted = new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame cf) {
                Log.i(TAG, "beforeCall; AudioFocus -> GRANTED");
                cf.setResult(1); // AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        };

        tryHook(AudioManager.class, "requestAudioFocus",
                focusGranted,
                AudioManager.OnAudioFocusChangeListener.class, int.class, int.class);

        try {
            tryHook(AudioManager.class, "requestAudioFocus",
                    focusGranted,
                    AudioManager.OnAudioFocusChangeListener.class,
                    Class.forName("android.media.AudioAttributes"),
                    int.class, int.class);
        } catch (ClassNotFoundException e) {
            Log.w(TAG, "AudioAttributes not found");
        }

        // requestAudioFocus(listener, attrs, int, int, AudioPolicy)
        try {
            Class<?> audioPolicy = Class.forName("android.media.audiopolicy.AudioPolicy");
            tryHook(AudioManager.class, "requestAudioFocus",
                    focusGranted,
                    AudioManager.OnAudioFocusChangeListener.class,
                    Class.forName("android.media.AudioAttributes"),
                    int.class, int.class, audioPolicy);
        } catch (Throwable ignored) {}

        tryHook(AudioManager.class, "abandonAudioFocus",
                focusGranted,
                AudioManager.OnAudioFocusChangeListener.class);

        if (Build.VERSION.SDK_INT >= 26) {
            try {
                tryHook(AudioManager.class, "requestAudioFocus",
                        focusGranted,
                        Class.forName("android.media.AudioFocusRequest"));

                try {
                    Class<?> audioPolicy = Class.forName("android.media.audiopolicy.AudioPolicy");
                    tryHook(AudioManager.class, "requestAudioFocus",
                            focusGranted,
                            Class.forName("android.media.AudioFocusRequest"),
                            audioPolicy);
                } catch (Throwable ignored) {}

                tryHook(AudioManager.class, "abandonAudioFocusRequest",
                        focusGranted,
                        Class.forName("android.media.AudioFocusRequest"));
            } catch (ClassNotFoundException e) {
                Log.w(TAG, "AudioFocusRequest not found");
            }
        }

        // 2) Block “becoming not visible / not focused / not top resumed”
        try {
            Method updateVisibility = findActivityThreadUpdateVisibility();
            if (updateVisibility != null) {
                Hooking.pineHook(updateVisibility, new ClearThrowableMethodHook() {
                    @Override public void beforeCall(Pine.CallFrame cf) {
                        boolean show = (Boolean) cf.args[1];
                        Log.i(TAG, "beforeCall; ActivityThread.updateVisibility; show=" + show);
                        if (!show) cf.setResult(null); // skip hiding
                    }
                });
            }
        } catch (Throwable t) {
            Log.w(TAG, "updateVisibility hook failed", t);
        }

        tryHook(android.app.Activity.class, "performTopResumedActivityChanged",
                new ClearThrowableMethodHook() {
                    @Override public void beforeCall(Pine.CallFrame cf) {
                        boolean isTop = (Boolean) cf.args[0];
                        Log.i(TAG, "beforeCall; performTopResumedActivityChanged; isTop=" + isTop);
                        if (!isTop) cf.setResult(null);
                    }
                },
                boolean.class, String.class);

        tryHook(View.class, "dispatchWindowFocusChanged",
                new MethodHook() {
                    @Override public void beforeCall(Pine.CallFrame cf) {
                        boolean hasFocus = (Boolean) cf.args[0];
                        if (!hasFocus) cf.setResult(null); // skip focus lost
                    }
                },
                boolean.class);

        MethodHook visibilityHook = new MethodHook() {
            @Override public void beforeCall(Pine.CallFrame cf) {
                int visibility = (Integer) cf.args[0];
                if (visibility != View.VISIBLE) cf.setResult(null); // skip invisible/gone
            }
        };

        tryHook(View.class, "dispatchWindowVisibilityChanged", visibilityHook, int.class);
        tryHook(ViewGroup.class, "dispatchWindowVisibilityChanged", visibilityHook, int.class);

        // 3) Non-compatibilityMode: block detach + be tolerant on attach
        if (!compatibilityMode) {
            MethodHook emptyVoid = new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) { cf.setResult(null); }
            };
            MethodHook clearThrowableAfter = new ClearThrowableMethodHook() { };

            tryHook(View.class, "dispatchDetachedFromWindow", emptyVoid);
            tryHook(ViewGroup.class, "dispatchDetachedFromWindow", emptyVoid);

            // Some Android versions have overloads; grab first by name
            tryHookFirstByName(View.class, "dispatchAttachedToWindow", clearThrowableAfter);
            tryHookFirstByName(ViewGroup.class, "dispatchAttachedToWindow", clearThrowableAfter);
        }

        // 4) Force host visibility = VISIBLE
        try {
            Class<?> vri = Class.forName("android.view.ViewRootImpl");
            Method m = vri.getDeclaredMethod("getHostVisibility");
            m.setAccessible(true);
            Hooking.pineHook(m, new MethodHook() {
                @Override public void beforeCall(Pine.CallFrame cf) {
                    cf.setResult(0); // View.VISIBLE
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "ViewRootImpl.getHostVisibility hook failed", t);
        }
    }

    // -------- helpers --------

    private static void tryHook(Class<?> cls, String name, MethodHook hook, Class<?>... params) {
        try {
            Method m = cls.getDeclaredMethod(name, params);
            m.setAccessible(true);
            Hooking.pineHook(m, hook);
        } catch (Throwable t) {
            // Log.w(TAG, "Hook failed: " + cls.getName() + "." + name, t);
        }
    }

    private static void tryHookFirstByName(Class<?> cls, String name, MethodHook hook) {
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    Hooking.pineHook(m, hook);
                    return;
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "Hook failed (firstByName): " + cls.getName() + "." + name, t);
        }
    }

    // ActivityThread.updateVisibility signature differs; jar searches by param count + boolean at end.
    private static Method findActivityThreadUpdateVisibility() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            for (Method m : at.getDeclaredMethods()) {
                if (!m.getName().equals("updateVisibility")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[1] == boolean.class) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Like jar’s PineHelper.ClearThrowableMethodHook */
    private static class ClearThrowableMethodHook extends MethodHook {
        @Override public void afterCall(Pine.CallFrame cf) {
            try {
                cf.setThrowable(null);
            } catch (Throwable ignored) {}
        }
    }
}
