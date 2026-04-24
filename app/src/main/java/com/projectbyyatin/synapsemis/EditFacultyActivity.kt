package com.projectbyyatin.synapsemis

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Faculty
import com.projectbyyatin.synapsemis.models.Department
import com.projectbyyatin.synapsemis.utils.ImageUploadHelper
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class EditFacultyActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var profileImage: CircleImageView
    private lateinit var btnChangePhoto: MaterialButton
    private lateinit var employeeIdInput: TextInputEditText
    private lateinit var nameInput: TextInputEditText
    private lateinit var nameLayout: TextInputLayout
    private lateinit var emailInput: TextInputEditText
    private lateinit var mobileInput: TextInputEditText
    private lateinit var mobileLayout: TextInputLayout
    private lateinit var departmentDropdown: AutoCompleteTextView
    private lateinit var designationInput: TextInputEditText
    private lateinit var qualificationsInput: TextInputEditText
    private lateinit var experienceInput: TextInputEditText
    private lateinit var subjectsChipGroup: ChipGroup
    private lateinit var noSubjectsText: TextView
    private lateinit var btnUpdateFaculty: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private val departmentsList = mutableListOf<String>()
    private val departmentsMap = mutableMapOf<String, Department>() // Map name to department
    private var subjectsList = mutableListOf<String>()
    private var facultyId: String = ""
    private var selectedImageUri: Uri? = null
    private var currentPhotoUrl: String = ""
    private var selectedDepartmentId: String = ""
    private var selectedDepartmentName: String = ""

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            selectedImageUri?.let {
                profileImage.setImageURI(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_faculty)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""

        if (facultyId.isEmpty()) {
            Toast.makeText(this, "Error: Faculty ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupBackButton()
        loadDepartments()
        setupPhotoUpload()
        setupUpdateButton()
        setupDepartmentDropdown()
        loadFacultyData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        profileImage = findViewById(R.id.profile_image)
        btnChangePhoto = findViewById(R.id.btn_change_photo)
        employeeIdInput = findViewById(R.id.employee_id_input)
        nameInput = findViewById(R.id.name_input)
        nameLayout = findViewById(R.id.name_layout)
        emailInput = findViewById(R.id.email_input)
        mobileInput = findViewById(R.id.mobile_input)
        mobileLayout = findViewById(R.id.mobile_layout)
        departmentDropdown = findViewById(R.id.department_dropdown)
        designationInput = findViewById(R.id.designation_input)
        qualificationsInput = findViewById(R.id.qualifications_input)
        experienceInput = findViewById(R.id.experience_input)
        subjectsChipGroup = findViewById(R.id.subjects_chip_group)
        noSubjectsText = findViewById(R.id.no_subjects_text)
        btnUpdateFaculty = findViewById(R.id.btn_update_faculty)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })
    }

    private fun loadDepartments() {
        departmentsList.clear()
        departmentsMap.clear()
        departmentsList.add("None")

        firestore.collection("departments")
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { document ->
                    val dept = document.toObject(Department::class.java)
                    dept.id = document.id
                    departmentsList.add(dept.name)
                    departmentsMap[dept.name] = dept
                }

                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    departmentsList
                )
                departmentDropdown.setAdapter(adapter)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error loading departments", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupDepartmentDropdown() {
        departmentDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedDept = departmentsList[position]

            if (selectedDept == "None") {
                selectedDepartmentId = ""
                selectedDepartmentName = ""
            } else {
                val dept = departmentsMap[selectedDept]
                selectedDepartmentId = dept?.id ?: ""
                selectedDepartmentName = dept?.name ?: ""
            }
        }
    }

    private fun setupPhotoUpload() {
        btnChangePhoto.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            imagePickerLauncher.launch(intent)
        }
    }

    private fun loadFacultyData() {
        showLoading(true)

        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                val faculty = document.toObject(Faculty::class.java)
                faculty?.let {
                    populateFields(it)
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun populateFields(faculty: Faculty) {
        employeeIdInput.setText(faculty.employeeId)
        nameInput.setText(faculty.name)
        emailInput.setText(faculty.email)
        mobileInput.setText(faculty.phone)

        // Set department
        selectedDepartmentId = faculty.departmentId
        selectedDepartmentName = faculty.department
        departmentDropdown.setText(
            if (faculty.department.isEmpty()) "None" else faculty.department,
            false
        )

        designationInput.setText(faculty.designation)
        qualificationsInput.setText(faculty.qualifications)
        experienceInput.setText(faculty.experience)

        currentPhotoUrl = faculty.photoUrl
        if (currentPhotoUrl.isNotEmpty()) {
            Picasso.get()
                .load(currentPhotoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImage)
        }

        // Load subjects (read-only display)
        subjectsList.clear()
        subjectsList.addAll(faculty.subjects)
        subjectsChipGroup.removeAllViews()

        if (faculty.subjects.isEmpty()) {
            noSubjectsText.visibility = View.VISIBLE
        } else {
            noSubjectsText.visibility = View.GONE
            faculty.subjects.forEach { subject ->
                displaySubjectChip(subject)
            }
        }
    }

    private fun displaySubjectChip(subject: String) {
        val chip = Chip(this).apply {
            text = subject
            isClickable = false
            isCloseIconVisible = false // Read-only chip
            setChipBackgroundColorResource(R.color.splash_accent)
            setTextColor(getColor(android.R.color.white))
        }
        subjectsChipGroup.addView(chip)
    }

    private fun setupUpdateButton() {
        btnUpdateFaculty.setOnClickListener {
            if (validateInputs()) {
                updateFaculty()
            }
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        nameLayout.error = null
        mobileLayout.error = null

        val name = nameInput.text.toString().trim()
        val mobile = mobileInput.text.toString().trim()

        if (name.isEmpty()) {
            nameLayout.error = "Name is required"
            isValid = false
        }

        if (mobile.isEmpty()) {
            mobileLayout.error = "Mobile number is required"
            isValid = false
        } else if (mobile.length != 10) {
            mobileLayout.error = "Mobile number must be 10 digits"
            isValid = false
        }

        return isValid
    }

    private fun updateFaculty() {
        showLoading(true)

        if (selectedImageUri != null) {
            uploadImageAndUpdateFaculty()
        } else {
            updateFacultyInFirestore(currentPhotoUrl)
        }
    }

    private fun uploadImageAndUpdateFaculty() {
        selectedImageUri?.let { uri ->
            ImageUploadHelper.uploadImage(
                context = this,
                imageUri = uri,
                onSuccess = { imageUrl ->
                    runOnUiThread {
                        updateFacultyInFirestore(imageUrl)
                    }
                },
                onFailure = { error ->
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    private fun updateFacultyInFirestore(photoUrl: String) {
        val updates = hashMapOf<String, Any>(
            "name" to nameInput.text.toString().trim(),
            "phone" to mobileInput.text.toString().trim(),
            "department" to selectedDepartmentName,
            "departmentId" to selectedDepartmentId,
            "designation" to designationInput.text.toString().trim(),
            "qualifications" to qualificationsInput.text.toString().trim(),
            "experience" to experienceInput.text.toString().trim(),
            "photoUrl" to photoUrl,
            "profileCompleted" to true
        )

        // Note: subjects are NOT updated here - they're managed elsewhere

        firestore.collection("faculty").document(facultyId)
            .update(updates)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Faculty updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnUpdateFaculty.isEnabled = !show
        btnChangePhoto.isEnabled = !show
    }
}
