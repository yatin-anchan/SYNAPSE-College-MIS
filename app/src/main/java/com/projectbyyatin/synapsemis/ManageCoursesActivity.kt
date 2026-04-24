package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.CoursesAdapter
import com.projectbyyatin.synapsemis.models.Course
import com.projectbyyatin.synapsemis.models.Class
import java.util.Calendar
import kotlin.random.Random

class ManageCoursesActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: CoursesAdapter
    private var coursesList = mutableListOf<Course>()

    private var departmentId: String = ""
    private var departmentName: String = ""
    private var college: String = ""
    private var stream: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_courses)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""
        college = intent.getStringExtra("COLLEGE") ?: ""
        stream = intent.getStringExtra("STREAM") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadCourses()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        fab = findViewById(R.id.fab)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Courses"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = CoursesAdapter(
            coursesList,
            onViewClick = { course -> viewCourseSemesters(course) },
            onEditClick = { course -> editCourse(course) },
            onDeleteClick = { course -> showDeleteConfirmation(course) },
            onToggleActive = { course, isActive -> toggleCourseActive(course, isActive) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        fab.setOnClickListener {
            showAddCourseDialog()
        }
    }

    private fun loadCourses() {
        showLoading(true)

        firestore.collection("courses")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                coursesList.clear()

                documents.forEach { document ->
                    val course = document.toObject(Course::class.java)
                    course.id = document.id
                    coursesList.add(course)
                }

                adapter.updateList(coursesList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddCourseDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_course, null)
        val courseNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.course_name_input)
        val courseCodeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.course_code_input)
        val durationInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.duration_input)
        val semestersInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.semesters_input)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Add Course")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = courseNameInput.text.toString().trim()
                val code = courseCodeInput.text.toString().trim()
                val duration = durationInput.text.toString().trim()
                val semesters = semestersInput.text.toString().trim().toIntOrNull() ?: 6

                if (name.isNotEmpty() && code.isNotEmpty() && duration.isNotEmpty()) {
                    // Extract years from duration (e.g., "3 Years" -> 3)
                    val durationYears = duration.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 3
                    addCourse(name, code, duration, semesters, durationYears)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addCourse(name: String, code: String, duration: String, semesters: Int, durationYears: Int) {
        showLoading(true)

        val courseId = firestore.collection("courses").document().id
        val course = hashMapOf(
            "name" to name,
            "code" to code,
            "duration" to duration,
            "totalSemesters" to semesters,
            "departmentId" to departmentId,
            "department" to departmentName,
            "college" to college,
            "stream" to stream,
            "isActive" to true,
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("courses").document(courseId)
            .set(course)
            .addOnSuccessListener {
                // Create semesters automatically
                createSemestersForCourse(courseId, name, semesters)

                // Create classes based on duration (FY, SY, TY, etc.)
                createClassesForCourse(courseId, name, durationYears, semesters)

                Toast.makeText(this, "Course added with $semesters semesters and $durationYears classes", Toast.LENGTH_SHORT).show()
                loadCourses()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createSemestersForCourse(courseId: String, courseName: String, totalSemesters: Int) {
        val batch = firestore.batch()

        for (i in 1..totalSemesters) {
            val semesterId = firestore.collection("semesters").document().id
            val semester = hashMapOf(
                "semesterNumber" to i,
                "semesterName" to "Semester $i",
                "courseId" to courseId,
                "courseName" to courseName,
                "departmentId" to departmentId,
                "department" to departmentName,
                "totalSubjects" to 0,
                "isActive" to true,
                "createdAt" to System.currentTimeMillis()
            )

            batch.set(firestore.collection("semesters").document(semesterId), semester)
        }

        batch.commit()
    }

    private fun createClassesForCourse(courseId: String, courseName: String, durationYears: Int, totalSemesters: Int) {
        val batch = firestore.batch()
        val yearNames = listOf("FY", "SY", "TY", "Fourth Year", "Fifth Year")

        // Get current academic year
        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR)
        val currentMonth = calendar.get(Calendar.MONTH)
        val academicYear = if (currentMonth >= 6) {
            "$currentYear-${currentYear + 1}"
        } else {
            "${currentYear - 1}-$currentYear"
        }

        val startYear = academicYear.split("-")[0].toIntOrNull() ?: currentYear
        val endYear = startYear + durationYears
        val batchYear = "$startYear-$endYear"

        for (year in 1..durationYears) {
            val classId = firestore.collection("classes").document().id
            val className = if (year <= yearNames.size) {
                "${yearNames[year - 1]} ${courseName}"
            } else {
                "Year $year ${courseName}"
            }

            // Calculate which semester this year starts with (2 semesters per year)
            val startingSemester = (year - 1) * 2 + 1

            val newClass = Class(
                id = classId,
                className = className,
                courseId = courseId,
                courseName = courseName,
                departmentId = departmentId,
                departmentName = departmentName,
                classTeacherId = "",
                classTeacherName = "",
                maxSize = 60,
                currentSize = 0,
                currentSemester = startingSemester,
                totalSemesters = totalSemesters,
                academicYear = academicYear,
                batch = batchYear,
                inviteCode = generateInviteCode(),
                isActive = true,
                isCompleted = false,
                canPromote = false,
                resultsReleasedForSemester = 0,
                createdBy = "System",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )

            batch.set(firestore.collection("classes").document(classId), newClass)
        }

        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Created $durationYears classes successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error creating classes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun generateInviteCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        return (1..8)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    private fun viewCourseSemesters(course: Course) {
        val intent = Intent(this, ManageSemestersActivity::class.java)
        intent.putExtra("COURSE_ID", course.id)
        intent.putExtra("COURSE_NAME", course.name)
        intent.putExtra("DEPARTMENT_ID", departmentId)
        intent.putExtra("DEPARTMENT_NAME", departmentName)
        startActivity(intent)
    }

    private fun editCourse(course: Course) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_course, null)
        val courseNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.course_name_input)
        val courseCodeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.course_code_input)
        val durationInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.duration_input)
        val semestersInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.semesters_input)

        // Pre-fill data
        courseNameInput.setText(course.name)
        courseCodeInput.setText(course.code)
        durationInput.setText(course.duration)
        semestersInput.setText(course.totalSemesters.toString())

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Edit Course")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = courseNameInput.text.toString().trim()
                val code = courseCodeInput.text.toString().trim()
                val duration = durationInput.text.toString().trim()
                val semesters = semestersInput.text.toString().trim().toIntOrNull() ?: course.totalSemesters

                if (name.isNotEmpty() && code.isNotEmpty()) {
                    updateCourse(course.id, name, code, duration, semesters)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateCourse(courseId: String, name: String, code: String, duration: String, semesters: Int) {
        showLoading(true)

        firestore.collection("courses").document(courseId)
            .update(mapOf(
                "name" to name,
                "code" to code,
                "duration" to duration,
                "totalSemesters" to semesters,
                "updatedAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Course updated", Toast.LENGTH_SHORT).show()
                loadCourses()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleCourseActive(course: Course, isActive: Boolean) {
        firestore.collection("courses").document(course.id)
            .update("isActive", isActive)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Course ${if (isActive) "activated" else "deactivated"}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                loadCourses() // Reload to reset switch
            }
    }

    private fun showDeleteConfirmation(course: Course) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Course")
            .setMessage("Delete ${course.name}?\n\nThis will permanently remove:\n• All semesters\n• All subjects\n• All classes\n• Faculty assignments")
            .setPositiveButton("Delete") { _, _ ->
                deleteCourse(course)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCourse(course: Course) {
        showLoading(true)

        // Delete course
        firestore.collection("courses").document(course.id)
            .delete()
            .addOnSuccessListener {
                deleteSemestersAndSubjects(course.id)
                deleteClasses(course.id)
                Toast.makeText(this, "Course deleted", Toast.LENGTH_SHORT).show()
                loadCourses()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteSemestersAndSubjects(courseId: String) {
        // Delete semesters
        firestore.collection("semesters")
            .whereEqualTo("courseId", courseId)
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { it.reference.delete() }
            }

        // Delete subjects
        firestore.collection("subjects")
            .whereEqualTo("courseId", courseId)
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { it.reference.delete() }
            }
    }

    private fun deleteClasses(courseId: String) {
        // Delete all classes associated with this course
        firestore.collection("classes")
            .whereEqualTo("courseId", courseId)
            .get()
            .addOnSuccessListener { documents ->
                documents.forEach { it.reference.delete() }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting classes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (coursesList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadCourses()
    }
}
