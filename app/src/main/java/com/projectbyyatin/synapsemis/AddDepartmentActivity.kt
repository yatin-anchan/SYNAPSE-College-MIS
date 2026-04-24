package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import java.text.SimpleDateFormat
import java.util.*

class AddDepartmentActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvCreatedDate: TextView
    private lateinit var etDepartmentName: TextInputEditText
    private lateinit var etDepartmentId: TextInputEditText
    private lateinit var tilDepartmentId: TextInputLayout
    private lateinit var cbAutoGenerateId: CheckBox
    private lateinit var spinnerCollegeLevel: Spinner
    private lateinit var spinnerStream: Spinner
    private lateinit var spinnerHOD: Spinner
    private lateinit var btnSaveDepartment: Button

    private lateinit var firestore: FirebaseFirestore
    private var selectedDate: Long = System.currentTimeMillis()
    private var selectedCollege: String = ""
    private var selectedStream: String = ""
    private var selectedHOD: String = ""
    private var selectedHODId: String = ""
    private var hodList = mutableListOf<Pair<String, String>>() // Pair<Name, ID>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_department)

        initializeViews()
        setupToolbar()
        setupDatePicker()
        setupAutoGenerateCheckbox()
        setupCollegeLevelSpinner()
        setupStreamSpinner()
        loadHODList()
        setupSaveButton()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        tvCreatedDate = findViewById(R.id.tv_created_date)
        etDepartmentName = findViewById(R.id.et_department_name)
        etDepartmentId = findViewById(R.id.et_department_id)
        tilDepartmentId = findViewById(R.id.til_department_id)
        cbAutoGenerateId = findViewById(R.id.cb_auto_generate_id)
        spinnerCollegeLevel = findViewById(R.id.spinner_college_level)
        spinnerStream = findViewById(R.id.spinner_stream)
        spinnerHOD = findViewById(R.id.spinner_hod)
        btnSaveDepartment = findViewById(R.id.btn_save_department)

        firestore = FirebaseFirestore.getInstance()
        updateDateDisplay()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Add Department"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDatePicker() {
        findViewById<View>(R.id.date_picker_card).setOnClickListener {
            try {
                val datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Creation Date")
                    .setSelection(selectedDate)
                    .build()

                datePicker.addOnPositiveButtonClickListener { selection ->
                    selectedDate = selection
                    updateDateDisplay()
                }

                datePicker.show(supportFragmentManager, "DATE_PICKER")
            } catch (e: Exception) {
                Toast.makeText(this, "Error opening date picker: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val date = Date(selectedDate)
        tvCreatedDate.text = dateFormat.format(date)
    }

    private fun setupAutoGenerateCheckbox() {
        cbAutoGenerateId.setOnCheckedChangeListener { _, isChecked ->
            etDepartmentId.isEnabled = !isChecked
            tilDepartmentId.isEnabled = !isChecked
            if (isChecked) etDepartmentId.setText("")
        }
    }

    private fun setupCollegeLevelSpinner() {
        val collegeLevels = listOf("Select College Level", "Junior College (JR)", "Senior College (SR)")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, collegeLevels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCollegeLevel.adapter = adapter

        spinnerCollegeLevel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedCollege = when (position) {
                    1 -> "JR"
                    2 -> "SR"
                    else -> ""
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { selectedCollege = "" }
        }
    }

    private fun setupStreamSpinner() {
        val streams = listOf("Select Stream", "Science", "Commerce", "Arts")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, streams)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerStream.adapter = adapter

        spinnerStream.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStream = if (position > 0) streams[position] else ""
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { selectedStream = "" }
        }
    }

    private fun loadHODList() {
        hodList.clear()
        hodList.add(Pair("None", ""))

        firestore.collection("faculty")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val name = document.getString("name") ?: ""
                    val id = document.id
                    if (name.isNotEmpty()) {
                        hodList.add(Pair(name, id))
                    }
                }
                setupHODSpinner()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
                setupHODSpinner()
            }
    }

    private fun setupHODSpinner() {
        val hodNames = hodList.map { it.first }

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, hodNames)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerHOD.adapter = adapter

        spinnerHOD.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    selectedHOD = ""
                    selectedHODId = ""
                } else {
                    selectedHOD = hodList[position].first
                    selectedHODId = hodList[position].second
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedHOD = ""
                selectedHODId = ""
            }
        }
    }

    private fun setupSaveButton() {
        btnSaveDepartment.setOnClickListener {
            if (validateInputs()) {
                if (cbAutoGenerateId.isChecked) {
                    generateDepartmentId()
                } else {
                    saveDepartment()
                }
            }
        }
    }

    private fun validateInputs(): Boolean {
        val departmentName = etDepartmentName.text.toString().trim()

        if (departmentName.isEmpty()) {
            etDepartmentName.error = "Department name is required"
            etDepartmentName.requestFocus()
            return false
        }

        if (!cbAutoGenerateId.isChecked) {
            val departmentId = etDepartmentId.text.toString().trim()
            if (departmentId.isEmpty()) {
                etDepartmentId.error = "Department ID is required"
                etDepartmentId.requestFocus()
                return false
            }
        }

        if (selectedCollege.isEmpty()) {
            Toast.makeText(this, "Please select college level", Toast.LENGTH_SHORT).show()
            return false
        }

        if (selectedStream.isEmpty()) {
            Toast.makeText(this, "Please select stream", Toast.LENGTH_SHORT).show()
            return false
        }

        return true
    }

    private fun generateDepartmentId() {
        val collegePrefix = selectedCollege.take(2).uppercase()
        val streamPrefix = selectedStream.take(3).uppercase()

        firestore.collection("departments")
            .whereEqualTo("college", selectedCollege)
            .whereEqualTo("stream", selectedStream)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size() + 1
                val generatedId = "DEPT-$collegePrefix$streamPrefix-${String.format("%03d", count)}"
                etDepartmentId.setText(generatedId)
                saveDepartment()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error generating ID: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun saveDepartment() {
        val departmentName = etDepartmentName.text.toString().trim()
        val departmentId = etDepartmentId.text.toString().trim()

        firestore.collection("departments")
            .whereEqualTo("code", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    createDepartment(departmentName, departmentId)
                } else {
                    Toast.makeText(this, "Department ID already exists", Toast.LENGTH_SHORT).show()
                    etDepartmentId.error = "This ID is already in use"
                    etDepartmentId.requestFocus()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error checking department ID: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun createDepartment(name: String, code: String) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val createdDate = dateFormat.format(Date(selectedDate))

        // First, create the department to get its document ID
        val departmentRef = firestore.collection("departments").document()

        val department = hashMapOf(
            "name" to name,
            "code" to code,
            "college" to selectedCollege,
            "stream" to selectedStream,
            "hod" to selectedHOD,
            "hodId" to selectedHODId,
            "hodEmail" to "",
            "hodPhone" to "",
            "description" to "",
            "totalFaculty" to 0,
            "totalStudents" to 0,
            "establishedYear" to createdDate,
            "photoUrl" to "",
            "isActive" to true,
            "createdAt" to System.currentTimeMillis()
        )

        // Use batch write to atomically update both collections
        val batch: WriteBatch = firestore.batch()

        // Set the department
        batch.set(departmentRef, department)

        // Update faculty member if HOD is selected
        if (selectedHODId.isNotEmpty()) {
            val facultyRef = firestore.collection("faculty").document(selectedHODId)

            // Only update the specific fields without touching other data
            val facultyUpdates = hashMapOf<String, Any>(
                "role" to "HOD",
                "designation" to "HOD",
                "department" to name,
                "departmentId" to departmentRef.id,
                "hodOf" to mapOf(
                    "departmentName" to name,
                    "departmentId" to departmentRef.id,
                    "college" to selectedCollege,
                    "stream" to selectedStream
                )
            )

            batch.update(facultyRef, facultyUpdates)
        }

        // Commit the batch
        batch.commit()
            .addOnSuccessListener {
                Toast.makeText(this, "Department created successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error creating department: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
