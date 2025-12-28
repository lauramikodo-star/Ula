package com.applisto.appcloner;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import top.canyie.pine.Pine;
import top.canyie.pine.callback.MethodHook;
import com.applisto.appcloner.hooking.Hooking;

/**
 * Powerful Dialog Intercept and Blocker Hook
 * 
 * Features:
 * - Intercepts and blocks unwanted dialogs (update prompts, rating requests, ads, etc.)
 * - Keyword-based blocking for flexible configuration
 * - Auto-dismiss dialogs that match blocking criteria
 * - Can auto-click specific buttons (e.g., "Later", "Cancel", "No Thanks")
 * - Supports both AlertDialog and custom Dialog classes
 */
public final class DialogInterceptHook {
    private static final String TAG = "DialogInterceptHook";
    
    private static boolean sEnabled = false;
    private static boolean sBlockUpdateDialogs = true;
    private static boolean sBlockRatingDialogs = true;
    private static boolean sBlockAdDialogs = true;
    private static boolean sBlockSubscriptionDialogs = false;
    private static Set<String> sBlockKeywords = new HashSet<>();
    private static Set<String> sAutoClickButtons = new HashSet<>();
    
    // Default keywords for different dialog types
    private static final String[] UPDATE_KEYWORDS = {
        "update", "upgrade", "new version", "latest version", "play store",
        "app store", "download now", "install now", "update available"
    };
    
    private static final String[] RATING_KEYWORDS = {
        "rate", "review", "rating", "star", "feedback", "rate us",
        "rate this app", "leave a review", "how do you like", "enjoying"
    };
    
    private static final String[] AD_KEYWORDS = {
        "advertisement", "sponsored", "promoted", "ad", "watch video",
        "claim reward", "free coins", "daily reward", "spin to win"
    };
    
    private static final String[] SUBSCRIPTION_KEYWORDS = {
        "subscribe", "premium", "pro version", "unlock", "purchase",
        "buy now", "try free", "trial", "subscription", "upgrade to premium"
    };
    
    // Buttons to auto-click when dialog is blocked
    private static final String[] AUTO_CLICK_BUTTONS = {
        "later", "no thanks", "cancel", "dismiss", "skip", "not now",
        "maybe later", "close", "remind me later", "never", "no"
    };
    
    public void init(Context context) {
        Log.i(TAG, "Initializing Dialog Intercept Hook...");
        
        try {
            // Load settings from ClonerSettings
            ClonerSettings settings = ClonerSettings.get(context);
            sEnabled = settings.raw().optBoolean("dialog_blocker_enabled", false);
            
            if (!sEnabled) {
                Log.i(TAG, "Dialog blocker is disabled");
                return;
            }
            
            sBlockUpdateDialogs = settings.raw().optBoolean("block_update_dialogs", true);
            sBlockRatingDialogs = settings.raw().optBoolean("block_rating_dialogs", true);
            sBlockAdDialogs = settings.raw().optBoolean("block_ad_dialogs", true);
            sBlockSubscriptionDialogs = settings.raw().optBoolean("block_subscription_dialogs", false);
            
            // Parse custom keywords
            String customKeywords = settings.raw().optString("dialog_block_keywords", "");
            if (!customKeywords.isEmpty()) {
                for (String keyword : customKeywords.split(",")) {
                    String trimmed = keyword.trim().toLowerCase(Locale.US);
                    if (!trimmed.isEmpty()) {
                        sBlockKeywords.add(trimmed);
                    }
                }
            }
            
            // Add default keywords based on settings
            if (sBlockUpdateDialogs) {
                sBlockKeywords.addAll(Arrays.asList(UPDATE_KEYWORDS));
            }
            if (sBlockRatingDialogs) {
                sBlockKeywords.addAll(Arrays.asList(RATING_KEYWORDS));
            }
            if (sBlockAdDialogs) {
                sBlockKeywords.addAll(Arrays.asList(AD_KEYWORDS));
            }
            if (sBlockSubscriptionDialogs) {
                sBlockKeywords.addAll(Arrays.asList(SUBSCRIPTION_KEYWORDS));
            }
            
            // Add auto-click buttons
            sAutoClickButtons.addAll(Arrays.asList(AUTO_CLICK_BUTTONS));
            
            // Hook AlertDialog.show()
            hookAlertDialogShow();
            
            // Hook Dialog.show()
            hookDialogShow();
            
            // Hook DialogFragment.show() if available
            hookDialogFragmentShow();
            
            Log.i(TAG, "Dialog Intercept Hook initialized. Blocking keywords: " + sBlockKeywords.size());
            
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize Dialog Intercept Hook", t);
        }
    }
    
    private void hookAlertDialogShow() {
        try {
            Method showMethod = AlertDialog.class.getDeclaredMethod("show");
            Hooking.pineHook(showMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    AlertDialog dialog = (AlertDialog) callFrame.thisObject;
                    if (shouldBlockDialog(dialog)) {
                        Log.i(TAG, "Blocking AlertDialog");
                        // Try to auto-click a dismiss button
                        if (!tryAutoClickButton(dialog)) {
                            // If no button found, just dismiss
                            try {
                                dialog.dismiss();
                            } catch (Exception ignored) {}
                        }
                        // Prevent the dialog from showing
                        callFrame.setResult(null);
                    }
                }
            });
            Log.i(TAG, "✓ Hooked AlertDialog.show()");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook AlertDialog.show()", t);
        }
    }
    
    private void hookDialogShow() {
        try {
            Method showMethod = Dialog.class.getDeclaredMethod("show");
            Hooking.pineHook(showMethod, new MethodHook() {
                @Override
                public void beforeCall(Pine.CallFrame callFrame) {
                    Dialog dialog = (Dialog) callFrame.thisObject;
                    // Skip AlertDialog (already handled)
                    if (dialog instanceof AlertDialog) {
                        return;
                    }
                    if (shouldBlockDialog(dialog)) {
                        Log.i(TAG, "Blocking Dialog: " + dialog.getClass().getName());
                        try {
                            dialog.dismiss();
                        } catch (Exception ignored) {}
                        callFrame.setResult(null);
                    }
                }
            });
            Log.i(TAG, "✓ Hooked Dialog.show()");
        } catch (Throwable t) {
            Log.w(TAG, "Failed to hook Dialog.show()", t);
        }
    }
    
    private void hookDialogFragmentShow() {
        // Try both support library and AndroidX DialogFragment
        String[] fragmentClasses = {
            "androidx.fragment.app.DialogFragment",
            "android.support.v4.app.DialogFragment",
            "android.app.DialogFragment"
        };
        
        for (String className : fragmentClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                // Hook show(FragmentManager, String)
                for (Method m : clazz.getDeclaredMethods()) {
                    if ("show".equals(m.getName())) {
                        Hooking.pineHook(m, new MethodHook() {
                            @Override
                            public void beforeCall(Pine.CallFrame callFrame) {
                                Object fragment = callFrame.thisObject;
                                if (shouldBlockDialogFragment(fragment)) {
                                    Log.i(TAG, "Blocking DialogFragment: " + fragment.getClass().getName());
                                    // Cancel the show by setting result
                                    callFrame.setResult(null);
                                }
                            }
                        });
                    }
                }
                Log.i(TAG, "✓ Hooked " + className + ".show()");
            } catch (ClassNotFoundException ignored) {
                // Class not available in this app
            } catch (Throwable t) {
                Log.w(TAG, "Failed to hook " + className, t);
            }
        }
    }
    
    /**
     * Check if a dialog should be blocked based on its content
     */
    private boolean shouldBlockDialog(Dialog dialog) {
        if (!sEnabled || sBlockKeywords.isEmpty()) {
            return false;
        }
        
        try {
            // Collect all text from the dialog
            List<String> dialogTexts = new ArrayList<>();
            
            if (dialog instanceof AlertDialog) {
                AlertDialog alertDialog = (AlertDialog) dialog;
                
                // Get title
                CharSequence title = alertDialog.getWindow() != null ? 
                    alertDialog.getWindow().getAttributes().getTitle() : null;
                if (title != null) {
                    dialogTexts.add(title.toString());
                }
                
                // Get message
                // AlertDialog doesn't expose getMessage(), so we try reflection
                try {
                    Method getMessageMethod = AlertDialog.class.getDeclaredMethod("getMessage");
                    getMessageMethod.setAccessible(true);
                    CharSequence message = (CharSequence) getMessageMethod.invoke(alertDialog);
                    if (message != null) {
                        dialogTexts.add(message.toString());
                    }
                } catch (Exception ignored) {}
                
                // Get button texts
                for (int buttonType : new int[]{
                    DialogInterface.BUTTON_POSITIVE,
                    DialogInterface.BUTTON_NEGATIVE,
                    DialogInterface.BUTTON_NEUTRAL
                }) {
                    try {
                        Button button = alertDialog.getButton(buttonType);
                        if (button != null && button.getText() != null) {
                            dialogTexts.add(button.getText().toString());
                        }
                    } catch (Exception ignored) {}
                }
            }
            
            // Extract text from the dialog's view hierarchy
            Window window = dialog.getWindow();
            if (window != null) {
                View decorView = window.getDecorView();
                extractAllText(decorView, dialogTexts);
            }
            
            // Check if any text matches blocking keywords
            for (String text : dialogTexts) {
                String lowerText = text.toLowerCase(Locale.US);
                for (String keyword : sBlockKeywords) {
                    if (lowerText.contains(keyword)) {
                        Log.d(TAG, "Dialog matched keyword: " + keyword + " in text: " + 
                              (text.length() > 50 ? text.substring(0, 50) + "..." : text));
                        return true;
                    }
                }
            }
            
        } catch (Throwable t) {
            Log.w(TAG, "Error checking dialog content", t);
        }
        
        return false;
    }
    
    /**
     * Check if a DialogFragment should be blocked
     */
    private boolean shouldBlockDialogFragment(Object fragment) {
        if (!sEnabled || sBlockKeywords.isEmpty()) {
            return false;
        }
        
        try {
            // Try to get the fragment's class name
            String className = fragment.getClass().getName().toLowerCase(Locale.US);
            
            // Check if class name contains blocking keywords
            for (String keyword : sBlockKeywords) {
                if (className.contains(keyword)) {
                    Log.d(TAG, "DialogFragment class matched keyword: " + keyword);
                    return true;
                }
            }
            
            // Try to get the dialog from the fragment and check its content
            Method getDialogMethod = null;
            for (Method m : fragment.getClass().getMethods()) {
                if ("getDialog".equals(m.getName()) && m.getParameterTypes().length == 0) {
                    getDialogMethod = m;
                    break;
                }
            }
            
            if (getDialogMethod != null) {
                Dialog dialog = (Dialog) getDialogMethod.invoke(fragment);
                if (dialog != null) {
                    return shouldBlockDialog(dialog);
                }
            }
            
        } catch (Throwable t) {
            Log.w(TAG, "Error checking DialogFragment content", t);
        }
        
        return false;
    }
    
    /**
     * Try to auto-click a dismiss button on the dialog
     */
    private boolean tryAutoClickButton(AlertDialog dialog) {
        try {
            // Try standard buttons first
            for (int buttonType : new int[]{
                DialogInterface.BUTTON_NEGATIVE,
                DialogInterface.BUTTON_NEUTRAL,
                DialogInterface.BUTTON_POSITIVE
            }) {
                Button button = dialog.getButton(buttonType);
                if (button != null && button.getText() != null) {
                    String buttonText = button.getText().toString().toLowerCase(Locale.US);
                    for (String autoClick : sAutoClickButtons) {
                        if (buttonText.contains(autoClick)) {
                            Log.d(TAG, "Auto-clicking button: " + button.getText());
                            button.performClick();
                            return true;
                        }
                    }
                }
            }
            
            // Try to find buttons in the view hierarchy
            Window window = dialog.getWindow();
            if (window != null) {
                View decorView = window.getDecorView();
                return tryAutoClickInView(decorView);
            }
            
        } catch (Throwable t) {
            Log.w(TAG, "Error auto-clicking button", t);
        }
        
        return false;
    }
    
    /**
     * Recursively search for buttons to auto-click in a view hierarchy
     */
    private boolean tryAutoClickInView(View view) {
        if (view == null) return false;
        
        if (view instanceof Button) {
            Button button = (Button) view;
            if (button.getText() != null) {
                String buttonText = button.getText().toString().toLowerCase(Locale.US);
                for (String autoClick : sAutoClickButtons) {
                    if (buttonText.contains(autoClick)) {
                        Log.d(TAG, "Auto-clicking view button: " + button.getText());
                        button.performClick();
                        return true;
                    }
                }
            }
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (tryAutoClickInView(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Extract all text from a view hierarchy
     */
    private void extractAllText(View view, List<String> texts) {
        if (view == null) return;
        
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) {
                texts.add(text.toString());
            }
        }
        
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                extractAllText(group.getChildAt(i), texts);
            }
        }
    }
    
    /**
     * Public API to enable/disable dialog blocking at runtime
     */
    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
        Log.i(TAG, "Dialog blocking " + (enabled ? "enabled" : "disabled"));
    }
    
    /**
     * Add custom keywords to block
     */
    public static void addBlockKeyword(String keyword) {
        if (keyword != null && !keyword.isEmpty()) {
            sBlockKeywords.add(keyword.toLowerCase(Locale.US));
        }
    }
    
    /**
     * Remove a keyword from blocking
     */
    public static void removeBlockKeyword(String keyword) {
        if (keyword != null) {
            sBlockKeywords.remove(keyword.toLowerCase(Locale.US));
        }
    }
    
    /**
     * Check if dialog blocking is enabled
     */
    public static boolean isEnabled() {
        return sEnabled;
    }
}
