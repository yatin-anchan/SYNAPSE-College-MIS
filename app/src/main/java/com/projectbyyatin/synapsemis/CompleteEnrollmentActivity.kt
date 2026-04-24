package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.EnrollmentApplication
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CompleteEnrollmentActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var toolbar: Toolbar
    private lateinit var progressBar: ProgressBar

    // Step Cards
    private lateinit var step1Card: MaterialCardView
    private lateinit var step2Card: MaterialCardView
    private lateinit var step3Card: MaterialCardView
    private lateinit var step4Card: MaterialCardView

    // Progress Indicator
    private lateinit var tvProgress: TextView

    // Step 1: Personal Info
    private lateinit var phoneInput: TextInputEditText
    private lateinit var dobInput: TextInputEditText
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var bloodGroupDropdown: AutoCompleteTextView
    private var selectedDateOfBirth: Long = 0L

    // Step 2: Address
    private lateinit var addressInput: TextInputEditText
    private lateinit var cityInput: TextInputEditText
    private lateinit var stateInput: TextInputEditText
    private lateinit var pincodeInput: TextInputEditText

    // Step 3: Parent/Guardian
    private lateinit var parentNameInput: TextInputEditText
    private lateinit var parentPhoneInput: TextInputEditText
    private lateinit var parentEmailInput: TextInputEditText
    private lateinit var parentOccupationInput: TextInputEditText

    // Step 4: Academic History
    private lateinit var previousSchoolInput: TextInputEditText
    private lateinit var previousPercentageInput: TextInputEditText
    private lateinit var previousBoardInput: TextInputEditText
    private lateinit var previousYearInput: TextInputEditText

    // Buttons
    private lateinit var btnNext1: MaterialButton
    private lateinit var btnNext2: MaterialButton
    private lateinit var btnPrev2: MaterialButton
    private lateinit var btnNext3: MaterialButton
    private lateinit var btnPrev3: MaterialButton
    private lateinit var btnSubmit: MaterialButton
    private lateinit var btnPrev4: MaterialButton

    private var currentStep = 1
    private var applicationData: Map<String, Any>? = null
    private var applicationId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_enrollment)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        loadApplicationData()
        setupClickListeners()
        showStep(1)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        progressBar = findViewById(R.id.progress_bar)
        tvProgress = findViewById(R.id.tv_progress)

        step1Card = findViewById(R.id.step1_card)
        step2Card = findViewById(R.id.step2_card)
        step3Card = findViewById(R.id.step3_card)
        step4Card = findViewById(R.id.step4_card)

        // Step 1
        phoneInput = findViewById(R.id.phone_input)
        dobInput = findViewById(R.id.dob_input)
        genderDropdown = findViewById(R.id.gender_dropdown)
        bloodGroupDropdown = findViewById(R.id.blood_group_dropdown)

        // Step 2
        addressInput = findViewById(R.id.address_input)
        cityInput = findViewById(R.id.city_input)
        stateInput = findViewById(R.id.state_input)
        pincodeInput = findViewById(R.id.pincode_input)

        // Step 3
        parentNameInput = findViewById(R.id.parent_name_input)
        parentPhoneInput = findViewById(R.id.parent_phone_input)
        parentEmailInput = findViewById(R.id.parent_email_input)
        parentOccupationInput = findViewById(R.id.parent_occupation_input)

        // Step 4
        previousSchoolInput = findViewById(R.id.previous_school_input)
        previousPercentageInput = findViewById(R.id.previous_percentage_input)
        previousBoardInput = findViewById(R.id.previous_board_input)
        previousYearInput = findViewById(R.id.previous_year_input)

        // Buttons
        btnNext1 = findViewById(R.id.btn_next_1)
        btnNext2 = findViewById(R.id.btn_next_2)
        btnPrev2 = findViewById(R.id.btn_prev_2)
        btnNext3 = findViewById(R.id.btn_next_3)
        btnPrev3 = findViewById(R.id.btn_prev_3)
        btnSubmit = findViewById(R.id.btn_submit)
        btnPrev4 = findViewById(R.id.btn_prev_4)

        // Setup dropdowns
        val genders = arrayOf("Male", "Female", "Other")
        genderDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders))

        val bloodGroups = arrayOf("A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-")
        bloodGroupDropdown.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bloodGroups))

        toolbar.setNavigationOnClickListener {
            if (currentStep > 1) {
                showStep(currentStep - 1)
            } else {
                finish()
            }
        }
    }

    private fun loadApplicationData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        showLoading(true)

        firestore.collection("applications")
            .whereEqualTo("userId", currentUser.uid)
            .whereEqualTo("accountCreated", true)
            .whereEqualTo("profileCompleted", false)
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)

                if (documents.isEmpty) {
                    Toast.makeText(this, "No pending enrollment found", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val doc = documents.documents[0]
                    applicationId = doc.id
                    applicationData = doc.data
                    prefillData()
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CompleteEnrollment", "Error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun prefillData() {
        applicationData?.let { app ->
            phoneInput.setText(app["phone"] as? String ?: "")
        }
    }

    private fun setupClickListeners() {
        dobInput.setOnClickListener { showDatePicker() }

        btnNext1.setOnClickListener {
            if (validateStep1()) showStep(2)
        }

        btnNext2.setOnClickListener {
            if (validateStep2()) showStep(3)
        }

        btnPrev2.setOnClickListener {
            showStep(1)
        }

        btnNext3.setOnClickListener {
            if (validateStep3()) showStep(4)
        }

        btnPrev3.setOnClickListener {
            showStep(2)
        }

        btnSubmit.setOnClickListener {
            if (validateStep4()) submitEnrollment()
        }

        btnPrev4.setOnClickListener {
            showStep(3)
        }
    }

    private fun showStep(step: Int) {
        currentStep = step

        step1Card.visibility = if (step == 1) View.VISIBLE else View.GONE
        step2Card.visibility = if (step == 2) View.VISIBLE else View.GONE
        step3Card.visibility = if (step == 3) View.VISIBLE else View.GONE
        step4Card.visibility = if (step == 4) View.VISIBLE else View.GONE

        tvProgress.text = "Step $step of 4"
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) - 18
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        DatePickerDialog(this, { _, y, m, d ->
            calendar.set(y, m, d)
            selectedDateOfBirth = calendar.timeInMillis
            val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            dobInput.setText(dateFormat.format(calendar.time))
        }, year, month, day).show()
    }

    private fun validateStep1(): Boolean {
        val phone = phoneInput.text.toString().trim()
        val gender = genderDropdown.text.toString().trim()
        val bloodGroup = bloodGroupDropdown.text.toString().trim()

        if (phone.isEmpty() || phone.length != 10) {
            Toast.makeText(this, "Please enter valid phone number", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedDateOfBirth == 0L) {
            Toast.makeText(this, "Please select date of birth", Toast.LENGTH_SHORT).show()
            return false
        }

        if (gender.isEmpty()) {
            Toast.makeText(this, "Please select gender", Toast.LENGTH_SHORT).show()
            return false
        }

        if (bloodGroup.isEmpty()) {
            Toast.makeText(this, "Please select blood group", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun validateStep2(): Boolean {
        val address = addressInput.text.toString().trim()
        val city = cityInput.text.toString().trim()
        val state = stateInput.text.toString().trim()
        val pincode = pincodeInput.text.toString().trim()

        if (address.isEmpty() || city.isEmpty() || state.isEmpty() || pincode.length != 6) {
            Toast.makeText(this, "Please fill all address fields", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun validateStep3(): Boolean {
        val parentName = parentNameInput.text.toString().trim()
        val parentPhone = parentPhoneInput.text.toString().trim()

        if (parentName.isEmpty() || parentPhone.length != 10) {
            Toast.makeText(this, "Please fill parent/guardian details", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun validateStep4(): Boolean {
        val school = previousSchoolInput.text.toString().trim()
        val percentage = previousPercentageInput.text.toString().trim()

        if (school.isEmpty() || percentage.isEmpty()) {
            Toast.makeText(this, "Please fill academic history", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun submitEnrollment() {
        showLoading(true)

        val enrollmentApp = EnrollmentApplication(
            applicationId = applicationId,
            referenceNumber = applicationData?.get("referenceNumber") as? String ?: "",
            userId = auth.currentUser?.uid ?: "",

            // Personal Info
            firstName = applicationData?.get("firstName") as? String ?: "",
            lastName = applicationData?.get("lastName") as? String ?: "",
            fullName = applicationData?.get("fullName") as? String ?: "",
            email = applicationData?.get("email") as? String ?: "",
            phone = phoneInput.text.toString().trim(),
            parentPhone = parentPhoneInput.text.toString().trim(),
            dateOfBirth = selectedDateOfBirth,
            gender = genderDropdown.text.toString().trim(),
            bloodGroup = bloodGroupDropdown.text.toString().trim(),

            // Address
            address = addressInput.text.toString().trim(),
            city = cityInput.text.toString().trim(),
            state = stateInput.text.toString().trim(),
            pincode = pincodeInput.text.toString().trim(),

            // Parent Info
            parentName = parentNameInput.text.toString().trim(),
            parentOccupation = parentOccupationInput.text.toString().trim(),
            parentEmail = parentEmailInput.text.toString().trim(),

            // Academic History
            previousSchool = previousSchoolInput.text.toString().trim(),
            previousPercentage = previousPercentageInput.text.toString().toDoubleOrNull() ?: 0.0,
            previousBoard = previousBoardInput.text.toString().trim(),
            previousYear = previousYearInput.text.toString().toIntOrNull() ?: 0,

            // Applied For
            appliedFor = applicationData?.get("appliedFor") as? String ?: "",
            courseId = applicationData?.get("courseId") as? String ?: "",
            courseName = applicationData?.get("courseName") as? String ?: "",

            status = "profile_submitted",
            submittedDate = System.currentTimeMillis()
        )

        // Create enrollment_applications document
        firestore.collection("enrollment_applications")
            .add(enrollmentApp)
            .addOnSuccessListener { docRef ->
                Log.d("CompleteEnrollment", "Enrollment created: ${docRef.id}")

                // Update original application
                updateOriginalApplication()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CompleteEnrollment", "Error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateOriginalApplication() {
        firestore.collection("applications")
            .document(applicationId)
            .update("profileCompleted", true)
            .addOnSuccessListener {
                showLoading(false)
                showSuccessDialog()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CompleteEnrollment", "Error updating: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showSuccessDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Profile Submitted!")
            .setMessage("Your enrollment profile has been submitted successfully. You will be notified once it's approved by the administration.")
            .setPositiveButton("OK") { _, _ ->
                // Navigate to applicant dashboard or login
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
    }
}
