package com.applisto.appcloner;

import android.util.Log;
import java.lang.reflect.Method;

public final class ReflectionUtil {
    private ReflectionUtil() {}

    public static Object getStaticFieldValue(Class<?> clazz, String fieldName) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(null);
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }

    public static boolean setStaticFieldValue(Class<?> clazz, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(null, value);
            return true;
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return false;
        }
    }

    public static Object getFieldValue(Object obj, String fieldName) {
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }

    public static Object invokeMethod(Object obj, String methodName, Object... args) {
        try {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = args[i].getClass();
                // Primitive type handling for simple int cases (common in these hooks)
                if (args[i] instanceof Integer) types[i] = int.class;
                else if (args[i] instanceof Boolean) types[i] = boolean.class;
            }
            Method m = obj.getClass().getDeclaredMethod(methodName, types);
            m.setAccessible(true);
            return m.invoke(obj, args);
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }

    public static Method findMethodByParameterTypes(String className, String methodName, Class<?>... paramTypes) {
        try {
            Class<?> c = Class.forName(className);
            return findMethodByParameterTypes(c, methodName, paramTypes);
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }

    public static Method findMethodByParameterTypes(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method m = clazz.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
            return m;
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }
}
