package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import java.text.SimpleDateFormat
import java.util.*

class ExamCreationStep4Activity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examSummaryText: TextView
    private lateinit var courseTabLayout: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnConfirm: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private var examName = ""
    private var semester = 0
    private var academicYear = ""
    private var startDate = 0L
    private var endDate = 0L
    private var allSchedules = listOf<SubjectSchedule>()

    private val courseSchedulesMap = mutableMapOf<String, List<SubjectSchedule>>()
    private var currentCourseId = ""
    private lateinit var adapter: TimetablePreviewAdapter

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_creation_step4)

        getIntentData()
        initializeViews()
        setupToolbar()
        setupExamSummary()
        organizeSchedulesByCourse()
        setupCourseTabs()
        setupButtons()
    }

    private fun getIntentData() {
        examName     = intent.getStringExtra("EXAM_NAME")     ?: ""
        semester     = intent.getIntExtra("SEMESTER", 0)
        academicYear = intent.getStringExtra("ACADEMIC_YEAR") ?: ""
        startDate    = intent.getLongExtra("START_DATE", 0L)
        endDate      = intent.getLongExtra("END_DATE",   0L)

        val schedulesJson = intent.getStringExtra("SCHEDULES") ?: ""
        allSchedules = com.google.gson.Gson().fromJson(
            schedulesJson, Array<SubjectSchedule>::class.java
        ).toList()
    }

    private fun initializeViews() {
        toolbar          = findViewById(R.id.toolbar)
        examSummaryText  = findViewById(R.id.exam_summary_text)
        courseTabLayout  = findViewById(R.id.course_tab_layout)
        recyclerView     = findViewById(R.id.recycler_view)
        btnConfirm       = findViewById(R.id.btn_confirm)
        loadingProgress  = findViewById(R.id.loading_progress)
        firestore        = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title    = "Create Exam - Step 4 of 4"
        supportActionBar?.subtitle = "Review & Confirm"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupExamSummary() {
        val totalSubjects = allSchedules.size
        val totalCourses  = allSchedules.map { it.courseId }.distinct().size

        examSummaryText.text = """
            Exam: $examName
            Semester: $semester
            Academic Year: $academicYear
            Period: ${dateFormat.format(Date(startDate))} to ${dateFormat.format(Date(endDate))}
            
            Total Courses: $totalCourses
            Total Subjects: $totalSubjects
        """.trimIndent()
    }

    private fun organizeSchedulesByCourse() {
        courseSchedulesMap.clear()
        allSchedules.groupBy { it.courseId }.forEach { (courseId, schedules) ->
            val sorted = schedules.sortedWith(compareBy({ it.examDate }, { parseTime(it.startTime) }))
            courseSchedulesMap[courseId] = sorted
        }
    }

    private fun parseTime(timeStr: String): Long {
        return try {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).parse(timeStr)?.time ?: 0L
        } catch (e: Exception) { 0L }
    }

    private fun setupCourseTabs() {
        courseTabLayout.removeAllViews()
        courseSchedulesMap.keys.forEach { courseId ->
            val courseName = allSchedules.find { it.courseId == courseId }?.courseName ?: ""
            val btn = MaterialButton(this).apply {
                text = courseName
                setOnClickListener { selectCourse(courseId) }
            }
            courseTabLayout.addView(btn)
        }
        if (courseSchedulesMap.isNotEmpty()) selectCourse(courseSchedulesMap.keys.first())
    }

    private fun selectCourse(courseId: String) {
        currentCourseId = courseId
        val schedules = courseSchedulesMap[courseId] ?: emptyList()
        adapter = TimetablePreviewAdapter(schedules)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        btnConfirm.setOnClickListener { showConfirmationDialog() }
    }

    private fun showConfirmationDialog() {
        val totalSubjects = allSchedules.size
        val totalCourses  = courseSchedulesMap.size

        AlertDialog.Builder(this, R.style.CustomAlertDialog_Schedule_Exam)
            .setTitle("Confirm Exam Creation")
            .setMessage(
                """
                Are you sure you want to create this exam?
                
                • $totalCourses course(s)
                • $totalSubjects subject(s)
                • Period: ${dateFormat.format(Date(startDate))} to ${dateFormat.format(Date(endDate))}
                
                This will generate timetables for all courses.
                """.trimIndent()
            )
            .setPositiveButton("Confirm & Create") { _, _ -> createExam() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createExam() {
        showLoading(true)

        // Get examType from first schedule (available from allSchedules)
        val examType = allSchedules.firstOrNull()?.examType?.takeIf { it in listOf("Written", "internal") }
            ?: allSchedules.mapNotNull { it.examType }.distinct().firstOrNull()
            ?: "Written"

        // Convert SubjectSchedule → ExamSubject (now preserving split max-marks + examType)
        val examSubjects = allSchedules.map { schedule ->
            ExamSubject(
                subjectId        = schedule.subjectId,
                subjectName      = schedule.subjectName,
                subjectCode      = schedule.subjectCode,
                courseId         = schedule.courseId,
                courseName       = schedule.courseName,
                examDate         = schedule.examDate,
                startTime        = schedule.startTime,
                endTime          = schedule.endTime,
                venue            = "",
                examType         = schedule.examType,
                maxMarks         = schedule.maxMarks,
                writtenMaxMarks  = schedule.writtenMaxMarks,
                internalMaxMarks = schedule.internalMaxMarks,
                duration         = schedule.duration
            )
        }

        val courseIds = allSchedules.map { it.courseId }.distinct()

        val exam = Exam(
            examName          = examName,
            semester          = semester,
            academicYear      = academicYear,
            examType          = examType,  // Fixed: Now properly assigned
            startDate         = startDate,
            endDate           = endDate,
            courses           = courseIds,
            subjects          = examSubjects,
            status            = "confirmed",
            isConfirmed       = true,
            timetableGenerated = true,
            createdBy         = "Admin",
            confirmedAt       = System.currentTimeMillis()
        )
        firestore.collection("exams")
            .add(exam)
            .addOnSuccessListener { ref ->
                showLoading(false)
                AlertDialog.Builder(this, R.style.CustomAlertDialog)
                    .setTitle("Success!")
                    .setMessage("Exam created successfully with timetables generated for all courses.")
                    .setPositiveButton("View Details") { _, _ ->
                        startActivity(
                            Intent(this, ExamDetailsActivity::class.java)
                                .putExtra("EXAM_ID", ref.id)
                        )
                        finish()
                    }
                    .setNegativeButton("Go to Exams List") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                AlertDialog.Builder(this, R.style.CustomAlertDialog)
                    .setTitle("Error")
                    .setMessage("Failed to create exam: ${e.message}")
                    .setPositiveButton("OK", null)
                    .show()
                Log.e("ExamCreation", "Error creating exam", e)
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnConfirm.isEnabled       = !show
    }
}

// ─── Preview Adapter ──────────────────────────────────────────────────────────

class TimetablePreviewAdapter(
    private val schedules: List<SubjectSchedule>
) : RecyclerView.Adapter<TimetablePreviewAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val dayFormat  = SimpleDateFormat("EEEE", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView    = view.findViewById(R.id.date_text)
        val dayText: TextView     = view.findViewById(R.id.day_text)
        val subjectName: TextView = view.findViewById(R.id.subject_name)
        val subjectCode: TextView = view.findViewById(R.id.subject_code)
        val timeText: TextView    = view.findViewById(R.id.time_text)
        val detailsText: TextView = view.findViewById(R.id.details_text)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable_preview, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = schedules[position]
        val date = Date(s.examDate)

        holder.dateText.text    = dateFormat.format(date)
        holder.dayText.text     = dayFormat.format(date)
        holder.subjectName.text = s.subjectName
        holder.subjectCode.text = s.subjectCode
        holder.timeText.text    = "${s.startTime} - ${s.endTime}"

        // Show the marks breakdown prominently in the preview row
        val marksDetail = buildString {
            append(s.examType).append("  •  ")
            if (s.writtenMaxMarks  > 0) append("Written: ${s.writtenMaxMarks}  ")
            if (s.internalMaxMarks > 0) append("Internal: ${s.internalMaxMarks}  ")
            append("Total: ${s.maxMarks}  •  ${s.duration} hrs")
        }
        holder.detailsText.text = marksDetail
    }

    override fun getItemCount() = schedules.size
}