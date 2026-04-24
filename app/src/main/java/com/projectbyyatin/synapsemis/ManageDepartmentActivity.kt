package com.projectbyyatin.synapsemis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
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
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Department
import com.projectbyyatin.synapsemis.models.Faculty
import com.squareup.picasso.Picasso
import de.hdodenhof.circleimageview.CircleImageView

class ManageDepartmentActivity : AppCompatActivity() {

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

    // Action Cards
    private lateinit var cardChangeHOD: MaterialCardView
    private lateinit var cardAddFaculty: MaterialCardView
    private lateinit var cardManageCourses: MaterialCardView
    private lateinit var cardManageClasses: MaterialCardView
    private lateinit var ManageStudents: MaterialCardView
    private lateinit var cardViewFaculty: MaterialCardView

    private lateinit var loadingProgress: ProgressBar
    private lateinit var firestore: FirebaseFirestore

    private var departmentId: String = ""
    private var department: Department? = null
    private var hodId: String = ""
    private var loadingDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_department)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        if (departmentId.isEmpty()) {
            Toast.makeText(this, "Error: Department ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupActionCards()
        setupHODClickListener()
        loadDepartmentData()
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

        cardChangeHOD = findViewById(R.id.card_change_hod)
        cardAddFaculty = findViewById(R.id.card_add_faculty)
        cardManageCourses = findViewById(R.id.card_manage_courses)
        cardManageClasses = findViewById(R.id.card_manage_classes)
        ManageStudents = findViewById(R.id.card_add_students)
        cardViewFaculty = findViewById(R.id.card_view_faculty)

        loadingProgress = findViewById(R.id.loading_progress)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Department"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupHODClickListener() {
        hodClickArea.setOnClickListener {
            if (hodId.isNotEmpty()) {
                loadAndShowHODDetails(hodId)
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
                    showHODDetailsDialog(it)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading HOD details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showHODDetailsDialog(faculty: Faculty) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_faculty_details, null)

        // Initialize dialog views
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

        // Load profile image
        if (faculty.photoUrl.isNotEmpty()) {
            Picasso.get()
                .load(faculty.photoUrl)
                .placeholder(R.drawable.ic_person)
                .error(R.drawable.ic_person)
                .into(profileImage)
        } else {
            profileImage.setImageResource(R.drawable.ic_person)
        }

        // Status badge
        if (faculty.profileCompleted) {
            statusBadge.setImageResource(R.drawable.ic_check_circle)
            statusBadge.setColorFilter(getColor(R.color.splash_accent))
        } else {
            statusBadge.setImageResource(R.drawable.ic_pending)
            statusBadge.setColorFilter(getColor(android.R.color.holo_orange_light))
        }

        // Set data
        employeeIdText.text = faculty.employeeId
        nameText.text = faculty.name

        // Role chip
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

        // Display subjects
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

        // Button listeners
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

        // Create and show dialog
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setNeutralButton("Edit") { _, _ ->
                val intent = Intent(this, EditFacultyActivity::class.java)
                intent.putExtra("FACULTY_ID", faculty.id)
                startActivity(intent)
            }
            .show()
    }

    private fun setupActionCards() {
        cardChangeHOD.setOnClickListener {
            val intent = Intent(this, ChangeHODActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        cardAddFaculty.setOnClickListener {
            val intent = Intent(this, AddFacultyToDepartmentActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            intent.putExtra("COLLEGE", department?.college)
            intent.putExtra("STREAM", department?.stream)
            startActivity(intent)
        }

        cardManageCourses.setOnClickListener {
            val intent = Intent(this, ManageCoursesActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            intent.putExtra("COLLEGE", department?.college)
            intent.putExtra("STREAM", department?.stream)
            startActivity(intent)
        }

        cardManageClasses.setOnClickListener {
            val intent = Intent(this, ManageClassesActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }

        ManageStudents.setOnClickListener {
            val intent = Intent(this, ManageDeptStudentsActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }


        cardViewFaculty.setOnClickListener {
            val intent = Intent(this, DepartmentFacultyActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", department?.name)
            startActivity(intent)
        }
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
        // Count faculty members
        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                facultyCount.text = "$count Faculty Members"

                // Update department document with real count
                firestore.collection("departments").document(departmentId)
                    .update("totalFaculty", count)
            }
            .addOnFailureListener {
                facultyCount.text = "0 Faculty Members"
            }

        // Count students
        firestore.collection("students")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()
                studentCount.text = "$count Students"

                // Update department document with real count
                firestore.collection("departments").document(departmentId)
                    .update("totalStudents", count)
            }
            .addOnFailureListener {
                studentCount.text = "0 Students"
            }
    }

    private fun displayDepartmentData(dept: Department) {
        // Load image
        if (dept.photoUrl.isNotEmpty()) {
            Picasso.get()
                .load(dept.photoUrl)
                .placeholder(R.drawable.ic_departments)
                .error(R.drawable.ic_departments)
                .into(departmentImage)
        } else {
            departmentImage.setImageResource(R.drawable.ic_departments)
        }

        departmentName.text = dept.name
        departmentCode.text = dept.code

        // Make HOD name clickable with visual indication
        if (dept.hod.isNotEmpty()) {
            hodName.text = "HOD: ${dept.hod}"
            hodClickArea.isClickable = true
            hodClickArea.isFocusable = true
            hodName.setTextColor(getColor(R.color.splash_accent))
            hodName.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_person, 0, R.drawable.ic_info, 0
            )
        } else {
            hodName.text = "HOD: Not Assigned"
            hodClickArea.isClickable = false
            hodClickArea.isFocusable = false
            hodName.setTextColor(getColor(R.color.splash_text_color))
            hodName.setCompoundDrawablesWithIntrinsicBounds(
                R.drawable.ic_person, 0, 0, 0
            )
        }

        facultyCount.text = "${dept.totalFaculty} Faculty Members"
        studentCount.text = "${dept.totalStudents} Students"
        establishedYear.text = "Established: ${dept.establishedYear}"
        description.text = dept.description.ifEmpty { "No description available" }

        // College chip
        collegeChip.text = if (dept.college == "JR") "Junior College" else "Senior College"
        collegeChip.setChipBackgroundColorResource(
            if (dept.college == "JR") R.color.splash_accent else android.R.color.holo_blue_dark
        )

        // Stream chip
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
        loadDepartmentData()
    }

    private fun deleteDepartment(departmentId: String, departmentName: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Department")
            .setMessage("Are you sure you want to delete '$departmentName'?\n\nAll faculty will be unassigned from this department.")
            .setPositiveButton("Delete") { _, _ ->
                showLoadingDialog()

                // Step 1: Unassign all faculty from this department
                unassignFacultyFromDepartment(departmentId) { success ->
                    if (success) {
                        // Step 2: Delete the department
                        firestore.collection("departments")
                            .document(departmentId)
                            .delete()
                            .addOnSuccessListener {
                                hideLoadingDialog()
                                Toast.makeText(this, "Department deleted successfully", Toast.LENGTH_SHORT).show()
                                finish() // Navigate back to previous screen
                            }
                            .addOnFailureListener { e ->
                                hideLoadingDialog()
                                Toast.makeText(this, "Error deleting department: ${e.message}", Toast.LENGTH_SHORT).show()
                                Log.e("ManageDept", "Error deleting department", e)
                            }
                    } else {
                        hideLoadingDialog()
                        Toast.makeText(this, "Failed to unassign faculty. Department not deleted.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            val progressBar = ProgressBar(this).apply {
                setPadding(50, 50, 50, 50)
            }

            loadingDialog = AlertDialog.Builder(this)
                .setView(progressBar)
                .setCancelable(false)
                .create()
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
    }

    private fun unassignFacultyFromDepartment(departmentId: String, callback: (Boolean) -> Unit) {
        Log.d("ManageDept", "Unassigning faculty from department: $departmentId")

        // Find all faculty assigned to this department
        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Log.d("ManageDept", "No faculty to unassign")
                    callback(true)
                    return@addOnSuccessListener
                }

                Log.d("ManageDept", "Found ${documents.size()} faculty to unassign")

                // Batch update all faculty
                val batch = firestore.batch()

                documents.forEach { doc ->
                    val facultyRef = firestore.collection("faculty").document(doc.id)
                    batch.update(facultyRef, mapOf(
                        "departmentId" to "",
                        "department" to ""
                    ))
                }

                // Commit the batch
                batch.commit()
                    .addOnSuccessListener {
                        Log.d("ManageDept", "Successfully unassigned ${documents.size()} faculty")
                        callback(true)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ManageDept", "Error unassigning faculty", e)
                        callback(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ManageDept", "Error finding faculty", e)
                callback(false)
            }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_manage_department, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_delete -> {
                department?.let {
                    deleteDepartment(departmentId, it.name)
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideLoadingDialog()
        loadingDialog = null
    }
}
