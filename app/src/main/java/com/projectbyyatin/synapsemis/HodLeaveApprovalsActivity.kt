package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.LeaveRequestAdapter
import com.projectbyyatin.synapsemis.models.LeaveRequest

class HodLeaveApprovalsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipPending: Chip
    private lateinit var chipApproved: Chip
    private lateinit var chipRejected: Chip
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: LeaveRequestAdapter
    private var leaveList = mutableListOf<LeaveRequest>()

    private var departmentId = ""
    private var departmentName = ""
    private var selectedStatus = "pending"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_leave_approvals)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupChips()
        setupRecyclerView()
        loadLeaveRequests()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        chipGroup = findViewById(R.id.chip_group)
        chipPending = findViewById(R.id.chip_pending)
        chipApproved = findViewById(R.id.chip_approved)
        chipRejected = findViewById(R.id.chip_rejected)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Leave Approvals"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupChips() {
        chipPending.isChecked = true

        chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedStatus = when (checkedIds.firstOrNull()) {
                R.id.chip_pending -> "pending"
                R.id.chip_approved -> "approved"
                R.id.chip_rejected -> "rejected"
                else -> "pending"
            }
            loadLeaveRequests()
        }
    }

    private fun setupRecyclerView() {
        adapter = LeaveRequestAdapter(leaveList, selectedStatus == "pending") { leave, action ->
            when (action) {
                "approve" -> showApprovalDialog(leave, true)
                "reject" -> showApprovalDialog(leave, false)
                "view" -> showLeaveDetails(leave)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadLeaveRequests() {
        showLoading(true)

        firestore.collection("leaveRequests")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("status", selectedStatus)
            .orderBy("requestDate", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { documents ->
                leaveList.clear()

                documents.forEach { doc ->
                    val leave = doc.toObject(LeaveRequest::class.java)
                    leave.id = doc.id
                    leaveList.add(leave)
                }

                adapter.updateList(leaveList, selectedStatus == "pending")
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showApprovalDialog(leave: LeaveRequest, approve: Boolean) {
        val title = if (approve) "Approve Leave" else "Reject Leave"
        val message = if (approve) {
            "Approve leave request for ${leave.applicantName}?\n\nFrom: ${leave.startDate}\nTo: ${leave.endDate}\nReason: ${leave.reason}"
        } else {
            "Reject leave request for ${leave.applicantName}?\n\nFrom: ${leave.startDate}\nTo: ${leave.endDate}"
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ ->
                updateLeaveStatus(leave.id, if (approve) "approved" else "rejected")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateLeaveStatus(leaveId: String, status: String) {
        firestore.collection("leaveRequests").document(leaveId)
            .update(mapOf(
                "status" to status,
                "approvedBy" to "HOD",
                "approvalDate" to System.currentTimeMillis()
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "Leave request $status", Toast.LENGTH_SHORT).show()
                loadLeaveRequests()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLeaveDetails(leave: LeaveRequest) {
        val details = """
            Applicant: ${leave.applicantName}
            Type: ${leave.applicantType}
            
            From: ${leave.startDate}
            To: ${leave.endDate}
            Days: ${leave.numberOfDays}
            
            Reason: ${leave.reason}
            
            Status: ${leave.status?.uppercase()}
            ${if (leave.status != "pending") "Approved/Rejected on: ${leave.approvalDate}" else ""}
        """.trimIndent()

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Leave Details")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (leaveList.isEmpty()) View.VISIBLE else View.GONE
        emptyView.text = "No ${selectedStatus} leave requests"
    }
}