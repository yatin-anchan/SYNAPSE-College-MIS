package com.projectbyyatin.synapsemis

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var emailInput: TextInputEditText
    private lateinit var passwordInput: TextInputEditText
    private lateinit var emailInputLayout: TextInputLayout
    private lateinit var passwordInputLayout: TextInputLayout
    private lateinit var loginButton: MaterialButton
    private lateinit var forgotPasswordText: TextView
    private lateinit var firstLoginText: TextView
    private lateinit var loginProgress: ProgressBar

    private lateinit var orbit1: View
    private lateinit var orbit2: View
    private lateinit var orbit3: View

    companion object {
        const val ROLE_STUDENT = "student"
        const val ROLE_FACULTY = "faculty"
        const val ROLE_HOD = "hod"
        const val ROLE_COE = "coe"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        startOrbitAnimations()
        setupClickListeners()
    }

    private fun initializeViews() {
        emailInput = findViewById(R.id.email_input)
        passwordInput = findViewById(R.id.password_input)
        emailInputLayout = findViewById(R.id.email_input_layout)
        passwordInputLayout = findViewById(R.id.password_input_layout)
        loginButton = findViewById(R.id.login_button)
        forgotPasswordText = findViewById(R.id.forgot_password_text)
        firstLoginText = findViewById(R.id.first_login_text)
        loginProgress = findViewById(R.id.login_progress)

        orbit1 = findViewById(R.id.orbit1)
        orbit2 = findViewById(R.id.orbit2)
        orbit3 = findViewById(R.id.orbit3)
    }

    private fun startOrbitAnimations() {
        listOf(
            Pair(orbit1, 20000L),
            Pair(orbit2, 15000L),
            Pair(orbit3, 10000L)
        ).forEachIndexed { index, pair ->
            ObjectAnimator.ofFloat(
                pair.first,
                "rotation",
                if (index == 1) 360f else 0f,
                if (index == 1) 0f else 360f
            ).apply {
                duration = pair.second
                repeatCount = ObjectAnimator.INFINITE
                interpolator = LinearInterpolator()
                start()
            }
        }
    }

    private fun setupClickListeners() {
        loginButton.setOnClickListener { attemptLogin() }
        forgotPasswordText.setOnClickListener { showForgotPasswordDialog() }
        firstLoginText.setOnClickListener {
            startActivity(Intent(this, FacultyFirstLoginActivity::class.java))
        }
    }

    private fun attemptLogin() {
        emailInputLayout.error = null
        passwordInputLayout.error = null

        val email = emailInput.text.toString().trim()
        val password = passwordInput.text.toString().trim()

        if (!validateInputs(email, password)) return

        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                navigateBasedOnRole()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, it.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun validateInputs(email: String, password: String): Boolean {
        var valid = true
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInputLayout.error = "Valid email required"
            valid = false
        }
        if (password.length < 6) {
            passwordInputLayout.error = "Minimum 6 characters"
            valid = false
        }
        return valid
    }

    // ===================== ROLE ROUTING =====================

    private fun navigateBasedOnRole() {
        val user = auth.currentUser ?: return

        firestore.collection("users").document(user.uid)
            .get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    blockLogin("User profile not found")
                    return@addOnSuccessListener
                }

                val role = userDoc.getString("role")?.lowercase()
                val profileCompleted = userDoc.getBoolean("profileCompleted") ?: false

                when (role) {
                    ROLE_STUDENT -> handleStudent(user.uid, profileCompleted)
                    ROLE_FACULTY, ROLE_HOD -> handleFaculty(userDoc, profileCompleted)
                    ROLE_COE -> navigateToDashboard(ROLE_COE)
                    else -> blockLogin("Invalid role")
                }
            }
            .addOnFailureListener {
                blockLogin(it.message ?: "Error")
            }
    }

    private fun handleStudent(userId: String, completed: Boolean) {
        if (completed) {
            navigateToDashboard(ROLE_STUDENT)
        } else {
            navigateToProfileSetup(userId)
        }
    }

    private fun handleFaculty(userDoc: com.google.firebase.firestore.DocumentSnapshot, completed: Boolean) {
        val facultyId = userDoc.getString("facultyId")

        if (facultyId.isNullOrEmpty()) {
            blockLogin("Faculty mapping missing")
            return
        }

        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { facultyDoc ->
                if (!facultyDoc.exists()) {
                    blockLogin("Faculty record not found")
                    return@addOnSuccessListener
                }

                val facultyRole = facultyDoc.getString("role")?.lowercase()

                if (!completed) {
                    navigateToFacultyProfileSetup(facultyId)
                    return@addOnSuccessListener
                }

                when (facultyRole) {
                    ROLE_HOD -> navigateToDashboard(ROLE_HOD)
                    ROLE_FACULTY -> navigateToDashboard(ROLE_FACULTY)
                    else -> blockLogin("Invalid faculty role")
                }
            }
            .addOnFailureListener {
                blockLogin(it.message ?: "Faculty error")
            }
    }

    // ===================== NAVIGATION =====================

    private fun navigateToProfileSetup(userId: String) {
        showLoading(false)
        startActivity(Intent(this, StudentProfileSetupActivity::class.java).apply {
            putExtra("USER_ID", userId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun navigateToFacultyProfileSetup(facultyId: String) {
        showLoading(false)
        startActivity(Intent(this, FacultyProfileSetupActivity::class.java).apply {
            putExtra("FACULTY_ID", facultyId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun navigateToDashboard(role: String) {
        showLoading(false)
        val intent = when (role) {
            ROLE_COE -> Intent(this, CoeDashboardActivity::class.java)
            ROLE_HOD -> Intent(this, HodDashboardActivity::class.java)
            ROLE_FACULTY -> Intent(this, FacultyDashboardActivity::class.java)
            ROLE_STUDENT -> Intent(this, StudentDashboardActivity::class.java)
            else -> return
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun blockLogin(message: String) {
        showLoading(false)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        auth.signOut()
    }

    private fun showForgotPasswordDialog() {
        Toast.makeText(this, "Password reset handled elsewhere", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(show: Boolean) {
        loginProgress.visibility = if (show) View.VISIBLE else View.GONE
        loginButton.isEnabled = !show
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            navigateBasedOnRole()
        }
    }
}
