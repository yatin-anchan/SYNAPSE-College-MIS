package com.projectbyyatin.synapsemis

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase


class SplashActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private val SPLASH_DELAY: Long = 3500

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activi
                ty_splash)

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Start animations
        startAnimations()

        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAndNavigate()
        }, SPLASH_DELAY)
    }

    private fun startAnimations() {
        val mainContent = findViewById<View>(R.id.main_content)
        val appLogo = findViewById<View>(R.id.app_logo)
        val bottomContent = findViewById<View>(R.id.bottom_content)
        val loadingIndicator = findViewById<View>(R.id.loading_indicator)
        val star1 = findViewById<View>(R.id.star1)
        val star2 = findViewById<View>(R.id.star2)
        val star3 = findViewById<View>(R.id.star3)
        val star4 = findViewById<View>(R.id.star4)
        val star5 = findViewById<View>(R.id.star5)
        val star6 = findViewById<View>(R.id.star6)
        val star7 = findViewById<View>(R.id.star7)
        val star8 = findViewById<View>(R.id.star8)
        val star9 = findViewById<View>(R.id.star9)
        val star10 = findViewById<View>(R.id.star10)

        // Logo scale and fade in animation
        val logoScaleX = ObjectAnimator.ofFloat(appLogo, "scaleX", 0.3f, 1f)
        val logoScaleY = ObjectAnimator.ofFloat(appLogo, "scaleY", 0.3f, 1f)
        val logoRotation = ObjectAnimator.ofFloat(appLogo, "rotation", -180f, 0f)

        logoScaleX.duration = 800
        logoScaleY.duration = 800
        logoRotation.duration = 800

        logoScaleX.interpolator = OvershootInterpolator()
        logoScaleY.interpolator = OvershootInterpolator()
        logoRotation.interpolator = DecelerateInterpolator()

        // Main content fade in
        val mainFadeIn = ObjectAnimator.ofFloat(mainContent, "alpha", 0f, 1f)
        mainFadeIn.duration = 600
        mainFadeIn.startDelay = 200
        mainFadeIn.interpolator = DecelerateInterpolator()

        // Main content slide up
        val mainSlideUp = ObjectAnimator.ofFloat(mainContent, "translationY", 100f, 0f)
        mainSlideUp.duration = 800
        mainSlideUp.startDelay = 200
        mainSlideUp.interpolator = DecelerateInterpolator()

        // Bottom content fade in
        val bottomFadeIn = ObjectAnimator.ofFloat(bottomContent, "alpha", 0f, 1f)
        bottomFadeIn.duration = 600
        bottomFadeIn.startDelay = 600
        bottomFadeIn.interpolator = DecelerateInterpolator()

        // Bottom content slide up
        val bottomSlideUp = ObjectAnimator.ofFloat(bottomContent, "translationY", 50f, 0f)
        bottomSlideUp.duration = 600
        bottomSlideUp.startDelay = 600
        bottomSlideUp.interpolator = DecelerateInterpolator()

        // Loading indicator fade in
        val loadingFadeIn = ObjectAnimator.ofFloat(loadingIndicator, "alpha", 0f, 1f)
        loadingFadeIn.duration = 400
        loadingFadeIn.startDelay = 1000
        loadingFadeIn.interpolator = DecelerateInterpolator()

        // Star animations with different delays for sparkle effect
        animateStar(star1, 300, 0.9f)
        animateStar(star2, 500, 1f)
        animateStar(star3, 700, 1f)
        animateStar(star4, 400, 0.85f)
        animateStar(star5, 600, 0.95f)
        animateStar(star6, 800, 1f)
        animateStar(star7, 350, 0.8f)
        animateStar(star8, 550, 0.9f)
        animateStar(star9, 750, 1f)
        animateStar(star10, 450, 0.85f)

        // Start all animations
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            logoScaleX, logoScaleY, logoRotation,
            mainFadeIn, mainSlideUp,
            bottomFadeIn, bottomSlideUp,
            loadingFadeIn
        )
        animatorSet.start()
    }

    private fun animateStar(star: View, delay: Long, maxAlpha: Float) {
        // Fade in
        val fadeIn = ObjectAnimator.ofFloat(star, "alpha", 0f, maxAlpha)
        fadeIn.duration = 500
        fadeIn.startDelay = delay
        fadeIn.interpolator = DecelerateInterpolator()

        // Twinkle effect (scale pulse)
        val scaleX = ObjectAnimator.ofFloat(star, "scaleX", 1f, 1.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(star, "scaleY", 1f, 1.5f, 1f)
        scaleX.duration = 1000
        scaleY.duration = 1000
        scaleX.startDelay = delay + 500
        scaleY.startDelay = delay + 500
        scaleX.repeatCount = ObjectAnimator.INFINITE
        scaleY.repeatCount = ObjectAnimator.INFINITE
        scaleX.interpolator = AccelerateDecelerateInterpolator()
        scaleY.interpolator = AccelerateDecelerateInterpolator()

        // Rotation for extra sparkle
        val rotation = ObjectAnimator.ofFloat(star, "rotation", 0f, 360f)
        rotation.duration = 2000
        rotation.startDelay = delay + 500
        rotation.repeatCount = ObjectAnimator.INFINITE
        rotation.interpolator = AccelerateDecelerateInterpolator()

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(fadeIn, scaleX, scaleY, rotation)
        animatorSet.start()
    }

    private fun checkUserAndNavigate() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            navigateBasedOnRole()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun navigateBasedOnRole() {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val role = document.getString("role")

                        val intent = when (role) {
                            "coe" -> Intent(this, CoeDashboardActivity::class.java)
                            "student" -> Intent(this, StudentDashboardActivity::class.java)
                            //"faculty" -> Intent(this, FacultyDashboardActivity::class.java)
                            else -> Intent(this, MainActivity::class.java)
                        }

                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
                }
        }
    }
}