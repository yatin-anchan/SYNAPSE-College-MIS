package com.projectbyyatin.synapsemis

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.ClassStudentsAdapter
import com.projectbyyatin.synapsemis.adapters.RollNumberAssignment
import com.projectbyyatin.synapsemis.adapters.RollNumberPreviewAdapter
import com.projectbyyatin.synapsemis.models.ClassStudent
import de.hdodenhof.circleimageview.CircleImageView

class ClassStudentsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var studentCountText: TextView
    private lateinit var btnAssignRollNumbers: MaterialButton

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: ClassStudentsAdapter
    private var studentsList = mutableListOf<ClassStudent>()
    private var filteredList = mutableListOf<ClassStudent>()

    private var classId: String = ""
    private var className: String = ""

    companion object {
        private const val TAG = "ClassStudentsActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_class_students)

        classId = intent.getStringExtra("CLASS_ID") ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""

        Log.d(TAG, "Loading students for classId: $classId, className: $className")

        initializeViews()
        setupToolbar()
        setupSearch()
        setupRecyclerView()
        setupAssignRollNumbers()
        loadStudents()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)
        studentCountText = findViewById(R.id.student_count_text)
        btnAssignRollNumbers = findViewById(R.id.btn_assign_roll_numbers)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Class Students"
        supportActionBar?.subtitle = className
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterStudents(newText ?: "")
                return true
            }
        })
    }

    private fun setupRecyclerView() {
        adapter = ClassStudentsAdapter(
            filteredList,
            onViewClick = { student -> showStudentDetailsDialog(student) },
            onRemoveClick = { student -> showRemoveConfirmation(student) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupAssignRollNumbers() {
        btnAssignRollNumbers.setOnClickListener {
            if (studentsList.isEmpty()) {
                Toast.makeText(this, "No students to assign roll numbers", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showRollNumberAssignmentPreview()
        }
    }

    private fun showRollNumberAssignmentPreview() {
        val sortedStudents = studentsList.sortedBy {
            it.studentName.trim().split(" ").lastOrNull()?.lowercase() ?: it.studentName.lowercase()
        }

        val assignments = sortedStudents.mapIndexed { index, student ->
            val newRollNumber = String.format("%02d", index + 1)
            RollNumberAssignment(
                studentId = student.studentId,
                studentName = student.studentName,
                newRollNumber = newRollNumber,
                currentRollNumber = student.rollNumber
            )
        }

        val hasExistingRollNumbers = assignments.any { it.currentRollNumber.isNotEmpty() && it.currentRollNumber != "0" }

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_roll_number_preview, null)
        val previewRecycler: RecyclerView = dialogView.findViewById(R.id.preview_recycler)
        val warningText: TextView = dialogView.findViewById(R.id.warning_text)

        if (hasExistingRollNumbers) warningText.visibility = View.VISIBLE

        previewRecycler.layoutManager = LinearLayoutManager(this)
        previewRecycler.adapter = RollNumberPreviewAdapter(assignments)

        val dialog = AlertDialog.Builder(this, R.style.LightAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Assign Roll Numbers") { _, _ -> assignRollNumbers(assignments) }
            .setNegativeButton("Cancel", null)
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.purple_700))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(android.R.color.darker_gray))
    }

    private fun assignRollNumbers(assignments: List<RollNumberAssignment>) {
        showLoading(true)

        Log.d(TAG, "🎯 Starting roll number assignment for ${assignments.size} students")

        var completed = 0
        val total = assignments.size

        fun checkComplete() {
            completed++
            Log.d(TAG, "📈 Progress: $completed/$total")

            if (completed == total) {
                Handler(Looper.getMainLooper()).postDelayed({
                    Log.d(TAG, "🔄 Reloading students after assignment...")
                    loadStudents()
                    Toast.makeText(
                        this@ClassStudentsActivity,
                        "✅ Roll numbers assigned!",
                        Toast.LENGTH_LONG
                    ).show()
                    showLoading(false)
                }, 1500)
            }
        }

        assignments.forEach { assignment ->
            Log.d(TAG, "🔍 Searching for student: ${assignment.studentId}")

            firestore.collection("students")
                .whereEqualTo("studentId", assignment.studentId)
                .get()
                .addOnSuccessListener { documents ->
                    if (documents.isEmpty) {
                        Log.w(TAG, "⚠️ No student found with studentId: ${assignment.studentId}")
                        checkComplete()
                        return@addOnSuccessListener
                    }

                    val doc = documents.documents[0]
                    Log.d(TAG, "📄 Found document ID: ${doc.id}")
                    Log.d(TAG, "📄 Current rollNumber field: ${doc.getString("rollNumber")}")

                    val studentRef = firestore.collection("students").document(doc.id)

                    val updateData = hashMapOf<String, Any>(
                        "rollNumber" to assignment.newRollNumber,
                        "updatedAt" to System.currentTimeMillis()
                    )

                    Log.d(TAG, "💾 Updating ${assignment.studentName} (${doc.id}) with rollNumber: ${assignment.newRollNumber}")

                    studentRef.update(updateData)
                        .addOnSuccessListener {
                            Log.d(TAG, "✅ SUCCESS: Updated ${assignment.studentName} → Roll: ${assignment.newRollNumber}")

                            // Verify the update
                            studentRef.get().addOnSuccessListener { updatedDoc ->
                                val verifyRoll = updatedDoc.getString("rollNumber")
                                Log.d(TAG, "🔍 VERIFY: ${assignment.studentName} now has rollNumber: '$verifyRoll'")
                            }

                            checkComplete()
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "❌ UPDATE FAILED for ${assignment.studentId}: ${e.message}")
                            Log.e(TAG, "❌ Error details: ", e)
                            checkComplete()
                        }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ QUERY FAILED for ${assignment.studentId}: ${e.message}")
                    Log.e(TAG, "❌ Query error details: ", e)
                    checkComplete()
                }
        }
    }


    private fun loadStudents() {
        showLoading(true)

        firestore.collection("class_students")
            .whereEqualTo("classId", classId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "📋 Found ${documents.size()} class_students records")

                if (documents.isEmpty()) {
                    Log.d(TAG, "📭 No class_students, loading from students collection")
                    loadStudentsFromStudentsCollection()
                    return@addOnSuccessListener
                }

                studentsList.clear()
                documents.forEach { document ->
                    val student = document.toObject(ClassStudent::class.java)
                    student.id = document.id
                    if (student.status == "approved") {
                        studentsList.add(student)
                    }
                }

                studentsList.sortBy { it.rollNumber}
                filteredList.clear()
                filteredList.addAll(studentsList)
                adapter.updateList(filteredList)
                updateStudentCount()
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener {
                Log.e(TAG, "❌ class_students load failed, trying students collection")
                loadStudentsFromStudentsCollection()
            }
    }

    private fun loadStudentsFromStudentsCollection() {
        Log.d(TAG, "🔄 Loading students from students collection (classId: $classId)")

        firestore.collection("students")
            .whereEqualTo("classId", classId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                studentsList.clear()
                Log.d(TAG, "📊 Found ${documents.size()} students in Firestore")

                documents.forEach { document ->
                    val rollNumber = document.getString("rollNumber") ?: ""
                    val studentId = document.getString("studentId") ?: ""
                    val fullName = document.getString("fullName") ?: ""
                    val email = document.getString("email") ?: ""

                    Log.d(TAG, "📄 Document ID: ${document.id}")
                    Log.d(TAG, "📄 Student: $fullName")
                    Log.d(TAG, "📄 StudentId: '$studentId'")
                    Log.d(TAG, "📄 RollNumber from DB: '$rollNumber'")
                    Log.d(TAG, "📄 All fields: ${document.data}")
                    Log.d(TAG, "---")

                    val classStudent = ClassStudent(
                        id = document.id,
                        classId = classId,
                        className = className,
                        studentId = studentId,
                        studentName = fullName,
                        studentEmail = email,
                        rollNumber= rollNumber,
                        status = "approved",
                        joinedAt = document.getLong("admissionDate") ?: 0L,
                        approvedAt = document.getLong("admissionDate") ?: 0L,
                        approvedBy = ""
                    )

                    studentsList.add(classStudent)
                }

                Log.d(TAG, "✅ Total students loaded: ${studentsList.size}")
                studentsList.sortBy { it.rollNumber}
                filteredList.clear()
                filteredList.addAll(studentsList)
                adapter.updateList(filteredList)
                updateStudentCount()
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Students load failed: ${e.message}", e)
                showLoading(false)
            }
    }

    private fun filterStudents(query: String) {
        filteredList.clear()
        if (query.isEmpty()) {
            filteredList.addAll(studentsList)
        } else {
            val lowerQuery = query.lowercase()
            filteredList.addAll(studentsList.filter {
                it.studentName.lowercase().contains(lowerQuery) ||
                        it.studentEmail.lowercase().contains(lowerQuery) ||
                        it.rollNumber.lowercase().contains(lowerQuery)
            })
        }
        adapter.updateList(filteredList)
        updateEmptyView()
    }

    private fun showStudentDetailsDialog(student: ClassStudent) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_student_details, null)
        val profileImage: CircleImageView = dialogView.findViewById(R.id.profile_image)
        val studentName: TextView = dialogView.findViewById(R.id.student_name)
        val studentEmail: TextView = dialogView.findViewById(R.id.student_email)
        val rollNumber: TextView = dialogView.findViewById(R.id.student_roll_no)
        val classNameText: TextView = dialogView.findViewById(R.id.class_name)
        val joinedDate: TextView = dialogView.findViewById(R.id.joined_date)
        val statusChip: Chip = dialogView.findViewById(R.id.status_chip)
        val btnCall: MaterialButton = dialogView.findViewById(R.id.btn_call)
        val btnEmail: MaterialButton = dialogView.findViewById(R.id.btn_email)

        studentName.text = student.studentName
        studentEmail.text = student.studentEmail

        rollNumber.text = if (student.rollNumber.isEmpty() || student.rollNumber== "0") {
            "Roll No: Not Assigned"
        } else {
            "Roll No: ${student.rollNumber}"
        }

        classNameText.text = "Class: ${student.className}"

        if (student.approvedAt > 0) {
            val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            joinedDate.text = "Joined: ${dateFormat.format(java.util.Date(student.approvedAt))}"
        } else {
            joinedDate.text = "Joined: N/A"
        }

        statusChip.text = "Active Student"
        statusChip.setChipBackgroundColorResource(android.R.color.holo_green_dark)

        btnCall.setOnClickListener {
            Toast.makeText(this, "Call functionality - Add phone field", Toast.LENGTH_SHORT).show()
        }

        btnEmail.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${student.studentEmail}")
            }
            startActivity(intent)
        }

        val dialog = AlertDialog.Builder(this, R.style.LightAlertDialog)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .setNegativeButton("Remove") { _, _ -> showRemoveConfirmation(student) }
            .create()

        dialog.window?.setBackgroundDrawableResource(android.R.color.white)
        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(getColor(R.color.purple_700))
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(android.R.color.holo_red_dark))
    }

    private fun showRemoveConfirmation(student: ClassStudent) {
        AlertDialog.Builder(this, R.style.LightAlertDialog)
            .setTitle("Remove Student")
            .setMessage("Remove ${student.studentName} from the class?\n\nThis will:\n• Remove from class\n• Keep attendance records\n• Can rejoin later")
            .setPositiveButton("Remove") { _, _ -> removeStudent(student) }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                window?.setBackgroundDrawableResource(android.R.color.white)
                show()
                getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
                    setTextColor(getColor(android.R.color.holo_red_dark))
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(getColor(android.R.color.darker_gray))
            }
    }

    private fun removeStudent(student: ClassStudent) {
        showLoading(true)
        val batch = firestore.batch()

        if (student.id.isNotEmpty()) {
            batch.update(firestore.collection("class_students").document(student.id),
                mapOf("status" to "removed", "removedAt" to System.currentTimeMillis()))
        }

        firestore.collection("students")
            .whereEqualTo("studentId", student.studentId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    batch.update(firestore.collection("students").document(documents.documents[0].id),
                        mapOf("classId" to "", "className" to "", "updatedAt" to System.currentTimeMillis()))
                }
                batch.commit().addOnSuccessListener {
                    decrementClassSize()
                    Toast.makeText(this, "${student.studentName} removed", Toast.LENGTH_SHORT).show()
                    loadStudents()
                }.addOnFailureListener {
                    showLoading(false)
                    Toast.makeText(this, "Error removing student", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun decrementClassSize() {
        firestore.collection("classes").document(classId)
            .get()
            .addOnSuccessListener { document ->
                val currentSize = document.getLong("currentSize")?.toInt() ?: 0
                if (currentSize > 0) {
                    firestore.collection("classes").document(classId)
                        .update("currentSize", currentSize - 1)
                }
            }
    }

    private fun updateStudentCount() {
        studentCountText.text = "${filteredList.size} Students"
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        btnAssignRollNumbers.isEnabled = !show
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadStudents()
    }
}
