package com.applisto.appcloner;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;

/**
 * Hook that intercepts the app launch and redirects to a fake calculator activity.
 * The calculator requires a passcode (default: "1234=") to access the real app.
 */
public class FakeCalculatorHook extends ExecStartActivityHook {
    private static final String TAG = "FakeCalculatorHook";
    private static final String PREF_NAME = "FakeCalculatorHookPrefs";
    private static final String PREF_ENABLED = "fake_calculator_enabled";
    private static final String PREF_PASSCODE = "fake_calculator_passcode";
    private static final String PREF_UNLOCKED = "fake_calculator_unlocked";
    private static final String DEFAULT_PASSCODE = "1234";
    
    // Static instance for callback from FakeCalculatorActivity
    private static FakeCalculatorHook sInstance;
    
    private Context context;
    private boolean enabled;
    private String passcode;
    
    public FakeCalculatorHook(Context context) {
        this.context = context;
        sInstance = this;
        loadSettings();
    }
    
    /**
     * Get the static instance (used by FakeCalculatorActivity)
     */
    public static FakeCalculatorHook getInstance() {
        return sInstance;
    }
    
    /**
     * Called by FakeCalculatorActivity when correct passcode is entered.
     * Sets the unlocked flag so subsequent launches don't show the calculator.
     */
    public void onPasscodeVerified() {
        if (context != null) {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putBoolean(PREF_UNLOCKED, true).apply();
            Log.i(TAG, "Passcode verified, app unlocked");
        }
    }
    
    /**
     * Check if the app is currently unlocked (passcode was entered correctly)
     */
    private boolean isUnlocked() {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_UNLOCKED, false);
    }
    
    private void loadSettings() {
        try {
            // Try to load from ClonerSettings first
            ClonerSettings settings = ConfigLoader.loadSettings(context);
            if (settings != null) {
                enabled = settings.fakeCalculatorEnabled();
                passcode = settings.fakeCalculatorPasscode();
                
                if (passcode == null || passcode.trim().isEmpty()) {
                    passcode = DEFAULT_PASSCODE;
                }
                
                Log.i(TAG, "Settings loaded from ClonerSettings: enabled=" + enabled);
                return;
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not load from ClonerSettings", e);
        }
        
        // Fallback to SharedPreferences
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(PREF_ENABLED, false);
        passcode = prefs.getString(PREF_PASSCODE, DEFAULT_PASSCODE);
        
        Log.i(TAG, "Settings loaded from SharedPreferences: enabled=" + enabled);
    }
    
    @Override
    protected boolean onExecStartActivity(ExecStartActivityArgs args) throws ActivityNotFoundException {
        // Only intercept if enabled
        if (!enabled) {
            return true; // Allow the activity to start normally
        }
        
        // If app is already unlocked, allow normal operation
        if (isUnlocked()) {
            Log.d(TAG, "App is unlocked, allowing activity start");
            return true;
        }
        
        Intent intent = args.intent;
        if (intent == null) {
            return true;
        }
        
        // Check if this is the calculator activity itself - don't intercept it
        String targetClass = null;
        if (intent.getComponent() != null) {
            targetClass = intent.getComponent().getClassName();
        }
        
        if (targetClass != null && targetClass.equals(FakeCalculatorActivity.class.getName())) {
            return true; // Don't intercept the calculator itself
        }
        
        // Check if this is a launcher intent (app being launched from home screen)
        // or the main activity of the app
        boolean isLauncherIntent = Intent.ACTION_MAIN.equals(intent.getAction()) && 
            intent.hasCategory(Intent.CATEGORY_LAUNCHER);
        boolean isMainActivity = intent.getComponent() != null && 
            isMainActivityOfApp(intent.getComponent().getClassName());
        
        if (isLauncherIntent || isMainActivity) {
            // Redirect to calculator
            Log.i(TAG, "Intercepting app launch, redirecting to calculator");
            
            // Create intent to launch calculator
            Intent calculatorIntent = new Intent(args.who, FakeCalculatorActivity.class);
            calculatorIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            
            // Pass information about the target app
            calculatorIntent.putExtra("target_package", context.getPackageName());
            if (intent.getComponent() != null) {
                calculatorIntent.putExtra("target_activity", intent.getComponent().getClassName());
            }
            
            try {
                context.startActivity(calculatorIntent);
                // Suppress the original intent
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Failed to launch calculator activity", e);
                // If we can't launch calculator, allow the original intent
                return true;
            }
        }
        
        // Allow all other activities to start normally
        return true;
    }
    
    /**
     * Check if the class name is the main activity of this app
     */
    private boolean isMainActivityOfApp(String className) {
        if (className == null) return false;
        // Check if it's in the same package and is likely a main activity
        String packageName = context.getPackageName();
        return className.startsWith(packageName) && 
               (className.contains("MainActivity") || className.contains("LauncherActivity"));
    }
    
    /**
     * Reset the unlock flag so calculator will be shown again on next launch
     */
    public static void resetLaunchFlag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_UNLOCKED, false).apply();
    }
    
    /**
     * Lock the app (requires passcode again)
     */
    public static void lockApp(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_UNLOCKED, false).apply();
    }
    
    /**
     * Enable or disable the fake calculator
     */
    public static void setEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
    }
    
    /**
     * Set the passcode for the fake calculator
     */
    public static void setPasscode(Context context, String passcode) {
        if (passcode == null || passcode.trim().isEmpty()) {
            passcode = DEFAULT_PASSCODE;
        }
        
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(PREF_PASSCODE, passcode).apply();
        
        // Also update in FakeCalculatorActivity
        FakeCalculatorActivity.setPasscode(context, passcode);
    }
    
    /**
     * Get current settings
     */
    public static boolean isEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(PREF_ENABLED, false);
    }
    
    public static String getPasscode(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(PREF_PASSCODE, DEFAULT_PASSCODE);
    }
}
