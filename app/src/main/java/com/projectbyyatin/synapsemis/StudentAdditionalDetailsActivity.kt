package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Student
import java.util.*

class StudentAdditionalDetailsActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // UI Elements
    private lateinit var phoneInput: TextInputEditText
    private lateinit var dobInput: TextInputEditText
    private lateinit var genderDropdown: AutoCompleteTextView
    private lateinit var bloodGroupDropdown: AutoCompleteTextView
    private lateinit var addressInput: TextInputEditText
    private lateinit var cityInput: TextInputEditText
    private lateinit var stateInput: TextInputEditText
    private lateinit var pincodeInput: TextInputEditText
    private lateinit var parentNameInput: TextInputEditText
    private lateinit var parentPhoneInput: TextInputEditText
    private lateinit var submitButton: Button
    private lateinit var progressBar: ProgressBar

    private var userId: String = ""
    private var selectedDateOfBirth: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_additional_details)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        userId = auth.currentUser?.uid ?: ""

        initializeViews()
        setupSpinners()
        setupClickListeners()
        loadExistingData()
    }

    private fun initializeViews() {
        phoneInput = findViewById(R.id.phone_input)
        dobInput = findViewById(R.id.dob_input)
        genderDropdown = findViewById(R.id.gender_dropdown)
        bloodGroupDropdown = findViewById(R.id.blood_group_dropdown)
        addressInput = findViewById(R.id.address_input)
        cityInput = findViewById(R.id.city_input)
        stateInput = findViewById(R.id.state_input)
        pincodeInput = findViewById(R.id.pincode_input)
        parentNameInput = findViewById(R.id.parent_name_input)
        parentPhoneInput = findViewById(R.id.parent_phone_input)
        submitButton = findViewById(R.id.submit_button)
        progressBar = findViewById(R.id.progress_bar)
    }

    private fun setupSpinners() {
        // Gender Dropdown
        val genders = listOf("Male", "Female", "Other")
        genderDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        )

        // Blood Group Dropdown
        val bloodGroups = listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")
        bloodGroupDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, bloodGroups)
        )
    }

    private fun setupClickListeners() {
        dobInput.setOnClickListener {
            showDatePicker()
        }

        submitButton.setOnClickListener {
            if (validateInputs()) {
                saveAdditionalDetails()
            }
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                selectedDateOfBirth = calendar.timeInMillis
                dobInput.setText("$day/${month + 1}/$year")
            },
            calendar.get(Calendar.YEAR) - 18,
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadExistingData() {
        progressBar.visibility = View.VISIBLE

        firestore.collection("students").document(userId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document.exists()) {
                    val student = document.toObject(Student::class.java)
                    student?.let {
                        // Pre-fill if data exists
                        phoneInput.setText(it.phoneNumber)
                        addressInput.setText(it.address)
                        cityInput.setText(it.city)
                        stateInput.setText(it.state)
                        pincodeInput.setText(it.pincode)
                        parentNameInput.setText(it.parentName)
                        parentPhoneInput.setText(it.parentPhone)

                        if (it.dateOfBirth > 0) {
                            selectedDateOfBirth = it.dateOfBirth
                            val cal = Calendar.getInstance()
                            cal.timeInMillis = it.dateOfBirth
                            dobInput.setText(
                                "${cal.get(Calendar.DAY_OF_MONTH)}/${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.YEAR)}"
                            )
                        }
                    }
                }
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error loading data", Toast.LENGTH_SHORT).show()
            }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (phoneInput.text.toString().trim().length != 10) {
            phoneInput.error = "Enter valid 10-digit phone number"
            isValid = false
        }

        if (selectedDateOfBirth == 0L) {
            Toast.makeText(this, "Please select date of birth", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (genderDropdown.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select gender", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (bloodGroupDropdown.text.toString().trim().isEmpty()) {
            Toast.makeText(this, "Please select blood group", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (addressInput.text.toString().trim().isEmpty()) {
            addressInput.error = "Address is required"
            isValid = false
        }

        if (cityInput.text.toString().trim().isEmpty()) {
            cityInput.error = "City is required"
            isValid = false
        }

        if (stateInput.text.toString().trim().isEmpty()) {
            stateInput.error = "State is required"
            isValid = false
        }

        if (pincodeInput.text.toString().trim().length != 6) {
            pincodeInput.error = "Enter valid 6-digit pincode"
            isValid = false
        }

        if (parentNameInput.text.toString().trim().isEmpty()) {
            parentNameInput.error = "Parent/Guardian name is required"
            isValid = false
        }

        if (parentPhoneInput.text.toString().trim().length != 10) {
            parentPhoneInput.error = "Enter valid 10-digit phone number"
            isValid = false
        }

        return isValid
    }

    private fun saveAdditionalDetails() {
        progressBar.visibility = View.VISIBLE
        submitButton.isEnabled = false

        val updates = hashMapOf<String, Any>(
            "phoneNumber" to phoneInput.text.toString().trim(),
            "dateOfBirth" to selectedDateOfBirth,
            "gender" to genderDropdown.text.toString(),
            "bloodGroup" to bloodGroupDropdown.text.toString(),
            "address" to addressInput.text.toString().trim(),
            "city" to cityInput.text.toString().trim(),
            "state" to stateInput.text.toString().trim(),
            "pincode" to pincodeInput.text.toString().trim(),
            "parentName" to parentNameInput.text.toString().trim(),
            "parentPhone" to parentPhoneInput.text.toString().trim(),
            "parentPhoneNumber" to parentPhoneInput.text.toString().trim(),
            "profileCompleted" to true,
            "isActive" to true,
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("students").document(userId)
            .update(updates)
            .addOnSuccessListener {
                // Update user document
                firestore.collection("users").document(userId)
                    .update(
                        mapOf(
                            "profileCompleted" to true,
                            "isFirstLogin" to false
                        )
                    )
                    .addOnSuccessListener {
                        progressBar.visibility = View.GONE
                        Toast.makeText(
                            this,
                            "Profile completed successfully!",
                            Toast.LENGTH_SHORT
                        ).show()

                        // NOW navigate to dashboard
                        val intent = Intent(this, StudentDashboardActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                submitButton.isEnabled = true
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
