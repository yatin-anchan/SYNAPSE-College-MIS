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
import com.projectbyyatin.synapsemis.adapters.DraftStudentAttendanceAdapter
import com.projectbyyatin.synapsemis.models.StudentAttendance
import com.projectbyyatin.synapsemis.views.SlideToConfirmView
import java.text.SimpleDateFormat
import java.util.*

class AttendanceDraftDetailsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvClassName: TextView
    private lateinit var tvSubjectName: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvDuration: TextView
    private lateinit var cardSummary: MaterialCardView
    private lateinit var tvTotalStudents: TextView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var slideToConfirm: SlideToConfirmView
    private lateinit var btnCancel: MaterialButton

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: DraftStudentAttendanceAdapter

    private var studentsList = mutableListOf<StudentAttendance>()

    private var draftId = ""
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
    private var createdBy = ""
    private var createdAt: Long = 0

    companion object {
        private const val TAG = "DraftDetails"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_draft_details)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        draftId = intent.getStringExtra("DRAFT_ID") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        if (draftId.isEmpty()) {
            Toast.makeText(this, "Invalid draft", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupButtons()
        loadDraftDetails()
    }

    private fun initializeViews() {
        toolbar        = findViewById(R.id.toolbar)
        tvClassName    = findViewById(R.id.tv_class_name)
        tvSubjectName  = findViewById(R.id.tv_subject_name)
        tvDate         = findViewById(R.id.tv_date)
        tvTime         = findViewById(R.id.tv_time)
        tvDuration     = findViewById(R.id.tv_duration)
        cardSummary    = findViewById(R.id.card_summary)
        tvTotalStudents = findViewById(R.id.tv_total_students)
        tvPresentCount = findViewById(R.id.tv_present_count)
        tvAbsentCount  = findViewById(R.id.tv_absent_count)
        tvPercentage   = findViewById(R.id.tv_percentage)
        recyclerView   = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        slideToConfirm = findViewById(R.id.slide_to_confirm)
        btnCancel      = findViewById(R.id.btn_cancel)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Draft Details"
            subtitle = departmentName
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = DraftStudentAttendanceAdapter(studentsList) { updateSummary() }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        slideToConfirm.setOnSlideCompleteListener { confirmPublishDraft() }
        btnCancel.setOnClickListener { finish() }
    }

    private fun loadDraftDetails() {
        showLoading(true)
        firestore.collection("attendance_drafts").document(draftId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    Toast.makeText(this, "Draft not found", Toast.LENGTH_SHORT).show()
                    finish(); return@addOnSuccessListener
                }

                classId     = doc.getString("classId")     ?: ""
                className   = doc.getString("className")   ?: ""
                subjectId   = doc.getString("subjectId")   ?: ""
                subjectName = doc.getString("subjectName") ?: ""
                date        = doc.getString("date")        ?: ""
                startTime   = doc.getString("startTime")   ?: ""
                endTime     = doc.getString("endTime")     ?: ""
                duration    = doc.getLong("duration")?.toInt() ?: 0
                createdBy   = doc.getString("createdBy")   ?: ""
                createdAt   = doc.getLong("createdAt")     ?: 0

                tvClassName.text   = className
                tvSubjectName.text = subjectName
                tvDate.text        = "Date: $date"
                tvTime.text        = "Time: $startTime - $endTime"
                tvDuration.text    = "Duration: $duration mins"

                studentsList.clear()
                (doc.get("students") as? List<Map<String, Any>> ?: emptyList()).forEach { m ->
                    studentsList.add(StudentAttendance(
                        id          = m["studentId"] as? String ?: "",
                        studentId   = m["studentId"] as? String ?: "",
                        studentName = m["studentName"] as? String ?: "",
                        rollNumber  = m["rollNumber"] as? String ?: "",
                        email       = m["email"] as? String ?: "",
                        status      = m["status"] as? String ?: "present",
                        remarks     = m["remarks"] as? String ?: ""
                    ))
                }

                studentsList.sortBy { it.rollNumber }
                adapter.notifyDataSetChanged()
                updateSummary()
                showLoading(false)
                recyclerView.visibility = if (studentsList.isEmpty()) View.GONE else View.VISIBLE
                Log.d(TAG, "✅ Loaded draft: ${studentsList.size} students")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Error loading draft", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false); finish()
            }
    }

    private fun updateSummary() {
        val total   = studentsList.size
        val present = studentsList.count { it.status == "present" }
        val absent  = total - present
        val pct     = if (total > 0) present.toDouble() / total * 100 else 0.0
        tvTotalStudents.text = total.toString()
        tvPresentCount.text  = present.toString()
        tvAbsentCount.text   = absent.toString()
        tvPercentage.text    = String.format("%.0f%%", pct)
    }

    private fun confirmPublishDraft() {
        if (studentsList.isEmpty()) {
            Toast.makeText(this, "No students to publish", Toast.LENGTH_SHORT).show()
            slideToConfirm.reset(); return
        }
        val presentCount = studentsList.count { it.status == "present" }
        val absentCount  = studentsList.size - presentCount
        val pct          = presentCount.toDouble() / studentsList.size * 100

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Publish Attendance")
            .setMessage(
                "Publish this attendance record?\n\n" +
                        "Class: $className\nSubject: $subjectName\nDate: $date\n" +
                        "Time: $startTime - $endTime\n\n" +
                        "Total: ${studentsList.size}\n" +
                        "Present: $presentCount (${String.format("%.1f", pct)}%)\n" +
                        "Absent: $absentCount\n\nThis cannot be undone."
            )
            .setPositiveButton("Publish") { _, _ -> publishDraft() }
            .setNegativeButton("Cancel")  { _, _ -> slideToConfirm.reset() }
            .setOnCancelListener { slideToConfirm.reset() }
            .show()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLISH — saves ONE document per session to "attendance" collection
    //
    // Firestore structure:
    // attendance/{sessionId}
    //   ├── [general session fields]
    //   ├── totalStudents, presentCount, absentCount, lateCount
    //   └── students: [
    //         { studentId, studentName, studentRollNo, status, remarks,
    //           + ALL general fields repeated for easy per-student querying }
    //       ]
    // ─────────────────────────────────────────────────────────────────────────
    private fun publishDraft() {
        showLoading(true)

        val currentUser = auth.currentUser
        val timestamp   = System.currentTimeMillis()

        // Date format coming in from draft: "23/02/2026" (dd/MM/yyyy)
        val inputSdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val isoSdf   = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monthSdf = SimpleDateFormat("yyyy-MM",    Locale.getDefault())
        val daySdf   = SimpleDateFormat("EEEE",       Locale.getDefault())

        val parsedDate = try { inputSdf.parse(date) } catch (e: Exception) { Date() } ?: Date()
        val isoDate    = isoSdf.format(parsedDate)    // "2026-02-23"
        val monthStr   = monthSdf.format(parsedDate)  // "2026-02"
        val dayStr     = daySdf.format(parsedDate)    // "Monday"

        firestore.collection("classes").document(classId)
            .get()
            .addOnSuccessListener { classDoc ->
                val semester     = classDoc.getLong("currentSemester")?.toInt() ?: 1
                val academicYear = classDoc.getString("academicYear") ?: ""
                val markedBy     = currentUser?.uid ?: ""
                val markedByName = currentUser?.displayName ?: currentUser?.email ?: ""

                val presentCount = studentsList.count { it.status == "present" }
                val lateCount    = studentsList.count { it.status == "late" }
                val absentCount  = studentsList.size - presentCount - lateCount

                // ── Build students[] array ─────────────────────────────────
                // Each map has student-specific fields + all general fields
                val studentsArray = studentsList.map { s ->
                    hashMapOf(
                        // Student-specific
                        "studentId"       to s.studentId,
                        "studentName"     to s.studentName,
                        "studentRollNo"   to s.rollNumber,
                        "status"          to s.status,
                        "remarks"         to s.remarks,

                        // General fields (repeated so each entry is self-contained)
                        "classId"         to classId,
                        "className"       to className,
                        "subjectId"       to subjectId,
                        "subjectName"     to subjectName,
                        "departmentId"    to departmentId,
                        "departmentName"  to departmentName,
                        "semester"        to semester,
                        "academicYear"    to academicYear,
                        "date"            to isoDate,
                        "month"           to monthStr,
                        "day"             to dayStr,
                        "sessionStartTime" to startTime,
                        "sessionEndTime"  to endTime,
                        "sessionDuration" to duration,
                        "markedBy"        to markedBy,
                        "markedByName"    to markedByName,
                        "markedAt"        to timestamp
                    )
                }

                // ── Build root session document ────────────────────────────
                val sessionDoc = hashMapOf(
                    // General session fields
                    "classId"          to classId,
                    "className"        to className,
                    "subjectId"        to subjectId,
                    "subjectName"      to subjectName,
                    "departmentId"     to departmentId,
                    "departmentName"   to departmentName,
                    "semester"         to semester,
                    "academicYear"     to academicYear,
                    "date"             to isoDate,
                    "month"            to monthStr,
                    "day"              to dayStr,
                    "sessionStartTime" to startTime,
                    "sessionEndTime"   to endTime,
                    "sessionDuration"  to duration,
                    "markedBy"         to markedBy,
                    "markedByName"     to markedByName,
                    "markedAt"         to timestamp,

                    // Aggregate counts
                    "totalStudents"    to studentsList.size,
                    "presentCount"     to presentCount,
                    "lateCount"        to lateCount,
                    "absentCount"      to absentCount,

                    // Embedded student records
                    "students"         to studentsArray
                )

                val batch      = firestore.batch()
                val sessionRef = firestore.collection("attendance").document()
                batch.set(sessionRef, sessionDoc)

                // Mark draft as published + link to session
                val draftRef = firestore.collection("attendance_drafts").document(draftId)
                batch.update(draftRef, mapOf(
                    "status"      to "published",
                    "publishedAt" to timestamp,
                    "publishedBy" to markedByName,
                    "sessionId"   to sessionRef.id
                ))

                batch.commit()
                    .addOnSuccessListener {
                        showLoading(false)
                        Toast.makeText(
                            this,
                            "✅ Published! $presentCount Present, $absentCount Absent",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.d(TAG, "✅ Session saved: ${sessionRef.id} (${studentsList.size} students)")
                        setResult(RESULT_OK)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        slideToConfirm.reset()
                        Log.e(TAG, "❌ Publish failed", e)
                        Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                slideToConfirm.reset()
                Log.e(TAG, "❌ Error getting class", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        slideToConfirm.isEnabled   = !show
        btnCancel.isEnabled        = !show
    }
}