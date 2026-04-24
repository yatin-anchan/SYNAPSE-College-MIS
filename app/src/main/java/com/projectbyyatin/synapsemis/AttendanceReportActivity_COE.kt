package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.AttendanceReportAdapter
import com.projectbyyatin.synapsemis.models.AttendanceReport
import com.projectbyyatin.synapsemis.models.Class
import com.projectbyyatin.synapsemis.models.Subject
import java.text.SimpleDateFormat
import java.util.*

class AttendanceReportActivity_COE : AppCompatActivity() {

    companion object {
        private const val TAG = "AttendanceReportsAct"
    }

    private val db = FirebaseFirestore.getInstance()

    private lateinit var toolbar         : androidx.appcompat.widget.Toolbar
    private lateinit var spinnerClass    : Spinner
    private lateinit var spinnerSubject  : Spinner
    private lateinit var spinnerMonth    : Spinner

    private lateinit var btnAnalytics: MaterialButton
    private lateinit var btnDefaulterList: MaterialButton
    private lateinit var rvStudents      : RecyclerView
    private lateinit var loadingProgress : CircularProgressIndicator
    private lateinit var emptyView       : TextView
    private lateinit var tvTotalClasses  : TextView
    private lateinit var tvAvgAttendance : TextView

    private val classList        = mutableListOf<Class>()
    private val subjectList      = mutableListOf<Subject>()
    private val monthList        = mutableListOf<String>()   // "2026-02"
    private val monthDisplayList = mutableListOf<String>()   // "February 2026"

    private var currentUserDepartmentId: String = ""
    private var selectedClass  : Class?   = null
    private var selectedSubject: Subject? = null

    private val reportList = mutableListOf<AttendanceReport>()
    private lateinit var adapter: AttendanceReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_reports)
        currentUserDepartmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        bindViews()
        setupToolbar()
        setupMonthSpinner()
        setupRecyclerView()
        setupSpinnerListeners()
        loadClasses()
    }

    private fun bindViews() {
        toolbar          = findViewById(R.id.toolbar)
        spinnerClass     = findViewById(R.id.spinner_class)
        spinnerSubject   = findViewById(R.id.spinner_subject)
        spinnerMonth     = findViewById(R.id.spinner_month)
        btnDefaulterList = findViewById(R.id.btn_defaulter_list)
        rvStudents       = findViewById(R.id.recycler_view)
        loadingProgress  = findViewById(R.id.loading_progress)
        emptyView        = findViewById(R.id.empty_view)
        tvTotalClasses   = findViewById(R.id.tv_total_classes)
        tvAvgAttendance  = findViewById(R.id.tv_avg_attendance)

        btnDefaulterList.setOnClickListener {
            startActivity(Intent(this, DefaulterListActivity_COE::class.java).apply { putExtra("DEPARTMENT_ID", selectedClass?.departmentId ?: currentUserDepartmentId) })
        }

        btnAnalytics = findViewById(R.id.btn_analytics)
        btnAnalytics.setOnClickListener {
            val cls = selectedClass ?: run { toast("Please select a class first"); return@setOnClickListener }
            startActivity(Intent(this, StudentAttendanceAnalyticsActivity::class.java).apply {
                putExtra("CLASS_ID",    cls.id)
                putExtra("CLASS_NAME",  cls.className)
                putExtra("SUBJECT_ID",  selectedSubject?.id ?: "")
            })
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Attendance Reports"
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = AttendanceReportAdapter(reportList) { report ->
            openStudentAnalysis(report)
        }
        rvStudents.layoutManager = LinearLayoutManager(this)
        rvStudents.adapter = adapter
    }

    private fun setupMonthSpinner() {
        monthList.clear()
        monthDisplayList.clear()

        val sdf        = SimpleDateFormat("yyyy-MM",    Locale.getDefault())
        val displaySdf = SimpleDateFormat("MMMM yyyy",  Locale.getDefault())
        val cal        = Calendar.getInstance()

        for (i in 0 until 12) {
            monthList.add(0, sdf.format(cal.time))
            monthDisplayList.add(0, displaySdf.format(cal.time))
            cal.add(Calendar.MONTH, -1)
        }

        val monthAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, monthDisplayList)
        monthAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMonth.adapter = monthAdapter
        spinnerMonth.setSelection(monthDisplayList.size - 1)
    }

    private fun setupSpinnerListeners() {
        spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (pos > 0) {
                    selectedClass = classList[pos - 1]
                    loadSubjectsForClass(selectedClass!!)
                } else {
                    selectedClass = null
                    subjectList.clear()
                    updateSubjectSpinner()
                    resetList()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                selectedSubject = if (pos > 0) subjectList[pos - 1] else null
                if (selectedSubject != null) loadAttendanceData() else resetList()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }

        spinnerMonth.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, pos: Int, id: Long) {
                if (selectedSubject != null) loadAttendanceData()
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    private fun loadClasses() {
        showLoading(true)
        db.collection("classes")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snapshot ->
                classList.clear()
                classList.addAll(snapshot.toObjects(Class::class.java))
                updateClassSpinner()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("Failed to load classes: ${e.message}")
            }
    }

    private fun loadSubjectsForClass(cls: Class) {
        showLoading(true)
        db.collection("subjects")
            .whereEqualTo("departmentId", cls.departmentId)
            .whereEqualTo("semesterNumber", cls.currentSemester)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snapshot ->
                subjectList.clear()
                snapshot.documents.forEach { doc ->
                    val subject = doc.toObject(Subject::class.java)
                    if (subject != null) {
                        subject.id = doc.id   // ← manually assign document ID
                        subjectList.add(subject)
                    }
                }
                updateSubjectSpinner()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("Failed to load subjects: ${e.message}")
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FETCH — new structure: query session docs, then read students[] array
    //
    // attendance collection now has ONE doc per session.
    // We query by classId + subjectId + month, then for each session doc
    // we iterate the embedded students[] array to build per-student report.
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadAttendanceData() {
        val cls     = selectedClass  ?: return
        val subject = selectedSubject ?: return
        val month   = monthList[spinnerMonth.selectedItemPosition]  // "2026-02"

        Log.d(TAG, "=== QUERY DEBUG ===")
        Log.d(TAG, "classId: '${cls.id}'")
        Log.d(TAG, "subjectId: '${subject.id}'")
        Log.d(TAG, "month: '$month'")
        Log.d(TAG, "===================")

        Log.d(TAG, "Loading: ${cls.className} → ${subject.name} → $month")

        showLoading(true)
        reportList.clear()
        adapter.notifyDataSetChanged()
        emptyView.visibility = View.GONE

        // Query all session docs for this class + subject + month
        db.collection("attendance")
            .whereEqualTo("classId",   cls.id)
            .whereEqualTo("subjectId", subject.id)
            .whereEqualTo("month",     month)           // "2026-02"
            .get()
            .addOnSuccessListener { sessionSnap ->
                Log.d(TAG, "Found ${sessionSnap.size()} sessions")

                if (sessionSnap.isEmpty) {
                    showLoading(false)
                    showEmpty("No attendance records found")
                    return@addOnSuccessListener
                }

                // Aggregate per-student across ALL sessions in this month
                // Map: studentId → { name, rollNumber, totalSessions, presentCount }
                data class StudentAgg(
                    val name: String,
                    val rollNumber: String,
                    var total: Int = 0,
                    var present: Int = 0
                )
                val studentMap = mutableMapOf<String, StudentAgg>()

                sessionSnap.documents.forEach { sessionDoc ->
                    val studentsArray = sessionDoc.get("students") as? List<Map<String, Any>>
                        ?: return@forEach

                    studentsArray.forEach { studentMap2 ->
                        val studentId   = studentMap2["studentId"]     as? String ?: return@forEach
                        val studentName = studentMap2["studentName"]   as? String ?: ""
                        val rollNumber  = studentMap2["studentRollNo"] as? String ?: ""
                        val status      = studentMap2["status"]        as? String ?: "absent"

                        val agg = studentMap.getOrPut(studentId) {
                            StudentAgg(studentName, rollNumber)
                        }
                        agg.total++
                        if (status == "present" || status == "late") agg.present++
                    }
                }

                // Build report list
                reportList.clear()
                studentMap.forEach { (studentId, agg) ->
                    reportList.add(
                        AttendanceReport.fromStudent(
                            studentId   = studentId,
                            studentName = agg.name,
                            rollNumber  = agg.rollNumber,
                            classId     = cls.id,
                            className   = cls.className,
                            subjectId   = subject.id,
                            subjectName = subject.name,
                            month       = month,
                            total       = agg.total,
                            present     = agg.present,
                            facultyName = subject.assignedFacultyName ?: "",
                            recordedAt  = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date())
                        )
                    )
                }

                reportList.sortBy { it.rollNumber }
                adapter.notifyDataSetChanged()
                updateSummary()
                showLoading(false)

                if (reportList.isEmpty()) showEmpty("No student records found")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load attendance", e)
                showLoading(false)
                toast("Failed to load attendance: ${e.message}")
            }
    }

    private fun updateClassSpinner() {
        val names = mutableListOf("-- Select Class --")
        names.addAll(classList.map { "${it.className} (${it.academicYear})" })
        spinnerClass.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun updateSubjectSpinner() {
        val names = mutableListOf("-- Select Subject --")
        names.addAll(subjectList.map { "${it.name} (${it.code})" })
        spinnerSubject.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    private fun updateSummary() {
        if (reportList.isEmpty()) return
        val avg = reportList.map { it.percentage }.average()
        tvTotalClasses.text  = "Total Students: ${reportList.size}"
        tvAvgAttendance.text = "Avg Attendance: ${"%.1f".format(avg)}%"
    }

    private fun resetSummary() {
        tvTotalClasses.text  = "Total Students: 0"
        tvAvgAttendance.text = "Avg Attendance: 0.0%"
    }

    private fun resetList() {
        reportList.clear()
        adapter.notifyDataSetChanged()
        resetSummary()
        showEmpty("Please select class, subject and month")
    }

    private fun showEmpty(msg: String) {
        emptyView.text       = msg
        emptyView.visibility = View.VISIBLE
    }

    private fun openStudentAnalysis(report: AttendanceReport) {
        startActivity(Intent(this, StudentAttendanceAnalysisActivity::class.java).apply {
            putExtra("STUDENT_ID",           report.studentId)
            putExtra("STUDENT_NAME",         report.studentName)
            putExtra("ROLL_NUMBER",          report.rollNumber)
            putExtra("CLASS_ID",             report.classId)
            putExtra("CLASS_NAME",           report.className)
            putExtra("CURRENT_SUBJECT_ID",   selectedSubject?.id   ?: "")
            putExtra("CURRENT_SUBJECT_NAME", selectedSubject?.name ?: "")
            putExtra("CURRENT_MONTH",        monthList[spinnerMonth.selectedItemPosition])
        })
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}