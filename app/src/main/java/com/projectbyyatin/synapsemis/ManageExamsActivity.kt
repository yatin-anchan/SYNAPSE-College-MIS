package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.ManageExamsAdapterNew
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

class ManageExamsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var semesterFilter: AutoCompleteTextView
    private lateinit var statusFilter: AutoCompleteTextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var loadingProgress: ProgressBar
    private lateinit var fab: FloatingActionButton

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ManageExamsAdapterNew
    private var examsList = mutableListOf<Exam>()

    private var selectedSemester: Int? = null
    private var selectedStatus: String? = null
    private var currentUserRole = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_exams)

        initializeViews()
        setupToolbar()
        setupFilters()
        setupRecyclerView()
        setupFab()
        loadUserRole()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        semesterFilter = findViewById(R.id.semester_filter)
        statusFilter = findViewById(R.id.status_filter)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)
        loadingProgress = findViewById(R.id.loading_progress)
        fab = findViewById(R.id.fab)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Exams"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupFilters() {
        // Semester filter
        val semesters = listOf("All Semesters") + (1..8).map { "Semester $it" }
        val semesterAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesters)
        semesterFilter.setAdapter(semesterAdapter)
        semesterFilter.setText("All Semesters", false)

        semesterFilter.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = if (position == 0) null else position
            loadExams()
        }

        // Status filter
        val statuses = listOf("All Status", "Confirmed", "Ongoing", "Completed", "Draft")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses)
        statusFilter.setAdapter(statusAdapter)
        statusFilter.setText("All Status", false)

        statusFilter.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = when (position) {
                0 -> null
                1 -> "confirmed"
                2 -> "ongoing"
                3 -> "completed"
                4 -> "draft"
                else -> null
            }
            loadExams()
        }
    }

    private fun setupRecyclerView() {
        adapter = ManageExamsAdapterNew(
            examsList,
            onViewClick = { exam -> viewExamDetails(exam) },
            onEnterMarksClick = { exam -> handleEnterMarks(exam) },
            onEnableMarksEntryClick = { exam -> enableMarksEntry(exam) },
            onPublishMarksClick = { exam -> publishMarks(exam) },
            currentUserRole = currentUserRole
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        fab.setOnClickListener {
            startActivity(Intent(this, ExamCreationStep1Activity::class.java))
        }
    }

    private fun loadUserRole() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                currentUserRole = userDoc.getString("role")?.lowercase() ?: ""
                adapter.updateRole(currentUserRole)
                loadExams()
            }
    }

    private fun loadExams() {
        showLoading(true)

        var query: Query = firestore.collection("exams")
            .orderBy("startDate", Query.Direction.DESCENDING)

        // Apply filters
        selectedSemester?.let { semester ->
            query = query.whereEqualTo("semester", semester)
        }

        selectedStatus?.let { status ->
            query = query.whereEqualTo("status", status)
        }

        query.get()
            .addOnSuccessListener { documents ->
                examsList.clear()

                documents.forEach { document ->
                    try {
                        // ✅ SAFE PARSING - Handles Timestamp -> Long conversion
                        val examData = document.data
                        if (examData != null) {
                            val exam = Exam().apply {
                                id = document.id
                                examName = examData["examName"]?.toString() ?: ""
                                semester = (examData["semester"] as? Number)?.toInt() ?: 1
                                academicYear = examData["academicYear"]?.toString() ?: ""

                                // ✅ Convert Timestamp to Long safely
                                startDate = safeTimestampToLong(examData["startDate"])
                                endDate = safeTimestampToLong(examData["endDate"])
                                createdAt = safeTimestampToLong(examData["createdAt"])
                                confirmedAt = safeTimestampToLong(examData["confirmedAt"])
                                this.marksPublishedAt = safeTimestampToLong(examData["marksPublishedAt"])

                                courseId = examData["courseId"]?.toString() ?: ""
                                courseName = examData["courseName"]?.toString() ?: ""
                                isActive = examData["isActive"] as? Boolean ?: true
                                status = examData["status"]?.toString() ?: "draft"
                                isConfirmed = examData["isConfirmed"] as? Boolean ?: false
                                timetableGenerated = examData["timetableGenerated"] as? Boolean ?: false
                                createdBy = examData["createdBy"]?.toString() ?: ""
                                marksEntryEnabled = examData["marksEntryEnabled"] as? Boolean ?: false
                                marksPublished = examData["marksPublished"] as? Boolean ?: false
                                marksPublishedBy = examData["marksPublishedBy"]?.toString() ?: ""

                                // ✅ Handle nested subjectsMarksStatus
                                val subjectsStatus = examData["subjectsMarksStatus"] as? Map<*, *>
                                if (subjectsStatus != null) {
                                    subjectsMarksStatus = parseSubjectsMarksStatus(subjectsStatus)
                                }
                            }
                            examsList.add(exam)
                        }
                    } catch (e: Exception) {
                        // Skip corrupted documents silently
                        android.util.Log.e("ManageExams", "Failed to parse exam ${document.id}: ${e.message}")
                    }
                }

                adapter.updateList(examsList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading exams: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun safeTimestampToLong(value: Any?): Long {
        return when (value) {
            is com.google.firebase.Timestamp -> value.toDate().time
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private fun parseSubjectsMarksStatus(rawStatus: Map<*, *>): Map<String, SubjectMarksStatus> {
        val result = mutableMapOf<String, SubjectMarksStatus>()

        rawStatus.forEach { (subjectId, statusData) ->
            try {
                val statusMap = statusData as? Map<*, *>
                if (statusMap != null) {
                    val status = SubjectMarksStatus().apply {
                        this.subjectId = subjectId.toString()
                        totalStudents = (statusMap["totalStudents"] as? Number)?.toInt() ?: 0
                        marksEntered = (statusMap["marksEntered"] as? Number)?.toInt() ?: 0
                        marksSubmitted = statusMap["marksSubmitted"] as? Boolean ?: false
                        submittedBy = statusMap["submittedBy"]?.toString() ?: ""
                        submittedAt = safeTimestampToLong(statusMap["submittedAt"])
                        moderated = statusMap["moderated"] as? Boolean ?: false
                        moderatedBy = statusMap["moderatedBy"]?.toString() ?: ""
                        this.moderatedAt = safeTimestampToLong(statusMap["moderatedAt"])
                        readyForPublish = statusMap["readyForPublish"] as? Boolean ?: false
                    }
                    result[subjectId.toString()] = status
                }
            } catch (e: Exception) {
                android.util.Log.e("ManageExams", "Failed to parse SubjectMarksStatus for $subjectId")
            }
        }
        return result
    }


    private fun viewExamDetails(exam: Exam) {
        val intent = Intent(this, ExamDetailsActivity::class.java)
        intent.putExtra("EXAM_ID", exam.id)
        startActivity(intent)
    }

    private fun handleEnterMarks(exam: Exam) {
        when (currentUserRole) {
            "coe" -> {
                // COE goes to publish activity
                val intent = Intent(this, CoeMarksPublishActivity::class.java)
                intent.putExtra("EXAM_ID", exam.id)
                startActivity(intent)
            }
            "hod" -> {
                // HOD can moderate or view subjects
                val intent = Intent(this, HodMarksManagementActivity::class.java)
                intent.putExtra("EXAM_ID", exam.id)
                startActivity(intent)
            }
            "faculty" -> {
                // Faculty enters marks for their subjects
                val intent = Intent(this, FacultySubjectsListActivity::class.java)
                intent.putExtra("EXAM_ID", exam.id)
                startActivity(intent)
            }
            else -> {
                Toast.makeText(this, "Invalid user role", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enableMarksEntry(exam: Exam) {
        if (currentUserRole != "coe") {
            Toast.makeText(this, "Only COE can enable marks entry", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Enable Marks Entry")
            .setMessage("Allow faculty to start entering marks for this exam?")
            .setPositiveButton("Enable") { _, _ ->
                firestore.collection("exams").document(exam.id)
                    .update("marksEntryEnabled", true)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Marks entry enabled", Toast.LENGTH_SHORT).show()
                        loadExams()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun publishMarks(exam: Exam) {
        val intent = Intent(this, CoeMarksPublishActivity::class.java)
        intent.putExtra("EXAM_ID", exam.id)
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (examsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadExams()
    }
}