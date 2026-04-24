package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Application
import com.projectbyyatin.synapsemis.utils.ImageUploadHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID

class ApplicationFormActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore

    private lateinit var toolbar: Toolbar
    private lateinit var firstNameInput: TextInputEditText
    private lateinit var lastNameInput: TextInputEditText
    private lateinit var emailInput: TextInputEditText
    private lateinit var phoneInput: TextInputEditText
    private lateinit var dobInput: TextInputEditText

    // Academic fields
    private lateinit var tenthPercentageInput: TextInputEditText
    private lateinit var tenthBoardInput: TextInputEditText
    private lateinit var tenthYearInput: TextInputEditText
    private lateinit var twelfthPercentageInput: TextInputEditText
    private lateinit var twelfthBoardInput: TextInputEditText
    private lateinit var twelfthYearInput: TextInputEditText
    private lateinit var streamDropdown: AutoCompleteTextView

    private lateinit var courseDropdown: AutoCompleteTextView
    private lateinit var batchDropdown: AutoCompleteTextView

    // Document upload buttons
    private lateinit var btnUploadPhoto: MaterialButton
    private lateinit var btnUploadTenthMarksheet: MaterialButton
    private lateinit var btnUploadTwelfthMarksheet: MaterialButton

    // Status TextViews
    private lateinit var tvPhotoStatus: TextView
    private lateinit var tvTenthMarksheetStatus: TextView
    private lateinit var tvTwelfthMarksheetStatus: TextView

    private lateinit var submitButton: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var trackApplicationText: TextView

    private var selectedDateOfBirth: Long = 0L
    private var selectedCourseId: String = ""
    private val courses = mutableListOf<Pair<String, String>>()

    // Document URIs
    private var photoUri: Uri? = null
    private var tenthMarksheetUri: Uri? = null
    private var twelfthMarksheetUri: Uri? = null

    // Uploaded URLs
    private var photoUrl: String = ""
    private var tenthMarksheetUrl: String = ""
    private var twelfthMarksheetUrl: String = ""

    // Image pickers
    private val photoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            photoUri = it
            tvPhotoStatus.text = "✓ Photo selected"
            tvPhotoStatus.setTextColor(getColor(android.R.color.holo_green_light))
        }
    }

    private val tenthMarksheetPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            tenthMarksheetUri = it
            tvTenthMarksheetStatus.text = "✓ 10th Marksheet selected"
            tvTenthMarksheetStatus.setTextColor(getColor(android.R.color.holo_green_light))
        }
    }

    private val twelfthMarksheetPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            twelfthMarksheetUri = it
            tvTwelfthMarksheetStatus.text = "✓ 12th Marksheet selected"
            tvTwelfthMarksheetStatus.setTextColor(getColor(android.R.color.holo_green_light))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_application_form)

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupStreamDropdown()
        setupBatchDropdown()
        loadCourses()
        setupClickListeners()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        firstNameInput = findViewById(R.id.first_name_input)
        lastNameInput = findViewById(R.id.last_name_input)
        emailInput = findViewById(R.id.email_input)
        phoneInput = findViewById(R.id.phone_input)
        dobInput = findViewById(R.id.dob_input)

        tenthPercentageInput = findViewById(R.id.tenth_percentage_input)
        tenthBoardInput = findViewById(R.id.tenth_board_input)
        tenthYearInput = findViewById(R.id.tenth_year_input)
        twelfthPercentageInput = findViewById(R.id.twelfth_percentage_input)
        twelfthBoardInput = findViewById(R.id.twelfth_board_input)
        twelfthYearInput = findViewById(R.id.twelfth_year_input)
        streamDropdown = findViewById(R.id.stream_dropdown)

        courseDropdown = findViewById(R.id.course_dropdown)
        batchDropdown = findViewById(R.id.batch_dropdown)

        btnUploadPhoto = findViewById(R.id.btn_upload_photo)
        btnUploadTenthMarksheet = findViewById(R.id.btn_upload_tenth_marksheet)
        btnUploadTwelfthMarksheet = findViewById(R.id.btn_upload_twelfth_marksheet)

        tvPhotoStatus = findViewById(R.id.tv_photo_status)
        tvTenthMarksheetStatus = findViewById(R.id.tv_tenth_marksheet_status)
        tvTwelfthMarksheetStatus = findViewById(R.id.tv_twelfth_marksheet_status)

        submitButton = findViewById(R.id.btn_submit)
        progressBar = findViewById(R.id.progress_bar)
        trackApplicationText = findViewById(R.id.tv_track_application)
    }

    private fun setupStreamDropdown() {
        val streams = listOf("Science", "Commerce", "Arts")
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, streams)
        streamDropdown.setAdapter(adapter)
        streamDropdown.threshold = 0

        streamDropdown.setOnClickListener {
            streamDropdown.showDropDown()
        }
    }

    private fun setupBatchDropdown() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val batches = listOf(
            "$currentYear-${currentYear + 1}",
            "${currentYear + 1}-${currentYear + 2}"
        )

        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, batches)
        batchDropdown.setAdapter(adapter)
        batchDropdown.threshold = 0
        batchDropdown.setText(batches[0], false)

        batchDropdown.setOnClickListener {
            batchDropdown.showDropDown()
        }
    }

    private fun loadCourses() {
        Log.d("ApplicationForm", "Starting to load courses...")

        firestore.collection("courses")
            .get()
            .addOnSuccessListener { documents ->
                courses.clear()

                for (doc in documents) {
                    val courseId = doc.id
                    val courseName = doc.getString("name") ?: ""
                    if (courseName.isNotEmpty()) {
                        courses.add(Pair(courseId, courseName))
                    }
                }

                if (courses.isEmpty()) {
                    Toast.makeText(this, "No courses available", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val courseNames = courses.map { it.second }
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    courseNames
                )

                courseDropdown.setAdapter(adapter)
                courseDropdown.threshold = 0

                Toast.makeText(this, "Loaded ${courses.size} courses", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun setupClickListeners() {
        dobInput.setOnClickListener {
            showDatePicker()
        }

        courseDropdown.setOnClickListener {
            courseDropdown.showDropDown()
        }

        courseDropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus && courses.isNotEmpty()) {
                courseDropdown.showDropDown()
            }
        }

        courseDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCourseId = courses[position].first
            Log.d("ApplicationForm", "Selected course: ${courses[position].second}")
        }

        btnUploadPhoto.setOnClickListener {
            photoPickerLauncher.launch("image/*")
        }

        btnUploadTenthMarksheet.setOnClickListener {
            tenthMarksheetPickerLauncher.launch("image/*")
        }

        btnUploadTwelfthMarksheet.setOnClickListener {
            twelfthMarksheetPickerLauncher.launch("image/*")
        }

        submitButton.setOnClickListener {
            submitApplication()
        }

        trackApplicationText.setOnClickListener {
            startActivity(Intent(this, TrackApplicationActivity::class.java))
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) - 18
        val month = calendar.get(Calendar.MONTH)
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val datePicker = DatePickerDialog(
            this,
            { _, selectedYear, selectedMonth, selectedDay ->
                calendar.set(selectedYear, selectedMonth, selectedDay)
                selectedDateOfBirth = calendar.timeInMillis

                val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                dobInput.setText(dateFormat.format(calendar.time))
            },
            year,
            month,
            day
        )

        val maxCalendar = Calendar.getInstance()
        maxCalendar.add(Calendar.YEAR, -15)
        datePicker.datePicker.maxDate = maxCalendar.timeInMillis

        val minCalendar = Calendar.getInstance()
        minCalendar.add(Calendar.YEAR, -50)
        datePicker.datePicker.minDate = minCalendar.timeInMillis

        datePicker.show()
    }

    private fun submitApplication() {
        val firstName = firstNameInput.text.toString().trim()
        val lastName = lastNameInput.text.toString().trim()
        val email = emailInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val courseName = courseDropdown.text.toString().trim()
        val batch = batchDropdown.text.toString().trim()

        val tenthPercentage = tenthPercentageInput.text.toString().trim()
        val tenthBoard = tenthBoardInput.text.toString().trim()
        val tenthYear = tenthYearInput.text.toString().trim()
        val twelfthPercentage = twelfthPercentageInput.text.toString().trim()
        val twelfthBoard = twelfthBoardInput.text.toString().trim()
        val twelfthYear = twelfthYearInput.text.toString().trim()
        val stream = streamDropdown.text.toString().trim()

        if (!validateInputs(firstName, lastName, email, phone, courseName, batch,
                tenthPercentage, tenthBoard, tenthYear, twelfthPercentage, twelfthBoard, twelfthYear, stream)) {
            return
        }

        showLoading(true)

        // Upload images to ImgBB
        uploadImagesToImgBB(firstName, lastName, email, phone, courseName, batch,
            tenthPercentage.toDouble(), tenthBoard, tenthYear.toInt(),
            twelfthPercentage.toDouble(), twelfthBoard, twelfthYear.toInt(), stream)
    }

    private fun uploadImagesToImgBB(
        firstName: String, lastName: String, email: String, phone: String,
        courseName: String, batch: String, tenthPercentage: Double, tenthBoard: String,
        tenthYear: Int, twelfthPercentage: Double, twelfthBoard: String,
        twelfthYear: Int, stream: String
    ) {
        var uploadedCount = 0
        val totalUploads = 3

        fun checkAndSubmit() {
            uploadedCount++
            if (uploadedCount == totalUploads) {
                saveApplication(firstName, lastName, email, phone, courseName, batch,
                    tenthPercentage, tenthBoard, tenthYear, twelfthPercentage,
                    twelfthBoard, twelfthYear, stream)
            }
        }

        // Upload photo
        photoUri?.let { uri ->
            ImageUploadHelper.uploadImage(
                this,
                uri,
                onSuccess = { url ->
                    photoUrl = url
                    runOnUiThread {
                        tvPhotoStatus.text = "✓ Photo uploaded"
                    }
                    checkAndSubmit()
                },
                onFailure = { error ->
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this, "Photo upload failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } ?: run {
            Toast.makeText(this, "Please select a photo", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        // Upload 10th marksheet
        tenthMarksheetUri?.let { uri ->
            ImageUploadHelper.uploadImage(
                this,
                uri,
                onSuccess = { url ->
                    tenthMarksheetUrl = url
                    runOnUiThread {
                        tvTenthMarksheetStatus.text = "✓ 10th Marksheet uploaded"
                    }
                    checkAndSubmit()
                },
                onFailure = { error ->
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this, "10th marksheet upload failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } ?: run {
            Toast.makeText(this, "Please select 10th marksheet", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }

        // Upload 12th marksheet
        twelfthMarksheetUri?.let { uri ->
            ImageUploadHelper.uploadImage(
                this,
                uri,
                onSuccess = { url ->
                    twelfthMarksheetUrl = url
                    runOnUiThread {
                        tvTwelfthMarksheetStatus.text = "✓ 12th Marksheet uploaded"
                    }
                    checkAndSubmit()
                },
                onFailure = { error ->
                    runOnUiThread {
                        showLoading(false)
                        Toast.makeText(this, "12th marksheet upload failed: $error", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } ?: run {
            Toast.makeText(this, "Please select 12th marksheet", Toast.LENGTH_SHORT).show()
            showLoading(false)
            return
        }
    }

    private fun saveApplication(
        firstName: String, lastName: String, email: String, phone: String,
        courseName: String, batch: String, tenthPercentage: Double, tenthBoard: String,
        tenthYear: Int, twelfthPercentage: Double, twelfthBoard: String,
        twelfthYear: Int, stream: String
    ) {
        val referenceNumber = generateReferenceNumber()

        val application = Application(
            applicationId = UUID.randomUUID().toString(),
            referenceNumber = referenceNumber,
            firstName = firstName,
            lastName = lastName,
            fullName = "$firstName $lastName",
            email = email,
            phone = phone,
            dateOfBirth = selectedDateOfBirth,
            tenthPercentage = tenthPercentage,
            tenthBoard = tenthBoard,
            tenthYear = tenthYear,
            twelfthPercentage = twelfthPercentage,
            twelfthBoard = twelfthBoard,
            twelfthYear = twelfthYear,
            stream = stream,
            appliedFor = courseName,
            courseId = selectedCourseId,
            courseName = courseName,
            preferredBatch = batch,
            photoUrl = photoUrl,
            tenthMarksheetUrl = tenthMarksheetUrl,
            twelfthMarksheetUrl = twelfthMarksheetUrl,
            status = "pending",
            appliedDate = System.currentTimeMillis()
        )

        firestore.collection("applications")
            .add(application)
            .addOnSuccessListener { documentReference ->
                showLoading(false)
                Log.d("ApplicationForm", "Application submitted: ${documentReference.id}")
                showSuccessDialog(referenceNumber)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("ApplicationForm", "Error submitting application", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun validateInputs(
        firstName: String, lastName: String, email: String, phone: String,
        courseName: String, batch: String, tenthPercentage: String, tenthBoard: String,
        tenthYear: String, twelfthPercentage: String, twelfthBoard: String,
        twelfthYear: String, stream: String
    ): Boolean {
        var isValid = true

        if (firstName.isEmpty()) {
            firstNameInput.error = "First name is required"
            isValid = false
        }

        if (lastName.isEmpty()) {
            lastNameInput.error = "Last name is required"
            isValid = false
        }

        if (email.isEmpty()) {
            emailInput.error = "Email is required"
            isValid = false
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.error = "Invalid email format"
            isValid = false
        }

        if (phone.isEmpty()) {
            phoneInput.error = "Phone number is required"
            isValid = false
        } else if (phone.length != 10) {
            phoneInput.error = "Invalid phone number"
            isValid = false
        }

        if (selectedDateOfBirth == 0L) {
            dobInput.error = "Date of birth is required"
            isValid = false
        }

        if (tenthPercentage.isEmpty()) {
            tenthPercentageInput.error = "Required"
            isValid = false
        } else if (tenthPercentage.toDoubleOrNull() == null || tenthPercentage.toDouble() !in 0.0..100.0) {
            tenthPercentageInput.error = "Invalid percentage"
            isValid = false
        }

        if (tenthBoard.isEmpty()) {
            tenthBoardInput.error = "Required"
            isValid = false
        }

        if (tenthYear.isEmpty()) {
            tenthYearInput.error = "Required"
            isValid = false
        }

        if (twelfthPercentage.isEmpty()) {
            twelfthPercentageInput.error = "Required"
            isValid = false
        } else if (twelfthPercentage.toDoubleOrNull() == null || twelfthPercentage.toDouble() !in 0.0..100.0) {
            twelfthPercentageInput.error = "Invalid percentage"
            isValid = false
        }

        if (twelfthBoard.isEmpty()) {
            twelfthBoardInput.error = "Required"
            isValid = false
        }

        if (twelfthYear.isEmpty()) {
            twelfthYearInput.error = "Required"
            isValid = false
        }

        if (stream.isEmpty()) {
            streamDropdown.error = "Please select a stream"
            isValid = false
        }

        if (courseName.isEmpty()) {
            courseDropdown.error = "Please select a course"
            isValid = false
        }

        if (batch.isEmpty()) {
            batchDropdown.error = "Batch is required"
            isValid = false
        }

        if (photoUri == null) {
            Toast.makeText(this, "Please upload your photo", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (tenthMarksheetUri == null) {
            Toast.makeText(this, "Please upload 10th marksheet", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (twelfthMarksheetUri == null) {
            Toast.makeText(this, "Please upload 12th marksheet", Toast.LENGTH_SHORT).show()
            isValid = false
        }

        if (!isValid) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
        }

        return isValid
    }

    private fun generateReferenceNumber(): String {
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val randomNum = (100000..999999).random()
        return "REF-$year-$randomNum"
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        submitButton.isEnabled = !show
    }

    private fun showSuccessDialog(referenceNumber: String) {
        val intent = Intent(this, ApplicationSuccessActivity::class.java)
        intent.putExtra("REFERENCE_NUMBER", referenceNumber)
        startActivity(intent)
        finish()
    }
}
