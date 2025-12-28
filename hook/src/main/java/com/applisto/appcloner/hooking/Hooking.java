package com.applisto.appcloner.hooking;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.util.Log;
import com.applisto.appcloner.classes.Utils;
import com.swift.sandhook.SandHook;
import com.swift.sandhook.SandHookConfig;
import com.swift.sandhook.utils.ReflectionUtils;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

public class Hooking {
    private static final String TAG = Hooking.class.getSimpleName();
    private static ExecutorService sHookExecutor;
    private static boolean sHookingInited;
    private static boolean sUseDelayedHooking;
    private static boolean sUseLegacyHooks;
    private static boolean sUseNewHooks;
    private static Boolean sUseSandHook;

    public static void initHooking(Context context) {
        synchronized (Hooking.class) {
            if (sHookingInited) {
                return;
            }
            if (context == null) {
                Log.w(TAG, "initHooking called with null context");
                return;
            }
            sHookingInited = true;

            if (sUseNewHooks) {
                Log.i(TAG, "initHooking; AliuHook");
                try {
                    // Reflection for XposedBridge.disableProfileSaver()
                    callStaticMethod(de.robv.android.xposed.XposedBridge.class, "disableProfileSaver");
                } catch (Throwable t) {
                    Log.w(TAG, t);
                    Utils.showNotification("Failed to initialize hooking.", true);
                }

                boolean hiddenApiRestrictionsDisabled = false;
                try {
                     // Reflection for XposedBridge.disableHiddenApiRestrictions()
                     Object res = callStaticMethod(de.robv.android.xposed.XposedBridge.class, "disableHiddenApiRestrictions");
                     if (res instanceof Boolean) {
                         hiddenApiRestrictionsDisabled = (Boolean) res;
                     }
                } catch (Throwable t) {
                    Log.w(TAG, "Failed to disableHiddenApiRestrictions", t);
                }

                Log.i(TAG, "initHooking; hiddenApiRestrictionsDisabled: " + hiddenApiRestrictionsDisabled);
                if (!hiddenApiRestrictionsDisabled && SandHookConfig.SDK_INT >= 28) {
                    boolean passApiCheck = ReflectionUtils.passApiCheck();
                    Log.i(TAG, "initHooking; passApiCheck: " + passApiCheck);
                }
                return;
            }

            if (useSandHook()) {
                Log.i(TAG, "initHooking; SandHook");
                try {
                    int flags = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0).flags;
                    boolean isDebuggable = (flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
                    Log.i(TAG, "initHooking; SandHook; isDebuggable: " + isDebuggable);
                    SandHookConfig.DEBUG = isDebuggable;
                    setStaticObjectField(SandHookConfig.class, "SELF_PACKAGE_NAME", context.getPackageName());
                    SandHook.disableVMInline();

                    try {
                        Method tryDisableProfile = SandHook.class.getDeclaredMethod("tryDisableProfile", String.class);
                        tryDisableProfile.invoke(null, context.getPackageName());
                    } catch (NoSuchMethodException e) {
                        // Method not present in this version
                    }

                    SandHook.disableDex2oatInline(false);
                    if (SandHookConfig.SDK_INT >= 28) {
                        SandHook.passApiCheck();
                    }
                } catch (Exception e) {
                    Log.w(TAG, e);
                    sUseSandHook = false;
                    // Fallback to AndHook
                }
            }

            if (!useSandHook()) {
                Log.i(TAG, "initHooking; AndHook");
                setStaticObjectField(SandHookConfig.class, "SELF_PACKAGE_NAME", context.getPackageName());
                andhook.lib.AndHook.ensureNativeLibraryLoaded(null);
            }
        }
    }

    public static void setUseNewHooks(boolean useNewHooks) {
        Log.i(TAG, "setUseNewHooks; useNewHooks: " + useNewHooks);
        sUseNewHooks = useNewHooks;
        sHookingInited = false;
        sUseSandHook = null;
    }

    public static void setUseLegacyHooks(boolean useLegacyHooks) {
        Log.i(TAG, "setUseLegacyHooks; useLegacyHooks: " + useLegacyHooks);
        sUseLegacyHooks = useLegacyHooks;
    }

    public static void setUseDelayedHooking(boolean useDelayedHooking) {
        Log.i(TAG, "setUseDelayedHooking; useDelayedHooking: " + useDelayedHooking);
        sUseDelayedHooking = useDelayedHooking;
    }

    public static boolean useSandHook() {
        synchronized (Hooking.class) {
            if (sUseSandHook != null) {
                return sUseSandHook;
            }

            if (Build.VERSION.SDK_INT < 21) {
                sUseSandHook = false;
                return false;
            }

            boolean isX86 = Utils.isX86();
            if (sUseNewHooks || sUseLegacyHooks) {
                Log.i(TAG, "useSandHook; using new / legacy hooks...");
                sUseSandHook = false;
            } else {
                Log.i(TAG, "useSandHook; x86: " + isX86);
                sUseSandHook = !isX86;
            }

            if (isX86 && sUseDelayedHooking) {
                sHookExecutor = Executors.newSingleThreadExecutor();
                sHookExecutor.submit(() -> {
                     // Empty lambda as seen in bytecode
                });
            }

            return sUseSandHook;
        }
    }

    public static MethodHook.Unhook pineHook(Member member, MethodHook callback) {
        return pineHook(member, callback, true);
    }

    public static MethodHook.Unhook pineHook(Member member, MethodHook callback, boolean isNative) {
        if (member == null || callback == null) {
            return null;
        }

        if (sUseNewHooks) {
            aliuHookBridge(member, callback);
            return null;
        }

        if (!sUseLegacyHooks && !Utils.isX86()) {
            return Hooking.pineHook(member, callback, isNative);
        }

        andHookBridge(member, callback);
        return null;
    }

    private static top.canyie.pine.Pine.HookRecord createHookRecord(Member member) {
        try {
            java.lang.reflect.Constructor<top.canyie.pine.Pine.HookRecord> constructor =
                    top.canyie.pine.Pine.HookRecord.class.getDeclaredConstructor(Member.class, long.class);
            constructor.setAccessible(true);
            return constructor.newInstance(member, 0L);
        } catch (Exception e) {
            Log.w(TAG, "Failed to create HookRecord", e);
            return null;
        }
    }

    private static void aliuHookBridge(Member member, MethodHook callback) {
        initHooking(Utils.getApplication());
        top.canyie.pine.Pine.HookRecord hookRecord = createHookRecord(member);
        de.robv.android.xposed.XposedBridge.hookMethod(member, new de.robv.android.xposed.XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                 if (hookRecord != null) {
                     top.canyie.pine.Pine.CallFrame frame = new top.canyie.pine.Pine.CallFrame(hookRecord, param.thisObject, param.args);
                     callback.beforeCall(frame);

                     Throwable frameThrowable = (Throwable) getObjectField(frame, "throwable");
                     if (frameThrowable != null) {
                         param.setThrowable(frameThrowable);
                     } else {
                         Object frameResult = getObjectField(frame, "result");
                         boolean returnEarly = (boolean) getObjectField(frame, "returnEarly");
                         if (returnEarly) {
                             param.setResult(frameResult);
                         }
                     }
                 }
            }
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                 if (hookRecord != null) {
                     top.canyie.pine.Pine.CallFrame frame = new top.canyie.pine.Pine.CallFrame(hookRecord, param.thisObject, param.args);

                     setObjectField(frame, "result", param.getResult());
                     setObjectField(frame, "throwable", param.getThrowable());

                     callback.afterCall(frame);

                     Throwable frameThrowable = (Throwable) getObjectField(frame, "throwable");
                     if (frameThrowable != null) {
                         param.setThrowable(frameThrowable);
                     } else {
                         Object frameResult = getObjectField(frame, "result");
                         boolean returnEarly = (boolean) getObjectField(frame, "returnEarly");
                         if (returnEarly) {
                             param.setResult(frameResult);
                         }
                     }
                 }
            }
        });
        Log.i(TAG, "aliuHookBridge; hooked " + member);
    }

    private static void andHookBridge(Member member, MethodHook callback) {
        if (member.getDeclaringClass().equals(Class.class)) {
            Log.w(TAG, "andHookBridge; cannot hook class: " + member.getDeclaringClass());
            return;
        }
        initHooking(Utils.getApplication());
        top.canyie.pine.Pine.HookRecord hookRecord = createHookRecord(member);
        andhook.lib.xposed.XposedBridge.hookMethod(member, new andhook.lib.xposed.XC_MethodHook() {
             @Override
             protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                 if (hookRecord != null) {
                     top.canyie.pine.Pine.CallFrame frame = new top.canyie.pine.Pine.CallFrame(hookRecord, param.thisObject, param.args);
                     callback.beforeCall(frame);

                     Throwable frameThrowable = (Throwable) getObjectField(frame, "throwable");
                     if (frameThrowable != null) {
                         param.setThrowable(frameThrowable);
                     } else {
                         Object frameResult = getObjectField(frame, "result");
                         boolean returnEarly = (boolean) getObjectField(frame, "returnEarly");
                         if (returnEarly) {
                             param.setResult(frameResult);
                         }
                     }
                 }
             }
             @Override
             protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                 if (hookRecord != null) {
                     top.canyie.pine.Pine.CallFrame frame = new top.canyie.pine.Pine.CallFrame(hookRecord, param.thisObject, param.args);

                     setObjectField(frame, "result", param.getResult());
                     setObjectField(frame, "throwable", param.getThrowable());

                     callback.afterCall(frame);

                     Throwable frameThrowable = (Throwable) getObjectField(frame, "throwable");
                     if (frameThrowable != null) {
                         param.setThrowable(frameThrowable);
                     } else {
                         Object frameResult = getObjectField(frame, "result");
                         boolean returnEarly = (boolean) getObjectField(frame, "returnEarly");
                         if (returnEarly) {
                             param.setResult(frameResult);
                         }
                     }
                 }
             }
        });
        Log.i(TAG, "andHookBridge; hooked " + member);
    }

    private static Object getObjectField(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            Log.w(TAG, "Failed to get field " + fieldName, e);
            return null;
        }
    }

    private static void setObjectField(Object obj, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
        } catch (Exception e) {
            Log.w(TAG, "Failed to set field " + fieldName, e);
        }
    }

    private static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
        } catch (Exception e) {
            // Log.w(TAG, "Failed to set static field " + fieldName, e);
        }
    }

    private static Object callStaticMethod(Class<?> clazz, String methodName) throws Exception {
        Method method = clazz.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    public static <T> T callInstanceOrigin(Method method, Object thisObject, Object... args) throws Throwable {
        if (useSandHook()) {
             return (T) SandHook.callOriginByBackup(method, thisObject, args);
        }
        return (T) andhook.lib.HookHelper.invokeObjectOrigin(thisObject, args);
    }

    public static <T> T callStaticOrigin(Method method, Object... args) throws Throwable {
         if (useSandHook()) {
             return (T) SandHook.callOriginByBackup(method, null, args);
        }
        return (T) andhook.lib.HookHelper.invokeObjectOrigin(null, args);
    }

    public static void hookMethod(Class<?> clazz, String methodName, MethodHook callback, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            pineHook(method, callback);
            Log.d(TAG, "Hooked method: " + clazz.getName() + "." + methodName);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Method not found: " + clazz.getName() + "." + methodName, e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook: " + clazz.getName() + "." + methodName, t);
        }
    }

    public static void hookMethod(Method method, MethodHook callback) {
        if (method != null) {
            pineHook(method, callback);
            Log.d(TAG, "Hooked method object: " + method.getName());
        } else {
            Log.w(TAG, "Attempted to hook null method");
        }
    }
}
