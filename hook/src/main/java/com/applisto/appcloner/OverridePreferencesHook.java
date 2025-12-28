package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.applisto.appcloner.hooking.Hooking;
import com.applisto.appcloner.ReflectionUtil;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * OverridePreferencesHook - mirrors secondary.jar OverridePreferences.
 * Forces SharedPreferences reads/writes to override values (supports regex keys).
 */
public class OverridePreferencesHook {
    private static final String TAG = "OverridePreferences";
    private static final String NULL_VALUE = "<<<NULL>>>";

    private static final Set<String> DISALLOWED_KEYS = new HashSet<>();

    static {
        DISALLOWED_KEYS.add("register_clone_timestamp");
        DISALLOWED_KEYS.add("register_clone_error_message");
    }

    private static final Map<String, String> sMap = new HashMap<>();
    private static final List<Pair<Pattern, String>> sList = new ArrayList<>();
    private static boolean sHooked;

    public static void install(Context context) {
        if (sHooked || context == null) return;

        JSONObject cfg = ClonerSettings.get(context).raw();
        JSONArray overrides = cfg.optJSONArray("override_shared_preferences");
        boolean enablePlaceholders = cfg.optBoolean("override_shared_preferences_placeholders", false);
        if (overrides == null || overrides.length() == 0) return;

        for (int i = 0; i < overrides.length(); i++) {
            JSONObject item = overrides.optJSONObject(i);
            if (item == null) continue;
            String name = item.optString("name", null);
            String value = item.optString("value", null);
            if (name == null || value == null) continue;

            boolean regex = item.optBoolean("nameRegExp", false);
            try {
                if (regex) {
                    sList.add(Pair.create(Pattern.compile(name), value));
                } else {
                    sMap.put(name, value);
                }
            } catch (Throwable t) {
                Log.w(TAG, "Failed to add override rule: " + name, t);
            }
        }

        if (sMap.isEmpty() && sList.isEmpty()) {
            return;
        }

        hookReads();
        hookWrites();
        sHooked = true;
        Log.i(TAG, "OverridePreferencesHook installed; rules: " + (sMap.size() + sList.size()));
    }

    /* ---------- Hook helpers ---------- */

    private static void hookReads() {
        try {
            Class<?> impl = Class.forName("android.app.SharedPreferencesImpl");
            hookGetter(impl, "getString");
            hookGetter(impl, "getStringSet");
            hookGetter(impl, "getInt");
            hookGetter(impl, "getLong");
            hookGetter(impl, "getFloat");
            hookGetter(impl, "getBoolean");
            hookContains(impl);
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook SharedPreferences getters", e);
        }
    }

    private static void hookWrites() {
        try {
            Class<?> editor = Class.forName("android.app.SharedPreferencesImpl$EditorImpl");
            hookPutter(editor, "putString");
            hookPutter(editor, "putStringSet");
            hookPutter(editor, "putInt");
            hookPutter(editor, "putLong");
            hookPutter(editor, "putFloat");
            hookPutter(editor, "putBoolean");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook SharedPreferences put*", e);
        }
    }

    private static void hookGetter(Class<?> impl, String name) throws NoSuchMethodException {
        for (Method m : impl.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterTypes().length != 2) continue;
            Hooking.pineHook(m, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (skip(cf.thisObject)) return;
                    String key = (String) cf.args[0];
                    Object override = getOverrideValue(key, cf.args[1], m.getReturnType());
                    if (override != null) {
                        cf.setResult(override);
                    }
                }
            });
        }
    }

    private static void hookContains(Class<?> impl) throws NoSuchMethodException {
        Method contains = impl.getDeclaredMethod("contains", String.class);
        Hooking.pineHook(contains, new MethodHook() {
            @Override
            public void beforeCall(Pine.CallFrame cf) {
                if (skip(cf.thisObject)) return;
                String key = (String) cf.args[0];
                String v = getValue(key);
                if (v != null) {
                    cf.setResult(!NULL_VALUE.equals(v));
                }
            }
        });
    }

    private static void hookPutter(Class<?> editor, String name) throws NoSuchMethodException {
        for (Method m : editor.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            if (m.getParameterTypes().length != 2) continue;
            Hooking.pineHook(m, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    if (skip(cf.thisObject)) return;
                    String key = (String) cf.args[0];
                    String v = getValue(key);
                    if (v == null) return;
                    if (NULL_VALUE.equals(v)) {
                        // Leave primitives untouched to avoid unboxing null; allow null for objects
                        if (cf.args[1] instanceof String || cf.args[1] instanceof Set) {
                            cf.args[1] = null;
                        }
                        return;
                    }
                    Class<?>[] params = m.getParameterTypes();
                    Class<?> type = params[1];
                    try {
                        if (type == String.class) {
                            cf.args[1] = v;
                        } else if (Set.class.isAssignableFrom(type)) {
                            String[] parts = v.split(",");
                            Set<String> set = new HashSet<>();
                            for (String p : parts) set.add(p.trim());
                            cf.args[1] = set;
                        } else if (type == int.class || type == Integer.class) {
                            cf.args[1] = Integer.parseInt(v);
                        } else if (type == long.class || type == Long.class) {
                            cf.args[1] = Long.parseLong(v);
                        } else if (type == float.class || type == Float.class) {
                            cf.args[1] = Float.parseFloat(v);
                        } else if (type == boolean.class || type == Boolean.class) {
                            cf.args[1] = parseBoolean(v);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to apply override for put*: " + key, e);
                    }
                }
            });
        }
    }

    /* ---------- Core override lookup ---------- */

    private static Object getOverrideValue(String key, Object defValue, Class<?> returnType) {
        if (key == null || !allowKey(key)) return null;
        String v = getValue(key);
        if (v == null) return null;
        if (NULL_VALUE.equals(v)) {
            // return defaults (same as secondary behavior)
            return defValue;
        }
        try {
            if (returnType == String.class) {
                return v;
            } else if (returnType == Set.class) {
                String[] parts = v.split(",");
                Set<String> set = new HashSet<>();
                for (String p : parts) set.add(p.trim());
                return set;
            } else if (returnType == int.class || returnType == Integer.class) {
                return Integer.parseInt(v);
            } else if (returnType == long.class || returnType == Long.class) {
                return Long.parseLong(v);
            } else if (returnType == float.class || returnType == Float.class) {
                return Float.parseFloat(v);
            } else if (returnType == boolean.class || returnType == Boolean.class) {
                return parseBoolean(v);
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse override for key " + key, e);
        }
        return null;
    }

    private static boolean parseBoolean(String v) {
        String s = v.toLowerCase();
        return "true".equals(s) || "yes".equals(s) || "1".equals(s);
    }

    private static String getValue(String key) {
        if (key == null || !allowKey(key)) return null;
        for (Pair<Pattern, String> p : sList) {
            try {
                if (p.first.matcher(key).matches()) {
                    return p.second;
                }
            } catch (Exception ignored) {}
        }
        if (sMap.containsKey(key)) {
            return sMap.get(key);
        }
        return null;
    }

    private static boolean allowKey(String key) {
        return !DISALLOWED_KEYS.contains(key);
    }

    private static boolean skip(Object prefsImpl) {
        try {
            if (prefsImpl == null) return true;
            // Skip AppCloner internal preferences to avoid self-overrides
            File f = (File) ReflectionUtil.getFieldValue(prefsImpl, "mFile");
            if (f != null && f.getName().contains("app_cloner_classes")) {
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
