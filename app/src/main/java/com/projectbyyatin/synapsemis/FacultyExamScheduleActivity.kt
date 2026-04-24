package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.FacultyExamScheduleAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import java.text.SimpleDateFormat
import java.util.*

class FacultyExamScheduleActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipUpcoming: Chip
    private lateinit var chipOngoing: Chip
    private lateinit var chipCompleted: Chip
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var statsText: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: FacultyExamScheduleAdapter

    private var facultyId = ""
    private var facultyName = ""
    private var departmentId = ""

    private val allExams = mutableListOf<Exam>()
    private val filteredExams = mutableListOf<Exam>()

    private var selectedFilter = "all"

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_exam_schedule)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        facultyName = intent.getStringExtra("FACULTY_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupChips()
        setupRecyclerView()
        loadExamSchedule()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        chipGroup = findViewById(R.id.chip_group)
        chipAll = findViewById(R.id.chip_all)
        chipUpcoming = findViewById(R.id.chip_upcoming)
        chipOngoing = findViewById(R.id.chip_ongoing)
        chipCompleted = findViewById(R.id.chip_completed)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)
        emptyText = findViewById(R.id.empty_text)
        statsText = findViewById(R.id.stats_text)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Exam Schedule"
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupChips() {
        chipAll.setOnClickListener {
            selectedFilter = "all"
            applyFilter()
        }

        chipUpcoming.setOnClickListener {
            selectedFilter = "upcoming"
            applyFilter()
        }

        chipOngoing.setOnClickListener {
            selectedFilter = "ongoing"
            applyFilter()
        }

        chipCompleted.setOnClickListener {
            selectedFilter = "completed"
            applyFilter()
        }

        chipAll.isChecked = true
    }

    private fun setupRecyclerView() {
        adapter = FacultyExamScheduleAdapter(
            filteredExams,
            facultyId, // ✅ REQUIRED PARAM
            onExamClick = { exam -> showExamDetails(exam) },
            onEnterMarks = { exam, subject -> openMarksEntry(exam, subject) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }


    private fun loadExamSchedule() {
        showLoading(true)

        firestore.collection("exams")
            .orderBy("startDate", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                allExams.clear()

                docs.forEach { doc ->
                    val exam = doc.toObject(Exam::class.java).apply { id = doc.id }

                    val hasFacultySubjects = exam.subjects.any {
                        it.assignedFacultyId == facultyId
                    }

                    if (hasFacultySubjects) {
                        allExams.add(exam)
                    }
                }

                applyFilter()
                updateStats()
                showLoading(false)
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to load exams", Toast.LENGTH_SHORT).show()
            }
    }

    private fun applyFilter() {
        filteredExams.clear()
        val now = System.currentTimeMillis()

        when (selectedFilter) {
            "all" -> filteredExams.addAll(allExams)
            "upcoming" -> filteredExams.addAll(allExams.filter { it.startDate > now })
            "ongoing" -> filteredExams.addAll(allExams.filter {
                it.startDate <= now && it.endDate >= now
            })
            "completed" -> filteredExams.addAll(allExams.filter { it.endDate < now })
        }

        adapter.updateList(filteredExams)
        updateEmptyView()
    }

    private fun updateStats() {
        val now = System.currentTimeMillis()
        val total = allExams.size
        val upcoming = allExams.count { it.startDate > now }
        val ongoing = allExams.count { it.startDate <= now && it.endDate >= now }
        val completed = allExams.count { it.endDate < now }

        statsText.text =
            "Total: $total | Upcoming: $upcoming | Ongoing: $ongoing | Completed: $completed"
    }

    private fun updateEmptyView() {
        if (filteredExams.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            emptyText.text = when (selectedFilter) {
                "upcoming" -> "No upcoming exams"
                "ongoing" -> "No ongoing exams"
                "completed" -> "No completed exams"
                else -> "No exam schedules found"
            }
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun showExamDetails(exam: Exam) {
        val message = buildString {
            append("Exam: ${exam.examName}\n")
            append("Duration: ${dateFormat.format(exam.startDate)} - ${dateFormat.format(exam.endDate)}\n\n")
            append("Your Subjects:\n")

            exam.subjects.filter { it.assignedFacultyId == facultyId }.forEach {
                append("• ${it.subjectName} (${it.subjectCode})\n")
                append("  Time: ${it.startTime} - ${it.endTime}\n")
                append("  Max Marks: ${it.maxMarks}\n\n")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("Exam Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun openMarksEntry(exam: Exam, subject: ExamSubject) {
        if (!exam.marksEntryEnabled) {
            Toast.makeText(this, "Marks entry not enabled yet", Toast.LENGTH_SHORT).show()
            return
        }

        if (exam.marksPublished) {
            Toast.makeText(this, "Marks already published", Toast.LENGTH_SHORT).show()
            return
        }

        startActivity(
            Intent(this, FacultyMarksEntryActivity::class.java).apply {
                putExtra("EXAM_ID", exam.id)
                putExtra("SUBJECT_ID", subject.subjectId)
                putExtra("SUBJECT_NAME", subject.subjectName)
                putExtra("COURSE_ID", subject.courseId)
                putExtra("MAX_MARKS", subject.maxMarks)
            }
        )
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadExamSchedule()
    }
}
