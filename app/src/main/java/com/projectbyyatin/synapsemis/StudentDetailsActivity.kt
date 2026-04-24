package com.projectbyyatin.synapsemis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Student
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.*

class StudentDetailsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var profileImage: CircleImageView
    private lateinit var statusBadge: ImageView
    private lateinit var studentName: TextView
    private lateinit var studentIdText: TextView
    private lateinit var rollNumber: TextView
    private lateinit var statusChip: Chip
    private lateinit var genderChip: Chip
    private lateinit var bloodGroupChip: Chip

    // Personal Info
    private lateinit var emailText: TextView
    private lateinit var phoneText: TextView
    private lateinit var dobText: TextView
    private lateinit var addressText: TextView
    private lateinit var parentNameText: TextView
    private lateinit var parentPhoneText: TextView

    // Academic Info
    private lateinit var departmentText: TextView
    private lateinit var courseText: TextView
    private lateinit var classText: TextView
    private lateinit var batchText: TextView
    private lateinit var academicYearText: TextView
    private lateinit var semesterText: TextView

    // Performance Stats
    private lateinit var attendancePercentage: TextView
    private lateinit var cgpaText: TextView
    private lateinit var sgpaText: TextView
    private lateinit var backlogsText: TextView

    // Action Buttons
    private lateinit var btnCall: MaterialButton
    private lateinit var btnEmail: MaterialButton
    private lateinit var btnCallParent: MaterialButton
    private lateinit var btnViewAttendance: MaterialCardView
    private lateinit var btnViewResults: MaterialCardView
    private lateinit var btnViewFees: MaterialCardView
    private lateinit var btnEditProfile: MaterialCardView

    private lateinit var progressBar: ProgressBar
    private lateinit var firestore: FirebaseFirestore

    private var studentDocId: String = ""
    private var student: Student? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_details)

        studentDocId = intent.getStringExtra("STUDENT_ID") ?: ""

        if (studentDocId.isEmpty()) {
            Toast.makeText(this, "Error: Student ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupClickListeners()
        loadStudentData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        profileImage = findViewById(R.id.profile_image)
        statusBadge = findViewById(R.id.profile_status_badge)
        studentName = findViewById(R.id.student_name)
        studentIdText = findViewById(R.id.student_id)
        rollNumber = findViewById(R.id.roll_number)
        statusChip = findViewById(R.id.status_chip)
        genderChip = findViewById(R.id.gender_chip)
        bloodGroupChip = findViewById(R.id.blood_group_chip)

        // Personal Info
        emailText = findViewById(R.id.email_text)
        phoneText = findViewById(R.id.phone_text)
        dobText = findViewById(R.id.dob_text)
        addressText = findViewById(R.id.address_text)
        parentNameText = findViewById(R.id.parent_name_text)
        parentPhoneText = findViewById(R.id.parent_phone_text)

        // Academic Info
        departmentText = findViewById(R.id.department_text)
        courseText = findViewById(R.id.course_text)
        classText = findViewById(R.id.class_text)
        batchText = findViewById(R.id.batch_text)
        academicYearText = findViewById(R.id.academic_year_text)
        semesterText = findViewById(R.id.semester_text)

        // Performance Stats
        attendancePercentage = findViewById(R.id.attendance_percentage)
        cgpaText = findViewById(R.id.cgpa_text)
        sgpaText = findViewById(R.id.sgpa_text)
        backlogsText = findViewById(R.id.backlogs_text)

        // Action Buttons
        btnCall = findViewById(R.id.btn_call)
        btnEmail = findViewById(R.id.btn_email)
        btnCallParent = findViewById(R.id.btn_call_parent)
        btnViewAttendance = findViewById(R.id.btn_view_attendance)
        btnViewResults = findViewById(R.id.btn_view_results)
        btnViewFees = findViewById(R.id.btn_view_fees)
        btnEditProfile = findViewById(R.id.btn_edit_profile)

        progressBar = findViewById(R.id.progress_bar)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Student Details"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupClickListeners() {
        btnCall.setOnClickListener {
            student?.let { makeCall(it.phoneNumber) }
        }

        btnEmail.setOnClickListener {
            student?.let { sendEmail(it.email) }
        }

        btnCallParent.setOnClickListener {
            student?.let {
                // Use parentPhoneNumber or parentPhone
                val phone = it.parentPhone.ifEmpty { it.parentPhoneNumber }
                makeCall(phone)
            }
        }

        btnViewAttendance.setOnClickListener {
            Toast.makeText(this, "View Attendance - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        btnViewResults.setOnClickListener {
            Toast.makeText(this, "View Results - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        btnViewFees.setOnClickListener {
            Toast.makeText(this, "View Fees - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        btnEditProfile.setOnClickListener {
            Toast.makeText(this, "Edit Profile - Coming Soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadStudentData() {
        progressBar.visibility = View.VISIBLE

        firestore.collection("students").document(studentDocId)
            .get()
            .addOnSuccessListener { document ->
                progressBar.visibility = View.GONE

                if (document != null && document.exists()) {
                    student = document.toObject(Student::class.java)
                    student?.let {
                        it.id = document.id
                        displayStudentData(it)
                    }
                } else {
                    Toast.makeText(this, "Student not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayStudentData(student: Student) {
        // Load profile image
        if (student.profileImageUrl.isNotEmpty()) {
            Picasso.get()
                .load(student.profileImageUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.ic_person)
        }

        // Status badge
        if (student.profileCompleted) {
            statusBadge.setImageResource(R.drawable.ic_check_circle)
            statusBadge.setColorFilter(getColor(R.color.splash_accent))
        } else {
            statusBadge.setImageResource(R.drawable.ic_pending)
            statusBadge.setColorFilter(getColor(android.R.color.holo_orange_light))
        }

        // Basic Info
        studentName.text = student.fullName.ifEmpty { "${student.firstName} ${student.lastName}".trim() }
        studentIdText.text = student.studentId.ifEmpty { "Not Assigned" }
        rollNumber.text = "Roll: ${student.rollNumber.ifEmpty { "N/A" }}"

        // Status chip
        statusChip.text = if (student.isActive) "Active" else "Inactive"
        statusChip.setChipBackgroundColorResource(
            if (student.isActive) android.R.color.holo_green_dark else android.R.color.holo_red_dark
        )

        // Gender chip
        genderChip.text = student.gender.ifEmpty { "N/A" }
        val genderColor = when (student.gender) {
            "Male" -> android.R.color.holo_blue_light
            "Female" -> android.R.color.holo_red_light
            else -> R.color.splash_text_secondary
        }
        genderChip.setChipBackgroundColorResource(genderColor)

        // Blood group chip
        bloodGroupChip.text = student.bloodGroup.ifEmpty { "N/A" }
        bloodGroupChip.setChipBackgroundColorResource(android.R.color.holo_red_dark)

        // Personal Info
        emailText.text = student.email
        phoneText.text = student.phoneNumber.ifEmpty { "Not Provided" }

        // Format date of birth
        if (student.dateOfBirth > 0) {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            dobText.text = sdf.format(Date(student.dateOfBirth))
        } else {
            dobText.text = "Not Provided"
        }

        addressText.text = if (student.address.isNotEmpty()) {
            buildString {
                append(student.address)
                if (student.city.isNotEmpty()) append(", ${student.city}")
                if (student.state.isNotEmpty()) append(", ${student.state}")
                if (student.pincode.isNotEmpty()) append(" - ${student.pincode}")
            }
        } else {
            "Not Provided"
        }

        parentNameText.text = student.parentName.ifEmpty { "Not Provided" }

        // Use parentPhone first, fallback to parentPhoneNumber
        val parentPhoneValue = student.parentPhone.ifEmpty { student.parentPhoneNumber }
        parentPhoneText.text = parentPhoneValue.ifEmpty { "Not Provided" }

        // Academic Info
        departmentText.text = student.departmentName.ifEmpty { "Not Assigned" }
        courseText.text = student.courseName.ifEmpty { "Not Assigned" }
        classText.text = student.className.ifEmpty { "Not Assigned" }
        batchText.text = student.batch.ifEmpty { "Not Assigned" }
        academicYearText.text = student.academicYear.ifEmpty { "Not Assigned" }
        semesterText.text = "Semester ${student.currentSemester}"

        // Performance Stats
        attendancePercentage.text = String.format("%.1f%%", student.totalAttendancePercentage)
        cgpaText.text = String.format("%.2f", student.cgpa)
        sgpaText.text = String.format("%.2f", student.sgpa)
        backlogsText.text = "${student.backlogs}"

        // Set color based on attendance
        val attendanceColor = when {
            student.totalAttendancePercentage >= 75 -> android.R.color.holo_green_dark
            student.totalAttendancePercentage >= 65 -> android.R.color.holo_orange_light
            else -> android.R.color.holo_red_dark
        }
        attendancePercentage.setTextColor(getColor(attendanceColor))

        // Disable buttons if info not available
        btnCall.isEnabled = student.phoneNumber.isNotEmpty()

        val hasParentPhone = student.parentPhone.isNotEmpty() || student.parentPhoneNumber.isNotEmpty()
        btnCallParent.isEnabled = hasParentPhone
    }

    private fun makeCall(phoneNumber: String) {
        if (phoneNumber.isEmpty()) {
            Toast.makeText(this, "Phone number not available", Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(Intent.ACTION_DIAL)
        intent.data = Uri.parse("tel:$phoneNumber")
        startActivity(intent)
    }

    private fun sendEmail(email: String) {
        val intent = Intent(Intent.ACTION_SENDTO)
        intent.data = Uri.parse("mailto:$email")
        intent.putExtra(Intent.EXTRA_SUBJECT, "Message from SYNAPSE MIS")
        startActivity(Intent.createChooser(intent, "Send Email"))
    }

    override fun onResume() {
        super.onResume()
        loadStudentData()
    }
}
