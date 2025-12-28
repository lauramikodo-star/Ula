package com.applisto.appcloner;

import android.content.Context;
import android.opengl.GLES10;
import android.opengl.GLES20;
import android.util.Log;

import com.applisto.appcloner.hooking.Hooking;

import java.lang.reflect.Method;

import javax.microedition.khronos.opengles.GL10;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;

/**
 * HideGpuInfoHook - Hides or spoofs GPU information.
 * 
 * Based on secondary.jar HideGpuInfo behavior:
 * - Hooks GLES10.glGetString() and GLES20.glGetString()
 * - Intercepts GL_VENDOR, GL_RENDERER, GL_VERSION queries
 * - Returns generic/spoofed GPU info to prevent device fingerprinting
 */
public class HideGpuInfoHook {
    private static final String TAG = "HideGpuInfo";
    
    // OpenGL string query constants
    private static final int GL_VENDOR = 0x1F00;
    private static final int GL_RENDERER = 0x1F01;
    private static final int GL_VERSION = 0x1F02;
    private static final int GL_EXTENSIONS = 0x1F03;
    
    private static boolean sInstalled = false;
    
    // Spoofed values (generic Mali GPU - common on many devices)
    private static String sSpoofedVendor = "ARM";
    private static String sSpoofedRenderer = "Mali-G78";
    private static String sSpoofedVersion = "OpenGL ES 3.2 v1.r32p1";
    private static String sSpoofedExtensions = null; // null means use original
    
    /**
     * Install the GPU info hiding hook with default spoofed values.
     */
    public static void install(Context context) {
        install(context, null, null, null);
    }
    
    /**
     * Install the GPU info hiding hook with custom spoofed values.
     * 
     * @param context Application context
     * @param vendor Custom vendor string (null for default)
     * @param renderer Custom renderer string (null for default)
     * @param version Custom version string (null for default)
     */
    public static void install(Context context, String vendor, String renderer, String version) {
        if (sInstalled || context == null) return;
        
        if (vendor != null && !vendor.isEmpty()) sSpoofedVendor = vendor;
        if (renderer != null && !renderer.isEmpty()) sSpoofedRenderer = renderer;
        if (version != null && !version.isEmpty()) sSpoofedVersion = version;
        
        try {
            hookGLES10();
            hookGLES20();
            hookGL10Interface();
            
            sInstalled = true;
            Log.i(TAG, "HideGpuInfoHook installed (Vendor=" + sSpoofedVendor + 
                  ", Renderer=" + sSpoofedRenderer + ")");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to install HideGpuInfoHook", t);
        }
    }
    
    /**
     * Hook GLES10.glGetString().
     */
    private static void hookGLES10() {
        try {
            Method glGetString = GLES10.class.getDeclaredMethod("glGetString", int.class);
            Hooking.pineHook(glGetString, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    int name = (int) frame.args[0];
                    String spoofed = getSpoofedValue(name);
                    if (spoofed != null) {
                        frame.setResult(spoofed);
                        Log.d(TAG, "GLES10.glGetString(" + name + ") -> " + spoofed);
                    }
                }
            });
            Log.d(TAG, "GLES10.glGetString hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook GLES10.glGetString", t);
        }
    }
    
    /**
     * Hook GLES20.glGetString().
     */
    private static void hookGLES20() {
        try {
            Method glGetString = GLES20.class.getDeclaredMethod("glGetString", int.class);
            Hooking.pineHook(glGetString, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    int name = (int) frame.args[0];
                    String spoofed = getSpoofedValue(name);
                    if (spoofed != null) {
                        frame.setResult(spoofed);
                        Log.d(TAG, "GLES20.glGetString(" + name + ") -> " + spoofed);
                    }
                }
            });
            Log.d(TAG, "GLES20.glGetString hook installed");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook GLES20.glGetString", t);
        }
    }
    
    /**
     * Hook GL10 interface methods (used by some apps via EGL).
     */
    private static void hookGL10Interface() {
        try {
            // Try to hook GLImpl which implements GL10
            Class<?> glImplClass = Class.forName("com.google.android.gles_jni.GLImpl");
            Method glGetString = glImplClass.getDeclaredMethod("glGetString", int.class);
            
            Hooking.pineHook(glGetString, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame frame) throws Throwable {
                    int name = (int) frame.args[0];
                    String spoofed = getSpoofedValue(name);
                    if (spoofed != null) {
                        frame.setResult(spoofed);
                        Log.d(TAG, "GLImpl.glGetString(" + name + ") -> " + spoofed);
                    }
                }
            });
            Log.d(TAG, "GLImpl.glGetString hook installed");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "GLImpl class not found, skipping");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook GLImpl.glGetString", t);
        }
    }
    
    /**
     * Get the spoofed value for a GL string query.
     * 
     * @param name GL constant (GL_VENDOR, GL_RENDERER, etc.)
     * @return Spoofed string or null to use original
     */
    private static String getSpoofedValue(int name) {
        switch (name) {
            case GL_VENDOR:
                return sSpoofedVendor;
            case GL_RENDERER:
                return sSpoofedRenderer;
            case GL_VERSION:
                return sSpoofedVersion;
            case GL_EXTENSIONS:
                return sSpoofedExtensions; // null means use original
            default:
                return null;
        }
    }
    
    /**
     * Set custom spoofed GPU values.
     */
    public static void setSpoofedValues(String vendor, String renderer, String version) {
        if (vendor != null) sSpoofedVendor = vendor;
        if (renderer != null) sSpoofedRenderer = renderer;
        if (version != null) sSpoofedVersion = version;
    }
    
    /**
     * Get current spoofed vendor.
     */
    public static String getSpoofedVendor() {
        return sSpoofedVendor;
    }
    
    /**
     * Get current spoofed renderer.
     */
    public static String getSpoofedRenderer() {
        return sSpoofedRenderer;
    }
    
    /**
     * Check if the hook is installed.
     */
    public static boolean isInstalled() {
        return sInstalled;
    }
}
