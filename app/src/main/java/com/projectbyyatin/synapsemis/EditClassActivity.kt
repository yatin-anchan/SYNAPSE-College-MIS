package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Class

class EditClassActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var classNameInput: TextInputEditText
    private lateinit var courseDropdown: AutoCompleteTextView
    private lateinit var classTeacherDropdown: AutoCompleteTextView
    private lateinit var maxSizeInput: TextInputEditText
    private lateinit var currentSemesterInput: TextInputEditText
    private lateinit var academicYearInput: TextInputEditText
    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private var classId: String = ""
    private var classData: Class? = null

    private var selectedCourseId: String = ""
    private var selectedCourseName: String = ""
    private var selectedDepartmentId: String = ""
    private var selectedDepartmentName: String = ""
    private var selectedClassTeacherId: String = ""
    private var selectedClassTeacherName: String = ""
    private var selectedClassTeacherEmail: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_class)

        classId = intent.getStringExtra("CLASS_ID") ?: ""

        if (classId.isEmpty()) {
            Toast.makeText(this, "Error: Class ID not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        loadClassData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        classNameInput = findViewById(R.id.class_name_input)
        courseDropdown = findViewById(R.id.course_dropdown)
        classTeacherDropdown = findViewById(R.id.class_teacher_dropdown)
        maxSizeInput = findViewById(R.id.max_size_input)
        currentSemesterInput = findViewById(R.id.current_semester_input)
        academicYearInput = findViewById(R.id.academic_year_input)
        btnUpdate = findViewById(R.id.btn_update)
        btnDelete = findViewById(R.id.btn_delete)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Class"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadClassData() {
        showLoading(true)

        firestore.collection("classes").document(classId)
            .get()
            .addOnSuccessListener { document ->
                classData = document.toObject(Class::class.java)
                classData?.let {
                    it.id = document.id
                    populateFields(it)
                    loadCourses()
                    loadTeachers(it.departmentId)
                    setupButtons()
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading data: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun populateFields(classItem: Class) {
        classNameInput.setText(classItem.className)
        maxSizeInput.setText(classItem.maxSize.toString())
        currentSemesterInput.setText(classItem.currentSemester.toString())
        academicYearInput.setText(classItem.academicYear)

        // Store existing selections
        selectedCourseId = classItem.courseId
        selectedCourseName = classItem.courseName
        selectedDepartmentId = classItem.departmentId
        selectedDepartmentName = classItem.departmentName
        selectedClassTeacherId = classItem.classTeacherId
        selectedClassTeacherName = classItem.classTeacherName

        Log.d("EditClass", "Current department: $selectedDepartmentId")
        Log.d("EditClass", "Current teacher: $selectedClassTeacherName")
    }

    private fun loadCourses() {
        firestore.collection("courses")
            .get()
            .addOnSuccessListener { documents ->
                val courseNames = mutableListOf<String>()
                val courseMap = mutableMapOf<String, Pair<String, Pair<String, String>>>()

                documents.forEach { doc ->
                    val courseName = doc.getString("courseName") ?: ""
                    val courseId = doc.id
                    val deptId = doc.getString("departmentId") ?: ""
                    val deptName = doc.getString("department") ?: doc.getString("departmentName") ?: ""

                    if (courseName.isNotEmpty()) {
                        courseNames.add(courseName)
                        courseMap[courseName] = Pair(courseId, Pair(deptId, deptName))
                    }
                }

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, courseNames.sorted())
                courseDropdown.setAdapter(adapter)
                courseDropdown.setText(selectedCourseName, false)

                courseDropdown.setOnItemClickListener { _, _, position, _ ->
                    val sortedNames = courseNames.sorted()
                    val selected = sortedNames[position]
                    val data = courseMap[selected]
                    selectedCourseName = selected
                    selectedCourseId = data?.first ?: ""
                    selectedDepartmentId = data?.second?.first ?: ""
                    selectedDepartmentName = data?.second?.second ?: ""

                    Log.d("EditClass", "Selected course: $selected, Dept: $selectedDepartmentName")

                    // Reload teachers for new department
                    loadTeachers(selectedDepartmentId)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading courses: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditClass", "Error loading courses", e)
            }
    }

    private fun loadTeachers(departmentId: String) {
        Log.d("EditClass", "Loading teachers for department: $departmentId")

        classTeacherDropdown.setText("Loading...")

        if (departmentId.isEmpty()) {
            Toast.makeText(this, "Department not selected. Showing all faculty.", Toast.LENGTH_SHORT).show()
            loadAllTeachers()
            return
        }

        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()  // REMOVED: .whereEqualTo("isActive", true)
            .addOnSuccessListener { documents ->
                Log.d("EditClass", "Found ${documents.size()} faculty members")

                // Filter active faculty in code
                val activeFaculty = documents.filter { doc ->
                    doc.getBoolean("active") == true  // CHANGED: using "active" field
                }

                if (activeFaculty.isEmpty()) {
                    classTeacherDropdown.setText("")
                    Toast.makeText(this, "No active faculty in this department. Showing all faculty.", Toast.LENGTH_LONG).show()
                    loadAllTeachers()
                    return@addOnSuccessListener
                }

                val teacherNames = mutableListOf<String>()
                val teacherMap = mutableMapOf<String, Pair<String, String>>()

                activeFaculty.forEach { doc ->
                    val name = doc.getString("name") ?: ""
                    val email = doc.getString("email") ?: ""
                    val id = doc.id

                    if (name.isNotEmpty()) {
                        teacherNames.add(name)
                        teacherMap[name] = Pair(id, email)
                        Log.d("EditClass", "Faculty: $name (ID: $id)")
                    }
                }

                classTeacherDropdown.setText("")

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherNames.sorted())
                classTeacherDropdown.setAdapter(adapter)
                classTeacherDropdown.setText(selectedClassTeacherName, false)
                classTeacherDropdown.threshold = 1

                classTeacherDropdown.setOnItemClickListener { _, _, position, _ ->
                    val sortedNames = teacherNames.sorted()
                    val selected = sortedNames[position]
                    val data = teacherMap[selected]
                    selectedClassTeacherName = selected
                    selectedClassTeacherId = data?.first ?: ""
                    selectedClassTeacherEmail = data?.second ?: ""
                    Log.d("EditClass", "Selected teacher: $selected (ID: $selectedClassTeacherId)")
                }

                classTeacherDropdown.setOnClickListener {
                    classTeacherDropdown.showDropDown()
                }
            }
            .addOnFailureListener { e ->
                classTeacherDropdown.setText("")
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditClass", "Error loading teachers", e)
                loadAllTeachers()
            }
    }

    private fun loadAllTeachers() {
        Log.d("EditClass", "Loading all active faculty as fallback")

        firestore.collection("faculty")
            .get()  // REMOVED: .whereEqualTo("isActive", true)
            .addOnSuccessListener { documents ->
                val teacherNames = mutableListOf<String>()
                val teacherMap = mutableMapOf<String, Pair<String, String>>()

                // Filter active faculty in code
                documents.forEach { doc ->
                    val isActive = doc.getBoolean("active") ?: false  // CHANGED: using "active"
                    val name = doc.getString("name") ?: ""
                    val email = doc.getString("email") ?: ""
                    val id = doc.id

                    if (isActive && name.isNotEmpty()) {
                        teacherNames.add(name)
                        teacherMap[name] = Pair(id, email)
                    }
                }

                Log.d("EditClass", "Found ${teacherNames.size} total active faculty")

                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, teacherNames.sorted())
                classTeacherDropdown.setAdapter(adapter)
                classTeacherDropdown.setText(selectedClassTeacherName, false)
                classTeacherDropdown.threshold = 1

                classTeacherDropdown.setOnItemClickListener { _, _, position, _ ->
                    val sortedNames = teacherNames.sorted()
                    val selected = sortedNames[position]
                    val data = teacherMap[selected]
                    selectedClassTeacherName = selected
                    selectedClassTeacherId = data?.first ?: ""
                    selectedClassTeacherEmail = data?.second ?: ""
                }

                classTeacherDropdown.setOnClickListener {
                    classTeacherDropdown.showDropDown()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditClass", "Error loading all faculty", e)
            }
    }


    private fun setupButtons() {
        btnUpdate.setOnClickListener {
            updateClass()
        }

        btnDelete.setOnClickListener {
            showDeleteConfirmation()
        }
    }

    private fun updateClass() {
        val className = classNameInput.text.toString().trim()
        val maxSize = maxSizeInput.text.toString().toIntOrNull() ?: 60
        val currentSemester = currentSemesterInput.text.toString().toIntOrNull() ?: 1
        val academicYear = academicYearInput.text.toString().trim()

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

        if (academicYear.isEmpty()) {
            Toast.makeText(this, "Please enter academic year", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)

        val updates = hashMapOf<String, Any>(
            "className" to className,
            "courseId" to selectedCourseId,
            "courseName" to selectedCourseName,
            "departmentId" to selectedDepartmentId,
            "departmentName" to selectedDepartmentName,
            "classTeacherId" to selectedClassTeacherId,
            "classTeacherName" to selectedClassTeacherName,
            "classTeacherEmail" to selectedClassTeacherEmail,
            "maxSize" to maxSize,
            "currentSemester" to currentSemester,
            "academicYear" to academicYear,
            "updatedAt" to System.currentTimeMillis()
        )

        Log.d("EditClass", "Updating class with: $updates")

        firestore.collection("classes").document(classId)
            .update(updates)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Class updated successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditClass", "Error updating class", e)
            }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Class")
            .setMessage("Are you sure you want to delete this class?\n\nThis will:\n• Mark the class as inactive\n• Keep student records\n• Keep attendance records\n\nThis action can be reversed by reactivating the class.")
            .setPositiveButton("Delete") { _, _ ->
                deleteClass()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteClass() {
        showLoading(true)

        // Set class as inactive instead of deleting
        firestore.collection("classes").document(classId)
            .update(mapOf(
                "isActive" to false,
                "deletedAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Class marked as inactive", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("EditClass", "Error deleting class", e)
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnUpdate.isEnabled = !show
        btnDelete.isEnabled = !show
    }
}
