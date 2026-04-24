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
import com.projectbyyatin.synapsemis.adapters.HodMarksModerationListAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.Subject
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

class HodMarksModerationListActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HodMarksModerationList"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipPendingModeration: Chip
    private lateinit var chipModerated: Chip
    private lateinit var semesterFilter: AutoCompleteTextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: HodMarksModerationListAdapter

    private var hodId = ""
    private var departmentId = ""

    private val allExams = mutableListOf<ExamWithSubjects>()
    private val filteredExams = mutableListOf<ExamWithSubjects>()

    // All subjects belonging to HOD's department (not faculty-specific)
    private val departmentSubjects = mutableListOf<Subject>()

    private var selectedFilter = "all"
    private var selectedSemester: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_marks_moderation_list)

        hodId = intent.getStringExtra("HOD_ID") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        if (hodId.isEmpty()) {
            hodId = auth.currentUser?.uid ?: ""
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
        chipPendingModeration = findViewById(R.id.chip_pending_moderation)
        chipModerated = findViewById(R.id.chip_moderated)
        semesterFilter = findViewById(R.id.semester_filter)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Marks Moderation"
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
    }

    private fun setupChips() {
        chipAll.setOnClickListener {
            selectedFilter = "all"
            updateChipSelection()
            applyFilter()
        }
        chipPendingModeration.setOnClickListener {
            selectedFilter = "pending_moderation"
            updateChipSelection()
            applyFilter()
        }
        chipModerated.setOnClickListener {
            selectedFilter = "moderated"
            updateChipSelection()
            applyFilter()
        }
        updateChipSelection()
    }

    private fun updateChipSelection() {
        chipAll.isChecked = selectedFilter == "all"
        chipPendingModeration.isChecked = selectedFilter == "pending_moderation"
        chipModerated.isChecked = selectedFilter == "moderated"
    }

    private fun setupRecyclerView() {
        adapter = HodMarksModerationListAdapter(
            filteredExams,
            onSubjectClick = { examWithSubjects, subject ->
                openMarksModeration(examWithSubjects.exam, subject)
            }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadData() {
        showLoading(true)
        loadHodDepartmentInfo()
    }

    private fun loadHodDepartmentInfo() {
        Log.d(TAG, "Loading HOD info for: $hodId")

        firestore.collection("hod").document(hodId)
            .get()
            .addOnSuccessListener { hodDoc ->
                if (hodDoc.exists()) {
                    departmentId = hodDoc.getString("departmentId") ?: departmentId
                    Log.d(TAG, "HOD department: $departmentId")
                }
                loadDepartmentSubjects()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading HOD info: ${e.message}", e)
                loadDepartmentSubjects()
            }
    }

    /**
     * KEY DIFFERENCE FROM FACULTY SIDE:
     * HOD loads ALL subjects in their department, not just their own assigned subjects.
     * This gives them visibility over every subject that needs moderation.
     */
    private fun loadDepartmentSubjects() {
        Log.d(TAG, "Loading ALL department subjects for departmentId: $departmentId")

        firestore.collection("subjects")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                departmentSubjects.clear()
                documents.forEach { doc ->
                    val subject = doc.toObject(Subject::class.java).apply { id = doc.id }
                    departmentSubjects.add(subject)
                }
                Log.d(TAG, "Loaded ${departmentSubjects.size} department subjects")

                if (departmentSubjects.isEmpty()) {
                    showLoading(false)
                    showEmptyState("No subjects found in your department")
                } else {
                    loadExams()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading department subjects: ${e.message}", e)
                showLoading(false)
                showEmptyState("Error loading department subjects")
            }
    }

    // ─── Drop-in replacement for loadExams() ─────────────────────────────────────
// Paste this into HodMarksModerationListActivity, replacing the existing loadExams().
// No model changes required.

    // ══════════════════════════════════════════════════════════════════════════════
// REPLACE your entire loadExams() with this.
// ALSO add the two private helpers below it into the same Activity class.
// ══════════════════════════════════════════════════════════════════════════════

    private fun loadExams() {
        Log.d(TAG, "Loading exams for HOD moderation")

        firestore.collection("exams")
            .orderBy("startDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Found ${documents.size()} total exams")
                allExams.clear()

                documents.forEach { doc ->
                    // ⚠️  DO NOT call doc.toObject(Exam::class.java) here.
                    // Firestore stores moderatedAt as Timestamp but the model
                    // declares it as Long → instant crash. Parse raw instead.
                    val exam = buildExamFromRaw(doc.id, doc.data ?: return@forEach)

                    if (!exam.marksEntryEnabled || !exam.isActive) return@forEach

                    val relevantSubjects = exam.subjects
                        .filter { examSubject ->
                            departmentSubjects.any { it.id == examSubject.subjectId }
                        }
                        .map { examSubject -> SubjectWithModerationStatus(examSubject) }

                    Log.d(TAG, "Exam '${exam.examName}': ${relevantSubjects.size} department subjects")

                    if (relevantSubjects.isNotEmpty()) {
                        allExams.add(ExamWithSubjects(exam, relevantSubjects.toMutableList()))
                    }
                }

                Log.d(TAG, "Total exams with department subjects: ${allExams.size}")
                loadMarksModerationStatus()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading exams: ${e.message}", e)
                showLoading(false)
            }
    }

    // ─────────────────────────────────────────────────────────────────────────────
// Builds an Exam from raw Firestore data, safely converting every Timestamp
// field to Long so the model never sees a Timestamp where it expects Long.
// ─────────────────────────────────────────────────────────────────────────────
    @Suppress("UNCHECKED_CAST")
    private fun buildExamFromRaw(id: String, raw: Map<String, Any>): Exam {

        // ── subjectsMarksStatus ───────────────────────────────────────────────
        val parsedStatus = mutableMapOf<String, SubjectMarksStatus>()
        (raw["subjectsMarksStatus"] as? Map<*, *>)?.forEach { (key, value) ->
            val m = value as? Map<*, *> ?: return@forEach
            parsedStatus[key.toString()] = SubjectMarksStatus(
                subjectId       = m["subjectId"]       as? String  ?: "",
                totalStudents   = tsLong(m["totalStudents"]).toInt(),
                marksEntered    = tsLong(m["marksEntered"]).toInt(),
                marksSubmitted  = m["marksSubmitted"]  as? Boolean ?: false,
                submittedBy     = m["submittedBy"]     as? String  ?: "",
                submittedAt     = tsLong(m["submittedAt"]),
                moderated       = m["moderated"]       as? Boolean ?: false,
                moderatedBy     = m["moderatedBy"]     as? String  ?: "",
                moderatedAt     = tsLong(m["moderatedAt"]),   // ← was crashing here
                readyForPublish = m["readyForPublish"] as? Boolean ?: false
            )
        }

        // ── subjects (List<ExamSubject>) ──────────────────────────────────────
        val parsedSubjects = mutableListOf<ExamSubject>()
        (raw["subjects"] as? List<*>)?.forEach { item ->
            val s = item as? Map<*, *> ?: return@forEach
            parsedSubjects.add(
                ExamSubject(
                    subjectId        = s["subjectId"]        as? String ?: "",
                    subjectName      = s["subjectName"]      as? String ?: "",
                    subjectCode      = s["subjectCode"]      as? String ?: "",
                    courseId         = s["courseId"]         as? String ?: "",
                    courseName       = s["courseName"]       as? String ?: "",
                    examDate         = tsLong(s["examDate"]),
                    startTime        = s["startTime"]        as? String ?: "",
                    endTime          = s["endTime"]          as? String ?: "",
                    venue            = s["venue"]            as? String ?: "",
                    examType         = s["examType"]         as? String ?: "Written",
                    maxMarks         = tsLong(s["maxMarks"]).toInt(),
                    writtenMaxMarks  = tsLong(s["writtenMaxMarks"]).toInt(),
                    internalMaxMarks = tsLong(s["internalMaxMarks"]).toInt(),
                    duration         = (s["duration"] as? Number)?.toFloat() ?: 3f,
                    assignedFacultyId = s["assignedFacultyId"] as? String ?: ""
                )
            )
        }

        // ── courses ───────────────────────────────────────────────────────────
        val parsedCourses = (raw["courses"] as? List<*>)
            ?.filterIsInstance<String>() ?: emptyList()

        return Exam(
            id                 = id,
            examName           = raw["examName"]           as? String  ?: "",
            semester           = tsLong(raw["semester"]).toInt(),
            academicYear       = raw["academicYear"]       as? String  ?: "",
            startDate          = tsLong(raw["startDate"]),
            endDate            = tsLong(raw["endDate"]),
            examType           = raw["examType"]           as? String  ?: "",
            courseId           = raw["courseId"]           as? String  ?: "",
            courseName         = raw["courseName"]         as? String  ?: "",
            isActive           = raw["isActive"]           as? Boolean ?: true,
            courses            = parsedCourses,
            subjects           = parsedSubjects,
            status             = raw["status"]             as? String  ?: "draft",
            isConfirmed        = raw["isConfirmed"]        as? Boolean ?: false,
            timetableGenerated = raw["timetableGenerated"] as? Boolean ?: false,
            createdBy          = raw["createdBy"]          as? String  ?: "",
            createdAt          = tsLong(raw["createdAt"]),
            confirmedAt        = tsLong(raw["confirmedAt"]),
            marksEntryEnabled  = raw["marksEntryEnabled"]  as? Boolean ?: false,
            marksPublished     = raw["marksPublished"]     as? Boolean ?: false,
            marksPublishedAt   = tsLong(raw["marksPublishedAt"]),
            marksPublishedBy   = raw["marksPublishedBy"]   as? String  ?: "",
            subjectsMarksStatus = parsedStatus
        )
    }

    // Converts Firestore Timestamp, Long, or Double safely to Long (epoch ms).
    private fun tsLong(value: Any?): Long = when (value) {
        is com.google.firebase.Timestamp -> value.toDate().time
        is Long                          -> value
        is Double                        -> value.toLong()
        is Int                           -> value.toLong()
        else                             -> 0L
    }

    private fun loadMarksModerationStatus() {
        if (allExams.isEmpty()) {
            showLoading(false)
            showEmptyState("No exams available for moderation")
            return
        }

        var totalToLoad = 0
        allExams.forEach { totalToLoad += it.relevantSubjects.size }
        var loadedCount = 0

        allExams.forEach { examWithSubjects ->
            examWithSubjects.relevantSubjects.forEach { subjectStatus ->

                // Check submission + moderation status from the exam document's subjectsMarksStatus map
                firestore.collection("exams").document(examWithSubjects.exam.id)
                    .get()
                    .addOnSuccessListener { examDoc ->
                        val subjectsMarksStatus = examDoc.get("subjectsMarksStatus") as? Map<*, *>
                        val thisSubjectStatus = subjectsMarksStatus?.get(subjectStatus.examSubject.subjectId) as? Map<*, *>

                        subjectStatus.isSubmitted = thisSubjectStatus?.get("marksSubmitted") as? Boolean ?: false
                        subjectStatus.isModerated = thisSubjectStatus?.get("moderated") as? Boolean ?: false

                        Log.d(TAG, "Subject '${subjectStatus.examSubject.subjectName}': " +
                                "submitted=${subjectStatus.isSubmitted}, moderated=${subjectStatus.isModerated}")

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
        allExams.sortByDescending { it.exam.startDate }
        applyFilter()
        showLoading(false)
    }

    private fun applyFilter() {
        filteredExams.clear()

        val examFiltered = allExams.filter { examWithSubjects ->
            selectedSemester?.let { examWithSubjects.exam.semester == it } ?: true
        }

        when (selectedFilter) {
            "all" -> filteredExams.addAll(examFiltered)

            "pending_moderation" -> {
                examFiltered.forEach { examWithSubjects ->
                    // Submitted by faculty but not yet moderated by HOD
                    val pending = examWithSubjects.relevantSubjects.filter {
                        it.isSubmitted && !it.isModerated
                    }
                    if (pending.isNotEmpty()) {
                        filteredExams.add(examWithSubjects.copy(relevantSubjects = pending.toMutableList()))
                    }
                }
            }

            "moderated" -> {
                examFiltered.forEach { examWithSubjects ->
                    val moderated = examWithSubjects.relevantSubjects.filter { it.isModerated }
                    if (moderated.isNotEmpty()) {
                        filteredExams.add(examWithSubjects.copy(relevantSubjects = moderated.toMutableList()))
                    }
                }
            }
        }

        adapter.notifyDataSetChanged()
        updateEmptyView()
    }

    private fun openMarksModeration(exam: Exam, subject: SubjectWithModerationStatus) {
        // Block entry if already moderated — locked state, no action
        if (subject.isModerated) {
            Toast.makeText(this, "🔒 This subject has already been moderated and locked", Toast.LENGTH_SHORT).show()
            return
        }

        // HOD can now moderate even if faculty hasn't submitted yet
        // (removed the isSubmitted check)

        val courseId = exam.courseId.takeIf { it.isNotBlank() }
            ?: subject.examSubject.courseId.takeIf { it.isNotBlank() }
            ?: run {
                Toast.makeText(this, "Missing course ID", Toast.LENGTH_SHORT).show()
                return
            }

        Log.d(TAG, "Opening moderation — Exam: ${exam.id}, Subject: ${subject.examSubject.subjectId}")

        val intent = Intent(this, HodMarksModerationActivity::class.java).apply {
            putExtra("EXAM_ID", exam.id)
            putExtra("SUBJECT_ID", subject.examSubject.subjectId)
            putExtra("SUBJECT_NAME", subject.examSubject.subjectName)
            putExtra("COURSE_ID", courseId)
            putExtra("MAX_MARKS", subject.examSubject.maxMarks)
            putExtra("HOD_ID", hodId)
            putExtra("DEPARTMENT_ID", departmentId)
        }
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyView.visibility = View.GONE
    }

    private fun showEmptyState(message: String) {
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyText.text = message
    }

    private fun updateEmptyView() {
        if (filteredExams.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyText.text = when (selectedFilter) {
                "pending_moderation" -> "No marks pending moderation"
                "moderated" -> "No moderated subjects yet"
                else -> "No exams available for moderation"
            }
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    // Tracks whether this is the very first resume (which follows onCreate's loadData call).
    // Prevents the double-load visible in logs where every query ran exactly twice.
    private var isFirstResume = true

    override fun onResume() {
        super.onResume()
        if (isFirstResume) {
            isFirstResume = false
            return  // onCreate already called loadData(); skip this one
        }
        // Subsequent resumes = returning from HodMarksModerationActivity.
        // Only re-check moderation status; no need to re-fetch HOD info or subjects.
        if (::adapter.isInitialized && allExams.isNotEmpty()) {
            loadMarksModerationStatus()
        }
    }

    // ─── Data Classes ─────────────────────────────────────────────────────────

    data class SubjectWithModerationStatus(
        val examSubject: ExamSubject,
        var isSubmitted: Boolean = false,
        var isModerated: Boolean = false
    )

    data class ExamWithSubjects(
        val exam: Exam,
        val relevantSubjects: MutableList<SubjectWithModerationStatus>
    )
}