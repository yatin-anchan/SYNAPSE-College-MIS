package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.Subject
import java.text.SimpleDateFormat
import java.util.*

/**
 * SubjectSchedule now carries separate written and internal max-marks so that
 * ExamResultsActivity can display the four-column result table:
 *   Max Marks (Written) | Max Marks (Internal) | Obtained (Written) | Obtained (Internal)
 *
 * How writtenMaxMarks / internalMaxMarks are set from CourseExamConfig:
 *   examType == "Written"   → writtenMaxMarks = maxMarks, internalMaxMarks = 0
 *   examType == "Internal"  → writtenMaxMarks = 0,        internalMaxMarks = maxMarks
 *   examType == "Practical" → writtenMaxMarks = 0,        internalMaxMarks = maxMarks
 *   examType == "Combined"  → each half stored separately; for now we split 70/30 by default.
 *                             The admin can override via the dialog in Step 2.
 */
data class SubjectSchedule(
    val subjectId: String,
    val subjectName: String,
    val subjectCode: String,
    val courseId: String,
    val courseName: String,
    var examDate: Long = 0L,
    var startTime: String = "09:00 AM",
    var endTime: String = "11:30 AM",
    var duration: Float = 2.5f,

    // Legacy / aggregate (kept for backward compat)
    val maxMarks: Int,

    // ── Split max-marks (the key new fields) ────────────────────────────────
    val writtenMaxMarks: Int  = 0,  // 0 if no written component
    val internalMaxMarks: Int = 0,  // 0 if no internal/practical component

    val studentStyle: String,
    val examType: String           // "Written" | "Internal" | "Practical" | "Combined"
)

// ─── Helper: build split marks from CourseExamConfig ─────────────────────────
private fun CourseExamConfig.toWrittenMax(): Int = when (examType) {
    "Internal", "Practical" -> 0
    else -> maxMarks   // "Written" or "Combined" full marks go to written side
}
private fun CourseExamConfig.toInternalMax(): Int = when (examType) {
    "Written" -> 0
    "Internal", "Practical" -> maxMarks
    else -> 0  // "Combined" — leave both sides 0; Step 2 should pass them separately
}

class ExamCreationStep3Activity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var courseTabLayout: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnNext: MaterialButton
    private lateinit var emptyView: View
    private lateinit var loadingProgress: ProgressBar
    private lateinit var globalStartTimeInput: TextInputEditText
    private lateinit var globalDurationInput: TextInputEditText
    private lateinit var btnApplyGlobal: MaterialButton

    private lateinit var firestore: FirebaseFirestore
    private val courseSubjectsMap = mutableMapOf<String, MutableList<SubjectSchedule>>()
    private val usedDatesPerCourse = mutableMapOf<String, MutableSet<Long>>()
    private var currentCourseId = ""
    private lateinit var adapter: SubjectScheduleAdapter

    private var examName = ""
    private var semester = 0
    private var academicYear = ""
    private var startDate = 0L
    private var endDate = 0L
    private var courseConfigs = listOf<CourseExamConfig>()

    private var globalStartTime = "09:00 AM"
    private var globalDuration = 2.5f

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_creation_step3)

        getIntentData()
        initializeViews()
        setupToolbar()
        loadSubjectsForAllCourses()
        setupButtons()
    }

    private fun getIntentData() {
        examName     = intent.getStringExtra("EXAM_NAME")     ?: ""
        semester     = intent.getIntExtra("SEMESTER", 0)
        academicYear = intent.getStringExtra("ACADEMIC_YEAR") ?: ""
        startDate    = intent.getLongExtra("START_DATE", 0L)
        endDate      = intent.getLongExtra("END_DATE", 0L)

        val coursesJson = intent.getStringExtra("COURSES_CONFIG") ?: ""
        courseConfigs = com.google.gson.Gson().fromJson(
            coursesJson, Array<CourseExamConfig>::class.java
        ).toList()
    }

    private fun initializeViews() {
        toolbar              = findViewById(R.id.toolbar)
        courseTabLayout      = findViewById(R.id.course_tab_layout)
        recyclerView         = findViewById(R.id.recycler_view)
        btnNext              = findViewById(R.id.btn_next)
        emptyView            = findViewById(R.id.empty_view)
        loadingProgress      = findViewById(R.id.loading_progress)
        globalStartTimeInput = findViewById(R.id.global_start_time_input)
        globalDurationInput  = findViewById(R.id.global_duration_input)
        btnApplyGlobal       = findViewById(R.id.btn_apply_global)
        firestore            = FirebaseFirestore.getInstance()

        globalStartTimeInput.setText(globalStartTime)
        globalDurationInput.setText(globalDuration.toString())
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title    = "Create Exam - Step 3 of 4"
        supportActionBar?.subtitle = "Schedule Subjects"
        toolbar.setNavigationOnClickListener { finish() }
    }

    // ─── Load subjects ─────────────────────────────────────────────────────────

    private fun loadSubjectsForAllCourses() {
        showLoading(true)

        courseConfigs.forEach { config ->
            usedDatesPerCourse[config.courseId] = mutableSetOf()

            firestore.collection("subjects")
                .whereEqualTo("courseId",      config.courseId)
                .whereEqualTo("semesterNumber", semester)
                .whereEqualTo("isActive",      true)
                .get()
                .addOnSuccessListener { documents ->
                    val subjects = documents.mapNotNull { doc ->
                        val subject = doc.toObject(Subject::class.java)
                        subject.id = doc.id

// ── FILTER SUBJECTS BASED ON EXAM TYPE ──────────────────────────────
                        val examType = config.examType.trim().lowercase()
                        val subjectType = subject.type.trim().lowercase()

                        val allowed = when (examType) {
                            "practical" ->
                                subjectType == "practical" || subjectType.contains("practical")

                            "written" ->
                                subjectType == "theory" || subjectType.contains("theory")

                            "internal" ->
                                subjectType == "theory" ||
                                        subjectType == "practical" ||
                                        subjectType.contains("practical")

                            "combined" ->
                                subjectType.contains("theory") && subjectType.contains("practical")

                            else -> true
                        }

                        if (!allowed) return@mapNotNull null
// ───────────────────────────────────────────────────────────────────

                        SubjectSchedule(
                            subjectId        = subject.id,
                            subjectName      = subject.name,
                            subjectCode      = subject.code,
                            courseId         = config.courseId,
                            courseName       = config.courseName,
                            startTime        = globalStartTime,
                            endTime          = calculateEndTime(globalStartTime, globalDuration),
                            duration         = globalDuration,
                            maxMarks         = config.maxMarks,
                            writtenMaxMarks  = config.toWrittenMax(),
                            internalMaxMarks = config.toInternalMax(),
                            studentStyle     = config.studentStyle,
                            examType         = config.examType
                        )
                    }.sortedBy { it.subjectCode }.toMutableList()

                    courseSubjectsMap[config.courseId] = subjects

                    if (courseSubjectsMap.size == courseConfigs.size) {
                        showLoading(false)
                        setupCourseTabs()
                        setupGlobalControls()
                        if (courseConfigs.isNotEmpty()) {
                            selectCourse(courseConfigs[0].courseId)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ─── Global time controls ─────────────────────────────────────────────────

    private fun setupGlobalControls() {
        globalStartTimeInput.setOnClickListener {
            showTimePicker(globalStartTimeInput.text.toString()) { time ->
                globalStartTime = time
                globalStartTimeInput.setText(time)
            }
        }
        btnApplyGlobal.setOnClickListener { applyGlobalSettings() }
    }

    private fun applyGlobalSettings() {
        val startTime = globalStartTimeInput.text.toString()
        val duration  = globalDurationInput.text.toString().toFloatOrNull()

        if (duration == null || duration <= 0) {
            Toast.makeText(this, "Please enter valid duration", Toast.LENGTH_SHORT).show()
            return
        }

        globalStartTime = startTime
        globalDuration  = duration
        val endTime     = calculateEndTime(startTime, duration)

        AlertDialog.Builder(this, R.style.CustomAlertDialog_Schedule_Exam)
            .setTitle("Apply Global Settings")
            .setMessage("Apply $startTime start and $duration hour(s) duration to ALL unscheduled subjects?")
            .setPositiveButton("Apply") { _, _ ->
                courseSubjectsMap.values.forEach { schedules ->
                    schedules.forEach { s ->
                        if (s.examDate == 0L) {
                            val idx = schedules.indexOf(s)
                            schedules[idx] = s.copy(startTime = startTime, endTime = endTime, duration = duration)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
                Toast.makeText(this, "Global settings applied", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Course tabs ──────────────────────────────────────────────────────────

    private fun setupCourseTabs() {
        courseTabLayout.removeAllViews()
        courseSubjectsMap.keys.forEach { courseId ->
            val courseName = courseConfigs.find { it.courseId == courseId }?.courseName ?: ""
            val btn = MaterialButton(this).apply {
                text = courseName
                setOnClickListener { selectCourse(courseId) }
            }
            courseTabLayout.addView(btn)
        }
    }

    private fun selectCourse(courseId: String) {
        currentCourseId = courseId
        val schedules = courseSubjectsMap[courseId] ?: mutableListOf()
        adapter = SubjectScheduleAdapter(schedules, startDate, endDate) { schedule ->
            showScheduleDialog(schedule, courseId)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        updateUI()
    }

    // ─── Schedule dialog ──────────────────────────────────────────────────────

    private fun showScheduleDialog(schedule: SubjectSchedule, courseId: String) {
        val dialogView  = LayoutInflater.from(this).inflate(R.layout.dialog_schedule_subject, null)
        // FIX: id is selected_date_text — matches the fixed dialog_schedule_subject.xml
        val dateText    = dialogView.findViewById<TextView>(R.id.selected_date_text)
        val startInput  = dialogView.findViewById<TextInputEditText>(R.id.start_time_input)
        val durationIn  = dialogView.findViewById<TextInputEditText>(R.id.duration_input)
        val endText     = dialogView.findViewById<TextView>(R.id.end_time_text)
        val selectDate  = dialogView.findViewById<MaterialButton>(R.id.btn_select_date)

        var selectedDate = schedule.examDate
        var currentStart = schedule.startTime
        var currentDur   = schedule.duration

        fun refreshEndTime() {
            val end = calculateEndTime(currentStart, currentDur)
            endText.text = "End Time: $end"
        }

        dateText.text  = if (selectedDate > 0) dateFormat.format(Date(selectedDate)) else "Not Selected"
        startInput.setText(currentStart)
        durationIn.setText(currentDur.toString())
        refreshEndTime()

        startInput.setOnClickListener {
            showTimePicker(currentStart) { t -> currentStart = t; startInput.setText(t); refreshEndTime() }
        }
        durationIn.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                currentDur = s.toString().toFloatOrNull() ?: currentDur; refreshEndTime()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        selectDate.setOnClickListener {
            showDatePicker(schedule.examDate, courseId) { d ->
                selectedDate = d
                dateText.text = dateFormat.format(Date(d))
            }
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Schedule: ${schedule.subjectName}")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                if (selectedDate == 0L) {
                    Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val endTime = calculateEndTime(currentStart, currentDur)
                if (!validateTime(currentStart, endTime, selectedDate)) {
                    Toast.makeText(this, "End time must be after start time", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val subjects = courseSubjectsMap[courseId] ?: return@setPositiveButton
                val idx = subjects.indexOfFirst { it.subjectId == schedule.subjectId }
                if (idx != -1) {
                    // Update date tracking
                    val old = subjects[idx]
                    if (old.examDate > 0) usedDatesPerCourse[courseId]?.remove(old.examDate)
                    usedDatesPerCourse[courseId]?.add(selectedDate)

                    subjects[idx] = old.copy(
                        examDate  = selectedDate,
                        startTime = currentStart,
                        endTime   = endTime,
                        duration  = currentDur
                    )
                    adapter.notifyItemChanged(idx)
                    updateUI()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── Validation / Navigation ──────────────────────────────────────────────

    private fun setupButtons() {
        btnNext.setOnClickListener {
            if (validateSchedules()) proceedToStep4()
        }
    }

    /**
     * FIX: Added missing "return true" at the end.
     * The for-loop could exit normally without returning anything,
     * which Kotlin treats as a compile error ("A 'return' expression required in a function
     * with a block body ('{...}')").
     */
    private fun validateSchedules(): Boolean {
        for ((courseId, subjects) in courseSubjectsMap) {
            val unscheduled = subjects.filter { it.examDate == 0L }
            if (unscheduled.isNotEmpty()) {
                val name = courseConfigs.find { it.courseId == courseId }?.courseName ?: ""
                Toast.makeText(this, "${unscheduled.size} subject(s) not scheduled in $name", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true  // ← FIX: this line was missing
    }

    private fun proceedToStep4() {
        val intent = Intent(this, ExamCreationStep4Activity::class.java).apply {
            putExtra("EXAM_NAME",     examName)
            putExtra("SEMESTER",      semester)
            putExtra("ACADEMIC_YEAR", academicYear)
            putExtra("START_DATE",    startDate)
            putExtra("END_DATE",      endDate)
            val all = courseSubjectsMap.values.flatten()
            putExtra("SCHEDULES", com.google.gson.Gson().toJson(all))
        }
        startActivity(intent)
    }

    // ─── Date / time pickers ──────────────────────────────────────────────────

    private fun showDatePicker(currentDate: Long, courseId: String, callback: (Long) -> Unit) {
        val calendar  = Calendar.getInstance()
        calendar.timeInMillis = if (currentDate > 0) currentDate else startDate
        val usedDates = usedDatesPerCourse[courseId] ?: mutableSetOf()

        DatePickerDialog(
            this,
            { _, year, month, day ->
                val cal = Calendar.getInstance()
                cal.set(year, month, day, 0, 0, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val selected = cal.timeInMillis

                if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    Toast.makeText(this, "⚠️ Sundays are not allowed", Toast.LENGTH_LONG).show()
                    return@DatePickerDialog
                }
                if (selected != currentDate && usedDates.contains(selected)) {
                    Toast.makeText(this, "⚠️ Date already used for another subject", Toast.LENGTH_LONG).show()
                    return@DatePickerDialog
                }
                callback(selected)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).also {
            it.datePicker.minDate = startDate
            it.datePicker.maxDate = endDate
            it.show()
        }
    }

    private fun showTimePicker(currentTime: String, callback: (String) -> Unit) {
        val cal = Calendar.getInstance()
        try { cal.time = timeFormat.parse(currentTime) ?: cal.time } catch (_: Exception) {}
        TimePickerDialog(this, { _, hour, minute ->
            val amPm  = if (hour >= 12) "PM" else "AM"
            val hour12 = when { hour > 12 -> hour - 12; hour == 0 -> 12; else -> hour }
            callback(String.format("%02d:%02d %s", hour12, minute, amPm))
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    private fun calculateEndTime(startTime: String, durationHours: Float): String {
        return try {
            val cal = Calendar.getInstance()
            cal.time = timeFormat.parse(startTime) ?: return startTime
            cal.add(Calendar.MINUTE, (durationHours * 60).toInt())
            timeFormat.format(cal.time)
        } catch (e: Exception) { startTime }
    }

    // FIX: toMillis moved outside the try block.
    // Kotlin's flow analysis cannot verify that a local function defined
    // INSIDE a try{} always returns, so it reported "Missing return statement"
    // on the outer validateTime even though the code was logically complete.
    // Moving toMillis to a private fun at the same level as validateTime
    // gives the compiler full visibility of its return type and eliminates
    // the false-positive error entirely.
    private fun toMillis(time: String, examDate: Long): Long {
        return try {
            val cal = Calendar.getInstance()
            cal.timeInMillis = examDate
            val t = timeFormat.parse(time) ?: return 0L
            val tmp = Calendar.getInstance().also { it.time = t }
            cal.set(Calendar.HOUR_OF_DAY, tmp.get(Calendar.HOUR_OF_DAY))
            cal.set(Calendar.MINUTE, tmp.get(Calendar.MINUTE))
            cal.timeInMillis
        } catch (e: Exception) { 0L }
    }

    private fun validateTime(startTime: String, endTime: String, examDate: Long): Boolean {
        return try {
            toMillis(endTime, examDate) > toMillis(startTime, examDate)
        } catch (e: Exception) { false }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun updateUI() {
        val subjects     = courseSubjectsMap[currentCourseId] ?: emptyList()
        emptyView.visibility = if (subjects.isEmpty()) View.VISIBLE else View.GONE
        val hasAny = courseSubjectsMap.values.any { it.isNotEmpty() }
        btnNext.visibility = if (hasAny) View.VISIBLE else View.GONE
    }
}

// ─── Adapter ──────────────────────────────────────────────────────────────────

class SubjectScheduleAdapter(
    private val schedules: MutableList<SubjectSchedule>,
    private val startDate: Long,
    private val endDate: Long,
    private val onScheduleClick: (SubjectSchedule) -> Unit
) : RecyclerView.Adapter<SubjectScheduleAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val subjectName: TextView    = view.findViewById(R.id.subject_name)
        val subjectCode: TextView    = view.findViewById(R.id.subject_code)
        val scheduleDetails: TextView = view.findViewById(R.id.schedule_details)
        val btnSchedule: MaterialButton = view.findViewById(R.id.btn_schedule)
        val statusIcon: ImageView    = view.findViewById(R.id.status_icon)
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_schedule, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = schedules[position]
        holder.subjectName.text = s.subjectName

        // Show exam-type and marks breakdown in the sub-title
        val marksLabel = buildString {
            if (s.writtenMaxMarks  > 0) append("Written: ${s.writtenMaxMarks}")
            if (s.writtenMaxMarks  > 0 && s.internalMaxMarks > 0) append("  |  ")
            if (s.internalMaxMarks > 0) append("Internal: ${s.internalMaxMarks}")
            append("  •  Total: ${s.maxMarks}")
        }
        holder.subjectCode.text = "${s.subjectCode}  •  ${s.examType}  •  $marksLabel"

        if (s.examDate > 0) {
            holder.scheduleDetails.text = "${dateFormat.format(Date(s.examDate))}\n${s.startTime} - ${s.endTime}"
            holder.scheduleDetails.visibility = View.VISIBLE
            holder.statusIcon.visibility      = View.VISIBLE
            holder.btnSchedule.text           = "Reschedule"
        } else {
            holder.scheduleDetails.visibility = View.GONE
            holder.statusIcon.visibility      = View.GONE
            holder.btnSchedule.text           = "Schedule"
        }

        holder.btnSchedule.setOnClickListener { onScheduleClick(s) }
    }

    override fun getItemCount() = schedules.size
}