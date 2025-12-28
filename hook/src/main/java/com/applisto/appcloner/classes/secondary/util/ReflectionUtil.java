package com.applisto.appcloner.classes.secondary.util;

import android.util.Log;
import java.lang.reflect.Method;

public class ReflectionUtil {
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

    public static Method findMethodByParameterTypes(String className, String methodName, Class<?>... parameterTypes) {
        try {
            return Class.forName(className).getDeclaredMethod(methodName, parameterTypes);
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }

    public static Method findMethodByParameterTypes(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            return clazz.getDeclaredMethod(methodName, parameterTypes);
        } catch (Exception e) {
            Log.w("ReflectionUtil", e);
            return null;
        }
    }
}
