package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

class GenerateResultActivity_HOD : AppCompatActivity() {

    private lateinit var toolbar               : Toolbar
    private lateinit var tvDeptName            : TextView   // shows auto-loaded dept
    private lateinit var courseSpinner         : Spinner
    private lateinit var semesterSpinner       : Spinner
    private lateinit var loadExamsButton       : MaterialButton

    private lateinit var writtenExamsCard      : MaterialCardView
    private lateinit var writtenExamsChipGroup : ChipGroup
    private lateinit var writtenEmptyText      : TextView

    private lateinit var internalExamsCard     : MaterialCardView
    private lateinit var internalExamsChipGroup: ChipGroup
    private lateinit var internalEmptyText     : TextView

    private lateinit var practicalExamsCard    : MaterialCardView
    private lateinit var practicalExamsChipGroup: ChipGroup
    private lateinit var practicalEmptyText    : TextView
    private lateinit var tvPracticalOptional   : TextView

    private lateinit var generateButton        : MaterialButton
    private lateinit var loadingProgress       : ProgressBar
    private lateinit var selectionStatusText   : TextView

    private val auth      = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val courses        = mutableListOf<CourseItem>()
    private val publishedExams = mutableListOf<ExamItem>()

    // Auto-filled from HOD profile
    private var selectedDeptId   = ""
    private var selectedDeptName = ""

    private var selectedCourseId = ""
    private var selectedSemester = 0

    private val selectedWrittenExamIds   = mutableSetOf<String>()
    private val selectedInternalExamIds  = mutableSetOf<String>()
    private val selectedPracticalExamIds = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_result_hod)
        initViews()
        setupToolbar()
        loadHodDepartment()   // auto-load dept from HOD profile
    }

    private fun initViews() {
        toolbar                  = findViewById(R.id.toolbar)
        tvDeptName               = findViewById(R.id.tv_dept_name)
        courseSpinner            = findViewById(R.id.course_spinner)
        semesterSpinner          = findViewById(R.id.semester_spinner)
        loadExamsButton          = findViewById(R.id.load_exams_button)

        writtenExamsCard         = findViewById(R.id.written_exams_card)
        writtenExamsChipGroup    = findViewById(R.id.written_exams_chip_group)
        writtenEmptyText         = findViewById(R.id.written_empty_text)

        internalExamsCard        = findViewById(R.id.internal_exams_card)
        internalExamsChipGroup   = findViewById(R.id.internal_exams_chip_group)
        internalEmptyText        = findViewById(R.id.internal_empty_text)

        practicalExamsCard       = findViewById(R.id.practical_exams_card)
        practicalExamsChipGroup  = findViewById(R.id.practical_exams_chip_group)
        practicalEmptyText       = findViewById(R.id.practical_empty_text)
        tvPracticalOptional      = findViewById(R.id.tv_practical_optional)

        generateButton           = findViewById(R.id.generate_button)
        loadingProgress          = findViewById(R.id.loading_progress)
        selectionStatusText      = findViewById(R.id.selection_status_text)

        // Semester spinner
        val sems = arrayOf("Select Semester") + (1..8).map { "Semester $it" }
        semesterSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, sems)

        // Hide exam cards until exams are loaded
        writtenExamsCard.visibility   = View.GONE
        internalExamsCard.visibility  = View.GONE
        practicalExamsCard.visibility = View.GONE
        generateButton.isEnabled      = false

        loadExamsButton.setOnClickListener { loadPublishedExams() }
        generateButton.setOnClickListener  { validateAndGenerate() }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Generate Result"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    // ── Auto-load HOD's department ────────────────────────────────────────────

    private fun loadHodDepartment() {
        showLoading(true)
        tvDeptName.text = "Loading department…"

        val email = auth.currentUser?.email ?: run {
            tvDeptName.text = "Not logged in"
            showLoading(false)
            return
        }

        firestore.collection("faculty")
            .whereEqualTo("email", email)
            .whereEqualTo("role", "hod")
            .limit(1)
            .get()
            .addOnSuccessListener { snap ->
                showLoading(false)
                if (snap.isEmpty) {
                    tvDeptName.text = "Department not found"
                    toast("HOD profile not found")
                    return@addOnSuccessListener
                }
                val doc = snap.documents[0]
                selectedDeptId   = doc.getString("departmentId") ?: ""
                selectedDeptName = doc.getString("department")   ?: ""

                tvDeptName.text = "🏛  $selectedDeptName"

                if (selectedDeptId.isNotEmpty()) {
                    loadCourses(selectedDeptId)
                } else {
                    tvDeptName.text = "Department not assigned"
                    toast("No department assigned to your profile")
                }
            }
            .addOnFailureListener {
                showLoading(false)
                tvDeptName.text = "Failed to load department"
                toast("Error: ${it.message}")
            }
    }

    // ── Courses for HOD's department ──────────────────────────────────────────

    private fun loadCourses(deptId: String) {
        firestore.collection("courses")
            .whereEqualTo("departmentId", deptId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs ->
                courses.clear()
                courses.add(CourseItem("", "Select Course"))
                docs.forEach { d -> courses.add(CourseItem(d.id, d.getString("name") ?: d.id)) }
                courseSpinner.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item, courses.map { it.name }
                )
                courseSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        selectedCourseId = courses[pos].id
                        resetExamCards()
                    }
                }
            }
            .addOnFailureListener { toast("Failed to load courses: ${it.message}") }
    }

    // ── Load published exams ──────────────────────────────────────────────────

    private fun loadPublishedExams() {
        selectedSemester = semesterSpinner.selectedItemPosition
        if (selectedDeptId.isEmpty())   { toast("Department not loaded yet"); return }
        if (selectedCourseId.isEmpty()) { toast("Select a course");           return }
        if (selectedSemester == 0)      { toast("Select a semester");         return }

        showLoading(true)
        resetExamCards()
        publishedExams.clear()

        firestore.collection("exams")
            .whereEqualTo("marksPublished", true)
            .whereEqualTo("semester", selectedSemester)
            .get()
            .addOnSuccessListener { docs ->
                showLoading(false)
                val written   = mutableListOf<ExamItem>()
                val internal  = mutableListOf<ExamItem>()
                val practical = mutableListOf<ExamItem>()

                docs.forEach { doc ->
                    val exam = buildExamFromRaw(doc.id, doc.data ?: return@forEach)
                    val matchesCourse = exam.subjects.any { it.courseId == selectedCourseId }
                            || exam.courseId == selectedCourseId
                            || exam.courses.contains(selectedCourseId)
                    if (!matchesCourse) return@forEach

                    val item = ExamItem(doc.id, exam.examName, exam.examType, exam)
                    publishedExams.add(item)

                    when (exam.examType.lowercase()) {
                        "practical" -> practical.add(item)
                        "internal"  -> internal.add(item)
                        else        -> written.add(item)
                    }
                }

                writtenExamsCard.visibility   = View.VISIBLE
                internalExamsCard.visibility  = View.VISIBLE
                practicalExamsCard.visibility = View.VISIBLE

                populateChips(written,   writtenExamsChipGroup,   selectedWrittenExamIds,   writtenEmptyText,   optional = false)
                populateChips(internal,  internalExamsChipGroup,  selectedInternalExamIds,  internalEmptyText,  optional = false)
                populateChips(practical, practicalExamsChipGroup, selectedPracticalExamIds, practicalEmptyText, optional = true)

                updateGenerateButton()

                if (written.isEmpty() && internal.isEmpty() && practical.isEmpty()) {
                    toast("No published exams found for this course & semester")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("Failed to load exams: ${e.message}")
            }
    }

    private fun populateChips(
        items   : List<ExamItem>,
        group   : ChipGroup,
        selected: MutableSet<String>,
        emptyTv : TextView,
        optional: Boolean
    ) {
        group.removeAllViews()
        if (optional) {
            tvPracticalOptional.text = if (items.isEmpty())
                "Optional — no practical exams found for this selection"
            else
                "Optional — select practical exams to include in result"
        }
        if (items.isEmpty()) { emptyTv.visibility = View.VISIBLE; return }
        emptyTv.visibility = View.GONE
        items.forEach { item ->
            val chip = Chip(this).apply {
                text = item.name
                isCheckable = true
                isChecked = false
                setChipBackgroundColorResource(android.R.color.transparent)
                setTextColor(resources.getColor(android.R.color.white, null))
                chipStrokeWidth = 2f
                setOnCheckedChangeListener { _, checked ->
                    if (checked) selected.add(item.id) else selected.remove(item.id)
                    updateGenerateButton()
                }
            }
            group.addView(chip)
        }
    }

    private fun updateGenerateButton() {
        val hasMinimum = selectedWrittenExamIds.isNotEmpty() || selectedInternalExamIds.isNotEmpty()
        generateButton.isEnabled = hasMinimum
        val parts = mutableListOf<String>()
        if (selectedWrittenExamIds.isNotEmpty())   parts.add("${selectedWrittenExamIds.size} written")
        if (selectedInternalExamIds.isNotEmpty())  parts.add("${selectedInternalExamIds.size} internal")
        if (selectedPracticalExamIds.isNotEmpty()) parts.add("${selectedPracticalExamIds.size} practical")
        selectionStatusText.text = if (parts.isEmpty())
            "Select at least one written or internal exam"
        else
            "${parts.joinToString(" + ")} exam(s) selected"
    }

    private fun validateAndGenerate() {
        if (selectedWrittenExamIds.isEmpty() && selectedInternalExamIds.isEmpty()) {
            toast("Select at least one written or internal exam"); return
        }
        startActivity(Intent(this, ResultDisplayActivity::class.java).apply {
            putExtra("DEPT_ID",            selectedDeptId)
            putExtra("DEPT_NAME",          selectedDeptName)
            putExtra("COURSE_ID",          selectedCourseId)
            putExtra("COURSE_NAME",        courses.find { it.id == selectedCourseId }?.name ?: "")
            putExtra("SEMESTER",           selectedSemester)
            putExtra("WRITTEN_EXAM_IDS",   selectedWrittenExamIds.toTypedArray())
            putExtra("INTERNAL_EXAM_IDS",  selectedInternalExamIds.toTypedArray())
            putExtra("PRACTICAL_EXAM_IDS", selectedPracticalExamIds.toTypedArray())
        })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun resetExamCards() {
        listOf(writtenExamsChipGroup, internalExamsChipGroup, practicalExamsChipGroup)
            .forEach { it.removeAllViews() }
        selectedWrittenExamIds.clear()
        selectedInternalExamIds.clear()
        selectedPracticalExamIds.clear()
        writtenExamsCard.visibility   = View.GONE
        internalExamsCard.visibility  = View.GONE
        practicalExamsCard.visibility = View.GONE
        generateButton.isEnabled      = false
        selectionStatusText.text      = "Select at least one written or internal exam"
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun tsLong(v: Any?): Long = when (v) {
        is Timestamp -> v.toDate().time
        is Long      -> v
        is Double    -> v.toLong()
        is Int       -> v.toLong()
        else         -> 0L
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildExamFromRaw(id: String, raw: Map<String, Any>): Exam {
        val parsedSubjects = mutableListOf<ExamSubject>()
        (raw["subjects"] as? List<*>)?.forEach { item ->
            val s = item as? Map<*, *> ?: return@forEach
            parsedSubjects.add(ExamSubject(
                subjectId        = s["subjectId"]        as? String ?: "",
                subjectName      = s["subjectName"]      as? String ?: "",
                subjectCode      = s["subjectCode"]      as? String ?: "",
                courseId         = s["courseId"]         as? String ?: "",
                courseName       = s["courseName"]       as? String ?: "",
                examDate         = tsLong(s["examDate"]),
                examType         = s["examType"]         as? String ?: "Written",
                maxMarks         = tsLong(s["maxMarks"]).toInt(),
                writtenMaxMarks  = tsLong(s["writtenMaxMarks"]).toInt(),
                internalMaxMarks = tsLong(s["internalMaxMarks"]).toInt(),
                assignedFacultyId= s["assignedFacultyId"] as? String ?: ""
            ))
        }
        val parsedStatus = mutableMapOf<String, SubjectMarksStatus>()
        (raw["subjectsMarksStatus"] as? Map<*, *>)?.forEach { (key, value) ->
            val m = value as? Map<*, *> ?: return@forEach
            parsedStatus[key.toString()] = SubjectMarksStatus(
                subjectId      = m["subjectId"]      as? String  ?: "",
                marksSubmitted = m["marksSubmitted"] as? Boolean ?: false,
                moderated      = m["moderated"]      as? Boolean ?: false,
                moderatedAt    = tsLong(m["moderatedAt"])
            )
        }
        return Exam(
            id                  = id,
            examName            = raw["examName"]            as? String  ?: "",
            semester            = tsLong(raw["semester"]).toInt(),
            academicYear        = raw["academicYear"]        as? String  ?: "",
            startDate           = tsLong(raw["startDate"]),
            examType            = raw["examType"]            as? String  ?: "",
            courseId            = raw["courseId"]            as? String  ?: "",
            courseName          = raw["courseName"]          as? String  ?: "",
            isActive            = raw["isActive"]            as? Boolean ?: true,
            courses             = (raw["courses"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            subjects            = parsedSubjects,
            marksEntryEnabled   = raw["marksEntryEnabled"]   as? Boolean ?: false,
            marksPublished      = raw["marksPublished"]      as? Boolean ?: false,
            subjectsMarksStatus = parsedStatus
        )
    }

    data class CourseItem(val id: String, val name: String)
    data class ExamItem(val id: String, val name: String, val type: String, val exam: Exam)
}