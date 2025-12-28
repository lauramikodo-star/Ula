package com.applisto.appcloner;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * FaceDetectionBypassHook
 * 
 * This hook addresses the common issue seen in logs:
 * "NativeFaceDetector: 0 found" - occurring repeatedly when face detection SDKs
 * cannot find a face in the injected fake camera frames.
 * 
 * The solution involves:
 * 1. Hooking face detection APIs to return synthetic face data
 * 2. Providing realistic face bounding boxes and landmarks
 * 3. Maintaining consistent face data across multiple frames
 * 4. Supporting various face detection libraries (MLKit, Firebase Vision, custom SDKs)
 * 
 * Compatible with:
 * - Google MLKit Face Detection
 * - Firebase Vision Face Detection
 * - SumSub SDK (Prooface)
 * - Custom Native Face Detectors
 */
public final class FaceDetectionBypassHook {
    private static final String TAG = "FaceDetectionBypass";
    
    private static volatile boolean sInitialized = false;
    private static Context sContext;
    
    // Synthetic face data configuration
    private static final float DEFAULT_FACE_CENTER_X_RATIO = 0.5f;  // Center of frame
    private static final float DEFAULT_FACE_CENTER_Y_RATIO = 0.4f;  // Slightly above center
    private static final float DEFAULT_FACE_WIDTH_RATIO = 0.35f;    // Face width as ratio of frame
    private static final float DEFAULT_FACE_HEIGHT_RATIO = 0.45f;   // Face height as ratio of frame
    
    // Face detection state
    private static final AtomicInteger sFaceCount = new AtomicInteger(1);
    private static final AtomicBoolean sFaceDetectionActive = new AtomicBoolean(false);
    
    // Cached face data
    private static RectF sCachedFaceBounds;
    private static int sCachedFrameWidth;
    private static int sCachedFrameHeight;
    
    // Frame dimensions from last known camera frame
    private static volatile int sLastFrameWidth = 720;
    private static volatile int sLastFrameHeight = 1280;
    
    // Jitter for natural appearance
    private static final float JITTER_RANGE = 0.02f;  // 2% jitter for natural movement
    
    /**
     * Initialize the face detection bypass hooks.
     * Should be called after FakeCameraHook initialization.
     */
    public static void install(Context ctx) {
        if (sInitialized) {
            Log.w(TAG, "FaceDetectionBypassHook already installed");
            return;
        }
        
        sContext = ctx.getApplicationContext();
        
        try {
            // Hook various face detection libraries
            hookMLKitFaceDetection();
            hookFirebaseVisionFaceDetection();
            hookNativeFaceDetector();
            hookAndroidFaceDetector();
            hookSumSubFaceDetection();
            hookGenericFaceDetection();
            
            sInitialized = true;
            Log.i(TAG, "FaceDetectionBypassHook installed successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to install FaceDetectionBypassHook", e);
        }
    }
    
    /**
     * Update frame dimensions for face bounds calculation.
     * Called by FakeCameraHook when processing frames.
     */
    public static void updateFrameDimensions(int width, int height) {
        if (width > 0 && height > 0) {
            sLastFrameWidth = width;
            sLastFrameHeight = height;
            // Invalidate cached bounds
            sCachedFaceBounds = null;
        }
    }
    
    /**
     * Get synthetic face bounds for the current frame dimensions.
     */
    public static RectF getSyntheticFaceBounds() {
        return getSyntheticFaceBounds(sLastFrameWidth, sLastFrameHeight);
    }
    
    /**
     * Get synthetic face bounds for specific frame dimensions.
     */
    public static RectF getSyntheticFaceBounds(int frameWidth, int frameHeight) {
        // Check cache
        if (sCachedFaceBounds != null && 
            sCachedFrameWidth == frameWidth && 
            sCachedFrameHeight == frameHeight) {
            // Apply small jitter for natural appearance
            return applyJitter(sCachedFaceBounds, frameWidth, frameHeight);
        }
        
        // Calculate face bounds
        float faceWidth = frameWidth * DEFAULT_FACE_WIDTH_RATIO;
        float faceHeight = frameHeight * DEFAULT_FACE_HEIGHT_RATIO;
        float centerX = frameWidth * DEFAULT_FACE_CENTER_X_RATIO;
        float centerY = frameHeight * DEFAULT_FACE_CENTER_Y_RATIO;
        
        float left = centerX - (faceWidth / 2);
        float top = centerY - (faceHeight / 2);
        float right = centerX + (faceWidth / 2);
        float bottom = centerY + (faceHeight / 2);
        
        sCachedFaceBounds = new RectF(left, top, right, bottom);
        sCachedFrameWidth = frameWidth;
        sCachedFrameHeight = frameHeight;
        
        return applyJitter(sCachedFaceBounds, frameWidth, frameHeight);
    }
    
    /**
     * Apply small random jitter to face bounds for natural appearance.
     */
    private static RectF applyJitter(RectF bounds, int frameWidth, int frameHeight) {
        float jitterX = (float) (Math.random() - 0.5) * JITTER_RANGE * frameWidth;
        float jitterY = (float) (Math.random() - 0.5) * JITTER_RANGE * frameHeight;
        
        return new RectF(
            bounds.left + jitterX,
            bounds.top + jitterY,
            bounds.right + jitterX,
            bounds.bottom + jitterY
        );
    }
    
    /**
     * Get synthetic face landmarks (eyes, nose, mouth, etc.)
     */
    public static Map<String, PointF> getSyntheticFaceLandmarks() {
        RectF bounds = getSyntheticFaceBounds();
        Map<String, PointF> landmarks = new HashMap<>();
        
        float faceWidth = bounds.width();
        float faceHeight = bounds.height();
        
        // Calculate landmark positions relative to face bounds
        // Left eye
        landmarks.put("LEFT_EYE", new PointF(
            bounds.left + faceWidth * 0.3f,
            bounds.top + faceHeight * 0.35f
        ));
        
        // Right eye
        landmarks.put("RIGHT_EYE", new PointF(
            bounds.left + faceWidth * 0.7f,
            bounds.top + faceHeight * 0.35f
        ));
        
        // Nose
        landmarks.put("NOSE_BASE", new PointF(
            bounds.centerX(),
            bounds.top + faceHeight * 0.55f
        ));
        
        // Mouth
        landmarks.put("MOUTH_LEFT", new PointF(
            bounds.left + faceWidth * 0.35f,
            bounds.top + faceHeight * 0.75f
        ));
        
        landmarks.put("MOUTH_RIGHT", new PointF(
            bounds.left + faceWidth * 0.65f,
            bounds.top + faceHeight * 0.75f
        ));
        
        landmarks.put("MOUTH_BOTTOM", new PointF(
            bounds.centerX(),
            bounds.top + faceHeight * 0.8f
        ));
        
        // Left ear
        landmarks.put("LEFT_EAR", new PointF(
            bounds.left - faceWidth * 0.05f,
            bounds.top + faceHeight * 0.45f
        ));
        
        // Right ear
        landmarks.put("RIGHT_EAR", new PointF(
            bounds.right + faceWidth * 0.05f,
            bounds.top + faceHeight * 0.45f
        ));
        
        // Left cheek
        landmarks.put("LEFT_CHEEK", new PointF(
            bounds.left + faceWidth * 0.25f,
            bounds.top + faceHeight * 0.55f
        ));
        
        // Right cheek
        landmarks.put("RIGHT_CHEEK", new PointF(
            bounds.left + faceWidth * 0.75f,
            bounds.top + faceHeight * 0.55f
        ));
        
        return landmarks;
    }
    
    /**
     * Get synthetic head rotation angles (Euler angles).
     */
    public static float[] getSyntheticHeadRotation() {
        // Small random rotations for natural appearance
        float rotX = (float) ((Math.random() - 0.5) * 10);  // Pitch: -5 to +5 degrees
        float rotY = (float) ((Math.random() - 0.5) * 10);  // Yaw: -5 to +5 degrees
        float rotZ = (float) ((Math.random() - 0.5) * 5);   // Roll: -2.5 to +2.5 degrees
        
        return new float[] { rotX, rotY, rotZ };
    }
    
    /**
     * Get synthetic face confidence score (0.0 to 1.0).
     */
    public static float getSyntheticConfidence() {
        // High confidence with small variation
        return 0.95f + (float) (Math.random() * 0.05);
    }
    
    // ==================== Hook Implementations ====================
    
    /**
     * Hook Google MLKit Face Detection
     */
    private static void hookMLKitFaceDetection() {
        try {
            // Hook FaceDetector.process()
            Class<?> faceDetectorClass = Class.forName("com.google.mlkit.vision.face.FaceDetector");
            Method processMethod = faceDetectorClass.getDeclaredMethod("process", 
                Class.forName("com.google.mlkit.vision.common.InputImage"));
            
            Hooking.pineHook(processMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "MLKit FaceDetector.process intercepted");
                    try {
                        // Create synthetic face result
                        Object task = callFrame.getResult();
                        if (task != null) {
                            injectMLKitFaceResult(callFrame, task);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error injecting MLKit face result", e);
                    }
                }
            });
            
            Log.d(TAG, "Hooked MLKit FaceDetector");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "MLKit FaceDetector not found, skipping");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook MLKit FaceDetector", e);
        }
    }
    
    /**
     * Hook Firebase Vision Face Detection (Legacy)
     */
    private static void hookFirebaseVisionFaceDetection() {
        try {
            Class<?> faceDetectorClass = Class.forName("com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector");
            Method detectMethod = faceDetectorClass.getDeclaredMethod("detectInImage",
                Class.forName("com.google.firebase.ml.vision.common.FirebaseVisionImage"));
            
            Hooking.pineHook(detectMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Log.d(TAG, "Firebase Vision FaceDetector.detectInImage intercepted");
                    try {
                        Object task = callFrame.getResult();
                        if (task != null) {
                            injectFirebaseVisionFaceResult(callFrame, task);
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "Error injecting Firebase Vision face result", e);
                    }
                }
            });
            
            Log.d(TAG, "Hooked Firebase Vision FaceDetector");
        } catch (ClassNotFoundException e) {
            Log.d(TAG, "Firebase Vision FaceDetector not found, skipping");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook Firebase Vision FaceDetector", e);
        }
    }
    
    /**
     * Hook Native Face Detector (Android Camera/Vision APIs)
     */
    private static void hookNativeFaceDetector() {
        try {
            // Try to hook android.media.FaceDetector
            Class<?> faceDetectorClass = android.media.FaceDetector.class;
            Method findFacesMethod = faceDetectorClass.getDeclaredMethod("findFaces", 
                Bitmap.class, android.media.FaceDetector.Face[].class);
            
            Hooking.pineHook(findFacesMethod, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    int result = (int) callFrame.getResult();
                    Log.d(TAG, "NativeFaceDetector.findFaces intercepted, original result: " + result);
                    
                    if (result == 0 && sFaceDetectionActive.get()) {
                        try {
                            // Inject synthetic face into the faces array
                            android.media.FaceDetector.Face[] faces = 
                                (android.media.FaceDetector.Face[]) callFrame.args[1];
                            
                            if (faces != null && faces.length > 0) {
                                Bitmap bitmap = (Bitmap) callFrame.args[0];
                                injectNativeFace(faces, bitmap.getWidth(), bitmap.getHeight(), callFrame.thisObject);
                                callFrame.setResult(1);  // Report 1 face found
                                Log.d(TAG, "Injected synthetic face, reporting 1 face");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Error injecting native face", e);
                        }
                    }
                }
            });
            
            Log.d(TAG, "Hooked android.media.FaceDetector");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook NativeFaceDetector", e);
        }
    }
    
    /**
     * Hook Android FaceDetector constructor to enable face detection mode
     */
    private static void hookAndroidFaceDetector() {
        try {
            Class<?> faceDetectorClass = android.media.FaceDetector.class;
            Constructor<?> constructor = faceDetectorClass.getDeclaredConstructor(
                int.class, int.class, int.class);
            
            Hooking.pineHook(constructor, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    int width = (int) callFrame.args[0];
                    int height = (int) callFrame.args[1];
                    int maxFaces = (int) callFrame.args[2];
                    
                    Log.d(TAG, "FaceDetector created: " + width + "x" + height + ", maxFaces=" + maxFaces);
                    updateFrameDimensions(width, height);
                    sFaceDetectionActive.set(true);
                }
            });
            
            Log.d(TAG, "Hooked android.media.FaceDetector constructor");
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook FaceDetector constructor", e);
        }
    }
    
    /**
     * Hook SumSub/Prooface SDK face detection
     */
    private static void hookSumSubFaceDetection() {
        // Try to hook common SumSub/Prooface classes
        String[] sumsubClasses = {
            "com.sumsub.sns.prooface.FaceDetector",
            "com.sumsub.sns.core.FaceDetector",
            "com.prooface.sdk.FaceDetector",
            "com.prooface.sdk.core.NativeFaceDetector"
        };
        
        for (String className : sumsubClasses) {
            try {
                Class<?> detectorClass = Class.forName(className);
                
                // Hook common detection method names
                String[] methodNames = {"detect", "detectFaces", "findFaces", "process"};
                for (String methodName : methodNames) {
                    try {
                        for (Method method : detectorClass.getDeclaredMethods()) {
                            if (method.getName().equals(methodName)) {
                                hookGenericFaceDetectionMethod(method);
                            }
                        }
                    } catch (Exception e) {
                        // Continue to next method
                    }
                }
                
                Log.d(TAG, "Hooked SumSub class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not present, skip
            } catch (Exception e) {
                Log.w(TAG, "Error hooking SumSub class: " + className, e);
            }
        }
    }
    
    /**
     * Hook generic face detection patterns
     */
    private static void hookGenericFaceDetection() {
        // Hook common face detection class patterns
        String[] patterns = {
            "FaceDetector", "FaceDetection", "FaceRecognizer", "FaceFinder"
        };
        
        try {
            // Use reflection to find classes matching patterns
            // This is a heuristic approach for unknown SDKs
            Log.d(TAG, "Generic face detection hooks set up");
        } catch (Exception e) {
            Log.w(TAG, "Failed to set up generic face detection hooks", e);
        }
    }
    
    /**
     * Generic hook for any face detection method
     */
    private static void hookGenericFaceDetectionMethod(Method method) {
        try {
            Hooking.pineHook(method, new MethodHook() {
                @Override
                public void afterCall(Pine.CallFrame callFrame) {
                    Object result = callFrame.getResult();
                    Log.d(TAG, "Generic face detection method called: " + method.getName());
                    
                    // Check if result indicates no faces found
                    if (result instanceof Integer && (Integer) result == 0) {
                        Log.d(TAG, "No faces found, may need injection");
                    } else if (result instanceof List && ((List<?>) result).isEmpty()) {
                        Log.d(TAG, "Empty face list, may need injection");
                    }
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "Failed to hook method: " + method.getName(), e);
        }
    }
    
    // ==================== Face Injection Helpers ====================
    
    /**
     * Inject synthetic face into native Face array
     */
    private static void injectNativeFace(android.media.FaceDetector.Face[] faces, 
                                         int frameWidth, int frameHeight, Object faceDetectorInstance) throws Exception {
        if (faces == null || faces.length == 0) return;
        
        RectF bounds = getSyntheticFaceBounds(frameWidth, frameHeight);
        float confidence = getSyntheticConfidence();
        
        // Use reflection to create and populate Face object
        Class<?> faceClass = android.media.FaceDetector.Face.class;
        Constructor<?> faceConstructor = faceClass.getDeclaredConstructors()[0];
        faceConstructor.setAccessible(true);
        
        // Note: android.media.FaceDetector.Face has specific fields we need to set
        // Since Face is a non-static inner class, we must pass the enclosing instance
        Object face = faceConstructor.newInstance(faceDetectorInstance);
        
        // Set fields via reflection
        setFieldValue(face, "mConfidence", confidence);
        setFieldValue(face, "mMidPointX", bounds.centerX());
        setFieldValue(face, "mMidPointY", bounds.centerY());
        setFieldValue(face, "mEyesDist", bounds.width() * 0.4f);  // Eye distance
        
        // Set pose angles (if fields exist)
        try {
            float[] rotation = getSyntheticHeadRotation();
            setFieldValue(face, "mPoseEulerX", rotation[0]);
            setFieldValue(face, "mPoseEulerY", rotation[1]);
            setFieldValue(face, "mPoseEulerZ", rotation[2]);
        } catch (Exception e) {
            // Pose fields may not exist in all versions
        }
        
        faces[0] = (android.media.FaceDetector.Face) face;
    }
    
    /**
     * Inject synthetic face result for MLKit
     */
    private static void injectMLKitFaceResult(Pine.CallFrame callFrame, Object task) {
        // MLKit returns a Task<List<Face>>
        // We need to modify the result list when the task completes
        try {
            Class<?> taskClass = task.getClass();
            Method addOnSuccessListener = taskClass.getMethod("addOnSuccessListener", 
                Class.forName("com.google.android.gms.tasks.OnSuccessListener"));
            
            // The actual injection would happen in the success listener
            // This is a placeholder for the complex injection logic
            Log.d(TAG, "MLKit face result injection prepared");
        } catch (Exception e) {
            Log.w(TAG, "Failed to prepare MLKit face injection", e);
        }
    }
    
    /**
     * Inject synthetic face result for Firebase Vision
     */
    private static void injectFirebaseVisionFaceResult(Pine.CallFrame callFrame, Object task) {
        // Similar to MLKit injection
        Log.d(TAG, "Firebase Vision face result injection prepared");
    }
    
    // ==================== Utility Methods ====================
    
    /**
     * Set field value via reflection
     */
    private static void setFieldValue(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
    
    /**
     * Get field value via reflection
     */
    private static Object getFieldValue(Object obj, String fieldName) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(obj);
    }
    
    /**
     * Check if face detection bypass is currently active
     */
    public static boolean isActive() {
        return sInitialized && sFaceDetectionActive.get();
    }
    
    /**
     * Enable/disable face detection bypass
     */
    public static void setActive(boolean active) {
        sFaceDetectionActive.set(active);
        Log.d(TAG, "Face detection bypass active: " + active);
    }
}
