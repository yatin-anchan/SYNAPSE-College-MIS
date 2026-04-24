package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R

class AssignFacultyToSubjectActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var etFaculty: MaterialAutoCompleteTextView
    private lateinit var btnAssign: MaterialButton
    private lateinit var firestore: FirebaseFirestore

    private var subjectId = ""
    private var subjectName = ""
    private var departmentId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_assign_faculty_to_subject)

        getIntentData()
        initViews()
        setupToolbar()
        loadFacultyList()
        setupAssignButton()
    }

    private fun getIntentData() {
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        etFaculty = findViewById(R.id.et_faculty)
        btnAssign = findViewById(R.id.btn_assign)
        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Assign Faculty"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadFacultyList() {
        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .orderBy("name")
            .get()
            .addOnSuccessListener { documents ->
                val facultyNames = mutableListOf<String>()
                documents.documents.forEach { doc ->
                    doc.getString("name")?.let { facultyNames.add(it) }
                }
                val adapter = ArrayAdapter(this, R.layout.dropdown_item, facultyNames)
                etFaculty.setAdapter(adapter)
            }
    }

    private fun setupAssignButton() {
        btnAssign.setOnClickListener {
            val selectedFaculty = etFaculty.text.toString()
            if (selectedFaculty.isNotEmpty()) {
                assignFaculty(selectedFaculty)
            } else {
                Toast.makeText(this, "Please select faculty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun assignFaculty(facultyName: String) {
        firestore.collection("subjects").document(subjectId)
            .update(
                "assignedFacultyName", facultyName,
                "assignedFacultyId", "faculty_id_placeholder", // Update with actual ID
                "updatedAt", System.currentTimeMillis()
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Faculty assigned successfully", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to assign", Toast.LENGTH_SHORT).show()
            }
    }
}
