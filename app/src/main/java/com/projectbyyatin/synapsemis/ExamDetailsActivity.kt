package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.EnhancedExamSubjectAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import java.io.File
import com.projectbyyatin.synapsemis.utils.SafeFirestoreParser
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class ExamDetailsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examName: TextView
    private lateinit var semesterChip: Chip
    private lateinit var academicYearChip: Chip
    private lateinit var statusChip: Chip
    private lateinit var examPeriod: TextView
    private lateinit var totalSubjects: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnDownloadTimetable: MaterialButton
    private lateinit var btnShareTimetable: MaterialButton
    private lateinit var btnRegenerateTimetable: MaterialButton
    private lateinit var btnStartExam: MaterialButton
    private lateinit var btnDeleteExam: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: EnhancedExamSubjectAdapter
    private var examData: Exam? = null
    private var examId: String = ""

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_details)

        examId = intent.getStringExtra("EXAM_ID") ?: ""
        if (examId.isEmpty()) {
            Toast.makeText(this, "Exam ID missing", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        loadExamData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examName = findViewById(R.id.exam_name)
        semesterChip = findViewById(R.id.semester_chip)
        academicYearChip = findViewById(R.id.academic_year_chip)
        statusChip = findViewById(R.id.status_chip)
        examPeriod = findViewById(R.id.exam_period)
        totalSubjects = findViewById(R.id.total_subjects)
        recyclerView = findViewById(R.id.recycler_view)
        btnDownloadTimetable = findViewById(R.id.btn_download_timetable)
        btnShareTimetable = findViewById(R.id.btn_share_timetable)
        btnRegenerateTimetable = findViewById(R.id.btn_regenerate_timetable)
        btnStartExam = findViewById(R.id.btn_start_exam)
        btnDeleteExam = findViewById(R.id.btn_delete_exam)
        loadingProgress = findViewById(R.id.loading_progress)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Exam Details"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadExamData() {
        showLoading(true)
        firestore.collection("exams").document(examId)
            .get()
            .addOnSuccessListener { document ->
                // ✅ FIXED - Use SafeFirestoreParser
                examData = SafeFirestoreParser.safeParseExam(document)

                examData?.let { exam ->
                    displayExamData(exam)
                    setupRecycler(exam)
                    setupButtons(exam)
                } ?: run {
                    // Handle parse failure
                    Toast.makeText(this, "Invalid exam data", Toast.LENGTH_SHORT).show()
                    finish()
                }

                showLoading(false)
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }


    private fun displayExamData(exam: Exam) {
        examName.text = exam.examName
        semesterChip.text = "Semester ${exam.semester}"
        academicYearChip.text = exam.academicYear
        examPeriod.text = "${displayDateFormat.format(Date(exam.startDate))} - ${displayDateFormat.format(Date(exam.endDate))}"
        totalSubjects.text = "${exam.subjects.size} Subjects"

        when (exam.status) {
            "confirmed" -> {
                statusChip.text = "Confirmed"
                statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)
            }
            "ongoing" -> {
                statusChip.text = "Ongoing"
                statusChip.setChipBackgroundColorResource(android.R.color.holo_orange_dark)
            }
            "completed" -> {
                statusChip.text = "Completed"
                statusChip.setChipBackgroundColorResource(android.R.color.holo_blue_dark)
            }
            else -> {
                statusChip.text = "Draft"
                statusChip.setChipBackgroundColorResource(android.R.color.darker_gray)
            }
        }
    }

    private fun setupRecycler(exam: Exam) {
        val sorted = exam.subjects.sortedWith(compareBy({ it.examDate }, { parseTime(it.startTime) }))
        adapter = EnhancedExamSubjectAdapter(
            sorted.toMutableList(),
            { rescheduleSubject(it) },
            { deleteSubject(it) },
            { assignInvigilator(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun parseTime(timeStr: String): Long {
        return try {
            SimpleDateFormat("hh:mm a", Locale.getDefault()).parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun setupButtons(exam: Exam) {
        btnDownloadTimetable.setOnClickListener { generateCourseWiseTimetables(exam) }
        btnShareTimetable.setOnClickListener { shareTimetables(exam) }
        btnRegenerateTimetable.setOnClickListener { regenerateTimetables(exam) }
        btnDeleteExam.setOnClickListener { deleteExam(exam) }

        when (exam.status) {
            "confirmed" -> {
                btnStartExam.visibility = View.VISIBLE
                btnStartExam.text = "Start Exam"
            }
            "ongoing" -> {
                btnStartExam.visibility = View.VISIBLE
                btnStartExam.text = "Mark as Completed"
            }
            else -> {
                btnStartExam.visibility = View.GONE
            }
        }

        btnStartExam.setOnClickListener {
            when (exam.status) {
                "confirmed" -> startExam(exam)
                "ongoing" -> completeExam(exam)
            }
        }
    }

    // ==================== RESCHEDULE ====================
    private fun rescheduleSubject(subject: ExamSubject) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reschedule_subject, null)

        val dateText: TextView = dialogView.findViewById(R.id.exam_date_text)
        val btnSelectDate: MaterialButton = dialogView.findViewById(R.id.btn_select_date)
        val startTimeInput: TextInputEditText = dialogView.findViewById(R.id.start_time_input)
        val durationInput: TextInputEditText = dialogView.findViewById(R.id.duration_input)
        val endTimeText: TextView = dialogView.findViewById(R.id.end_time_text)

        dateText.text = dateFormat.format(Date(subject.examDate))
        startTimeInput.setText(subject.startTime)
        durationInput.setText(subject.duration.toString())
        endTimeText.text = subject.endTime

        var selectedDate = subject.examDate

        btnSelectDate.setOnClickListener {
            examData?.let { exam ->
                showDatePicker(exam.startDate, exam.endDate) { date ->
                    selectedDate = date
                    dateText.text = dateFormat.format(Date(date))
                }
            }
        }

        startTimeInput.setOnClickListener {
            showTimePicker(startTimeInput.text.toString()) { time ->
                startTimeInput.setText(time)
                val duration = durationInput.text.toString().toFloatOrNull() ?: 2.5f
                endTimeText.text = calculateEndTime(time, duration)
            }
        }

        durationInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val duration = s.toString().toFloatOrNull()
                if (duration != null && duration > 0) {
                    endTimeText.text = calculateEndTime(startTimeInput.text.toString(), duration)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Reschedule: ${subject.subjectName}")
            .setView(dialogView)
            .setPositiveButton("Save", null)
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val duration = durationInput.text.toString().toFloatOrNull()
                if (duration == null || duration <= 0) {
                    Toast.makeText(this, "⚠️ Invalid duration", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val cal = Calendar.getInstance()
                cal.timeInMillis = selectedDate
                if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                    Toast.makeText(this, "⚠️ Cannot schedule on Sundays", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updated = subject.copy(
                    examDate = selectedDate,
                    startTime = startTimeInput.text.toString(),
                    endTime = endTimeText.text.toString(),
                    duration = duration
                )

                updateSubjectInFirestore(updated) {
                    adapter.updateSubject(updated)
                    Toast.makeText(this, "✅ Rescheduled", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    // ==================== DELETE ====================
    private fun deleteSubject(subject: ExamSubject) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Subject")
            .setMessage("Remove ${subject.subjectName}?\n\nThis cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                showLoading(true)
                val exam = examData ?: return@setPositiveButton
                val updated = exam.subjects.filter { it.subjectId != subject.subjectId }

                firestore.collection("exams").document(examId)
                    .update("subjects", updated)
                    .addOnSuccessListener {
                        showLoading(false)
                        adapter.removeSubject(subject)
                        exam.subjects = updated
                        totalSubjects.text = "${updated.size} Subjects"
                        Toast.makeText(this, "✅ Deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================== ASSIGN INVIGILATOR ====================
    private fun assignInvigilator(subject: ExamSubject) {
        Toast.makeText(this, "Assign invigilator: ${subject.subjectName}\n\nComing soon!", Toast.LENGTH_LONG).show()
    }

    // ==================== START/COMPLETE EXAM ====================
    private fun startExam(exam: Exam) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Start Exam")
            .setMessage("Mark as ONGOING?\n\n• Enable marks entry\n• Lock timetable")
            .setPositiveButton("Start") { _, _ ->
                showLoading(true)
                firestore.collection("exams").document(examId)
                    .update(
                        "status", "ongoing",
                        "marksEntryEnabled", true  // ← ADD THIS
                    )
                    .addOnSuccessListener {
                        showLoading(false)
                        examData?.status = "ongoing"
                        examData?.marksEntryEnabled = true  // ← Update local object
                        examData?.let {
                            displayExamData(it)
                            setupButtons(it)
                        }
                        Toast.makeText(this, "✅ Marks entry enabled", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun completeExam(exam: Exam) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Complete Exam")
            .setMessage("Mark as COMPLETED?\n\n• Archive exam\n• Lock all changes")
            .setPositiveButton("Complete") { _, _ ->
                updateExamStatus("completed")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateExamStatus(status: String) {
        showLoading(true)
        firestore.collection("exams").document(examId)
            .update("status", status)
            .addOnSuccessListener {
                showLoading(false)
                examData?.status = status
                examData?.let {
                    displayExamData(it)
                    setupButtons(it)
                }
                Toast.makeText(this, "✅ Status updated", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ==================== DELETE EXAM ====================
    private fun deleteExam(exam: Exam) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("⚠️ Delete Exam")
            .setMessage("Permanently delete?\n\n• All schedules lost\n• PDFs deleted\n• Cannot be undone!")
            .setPositiveButton("Delete") { _, _ ->
                showLoading(true)
                firestore.collection("exams").document(examId)
                    .delete()
                    .addOnSuccessListener {
                        showLoading(false)
                        deleteTimetablePDFs(exam)
                        Toast.makeText(this, "✅ Deleted", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTimetablePDFs(exam: Exam) {
        try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Timetables")
            dir.listFiles { file ->
                file.extension == "pdf" && file.name.contains(exam.examName)
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ==================== REGENERATE ====================
    private fun regenerateTimetables(exam: Exam) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Regenerate Timetables")
            .setMessage("Delete old PDFs and generate new ones?")
            .setPositiveButton("Regenerate") { _, _ ->
                deleteTimetablePDFs(exam)
                generateCourseWiseTimetables(exam)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ==================== PDF GENERATION ====================
    private fun generateCourseWiseTimetables(exam: Exam) {
        showLoading(true)
        try {
            val courseGroups = exam.subjects.groupBy { it.courseId }
            val files = mutableListOf<File>()

            courseGroups.forEach { (_, subjects) ->
                val courseName = subjects.firstOrNull()?.courseName ?: "Course"
                generatePDF(exam, subjects, courseName)?.let { files.add(it) }
            }

            showLoading(false)
            if (files.isNotEmpty()) {
                btnShareTimetable.visibility = View.VISIBLE
                AlertDialog.Builder(this, R.style.CustomAlertDialog)
                    .setTitle("✅ Generated!")
                    .setMessage("${files.size} timetable(s) created")
                    .setPositiveButton("Share") { _, _ -> shareTimetables(exam) }
                    .setNegativeButton("Done", null)
                    .show()
            }
        } catch (e: Exception) {
            showLoading(false)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun generatePDF(exam: Exam, subjects: List<ExamSubject>, courseName: String): File? {
        try {
            val pdf = PdfDocument()
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
            val canvas = page.canvas
            val paint = Paint().apply { isAntiAlias = true }

            var y = 70f
            val margin = 50f

            // Date stamp
            paint.apply {
                textSize = 11f
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText(dateFormat.format(Date()), 545f, y, paint)

            // Header
            y = 100f
            paint.apply {
                textSize = 15f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("ROYAL COLLEGE OF ARTS, SCIENCE AND COMMERCE", 297.5f, y, paint)
            y += 22f
            canvas.drawText("(AUTONOMOUS)", 297.5f, y, paint)
            y += 25f
            paint.textSize = 14f
            canvas.drawText(courseName, 297.5f, y, paint)
            y += 22f
            paint.textSize = 13f
            canvas.drawText(exam.examName.uppercase(), 297.5f, y, paint)
            y += 22f
            canvas.drawText(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(exam.startDate)), 297.5f, y, paint)
            y += 22f
            canvas.drawText("TIME TABLE", 297.5f, y, paint)

            y += 40f
            paint.textAlign = Paint.Align.LEFT

            // Time info if all same
            val timesVary = subjects.distinctBy { "${it.startTime}-${it.endTime}" }.size > 1
            if (!timesVary) {
                paint.apply {
                    textSize = 12f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                subjects.firstOrNull()?.let {
                    canvas.drawText("Time: ${it.startTime} to ${it.endTime}", margin, y, paint)
                }
                y += 30f
            }

            // Table
            val tableLeft = margin
            val tableRight = 545f
            val colDayDate = 100f
            val colCodeSubj = if (timesVary) 240f else 320f
            val colTime = if (timesVary) 110f else 0f
            val colRef = 50f

            // Header
            paint.apply {
                style = Paint.Style.FILL
                color = 0xFFF0F0F0.toInt()
            }
            canvas.drawRect(tableLeft, y - 15f, tableRight, y + 10f, paint)

            paint.apply {
                style = Paint.Style.STROKE
                color = 0xFF333333.toInt()
                strokeWidth = 2f
            }
            canvas.drawRect(tableLeft, y - 15f, tableRight, y + 10f, paint)

            paint.apply {
                style = Paint.Style.FILL
                color = 0xFF000000.toInt()
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            var x = tableLeft + 10f
            canvas.drawText("Day & Date", x, y, paint)
            x += colDayDate
            canvas.drawText("Subject Code & Name", x, y, paint)
            x += colCodeSubj
            if (timesVary) {
                canvas.drawText("Time", x, y, paint)
                x += colTime
            }
            canvas.drawText("Ref", x, y, paint)

            y += 10f

            // Rows
            paint.apply {
                textSize = 11f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }

            subjects.sortedBy { it.examDate }.forEachIndexed { i, s ->
                val rowTop = y
                val rowBottom = y + 30f

                val date = Date(s.examDate)
                x = tableLeft + 10f

                // Day & Date
                canvas.drawText(dayFormat.format(date), x, y + 10f, paint)
                canvas.drawText(dateFormat.format(date), x, y + 22f, paint)

                // Code & Subject
                x += colDayDate
                canvas.drawText(s.subjectCode, x, y + 10f, paint)
                canvas.drawText(s.subjectName, x, y + 22f, paint)

                // Time
                if (timesVary) {
                    x += colCodeSubj
                    canvas.drawText("${s.startTime} -", x, y + 10f, paint)
                    canvas.drawText(s.endTime, x, y + 22f, paint)
                }

                // Ref
                x = tableLeft + (tableRight - tableLeft) - colRef + 10f
                canvas.drawText((i + 1).toString(), x, y + 16f, paint)

                // Borders
                paint.style = Paint.Style.STROKE
                canvas.drawLine(tableLeft, rowBottom, tableRight, rowBottom, paint)
                paint.style = Paint.Style.FILL

                y += 30f
            }

            // Final border
            paint.style = Paint.Style.STROKE
            canvas.drawRect(tableLeft, 100f + 110f, tableRight, y, paint)

            // Signatures
            y = 742f
            paint.apply {
                style = Paint.Style.FILL
                textSize = 11f
            }

            canvas.drawLine(margin, y, margin + 120f, y, paint)
            canvas.drawLine(247.5f, y, 347.5f, y, paint)
            canvas.drawLine(425f, y, 545f, y, paint)

            y += 20f
            canvas.drawText("EXAMINATION COMMITTEE", margin, y, paint)
            canvas.drawText("Controller of Examination", 247.5f, y, paint)
            paint.textAlign = Paint.Align.RIGHT
            canvas.drawText("Principal", 545f, y, paint)

            pdf.finishPage(page)

            // Save
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Timetables")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${exam.examName}_${courseName.replace(" ", "_")}_Timetable.pdf")
            pdf.writeTo(FileOutputStream(file))
            pdf.close()

            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // ==================== SHARE ====================
    private fun shareTimetables(exam: Exam) {
        try {
            val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Timetables")
            val files = dir.listFiles { f -> f.extension == "pdf" && f.name.contains(exam.examName) }

            if (files.isNullOrEmpty()) {
                Toast.makeText(this, "Generate timetables first", Toast.LENGTH_SHORT).show()
                return
            }

            if (files.size == 1) {
                val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", files[0])
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share"))
            } else {
                val uris = ArrayList<android.net.Uri>()
                files.forEach { uris.add(FileProvider.getUriForFile(this, "${packageName}.fileprovider", it)) }
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "application/pdf"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "Share"))
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== HELPERS ====================
    private fun updateSubjectInFirestore(updated: ExamSubject, onSuccess: () -> Unit) {
        showLoading(true)
        val exam = examData ?: return
        val subjects = exam.subjects.map { if (it.subjectId == updated.subjectId) updated else it }

        firestore.collection("exams").document(examId)
            .update("subjects", subjects)
            .addOnSuccessListener {
                showLoading(false)
                exam.subjects = subjects
                onSuccess()
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePicker(minDate: Long, maxDate: Long, callback: (Long) -> Unit) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d, 0, 0, 0)
            if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {
                Toast.makeText(this, "⚠️ Sundays not allowed", Toast.LENGTH_SHORT).show()
                return@DatePickerDialog
            }
            callback(cal.timeInMillis)
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
            datePicker.minDate = minDate
            datePicker.maxDate = maxDate
        }.show()
    }

    private fun showTimePicker(current: String, callback: (String) -> Unit) {
        val cal = Calendar.getInstance()
        TimePickerDialog(this, { _, h, m ->
            val amPm = if (h >= 12) "PM" else "AM"
            val hour = if (h > 12) h - 12 else if (h == 0) 12 else h
            callback(String.format("%02d:%02d %s", hour, m, amPm))
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), false).show()
    }

    private fun calculateEndTime(start: String, duration: Float): String {
        return try {
            val f = SimpleDateFormat("hh:mm a", Locale.getDefault())
            val cal = Calendar.getInstance()
            cal.time = f.parse(start) ?: return start
            cal.add(Calendar.MINUTE, (duration * 60).toInt())
            f.format(cal.time)
        } catch (e: Exception) {
            start
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (examId.isNotEmpty()) loadExamData()
    }
}