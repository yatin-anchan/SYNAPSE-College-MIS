package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.StudentAttendanceAdapter
import com.projectbyyatin.synapsemis.models.StudentAttendance

class MarkStudentAttendanceActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvClassName: TextView
    private lateinit var tvSubjectName: TextView
    private lateinit var tvDateTime: TextView
    private lateinit var btnMarkAllPresent: MaterialButton
    private lateinit var btnMarkAllAbsent: MaterialButton
    private lateinit var cardSummary: MaterialCardView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var btnSaveDraft: MaterialButton

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: StudentAttendanceAdapter

    private var studentsList = mutableListOf<StudentAttendance>()

    // Data from previous activity
    private var departmentId = ""
    private var departmentName = ""
    private var classId = ""
    private var className = ""
    private var subjectId = ""
    private var subjectName = ""
    private var date = ""
    private var startTime = ""
    private var endTime = ""
    private var duration = 0

    companion object {
        private const val TAG = "MarkStudentAttendance"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_student_attendance)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Get data from intent
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""
        classId = intent.getStringExtra("CLASS_ID") ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        date = intent.getStringExtra("DATE") ?: ""
        startTime = intent.getStringExtra("START_TIME") ?: ""
        endTime = intent.getStringExtra("END_TIME") ?: ""
        duration = intent.getIntExtra("DURATION", 0)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupButtons()
        displaySessionInfo()
        loadStudents()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        tvClassName = findViewById(R.id.tv_class_name)
        tvSubjectName = findViewById(R.id.tv_subject_name)
        tvDateTime = findViewById(R.id.tv_date_time)
        btnMarkAllPresent = findViewById(R.id.btn_mark_all_present)
        btnMarkAllAbsent = findViewById(R.id.btn_mark_all_absent)
        cardSummary = findViewById(R.id.card_summary)
        tvTotalCount = findViewById(R.id.tv_total_count)
        tvPresentCount = findViewById(R.id.tv_present_count)
        tvAbsentCount = findViewById(R.id.tv_absent_count)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)
        loadingProgress = findViewById(R.id.loading_progress)
        btnSaveDraft = findViewById(R.id.btn_save_draft)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Mark Attendance"
            subtitle = departmentName
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = StudentAttendanceAdapter(studentsList) {
            updateSummary()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        btnMarkAllPresent.setOnClickListener {
            adapter.markAllPresent()
            updateSummary()
        }

        btnMarkAllAbsent.setOnClickListener {
            adapter.markAllAbsent()
            updateSummary()
        }

        btnSaveDraft.setOnClickListener {
            confirmSaveDraft()
        }
    }

    private fun displaySessionInfo() {
        tvClassName.text = "Class: $className"
        tvSubjectName.text = "Subject: $subjectName"
        tvDateTime.text = "$date | $startTime - $endTime ($duration min)"
    }

    private fun loadStudents() {
        showLoading(true)

        firestore.collection("students")
            .whereEqualTo("classId", classId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                studentsList.clear()

                documents.forEach { doc ->
                    val student = StudentAttendance(
                        id = doc.id,
                        studentId = doc.getString("studentId") ?: "",
                        studentName = doc.getString("fullName") ?: "",
                        rollNumber = doc.getString("rollNumber") ?: "",
                        email = doc.getString("email") ?: "",
                        status = "present" // Default to present
                    )
                    studentsList.add(student)
                }

                // Sort by roll number
                studentsList.sortBy { it.rollNumber }

                adapter.notifyDataSetChanged()
                updateSummary()
                showLoading(false)

                if (studentsList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }

                Log.d(TAG, "✅ Loaded ${studentsList.size} students")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error loading students", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    private fun updateSummary() {
        val total = studentsList.size
        val present = studentsList.count { it.status == "present" }
        val absent = total - present

        tvTotalCount.text = "Total: $total"
        tvPresentCount.text = "Present: $present"
        tvAbsentCount.text = "Absent: $absent"
    }

    private fun confirmSaveDraft() {
        if (studentsList.isEmpty()) {
            Toast.makeText(this, "No students to save", Toast.LENGTH_SHORT).show()
            return
        }

        val presentCount = studentsList.count { it.status == "present" }
        val absentCount = studentsList.size - presentCount

        AlertDialog.Builder(this, R.style.LightAlertDialog)
            .setTitle("Save Draft")
            .setMessage(
                "Save attendance draft?\n\n" +
                        "Total: ${studentsList.size}\n" +
                        "Present: $presentCount\n" +
                        "Absent: $absentCount\n\n" +
                        "You can edit and submit this later."
            )
            .setPositiveButton("Save Draft") { _, _ -> saveDraft() }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                window?.setBackgroundDrawableResource(android.R.color.white)
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.purple_700))
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(android.R.color.darker_gray))
            }
    }

    private fun saveDraft() {
        showLoading(true)

        val currentUser = auth.currentUser
        val studentData = studentsList.map {
            mapOf(
                "studentId" to it.id,
                "studentName" to it.studentName,
                "rollNumber" to it.rollNumber,
                "email" to it.email,
                "status" to it.status,
                "remarks" to it.remarks
            )
        }

        val presentCount = studentsList.count { it.status == "present" }
        val absentCount = studentsList.size - presentCount

        val draft = hashMapOf(
            "classId" to classId,
            "className" to className,
            "subjectId" to subjectId,
            "subjectName" to subjectName,
            "departmentId" to departmentId,
            "departmentName" to departmentName,
            "facultyId" to (currentUser?.uid ?: ""),
            "date" to date,
            "startTime" to startTime,
            "endTime" to endTime,
            "duration" to duration,
            "status" to "draft",
            "createdAt" to System.currentTimeMillis(),
            "createdBy" to (currentUser?.displayName ?: currentUser?.email ?: ""),
            "totalStudents" to studentsList.size,
            "presentCount" to presentCount,
            "absentCount" to absentCount,
            "students" to studentData
        )

        firestore.collection("attendance_drafts")
            .add(draft)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "✅ Draft saved successfully!", Toast.LENGTH_LONG).show()
                Log.d(TAG, "✅ Draft saved with ID: ${it.id}")

                setResult(RESULT_OK)
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "❌ Error saving draft", e)
                Toast.makeText(this, "Error saving draft: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnSaveDraft.isEnabled = !show
        btnMarkAllPresent.isEnabled = !show
        btnMarkAllAbsent.isEnabled = !show
    }
}