package com.applisto.appcloner;

import android.util.Log;

import java.lang.reflect.Method;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class SimplifiedHooking {
    private static final String TAG = "SimplifiedHooking";

    public abstract static class HookCallback extends MethodHook {
        @Override
        public void beforeCall(Pine.CallFrame callFrame) throws Throwable {
            before(callFrame.thisObject, callFrame.args, callFrame);
        }

        @Override
        public void afterCall(Pine.CallFrame callFrame) throws Throwable {
            after(callFrame.thisObject, callFrame.args, callFrame);
        }

        public abstract void before(Object thisObject, Object[] args, Pine.CallFrame callFrame) throws Throwable;

        public abstract void after(Object thisObject, Object[] args, Pine.CallFrame callFrame) throws Throwable;
    }

    public static void hookMethod(Class<?> clazz, String methodName, HookCallback callback, Class<?>... parameterTypes) {
        try {
            Method method = clazz.getDeclaredMethod(methodName, parameterTypes);
            Hooking.pineHook(method, callback);
            Log.d(TAG, "Hooked method: " + clazz.getName() + "." + methodName);
        } catch (NoSuchMethodException e) {
            Log.e(TAG, "Method not found: " + clazz.getName() + "." + methodName, e);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to hook: " + clazz.getName() + "." + methodName, t);
        }
    }

}
