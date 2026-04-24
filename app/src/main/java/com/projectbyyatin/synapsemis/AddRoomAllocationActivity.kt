package com.projectbyyatin.synapsemis

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Faculty
import com.projectbyyatin.synapsemis.models.ClassInfo

class AddRoomAllocationActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var roomNoInput: TextInputEditText
    private lateinit var teacherSearchInput: AutoCompleteTextView
    private lateinit var classDropdown: AutoCompleteTextView
    private lateinit var presentRadio: RadioButton
    private lateinit var absentRadio: RadioButton
    private lateinit var btnSave: MaterialButton
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore

    private var facultyId = ""
    private var sessionDate: Long = 0
    private var sessionTimeHour = 0
    private var sessionTimeMinute = 0

    private var editMode = false
    private var editPosition = -1

    private var selectedTeacherId = ""
    private var selectedTeacherName = ""
    private var selectedClassId = ""
    private var selectedClassName = ""

    private val allTeachers = mutableListOf<Faculty>()
    private val filteredTeachers = mutableListOf<Faculty>()
    private val allClasses = mutableListOf<ClassInfo>()
    private val assignedTeacherIds = mutableListOf<String>()

    private lateinit var teacherAdapter: ArrayAdapter<String>
    private lateinit var classAdapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_room_allocation)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        sessionDate = intent.getLongExtra("SESSION_DATE", 0)
        sessionTimeHour = intent.getIntExtra("SESSION_TIME_HOUR", 0)
        sessionTimeMinute = intent.getIntExtra("SESSION_TIME_MINUTE", 0)

        editMode = intent.getBooleanExtra("EDIT_MODE", false)
        editPosition = intent.getIntExtra("EDIT_POSITION", -1)

        intent.getStringArrayListExtra("ASSIGNED_TEACHERS")?.let {
            assignedTeacherIds.addAll(it)
        }

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupListeners()

        if (editMode) {
            loadEditData()
        }

        loadTeachers()
        loadClasses()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        roomNoInput = findViewById(R.id.room_no_input)
        teacherSearchInput = findViewById(R.id.teacher_search_input)
        classDropdown = findViewById(R.id.class_spinner)
        presentRadio = findViewById(R.id.present_radio)
        absentRadio = findViewById(R.id.absent_radio)
        btnSave = findViewById(R.id.btn_save)
        loadingProgress = findViewById(R.id.loading_progress)

        // Default to present
        presentRadio.isChecked = true
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = if (editMode) "Edit Room Allocation" else "Add Room Allocation"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        // Teacher search with autocomplete
        teacherAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf<String>()
        )
        teacherSearchInput.setAdapter(teacherAdapter)
        teacherSearchInput.threshold = 1

        teacherSearchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterTeachers(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        teacherSearchInput.setOnItemClickListener { parent, _, position, _ ->
            val teacherName = parent.getItemAtPosition(position) as String
            val teacher = filteredTeachers.find {
                "${it.name} (${it.department})" == teacherName
            }
            teacher?.let {
                selectedTeacherId = it.id
                selectedTeacherName = it.name
            }
        }

        // Class dropdown
        classAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf<String>()
        )
        classDropdown.setAdapter(classAdapter)
        classDropdown.threshold = 1000 // Don't filter, just show all

        classDropdown.setOnItemClickListener { parent, _, position, _ ->
            val className = parent.getItemAtPosition(position) as String
            if (position > 0 && position <= allClasses.size) {
                val classInfo = allClasses[position - 1]
                selectedClassId = classInfo.id
                selectedClassName = classInfo.className
            }
        }

        btnSave.setOnClickListener { validateAndSave() }
    }

    private fun loadEditData() {
        roomNoInput.setText(intent.getStringExtra("ROOM_NO"))
        selectedTeacherId = intent.getStringExtra("TEACHER_ID") ?: ""
        selectedTeacherName = intent.getStringExtra("TEACHER_NAME") ?: ""
        selectedClassId = intent.getStringExtra("CLASS_ID") ?: ""
        selectedClassName = intent.getStringExtra("CLASS_NAME") ?: ""

        val isPresent = intent.getBooleanExtra("IS_PRESENT", true)
        if (isPresent) {
            presentRadio.isChecked = true
        } else {
            absentRadio.isChecked = true
        }

        teacherSearchInput.setText("$selectedTeacherName")
    }

    private fun loadTeachers() {
        showLoading(true)

        // Load all faculty from all departments
        firestore.collection("faculty")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                allTeachers.clear()

                documents.forEach { doc ->
                    val teacher = doc.toObject(Faculty::class.java).apply {
                        id = doc.id
                    }

                    // Exclude teachers already assigned in this session
                    if (!assignedTeacherIds.contains(teacher.id) || teacher.id == selectedTeacherId) {
                        allTeachers.add(teacher)
                    }
                }

                // Sort by name
                allTeachers.sortBy { it.name }
                filteredTeachers.addAll(allTeachers)

                updateTeacherDropdown()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading teachers: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterTeachers(query: String) {
        filteredTeachers.clear()

        if (query.isEmpty()) {
            filteredTeachers.addAll(allTeachers)
        } else {
            val lowerQuery = query.lowercase()
            filteredTeachers.addAll(
                allTeachers.filter {
                    it.name.lowercase().contains(lowerQuery) ||
                            it.department.lowercase().contains(lowerQuery) ||
                            it.employeeId.lowercase().contains(lowerQuery)
                }
            )
        }

        updateTeacherDropdown()
    }

    private fun updateTeacherDropdown() {
        val displayList = filteredTeachers.map {
            "${it.name} (${it.department})"
        }

        teacherAdapter.clear()
        teacherAdapter.addAll(displayList)
        teacherAdapter.notifyDataSetChanged()
    }

    private fun loadClasses() {
        // Load all classes from all departments
        firestore.collection("classes")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                allClasses.clear()

                documents.forEach { doc ->
                    val classInfo = ClassInfo(
                        id = doc.id,
                        className = doc.getString("className") ?: "",
                        departmentId = doc.getString("departmentId") ?: "",
                        departmentName = doc.getString("departmentName") ?: "",
                        courseId = doc.getString("courseId") ?: "",
                        courseName = doc.getString("courseName") ?: "",
                        semester = doc.getLong("semester")?.toInt() ?: 0,
                        section = doc.getString("section") ?: ""
                    )
                    allClasses.add(classInfo)
                }

                // Sort by course, semester, section
                allClasses.sortWith(compareBy({ it.courseName }, { it.semester }, { it.section }))

                updateClassDropdown()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading classes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateClassDropdown() {
        val displayList = mutableListOf("Select Class")
        displayList.addAll(
            allClasses.map {
                "${it.courseName} - Sem ${it.semester} ${it.section} (${it.departmentName})"
            }
        )

        classAdapter.clear()
        classAdapter.addAll(displayList)
        classAdapter.notifyDataSetChanged()

        // Set selection if in edit mode
        if (editMode && selectedClassId.isNotEmpty()) {
            val position = allClasses.indexOfFirst { it.id == selectedClassId }
            if (position != -1) {
                classDropdown.setText(displayList[position + 1], false)
            }
        }
    }

    private fun validateAndSave() {
        val roomNo = roomNoInput.text.toString().trim()

        // Validation
        if (roomNo.isEmpty()) {
            roomNoInput.error = "Enter room number"
            roomNoInput.requestFocus()
            return
        }

        if (selectedTeacherId.isEmpty()) {
            Toast.makeText(this, "Please select a teacher", Toast.LENGTH_SHORT).show()
            teacherSearchInput.requestFocus()
            return
        }

        if (selectedClassId.isEmpty()) {
            Toast.makeText(this, "Please select a class", Toast.LENGTH_SHORT).show()
            return
        }

        val isPresent = presentRadio.isChecked

        // Return result
        val resultIntent = Intent().apply {
            putExtra("ROOM_NO", roomNo)
            putExtra("TEACHER_ID", selectedTeacherId)
            putExtra("TEACHER_NAME", selectedTeacherName)
            putExtra("CLASS_ID", selectedClassId)
            putExtra("CLASS_NAME", selectedClassName)
            putExtra("IS_PRESENT", isPresent)
            if (editMode) {
                putExtra("EDIT_POSITION", editPosition)
            }
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        btnSave.isEnabled = !show
    }
}