package com.applisto.appcloner;

import android.content.Context;
import java.io.IOException;

public class ConfigLoader {
    public static String loadConfigFromAssets(Context context, String fileName) throws IOException {
        // Placeholder
        return "{}";
    }
    
    /**
     * Load ClonerSettings from the context
     */
    public static ClonerSettings loadSettings(Context context) {
        try {
            return ClonerSettings.get(context);
        } catch (Exception e) {
            return null;
        }
    }
}
