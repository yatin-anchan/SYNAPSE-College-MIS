package com.projectbyyatin.synapsemis

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var starsContainer: RelativeLayout
    private lateinit var btnLogin: MaterialButton
    private lateinit var btnNewStudent: MaterialButton
    private lateinit var appLogo: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide()

        initializeViews()
        createStars()
        animateLogo()
        setupClickListeners()
    }

    private fun initializeViews() {
        starsContainer = findViewById(R.id.stars_container)
        btnLogin = findViewById(R.id.btn_login)
        btnNewStudent = findViewById(R.id.btn_new_student)
        appLogo = findViewById(R.id.app_logo)
    }

    private fun createStars() {
        // Create 100 stars
        for (i in 1..100) {
            val star = View(this)
            val size = Random.nextInt(2, 6)
            val params = RelativeLayout.LayoutParams(size, size)

            // Random position
            params.leftMargin = Random.nextInt(0, resources.displayMetrics.widthPixels)
            params.topMargin = Random.nextInt(0, resources.displayMetrics.heightPixels)

            star.layoutParams = params
            star.setBackgroundResource(R.drawable.ic_star)
            star.alpha = Random.nextFloat() * 0.8f + 0.2f

            starsContainer.addView(star)

            // Animate star twinkling
            animateStar(star)
        }
    }

    private fun animateStar(star: View) {
        val animator = ObjectAnimator.ofFloat(star, "alpha", star.alpha, 0.2f, star.alpha)
        animator.duration = Random.nextLong(1000, 3000)
        animator.repeatCount = ValueAnimator.INFINITE
        animator.repeatMode = ValueAnimator.REVERSE
        animator.interpolator = LinearInterpolator()
        animator.startDelay = Random.nextLong(0, 1000)
        animator.start()
    }

    private fun animateLogo() {
        // Rotate logo continuously
        val rotateAnimator = ObjectAnimator.ofFloat(appLogo, "rotation", 0f, 360f)
        rotateAnimator.duration = 20000
        rotateAnimator.repeatCount = ValueAnimator.INFINITE
        rotateAnimator.interpolator = LinearInterpolator()
        rotateAnimator.start()

        // Pulse effect
        val scaleX = ObjectAnimator.ofFloat(appLogo, "scaleX", 1f, 1.1f, 1f)
        val scaleY = ObjectAnimator.ofFloat(appLogo, "scaleY", 1f, 1.1f, 1f)
        scaleX.duration = 2000
        scaleY.duration = 2000
        scaleX.repeatCount = ValueAnimator.INFINITE
        scaleY.repeatCount = ValueAnimator.INFINITE
        scaleX.start()
        scaleY.start()
    }

    private fun setupClickListeners() {
        btnLogin.setOnClickListener {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        btnNewStudent.setOnClickListener {
            val intent = Intent(this, ApplicationFormActivity::class.java)
            startActivity(intent)
        }
    }
}
