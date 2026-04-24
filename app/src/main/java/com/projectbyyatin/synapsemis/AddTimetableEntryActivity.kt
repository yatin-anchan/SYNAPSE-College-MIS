package com.projectbyyatin.synapsemis

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R
import java.util.*
import androidx.activity.OnBackPressedCallback


class AddTimetableEntryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    // All UI elements
    private lateinit var etSubject: TextInputEditText
    private lateinit var etSubjectCode: TextInputEditText
    private lateinit var etType: MaterialAutoCompleteTextView
    private lateinit var etFaculty: MaterialAutoCompleteTextView
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    private lateinit var etRoom: TextInputEditText
    private lateinit var etSemester: MaterialAutoCompleteTextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnDelete: MaterialButton

    // Data
    private var departmentId = ""
    private var departmentName = ""
    private var selectedDay = ""
    private var startHour = 9
    private var startMinute = 0
    private var endHour = 10
    private var endMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_timetable_entry)

        // ✅ MODERN BACK HANDLING (Android 13+)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        getIntentData()
        initViews()
        setupDropdowns()
        setupToolbar()
        setupAutocomplete()
        setupTimePickers()
        setupSaveButton()
    }


    private fun getIntentData() {
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""
        selectedDay = intent.getStringExtra("DAY") ?: "Monday"
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        etSubject = findViewById(R.id.et_subject)
        etSubjectCode = findViewById(R.id.et_subject_code)
        etType = findViewById(R.id.etType)
        etFaculty = findViewById(R.id.et_faculty)
        etStartTime = findViewById(R.id.et_start_time)
        etEndTime = findViewById(R.id.et_end_time)
        etRoom = findViewById(R.id.et_room)
        etSemester = findViewById(R.id.etSemester)
        btnSave = findViewById(R.id.btnSave)
        btnDelete = findViewById(R.id.btnDelete)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Add $selectedDay Class"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDropdowns() {
        // Type dropdown
        val typeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            arrayOf("Theory", "Practical", "Lab", "Project", "Tutorial")
        )
        etType.setAdapter(typeAdapter)

        // Semester dropdown
        val semesterAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            arrayOf("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5", "Sem 6", "Sem 7", "Sem 8")
        )
        etSemester.setAdapter(semesterAdapter)
    }

    private fun setupAutocomplete() {
        loadFacultyList()
    }

    private fun loadFacultyList() {
        if (departmentId.isEmpty()) return

        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val facultyNames = mutableListOf<String>()
                documents.documents.forEach { doc ->
                    val name = doc.getString("name") ?: ""
                    if (name.isNotEmpty()) {
                        facultyNames.add(name)
                    }
                }
                if (facultyNames.isNotEmpty()) {
                    etFaculty.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, facultyNames))
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load faculty", Toast.LENGTH_SHORT).show()
            }
    }

    private fun setupTimePickers() {
        etStartTime.setOnClickListener { showTimePicker(true) }
        etEndTime.setOnClickListener { showTimePicker(false) }
    }

    private fun showTimePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        val hour = if (isStart) startHour else endHour
        val minute = if (isStart) startMinute else endMinute

        TimePickerDialog(
            this,
            { _, selectedHour, selectedMinute ->
                if (isStart) {
                    startHour = selectedHour
                    startMinute = selectedMinute
                    etStartTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
                } else {
                    endHour = selectedHour
                    endMinute = selectedMinute
                    etEndTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
                }
            },
            hour,
            minute,
            false // 24-hour format
        ).show()
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            if (validateInputs()) {
                saveTimetableEntry()
            }
        }

        btnDelete.setOnClickListener {
            // Handle delete if editing existing entry
            Toast.makeText(this, "Delete functionality for edit mode", Toast.LENGTH_SHORT).show()
        }
    }

    private fun validateInputs(): Boolean {
        // Subject
        if (etSubject.text.isNullOrBlank()) {
            etSubject.error = "Subject name is required"
            etSubject.requestFocus()
            return false
        }

        // Subject Code
        if (etSubjectCode.text.isNullOrBlank()) {
            etSubjectCode.error = "Subject code is required"
            etSubjectCode.requestFocus()
            return false
        }

        // Type
        if (etType.text.isNullOrBlank()) {
            etType.error = "Type is required"
            etType.requestFocus()
            return false
        }

        // Faculty
        if (etFaculty.text.isNullOrBlank()) {
            etFaculty.error = "Faculty is required"
            etFaculty.requestFocus()
            return false
        }

        // Times
        if (etStartTime.text.isNullOrBlank() || etEndTime.text.isNullOrBlank()) {
            Toast.makeText(this, "Please select start and end times", Toast.LENGTH_SHORT).show()
            return false
        }

        // Semester
        if (etSemester.text.isNullOrBlank()) {
            etSemester.error = "Semester is required"
            etSemester.requestFocus()
            return false
        }

        return true
    }

    private fun saveTimetableEntry() {
        val entry = hashMapOf(
            "departmentId" to departmentId,
            "departmentName" to departmentName,
            "day" to selectedDay,
            "subject" to etSubject.text.toString().trim(),
            "subjectCode" to etSubjectCode.text.toString().trim().uppercase(),
            "type" to etType.text.toString(),
            "facultyName" to etFaculty.text.toString(),
            "startTime" to etStartTime.text.toString(),
            "endTime" to etEndTime.text.toString(),
            "room" to etRoom.text.toString().trim().ifEmpty { "TBD" },
            "semester" to etSemester.text.toString(),
            "isActive" to true,
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("timetable")
            .add(entry)
            .addOnSuccessListener {
                Toast.makeText(this, "✅ Class scheduled successfully!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "❌ Failed to save: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}
