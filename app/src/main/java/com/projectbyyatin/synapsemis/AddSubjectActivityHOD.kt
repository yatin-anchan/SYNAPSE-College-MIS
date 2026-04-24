package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Subject
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

class AddSubjectActivityHOD : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var toolbar: Toolbar
    private lateinit var etSubjectCode: TextInputEditText
    private lateinit var etSubjectName: TextInputEditText
    private lateinit var etCredits: TextInputEditText
    private lateinit var btnSaveSubject: MaterialButton

    private var departmentId: String? = null
    private var departmentName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_subject_hod)

        firestore = FirebaseFirestore.getInstance()
        departmentId = intent.getStringExtra("departmentId")
        departmentName = intent.getStringExtra("departmentName")

        toolbar = findViewById(R.id.toolbar)
        etSubjectCode = findViewById(R.id.etSubjectCode)
        etSubjectName = findViewById(R.id.etSubjectName)
        etCredits = findViewById(R.id.etCredits)
        btnSaveSubject = findViewById(R.id.btnSaveSubject)

        setupToolbar()
        setupClickListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Add Subject"
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupClickListeners() {
        btnSaveSubject.setOnClickListener {
            saveSubject()
        }
    }

    private fun saveSubject() {
        val subjectCode = etSubjectCode.text.toString().trim()
        val subjectName = etSubjectName.text.toString().trim()
        val creditsText = etCredits.text.toString().trim()

        // Validation
        if (subjectCode.isEmpty()) {
            etSubjectCode.error = "Subject code is required"
            etSubjectCode.requestFocus()
            return
        }
        if (subjectName.isEmpty()) {
            etSubjectName.error = "Subject name is required"
            etSubjectName.requestFocus()
            return
        }
        val credits = creditsText.toIntOrNull()
        if (credits == null || credits <= 0 || credits > 10) {
            etCredits.error = "Valid credits required (1-10)"
            etCredits.requestFocus()
            return
        }

        val safeDepartmentId = departmentId ?: ""
        val safeDepartmentName = departmentName ?: ""

        // Show loading
        btnSaveSubject.text = "Saving..."
        btnSaveSubject.isEnabled = false
        btnSaveSubject.alpha = 0.6f

        lifecycleScope.launch {
            try {
                val currentTime = Calendar.getInstance().timeInMillis

                // ✅ PERFECT MATCH for YOUR Subject model
                val subject = Subject(
                    id = "", // Firestore will generate
                    name = subjectName,
                    code = subjectCode,
                    credits = credits,
                    type = "Theory", // Default
                    semesterId = "",
                    semesterNumber = 1, // Default
                    courseId = "",
                    courseName = "",
                    departmentId = safeDepartmentId,
                    department = safeDepartmentName,
                    assignedFacultyId = "",
                    assignedFacultyName = "",
                    assignedFacultyEmail = "",
                    isActive = true,
                    createdAt = currentTime,
                    updatedAt = currentTime
                )

                firestore.collection("subjects")
                    .add(subject)
                    .await()

                Toast.makeText(this@AddSubjectActivityHOD, "✅ Subject added successfully!", Toast.LENGTH_SHORT).show()
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@AddSubjectActivityHOD, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                btnSaveSubject.text = "Save Subject"
                btnSaveSubject.isEnabled = true
                btnSaveSubject.alpha = 1.0f
            }
        }
    }
}
