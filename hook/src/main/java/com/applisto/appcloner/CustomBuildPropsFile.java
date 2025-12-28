package com.applisto.appcloner;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

public class CustomBuildPropsFile {
    private static final String TAG = "CustomBuildPropsFile";

    public static void install(Context context, String text, boolean enablePlaceholders) {
        if (TextUtils.isEmpty(text)) return;

        if (enablePlaceholders) {
            // Placeholder substitution stub
            // text = Utils.substitutePlaceholders(context, text, "custom_build_props_file_title");
        }

        try {
            Map<String, String> map = parseBuildProp(text);
            CustomBuildProps.install(context, map);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse custom build props", e);
        }
    }

    private static Map<String, String> parseBuildProp(String text) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new StringReader(text))) {
            String line;
            while ((line = br.readLine()) != null) {
                String s = line.trim();
                if (s.isEmpty()) continue;
                char c = s.charAt(0);
                if (c == '#' || c == ';' || c == '!' || c == '[') continue;

                int i = s.indexOf('=');
                if (i < 0) i = s.indexOf(':');
                if (i < 1) continue;

                String k = s.substring(0, i).trim();
                String v = s.substring(i + 1).trim();
                out.put(k, v);
            }
        }
        return out;
    }
}
