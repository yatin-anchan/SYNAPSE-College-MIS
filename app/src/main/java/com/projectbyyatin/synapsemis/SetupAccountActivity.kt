package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SetupAccountActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // Views
    private lateinit var referenceSection: LinearLayout
    private lateinit var setupSection: LinearLayout

    private lateinit var referenceInput: TextInputEditText
    private lateinit var referenceLayout: TextInputLayout
    private lateinit var btnVerify: MaterialButton

    private lateinit var tvVerifiedName: TextView
    private lateinit var tvVerifiedEmail: TextView
    private lateinit var passwordInput: TextInputEditText
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var btnCreateAccount: MaterialButton

    private lateinit var progressBar: ProgressBar
    private lateinit var tvBackToLogin: TextView

    // Application data
    private var verifiedApplication: Map<String, Any>? = null
    private var applicationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_account)

        supportActionBar?.hide()

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupClickListeners()

        // Auto-fill if reference number is passed
        intent.getStringExtra("REFERENCE_NUMBER")?.let { refNum ->
            referenceInput.setText(refNum)
        }
    }

    private fun initializeViews() {
        referenceSection = findViewById(R.id.reference_section)
        setupSection = findViewById(R.id.setup_section)

        referenceInput = findViewById(R.id.reference_input)
        referenceLayout = findViewById(R.id.reference_layout)
        btnVerify = findViewById(R.id.btn_verify)

        tvVerifiedName = findViewById(R.id.tv_verified_name)
        tvVerifiedEmail = findViewById(R.id.tv_verified_email)
        passwordInput = findViewById(R.id.password_input)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        passwordLayout = findViewById(R.id.password_layout)
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout)
        btnCreateAccount = findViewById(R.id.btn_create_account)

        progressBar = findViewById(R.id.progress_bar)
        tvBackToLogin = findViewById(R.id.tv_back_to_login)
    }

    private fun setupClickListeners() {
        btnVerify.setOnClickListener {
            verifyReferenceNumber()
        }

        btnCreateAccount.setOnClickListener {
            createAccount()
        }

        tvBackToLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    private fun verifyReferenceNumber() {
        val refNum = referenceInput.text.toString().trim().uppercase()

        if (refNum.isEmpty()) {
            referenceLayout.error = "Please enter reference number"
            return
        }

        referenceLayout.error = null
        showLoading(true)

        firestore.collection("applications")
            .whereEqualTo("referenceNumber", refNum)
            .whereEqualTo("status", "accepted")
            .whereEqualTo("accountCreated", false)
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)

                if (documents.isEmpty) {
                    Toast.makeText(
                        this,
                        "Invalid reference number or account already created",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val doc = documents.documents[0]
                    applicationId = doc.id
                    verifiedApplication = doc.data
                    showSetupSection()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SetupAccount", "Error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSetupSection() {
        verifiedApplication?.let { app ->
            tvVerifiedName.text = app["fullName"] as? String ?: "N/A"
            tvVerifiedEmail.text = app["email"] as? String ?: "N/A"

            referenceSection.visibility = View.GONE
            setupSection.visibility = View.VISIBLE
        }
    }

    private fun createAccount() {
        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        // Validate
        if (!validatePasswords(password, confirmPassword)) {
            return
        }

        val email = verifiedApplication?.get("email") as? String ?: ""

        if (email.isEmpty()) {
            Toast.makeText(this, "Email not found", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        // Create Firebase Auth account
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: ""
                Log.d("SetupAccount", "Auth account created: $userId")

                // Update application status
                updateApplicationStatus(userId)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SetupAccount", "Auth error: ${e.message}")

                val errorMessage = when {
                    e.message?.contains("already in use") == true ->
                        "This email is already registered"
                    e.message?.contains("network") == true ->
                        "Network error. Please check your connection"
                    else -> e.message ?: "Failed to create account"
                }

                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
    }

    private fun validatePasswords(password: String, confirmPassword: String): Boolean {
        var isValid = true

        if (password.isEmpty()) {
            passwordLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        } else {
            passwordLayout.error = null
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.error = "Please confirm password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordLayout.error = "Passwords do not match"
            isValid = false
        } else {
            confirmPasswordLayout.error = null
        }

        return isValid
    }

    private fun updateApplicationStatus(userId: String) {
        val updates = hashMapOf<String, Any>(
            "accountCreated" to true,
            "userId" to userId
        )

        firestore.collection("applications")
            .document(applicationId)
            .update(updates)
            .addOnSuccessListener {
                Log.d("SetupAccount", "Application updated")

                // Create user document
                createUserDocument(userId)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SetupAccount", "Error updating application: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createUserDocument(userId: String) {
        val userDoc = hashMapOf(
            "userId" to userId,
            "email" to (verifiedApplication?.get("email") as? String ?: ""),
            "role" to "applicant", // Will be changed to "student" after final approval
            "fullName" to (verifiedApplication?.get("fullName") as? String ?: ""),
            "applicationId" to applicationId,
            "referenceNumber" to (verifiedApplication?.get("referenceNumber") as? String ?: ""),
            "createdAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .set(userDoc)
            .addOnSuccessListener {
                showLoading(false)
                Log.d("SetupAccount", "User document created")

                // Show success and navigate
                showSuccessDialog()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("SetupAccount", "Error creating user doc: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSuccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Account Created!")
            .setMessage("Your account has been created successfully. Please login to complete your enrollment profile.")
            .setPositiveButton("Login Now") { _, _ ->
                // Navigate to login
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnVerify.isEnabled = !show
        btnCreateAccount.isEnabled = !show
    }
}
