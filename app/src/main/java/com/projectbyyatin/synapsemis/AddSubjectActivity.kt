package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R

class AddSubjectActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var etName: TextInputEditText
    private lateinit var etCode: TextInputEditText
    private lateinit var etCredits: TextInputEditText
    private lateinit var etType: MaterialAutoCompleteTextView
    private lateinit var etSemester: MaterialAutoCompleteTextView
    private lateinit var btnSave: MaterialButton
    private lateinit var firestore: FirebaseFirestore

    private var departmentId = ""
    private var departmentName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_subject)

        getIntentData()
        initViews()
        setupToolbar()
        setupDropdowns()
        setupSaveButton()
    }

    private fun getIntentData() {
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etName = findViewById(R.id.et_name)
        etCode = findViewById(R.id.et_code)
        etCredits = findViewById(R.id.et_credits)
        etType = findViewById(R.id.et_type)
        etSemester = findViewById(R.id.et_semester)
        btnSave = findViewById(R.id.btn_save)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Add Subject"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDropdowns() {
        // Subject types
        val types = listOf("Theory", "Practical", "Elective", "Lab")
        etType.setAdapter(ArrayAdapter(this, R.layout.dropdown_item, types))

        // Semesters
        val semesters = listOf("Sem 1", "Sem 2", "Sem 3", "Sem 4", "Sem 5", "Sem 6")
        etSemester.setAdapter(ArrayAdapter(this, R.layout.dropdown_item, semesters))
    }

    private fun setupSaveButton() {
        btnSave.setOnClickListener {
            if (validateInputs()) {
                saveSubject()
            }
        }
    }

    private fun validateInputs(): Boolean {
        if (etName.text.isNullOrBlank()) {
            etName.error = "Subject name required"
            return false
        }
        if (etCode.text.isNullOrBlank()) {
            etCode.error = "Code required"
            return false
        }
        return true
    }

    private fun saveSubject() {
        val subject = mapOf(
            "name" to etName.text.toString(),
            "code" to etCode.text.toString(),
            "credits" to (etCredits.text.toString().toIntOrNull() ?: 4),
            "type" to etType.text.toString(),
            "semesterNumber" to (etSemester.text.toString().substringBefore(" ").toIntOrNull() ?: 1),
            "departmentId" to departmentId,
            "department" to departmentName,
            "isActive" to true,
            "createdAt" to System.currentTimeMillis().toString(),
            "updatedAt" to System.currentTimeMillis().toString()
        )

        firestore.collection("subjects")
            .add(subject)
            .addOnSuccessListener {
                Toast.makeText(this, "Subject added successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
            }
    }
}
