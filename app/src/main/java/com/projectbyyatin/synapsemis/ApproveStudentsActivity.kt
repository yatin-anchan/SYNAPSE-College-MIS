package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.adapters.PendingStudentsAdapter
import com.projectbyyatin.synapsemis.models.ClassStudent

class ApproveStudentsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: PendingStudentsAdapter
    private var pendingStudentsList = mutableListOf<ClassStudent>()

    private var classId: String = ""
    private var className: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_approve_students)

        // ✅ Get class data from intent
        classId = intent.getStringExtra("CLASS_ID") ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""

        Log.d("ApproveStudents", "Class ID: $classId, Class Name: $className")

        if (classId.isEmpty()) {
            Toast.makeText(this, "❌ Invalid class data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadPendingStudents()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Approve Students"
        supportActionBar?.subtitle = className
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = PendingStudentsAdapter(
            pendingStudentsList,
            onApproveClick = { student -> showApproveDialog(student) },
            onRejectClick = { student -> showRejectDialog(student) }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(this@ApproveStudentsActivity)
            adapter = this@ApproveStudentsActivity.adapter
        }
    }

    private fun loadPendingStudents() {
        showLoading(true)
        Log.d("ApproveStudents", "🔍 Loading pending students for class: $classId")

        firestore.collection("class_students")
            .whereEqualTo("classId", classId)
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("ApproveStudents", "📊 Found ${documents.size()} pending students")
                pendingStudentsList.clear()

                for (document in documents) {
                    val student = document.toObject(ClassStudent::class.java)
                    student.id = document.id
                    pendingStudentsList.add(student)
                }

                adapter.updateList(pendingStudentsList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                Log.e("ApproveStudents", "❌ Error loading students", e)
                showLoading(false)
                Toast.makeText(this, "❌ Error loading requests: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showApproveDialog(student: ClassStudent) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("✅ Approve Student")
            .setMessage("Approve ${student.studentName}?\n\nRoll number will be assigned after approval.")
            .setPositiveButton("Approve") { _, _ -> approveStudent(student) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun approveStudent(student: ClassStudent) {
        Log.d("ApproveStudents", "✅ Approving student: ${student.studentName} (${student.id})")

        showLoading(true)

        firestore.collection("class_students").document(student.id)
            .update(
                "status", "approved",
                "approvedAt", System.currentTimeMillis(),
                "approvedBy", "Teacher"
            )
            .addOnSuccessListener {
                incrementClassSize(student.id)
                Toast.makeText(this, "✅ ${student.studentName} approved!", Toast.LENGTH_SHORT).show()
                showLoading(false)
                loadPendingStudents()
            }
            .addOnFailureListener { e ->
                Log.e("ApproveStudents", "❌ Approve failed", e)
                showLoading(false)
                Toast.makeText(this, "❌ Approve failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRejectDialog(student: ClassStudent) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("❌ Reject Student")
            .setMessage("Reject ${student.studentName}?\n\nNo roll number will be assigned.")
            .setPositiveButton("Reject") { _, _ -> rejectStudent(student) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rejectStudent(student: ClassStudent) {
        Log.d("ApproveStudents", "❌ Rejecting student: ${student.studentName} (${student.id})")

        firestore.collection("class_students").document(student.id)
            .update(
                "status", "rejected",
                "rejectedAt", System.currentTimeMillis(),
                "rejectedBy", "Teacher"
            )
            .addOnSuccessListener {
                Toast.makeText(this, "❌ ${student.studentName} rejected", Toast.LENGTH_SHORT).show()
                loadPendingStudents()
            }
            .addOnFailureListener { e ->
                Log.e("ApproveStudents", "❌ Reject failed", e)
                Toast.makeText(this, "❌ Reject failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun incrementClassSize(studentClassDocId: String) {
        // Get current class size
        firestore.collection("classes").document(classId)
            .get()
            .addOnSuccessListener { document ->
                val currentSize = document.getLong("currentSize")?.toInt() ?: 0
                firestore.collection("classes").document(classId)
                    .update("currentSize", currentSize + 1)
                    .addOnFailureListener { e ->
                        Log.e("ApproveStudents", "❌ Failed to increment class size", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ApproveStudents", "❌ Failed to read class size", e)
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        emptyView.visibility = View.GONE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (pendingStudentsList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadPendingStudents()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ApproveStudents", "Activity destroyed")
    }
}
