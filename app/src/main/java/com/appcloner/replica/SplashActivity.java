package com.appcloner.replica;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;

public class SplashActivity extends AppCompatActivity {
    
    private static final int SPLASH_DELAY = 2000; // 2 seconds
    private ImageView logoIcon;
    private TextView appTitle;
    private TextView appSubtitle;
    private View progressIndicator;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Handle the splash screen transition
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);
        
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        
        // Initialize views
        logoIcon = findViewById(R.id.splash_logo);
        appTitle = findViewById(R.id.splash_title);
        appSubtitle = findViewById(R.id.splash_subtitle);
        progressIndicator = findViewById(R.id.splash_progress);
        
        // Start entrance animations
        startEntranceAnimations();
        
        // Navigate to MainActivity after delay
        new Handler(Looper.getMainLooper()).postDelayed(this::navigateToMain, SPLASH_DELAY);
    }
    
    private void startEntranceAnimations() {
        // Initial state - set invisible
        logoIcon.setAlpha(0f);
        logoIcon.setScaleX(0.3f);
        logoIcon.setScaleY(0.3f);
        appTitle.setAlpha(0f);
        appTitle.setTranslationY(50f);
        appSubtitle.setAlpha(0f);
        appSubtitle.setTranslationY(30f);
        progressIndicator.setAlpha(0f);
        
        // Logo animation - bounce in
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(logoIcon, "alpha", 0f, 1f);
        logoAlpha.setDuration(500);
        
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(logoIcon, "scaleX", 0.3f, 1f);
        logoScaleX.setDuration(600);
        logoScaleX.setInterpolator(new OvershootInterpolator(1.5f));
        
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(logoIcon, "scaleY", 0.3f, 1f);
        logoScaleY.setDuration(600);
        logoScaleY.setInterpolator(new OvershootInterpolator(1.5f));
        
        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoAlpha, logoScaleX, logoScaleY);
        
        // Title animation - slide up and fade in
        ObjectAnimator titleAlpha = ObjectAnimator.ofFloat(appTitle, "alpha", 0f, 1f);
        titleAlpha.setDuration(400);
        
        ObjectAnimator titleTranslate = ObjectAnimator.ofFloat(appTitle, "translationY", 50f, 0f);
        titleTranslate.setDuration(500);
        titleTranslate.setInterpolator(new AccelerateDecelerateInterpolator());
        
        AnimatorSet titleSet = new AnimatorSet();
        titleSet.playTogether(titleAlpha, titleTranslate);
        titleSet.setStartDelay(200);
        
        // Subtitle animation - slide up and fade in
        ObjectAnimator subtitleAlpha = ObjectAnimator.ofFloat(appSubtitle, "alpha", 0f, 1f);
        subtitleAlpha.setDuration(400);
        
        ObjectAnimator subtitleTranslate = ObjectAnimator.ofFloat(appSubtitle, "translationY", 30f, 0f);
        subtitleTranslate.setDuration(500);
        subtitleTranslate.setInterpolator(new AccelerateDecelerateInterpolator());
        
        AnimatorSet subtitleSet = new AnimatorSet();
        subtitleSet.playTogether(subtitleAlpha, subtitleTranslate);
        subtitleSet.setStartDelay(400);
        
        // Progress indicator animation
        ObjectAnimator progressAlpha = ObjectAnimator.ofFloat(progressIndicator, "alpha", 0f, 1f);
        progressAlpha.setDuration(300);
        progressAlpha.setStartDelay(600);
        
        // Start all animations
        logoSet.start();
        titleSet.start();
        subtitleSet.start();
        progressAlpha.start();
    }
    
    private void navigateToMain() {
        // Exit animation
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(getWindow().getDecorView(), "alpha", 1f, 0f);
        fadeOut.setDuration(300);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Intent intent = new Intent(SplashActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
        fadeOut.start();
    }
}
