package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore

class FacultyFirstLoginActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var emailInput: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var invitationCodeInput: TextInputEditText
    private lateinit var invitationCodeLayout: TextInputLayout
    private lateinit var employeeIdInput: TextInputEditText
    private lateinit var employeeIdLayout: TextInputLayout
    private lateinit var btnVerify: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_first_login)

        initializeViews()
        setupToolbar()
        setupVerifyButton()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        emailInput = findViewById(R.id.email_input)
        emailLayout = findViewById(R.id.email_layout)
        invitationCodeInput = findViewById(R.id.invitation_code_input)
        invitationCodeLayout = findViewById(R.id.invitation_code_layout)
        employeeIdInput = findViewById(R.id.employee_id_input)
        employeeIdLayout = findViewById(R.id.employee_id_layout)
        btnVerify = findViewById(R.id.btn_verify)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Faculty First Login"

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupVerifyButton() {
        btnVerify.setOnClickListener {
            if (validateInputs()) {
                verifyCredentials()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        emailLayout.error = null
        invitationCodeLayout.error = null
        employeeIdLayout.error = null

        val email = emailInput.text.toString().trim()
        val code = invitationCodeInput.text.toString().trim()
        val employeeId = employeeIdInput.text.toString().trim()

        // Email is always required
        if (email.isEmpty()) {
            emailLayout.error = "Email is required"
            isValid = false
        }

        // Either invitation code OR employee ID must be provided
        if (code.isEmpty() && employeeId.isEmpty()) {
            invitationCodeLayout.error = "Provide either invitation code or employee ID"
            employeeIdLayout.error = "Provide either invitation code or employee ID"
            isValid = false
        } else if (code.isNotEmpty() && employeeId.isNotEmpty()) {
            Toast.makeText(this, "Please provide either invitation code OR employee ID, not both", Toast.LENGTH_LONG).show()
            isValid = false
        } else {
            // Validate format if provided
            if (code.isNotEmpty() && code.length != 6) {
                invitationCodeLayout.error = "Code must be 6 digits"
                isValid = false
            }
            if (employeeId.isNotEmpty() && !employeeId.startsWith("FAC")) {
                employeeIdLayout.error = "Invalid employee ID format"
                isValid = false
            }
        }

        return isValid
    }

    private fun verifyCredentials() {
        showLoading(true)

        val email = emailInput.text.toString().trim()
        val code = invitationCodeInput.text.toString().trim()
        val employeeId = employeeIdInput.text.toString().trim()

        if (code.isNotEmpty()) {
            // Verify using invitation code
            verifyWithInvitationCode(email, code)
        } else {
            // Verify using employee ID
            verifyWithEmployeeId(email, employeeId)
        }
    }

    private fun verifyWithInvitationCode(email: String, code: String) {
        // Query faculty_invitations collection
        val normalizedEmail = email.lowercase().trim()
        Log.d("FacultyLogin", "Querying: email=$normalizedEmail, code=$code") // Add this
        firestore.collection("faculty_invitations")
            .whereEqualTo("email", normalizedEmail)
            .whereEqualTo("invitationCode", code)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("FacultyLogin", "Found ${documents.size()} docs") // Add this
                if (documents.isEmpty) {
                    showLoading(false)
                    Toast.makeText(this, "Invalid invitation code or email", Toast.LENGTH_LONG).show()
                    invitationCodeLayout.error = "Invalid code"
                } else {
                    val invitation = documents.documents[0]
                    val facultyId = invitation.getString("facultyId") ?: ""
                    val expiresAt = invitation.getLong("expiresAt") ?: 0L

                    // Check if expired
                    if (System.currentTimeMillis() > expiresAt) {
                        showLoading(false)
                        Toast.makeText(this, "Invitation code has expired. Use Employee ID instead.", Toast.LENGTH_LONG).show()
                        invitationCodeLayout.error = "Expired code"
                        return@addOnSuccessListener
                    }

                    // Valid invitation - proceed to profile setup
                    proceedToProfileSetup(facultyId, email)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun verifyWithEmployeeId(email: String, employeeId: String) {
        // Query faculty collection directly using employee ID
        firestore.collection("faculty")
            .whereEqualTo("email", email)
            .whereEqualTo("employeeId", employeeId)
            .whereEqualTo("profileCompleted", false)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showLoading(false)

                    // Check if profile is already completed
                    checkIfProfileCompleted(email, employeeId)
                } else {
                    val facultyDoc = documents.documents[0]
                    val facultyId = facultyDoc.id

                    // Valid employee ID - proceed to profile setup
                    proceedToProfileSetup(facultyId, email)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun checkIfProfileCompleted(email: String, employeeId: String) {
        // Check if user already completed profile
        firestore.collection("faculty")
            .whereEqualTo("email", email)
            .whereEqualTo("employeeId", employeeId)
            .whereEqualTo("profileCompleted", true)
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)
                if (!documents.isEmpty) {
                    Toast.makeText(this, "Profile already completed. Please login normally.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Invalid email or employee ID. Contact admin.", Toast.LENGTH_LONG).show()
                    employeeIdLayout.error = "Invalid credentials"
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun proceedToProfileSetup(facultyId: String, email: String) {
        // Verify faculty exists in faculty collection
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)
                if (document.exists()) {
                    val profileCompleted = document.getBoolean("profileCompleted") ?: false

                    if (profileCompleted) {
                        Toast.makeText(this, "Profile already completed. Please login normally.", Toast.LENGTH_LONG).show()
                        finish()
                    } else {
                        // Navigate to profile setup
                        val intent = Intent(this, FacultyProfileSetupActivity::class.java)
                        intent.putExtra("FACULTY_ID", facultyId)
                        intent.putExtra("EMAIL", email)
                        startActivity(intent)
                        finish()
                    }
                } else {
                    Toast.makeText(this, "Faculty record not found. Contact admin.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnVerify.isEnabled = !show
        emailInput.isEnabled = !show
        invitationCodeInput.isEnabled = !show
        employeeIdInput.isEnabled = !show
    }
}