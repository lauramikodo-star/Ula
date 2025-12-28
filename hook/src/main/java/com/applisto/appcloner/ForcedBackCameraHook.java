package com.applisto.appcloner;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraDevice;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

public final class ForcedBackCameraHook {
    private static final String TAG = "ForcedBackCamera";
    
    // Track which camera is actually opened
    private static final Map<Camera, Integer> CAMERA_MAP = new HashMap<>();
    private static final Map<String, String> CAMERA2_MAP = new HashMap<>();
    
    private static String sBackCameraId = "0";
    private static String sFrontCameraId = "1";

    public static void install(Context ctx) {
        try {
            if (!ClonerSettings.get(ctx).raw().optBoolean("ForcedBackCamera", false)) {
                return;
            }

            detectCameraIds(ctx);
            
            // Hook Camera1 API
            hookCamera1();
            
            // Hook Camera2 API  
            hookCamera2();
            
            // Hook CameraX
            hookCameraX();
            
            Log.i(TAG, "Forced back camera hooks installed (front camera still appears available)");
        } catch (Throwable t) {
            Log.e(TAG, "Hook installation failed", t);
        }
    }

    private static void detectCameraIds(Context ctx) {
        try {
            CameraManager manager = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String[] ids = manager.getCameraIdList();
            
            for (String id : ids) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                
                if (facing != null) {
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        sBackCameraId = id;
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                        sFrontCameraId = id;
                    }
                }
            }
        } catch (Exception e) {
            // Use defaults
        }
    }

    /* ====================================================== */
    /* Camera1: Make front camera exist but use back          */
    /* ====================================================== */
    private static void hookCamera1() {
        try {
            // Don't change camera count - report actual number
            Hooking.pineHook(Camera.class.getDeclaredMethod("getNumberOfCameras"), new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    Integer count = (Integer) cf.getResult();
                    if (count != null && count < 2) {
                        // Ensure at least 2 cameras are reported
                        cf.setResult(2);
                        Log.d(TAG, "Forced camera count to 2");
                    }
                }
            });

            // Hook getCameraInfo - keep front camera info intact
            Hooking.pineHook(Camera.class.getDeclaredMethod("getCameraInfo", int.class, Camera.CameraInfo.class), 
                new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        // Don't modify - let it report correct info
                        // This allows apps to detect front camera exists
                    }
                });

            // Hook Camera.open(int) - this is where we swap
            Hooking.pineHook(Camera.class.getDeclaredMethod("open", int.class), new MethodHook() {
                private int originalRequestedId = -1;
                
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    int requestedId = (int) cf.args[0];
                    originalRequestedId = requestedId; // Store original request
                    
                    if (requestedId == 1) { // Front camera requested
                        Log.i(TAG, "Camera.open(1) intercepted, will open back camera");
                        cf.args[0] = 0; // Open back camera instead
                    }
                }
                
                @Override
                public void afterCall(Pine.CallFrame cf) {
                    Camera camera = (Camera) cf.getResult();
                    if (camera != null && originalRequestedId == 1) {
                        // We swapped front to back, remember this
                        CAMERA_MAP.put(camera, 1);
                    }
                }
            });

            // Hook Camera parameters to fake front camera characteristics
            hookCameraParameters();
            
        } catch (Exception e) {
            Log.e(TAG, "Camera1 hooks failed", e);
        }
    }

    private static void hookCameraParameters() {
        try {
            // Make back camera parameters look like front camera when needed
            Class<?> parametersClass = Class.forName("android.hardware.Camera$Parameters");
            
            // Hook getSupportedPreviewSizes and similar methods
            for (String method : new String[]{"getSupportedPreviewSizes", 
                                            "getSupportedPictureSizes",
                                            "getSupportedVideoSizes"}) {
                try {
                    Method m = parametersClass.getDeclaredMethod(method);
                    Hooking.pineHook(m, new MethodHook() {
                        @Override
                        public void afterCall(Pine.CallFrame cf) {
                            // Check if this is a swapped camera
                            Object params = cf.thisObject;
                            // Parameters object is associated with a Camera
                            // This is complex, so we'll leave sizes as-is
                        }
                    });
                } catch (Exception ignore) {}
            }
        } catch (Exception e) {
            Log.w(TAG, "Parameter hooks failed", e);
        }
    }

    /* ====================================================== */
    /* Camera2: Keep front camera visible but use back        */
    /* ====================================================== */
    private static void hookCamera2() {
        try {
            // Don't hide cameras in getCameraIdList
            Hooking.pineHook(CameraManager.class.getDeclaredMethod("getCameraIdList"),
                new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame cf) {
                        String[] ids = (String[]) cf.getResult();
                        // Keep all camera IDs - don't hide any
                        if (ids != null && ids.length < 2) {
                            // Ensure both cameras appear
                            cf.setResult(new String[]{sBackCameraId, sFrontCameraId});
                        }
                    }
                });

            // Keep camera characteristics reporting correct info
            // This allows apps to detect front camera
            Hooking.pineHook(CameraManager.class.getDeclaredMethod("getCameraCharacteristics", String.class),
                new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame cf) {
                        // Don't modify characteristics
                        // Let apps see that front camera exists
                    }
                });

            // Only swap when actually opening the camera
            Method openCamera = CameraManager.class.getDeclaredMethod("openCamera", 
                String.class, CameraDevice.StateCallback.class, android.os.Handler.class);
                
            Hooking.pineHook(openCamera, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    String cameraId = (String) cf.args[0];
                    if (cameraId.equals(sFrontCameraId)) {
                        Log.i(TAG, "openCamera(front) -> using back camera instead");
                        cf.args[0] = sBackCameraId;
                        CAMERA2_MAP.put(sBackCameraId, sFrontCameraId);
                    }
                }
            });

            // Alternative openCamera method signatures
            try {
                Method openCamera2 = CameraManager.class.getDeclaredMethod("openCamera",
                    String.class, java.util.concurrent.Executor.class, 
                    CameraDevice.StateCallback.class);
                    
                Hooking.pineHook(openCamera2, new MethodHook() {
                    @Override
                    public void beforeCall(Pine.CallFrame cf) {
                        String cameraId = (String) cf.args[0];
                        if (cameraId.equals(sFrontCameraId)) {
                            cf.args[0] = sBackCameraId;
                        }
                    }
                });
            } catch (NoSuchMethodException ignore) {}

        } catch (Exception e) {
            Log.e(TAG, "Camera2 hooks failed", e);
        }
    }

    /* ====================================================== */
    /* CameraX: Keep front camera available but use back      */
    /* ====================================================== */
    private static void hookCameraX() {
        try {
            // Hook CameraSelector.Builder
            Class<?> cameraSelectorBuilder = Class.forName("androidx.camera.core.CameraSelector$Builder");
            Method requireLensFacing = cameraSelectorBuilder.getDeclaredMethod("requireLensFacing", int.class);
            
            Hooking.pineHook(requireLensFacing, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame cf) {
                    int facing = (int) cf.args[0];
                    if (facing == 0) { // LENS_FACING_FRONT
                        Log.i(TAG, "CameraX requireLensFacing(FRONT) -> BACK");
                        cf.args[0] = 1; // LENS_FACING_BACK
                    }
                }
            });

            // Hook hasCamera to always return true for front camera
            try {
                Class<?> cameraX = Class.forName("androidx.camera.lifecycle.ProcessCameraProvider");
                Method hasCamera = cameraX.getDeclaredMethod("hasCamera", 
                    Class.forName("androidx.camera.core.CameraSelector"));
                    
                Hooking.pineHook(hasCamera, new MethodHook() {
                    @Override
                    public void afterCall(Pine.CallFrame cf) {
                        // Always return true - cameras are available
                        cf.setResult(true);
                    }
                });
            } catch (Exception ignore) {}
            
        } catch (Exception e) {
            Log.w(TAG, "CameraX hooks skipped (app might not use CameraX)");
        }
    }
}
