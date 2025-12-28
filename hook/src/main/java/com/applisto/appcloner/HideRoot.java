package com.applisto.appcloner;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class HideRoot {
    private static final String TAG = "HideRoot";

    public static void install(Context context) {
        Log.i(TAG, "Installing HideRoot hooks...");

        // 1. Hide Root Apps
        List<String> rootApps = Arrays.asList(
            "com.noshufou.android.su",
            "com.noshufou.android.su.elite",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.thirdparty.superuser",
            "com.yellowes.su",
            "com.topjohnwu.magisk",
            "com.koushikdutta.rommanager",
            "com.koushikdutta.rommanager.license",
            "com.dimonvideo.luckypatcher",
            "com.chelpus.lackypatch",
            "com.ramdroid.appquarantine",
            "com.ramdroid.appquarantinepro",
            "com.devadvance.rootcloak",
            "com.devadvance.rootcloakplus",
            "de.robv.android.xposed.installer",
            "com.saurik.substrate",
            "com.zachspong.temprootremovejb",
            "com.amphoras.hidemyroot",
            "com.amphoras.hidemyrootadfree",
            "com.formyhm.hiderootPremium",
            "com.formyhm.hideroot",
            "me.twrp.twrpapp"
            // Add more from the reference list if needed, this covers the major ones
        );
        PackageHider.install(context, rootApps);

        // 2. Override Build Props
        SystemPropertiesHook.overrideSystemProperty("ro.build.tags", "release-keys");

        // 3. Hook RootBeer (if present)
        // RootBeer checks are often in a library packaged with the app.
        // We try to find common class names.
        String[] possibleRootBeerClasses = {
            "com.scottyab.rootbeer.RootBeer",
            "com.kimchangyoun.rootbeer.RootBeer" // Just in case of package variations
        };

        for (String className : possibleRootBeerClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                Log.i(TAG, "Found RootBeer class: " + className);

                for (Method method : clazz.getDeclaredMethods()) {
                    // Hook all boolean methods that sound like checks
                    if (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class) {
                        String name = method.getName();
                        if (name.startsWith("is") || name.startsWith("check") || name.startsWith("detect")) {
                             Hooking.hookMethod(method, new MethodHook() {
                                 @Override
                                 public void beforeCall(Pine.CallFrame callFrame) {
                                     // Return false for "isRooted" etc.
                                     callFrame.setResult(false);
                                 }
                             });
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // Not found, normal for apps that don't use it
            } catch (Exception e) {
                Log.w(TAG, "Error hooking RootBeer", e);
            }
        }

        Log.i(TAG, "HideRoot installed.");
    }
}
