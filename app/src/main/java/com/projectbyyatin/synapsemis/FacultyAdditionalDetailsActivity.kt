package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Faculty

class FacultyAdditionalDetailsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var qualificationsInput: TextInputEditText
    private lateinit var qualificationsLayout: TextInputLayout
    private lateinit var experienceInput: TextInputEditText
    private lateinit var experienceLayout: TextInputLayout
    private lateinit var collegeInput: TextInputEditText
    private lateinit var collegeLayout: TextInputLayout
    private lateinit var streamDropdown: AutoCompleteTextView
    private lateinit var streamLayout: TextInputLayout
    private lateinit var completeButton: MaterialButton
    private lateinit var progressBar: ProgressBar

    private var facultyId: String = ""
    private var userId: String = ""
    private val streamsList = listOf(
        "Engineering",
        "Arts & Science",
        "Management",
        "Medical",
        "Law",
        "Education",
        "Other"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_additional_details)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        userId = intent.getStringExtra("USER_ID") ?: auth.currentUser?.uid ?: ""

        initializeViews()
        setupStreamDropdown()
        loadExistingData()
        setupClickListeners()
    }

    private fun initializeViews() {
        qualificationsInput = findViewById(R.id.qualifications_input)
        qualificationsLayout = findViewById(R.id.qualifications_layout)
        experienceInput = findViewById(R.id.experience_input)
        experienceLayout = findViewById(R.id.experience_layout)
        streamDropdown = findViewById(R.id.stream_dropdown)
        streamLayout = findViewById(R.id.stream_layout)
        completeButton = findViewById(R.id.complete_button)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupStreamDropdown() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            streamsList
        )
        streamDropdown.setAdapter(adapter)
    }

    private fun loadExistingData() {
        progressBar.visibility = View.VISIBLE

        firestore.collection("faculty")
            .document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    val faculty = document.toObject(Faculty::class.java)
                    faculty?.let {
                        qualificationsInput.setText(it.qualifications)
                        experienceInput.setText(it.experience)
                        collegeInput.setText(it.college)
                        if (it.stream.isNotEmpty()) {
                            streamDropdown.setText(it.stream, false)
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupClickListeners() {
        completeButton.setOnClickListener {
            if (validateInputs()) {
                saveAdditionalDetails()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        qualificationsLayout.error = null
        experienceLayout.error = null
        collegeLayout.error = null
        streamLayout.error = null

        val qualifications = qualificationsInput.text.toString().trim()
        val experience = experienceInput.text.toString().trim()
        val college = collegeInput.text.toString().trim()
        val stream = streamDropdown.text.toString().trim()

        if (qualifications.isEmpty()) {
            qualificationsLayout.error = "Qualifications are required"
            isValid = false
        }

        if (experience.isEmpty()) {
            experienceLayout.error = "Experience is required"
            isValid = false
        }

        if (college.isEmpty()) {
            collegeLayout.error = "College name is required"
            isValid = false
        }

        if (stream.isEmpty()) {
            streamLayout.error = "Please select a stream"
            isValid = false
        }

        return isValid
    }

    private fun saveAdditionalDetails() {
        progressBar.visibility = View.VISIBLE
        completeButton.isEnabled = false

        val qualifications = qualificationsInput.text.toString().trim()
        val experience = experienceInput.text.toString().trim()
        val college = collegeInput.text.toString().trim()
        val stream = streamDropdown.text.toString().trim()

        val updates = hashMapOf<String, Any>(
            "qualifications" to qualifications,
            "experience" to experience,
            "college" to college,
            "stream" to stream,
            "profileCompleted" to true,
            "appAccessEnabled" to true,
            "isActive" to true,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("faculty")
            .document(facultyId)
            .update(updates)
            .addOnSuccessListener {
                // Also update users collection
                updateUsersCollection()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                completeButton.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("FacultyDetails", "Error saving details", e)
            }
    }

    private fun updateUsersCollection() {
        val userUpdates = hashMapOf<String, Any>(
            "profileCompleted" to true,
            "isFirstLogin" to false,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("users")
            .document(userId)
            .update(userUpdates)
            .addOnSuccessListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Profile completed successfully!", Toast.LENGTH_SHORT).show()
                navigateToFacultyDashboard()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                // Even if this fails, navigate to dashboard since faculty doc is updated
                Toast.makeText(this, "Profile saved! Logging in...", Toast.LENGTH_SHORT).show()
                navigateToFacultyDashboard()
            }
    }

    private fun navigateToFacultyDashboard() {
        val intent = Intent(this, FacultyDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    override fun onBackPressed() {
        // Prevent going back during profile setup
        Toast.makeText(this, "Please complete your profile to continue", Toast.LENGTH_SHORT).show()
    }
}