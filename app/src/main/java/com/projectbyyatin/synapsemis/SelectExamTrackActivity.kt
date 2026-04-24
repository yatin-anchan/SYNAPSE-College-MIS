package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.ExamTrackerAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

class SelectExamTrackActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var firestore: FirebaseFirestore

    private lateinit var semesterDropdown: AutoCompleteTextView
    private lateinit var courseDropdown: AutoCompleteTextView
    private lateinit var examsRecycler: RecyclerView
    private lateinit var emptyView: View
    private lateinit var loadingProgress: ProgressBar
    private lateinit var adapter: ExamTrackerAdapter

    // Full list loaded from Firestore — never mutated after load
    private var allExamsList = mutableListOf<Exam>()

    // Courses for the dropdown
    private var coursesList = mutableListOf<Pair<String, String>>()

    private var selectedSemester: Int? = null
    private var selectedCourseId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_exam_track)

        firestore = FirebaseFirestore.getInstance()
        initViews()
        setupToolbar()
        setupSemesterDropdown()
        setupCourseDropdown()
        setupRecyclerView()
        loadAllExams()
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        semesterDropdown = findViewById(R.id.semester_dropdown)
        courseDropdown = findViewById(R.id.course_dropdown)
        examsRecycler = findViewById(R.id.exams_recycler)
        emptyView = findViewById(R.id.empty_view)
        loadingProgress = findViewById(R.id.loading_progress)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "📊 Track Marks Progress"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSemesterDropdown() {
        val items = listOf("All Semesters") + (1..8).map { "Semester $it" }
        semesterDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, items)
        )
        semesterDropdown.setText("All Semesters", false)

        semesterDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = if (position == 0) null else position
            resetCourseDropdown()
            filterAndDisplay()
        }
    }

    private fun setupCourseDropdown() {
        courseDropdown.setOnItemClickListener { _, _, position, _ ->
            selectedCourseId = if (position < coursesList.size && coursesList[position].first.isNotEmpty()) {
                coursesList[position].first
            } else {
                null
            }
            filterAndDisplay()
        }
    }

    private fun setupRecyclerView() {
        adapter = ExamTrackerAdapter(mutableListOf()) { exam -> trackExamMarks(exam) }
        examsRecycler.layoutManager = LinearLayoutManager(this)
        examsRecycler.adapter = adapter
    }

    private fun trackExamMarks(exam: Exam) {
        val intent = Intent(this, ExamMarksTrackerActivity::class.java).apply {
            selectedSemester?.let { putExtra("FILTER_SEMESTER", it) }
            selectedCourseId?.let { putExtra("FILTER_COURSE_ID", it) }
            putExtra("FILTER_EXAM_ID", exam.id)
        }
        startActivity(intent)
    }

    // ─── Load ALL exams from Firestore ────────────────────────────────────────
    private fun loadAllExams() {
        Log.d("SelectExamTrack", "🔥 LOADING ALL EXAMS...")
        showLoading(true)

        firestore.collection("exams")
            .orderBy("startDate", Query.Direction.DESCENDING) // ✅ Same as ManageExamsActivity
            .get()
            .addOnSuccessListener { documents ->
                Log.d("SelectExamTrack", "📊 Found ${documents.size()} total exams")
                allExamsList.clear()

                documents.forEach { document ->
                    try {
                        val examData = document.data
                        if (examData != null) {
                            @Suppress("UNCHECKED_CAST")
                            val parsedCourses = (examData["courses"] as? List<*>)
                                ?.filterIsInstance<String>() ?: emptyList()

                            val exam = Exam(
                                courses = parsedCourses  // ✅ set via constructor since it's a val
                            ).apply {
                                id = document.id
                                examName = examData["examName"]?.toString() ?: ""
                                semester = (examData["semester"] as? Number)?.toInt() ?: 1
                                academicYear = examData["academicYear"]?.toString() ?: ""
                                startDate = safeTimestampToLong(examData["startDate"])
                                endDate = safeTimestampToLong(examData["endDate"])
                                createdAt = safeTimestampToLong(examData["createdAt"])
                                confirmedAt = safeTimestampToLong(examData["confirmedAt"])
                                marksPublishedAt = safeTimestampToLong(examData["marksPublishedAt"])
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
                                val subjectsStatus = examData["subjectsMarksStatus"] as? Map<*, *>
                                if (subjectsStatus != null) {
                                    subjectsMarksStatus = parseSubjectsMarksStatus(subjectsStatus)
                                }
                            }
                            allExamsList.add(exam)
                        }
                    } catch (e: Exception) {
                        Log.e("SelectExamTrack", "Parse error ${document.id}: ${e.message}")
                    }
                }

                Log.d("SelectExamTrack", "🎉 Loaded ${allExamsList.size} exams")
                loadCourses()           // Load courses after exams are ready
                showLoading(false)      // ✅ Hide loading BEFORE updating views
                filterAndDisplay()      // ✅ Apply any active filters and refresh list
            }
            .addOnFailureListener { e ->
                Log.e("SelectExamTrack", "FAILED to load exams: ${e.message}")
                showLoading(false)
                updateEmptyView()
                Toast.makeText(this, "Failed to load exams: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ─── Client-side filter on already-loaded allExamsList ────────────────────
    private fun filterAndDisplay() {
        val filtered = allExamsList.filter { exam ->
            (selectedSemester == null || exam.semester == selectedSemester) &&
                    (selectedCourseId == null ||
                            exam.courseId == selectedCourseId ||           // legacy single courseId
                            exam.courses.contains(selectedCourseId))       // ✅ check courses array
        }

        adapter.updateList(filtered.toMutableList())
        updateEmptyView()
    }

    // ─── Load courses for dropdown ────────────────────────────────────────────
    private fun loadCourses() {
        firestore.collection("courses")
            .get()
            .addOnSuccessListener { docs ->
                coursesList.clear()
                coursesList.add(Pair("", "All Courses"))
                docs.forEach { doc ->
                    val name = doc.getString("name") ?: doc.getString("courseName") ?: return@forEach
                    coursesList.add(Pair(doc.id, name))
                }
                refreshCourseAdapter()
                Log.d("SelectExamTrack", "✅ Loaded ${coursesList.size - 1} courses")
            }
            .addOnFailureListener {
                coursesList.clear()
                coursesList.add(Pair("", "All Courses"))
                refreshCourseAdapter()
            }
    }

    private fun refreshCourseAdapter() {
        val names = coursesList.map { it.second }
        courseDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, names)
        )
        courseDropdown.setText("All Courses", false)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun safeTimestampToLong(value: Any?): Long {
        return when (value) {
            is Timestamp -> value.toDate().time
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
                    result[subjectId.toString()] = SubjectMarksStatus().apply {
                        this.subjectId = subjectId.toString()
                        totalStudents = (statusMap["totalStudents"] as? Number)?.toInt() ?: 0
                        marksEntered = (statusMap["marksEntered"] as? Number)?.toInt() ?: 0
                        marksSubmitted = statusMap["marksSubmitted"] as? Boolean ?: false
                        submittedBy = statusMap["submittedBy"]?.toString() ?: ""
                        submittedAt = safeTimestampToLong(statusMap["submittedAt"])
                        moderated = statusMap["moderated"] as? Boolean ?: false
                        moderatedBy = statusMap["moderatedBy"]?.toString() ?: ""
                        moderatedAt = safeTimestampToLong(statusMap["moderatedAt"])
                        readyForPublish = statusMap["readyForPublish"] as? Boolean ?: false
                    }
                }
            } catch (e: Exception) {
                Log.e("SelectExamTrack", "Subject parse error for $subjectId: ${e.message}")
            }
        }
        return result
    }

    private fun resetCourseDropdown() {
        selectedCourseId = null
        courseDropdown.setText("All Courses", false)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        examsRecycler.visibility = if (show) View.GONE else View.VISIBLE
        emptyView.visibility = View.GONE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadAllExams()
    }
}