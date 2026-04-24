package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.EnrollmentAdapter
import com.projectbyyatin.synapsemis.models.EnrollmentApplication
import java.util.UUID

class CoeEnrollmentApprovalActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View

    private lateinit var tvPendingCount: TextView
    private lateinit var tvApprovedCount: TextView
    private lateinit var tvRejectedCount: TextView

    private lateinit var adapter: EnrollmentAdapter
    private var allEnrollments = listOf<EnrollmentApplication>()
    private var departments = listOf<Pair<String, String>>()
    private var classes = listOf<Pair<String, String>>()
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coe_enrollment_approval)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        loadDepartments()
        loadClasses()
        setupRecyclerView()
        setupTabs()
        loadEnrollments()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tabLayout = findViewById(R.id.tab_layout)
        recyclerView = findViewById(R.id.enrollments_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        emptyState = findViewById(R.id.empty_state)

        tvPendingCount = findViewById(R.id.tv_pending_count)
        tvApprovedCount = findViewById(R.id.tv_approved_count)
        tvRejectedCount = findViewById(R.id.tv_rejected_count)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun loadDepartments() {
        firestore.collection("departments")
            .get()
            .addOnSuccessListener { documents ->
                departments = documents.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("departmentName") ?: return@mapNotNull null
                    Pair(id, name)
                }
            }
    }

    private fun loadClasses() {
        firestore.collection("classes")
            .get()
            .addOnSuccessListener { documents ->
                classes = documents.mapNotNull { doc ->
                    val id = doc.id
                    val name = doc.getString("className") ?: return@mapNotNull null
                    Pair(id, name)
                }
            }
    }

    private fun setupRecyclerView() {
        adapter = EnrollmentAdapter(
            enrollments = emptyList(),
            departments = departments,
            classes = classes,
            onViewProfile = { enrollment ->
                showProfileDialog(enrollment)
            },
            onApprove = { enrollment, deptId, classId, deptName, semester ->
                approveEnrollment(enrollment, deptId, classId, deptName, semester)
            },
            onReject = { enrollment ->
                showRejectDialog(enrollment)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("All"))
        tabLayout.addTab(tabLayout.newTab().setText("Pending"))
        tabLayout.addTab(tabLayout.newTab().setText("Approved"))
        tabLayout.addTab(tabLayout.newTab().setText("Rejected"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFilter = when (tab.position) {
                    0 -> "all"
                    1 -> "profile_submitted"
                    2 -> "approved"
                    3 -> "rejected"
                    else -> "all"
                }
                filterEnrollments()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadEnrollments() {
        showLoading(true)

        firestore.collection("enrollment_applications")
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)

                allEnrollments = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(EnrollmentApplication::class.java)
                    } catch (e: Exception) {
                        Log.e("CoeEnrollment", "Error parsing: ${e.message}")
                        null
                    }
                }

                updateStats()
                filterEnrollments()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeEnrollment", "Error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateStats() {
        val pendingCount = allEnrollments.count { it.status == "profile_submitted" }
        val approvedCount = allEnrollments.count { it.status == "approved" }
        val rejectedCount = allEnrollments.count { it.status == "rejected" }

        tvPendingCount.text = pendingCount.toString()
        tvApprovedCount.text = approvedCount.toString()
        tvRejectedCount.text = rejectedCount.toString()
    }

    private fun filterEnrollments() {
        val filtered = when (currentFilter) {
            "all" -> allEnrollments
            else -> allEnrollments.filter { it.status == currentFilter }
        }

        adapter.updateData(filtered)

        if (filtered.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    private fun approveEnrollment(
        enrollment: EnrollmentApplication,
        deptId: String,
        classId: String,
        deptName: String,
        semester: Int
    ) {
        AlertDialog.Builder(this)
            .setTitle("Approve Enrollment")
            .setMessage("Approve ${enrollment.fullName}'s enrollment?")
            .setPositiveButton("Approve") { _, _ ->
                processApproval(enrollment, deptId, classId, deptName, semester)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processApproval(
        enrollment: EnrollmentApplication,
        deptId: String,
        classId: String,
        deptName: String,
        semester: Int
    ) {
        showLoading(true)

        // Generate student ID
        val studentId = "STU${System.currentTimeMillis().toString().takeLast(6)}"
        val rollNumber = "ROLL${(1000..9999).random()}"

        val currentUser = auth.currentUser

        // Create student document
        val studentDoc = hashMapOf(
            "studentId" to studentId,
            "userId" to enrollment.userId,
            "rollNumber" to rollNumber,
            "firstName" to enrollment.firstName,
            "lastName" to enrollment.lastName,
            "fullName" to enrollment.fullName,
            "email" to enrollment.email,
            "phone" to enrollment.phone,
            "parentPhone" to enrollment.parentPhone,
            "dateOfBirth" to enrollment.dateOfBirth,
            "gender" to enrollment.gender,
            "bloodGroup" to enrollment.bloodGroup,
            "address" to enrollment.address,
            "city" to enrollment.city,
            "state" to enrollment.state,
            "pincode" to enrollment.pincode,
            "parentName" to enrollment.parentName,
            "parentOccupation" to enrollment.parentOccupation,
            "parentEmail" to enrollment.parentEmail,
            "departmentId" to deptId,
            "departmentName" to deptName,
            "classId" to classId,
            "semester" to semester,
            "courseId" to enrollment.courseId,
            "courseName" to enrollment.courseName,
            "admissionDate" to System.currentTimeMillis(),
            "status" to "active"
        )

        firestore.collection("students")
            .add(studentDoc)
            .addOnSuccessListener { studentDocRef ->
                Log.d("CoeEnrollment", "Student created: ${studentDocRef.id}")

                // Update enrollment application
                updateEnrollmentStatus(enrollment, studentId, rollNumber, deptId, deptName, classId, semester)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeEnrollment", "Error creating student: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateEnrollmentStatus(
        enrollment: EnrollmentApplication,
        studentId: String,
        rollNumber: String,
        deptId: String,
        deptName: String,
        classId: String,
        semester: Int
    ) {
        val currentUser = auth.currentUser
        val updates = hashMapOf<String, Any>(
            "status" to "approved",
            "approvedDate" to System.currentTimeMillis(),
            "approvedBy" to (currentUser?.uid ?: ""),
            "approvedByName" to (currentUser?.email ?: "COE"),
            "studentId" to studentId,
            "rollNumber" to rollNumber,
            "assignedDepartmentId" to deptId,
            "assignedDepartmentName" to deptName,
            "assignedClassId" to classId,
            "assignedSemester" to semester
        )

        firestore.collection("enrollment_applications")
            .whereEqualTo("applicationId", enrollment.applicationId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val docId = documents.documents[0].id
                    firestore.collection("enrollment_applications")
                        .document(docId)
                        .update(updates)
                        .addOnSuccessListener {
                            // Update user role
                            updateUserRole(enrollment.userId, studentId)
                        }
                }
            }
    }

    private fun updateUserRole(userId: String, studentId: String) {
        val userUpdates = hashMapOf<String, Any>(
            "role" to "student",
            "studentId" to studentId
        )

        firestore.collection("users")
            .document(userId)
            .update(userUpdates)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Enrollment approved!", Toast.LENGTH_SHORT).show()

                // TODO: Send email notification

                // Reload data
                loadEnrollments()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeEnrollment", "Error updating user: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRejectDialog(enrollment: EnrollmentApplication) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
        val reasonInput = dialogView.findViewById<TextInputEditText>(R.id.reason_input)

        AlertDialog.Builder(this)
            .setTitle("Reject Enrollment")
            .setMessage("Reject ${enrollment.fullName}'s enrollment?")
            .setView(dialogView)
            .setPositiveButton("Reject") { _, _ ->
                val reason = reasonInput.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Please provide a reason", Toast.LENGTH_SHORT).show()
                } else {
                    processRejection(enrollment, reason)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processRejection(enrollment: EnrollmentApplication, reason: String) {
        showLoading(true)

        val currentUser = auth.currentUser
        val updates = hashMapOf<String, Any>(
            "status" to "rejected",
            "approvedDate" to System.currentTimeMillis(),
            "approvedBy" to (currentUser?.uid ?: ""),
            "approvedByName" to (currentUser?.email ?: "COE"),
            "rejectionReason" to reason
        )

        firestore.collection("enrollment_applications")
            .whereEqualTo("applicationId", enrollment.applicationId)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val docId = documents.documents[0].id
                    firestore.collection("enrollment_applications")
                        .document(docId)
                        .update(updates)
                        .addOnSuccessListener {
                            showLoading(false)
                            Toast.makeText(this, "Enrollment rejected", Toast.LENGTH_SHORT).show()

                            // TODO: Send email notification

                            // Reload data
                            loadEnrollments()
                        }
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeEnrollment", "Error: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showProfileDialog(enrollment: EnrollmentApplication) {
        // TODO: Create detailed profile view dialog
        Toast.makeText(this, "View profile: ${enrollment.fullName}", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
