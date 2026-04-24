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
import com.projectbyyatin.synapsemis.adapters.OverallAttendanceAdapter
import com.projectbyyatin.synapsemis.models.ClassStudent

class OverallAttendanceActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: OverallAttendanceAdapter
    private var overallAttendanceList = mutableListOf<OverallAttendanceSummary>()

    private var classId: String = ""
    private var className: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_overall_attendance)

        classId = intent.getStringExtra("CLASS_ID") ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadOverallAttendance()
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
        supportActionBar?.title = "Overall Attendance"
        supportActionBar?.subtitle = className
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = OverallAttendanceAdapter(overallAttendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadOverallAttendance() {
        showLoading(true)

        // Load all approved students
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

                // Load all attendance for each student
                loadAllAttendanceForStudents(students)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAllAttendanceForStudents(students: List<ClassStudent>) {
        overallAttendanceList.clear()

        students.forEach { student ->
            firestore.collection("attendance")
                .whereEqualTo("classId", classId)
                .whereEqualTo("studentId", student.studentId)
                .get()
                .addOnSuccessListener { attendanceDocs ->
                    var presentCount = 0
                    var absentCount = 0
                    var lateCount = 0
                    var totalDays = attendanceDocs.size()

                    // Calculate semester-wise breakdown
                    val semesterMap = mutableMapOf<Int, SemesterData>()

                    attendanceDocs.forEach { doc ->
                        val status = doc.getString("status") ?: "absent"
                        val semester = doc.getLong("semester")?.toInt() ?: 1

                        when (status) {
                            "present" -> presentCount++
                            "absent" -> absentCount++
                            "late" -> lateCount++
                        }

                        // Update semester-wise data
                        val semData = semesterMap.getOrDefault(semester, SemesterData())
                        when (status) {
                            "present" -> semData.present++
                            "absent" -> semData.absent++
                            "late" -> semData.late++
                        }
                        semData.total++
                        semesterMap[semester] = semData
                    }

                    val percentage = if (totalDays > 0) {
                        ((presentCount + lateCount).toFloat() / totalDays * 100)
                    } else {
                        0f
                    }

                    val summary = OverallAttendanceSummary(
                        studentId = student.studentId,
                        studentName = student.studentName,
                        studentRollNo = student.rollNumber,
                        presentCount = presentCount,
                        absentCount = absentCount,
                        lateCount = lateCount,
                        totalDays = totalDays,
                        percentage = percentage,
                        semesterWiseData = semesterMap
                    )

                    overallAttendanceList.add(summary)

                    // Update adapter when all students loaded
                    if (overallAttendanceList.size == students.size) {
                        overallAttendanceList.sortBy { it.studentRollNo }
                        adapter.updateList(overallAttendanceList)
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
        emptyView.visibility = if (overallAttendanceList.isEmpty()) View.VISIBLE else View.GONE
    }
}

data class OverallAttendanceSummary(
    val studentId: String = "",
    val studentName: String = "",
    val studentRollNo: String = "",
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val lateCount: Int = 0,
    val totalDays: Int = 0,
    val percentage: Float = 0f,
    val semesterWiseData: Map<Int, SemesterData> = emptyMap()
)

data class SemesterData(
    var present: Int = 0,
    var absent: Int = 0,
    var late: Int = 0,
    var total: Int = 0
) {
    fun getPercentage(): Float {
        return if (total > 0) {
            ((present + late).toFloat() / total * 100)
        } else {
            0f
        }
    }
}
