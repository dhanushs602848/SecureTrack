package com.example.securetrack.activities;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import androidx.appcompat.app.AppCompatActivity;
import com.example.securetrack.MainActivity;
import com.example.securetrack.R;
import com.example.securetrack.utils.SessionManager;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        startStartupAnimation();
        scheduleAppNavigation();
    }

    private void startStartupAnimation() {
        View ivLogo = findViewById(R.id.iv_logo);
        View viewGlow = findViewById(R.id.view_logo_glow);
        View radarRing1 = findViewById(R.id.radar_ring_1);
        View radarRing2 = findViewById(R.id.radar_ring_2);
        View layoutBrand = findViewById(R.id.layout_brand);
        View layoutFooter = findViewById(R.id.layout_footer);

        // Stage 1: Ambient Glow Reveal
        ObjectAnimator glowFade = ObjectAnimator.ofFloat(viewGlow, View.ALPHA, 0f, 1f);
        glowFade.setDuration(500);

        // Stage 2: Logo Formation with subtle overshoot bounce
        ObjectAnimator logoScaleX = ObjectAnimator.ofFloat(ivLogo, View.SCALE_X, 0.4f, 1.0f);
        ObjectAnimator logoScaleY = ObjectAnimator.ofFloat(ivLogo, View.SCALE_Y, 0.4f, 1.0f);
        ObjectAnimator logoAlpha = ObjectAnimator.ofFloat(ivLogo, View.ALPHA, 0f, 1.0f);
        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playTogether(logoScaleX, logoScaleY, logoAlpha);
        logoSet.setDuration(700);
        logoSet.setInterpolator(new OvershootInterpolator(1.4f));

        // Stage 3: Radar Pulse Expanding Rings
        ObjectAnimator ring1ScaleX = ObjectAnimator.ofFloat(radarRing1, View.SCALE_X, 0.7f, 1.45f);
        ObjectAnimator ring1ScaleY = ObjectAnimator.ofFloat(radarRing1, View.SCALE_Y, 0.7f, 1.45f);
        ObjectAnimator ring1Alpha = ObjectAnimator.ofFloat(radarRing1, View.ALPHA, 0.9f, 0f);
        AnimatorSet ring1Set = new AnimatorSet();
        ring1Set.playTogether(ring1ScaleX, ring1ScaleY, ring1Alpha);
        ring1Set.setDuration(1000);
        ring1Set.setInterpolator(new DecelerateInterpolator());
        ring1Set.setStartDelay(200);

        ObjectAnimator ring2ScaleX = ObjectAnimator.ofFloat(radarRing2, View.SCALE_X, 0.7f, 1.6f);
        ObjectAnimator ring2ScaleY = ObjectAnimator.ofFloat(radarRing2, View.SCALE_Y, 0.7f, 1.6f);
        ObjectAnimator ring2Alpha = ObjectAnimator.ofFloat(radarRing2, View.ALPHA, 0.7f, 0f);
        AnimatorSet ring2Set = new AnimatorSet();
        ring2Set.playTogether(ring2ScaleX, ring2ScaleY, ring2Alpha);
        ring2Set.setDuration(1200);
        ring2Set.setInterpolator(new DecelerateInterpolator());
        ring2Set.setStartDelay(450);

        // Stage 4: Brand Typography Slide and Fade
        layoutBrand.setTranslationY(40f);
        ObjectAnimator brandTransY = ObjectAnimator.ofFloat(layoutBrand, View.TRANSLATION_Y, 40f, 0f);
        ObjectAnimator brandAlpha = ObjectAnimator.ofFloat(layoutBrand, View.ALPHA, 0f, 1f);
        AnimatorSet brandSet = new AnimatorSet();
        brandSet.playTogether(brandTransY, brandAlpha);
        brandSet.setDuration(600);
        brandSet.setInterpolator(new DecelerateInterpolator());
        brandSet.setStartDelay(550);

        // Stage 5: Footer Fade-in
        ObjectAnimator footerAlpha = ObjectAnimator.ofFloat(layoutFooter, View.ALPHA, 0f, 1f);
        footerAlpha.setDuration(500);
        footerAlpha.setStartDelay(750);

        // Master Animation Orchestration
        AnimatorSet masterSet = new AnimatorSet();
        masterSet.playTogether(glowFade, logoSet, ring1Set, ring2Set, brandSet, footerAlpha);
        masterSet.start();
    }

    private void scheduleAppNavigation() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            SessionManager sessionManager = new SessionManager(this);
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            Class<?> targetActivity;

            if (currentUser != null) {
                String uid = currentUser.getUid();
                String email = currentUser.getEmail() != null ? currentUser.getEmail() : "";
                String name = currentUser.getDisplayName() != null ? currentUser.getDisplayName() : email;
                String existingOwner = sessionManager.getOwnerUid();
                if (existingOwner == null || existingOwner.isEmpty()) {
                    sessionManager.saveFirebaseAuthSession(uid, email, name);
                }
                targetActivity = MainActivity.class;
            } else if (sessionManager.isLoggedIn()) {
                targetActivity = MainActivity.class;
            } else {
                targetActivity = LoginActivity.class;
            }

            Intent intent = new Intent(SplashActivity.this, targetActivity);
            startActivity(intent);
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 1900);
    }
}
