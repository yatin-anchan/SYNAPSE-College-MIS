package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Class
import java.util.Calendar
import kotlin.random.Random

class AddClassActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var classNameInput: TextInputEditText
    private lateinit var courseDropdown: AutoCompleteTextView
    private lateinit var classTeacherDropdown: AutoCompleteTextView
    private lateinit var maxSizeInput: TextInputEditText
    private lateinit var currentSemesterInput: TextInputEditText
    private lateinit var totalSemestersInput: TextInputEditText
    private lateinit var academicYearInput: TextInputEditText
    private lateinit var batchInput: TextInputEditText
    private lateinit var btnCreate: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private var selectedCourseId: String = ""
    private var selectedCourseName: String = ""
    private var selectedDepartmentId: String = ""
    private var selectedDepartmentName: String = ""
    private var selectedClassTeacherId: String = ""
    private var selectedClassTeacherName: String = ""
    private var totalCourseSemesters: Int = 6

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_class)

        initializeViews()
        setupToolbar()
        loadCourses()
        loadTeachers()
        setupButtons()
        setDefaultValues()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        classNameInput = findViewById(R.id.class_name_input)
        courseDropdown = findViewById(R.id.course_dropdown)
        classTeacherDropdown = findViewById(R.id.class_teacher_dropdown)
        maxSizeInput = findViewById(R.id.max_size_input)
        currentSemesterInput = findViewById(R.id.current_semester_input)
        totalSemestersInput = findViewById(R.id.total_semesters_input)
        academicYearInput = findViewById(R.id.academic_year_input)
        batchInput = findViewById(R.id.batch_input)
        btnCreate = findViewById(R.id.btn_create)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Add New Class"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setDefaultValues() {
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)

        // Academic year typically starts in June/July
        val academicYear = if (currentMonth >= 6) {
            "$currentYear-${currentYear + 1}"
        } else {
            "${currentYear - 1}-$currentYear"
        }

        academicYearInput.setText(academicYear)

        // Auto-calculate batch based on total semesters (assuming 6 sems = 3 years)
        currentSemesterInput.setText("1")
        totalSemestersInput.setText("6")
        updateBatch()
    }

    private fun updateBatch() {
        val currentSem = currentSemesterInput.text.toString().toIntOrNull() ?: 1
        val totalSems = totalSemestersInput.text.toString().toIntOrNull() ?: 6
        val academicYear = academicYearInput.text.toString()

        if (academicYear.isNotEmpty() && academicYear.contains("-")) {
            val startYear = academicYear.split("-")[0].toIntOrNull() ?: return
            val yearsInCourse = (totalSems / 2.0).toInt() // 2 sems per year
            val endYear = startYear + yearsInCourse
            batchInput.setText("$startYear-$endYear")
        }
    }

    private fun loadCourses() {
        firestore.collection("courses")
            .get()
            .addOnSuccessListener { documents ->
                val courseNames = mutableListOf<String>()
                val courseMap = mutableMapOf<String, Triple<String, Pair<String, String>, Int>>()

                documents.forEach { doc ->
                    val courseName = doc.getString("courseName") ?: ""
                    val courseId = doc.id
                    val deptId = doc.getString("departmentId") ?: ""
                    val deptName = doc.getString("departmentName") ?: ""
                    val totalSems = doc.getLong("totalSemesters")?.toInt() ?: 6

                    courseNames.add(courseName)
                    courseMap[courseName] = Triple(courseId, Pair(deptId, deptName), totalSems)
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, courseNames)
                courseDropdown.setAdapter(adapter)

                courseDropdown.setOnItemClickListener { _, _, position, _ ->
                    val selected = courseNames[position]
                    val data = courseMap[selected]
                    selectedCourseName = selected
                    selectedCourseId = data?.first ?: ""
                    selectedDepartmentId = data?.second?.first ?: ""
                    selectedDepartmentName = data?.second?.second ?: ""
                    totalCourseSemesters = data?.third ?: 6

                    // Update total semesters based on course
                    totalSemestersInput.setText(totalCourseSemesters.toString())
                    updateBatch()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadTeachers() {
        firestore.collection("teachers")
            .get()
            .addOnSuccessListener { documents ->
                val teacherNames = mutableListOf<String>()
                val teacherMap = mutableMapOf<String, String>()

                documents.forEach { doc ->
                    val name = doc.getString("name") ?: ""
                    val id = doc.id
                    teacherNames.add(name)
                    teacherMap[name] = id
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherNames)
                classTeacherDropdown.setAdapter(adapter)

                classTeacherDropdown.setOnItemClickListener { _, _, position, _ ->
                    val selected = teacherNames[position]
                    selectedClassTeacherName = selected
                    selectedClassTeacherId = teacherMap[selected] ?: ""
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupButtons() {
        btnCreate.setOnClickListener {
            createClass()
        }

        // Update batch when semester or academic year changes
        currentSemesterInput.setOnFocusChangeListener { _, _ -> updateBatch() }
        totalSemestersInput.setOnFocusChangeListener { _, _ -> updateBatch() }
        academicYearInput.setOnFocusChangeListener { _, _ -> updateBatch() }
    }

    private fun createClass() {
        val className = classNameInput.text.toString().trim()
        val maxSize = maxSizeInput.text.toString().toIntOrNull() ?: 60
        val currentSemester = currentSemesterInput.text.toString().toIntOrNull() ?: 1
        val totalSemesters = totalSemestersInput.text.toString().toIntOrNull() ?: 6
        val academicYear = academicYearInput.text.toString().trim()
        val batch = batchInput.text.toString().trim()

        if (className.isEmpty()) {
            Toast.makeText(this, "Please enter class name", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedCourseId.isEmpty()) {
            Toast.makeText(this, "Please select a course", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedClassTeacherId.isEmpty()) {
            Toast.makeText(this, "Please select class teacher", Toast.LENGTH_SHORT).show()
            return
        }

        if (batch.isEmpty()) {
            Toast.makeText(this, "Please enter batch", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        val inviteCode = generateInviteCode()

        val newClass = Class(
            className = className,
            courseId = selectedCourseId,
            courseName = selectedCourseName,
            departmentId = selectedDepartmentId,
            departmentName = selectedDepartmentName,
            classTeacherId = selectedClassTeacherId,
            classTeacherName = selectedClassTeacherName,
            maxSize = maxSize,
            currentSize = 0,
            currentSemester = currentSemester,
            totalSemesters = totalSemesters,
            academicYear = academicYear,
            batch = batch,
            inviteCode = inviteCode,
            isActive = true,
            isCompleted = false,
            canPromote = false,
            resultsReleasedForSemester = 0,
            createdBy = "Admin",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        firestore.collection("classes")
            .add(newClass)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Class created successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnCreate.isEnabled = !show
    }
}
