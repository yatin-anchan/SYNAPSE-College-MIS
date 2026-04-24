package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.AttendanceDraftsAdapter
import com.projectbyyatin.synapsemis.models.AttendanceDraft
import com.projectbyyatin.synapsemis.models.StudentAttendance

class AttendanceDraftsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: AttendanceDraftsAdapter

    private val draftsList = mutableListOf<AttendanceDraft>()

    private var departmentId = ""
    private var departmentName = ""
    private var facultyId = ""
    private var userRole = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_drafts)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""
        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        userRole = intent.getStringExtra("USER_ROLE") ?: "faculty"

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadDrafts()
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
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Attendance Drafts"
            subtitle = departmentName
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = AttendanceDraftsAdapter(
            draftsList,
            onViewClick = { viewDraftDetails(it) },
            onDeleteClick = { confirmDeleteDraft(it) }
        )
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadDrafts() {
        showLoading(true)

        val baseQuery = if (userRole == "hod") {
            firestore.collection("attendance_drafts")
                .whereEqualTo("departmentId", departmentId)
        } else {
            firestore.collection("attendance_drafts")
                .whereEqualTo("facultyId", facultyId)
        }

        baseQuery
            .whereEqualTo("status", "draft")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->

                draftsList.clear()

                for (doc in documents) {

                    val draft = doc.toObject(AttendanceDraft::class.java)
                    draft.id = doc.id

                    // ✅ MANUAL STUDENT MAPPING (NO TYPE MISMATCH)
                    val rawStudents =
                        doc.get("students") as? List<Map<String, Any>> ?: emptyList()

                    val students = rawStudents.map { map ->
                        StudentAttendance(
                            studentId = map["studentId"] as? String ?: "",
                            studentName = map["studentName"] as? String ?: "",
                            rollNumber = map["rollNumber"] as? String ?: "",
                            email = map["email"] as? String ?: "",
                            status = map["status"] as? String ?: "present",
                            remarks = map["remarks"] as? String ?: ""
                        )
                    }

                    draft.students = students
                    draftsList.add(draft)
                }

                adapter.notifyDataSetChanged()
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(
                    this,
                    "Error loading drafts: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                updateEmptyView()
            }
    }

    private fun viewDraftDetails(draft: AttendanceDraft) {
        val intent = Intent(this, AttendanceDraftDetailsActivity::class.java)
        intent.putExtra("DRAFT_ID", draft.id)
        intent.putExtra("DEPARTMENT_ID", departmentId)
        intent.putExtra("DEPARTMENT_NAME", departmentName)
        startActivityForResult(intent, REQUEST_DRAFT_DETAILS)
    }

    private fun confirmDeleteDraft(draft: AttendanceDraft) {
        AlertDialog.Builder(this)
            .setTitle("Delete Draft")
            .setMessage("Are you sure you want to delete this attendance draft?")
            .setPositiveButton("Delete") { _, _ -> deleteDraft(draft) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDraft(draft: AttendanceDraft) {
        firestore.collection("attendance_drafts")
            .document(draft.id)
            .delete()
            .addOnSuccessListener {
                draftsList.remove(draft)
                adapter.notifyDataSetChanged()
                updateEmptyView()
                Toast.makeText(this, "Draft deleted", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (draftsList.isEmpty()) View.VISIBLE else View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DRAFT_DETAILS && resultCode == RESULT_OK) {
            loadDrafts()
        }
    }

    companion object {
        private const val REQUEST_DRAFT_DETAILS = 1001
    }
}