package com.applisto.appcloner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Fake Calculator Activity that requires a passcode to access the real cloned app.
 * Displays a functional calculator interface, but entering the correct passcode
 * (default: "1234" - numbers only, no equals sign) will launch the actual app.
 * 
 * Features:
 * - Numbers-only passcode (no = required)
 * - Auto-submit when correct code entered
 * - Ask Once option: only ask for passcode once per session
 */
import android.view.Window;
import android.view.WindowManager;

public class FakeCalculatorActivity extends Activity {
    private static final String TAG = "FakeCalculatorActivity";
    private static final String PREF_NAME = "FakeCalculatorPrefs";
    private static final String PREF_PASSCODE = "passcode";
    private static final String PREF_ASK_ONCE = "ask_once";
    private static final String PREF_IS_UNLOCKED = "is_unlocked";
    private static final String DEFAULT_PASSCODE = "1234";  // Numbers only, no = required
    
    private TextView displayTextView;
    private StringBuilder currentInput = new StringBuilder();
    private StringBuilder calculationBuffer = new StringBuilder();
    private double firstOperand = 0;
    private String currentOperator = "";
    private boolean isNewCalculation = true;
    private String correctPasscode = DEFAULT_PASSCODE;
    private boolean askOnce = false;
    private boolean enabled = true;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load settings first
        loadSettings();

        if (!enabled) {
            Log.i(TAG, "Fake calculator disabled, launching real app.");
            launchRealApp(false);
            return;
        }

        // Make full screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Check if already unlocked
        if (askOnce && isAppUnlocked()) {
            Log.i(TAG, "App already unlocked, launching directly.");
            launchRealApp(false); // false = don't need to update unlock state
            return;
        }
        
        // Create calculator UI programmatically
        createCalculatorUI();
        
        Log.i(TAG, "FakeCalculatorActivity started. Enter passcode to access app.");
    }
    
    private void loadSettings() {
        // Read configuration from cloner.json via ClonerSettings
        ClonerSettings settings = ClonerSettings.get(this);
        enabled = settings.fakeCalculatorEnabled();

        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        // Passcode logic:
        // 1. Check SharedPreferences first (runtime overrides)
        // 2. Fallback to ClonerSettings (cloner.json)
        // 3. Fallback to hardcoded default

        if (prefs.contains(PREF_PASSCODE)) {
            correctPasscode = prefs.getString(PREF_PASSCODE, DEFAULT_PASSCODE);
        } else {
            correctPasscode = settings.fakeCalculatorPasscode();
            // Sanitize: ensure numbers only to support auto-submit behavior
            // If user set "1234=", we strip "=" to make it "1234"
            if (correctPasscode != null) {
                correctPasscode = correctPasscode.replaceAll("[^0-9]", "");
            }

            if (correctPasscode == null || correctPasscode.isEmpty()) {
                correctPasscode = DEFAULT_PASSCODE;
            }
        }

        if (prefs.contains(PREF_ASK_ONCE)) {
            askOnce = prefs.getBoolean(PREF_ASK_ONCE, false);
        } else {
            askOnce = settings.fakeCalculatorAskOnce();
        }
    }

    private boolean isAppUnlocked() {
        SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getBoolean(PREF_IS_UNLOCKED, false);
    }
    
    private void createCalculatorUI() {
        // Create main layout programmatically
        android.widget.LinearLayout mainLayout = new android.widget.LinearLayout(this);
        mainLayout.setOrientation(android.widget.LinearLayout.VERTICAL);
        mainLayout.setPadding(16, 16, 16, 16);
        mainLayout.setBackgroundColor(0xFF2C2C2C);
        
        // Display TextView
        displayTextView = new TextView(this);
        displayTextView.setText("0");
        displayTextView.setTextSize(48);
        displayTextView.setTextColor(0xFFFFFFFF);
        displayTextView.setGravity(android.view.Gravity.END | android.view.Gravity.CENTER_VERTICAL);
        displayTextView.setPadding(16, 32, 16, 32);
        displayTextView.setBackgroundColor(0xFF1C1C1C);
        android.widget.LinearLayout.LayoutParams displayParams = new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                0, 2f);
        displayParams.setMargins(0, 0, 0, 16);
        mainLayout.addView(displayTextView, displayParams);
        
        // Button grid - only one = button now, better layout
        String[][] buttons = {
                {"C", "⌫", "%", "/"},
                {"7", "8", "9", "×"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"00", "0", ".", "="}
        };
        
        for (String[] row : buttons) {
            android.widget.LinearLayout rowLayout = new android.widget.LinearLayout(this);
            rowLayout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    0, 1f);
            rowParams.setMargins(0, 0, 0, 8);
            
            for (String buttonText : row) {
                Button button = new Button(this);
                button.setText(buttonText);
                button.setTextSize(24);
                button.setTextColor(0xFFFFFFFF);
                
                // Set button colors based on type
                if (buttonText.matches("[0-9\\.]")) {
                    button.setBackgroundColor(0xFF505050);
                } else if (buttonText.equals("=")) {
                    button.setBackgroundColor(0xFF0066CC);
                } else {
                    button.setBackgroundColor(0xFF404040);
                }
                
                android.widget.LinearLayout.LayoutParams buttonParams = new android.widget.LinearLayout.LayoutParams(
                        0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1f);
                buttonParams.setMargins(4, 4, 4, 4);
                
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onButtonClick(buttonText);
                    }
                });
                
                rowLayout.addView(button, buttonParams);
            }
            
            mainLayout.addView(rowLayout, rowParams);
        }
        
        setContentView(mainLayout);
    }
    
    private void onButtonClick(String buttonText) {
        // For passcode tracking, only track number inputs
        if (buttonText.matches("[0-9]+")) {
            currentInput.append(buttonText);
        }
        
        // Check if passcode is entered (numbers-only passcode, auto-submit)
        String inputStr = currentInput.toString();
        if (inputStr.endsWith(correctPasscode)) {
            // Correct passcode entered - launch the real app
            launchRealApp();
            return;
        }
        
        // Regular calculator logic
        switch (buttonText) {
            case "C":
                clear();
                break;
            case "⌫":
                backspace();
                break;
            case "+":
            case "-":
            case "×":
            case "/":
            case "%":
                handleOperator(buttonText);
                break;
            case "=":
                calculate();
                break;
            case ".":
                if (!displayTextView.getText().toString().contains(".")) {
                    updateDisplay(displayTextView.getText().toString() + ".");
                    isNewCalculation = false;
                }
                break;
            default:
                // Number button
                if (isNewCalculation) {
                    updateDisplay(buttonText);
                    isNewCalculation = false;
                } else {
                    String current = displayTextView.getText().toString();
                    if (current.equals("0")) {
                        updateDisplay(buttonText);
                    } else {
                        updateDisplay(current + buttonText);
                    }
                }
                break;
        }
        
        // Limit input tracking to last 20 characters to prevent memory issues
        if (currentInput.length() > 20) {
            currentInput.delete(0, currentInput.length() - 20);
        }
    }
    
    private void clear() {
        displayTextView.setText("0");
        firstOperand = 0;
        currentOperator = "";
        isNewCalculation = true;
        calculationBuffer.setLength(0);
    }
    
    private void backspace() {
        String current = displayTextView.getText().toString();
        if (current.length() > 1 && !isNewCalculation) {
            updateDisplay(current.substring(0, current.length() - 1));
        } else {
            updateDisplay("0");
            isNewCalculation = true;
        }
    }
    
    private void handleOperator(String operator) {
        try {
            if (!currentOperator.isEmpty()) {
                calculate();
            } else {
                firstOperand = Double.parseDouble(displayTextView.getText().toString());
            }
            currentOperator = operator;
            isNewCalculation = true;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error parsing number", e);
        }
    }
    
    private void calculate() {
        try {
            if (currentOperator.isEmpty()) {
                return;
            }
            
            double secondOperand = Double.parseDouble(displayTextView.getText().toString());
            double result = 0;
            
            switch (currentOperator) {
                case "+":
                    result = firstOperand + secondOperand;
                    break;
                case "-":
                    result = firstOperand - secondOperand;
                    break;
                case "×":
                    result = firstOperand * secondOperand;
                    break;
                case "/":
                    if (secondOperand != 0) {
                        result = firstOperand / secondOperand;
                    } else {
                        Toast.makeText(this, "Cannot divide by zero", Toast.LENGTH_SHORT).show();
                        clear();
                        return;
                    }
                    break;
                case "%":
                    result = firstOperand % secondOperand;
                    break;
            }
            
            // Format result
            String resultStr;
            if (result == (long) result) {
                resultStr = String.valueOf((long) result);
            } else {
                resultStr = String.valueOf(result);
            }
            
            updateDisplay(resultStr);
            firstOperand = result;
            currentOperator = "";
            isNewCalculation = true;
        } catch (NumberFormatException e) {
            Log.e(TAG, "Error calculating", e);
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            clear();
        }
    }
    
    private void updateDisplay(String text) {
        displayTextView.setText(text);
    }
    
    private void launchRealApp() {
        launchRealApp(true);
    }

    private void launchRealApp(boolean updateState) {
        Log.i(TAG, "Launching real app.");
        
        if (updateState) {
            // Clear the calculator state
            clear();
            currentInput.setLength(0);

            // Notify the hook that passcode was verified
            FakeCalculatorHook hook = FakeCalculatorHook.getInstance();
            if (hook != null) {
                hook.onPasscodeVerified();
            }

            // Save unlock state if askOnce is enabled
            if (askOnce) {
                SharedPreferences prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
                prefs.edit().putBoolean(PREF_IS_UNLOCKED, true).apply();
            }
        }
        
        // Get the real app launch intent from meta-data
        String targetPackage = getPackageName();
        String targetActivity = getOriginalLauncherActivity();
        
        if (targetActivity != null) {
            try {
                Intent launchIntent = new Intent();
                launchIntent.setClassName(targetPackage, targetActivity);
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launchIntent);
                finish(); // Close the calculator
            } catch (Exception e) {
                Log.e(TAG, "Error launching real app", e);
                Toast.makeText(this, "Error launching app", Toast.LENGTH_SHORT).show();
            }
        } else {
            Log.w(TAG, "No target activity found in meta-data");

            // Fallback: try to launch via package manager if meta-data failed
            try {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(targetPackage);
                if (launchIntent != null) {
                    // Check if launch intent is pointing to us (FakeCalculatorActivity) to avoid loop
                    if (launchIntent.getComponent() != null &&
                        launchIntent.getComponent().getClassName().equals(FakeCalculatorActivity.class.getName())) {
                        Log.e(TAG, "Launch intent points to FakeCalculatorActivity, aborting loop");
                        finish();
                        return;
                    }

                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(launchIntent);
                    finish();
                } else {
                    Log.e(TAG, "Could not create launch intent for package: " + targetPackage);
                    finish();
                }
            } catch (Exception e) {
                Log.e(TAG, "Fallback launch failed", e);
                finish();
            }
        }
    }

    private String getOriginalLauncherActivity() {
        try {
            android.content.pm.ActivityInfo ai = getPackageManager().getActivityInfo(getComponentName(), android.content.pm.PackageManager.GET_META_DATA);
            Bundle bundle = ai.metaData;
            if (bundle != null) {
                String className = bundle.getString("com.applisto.appcloner.original_launcher_activity");
                if (className != null) {
                    if (className.startsWith(".")) {
                        return getPackageName() + className;
                    }
                    // Handle cases where it might be just "MainActivity" without dot (rare but possible)
                    if (!className.contains(".")) {
                         return getPackageName() + "." + className;
                    }
                    return className;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get meta-data for original launcher", e);
        }
        return null;
    }
    
    @Override
    public void onBackPressed() {
        // Prevent back button from bypassing the calculator
        // Instead, move app to background
        moveTaskToBack(true);
    }
    
    /**
     * Static method to set a custom passcode
     */
    public static void setPasscode(android.content.Context context, String passcode) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putString(PREF_PASSCODE, passcode).apply();
    }

    /**
     * Static method to set ask once preference
     */
    public static void setAskOnce(android.content.Context context, boolean askOnce) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        prefs.edit().putBoolean(PREF_ASK_ONCE, askOnce).apply();
    }
    
    /**
     * Static method to get current passcode
     */
    public static String getPasscode(android.content.Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        return prefs.getString(PREF_PASSCODE, DEFAULT_PASSCODE);
    }
}
