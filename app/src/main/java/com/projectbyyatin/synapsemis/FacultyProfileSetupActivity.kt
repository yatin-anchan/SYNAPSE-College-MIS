package com.projectbyyatin.synapsemis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Patterns
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Faculty
import com.projectbyyatin.synapsemis.utils.ImageUploadHelper
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class FacultyProfileSetupActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var profileImage: CircleImageView
    private lateinit var btnUploadPhoto: MaterialButton
    private lateinit var welcomeText: TextView
    private lateinit var nameText: TextView
    private lateinit var emailText: TextView
    private lateinit var passwordInput: TextInputEditText
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordInput: TextInputEditText
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var btnComplete: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var selectedImageUri: Uri? = null
    private var facultyId: String = ""
    private var email: String = ""
    private var facultyName: String = ""

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            selectedImageUri = result.data?.data
            profileImage.setImageURI(selectedImageUri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_profile_setup)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        email = intent.getStringExtra("EMAIL") ?: ""

        initializeViews()
        setupToolbar()
        loadFacultyData()
        setupClickListeners()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        profileImage = findViewById(R.id.profile_image)
        btnUploadPhoto = findViewById(R.id.btn_upload_photo)
        welcomeText = findViewById(R.id.welcome_text)
        nameText = findViewById(R.id.name_text)
        emailText = findViewById(R.id.email_text)
        passwordInput = findViewById(R.id.password_input)
        passwordLayout = findViewById(R.id.password_layout)
        confirmPasswordInput = findViewById(R.id.confirm_password_input)
        confirmPasswordLayout = findViewById(R.id.confirm_password_layout)
        btnComplete = findViewById(R.id.btn_complete)
        loadingProgress = findViewById(R.id.loading_progress)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(false) // Prevent going back
        supportActionBar?.title = "Complete Your Profile"
    }

    private fun loadFacultyData() {
        showLoading(true)

        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)
                if (document.exists()) {
                    val faculty = document.toObject(Faculty::class.java)
                    faculty?.let {
                        facultyName = it.name
                        welcomeText.text = "Welcome, $facultyName!"
                        nameText.text = "Name: $facultyName"
                        emailText.text = "Email: ${it.email}"
                    }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        btnUploadPhoto.setOnClickListener {
            openImagePicker()
        }

        btnComplete.setOnClickListener {
            if (validateInputs()) {
                completeProfileSetup()
            }
        }
    }

    private fun openImagePicker() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        passwordLayout.error = null
        confirmPasswordLayout.error = null

        val password = passwordInput.text.toString().trim()
        val confirmPassword = confirmPasswordInput.text.toString().trim()

        if (password.isEmpty()) {
            passwordLayout.error = "Password is required"
            isValid = false
        } else if (password.length < 6) {
            passwordLayout.error = "Password must be at least 6 characters"
            isValid = false
        }

        if (confirmPassword.isEmpty()) {
            confirmPasswordLayout.error = "Confirm password is required"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordLayout.error = "Passwords do not match"
            isValid = false
        }

        if (selectedImageUri == null) {
            Toast.makeText(this, "Please upload a profile photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        return isValid
    }

    private fun completeProfileSetup() {
        showLoading(true)

        val password = passwordInput.text.toString().trim()

        // First upload image
        if (selectedImageUri != null) {
            ImageUploadHelper.uploadImage(
                context = this,
                imageUri = selectedImageUri!!,
                onSuccess = { imageUrl ->
                    // Create Firebase Auth account
                    createAuthAccount(password, imageUrl)
                },
                onFailure = { error ->
                    showLoading(false)
                    Toast.makeText(this, "Upload failed: $error", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun createAuthAccount(password: String, photoUrl: String) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                val userId = authResult.user?.uid ?: ""

                // Update faculty document
                updateFacultyProfile(userId, photoUrl)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error creating account: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateFacultyProfile(userId: String, photoUrl: String) {
        val updates = hashMapOf<String, Any>(
            "id" to userId,
            "photoUrl" to photoUrl,
            "profileCompleted" to true,
            "appAccessEnabled" to true,
            "isActive" to true,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("faculty").document(facultyId)
            .update(updates)
            .addOnSuccessListener {
                // Create user document for role-based access
                createUserDocument(userId)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error updating profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createUserDocument(userId: String) {
        // Determine role based on faculty data
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                // Check the role field in faculty document
                val facultyRole = document.getString("role") ?: "Faculty Member"

                // Determine designation for navigation
                // HOD role variations: "HOD", "Head of Department", "hod"
                val isHOD = facultyRole.equals("HOD", ignoreCase = true) ||
                        facultyRole.contains("Head of Department", ignoreCase = true)

                val designation = if (isHOD) "HOD" else "Faculty Member"

                val userDoc = hashMapOf(
                    "email" to email,
                    "role" to "faculty", // Always "faculty" for faculty members
                    "designation" to designation, // "HOD" or "Faculty Member" determines dashboard
                    "facultyId" to facultyId,
                    "profileCompleted" to true,
                    "isFirstLogin" to false,
                    "createdAt" to System.currentTimeMillis()
                )

                firestore.collection("users").document(userId)
                    .set(userDoc)
                    .addOnSuccessListener {
                        // Update invitation status, then navigate based on designation
                        updateInvitationStatus(designation)
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading role: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateInvitationStatus(designation: String) {
        firestore.collection("faculty_invitations")
            .document(facultyId)
            .update("status", "completed")
            .addOnSuccessListener {
                showLoading(false)
                showSuccessAndNavigate(designation)
            }
            .addOnFailureListener {
                // Even if this fails, profile is complete
                showLoading(false)
                showSuccessAndNavigate(designation)
            }
    }

    private fun showSuccessAndNavigate(designation: String) {
        val dashboardName = if (designation == "HOD") "HOD Dashboard" else "Faculty Dashboard"

        Toast.makeText(
            this,
            "Profile completed! Welcome to $dashboardName",
            Toast.LENGTH_LONG
        ).show()

        // Navigate to appropriate dashboard based on designation
        val intent = when (designation) {
            "HOD" -> Intent(this, HodDashboardActivity::class.java)
            else -> Intent(this, FacultyDashboardActivity::class.java)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnComplete.isEnabled = !show
        btnUploadPhoto.isEnabled = !show
        passwordInput.isEnabled = !show
        confirmPasswordInput.isEnabled = !show
    }

    override fun onBackPressed() {
        // Prevent going back during profile setup
        Toast.makeText(this, "Please complete your profile", Toast.LENGTH_SHORT).show()
    }
}