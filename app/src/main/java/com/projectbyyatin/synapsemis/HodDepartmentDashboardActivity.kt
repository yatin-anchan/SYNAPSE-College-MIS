package com.projectbyyatin.synapsemis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Department
import com.projectbyyatin.synapsemis.models.Faculty
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class HodDepartmentDashboardActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var departmentImage: ImageView
    private lateinit var departmentName: TextView
    private lateinit var departmentCode: TextView
    private lateinit var collegeChip: Chip
    private lateinit var streamChip: Chip
    private lateinit var hodName: TextView
    private lateinit var hodClickArea: LinearLayout
    private lateinit var facultyCount: TextView
    private lateinit var studentCount: TextView
    private lateinit var establishedYear: TextView
    private lateinit var description: TextView

    // Action Cards for HOD
    private lateinit var cardViewFaculty: MaterialCardView
    private lateinit var cardViewStudents: MaterialCardView
    private lateinit var cardAttendanceReports: MaterialCardView
    private lateinit var cardTimetable: MaterialCardView
    private lateinit var cardLeaveApprovals: MaterialCardView
    private lateinit var cardViewClasses: MaterialCardView
    private lateinit var cardDepartmentStats: MaterialCardView

    private lateinit var loadingProgress: ProgressBar
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var departmentId: String = ""
    private var department: Department? = null
    private var hodId: String = ""
    private var isCurrentUserHOD: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_department_dashboard)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        if (departmentId.isEmpty()) {
            loadHodDepartment()
        } else {
            initializeViews()
            setupToolbar()
            setupActionCards()
            setupHODClickListener()
            loadDepartmentData()
        }
    }

    private fun loadHodDepartment() {
        val progressBar = ProgressBar(this)
        val dialog = AlertDialog.Builder(this)
            .setView(progressBar)
            .setMessage("Loading department...")
            .setCancelable(false)
            .create()
        dialog.show()

        auth.currentUser?.let { user ->
            firestore.collection("faculty")
                .whereEqualTo("email", user.email)
                .whereEqualTo("role", "hod")
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    dialog.dismiss()
                    if (!querySnapshot.isEmpty) {
                        val hodDoc = querySnapshot.documents[0]
                        departmentId = hodDoc.getString("departmentId") ?: ""
                        hodId = hodDoc.id

                        if (departmentId.isNotEmpty()) {
                            isCurrentUserHOD = true
                            initializeViews()
                            setupToolbar()
                            setupActionCards()
                            setupHODClickListener()
                            loadDepartmentData()
                        } else {
                            Toast.makeText(this, "No department assigned to your account", Toast.LENGTH_LONG).show()
                            finish()
                        }
                    } else {
                        Toast.makeText(this, "HOD profile not found", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    dialog.dismiss()
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                }
        }
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        departmentImage = findViewById(R.id.department_image)
        departmentName = findViewById(R.id.department_name)
        departmentCode = findViewById(R.id.department_code)
        collegeChip = findViewById(R.id.college_chip)
        streamChip = findViewById(R.id.stream_chip)
        hodName = findViewById(R.id.hod_name)
        hodClickArea = findViewById(R.id.hod_click_area)
        facultyCount = findViewById(R.id.faculty_count)
        studentCount = findViewById(R.id.student_count)
        establishedYear = findViewById(R.id.established_year)
        description = findViewById(R.id.description)

        cardViewFaculty = findViewById(R.id.card_view_faculty)
        cardViewStudents = findViewById(R.id.card_view_students)
        cardAttendanceReports = findViewById(R.id.card_attendance_reports)
        cardTimetable = findViewById(R.id.card_timetable)
        cardLeaveApprovals = findViewById(R.id.card_leave_approvals)
        cardViewClasses = findViewById(R.id.card_view_classes)
        cardDepartmentStats = findViewById(R.id.card_department_stats)

        loadingProgress = findViewById(R.id.loading_progress)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Department Dashboard"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupHODClickListener() {
        hodClickArea.setOnClickListener {
            if (hodId.isNotEmpty()) {
                if (isCurrentUserHOD) {
                    showHODDetailsDialog(hodId)
                } else {
                    loadAndShowHODDetails(hodId)
                }
            } else {
                Toast.makeText(this, "No HOD assigned to this department", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadAndShowHODDetails(facultyId: String) {
        showLoading(true)
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)
                val faculty = document.toObject(Faculty::class.java)
                faculty?.let {
                    it.id = document.id
                    showHODDetailsDialog(facultyId)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading HOD details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showHODDetailsDialog(facultyId: String) {
        showLoading(true)
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)
                val faculty = document.toObject(Faculty::class.java)
                faculty?.let {
                    it.id = document.id
                    displayHODDetailsDialog(it)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayHODDetailsDialog(faculty: Faculty) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_faculty_details, null)

        val profileImage: CircleImageView = dialogView.findViewById(R.id.profile_image)
        val statusBadge: ImageView = dialogView.findViewById(R.id.profile_status_badge)
        val employeeIdText: TextView = dialogView.findViewById(R.id.employee_id_text)
        val nameText: TextView = dialogView.findViewById(R.id.name_text)
        val roleChip: Chip = dialogView.findViewById(R.id.role_chip)
        val emailText: TextView = dialogView.findViewById(R.id.email_text)
        val phoneText: TextView = dialogView.findViewById(R.id.phone_text)
        val departmentText: TextView = dialogView.findViewById(R.id.department_text)
        val collegeText: TextView = dialogView.findViewById(R.id.college_text)
        val streamText: TextView = dialogView.findViewById(R.id.stream_text)
        val designationText: TextView = dialogView.findViewById(R.id.designation_text)
        val qualificationsText: TextView = dialogView.findViewById(R.id.qualifications_text)
        val experienceText: TextView = dialogView.findViewById(R.id.experience_text)
        val subjectsChipGroup: ChipGroup = dialogView.findViewById(R.id.subjects_chip_group)
        val noSubjectsText: TextView = dialogView.findViewById(R.id.no_subjects_text)
        val btnCall: MaterialButton = dialogView.findViewById(R.id.btn_call)
        val btnEmail: MaterialButton = dialogView.findViewById(R.id.btn_email)

        if (faculty.photoUrl.isNotEmpty()) {
            Picasso.get()
                .load(faculty.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.ic_person)
        }

        if (faculty.profileCompleted) {
            statusBadge.setImageResource(R.drawable.ic_check_circle)
            statusBadge.setColorFilter(getColor(R.color.splash_accent))
        } else {
            statusBadge.setImageResource(R.drawable.ic_pending)
            statusBadge.setColorFilter(getColor(android.R.color.holo_orange_light))
        }

        employeeIdText.text = faculty.employeeId
        nameText.text = faculty.name
        roleChip.text = "Head of Department"
        roleChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        roleChip.visibility = View.VISIBLE
        emailText.text = faculty.email
        phoneText.text = faculty.phone
        departmentText.text = faculty.department.ifEmpty { "Not Assigned" }
        collegeText.text = department?.college ?: "N/A"
        streamText.text = department?.stream ?: "N/A"
        designationText.text = faculty.designation.ifEmpty { "Not Specified" }
        qualificationsText.text = faculty.qualifications.ifEmpty { "Not Specified" }
        experienceText.text = faculty.experience.ifEmpty { "Not Specified" }

        if (faculty.subjects.isEmpty()) {
            noSubjectsText.visibility = View.VISIBLE
            subjectsChipGroup.visibility = View.GONE
        } else {
            noSubjectsText.visibility = View.GONE
            subjectsChipGroup.visibility = View.VISIBLE
            subjectsChipGroup.removeAllViews()
            faculty.subjects.forEach { subject ->
                val chip = Chip(this).apply {
                    text = subject
                    isClickable = false
                    setChipBackgroundColorResource(R.color.splash_accent)
                    setTextColor(getColor(android.R.color.white))
                }
                subjectsChipGroup.addView(chip)
            }
        }

        btnCall.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${faculty.phone}")
            startActivity(intent)
        }

        btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO)
            intent.data = Uri.parse("mailto:${faculty.email}")
            startActivity(intent)
        }

        val builder = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Close", null)

        if (isCurrentUserHOD && faculty.id == auth.currentUser?.uid) {
            builder.setNeutralButton("Edit Profile") { _, _ ->
                val intent = Intent(this, FacultyProfileSetupActivity::class.java)
                startActivity(intent)
            }
        }

        builder.show()
    }

    private fun setupActionCards() {
        cardViewFaculty.setOnClickListener {
            val intent = Intent(this, DepartmentFacultyActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        cardViewStudents.setOnClickListener {
            val intent = Intent(this, ManageDeptStudentsActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        cardAttendanceReports.setOnClickListener {
            val intent = Intent(this, AttendanceReportsActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        cardTimetable.setOnClickListener {
            val intent = Intent(this, HodTimetableActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        cardLeaveApprovals.setOnClickListener {
            val intent = Intent(this, HodLeaveApprovalsActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        cardViewClasses.setOnClickListener {
            val intent = Intent(this, ManageClassesActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            intent.putExtra("VIEW_ONLY", true)
            startActivity(intent)
        }

        cardDepartmentStats.setOnClickListener {
            showDepartmentStatistics()
        }
    }

    private fun showDepartmentStatistics() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_department_statistics, null)

        val totalFacultyText: TextView = dialogView.findViewById(R.id.total_faculty_stat)
        val totalStudentsText: TextView = dialogView.findViewById(R.id.total_students_stat)
        val totalSubjectsText: TextView = dialogView.findViewById(R.id.total_subjects_stat)
        val totalClassesText: TextView = dialogView.findViewById(R.id.total_classes_stat)
        val activeFacultyText: TextView = dialogView.findViewById(R.id.active_faculty_stat)
        val activeStudentsText: TextView = dialogView.findViewById(R.id.active_students_stat)
        val avgAttendanceText: TextView = dialogView.findViewById(R.id.avg_attendance_stat)
        val pendingLeavesText: TextView = dialogView.findViewById(R.id.pending_leaves_stat)

        loadDetailedStatistics(
            totalFacultyText, totalStudentsText, totalSubjectsText, totalClassesText,
            activeFacultyText, activeStudentsText, avgAttendanceText, pendingLeavesText
        )

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("${department?.name} Statistics")
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun loadDetailedStatistics(
        totalFacultyText: TextView, totalStudentsText: TextView,
        totalSubjectsText: TextView, totalClassesText: TextView,
        activeFacultyText: TextView, activeStudentsText: TextView,
        avgAttendanceText: TextView, pendingLeavesText: TextView
    ) {
        firestore.collection("faculty").whereEqualTo("departmentId", departmentId).get()
            .addOnSuccessListener { docs ->
                totalFacultyText.text = docs.size().toString()
                activeFacultyText.text = "${docs.count { it.getBoolean("isActive") == true }} active"
            }

        firestore.collection("students").whereEqualTo("departmentId", departmentId).get()
            .addOnSuccessListener { docs ->
                totalStudentsText.text = docs.size().toString()
                activeStudentsText.text = "${docs.count { it.getBoolean("isActive") == true }} active"
            }

        firestore.collection("subjects").whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true).get()
            .addOnSuccessListener { docs -> totalSubjectsText.text = docs.size().toString() }

        firestore.collection("classes").whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true).get()
            .addOnSuccessListener { docs -> totalClassesText.text = docs.size().toString() }

        firestore.collection("leave_requests").whereEqualTo("departmentId", departmentId)
            .whereEqualTo("status", "pending").get()
            .addOnSuccessListener { docs -> pendingLeavesText.text = "${docs.size()} pending" }

        avgAttendanceText.text = "Coming Soon"
    }

    private fun loadDepartmentData() {
        showLoading(true)
        firestore.collection("departments").document(departmentId)
            .get()
            .addOnSuccessListener { document ->
                department = document.toObject(Department::class.java)
                department?.let {
                    it.id = document.id
                    hodId = it.hodId
                    displayDepartmentData(it)
                    loadRealTimeCounts()
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadRealTimeCounts() {
        firestore.collection("faculty").whereEqualTo("departmentId", departmentId).get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                facultyCount.text = "$count Faculty Members"
                firestore.collection("departments").document(departmentId)
                    .update("totalFaculty", count)
                    .addOnFailureListener { e -> Log.w("HodDeptDashboard", "Error updating faculty count", e) }
            }
            .addOnFailureListener { facultyCount.text = "0 Faculty Members" }

        firestore.collection("students").whereEqualTo("departmentId", departmentId).get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                studentCount.text = "$count Students"
                firestore.collection("departments").document(departmentId)
                    .update("totalStudents", count)
                    .addOnFailureListener { e -> Log.w("HodDeptDashboard", "Error updating student count", e) }
            }
            .addOnFailureListener { studentCount.text = "0 Students" }
    }

    private fun displayDepartmentData(dept: Department) {
        if (dept.photoUrl.isNotEmpty()) {
            Picasso.get().load(dept.photoUrl)
                .placeholder(R.drawable.ic_departments)
                .error(R.drawable.ic_departments)
                .into(departmentImage)
        } else {
            departmentImage.setImageResource(R.drawable.ic_departments)
        }

        departmentName.text = dept.name
        departmentCode.text = dept.code

        if (dept.hod.isNotEmpty()) {
            hodName.text = dept.hod
            hodClickArea.isClickable = true
            hodClickArea.isFocusable = true
            hodName.setTextColor(getColor(android.R.color.white))
        } else {
            hodName.text = "Not Assigned"
            hodClickArea.isClickable = false
            hodClickArea.isFocusable = false
            hodName.setTextColor(getColor(R.color.splash_text_secondary))
        }

        facultyCount.text = "${dept.totalFaculty} Faculty Members"
        studentCount.text = "${dept.totalStudents} Students"
        establishedYear.text = "Established: ${dept.establishedYear}"
        description.text = dept.description.ifEmpty { "No description available" }

        collegeChip.text = if (dept.college == "JR") "Junior College" else "Senior College"
        collegeChip.setChipBackgroundColorResource(
            if (dept.college == "JR") R.color.splash_accent else android.R.color.holo_blue_dark
        )

        streamChip.text = dept.stream
        val streamColor = when (dept.stream) {
            "Science" -> android.R.color.holo_green_dark
            "Commerce" -> android.R.color.holo_orange_dark
            "Arts" -> android.R.color.holo_purple
            else -> R.color.splash_text_secondary
        }
        streamChip.setChipBackgroundColorResource(streamColor)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (departmentId.isNotEmpty()) {
            loadDepartmentData()
        }
    }
}