package com.projectbyyatin.synapsemis

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Class

class ClassDetailsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var classIcon: ImageView
    private lateinit var className: TextView
    private lateinit var courseName: TextView
    private lateinit var currentSemesterChip: Chip
    private lateinit var classSizeChip: Chip
    private lateinit var statusChip: Chip
    private lateinit var classTeacherName: TextView
    private lateinit var departmentName: TextView
    private lateinit var academicYear: TextView
    private lateinit var inviteCodeText: TextView
    private lateinit var btnCopyInvite: MaterialButton

    // Action Cards
    private lateinit var cardInviteStudents: MaterialCardView
    private lateinit var cardApproveStudents: MaterialCardView
    private lateinit var cardViewStudents: MaterialCardView
    private lateinit var cardStartNextSem: MaterialCardView
    private lateinit var cardMonthlyAttendance: MaterialCardView
    private lateinit var cardSemesterAttendance: MaterialCardView
    private lateinit var cardOverallAttendance: MaterialCardView
    private lateinit var cardManageTimetable: MaterialCardView
    private lateinit var cardReleaseResults: MaterialCardView

    private lateinit var loadingProgress: ProgressBar
    private lateinit var fabEditClass: FloatingActionButton
    private lateinit var firestore: FirebaseFirestore

    private var classId: String = ""
    private var classData: Class? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_details)

        classId = intent.getStringExtra("CLASS_ID") ?: ""

        if (classId.isEmpty()) {
            Toast.makeText(this, "Error: Class ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupActionCards()
        setupFab()
        loadClassData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        classIcon = findViewById(R.id.class_icon)
        className = findViewById(R.id.class_name)
        courseName = findViewById(R.id.course_name)
        currentSemesterChip = findViewById(R.id.current_semester_chip)
        classSizeChip = findViewById(R.id.class_size_chip)
        statusChip = findViewById(R.id.status_chip)
        classTeacherName = findViewById(R.id.class_teacher_name)
        departmentName = findViewById(R.id.department_name)
        academicYear = findViewById(R.id.academic_year)
        inviteCodeText = findViewById(R.id.invite_code_text)
        btnCopyInvite = findViewById(R.id.btn_copy_invite)

        cardInviteStudents = findViewById(R.id.card_invite_students)
        cardApproveStudents = findViewById(R.id.card_approve_students)
        cardViewStudents = findViewById(R.id.card_view_students)
        cardStartNextSem = findViewById(R.id.card_start_next_sem)
        cardMonthlyAttendance = findViewById(R.id.card_monthly_attendance)
        cardSemesterAttendance = findViewById(R.id.card_semester_attendance)
        cardOverallAttendance = findViewById(R.id.card_overall_attendance)
        cardManageTimetable = findViewById(R.id.card_manage_timetable)
        cardReleaseResults = findViewById(R.id.card_release_results)

        loadingProgress = findViewById(R.id.loading_progress)
        fabEditClass = findViewById(R.id.fab_edit_class)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Class Details"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupFab() {
        fabEditClass.setOnClickListener {
            showEditClassDialog()
        }
    }

    private fun setupActionCards() {
        btnCopyInvite.setOnClickListener {
            copyInviteCode()
        }

        cardInviteStudents.setOnClickListener {
            showInviteDialog()
        }

        cardApproveStudents.setOnClickListener {
            val intent = Intent(this, ApproveStudentsActivity::class.java)
            intent.putExtra("CLASS_ID", classId)
            intent.putExtra("CLASS_NAME", classData?.className)
            startActivity(intent)
        }

        cardViewStudents.setOnClickListener {
            val intent = Intent(this, ClassStudentsActivity::class.java)
            intent.putExtra("CLASS_ID", classId)
            intent.putExtra("CLASS_NAME", classData?.className)
            startActivity(intent)
        }

        cardStartNextSem.setOnClickListener {
            checkAndPromoteSemester()
        }

        cardMonthlyAttendance.setOnClickListener {
            val intent = Intent(this, MonthlyAttendanceActivity::class.java)
            intent.putExtra("CLASS_ID", classId)
            intent.putExtra("CLASS_NAME", classData?.className)
            intent.putExtra("CURRENT_SEMESTER", classData?.currentSemester)
            startActivity(intent)
        }

        cardSemesterAttendance.setOnClickListener {
            val intent = Intent(this, SemesterAttendanceActivity::class.java)
            intent.putExtra("CLASS_ID", classId)
            intent.putExtra("CLASS_NAME", classData?.className)
            intent.putExtra("CURRENT_SEMESTER", classData?.currentSemester)
            startActivity(intent)
        }

        cardOverallAttendance.setOnClickListener {
            val intent = Intent(this, OverallAttendanceActivity::class.java)
            intent.putExtra("CLASS_ID", classId)
            intent.putExtra("CLASS_NAME", classData?.className)
            startActivity(intent)
        }

        cardManageTimetable.setOnClickListener {
            Toast.makeText(this, "Timetable Management - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        cardReleaseResults.setOnClickListener {
            showReleaseResultsDialog()
        }
    }

    private fun showEditClassDialog() {
        val classItem = classData ?: return

        Log.d("ClassDetails", "Editing class with Department ID: ${classItem.departmentId}")

        // Inflate custom dialog layout
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_class, null)

        val classTeacherInput = dialogView.findViewById<AutoCompleteTextView>(R.id.class_teacher_dropdown)
        val activeSwitch = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.active_switch)

        // Set current values
        classTeacherInput.setText(classItem.classTeacherName)
        activeSwitch.isChecked = classItem.isActive

        // Load faculty for dropdown
        loadFacultyForDropdown(classTeacherInput, classItem.departmentId)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Edit Class")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newTeacherName = classTeacherInput.text.toString().trim()
                val isActive = activeSwitch.isChecked

                if (newTeacherName.isEmpty()) {
                    Toast.makeText(this, "Please select a class teacher", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                updateClassDetails(newTeacherName, isActive)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadFacultyForDropdown(dropdown: AutoCompleteTextView, departmentId: String) {
        Log.d("ClassDetails", "Loading faculty for department: $departmentId")

        if (departmentId.isEmpty()) {
            Toast.makeText(this, "Department ID not found", Toast.LENGTH_SHORT).show()
            loadAllActiveFaculty(dropdown)
            return
        }

        dropdown.setText("Loading...")

        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()  // REMOVED: .whereEqualTo("isActive", true) - will filter in code
            .addOnSuccessListener { documents ->
                Log.d("ClassDetails", "Found ${documents.size()} faculty members")

                // Filter active faculty in code
                val activeFaculty = documents.filter { doc ->
                    doc.getBoolean("active") == true  // CHANGED: using "active" field
                }

                if (activeFaculty.isEmpty()) {
                    dropdown.setText("")
                    Toast.makeText(
                        this,
                        "No active faculty in this department. Showing all faculty.",
                        Toast.LENGTH_LONG
                    ).show()
                    loadAllActiveFaculty(dropdown)
                    return@addOnSuccessListener
                }

                val facultyNames = activeFaculty.mapNotNull { doc ->
                    val name = doc.getString("name") ?: ""
                    Log.d("ClassDetails", "Faculty found: $name")
                    name
                }.sorted()

                dropdown.setText("")
                setupDropdownAdapter(dropdown, facultyNames)
            }
            .addOnFailureListener { e ->
                dropdown.setText("")
                Log.e("ClassDetails", "Error loading faculty", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                loadAllActiveFaculty(dropdown)
            }
    }

    private fun loadAllActiveFaculty(dropdown: AutoCompleteTextView) {
        Log.d("ClassDetails", "Loading all active faculty as fallback")

        firestore.collection("faculty")
            .get()  // REMOVED: .whereEqualTo("isActive", true)
            .addOnSuccessListener { documents ->
                // Filter active faculty in code
                val facultyNames = documents.mapNotNull { doc ->
                    val isActive = doc.getBoolean("active") ?: false  // CHANGED: using "active"
                    val name = doc.getString("name") ?: ""
                    if (isActive && name.isNotEmpty()) name else null
                }.sorted()

                Log.d("ClassDetails", "Found ${facultyNames.size} total active faculty")
                setupDropdownAdapter(dropdown, facultyNames)
            }
            .addOnFailureListener { e ->
                Log.e("ClassDetails", "Error loading all faculty", e)
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupDropdownAdapter(dropdown: AutoCompleteTextView, facultyNames: List<String>) {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            facultyNames
        )
        dropdown.setAdapter(adapter)
        dropdown.threshold = 1

        // Auto-show dropdown on click
        dropdown.setOnClickListener {
            dropdown.showDropDown()
        }

        dropdown.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                dropdown.showDropDown()
            }
        }
    }

    private fun updateClassDetails(teacherName: String, isActive: Boolean) {
        showLoading(true)

        Log.d("ClassDetails", "Updating class with teacher: $teacherName")

        firestore.collection("faculty")
            .whereEqualTo("name", teacherName)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    showLoading(false)
                    Toast.makeText(this, "Teacher not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val teacherDoc = documents.documents[0]
                val teacherId = teacherDoc.id
                val teacherEmail = teacherDoc.getString("email") ?: ""

                // ✅ CRITICAL: Get CURRENT inviteCode BEFORE updating
                val currentInviteCode = classData?.inviteCode ?: ""

                Log.d("ClassDetails", "Found teacher: $teacherName (ID: $teacherId)")

                val updates = hashMapOf<String, Any>(
                    "classTeacherName" to teacherName,
                    "classTeacherId" to teacherId,
                    "classTeacherEmail" to teacherEmail,
                    "isActive" to isActive,
                    "updatedAt" to System.currentTimeMillis()
                    // ✅ inviteCode NOT TOUCHED - REMAINS PERMANENT
                )

                firestore.collection("classes")
                    .document(classId)
                    .update(updates)
                    .addOnSuccessListener {
                        showLoading(false)
                        Toast.makeText(this, "Class updated successfully", Toast.LENGTH_SHORT).show()
                        loadClassData()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Log.e("ClassDetails", "Error updating class", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("ClassDetails", "Error finding teacher", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun checkAndPromoteSemester() {
        val classItem = classData ?: return

        if (classItem.isCompleted) {
            Toast.makeText(this, "Class has completed all semesters", Toast.LENGTH_SHORT).show()
            return
        }

        if (!classItem.canPromote || classItem.resultsReleasedForSemester != classItem.currentSemester) {
            AlertDialog.Builder(this, R.style.CustomAlertDialog)
                .setTitle("Cannot Promote")
                .setMessage("Results for Semester ${classItem.currentSemester} must be released before promoting to next semester.\n\nPlease release results first.")
                .setPositiveButton("Release Results") { _, _ ->
                    showReleaseResultsDialog()
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        if (classItem.currentSemester >= classItem.totalSemesters) {
            showCompleteClassDialog()
        } else {
            showStartNextSemesterDialog()
        }
    }

    private fun showReleaseResultsDialog() {
        val classItem = classData ?: return
        val currentSem = classItem.currentSemester

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Release Results")
            .setMessage("Release results for Semester $currentSem?\n\nThis will:\n• Mark results as published\n• Enable semester promotion\n• Notify students (if enabled)")
            .setPositiveButton("Release Results") { _, _ ->
                releaseResults(currentSem)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun releaseResults(semester: Int) {
        showLoading(true)

        firestore.collection("classes").document(classId)
            .update(mapOf(
                "canPromote" to true,
                "resultsReleasedForSemester" to semester,
                "updatedAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Results released for Semester $semester", Toast.LENGTH_SHORT).show()
                loadClassData()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showCompleteClassDialog() {
        val classItem = classData ?: return

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Complete Class")
            .setMessage("This is the final semester (${classItem.totalSemesters}).\n\nMark this class as completed?\n\nBatch ${classItem.batch} will be marked as passed out.\n\nThis action cannot be undone.")
            .setPositiveButton("Complete & Pass Out") { _, _ ->
                completeClass()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun completeClass() {
        showLoading(true)

        firestore.collection("classes").document(classId)
            .update(mapOf(
                "isCompleted" to true,
                "isActive" to false,
                "completedAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
                // ✅ NO inviteCode change
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Class completed successfully. Batch passed out!", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showStartNextSemesterDialog() {
        val currentSem = classData?.currentSemester ?: 1
        val nextSem = currentSem + 1

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Start Next Semester")
            .setMessage("Move class from Semester $currentSem to Semester $nextSem?\n\nThis action cannot be undone.")
            .setPositiveButton("Start Sem $nextSem") { _, _ ->
                startNextSemester(nextSem)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startNextSemester(nextSem: Int) {
        showLoading(true)

        firestore.collection("classes").document(classId)
            .update(mapOf(
                "currentSemester" to nextSem,
                "canPromote" to false,
                "updatedAt" to System.currentTimeMillis()
                // ✅ NO inviteCode change
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Started Semester $nextSem successfully", Toast.LENGTH_SHORT).show()
                loadClassData()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadClassData() {
        showLoading(true)

        firestore.collection("classes").document(classId)
            .get()
            .addOnSuccessListener { document ->
                classData = document.toObject(Class::class.java)
                classData?.let {
                    it.id = document.id
                    displayClassData(it)
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayClassData(classItem: Class) {
        className.text = classItem.className
        courseName.text = classItem.courseName
        currentSemesterChip.text = "Sem ${classItem.currentSemester}/${classItem.totalSemesters}"
        classSizeChip.text = "${classItem.currentSize}/${classItem.maxSize} Students"

        if (classItem.isCompleted) {
            statusChip.text = "Completed"
            statusChip.setChipBackgroundColorResource(android.R.color.holo_purple)
        } else if (classItem.isActive) {
            statusChip.text = "Active"
            statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
        } else {
            statusChip.text = "Inactive"
            statusChip.setChipBackgroundColorResource(android.R.color.holo_red_dark)
        }

        classTeacherName.text = "Class Teacher: ${classItem.classTeacherName}"
        departmentName.text = "Department: ${classItem.departmentName}"
        academicYear.text = "Academic Year: ${classItem.academicYear}"

        val batchText: TextView = findViewById(R.id.batch_text)
        batchText.text = "Batch: ${classItem.batch}"

        inviteCodeText.text = classItem.inviteCode

        if (classItem.isCompleted) {
            cardStartNextSem.alpha = 0.5f
            cardStartNextSem.isEnabled = false
        } else if (!classItem.canPromote || classItem.resultsReleasedForSemester != classItem.currentSemester) {
            cardStartNextSem.alpha = 0.6f
        } else {
            cardStartNextSem.alpha = 1.0f
        }
    }

    private fun copyInviteCode() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Invite Code", classData?.inviteCode ?: "")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Invite code copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    private fun showInviteDialog() {
        val inviteCode = classData?.inviteCode ?: ""
        val message = """
            Share this invite code with students:
            
            Invite Code: $inviteCode
            Class: ${classData?.className}
            Course: ${classData?.courseName}
            
            Students can use this code to request joining the class.
        """.trimIndent()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Invite Students")
            .setMessage(message)
            .setPositiveButton("Copy Code") { _, _ ->
                copyInviteCode()
            }
            .setNegativeButton("Share") { _, _ ->
                shareInviteCode()
            }
            .setNeutralButton("Close", null)
            .show()
    }

    private fun shareInviteCode() {
        val shareText = """
            Join ${classData?.className} - ${classData?.courseName}
            
            Invite Code: ${classData?.inviteCode}
            
            Use this code in SYNAPSE MIS app to join the class.
        """.trimIndent()

        val shareIntent = Intent(Intent.ACTION_SEND)
        shareIntent.type = "text/plain"
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText)
        startActivity(Intent.createChooser(shareIntent, "Share Invite Code"))
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadClassData()
    }
}
