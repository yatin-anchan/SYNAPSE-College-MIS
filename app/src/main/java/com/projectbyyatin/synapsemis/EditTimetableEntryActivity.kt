package com.projectbyyatin.synapsemis

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.models.TimetableEntry
import java.util.*

class EditTimetableEntryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var firestore: FirebaseFirestore

    private lateinit var etSubject: TextInputEditText
    private lateinit var etSubjectCode: TextInputEditText
    private lateinit var etFaculty: MaterialAutoCompleteTextView
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etEndTime: TextInputEditText
    private lateinit var etRoom: TextInputEditText
    private lateinit var etSemester: MaterialAutoCompleteTextView
    private lateinit var btnUpdate: MaterialButton
    private lateinit var btnDelete: MaterialButton

    private var entryId = ""
    private var departmentId = ""
    private lateinit var originalEntry: TimetableEntry
    private var startHour = 9
    private var startMinute = 0
    private var endHour = 10
    private var endMinute = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_timetable_entry)

        getIntentData()
        initViews()
        setupToolbar()
        loadEntry()
        setupAutocomplete()
        setupTimePickers()
        setupButtons()
    }

    private fun getIntentData() {
        entryId = intent.getStringExtra("ENTRY_ID") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        firestore = FirebaseFirestore.getInstance()

        etSubject = findViewById(R.id.et_subject)
        etSubjectCode = findViewById(R.id.et_subject_code)
        etFaculty = findViewById(R.id.et_faculty)
        etStartTime = findViewById(R.id.et_start_time)
        etEndTime = findViewById(R.id.et_end_time)
        etRoom = findViewById(R.id.et_room)
        etSemester = findViewById(R.id.et_semester)
        btnUpdate = findViewById(R.id.btn_update)
        btnDelete = findViewById(R.id.btn_delete)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Edit Class"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadEntry() {
        firestore.collection("timetable").document(entryId)
            .get()
            .addOnSuccessListener { doc ->
                originalEntry = doc.toObject(TimetableEntry::class.java) ?: return@addOnSuccessListener
                populateFields()
            }
    }

    private fun populateFields() {
        etSubject.setText(originalEntry.subject)
        etSubjectCode.setText(originalEntry.subjectCode)
        etFaculty.setText(originalEntry.facultyName)
        etStartTime.setText(originalEntry.startTime)
        etEndTime.setText(originalEntry.endTime)
        etRoom.setText(originalEntry.room)
        etSemester.setText(originalEntry.semester)

        // Parse times for picker
        parseTime(originalEntry.startTime, true)
        parseTime(originalEntry.endTime, false)
    }

    private fun parseTime(time: String, isStart: Boolean) {
        val parts = time.split(":")
        if (parts.size == 2) {
            val hour = parts[0].toIntOrNull() ?: 9
            val minute = parts[1].toIntOrNull() ?: 0
            if (isStart) {
                startHour = hour
                startMinute = minute
            } else {
                endHour = hour
                endMinute = minute
            }
        }
    }

    private fun setupAutocomplete() {
        val semesters = listOf("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5", "Sem 6")
        etSemester.setAdapter(ArrayAdapter(this, R.layout.dropdown_item, semesters))

        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val facultyNames = mutableListOf<String>()
                documents.documents.forEach { doc ->
                    doc.getString("name")?.let { facultyNames.add(it) }
                }
                etFaculty.setAdapter(ArrayAdapter(this, R.layout.dropdown_item, facultyNames))
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

        TimePickerDialog(this, { _, selectedHour, selectedMinute ->
            if (isStart) {
                startHour = selectedHour
                startMinute = selectedMinute
                etStartTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            } else {
                endHour = selectedHour
                endMinute = selectedMinute
                etEndTime.setText(String.format("%02d:%02d", selectedHour, selectedMinute))
            }
        }, hour, minute, true).show()
    }

    private fun setupButtons() {
        btnUpdate.setOnClickListener { updateEntry() }
        btnDelete.setOnClickListener { showDeleteDialog() }
    }

    private fun updateEntry() {
        if (etSubject.text.isNullOrBlank()) {
            etSubject.error = "Subject required"
            return
        }

        val updatedEntry = mapOf(
            "subject" to etSubject.text.toString(),
            "subjectCode" to etSubjectCode.text.toString(),
            "facultyName" to etFaculty.text.toString(),
            "startTime" to etStartTime.text.toString(),
            "endTime" to etEndTime.text.toString(),
            "room" to etRoom.text.toString().ifEmpty { "TBD" },
            "semester" to etSemester.text.toString()
        )

        firestore.collection("timetable").document(entryId)
            .update(updatedEntry)
            .addOnSuccessListener {
                Toast.makeText(this, "Class updated", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete Class")
            .setMessage("Remove this class from timetable?")
            .setPositiveButton("Delete") { _, _ -> deleteEntry() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEntry() {
        firestore.collection("timetable").document(entryId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "Class deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
    }
}
