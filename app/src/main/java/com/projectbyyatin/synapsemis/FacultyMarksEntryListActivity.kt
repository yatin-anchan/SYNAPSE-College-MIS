package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.FacultyMarksEntryListAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.Subject

class FacultyMarksEntryListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FacultyMarksEntryList"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipPending: Chip
    private lateinit var chipSubmitted: Chip
    private lateinit var semesterFilter: AutoCompleteTextView
    private lateinit var statusFilter: AutoCompleteTextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: FacultyMarksEntryListAdapter

    private var facultyId = ""
    private var facultyName = ""
    private var departmentId = ""

    private val allExams = mutableListOf<ExamWithSubjects>()
    private val filteredExams = mutableListOf<ExamWithSubjects>()
    private val facultySubjects = mutableListOf<Subject>()

    private var selectedFilter = "all"
    private var selectedSemester: Int? = null
    private var selectedStatus: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_marks_entry_list)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        facultyName = intent.getStringExtra("FACULTY_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (facultyId.isEmpty()) {
            facultyId = auth.currentUser?.uid ?: ""
        }

        initializeViews()
        setupToolbar()
        setupFilters()
        setupChips()
        setupRecyclerView()
        loadData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)
        emptyText = findViewById(R.id.empty_text)
        chipGroup = findViewById(R.id.chip_group)
        chipAll = findViewById(R.id.chip_all)
        chipPending = findViewById(R.id.chip_pending)
        chipSubmitted = findViewById(R.id.chip_submitted)
        semesterFilter = findViewById(R.id.semester_filter)
        statusFilter = findViewById(R.id.status_filter)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Marks Entry"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupFilters() {
        val semesters = listOf("All Semesters") + (1..8).map { "Semester $it" }
        val semesterAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesters)
        semesterFilter.setAdapter(semesterAdapter)
        semesterFilter.setText("All Semesters", false)

        semesterFilter.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = if (position == 0) null else position
            applyFilter()
        }

        val statuses = listOf("All Status", "Confirmed", "Ongoing", "Completed")
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses)
        statusFilter.setAdapter(statusAdapter)
        statusFilter.setText("All Status", false)

        statusFilter.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = when (position) {
                0 -> null
                1 -> "confirmed"
                2 -> "ongoing"
                3 -> "completed"
                else -> null
            }
            applyFilter()
        }
    }

    private fun setupChips() {
        chipAll.setOnClickListener {
            selectedFilter = "all"
            applyFilter()
        }
        chipPending.setOnClickListener {
            selectedFilter = "pending"
            applyFilter()
        }
        chipSubmitted.setOnClickListener {
            selectedFilter = "submitted"
            applyFilter()
        }
        chipAll.isChecked = true
    }

    private fun setupRecyclerView() {
        adapter = FacultyMarksEntryListAdapter(
            filteredExams,
            onItemClick = { examWithSubjects, subject ->
                openMarksEntry(examWithSubjects.exam, subject)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadData() {
        showLoading(true)
        loadFacultyInfo()
    }

    private fun loadFacultyInfo() {
        Log.d(TAG, "Loading faculty info for: $facultyId")
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { facultyDoc ->
                if (facultyDoc.exists()) {
                    departmentId = facultyDoc.getString("departmentId") ?: ""
                    Log.d(TAG, "Faculty department: $departmentId")
                }
                loadFacultySubjects()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading faculty info: ${e.message}", e)
                loadFacultySubjects()
            }
    }

    private fun loadFacultySubjects() {
        Log.d(TAG, "Loading subjects for faculty: $facultyId")
        firestore.collection("subjects")
            .whereEqualTo("assignedFacultyId", facultyId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Strategy 1: Found ${documents.size()} subjects by assignedFacultyId")
                if (!documents.isEmpty) {
                    processFacultySubjects(documents)
                } else {
                    loadFacultySubjectsByEmail()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Strategy 1 error: ${e.message}", e)
                loadFacultySubjectsByEmail()
            }
    }

    private fun loadFacultySubjectsByEmail() {
        val email = auth.currentUser?.email ?: ""
        if (email.isEmpty()) {
            Log.e(TAG, "No email available, trying Strategy 3")
            loadFacultySubjectsByNames()
            return
        }
        Log.d(TAG, "Strategy 2: Trying by assignedFacultyEmail = $email")
        firestore.collection("subjects")
            .whereEqualTo("assignedFacultyEmail", email)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Strategy 2: Found ${documents.size()} subjects by email")
                if (!documents.isEmpty) {
                    processFacultySubjects(documents)
                } else {
                    loadFacultySubjectsByNames()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Strategy 2 error: ${e.message}", e)
                loadFacultySubjectsByNames()
            }
    }

    private fun loadFacultySubjectsByNames() {
        Log.d(TAG, "Strategy 3: Loading by subject names from faculty doc")
        firestore.collection("faculty").document(facultyId)
            .get()
            .addOnSuccessListener { facultyDoc ->
                if (!facultyDoc.exists()) {
                    Log.e(TAG, "Faculty document not found")
                    showLoading(false)
                    showEmptyState("No subjects assigned to you yet")
                    return@addOnSuccessListener
                }
                val subjectNames = facultyDoc.get("subjects") as? List<String> ?: emptyList()
                Log.d(TAG, "Found ${subjectNames.size} subject names in faculty doc")
                if (subjectNames.isEmpty()) {
                    showLoading(false)
                    showEmptyState("No subjects assigned to you yet")
                    return@addOnSuccessListener
                }
                loadSubjectsBatch(subjectNames, 0)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Strategy 3 error: ${e.message}", e)
                showLoading(false)
                showEmptyState("Error loading subjects")
            }
    }

    private fun loadSubjectsBatch(subjectNames: List<String>, startIndex: Int) {
        if (startIndex >= subjectNames.size) {
            finishLoadingSubjects()
            return
        }
        val endIndex = minOf(startIndex + 10, subjectNames.size)
        val batch = subjectNames.subList(startIndex, endIndex)
        firestore.collection("subjects")
            .whereIn("name", batch)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { doc ->
                    val subject = doc.toObject(Subject::class.java).apply { id = doc.id }
                    if (!facultySubjects.any { it.id == subject.id }) {
                        facultySubjects.add(subject)
                    }
                }
                loadSubjectsBatch(subjectNames, endIndex)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Batch loading error: ${e.message}", e)
                finishLoadingSubjects()
            }
    }

    private fun processFacultySubjects(documents: com.google.firebase.firestore.QuerySnapshot) {
        facultySubjects.clear()
        documents.forEach { doc ->
            val subject = doc.toObject(Subject::class.java).apply { id = doc.id }
            facultySubjects.add(subject)
        }
        finishLoadingSubjects()
    }

    private fun finishLoadingSubjects() {
        Log.d(TAG, "Loaded ${facultySubjects.size} subjects")
        if (facultySubjects.isEmpty()) {
            showLoading(false)
            showEmptyState("No subjects assigned to you yet")
        } else {
            loadExams()
        }
    }

    // ─── FIX: Parse Exam manually to avoid Timestamp→Long crash ──────────────
    // Firestore stores moderatedAt inside subjectsMarksStatus as a Timestamp,
    // but SubjectMarksStatus.moderatedAt is Long. Calling doc.toObject(Exam::class.java)
    // triggers a RuntimeException. We only need a subset of fields here anyway,
    // so we build the Exam manually and skip subjectsMarksStatus entirely.
    private fun loadExams() {
        Log.d(TAG, "=== LOADING EXAMS ===")
        firestore.collection("exams")
            .orderBy("startDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "✅ SUCCESS: Found ${documents.size()} exams")
                allExams.clear()

                documents.forEach { doc ->
                    // ✅ Manual construction — never calls toObject(Exam) so the
                    //    Timestamp inside subjectsMarksStatus.moderatedAt is never touched.
                    val exam = Exam(
                        id                = doc.id,
                        examName          = doc.getString("examName") ?: "",
                        semester          = (doc.getLong("semester") ?: 1L).toInt(),
                        academicYear      = doc.getString("academicYear") ?: "",
                        startDate         = doc.getLong("startDate") ?: 0L,
                        endDate           = doc.getLong("endDate") ?: 0L,
                        examType          = doc.getString("examType") ?: "",
                        courseId          = doc.getString("courseId") ?: "",
                        courseName        = doc.getString("courseName") ?: "",
                        isActive          = doc.getBoolean("isActive") ?: true,
                        status            = doc.getString("status") ?: "draft",
                        isConfirmed       = doc.getBoolean("isConfirmed") ?: false,
                        marksEntryEnabled = doc.getBoolean("marksEntryEnabled") ?: false,
                        marksPublished    = doc.getBoolean("marksPublished") ?: false,
                        subjects          = parseExamSubjects(doc)
                        // subjectsMarksStatus intentionally omitted — not needed here
                        // and its moderatedAt field is a Timestamp in Firestore
                    )

                    if (!exam.marksEntryEnabled || !exam.isActive) {
                        Log.d(TAG, "Skipping exam ${exam.examName}: marksEntryEnabled=${exam.marksEntryEnabled}, isActive=${exam.isActive}")
                        return@forEach
                    }

                    val relevantSubjects = exam.subjects.filter { examSubject ->
                        facultySubjects.any { it.id == examSubject.subjectId }
                    }.map {
                        SubjectWithStatus(it, isSubmitted = false)
                    }

                    Log.d(TAG, "Exam ${exam.examName}: ${relevantSubjects.size} relevant subjects")

                    if (relevantSubjects.isNotEmpty()) {
                        allExams.add(ExamWithSubjects(exam, relevantSubjects))
                    }
                }

                Log.d(TAG, "✅ FINAL: ${allExams.size} exams with faculty subjects")
                loadMarksStatus()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ FAILED: ${e.message}", e)
                showLoading(false)
            }
    }

    /**
     * Safely parses the "subjects" array from an exam document without calling
     * toObject(), so no Timestamp field can cause a deserialization crash.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseExamSubjects(
        doc: com.google.firebase.firestore.DocumentSnapshot
    ): List<ExamSubject> {
        val rawList = doc.get("subjects") as? List<Map<String, Any?>> ?: return emptyList()
        return rawList.mapNotNull { map ->
            try {
                ExamSubject(
                    subjectId         = map["subjectId"] as? String ?: "",
                    subjectName       = map["subjectName"] as? String ?: "",
                    subjectCode       = map["subjectCode"] as? String ?: "",
                    courseId          = map["courseId"] as? String ?: "",
                    courseName        = map["courseName"] as? String ?: "",
                    examDate          = (map["examDate"] as? Long) ?: 0L,
                    startTime         = map["startTime"] as? String ?: "",
                    endTime           = map["endTime"] as? String ?: "",
                    examType          = map["examType"] as? String ?: "Written",
                    maxMarks          = ((map["maxMarks"] as? Long) ?: 100L).toInt(),
                    writtenMaxMarks   = ((map["writtenMaxMarks"] as? Long) ?: 0L).toInt(),
                    internalMaxMarks  = ((map["internalMaxMarks"] as? Long) ?: 0L).toInt(),
                    assignedFacultyId = map["assignedFacultyId"] as? String ?: ""
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing ExamSubject: ${e.message}")
                null
            }
        }
    }

    private fun loadMarksStatus() {
        if (allExams.isEmpty()) {
            showLoading(false)
            showEmptyState("No exams available for marks entry")
            return
        }

        var totalToLoad = 0
        allExams.forEach { examWithSubjects ->
            totalToLoad += examWithSubjects.relevantSubjects.size
        }

        var loadedCount = 0

        allExams.forEach { examWithSubjects ->
            examWithSubjects.relevantSubjects.forEach { subjectWithStatus ->
                firestore.collection("examMarks")
                    .whereEqualTo("examId", examWithSubjects.exam.id)
                    .whereEqualTo("subjectId", subjectWithStatus.examSubject.subjectId)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { docs ->
                        if (!docs.isEmpty) {
                            val marks = docs.documents[0]
                                .toObject(com.projectbyyatin.synapsemis.models.ExamMarks::class.java)
                            subjectWithStatus.isSubmitted = marks?.isSubmitted ?: false
                        }
                        loadedCount++
                        if (loadedCount >= totalToLoad) finishLoading()
                    }
                    .addOnFailureListener {
                        loadedCount++
                        if (loadedCount >= totalToLoad) finishLoading()
                    }
            }
        }
    }

    private fun finishLoading() {
        Log.d(TAG, "Finishing loading with ${allExams.size} total exams")
        allExams.sortByDescending { it.exam.startDate }
        applyFilter()
        showLoading(false)
    }

    private fun applyFilter() {
        filteredExams.clear()

        Log.d(TAG, "=== APPLYING FILTERS ===")
        Log.d(TAG, "Selected semester: $selectedSemester")
        Log.d(TAG, "Selected status: $selectedStatus")
        Log.d(TAG, "Selected submission filter: $selectedFilter")

        val examFiltered = allExams.filter { examWithSubjects ->
            val exam = examWithSubjects.exam
            val semesterMatch = selectedSemester?.let { exam.semester == it } ?: true
            val statusMatch   = selectedStatus?.let { exam.status == it } ?: true
            semesterMatch && statusMatch
        }

        Log.d(TAG, "After semester/status filter: ${examFiltered.size} exams")

        when (selectedFilter) {
            "all" -> filteredExams.addAll(examFiltered)
            "pending" -> {
                examFiltered.forEach { examWithSubjects ->
                    val pendingSubjects = examWithSubjects.relevantSubjects.filter { !it.isSubmitted }
                    if (pendingSubjects.isNotEmpty()) {
                        filteredExams.add(examWithSubjects.copy(relevantSubjects = pendingSubjects))
                    }
                }
            }
            "submitted" -> {
                examFiltered.forEach { examWithSubjects ->
                    val submittedSubjects = examWithSubjects.relevantSubjects.filter { it.isSubmitted }
                    if (submittedSubjects.isNotEmpty()) {
                        filteredExams.add(examWithSubjects.copy(relevantSubjects = submittedSubjects))
                    }
                }
            }
        }

        Log.d(TAG, "After submission filter: ${filteredExams.size} exams to display")
        adapter.notifyDataSetChanged()
        updateEmptyView()
    }

    private fun openMarksEntry(exam: Exam, subject: SubjectWithStatus) {
        Log.d(TAG, "Opening marks entry - Exam: ${exam.id}, Subject: ${subject.examSubject.subjectId}")

        val courseId = when {
            exam.courseId.isNotBlank() -> {
                Log.d(TAG, "Using exam.courseId: ${exam.courseId}")
                exam.courseId
            }
            subject.examSubject.courseId.isNotBlank() -> {
                Log.d(TAG, "Using subject.courseId: ${subject.examSubject.courseId}")
                subject.examSubject.courseId
            }
            else -> {
                Log.e(TAG, "❌ No courseId found in exam OR subject!")
                Toast.makeText(this, "Missing course ID in exam/subject", Toast.LENGTH_SHORT).show()
                return
            }
        }

        val intent = Intent(this, FacultyMarksEntryActivity::class.java).apply {
            putExtra("EXAM_ID",            exam.id)
            putExtra("SUBJECT_ID",         subject.examSubject.subjectId)
            putExtra("SUBJECT_NAME",       subject.examSubject.subjectName)
            putExtra("COURSE_ID",          courseId)
            putExtra("EXAM_TYPE",          subject.examSubject.examType)
            putExtra("WRITTEN_MAX_MARKS",  subject.examSubject.writtenMaxMarks)
            putExtra("INTERNAL_MAX_MARKS", subject.examSubject.internalMaxMarks)
            putExtra("MAX_MARKS",          subject.examSubject.maxMarks)
        }

        Log.d(TAG, "✅ Intent extras sent - EXAM_ID: '${exam.id}', SUBJECT_ID: '${subject.examSubject.subjectId}', COURSE_ID: '$courseId'")
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility    = if (show) View.GONE   else View.VISIBLE
    }

    private fun showEmptyState(message: String) {
        emptyView.visibility    = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyText.text          = message
    }

    private fun updateEmptyView() {
        if (filteredExams.isEmpty()) {
            emptyView.visibility    = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyText.text = when (selectedFilter) {
                "pending"   -> "No pending marks entries"
                "submitted" -> "No submitted marks"
                else        -> "No exams available for marks entry"
            }
        } else {
            emptyView.visibility    = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadData()
    }

    // ─── Data classes ─────────────────────────────────────────────────────────

    data class SubjectWithStatus(
        val examSubject: ExamSubject,
        var isSubmitted: Boolean = false
    )

    data class ExamWithSubjects(
        val exam: Exam,
        val relevantSubjects: List<SubjectWithStatus>
    )
}