package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.ExamSubjectAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.Subject
import java.text.SimpleDateFormat
import java.util.*

class CreateExamActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examNameInput: TextInputEditText
    private lateinit var semesterDropdown: AutoCompleteTextView
    private lateinit var courseDropdown: AutoCompleteTextView
    private lateinit var academicYearInput: TextInputEditText
    private lateinit var startDateText: TextView
    private lateinit var endDateText: TextView
    private lateinit var btnSelectStartDate: MaterialButton
    private lateinit var btnSelectEndDate: MaterialButton
    private lateinit var defaultDurationInput: TextInputEditText
    private lateinit var defaultStartTimeInput: TextInputEditText
    private lateinit var btnApplyToAll: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var loadingSubjectsText: TextView
    private lateinit var btnConfirmExam: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: ExamSubjectAdapter
    private var subjectsList = mutableListOf<ExamSubject>()
    private val courseMap = mutableMapOf<String, String>() // name -> id

    private var selectedSemester: Int = 1
    private var selectedCourseId: String = ""
    private var selectedCourseName: String = ""
    private var startDate: Long = 0L
    private var endDate: Long = 0L
    private val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_exam)

        initializeViews()
        setupToolbar()
        setupSemesterDropdown()
        setupCourseDropdown()
        setupRecyclerView()
        setupButtons()
        setDefaultValues()
        updateUI()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examNameInput = findViewById(R.id.exam_name_input)
        semesterDropdown = findViewById(R.id.semester_dropdown)
        courseDropdown = findViewById(R.id.course_dropdown)
        academicYearInput = findViewById(R.id.academic_year_input)
        startDateText = findViewById(R.id.start_date_text)
        endDateText = findViewById(R.id.end_date_text)
        btnSelectStartDate = findViewById(R.id.btn_select_start_date)
        btnSelectEndDate = findViewById(R.id.btn_select_end_date)
        defaultDurationInput = findViewById(R.id.default_duration_input)
        defaultStartTimeInput = findViewById(R.id.default_start_time_input)
        btnApplyToAll = findViewById(R.id.btn_apply_to_all)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)
        loadingSubjectsText = findViewById(R.id.loading_subjects_text)
        btnConfirmExam = findViewById(R.id.btn_confirm_exam)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()

        // Set default values
        defaultDurationInput.setText("3")
        defaultStartTimeInput.setText("10:00 AM")
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Create Exam"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setDefaultValues() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        val academicYear = if (currentMonth >= 6) {
            "$currentYear-${currentYear + 1}"
        } else {
            "${currentYear - 1}-$currentYear"
        }

        academicYearInput.setText(academicYear)
    }

    private fun setupSemesterDropdown() {
        val semesters = (1..8).map { "Semester $it" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesters)
        semesterDropdown.setAdapter(adapter)

        semesterDropdown.setOnItemClickListener { _, _, position, _ ->
            if (subjectsList.isNotEmpty()) {
                showSemesterChangeWarning(position + 1)
            } else {
                selectedSemester = position + 1
                if (selectedCourseId.isNotEmpty()) {
                    loadSubjectsForExam()
                }
            }
        }
    }

    private fun setupCourseDropdown() {
        // Load courses from Firestore
        firestore.collection("courses")
            .get()
            .addOnSuccessListener { documents ->
                courseMap.clear()

                documents.forEach { doc ->
                    val courseId = doc.id
                    val courseName = doc.getString("name") ?:
                    doc.getString("courseName") ?: ""

                    if (courseName.isNotEmpty()) {
                        courseMap[courseName] = courseId
                    }
                }

                val courseNames = courseMap.keys.toList().sorted()
                val adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_dropdown_item_1line,
                    courseNames
                )
                courseDropdown.setAdapter(adapter)

                courseDropdown.setOnItemClickListener { parent, _, position, _ ->
                    val selected = parent.getItemAtPosition(position) as String
                    val courseId = courseMap[selected] ?: ""

                    if (subjectsList.isNotEmpty()) {
                        showCourseChangeWarning(courseId, selected)
                    } else {
                        selectedCourseId = courseId
                        selectedCourseName = selected

                        if (selectedSemester > 0) {
                            loadSubjectsForExam()
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupRecyclerView() {
        adapter = ExamSubjectAdapter(
            subjectsList,
            onEditClick = { subject -> editSubject(subject) },
            onDeleteClick = { subject -> deleteSubject(subject) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        // Setup drag-and-drop
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPosition = viewHolder.adapterPosition
                val toPosition = target.adapterPosition

                Collections.swap(subjectsList, fromPosition, toPosition)
                adapter.notifyItemMoved(fromPosition, toPosition)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // Not used
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        })

        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun setupButtons() {
        btnSelectStartDate.setOnClickListener {
            showDatePicker { selectedDate ->
                startDate = selectedDate
                startDateText.text = dateFormat.format(Date(selectedDate))
                startDateText.visibility = View.VISIBLE
            }
        }

        btnSelectEndDate.setOnClickListener {
            if (startDate == 0L) {
                Toast.makeText(this, "Please select start date first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showDatePicker(minDate = startDate) { selectedDate ->
                endDate = selectedDate
                endDateText.text = dateFormat.format(Date(selectedDate))
                endDateText.visibility = View.VISIBLE
            }
        }

        defaultStartTimeInput.setOnClickListener {
            showTimePicker(defaultStartTimeInput.text.toString()) { time ->
                defaultStartTimeInput.setText(time)
            }
        }

        btnApplyToAll.setOnClickListener {
            applyDefaultTimingsToAll()
        }

        btnConfirmExam.setOnClickListener {
            confirmExam()
        }
    }

    private fun loadSubjectsForExam() {
        if (selectedCourseId.isEmpty() || selectedSemester == 0) {
            return
        }

        showLoadingSubjects(true)

        Log.d("CreateExam", "Loading subjects for course: $selectedCourseId, semester: $selectedSemester")

        firestore.collection("subjects")
            .whereEqualTo("courseId", selectedCourseId)
            .get()
            .addOnSuccessListener { documents ->
                subjectsList.clear()

                val filteredSubjects = mutableListOf<Subject>()

                documents.forEach { doc ->
                    val subject = doc.toObject(Subject::class.java)
                    subject.id = doc.id

                    Log.d("CreateExam", "Found subject: ${subject.name}, semester: ${subject.semesterNumber}")

                    if (subject.semesterNumber == selectedSemester && subject.isActive) {
                        filteredSubjects.add(subject)
                    }
                }

                // Sort by subject code
                filteredSubjects.sortBy { it.code }

                Log.d("CreateExam", "Filtered ${filteredSubjects.size} subjects for semester $selectedSemester")

                if (filteredSubjects.isEmpty()) {
                    showLoadingSubjects(false)
                    Toast.makeText(
                        this,
                        "No subjects found for Semester $selectedSemester in $selectedCourseName",
                        Toast.LENGTH_LONG
                    ).show()
                    updateUI()
                    return@addOnSuccessListener
                }

                // Auto-schedule subjects
                autoScheduleSubjects(filteredSubjects)

                showLoadingSubjects(false)
                Toast.makeText(
                    this,
                    "${filteredSubjects.size} subjects loaded successfully",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                showLoadingSubjects(false)
                Toast.makeText(this, "Error loading subjects: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e("CreateExam", "Error loading subjects", e)
            }
    }

    private fun autoScheduleSubjects(subjects: List<Subject>) {
        if (startDate == 0L || endDate == 0L) {
            Toast.makeText(this, "Please select exam dates first", Toast.LENGTH_SHORT).show()
            return
        }

        val durationHours = defaultDurationInput.text.toString().toFloatOrNull() ?: 3f
        val startTime = defaultStartTimeInput.text.toString()

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = startDate

        subjects.forEach { subject ->
            // Skip weekends (optional)
            while (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                calendar.add(Calendar.DAY_OF_MONTH, 1)
            }

            // Check if we exceeded exam end date
            if (calendar.timeInMillis > endDate) {
                Toast.makeText(
                    this,
                    "Not enough days in exam period for all subjects!",
                    Toast.LENGTH_LONG
                ).show()
                return
            }

            val endTime = calculateEndTime(startTime, durationHours)

            val examSubject = ExamSubject(
                subjectId = subject.id,
                subjectName = subject.name,
                subjectCode = subject.code,
                courseId = selectedCourseId,
                courseName = selectedCourseName,
                examDate = calendar.timeInMillis,
                startTime = startTime,
                endTime = endTime,
                venue = "", // No venue as requested
                maxMarks = 100,
                duration = durationHours
            )

            subjectsList.add(examSubject)

            // Move to next day
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        adapter.updateList(subjectsList)
        updateUI()
    }

    private fun calculateEndTime(startTime: String, durationHours: Float): String {
        try {
            val timeFormat12 = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = timeFormat12.parse(startTime) ?: return startTime

            val calendar = Calendar.getInstance()
            calendar.time = date
            calendar.add(Calendar.MINUTE, (durationHours * 60).toInt())

            return timeFormat12.format(calendar.time)
        } catch (e: Exception) {
            Log.e("CreateExam", "Error calculating end time", e)
            return startTime
        }
    }

    private fun applyDefaultTimingsToAll() {
        if (subjectsList.isEmpty()) {
            Toast.makeText(this, "No subjects to apply timings", Toast.LENGTH_SHORT).show()
            return
        }

        val durationHours = defaultDurationInput.text.toString().toFloatOrNull() ?: 3f
        val startTime = defaultStartTimeInput.text.toString()
        val endTime = calculateEndTime(startTime, durationHours)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Apply Default Timings")
            .setMessage("Apply $startTime - $endTime (${durationHours}hrs) to all ${subjectsList.size} subjects?")
            .setPositiveButton("Apply") { _, _ ->
                subjectsList.forEachIndexed { index, subject ->
                    subjectsList[index] = subject.copy(
                        startTime = startTime,
                        endTime = endTime,
                        duration = durationHours
                    )
                }
                adapter.updateList(subjectsList)
                Toast.makeText(this, "Timings applied to all subjects", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showDatePicker(minDate: Long = System.currentTimeMillis(), callback: (Long) -> Unit) {
        val calendar = Calendar.getInstance()

        val datePicker = DatePickerDialog(
            this,
            { _, year, month, day ->
                calendar.set(year, month, day)
                callback(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )

        datePicker.datePicker.minDate = minDate
        datePicker.show()
    }

    private fun showTimePicker(currentTime: String = "10:00 AM", callback: (String) -> Unit) {
        val calendar = Calendar.getInstance()

        // Parse current time if available
        try {
            val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val date = timeFormat.parse(currentTime)
            date?.let { calendar.time = it }
        } catch (e: Exception) {
            // Use default time
        }

        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                val amPm = if (hourOfDay >= 12) "PM" else "AM"
                val hour12 = if (hourOfDay > 12) hourOfDay - 12 else if (hourOfDay == 0) 12 else hourOfDay
                callback(String.format("%02d:%02d %s", hour12, minute, amPm))
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            false
        ).show()
    }

    private fun editSubject(subject: ExamSubject) {
        val index = subjectsList.indexOf(subject)
        if (index == -1) return

        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_exam_subject, null)

        val examDateText: TextView = dialogView.findViewById(R.id.exam_date_text)
        val btnSelectDate: MaterialButton = dialogView.findViewById(R.id.btn_select_date)
        val startTimeInput: TextInputEditText = dialogView.findViewById(R.id.start_time_input)
        val endTimeInput: TextInputEditText = dialogView.findViewById(R.id.end_time_input)
        val durationInput: TextInputEditText = dialogView.findViewById(R.id.duration_input)
        val maxMarksInput: TextInputEditText = dialogView.findViewById(R.id.max_marks_input)

        // Pre-fill existing data
        examDateText.text = dateFormat.format(Date(subject.examDate))
        examDateText.visibility = View.VISIBLE
        startTimeInput.setText(subject.startTime)
        endTimeInput.setText(subject.endTime)
        durationInput.setText(subject.duration.toString())
        maxMarksInput.setText(subject.maxMarks.toString())

        var updatedExamDate = subject.examDate

        btnSelectDate.setOnClickListener {
            showDatePicker(minDate = startDate) { date ->
                if (date <= endDate) {
                    updatedExamDate = date
                    examDateText.text = dateFormat.format(Date(date))
                } else {
                    Toast.makeText(this, "Date must be within exam period", Toast.LENGTH_SHORT).show()
                }
            }
        }

        startTimeInput.setOnClickListener {
            showTimePicker(startTimeInput.text.toString()) { time ->
                startTimeInput.setText(time)

                // Auto-calculate end time
                val duration = durationInput.text.toString().toFloatOrNull() ?: 3f
                endTimeInput.setText(calculateEndTime(time, duration))
            }
        }

        endTimeInput.setOnClickListener {
            showTimePicker(endTimeInput.text.toString()) { time -> endTimeInput.setText(time) }
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Edit ${subject.subjectName}")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val updatedSubject = subject.copy(
                    examDate = updatedExamDate,
                    startTime = startTimeInput.text.toString(),
                    endTime = endTimeInput.text.toString(),
                    duration = durationInput.text.toString().toFloatOrNull() ?: 3f,
                    maxMarks = maxMarksInput.text.toString().toIntOrNull() ?: 100
                )

                subjectsList[index] = updatedSubject
                adapter.updateList(subjectsList)
                Toast.makeText(this, "Subject updated", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSubject(subject: ExamSubject) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Remove Subject")
            .setMessage("Remove ${subject.subjectName} from exam?")
            .setPositiveButton("Remove") { _, _ ->
                subjectsList.remove(subject)
                adapter.updateList(subjectsList)
                updateUI()
                Toast.makeText(this, "Subject removed", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSemesterChangeWarning(newSemester: Int) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Change Semester")
            .setMessage("Changing semester will clear all loaded subjects. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                selectedSemester = newSemester
                subjectsList.clear()
                adapter.updateList(subjectsList)
                updateUI()

                if (selectedCourseId.isNotEmpty()) {
                    loadSubjectsForExam()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                semesterDropdown.setText("Semester $selectedSemester", false)
            }
            .show()
    }

    private fun showCourseChangeWarning(newCourseId: String, newCourseName: String) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Change Course")
            .setMessage("Changing course will clear all loaded subjects. Continue?")
            .setPositiveButton("Continue") { _, _ ->
                selectedCourseId = newCourseId
                selectedCourseName = newCourseName
                subjectsList.clear()
                adapter.updateList(subjectsList)
                updateUI()

                if (selectedSemester > 0) {
                    loadSubjectsForExam()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                courseDropdown.setText(selectedCourseName, false)
            }
            .show()
    }

    private fun confirmExam() {
        if (!validateExamDetails()) return

        if (subjectsList.isEmpty()) {
            Toast.makeText(this, "Please load subjects for the exam", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Confirm Exam")
            .setMessage("Confirm exam schedule?\n\n${subjectsList.size} subjects will be scheduled.\n\nThis action cannot be undone.")
            .setPositiveButton("Confirm") { _, _ ->
                saveExam()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun validateExamDetails(): Boolean {
        val examName = examNameInput.text.toString().trim()
        val semester = semesterDropdown.text.toString()
        val course = courseDropdown.text.toString()
        val academicYear = academicYearInput.text.toString().trim()

        when {
            examName.isEmpty() -> {
                Toast.makeText(this, "Please enter exam name", Toast.LENGTH_SHORT).show()
                return false
            }
            semester.isEmpty() -> {
                Toast.makeText(this, "Please select semester", Toast.LENGTH_SHORT).show()
                return false
            }
            course.isEmpty() -> {
                Toast.makeText(this, "Please select course", Toast.LENGTH_SHORT).show()
                return false
            }
            academicYear.isEmpty() -> {
                Toast.makeText(this, "Please enter academic year", Toast.LENGTH_SHORT).show()
                return false
            }
            startDate == 0L -> {
                Toast.makeText(this, "Please select start date", Toast.LENGTH_SHORT).show()
                return false
            }
            endDate == 0L -> {
                Toast.makeText(this, "Please select end date", Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return true
    }

    private fun saveExam() {
        showLoading(true)

        val examName = examNameInput.text.toString().trim()
        val academicYear = academicYearInput.text.toString().trim()

        val exam = Exam(
            examName = examName,
            semester = selectedSemester,
            academicYear = academicYear,
            startDate = startDate,
            endDate = endDate,
            courses = listOf(selectedCourseId),
            subjects = subjectsList,
            status = "confirmed",
            isConfirmed = true,
            timetableGenerated = false,
            createdBy = "Admin",
            confirmedAt = System.currentTimeMillis()
        )

        firestore.collection("exams")
            .add(exam)
            .addOnSuccessListener { documentReference ->
                showLoading(false)
                Toast.makeText(this, "Exam created successfully", Toast.LENGTH_SHORT).show()

                // Navigate to exam details
                val intent = Intent(this, ExamDetailsActivity::class.java)
                intent.putExtra("EXAM_ID", documentReference.id)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CreateExam", "Error saving exam", e)
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnConfirmExam.isEnabled = !show
    }

    private fun showLoadingSubjects(show: Boolean) {
        loadingSubjectsText.visibility = if (show) View.VISIBLE else View.GONE
        emptyView.visibility = View.GONE
    }

    private fun updateUI() {
        val hasSubjects = subjectsList.isNotEmpty()
        emptyView.visibility = if (!hasSubjects && loadingSubjectsText.visibility != View.VISIBLE) View.VISIBLE else View.GONE
        btnConfirmExam.visibility = if (hasSubjects) View.VISIBLE else View.GONE
        btnApplyToAll.isEnabled = hasSubjects
    }
}
