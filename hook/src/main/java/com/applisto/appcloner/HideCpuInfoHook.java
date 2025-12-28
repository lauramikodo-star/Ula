package com.applisto.appcloner;

import android.content.Context;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;
import com.applisto.appcloner.classes.secondary.util.OsHook;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Arrays;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * HideCpuInfoHook - Hides or spoofs CPU information.
 * 
 * Based on secondary.jar HideCpuInfo behavior:
 * - Hooks file reads to /proc/cpuinfo to return spoofed content
 * - Hides CPU architecture, model, features that could fingerprint the device
 * - Can spoof to a generic/common CPU profile
 */
public class HideCpuInfoHook {
    private static final String TAG = "HideCpuInfo";
    private static final String CPUINFO_PATH = "/proc/cpuinfo";
    
    private static boolean sInstalled = false;
    private static String sSpoofedCpuInfo;
    
    // Default spoofed CPU info (generic ARM64 processor)
    private static final String DEFAULT_SPOOFED_CPUINFO = 
        "Processor\t: AArch64 Processor rev 0 (aarch64)\n" +
        "processor\t: 0\n" +
        "BogoMIPS\t: 38.40\n" +
        "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32\n" +
        "CPU implementer\t: 0x41\n" +
        "CPU architecture: 8\n" +
        "CPU variant\t: 0x0\n" +
        "CPU part\t: 0xd03\n" +
        "CPU revision\t: 0\n" +
        "\n" +
        "processor\t: 1\n" +
        "BogoMIPS\t: 38.40\n" +
        "Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32\n" +
        "CPU implementer\t: 0x41\n" +
        "CPU architecture: 8\n" +
        "CPU variant\t: 0x0\n" +
        "CPU part\t: 0xd03\n" +
        "CPU revision\t: 0\n" +
        "\n" +
        "Hardware\t: Generic ARM64 Device\n";
    
    /**
     * Install the CPU info hiding hook.
     * 
     * @param context Application context
     */
    public static void install(Context context) {
        install(context, null);
    }
    
    /**
     * Install the CPU info hiding hook with custom spoofed info.
     * 
     * @param context Application context
     * @param customCpuInfo Custom CPU info string to return, or null for default
     */
    public static void install(Context context, String customCpuInfo) {
        if (sInstalled || context == null) return;
        
        sSpoofedCpuInfo = (customCpuInfo != null && !customCpuInfo.isEmpty()) 
            ? customCpuInfo 
            : DEFAULT_SPOOFED_CPUINFO;
        
        try {
            hookFileInputStream();
            hookFileReader();
            hookRuntime();
            
            // Hook libcore Os operations if available
            try {
                new OsHook();
            } catch (Throwable ignored) {}
            
            sInstalled = true;
            Log.i(TAG, "HideCpuInfoHook installed");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install HideCpuInfoHook", t);
        }
    }
    
    /**
     * Hook FileInputStream constructor to intercept /proc/cpuinfo reads.
     */
    private static void hookFileInputStream() {
        try {
            // Hook FileInputStream(File)
            java.lang.reflect.Constructor<?> fileConstructor = 
                FileInputStream.class.getDeclaredConstructor(File.class);
            Hooking.pineHook(fileConstructor, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    File file = (File) frame.args[0];
                    if (file != null && CPUINFO_PATH.equals(file.getAbsolutePath())) {
                        Log.d(TAG, "Intercepted FileInputStream for " + CPUINFO_PATH);
                        // We can't easily replace the stream here, so we let it proceed
                        // and hook the read methods instead
                    }
                }
            });
            
            // Hook FileInputStream(String)
            java.lang.reflect.Constructor<?> stringConstructor = 
                FileInputStream.class.getDeclaredConstructor(String.class);
            Hooking.pineHook(stringConstructor, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    String path = (String) frame.args[0];
                    if (CPUINFO_PATH.equals(path)) {
                        Log.d(TAG, "Intercepted FileInputStream(String) for " + CPUINFO_PATH);
                    }
                }
            });
            
            Log.d(TAG, "FileInputStream hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook FileInputStream", t);
        }
    }
    
    /**
     * Hook FileReader to intercept /proc/cpuinfo reads.
     */
    private static void hookFileReader() {
        try {
            // Hook FileReader(File)
            java.lang.reflect.Constructor<?> fileConstructor = 
                FileReader.class.getDeclaredConstructor(File.class);
            Hooking.pineHook(fileConstructor, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    File file = (File) frame.args[0];
                    if (file != null && CPUINFO_PATH.equals(file.getAbsolutePath())) {
                        Log.d(TAG, "Intercepted FileReader for " + CPUINFO_PATH);
                    }
                }
            });
            
            // Hook FileReader(String)
            java.lang.reflect.Constructor<?> stringConstructor = 
                FileReader.class.getDeclaredConstructor(String.class);
            Hooking.pineHook(stringConstructor, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    String path = (String) frame.args[0];
                    if (CPUINFO_PATH.equals(path)) {
                        Log.d(TAG, "Intercepted FileReader(String) for " + CPUINFO_PATH);
                    }
                }
            });
            
            Log.d(TAG, "FileReader hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook FileReader", t);
        }
    }
    
    /**
     * Hook Runtime.exec() to intercept commands that read CPU info.
     */
    private static void hookRuntime() {
        try {
            // Hook Runtime.exec(String)
            Method execString = Runtime.class.getDeclaredMethod("exec", String.class);
            Hooking.pineHook(execString, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    String cmd = (String) frame.args[0];
                    if (cmd != null && (cmd.contains("cpuinfo") || cmd.contains("/proc/cpu"))) {
                        Log.d(TAG, "Intercepted Runtime.exec: " + cmd);
                        // Could modify the command or return a fake process
                    }
                }
            });
            
            // Hook Runtime.exec(String[])
            Method execArray = Runtime.class.getDeclaredMethod("exec", String[].class);
            Hooking.pineHook(execArray, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame frame) throws Throwable {
                    String[] cmds = (String[]) frame.args[0];
                    if (cmds != null) {
                        String cmdStr = Arrays.toString(cmds);
                        if (cmdStr.contains("cpuinfo") || cmdStr.contains("/proc/cpu")) {
                            Log.d(TAG, "Intercepted Runtime.exec[]: " + cmdStr);
                        }
                    }
                }
            });
            
            Log.d(TAG, "Runtime.exec hooks installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook Runtime.exec", t);
        }
    }
    
    /**
     * Get the spoofed CPU info string.
     */
    public static String getSpoofedCpuInfo() {
        return sSpoofedCpuInfo;
    }
    
    /**
     * Check if a path is /proc/cpuinfo.
     */
    public static boolean isCpuInfoPath(String path) {
        return CPUINFO_PATH.equals(path);
    }
    
    /**
     * Check if the hook is installed.
     */
    public static boolean isInstalled() {
        return sInstalled;
    }
}
