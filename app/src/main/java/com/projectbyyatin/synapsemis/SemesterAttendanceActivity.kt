package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.SemesterAttendanceAdapter
import com.projectbyyatin.synapsemis.models.ClassStudent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SemesterAttendanceActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SemesterAttendanceAdapter
    private var studentAttendanceList = mutableListOf<StudentAttendanceSummary>()

    private var classId: String = ""
    private var className: String = ""
    private var currentSemester: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_semester_attendance)

        classId = intent.getStringExtra("CLASS_ID") ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""
        currentSemester = intent.getIntExtra("CURRENT_SEMESTER", 1)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadSemesterAttendance()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Semester $currentSemester Attendance"
        supportActionBar?.subtitle = className
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SemesterAttendanceAdapter(studentAttendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadSemesterAttendance() {
        showLoading(true)

        // First load all approved students
        firestore.collection("class_students")
            .whereEqualTo("classId", classId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { studentDocs ->
                val students = mutableListOf<ClassStudent>()
                studentDocs.forEach { doc ->
                    val student = doc.toObject(ClassStudent::class.java)
                    student.id = doc.id
                    students.add(student)
                }

                // Load attendance for each student in current semester
                loadAttendanceForStudents(students)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAttendanceForStudents(students: List<ClassStudent>) {
        studentAttendanceList.clear()

        students.forEach { student ->
            firestore.collection("attendance")
                .whereEqualTo("classId", classId)
                .whereEqualTo("studentId", student.studentId)
                .whereEqualTo("semester", currentSemester)
                .get()
                .addOnSuccessListener { attendanceDocs ->
                    var presentCount = 0
                    var absentCount = 0
                    var lateCount = 0
                    var totalDays = attendanceDocs.size()

                    attendanceDocs.forEach { doc ->
                        when (doc.getString("status")) {
                            "present" -> presentCount++
                            "absent" -> absentCount++
                            "late" -> lateCount++
                        }
                    }

                    val percentage = if (totalDays > 0) {
                        ((presentCount + lateCount).toFloat() / totalDays * 100)
                    } else {
                        0f
                    }

                    val summary = StudentAttendanceSummary(
                        studentId = student.studentId,
                        studentName = student.studentName,
                        studentRollNo = student.rollNumber,
                        presentCount = presentCount,
                        absentCount = absentCount,
                        lateCount = lateCount,
                        totalDays = totalDays,
                        percentage = percentage
                    )

                    studentAttendanceList.add(summary)

                    // Update adapter when all students loaded
                    if (studentAttendanceList.size == students.size) {
                        studentAttendanceList.sortBy { it.studentRollNo }
                        adapter.updateList(studentAttendanceList)
                        showLoading(false)
                        updateEmptyView()
                    }
                }
        }

        if (students.isEmpty()) {
            showLoading(false)
            updateEmptyView()
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (studentAttendanceList.isEmpty()) View.VISIBLE else View.GONE
    }
}

data class StudentAttendanceSummary(
    val studentId: String = "",
    val studentName: String = "",
    val studentRollNo: String = "",
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val lateCount: Int = 0,
    val totalDays: Int = 0,
    val percentage: Float = 0f
)
