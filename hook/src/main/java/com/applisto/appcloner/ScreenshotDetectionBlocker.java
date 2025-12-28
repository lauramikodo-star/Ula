package com.applisto.appcloner;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * Screenshot bypass using Pine hooking framework directly
 */
public class ScreenshotDetectionBlocker {

    private static final String TAG = "PineScreenshotBypass";
    private static final int FLAG_SECURE = 8192;

    public static void install(Context context) {
        try {
            if (!ClonerSettings.get(context).raw().optBoolean("AllowScreenshots", false)) {
                return;
            }

            // Initialize Pine
            Pine.ensureInitialized();

            // Hook Window.setFlags
            hookWindowSetFlags();

            // Hook Window.addFlags
            hookWindowAddFlags();

            // Hook WindowManager$LayoutParams
            hookLayoutParams();

            // Hook SurfaceView.setSecure
            hookSurfaceViewSetSecure();

            // Hook Activity.setRecentsScreenshotEnabled (API 33+)
            if (Build.VERSION.SDK_INT >= 33) { // Build.VERSION_CODES.TIRAMISU
                hookSetRecentsScreenshotEnabled();
            }

            Log.i(TAG, "All Pine hooks installed successfully");

        } catch (Throwable t) {
            Log.e(TAG, "Failed to install Pine hooks", t);
        }
    }

    private static void hookSetRecentsScreenshotEnabled() {
        try {
            // public void setRecentsScreenshotEnabled(boolean enabled)
            Method method = Activity.class.getMethod("setRecentsScreenshotEnabled", boolean.class);
            Hooking.pineHook(method, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    // Force argument to true
                    callFrame.args[0] = true;
                    Log.d(TAG, "Activity.setRecentsScreenshotEnabled forced to true");
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook setRecentsScreenshotEnabled", e);
        }
    }

    private static void hookWindowSetFlags() throws Throwable {
        Hooking.pineHook(
            Window.class.getDeclaredMethod("setFlags", int.class, int.class),
            new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    int flags = (int) callFrame.args[0];
                    int mask = (int) callFrame.args[1];

                    // Remove FLAG_SECURE
                    flags &= ~FLAG_SECURE;
                    mask &= ~FLAG_SECURE;

                    callFrame.args[0] = flags;
                    callFrame.args[1] = mask;

                    Log.d(TAG, "Window.setFlags - removed FLAG_SECURE");
                }
            }
        );
    }

    private static void hookWindowAddFlags() throws Throwable {
        Hooking.pineHook(
            Window.class.getDeclaredMethod("addFlags", int.class),
            new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    int flags = (int) callFrame.args[0];
                    flags &= ~FLAG_SECURE;
                    callFrame.args[0] = flags;

                    Log.d(TAG, "Window.addFlags - removed FLAG_SECURE");
                }
            }
        );
    }

    private static void hookLayoutParams() throws Throwable {
        // Hook all LayoutParams constructors
        Class<?> layoutParamsClass = WindowManager.LayoutParams.class;

        // Constructor with no args
        Hooking.pineHook(
            layoutParamsClass.getDeclaredConstructor(),
            new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    clearSecureFlagFromParams(callFrame.thisObject); // Fixed: thisObject instead of thisObj
                }
            }
        );

        // Constructor with flags
        Hooking.pineHook(
            layoutParamsClass.getDeclaredConstructor(int.class, int.class),
            new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    int flags = (int) callFrame.args[1];
                    callFrame.args[1] = flags & ~FLAG_SECURE;
                }
            }
        );
    }

    private static void hookSurfaceViewSetSecure() throws Throwable {
        Hooking.pineHook(
            SurfaceView.class.getDeclaredMethod("setSecure", boolean.class),
            new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    // Always set to false (not secure)
                    callFrame.args[0] = false;
                    Log.d(TAG, "SurfaceView.setSecure forced to false");
                }
            }
        );
    }

    private static void clearSecureFlagFromParams(Object layoutParams) {
        try {
            Field flagsField = layoutParams.getClass().getField("flags");
            flagsField.setAccessible(true);
            int flags = flagsField.getInt(layoutParams);
            flagsField.setInt(layoutParams, flags & ~FLAG_SECURE);
        } catch (Exception e) {
            Log.e(TAG, "Error clearing flag from params", e);
        }
    }
}
