package com.applisto.appcloner;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * BackgroundMediaHook - Enables media playback to continue when app goes to background.
 * 
 * This hook prevents media from being paused when the app loses focus or goes to background.
 * It intercepts various media-related APIs including:
 * - WebView (video/audio playback)
 * - Android MediaPlayer
 * - ExoPlayer (Google's media player library)
 * - Media3 (AndroidX media library)
 * - Audio focus management
 * 
 * Configuration (cloner.json):
 * - background_media: boolean (default: false) - Enable/disable background media playback
 * - background_media_webview: boolean (default: true) - Enable WebView background playback
 * - background_media_mediaplayer: boolean (default: true) - Enable MediaPlayer background playback
 * - background_media_exoplayer: boolean (default: true) - Enable ExoPlayer background playback
 * - background_media_audio_focus: boolean (default: true) - Enable audio focus management
 * 
 * Usage:
 * This hook is automatically initialized by DefaultProvider if background_media is enabled
 * in cloner.json. It tracks app foreground/background state and prevents media pause
 * operations when the app is in the background.
 */
public final class BackgroundMediaHook {
    private static final String TAG = "BackgroundMediaHook";

    private static final AtomicBoolean INSTALLED = new AtomicBoolean(false);
    private static final AtomicInteger startedActivities = new AtomicInteger(0);
    private static volatile boolean appInBackground = false;
    private static volatile boolean enabled = false;
    
    // Configuration flags for selective hooking
    private static boolean hookWebView = true;
    private static boolean hookMediaPlayer = true;
    private static boolean hookExoPlayer = true;
    private static boolean hookAudioFocus = true;

    /**
     * Initialize the background media hook.
     * @param context Application context
     */
    public void init(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot initialize BackgroundMediaHook");
            return;
        }
        
        // Load configuration
        loadSettings(context);
        
        if (!enabled) {
            Log.i(TAG, "Background media: disabled via config");
            return;
        }
        
        if (!INSTALLED.compareAndSet(false, true)) {
            Log.i(TAG, "Background media: already installed");
            return;
        }

        Log.i(TAG, "Installing background media hooks...");
        
        // Track app foreground/background state
        trackAppForegroundState(context.getApplicationContext());
        
        // Hook media components based on configuration
        if (hookWebView) {
            hookWebViewConstructorsAndVisibility();
            hookWebViewPauseAPIs();
        }
        
        if (hookMediaPlayer) {
            hookMediaPlayer();
        }
        
        if (hookExoPlayer) {
            hookExoAndMedia3();
        }
        
        if (hookAudioFocus) {
            hookAudioFocus();
        }
        
        Log.i(TAG, "Background media hooks installed successfully");
    }
    
    /**
     * Load settings from ClonerSettings/cloner.json
     */
    private void loadSettings(Context context) {
        try {
            ClonerSettings settings = ClonerSettings.get(context);
            org.json.JSONObject cfg = settings.raw();
            
            enabled = cfg.optBoolean("background_media", false);
            hookWebView = cfg.optBoolean("background_media_webview", true);
            hookMediaPlayer = cfg.optBoolean("background_media_mediaplayer", true);
            hookExoPlayer = cfg.optBoolean("background_media_exoplayer", true);
            hookAudioFocus = cfg.optBoolean("background_media_audio_focus", true);
            
            Log.d(TAG, "BackgroundMediaHook config: enabled=" + enabled + 
                  ", webview=" + hookWebView + ", mediaplayer=" + hookMediaPlayer +
                  ", exoplayer=" + hookExoPlayer + ", audiofocus=" + hookAudioFocus);
        } catch (Throwable t) {
            enabled = false;
            Log.w(TAG, "Failed to load background media settings", t);
        }
    }

    /* ==================== Foreground/Background Tracking ==================== */
    
    /**
     * Track app foreground/background state using ActivityLifecycleCallbacks.
     */
    private void trackAppForegroundState(Context appCtx) {
        try {
            Application app = (Application) appCtx.getApplicationContext();
            app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override 
                public void onActivityStarted(Activity activity) {
                    int count = startedActivities.incrementAndGet();
                    if (count > 0) {
                        appInBackground = false;
                        Log.d(TAG, "App in foreground (activities: " + count + ")");
                    }
                }
                
                @Override 
                public void onActivityStopped(Activity activity) {
                    int count = startedActivities.decrementAndGet();
                    if (count < 0) {
                        startedActivities.set(0);
                        count = 0;
                    }
                    appInBackground = (count == 0);
                    if (appInBackground) {
                        Log.d(TAG, "App in background");
                    }
                }
                
                @Override public void onActivityCreated(Activity a, Bundle b) {}
                @Override public void onActivityResumed(Activity a) {}
                @Override public void onActivityPaused(Activity a) {}
                @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
                @Override public void onActivityDestroyed(Activity a) {}
            });
            Log.d(TAG, "Activity lifecycle tracking registered");
        } catch (Throwable t) {
            Log.w(TAG, "Lifecycle tracking failed; background detection may be inaccurate", t);
        }
    }

    /* ==================== WebView Hooks ==================== */
    
    /**
     * Hook WebView constructors and visibility methods.
     */
    private void hookWebViewConstructorsAndVisibility() {
        // Hook WebView(Context) constructor
        hookWebViewConstructor(Context.class);
        
        // Hook WebView(Context, AttributeSet) constructor
        hookWebViewConstructor(Context.class, AttributeSet.class);
        
        // Hook WebView(Context, AttributeSet, int) constructor
        hookWebViewConstructor(Context.class, AttributeSet.class, int.class);
        
        // Fallback: configure when attached to window
        try {
            Method onAttach = View.class.getDeclaredMethod("onAttachedToWindow");
            Hooking.pineHook(onAttach, new MethodHook() {
                @Override 
                public void afterCall(Pine.CallFrame frame) {
                    if (frame.thisObject instanceof WebView) {
                        configureWebView((WebView) frame.thisObject);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "View.onAttachedToWindow hook failed", t);
        }

        // Hook onWindowVisibilityChanged to prevent background pauses
        try {
            Method onWvc = WebView.class.getDeclaredMethod("onWindowVisibilityChanged", int.class);
            Hooking.pineHook(onWvc, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        // Block visibility change that would pause playback
                        frame.setResult(null);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "WebView.onWindowVisibilityChanged hook failed", t);
        }

        // Hook View.setVisibility to prevent WebView from being hidden
        try {
            Method setVis = View.class.getDeclaredMethod("setVisibility", int.class);
            Hooking.pineHook(setVis, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground && frame.thisObject instanceof WebView) {
                        int newVisibility = (Integer) frame.args[0];
                        if (newVisibility != View.VISIBLE) {
                            // Block attempts to hide WebView in background
                            frame.setResult(null);
                        }
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "View.setVisibility hook failed", t);
        }
    }
    
    /**
     * Hook a specific WebView constructor.
     */
    private void hookWebViewConstructor(Class<?>... paramTypes) {
        try {
            Constructor<WebView> ctor = WebView.class.getDeclaredConstructor(paramTypes);
            Hooking.pineHook(ctor, new MethodHook() {
                @Override 
                public void afterCall(Pine.CallFrame frame) {
                    configureWebView((WebView) frame.thisObject);
                }
            });
        } catch (NoSuchMethodException e) {
            // Constructor doesn't exist, skip
        } catch (Throwable t) {
            Log.w(TAG, "WebView constructor hook failed for params: " + java.util.Arrays.toString(paramTypes), t);
        }
    }

    /**
     * Configure WebView for background media playback.
     */
    private void configureWebView(WebView wv) {
        if (wv == null) return;
        
        try {
            WebSettings settings = wv.getSettings();
            
            // Allow media playback without user gesture
            try { 
                settings.setMediaPlaybackRequiresUserGesture(false); 
            } catch (Throwable ignored) {}
            
            // Set renderer priority to keep it running in background (API 26+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { 
                    // RENDERER_PRIORITY_IMPORTANT = 2, waive when not visible = true
                    wv.setRendererPriorityPolicy(2, true); 
                } catch (Throwable ignored) {}
            }
            
            Log.d(TAG, "Configured WebView for background media");
        } catch (Throwable t) {
            Log.w(TAG, "configureWebView failed", t);
        }
    }

    /**
     * Hook WebView pause APIs to prevent pausing in background.
     */
    private void hookWebViewPauseAPIs() {
        // Hook WebView.onPause
        try {
            Method onPause = WebView.class.getDeclaredMethod("onPause");
            Hooking.pineHook(onPause, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        frame.setResult(null);
                        Log.d(TAG, "Blocked WebView.onPause in background");
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "WebView.onPause hook failed", t);
        }
        
        // Hook WebView.pauseTimers
        try {
            Method pauseTimers = WebView.class.getDeclaredMethod("pauseTimers");
            Hooking.pineHook(pauseTimers, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        frame.setResult(null);
                        Log.d(TAG, "Blocked WebView.pauseTimers in background");
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "WebView.pauseTimers hook failed", t);
        }
    }

    /* ==================== MediaPlayer Hooks ==================== */
    
    /**
     * Hook Android MediaPlayer to prevent pause/stop in background.
     */
    private void hookMediaPlayer() {
        Class<?> mediaPlayerClass = android.media.MediaPlayer.class;
        
        // Hook pause()
        hookMediaPlayerMethod(mediaPlayerClass, "pause");
        
        // Hook stop()
        hookMediaPlayerMethod(mediaPlayerClass, "stop");
        
        // Hook release() - be more careful with this one
        try {
            Method release = mediaPlayerClass.getDeclaredMethod("release");
            Hooking.pineHook(release, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        // Only log but allow release to proceed to prevent memory leaks
                        Log.d(TAG, "MediaPlayer.release called in background");
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "MediaPlayer.release hook failed", t);
        }
    }
    
    /**
     * Hook a MediaPlayer method to block it in background.
     */
    private void hookMediaPlayerMethod(Class<?> mpClass, String methodName) {
        try {
            Method method = mpClass.getDeclaredMethod(methodName);
            Hooking.pineHook(method, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        frame.setResult(null);
                        Log.d(TAG, "Blocked MediaPlayer." + methodName + " in background");
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "MediaPlayer." + methodName + " hook failed", t);
        }
    }

    /* ==================== ExoPlayer / Media3 Hooks ==================== */
    
    /**
     * Hook ExoPlayer and Media3 player implementations.
     */
    private void hookExoAndMedia3() {
        // Player interface classes to hook
        String[] playerClasses = {
            "com.google.android.exoplayer2.Player",
            "com.google.android.exoplayer2.ExoPlayer",
            "com.google.android.exoplayer2.SimpleExoPlayer",
            "androidx.media3.common.Player",
            "androidx.media3.exoplayer.ExoPlayer"
        };
        
        // Methods to hook on player interfaces
        String[][] methodsToHook = {
            {"setPlayWhenReady", "boolean"},  // setPlayWhenReady(boolean)
            {"play"},                          // play()
            {"pause"},                         // pause()
            {"stop"},                          // stop()
            {"stop", "boolean"}                // stop(boolean) - deprecated
        };
        
        for (String className : playerClasses) {
            try {
                Class<?> playerClass = Class.forName(className);
                
                for (String[] methodDef : methodsToHook) {
                    hookPlayerMethod(playerClass, methodDef);
                }
                
                Log.d(TAG, "Hooked player class: " + className);
            } catch (ClassNotFoundException e) {
                // Class not available in this app, skip silently
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook player class: " + className, t);
            }
        }
    }
    
    /**
     * Hook a player method.
     */
    private void hookPlayerMethod(Class<?> playerClass, String[] methodDef) {
        String methodName = methodDef[0];
        
        try {
            Method method;
            if (methodDef.length == 2 && "boolean".equals(methodDef[1])) {
                method = playerClass.getDeclaredMethod(methodName, boolean.class);
            } else {
                method = playerClass.getDeclaredMethod(methodName);
            }
            
            Hooking.pineHook(method, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (!enabled || !appInBackground) return;
                    
                    String name = frame.method.getName();
                    switch (name) {
                        case "setPlayWhenReady":
                            // If trying to set playWhenReady to false, force it to true
                            if (frame.args != null && frame.args.length == 1 && 
                                frame.args[0] instanceof Boolean) {
                                if (!((Boolean) frame.args[0])) {
                                    frame.args[0] = true;
                                    Log.d(TAG, "Forced setPlayWhenReady(true) in background");
                                }
                            }
                            break;
                            
                        case "pause":
                        case "stop":
                            // Block pause/stop in background
                            frame.setResult(null);
                            Log.d(TAG, "Blocked Player." + name + " in background");
                            break;
                    }
                }
            });
            
            Log.d(TAG, "Hooked " + playerClass.getSimpleName() + "#" + methodName);
        } catch (NoSuchMethodException e) {
            // Method doesn't exist on this interface, skip
        } catch (Throwable t) {
            Log.w(TAG, "Hook failed for " + playerClass.getSimpleName() + "#" + methodName, t);
        }
    }

    /* ==================== Audio Focus Hooks ==================== */
    
    /**
     * Hook audio focus management to keep audio playing in background.
     */
    private void hookAudioFocus() {
        // Hook legacy requestAudioFocus
        hookLegacyAudioFocus();
        
        // Hook API 26+ audio focus methods
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            hookModernAudioFocus();
        }
    }
    
    /**
     * Hook legacy audio focus methods (pre-API 26).
     */
    private void hookLegacyAudioFocus() {
        // Hook requestAudioFocus(listener, streamType, durationHint)
        try {
            Method requestFocus = AudioManager.class.getDeclaredMethod(
                "requestAudioFocus",
                AudioManager.OnAudioFocusChangeListener.class, 
                int.class, 
                int.class
            );
            Hooking.pineHook(requestFocus, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        // Always grant audio focus in background
                        frame.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "requestAudioFocus (legacy) hook failed", t);
        }

        // Hook abandonAudioFocus(listener)
        try {
            Method abandonFocus = AudioManager.class.getDeclaredMethod(
                "abandonAudioFocus", 
                AudioManager.OnAudioFocusChangeListener.class
            );
            Hooking.pineHook(abandonFocus, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        // Pretend we successfully abandoned focus but don't actually do it
                        frame.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "abandonAudioFocus (legacy) hook failed", t);
        }
    }
    
    /**
     * Hook modern audio focus methods (API 26+).
     */
    private void hookModernAudioFocus() {
        // Hook requestAudioFocus(AudioFocusRequest)
        try {
            Method requestFocus = AudioManager.class.getDeclaredMethod(
                "requestAudioFocus", 
                AudioFocusRequest.class
            );
            Hooking.pineHook(requestFocus, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        frame.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "requestAudioFocus(AudioFocusRequest) hook failed", t);
        }

        // Hook abandonAudioFocusRequest(AudioFocusRequest)
        try {
            Method abandonFocus = AudioManager.class.getDeclaredMethod(
                "abandonAudioFocusRequest", 
                AudioFocusRequest.class
            );
            Hooking.pineHook(abandonFocus, new MethodHook() {
                @Override 
                public void beforeCall(Pine.CallFrame frame) {
                    if (enabled && appInBackground) {
                        frame.setResult(AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                    }
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "abandonAudioFocusRequest hook failed", t);
        }
    }

    /* ==================== Public API ==================== */
    
    /**
     * Enable or disable background media at runtime.
     */
    public static void setEnabled(boolean enable) {
        enabled = enable;
        Log.i(TAG, "Background media " + (enable ? "enabled" : "disabled"));
    }
    
    /**
     * Check if background media is enabled.
     */
    public static boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Check if app is currently in background.
     */
    public static boolean isAppInBackground() {
        return appInBackground;
    }
    
    /**
     * Get the number of started activities.
     */
    public static int getStartedActivityCount() {
        return startedActivities.get();
    }
}
