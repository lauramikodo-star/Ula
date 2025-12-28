package com.applisto.appcloner;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public class HideEmulator {
    private static final String TAG = "HideEmulator";

    public static void install(Context context) {
        Log.i(TAG, "Installing HideEmulator hooks...");

        // 1. Hide Emulator Apps
        List<String> emulatorApps = Arrays.asList(
            "com.google.android.launcher.layouts.genymotion",
            "com.bluestacks",
            "com.bluestacks.filemanager",
            "com.bluestacks.appmart",
            "com.bluestacks.BstCommandProcessor",
            "com.bluestacks.settings",
            "com.bluestacks.home",
            "com.bluestacks.appguidance",
            "com.microvirt.installer",
            "com.microvirt.guide",
            "com.microvirt.tools",
            "com.microvirt.download",
            "com.microvirt.memuime",
            "com.microvirt.launcher",
            "com.microvirt.launcher2",
            "com.bignox.app"
        );
        PackageHider.install(context, emulatorApps);

        // 2. Override System Properties
        SystemPropertiesHook.overrideSystemProperty("init.svc.qemud", null);
        SystemPropertiesHook.overrideSystemProperty("init.svc.qemu-props", null);
        SystemPropertiesHook.overrideSystemProperty("qemu.hw.mainkeys", null);
        SystemPropertiesHook.overrideSystemProperty("qemu.sf.fake_camera", null);
        SystemPropertiesHook.overrideSystemProperty("qemu.sf.lcd_density", null);
        SystemPropertiesHook.overrideSystemProperty("ro.bootloader", "");
        SystemPropertiesHook.overrideSystemProperty("ro.bootmode", "");
        SystemPropertiesHook.overrideSystemProperty("ro.hardware", "");
        SystemPropertiesHook.overrideSystemProperty("ro.kernel.android.qemud", null);
        SystemPropertiesHook.overrideSystemProperty("ro.kernel.qemu.gles", null);
        SystemPropertiesHook.overrideSystemProperty("ro.kernel.qemu", "");
        SystemPropertiesHook.overrideSystemProperty("ro.product.device", "");
        SystemPropertiesHook.overrideSystemProperty("ro.product.model", "");
        SystemPropertiesHook.overrideSystemProperty("ro.product.name", "");
        SystemPropertiesHook.overrideSystemProperty("ro.serialno", null);

        // 3. Override Build fields
        ReflectionUtil.setStaticFieldValue(Build.class, "IS_EMULATOR", false); // Deprecated but sometimes checked
        // Common checks check Build.FINGERPRINT, Build.MODEL, etc.
        // We might want to set generic values if not already handled by BuildPropsHook

        // 4. Hook Emulator Detector (com.felhr.emulatorspotter)
        try {
             Class<?> spotterClass = Class.forName("com.felhr.emulatorspotter.IsThisAnEmulator");
             Log.i(TAG, "Found IsThisAnEmulator class");

             // Hook isEmulator()
             Hooking.hookMethod(spotterClass, "isEmulator", new MethodHook() {
                 @Override
                 public void beforeCall(Pine.CallFrame callFrame) {
                     callFrame.setResult(false);
                 }
             }, (Class<?>[]) null); // varargs for empty params

        } catch (ClassNotFoundException e) {
             // Normal
        } catch (Exception e) {
             Log.w(TAG, "Error hooking IsThisAnEmulator", e);
        }

        Log.i(TAG, "HideEmulator installed.");
    }
}
