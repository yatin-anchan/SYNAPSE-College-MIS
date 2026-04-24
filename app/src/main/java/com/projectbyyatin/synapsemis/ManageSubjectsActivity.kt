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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.projectbyyatin.synapsemis.adapters.SubjectsAdapter
import com.projectbyyatin.synapsemis.models.Faculty
import com.projectbyyatin.synapsemis.models.Subject
import com.projectbyyatin.synapsemis.utils.AssignFacultyToSubjectHelper

class ManageSubjectsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ManageSubjects"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SubjectsAdapter
    private lateinit var assignmentHelper: AssignFacultyToSubjectHelper

    private var subjectsList = mutableListOf<Subject>()
    private var facultyList = mutableListOf<Faculty>()

    private var semesterId: String = ""
    private var semesterNumber: Int = 1
    private var semesterName: String = ""
    private var courseId: String = ""
    private var courseName: String = ""
    private var departmentId: String = ""
    private var departmentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_subjects)

        semesterId = intent.getStringExtra("SEMESTER_ID") ?: ""
        semesterNumber = intent.getIntExtra("SEMESTER_NUMBER", 1)
        semesterName = intent.getStringExtra("SEMESTER_NAME") ?: ""
        courseId = intent.getStringExtra("COURSE_ID") ?: ""
        courseName = intent.getStringExtra("COURSE_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadFacultyList()
        loadSubjects()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        fab = findViewById(R.id.fab)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
        assignmentHelper = AssignFacultyToSubjectHelper(firestore)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = semesterName
        supportActionBar?.subtitle = "Subjects & Faculty"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SubjectsAdapter(
            subjectsList,
            onAssignFacultyClick = { subject -> showAssignFacultyDialog(subject) },
            onEditClick = { subject -> editSubject(subject) },
            onDeleteClick = { subject -> showDeleteConfirmation(subject) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        fab.setOnClickListener {
            showAddSubjectDialog()
        }
    }

    private fun loadFacultyList() {
        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                facultyList.clear()
                documents.forEach { document ->
                    val faculty = document.toObject(Faculty::class.java)
                    // IMPORTANT: Store the Firestore document ID
                    faculty.id = document.id
                    facultyList.add(faculty)
                }
                Log.d(TAG, "Loaded ${facultyList.size} faculty members")

                // Log faculty IDs for debugging
                facultyList.forEach {
                    Log.d(TAG, "Faculty: ${it.name}, Doc ID: ${it.id}, Auth ID from field: ${it.id}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error loading faculty list", e)
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadSubjects() {
        showLoading(true)

        firestore.collection("subjects")
            .whereEqualTo("semesterId", semesterId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                subjectsList.clear()

                documents.forEach { document ->
                    val subject = document.toObject(Subject::class.java)
                    subject.id = document.id
                    subjectsList.add(subject)
                }

                Log.d(TAG, "Loaded ${subjectsList.size} subjects")
                adapter.updateList(subjectsList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error loading subjects", e)
                Toast.makeText(this, "Error loading subjects: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAddSubjectDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_subject, null)
        val subjectNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subject_name_input)
        val subjectCodeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subject_code_input)
        val creditsInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.credits_input)
        val typeDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.type_dropdown)

        // Setup type dropdown
        val types = arrayOf("Theory + Practical", "Elective", "Theory", "Practical")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        typeDropdown.setAdapter(typeAdapter)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Add Subject")
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val name = subjectNameInput.text.toString().trim()
                val code = subjectCodeInput.text.toString().trim()
                val credits = creditsInput.text.toString().trim().toIntOrNull() ?: 4
                val type = typeDropdown.text.toString()

                if (name.isNotEmpty() && code.isNotEmpty() && type.isNotEmpty()) {
                    addSubject(name, code, credits, type)
                } else {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addSubject(name: String, code: String, credits: Int, type: String) {
        showLoading(true)

        val subject = hashMapOf(
            "name" to name,
            "code" to code,
            "credits" to credits,
            "type" to type,
            "semesterId" to semesterId,
            "semesterNumber" to semesterNumber,
            "courseId" to courseId,
            "courseName" to courseName,
            "departmentId" to departmentId,
            "department" to departmentName,
            "assignedFacultyId" to "",
            "assignedFacultyName" to "",
            "assignedFacultyEmail" to "",
            "isActive" to true,
            "createdAt" to System.currentTimeMillis(),
            "updatedAt" to System.currentTimeMillis()
        )

        firestore.collection("subjects")
            .add(subject)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Subject added: ${docRef.id}")
                Toast.makeText(this, "Subject added successfully", Toast.LENGTH_SHORT).show()
                loadSubjects()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error adding subject", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showAssignFacultyDialog(subject: Subject) {
        if (facultyList.isEmpty()) {
            Toast.makeText(this, "No faculty available in this department", Toast.LENGTH_SHORT).show()
            return
        }

        val facultyNames = facultyList.map { "${it.name} (${it.designation})" }.toTypedArray()
        var selectedFacultyIndex = -1

        // Find currently assigned faculty if any
        val currentlyAssignedIndex = facultyList.indexOfFirst { it.id == subject.assignedFacultyId }
        if (currentlyAssignedIndex >= 0) {
            selectedFacultyIndex = currentlyAssignedIndex
        }

        val dialog = AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Assign Faculty to ${subject.name}")
            .setSingleChoiceItems(facultyNames, selectedFacultyIndex) { _, which ->
                selectedFacultyIndex = which
            }
            .setPositiveButton("Assign") { _, _ ->
                if (selectedFacultyIndex >= 0) {
                    val selectedFaculty = facultyList[selectedFacultyIndex]
                    assignFacultyToSubject(subject, selectedFaculty)
                } else {
                    Toast.makeText(this, "Please select a faculty", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)

        // Only show "Remove Assignment" if a faculty is currently assigned
        if (subject.assignedFacultyId.isNotEmpty()) {
            dialog.setNeutralButton("Remove Assignment") { _, _ ->
                removeFacultyFromSubject(subject)
            }
        }

        dialog.show()
    }

    private fun assignFacultyToSubject(subject: Subject, faculty: Faculty) {
        showLoading(true)

        Log.d(TAG, "Assigning faculty ${faculty.name} (ID: ${faculty.id}) to subject ${subject.name} (ID: ${subject.id})")

        assignmentHelper.assignFaculty(
            subjectId = subject.id,
            facultyDocId = faculty.id, // This is the Firestore document ID
            onSuccess = {
                Log.d(TAG, "Successfully assigned faculty to subject")
                Toast.makeText(this, "Faculty assigned to ${subject.name}", Toast.LENGTH_SHORT).show()
                loadSubjects() // Reload to show updated data
            },
            onError = { exception ->
                showLoading(false)
                Log.e(TAG, "Error assigning faculty", exception)
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun removeFacultyFromSubject(subject: Subject) {
        if (subject.assignedFacultyId.isEmpty()) {
            Toast.makeText(this, "No faculty assigned to this subject", Toast.LENGTH_SHORT).show()
            return
        }

        showLoading(true)
        Log.d(TAG, "Removing faculty from ${subject.name}")

        assignmentHelper.unassignFaculty(
            subjectId = subject.id,
            onSuccess = {
                Log.d(TAG, "Successfully removed faculty assignment")
                Toast.makeText(this, "Faculty unassigned from ${subject.name}", Toast.LENGTH_SHORT).show()
                loadSubjects() // Reload to show updated data
            },
            onError = { exception ->
                showLoading(false)
                Log.e(TAG, "Error removing faculty", exception)
                Toast.makeText(this, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun editSubject(subject: Subject) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_subject, null)
        val subjectNameInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subject_name_input)
        val subjectCodeInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.subject_code_input)
        val creditsInput = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.credits_input)
        val typeDropdown = dialogView.findViewById<AutoCompleteTextView>(R.id.type_dropdown)

        // Pre-fill data
        subjectNameInput.setText(subject.name)
        subjectCodeInput.setText(subject.code)
        creditsInput.setText(subject.credits.toString())
        typeDropdown.setText(subject.type, false)

        // Setup type dropdown
        val types = arrayOf("Theory + Practical", "Elective", "Theory", "Practical")
        val typeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, types)
        typeDropdown.setAdapter(typeAdapter)

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Edit Subject")
            .setView(dialogView)
            .setPositiveButton("Update") { _, _ ->
                val name = subjectNameInput.text.toString().trim()
                val code = subjectCodeInput.text.toString().trim()
                val credits = creditsInput.text.toString().trim().toIntOrNull() ?: 4
                val type = typeDropdown.text.toString()

                if (name.isNotEmpty() && code.isNotEmpty()) {
                    // If name changed and faculty is assigned, update faculty's subjects list
                    if (name != subject.name && subject.assignedFacultyId.isNotEmpty()) {
                        updateSubjectWithNameChange(subject, name, code, credits, type)
                    } else {
                        updateSubject(subject.id, name, code, credits, type)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateSubjectWithNameChange(subject: Subject, newName: String, code: String, credits: Int, type: String) {
        showLoading(true)

        // Get faculty document to update subjects list
        firestore.collection("faculty").document(subject.assignedFacultyId).get()
            .addOnSuccessListener { facultyDoc ->
                if (!facultyDoc.exists()) {
                    // Faculty doc doesn't exist, just update the subject
                    updateSubject(subject.id, newName, code, credits, type)
                    return@addOnSuccessListener
                }

                val currentSubjects = facultyDoc.get("subjects") as? List<String> ?: emptyList()
                val updatedSubjects = currentSubjects.toMutableList()

                // Replace old name with new name
                val index = updatedSubjects.indexOf(subject.name)
                if (index >= 0) {
                    updatedSubjects[index] = newName
                }

                val batch: WriteBatch = firestore.batch()

                // Update subject
                val subjectRef = firestore.collection("subjects").document(subject.id)
                batch.update(subjectRef, mapOf(
                    "name" to newName,
                    "code" to code,
                    "credits" to credits,
                    "type" to type,
                    "updatedAt" to System.currentTimeMillis()
                ))

                // Update faculty
                val facultyRef = firestore.collection("faculty").document(subject.assignedFacultyId)
                batch.update(facultyRef, mapOf(
                    "subjects" to updatedSubjects,
                    "updatedAt" to System.currentTimeMillis()
                ))

                batch.commit()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Subject updated", Toast.LENGTH_SHORT).show()
                        loadSubjects()
                    }
                    .addOnFailureListener { e ->
                        showLoading(false)
                        Log.e(TAG, "Error updating subject", e)
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateSubject(subjectId: String, name: String, code: String, credits: Int, type: String) {
        showLoading(true)

        firestore.collection("subjects").document(subjectId)
            .update(mapOf(
                "name" to name,
                "code" to code,
                "credits" to credits,
                "type" to type,
                "updatedAt" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Subject updated", Toast.LENGTH_SHORT).show()
                loadSubjects()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error updating subject", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDeleteConfirmation(subject: Subject) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Subject")
            .setMessage("Delete ${subject.name}?\n\nThis will also remove faculty assignment.")
            .setPositiveButton("Delete") { _, _ ->
                deleteSubject(subject)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteSubject(subject: Subject) {
        showLoading(true)

        // If faculty is assigned, remove subject from their list first
        if (subject.assignedFacultyId.isNotEmpty()) {
            firestore.collection("faculty").document(subject.assignedFacultyId).get()
                .addOnSuccessListener { document ->
                    if (!document.exists()) {
                        // Faculty doesn't exist, just delete the subject
                        deleteSubjectOnly(subject.id)
                        return@addOnSuccessListener
                    }

                    val currentSubjects = document.get("subjects") as? List<String> ?: emptyList()
                    val updatedSubjects = currentSubjects.toMutableList()
                    updatedSubjects.remove(subject.name)

                    // Use batch to ensure both operations succeed or fail together
                    val batch: WriteBatch = firestore.batch()

                    // Update faculty
                    val facultyRef = firestore.collection("faculty").document(subject.assignedFacultyId)
                    batch.update(facultyRef, mapOf(
                        "subjects" to updatedSubjects,
                        "updatedAt" to System.currentTimeMillis()
                    ))

                    // Delete subject (soft delete by setting isActive to false)
                    val subjectRef = firestore.collection("subjects").document(subject.id)
                    batch.update(subjectRef, mapOf(
                        "isActive" to false,
                        "updatedAt" to System.currentTimeMillis()
                    ))

                    batch.commit()
                        .addOnSuccessListener {
                            Toast.makeText(this, "Subject deleted", Toast.LENGTH_SHORT).show()
                            loadSubjects()
                        }
                        .addOnFailureListener { e ->
                            showLoading(false)
                            Log.e(TAG, "Error deleting subject", e)
                            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener { e ->
                    showLoading(false)
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // No faculty assigned, just delete the subject
            deleteSubjectOnly(subject.id)
        }
    }

    private fun deleteSubjectOnly(subjectId: String) {
        firestore.collection("subjects").document(subjectId)
            .update("isActive", false, "updatedAt", System.currentTimeMillis())
            .addOnSuccessListener {
                Toast.makeText(this, "Subject deleted", Toast.LENGTH_SHORT).show()
                loadSubjects()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Error deleting subject", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (subjectsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadSubjects()
    }
}