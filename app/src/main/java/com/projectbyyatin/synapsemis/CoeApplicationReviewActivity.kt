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
import com.projectbyyatin.synapsemis.adapters.ApplicationAdapter
import com.projectbyyatin.synapsemis.models.Application
import com.projectbyyatin.synapsemis.models.Course
import com.projectbyyatin.synapsemis.models.Student

class CoeApplicationReviewActivity : AppCompatActivity() {

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyState: View

    private lateinit var tvPendingCount: TextView
    private lateinit var tvAcceptedCount: TextView
    private lateinit var tvRejectedCount: TextView

    private lateinit var adapter: ApplicationAdapter
    private var allApplications = listOf<Application>()
    private var currentFilter = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coe_application_review)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupRecyclerView()
        setupTabs()
        loadApplications()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        tabLayout = findViewById(R.id.tab_layout)
        recyclerView = findViewById(R.id.applications_recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        emptyState = findViewById(R.id.empty_state)

        tvPendingCount = findViewById(R.id.tv_pending_count)
        tvAcceptedCount = findViewById(R.id.tv_accepted_count)
        tvRejectedCount = findViewById(R.id.tv_rejected_count)

        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ApplicationAdapter(
            applications = emptyList(),
            onViewDetails = { application ->
                showApplicationDetails(application)
            },
            onAccept = { application ->
                acceptApplication(application)
            },
            onReject = { application ->
                showRejectDialog(application)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("All"))
        tabLayout.addTab(tabLayout.newTab().setText("Pending"))
        tabLayout.addTab(tabLayout.newTab().setText("Accepted"))
        tabLayout.addTab(tabLayout.newTab().setText("Rejected"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentFilter = when (tab.position) {
                    0 -> "pending"
                    1 -> "all"
                    2 -> "accepted"
                    3 -> "rejected"
                    else -> "all"
                }
                filterApplications()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun loadApplications() {
        showLoading(true)

        firestore.collection("applications")
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)

                allApplications = documents.mapNotNull { doc ->
                    try {
                        doc.toObject(Application::class.java).copy(
                            applicationId = doc.id
                        )
                    } catch (e: Exception) {
                        Log.e("CoeAppReview", "Error parsing application: ${e.message}")
                        null
                    }
                }

                updateStats()
                filterApplications()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeAppReview", "Error loading applications: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateStats() {
        val pendingCount = allApplications.count { it.status == "pending" }
        val acceptedCount = allApplications.count { it.status == "accepted" }
        val rejectedCount = allApplications.count { it.status == "rejected" }

        tvPendingCount.text = pendingCount.toString()
        tvAcceptedCount.text = acceptedCount.toString()
        tvRejectedCount.text = rejectedCount.toString()
    }

    private fun filterApplications() {
        val filtered = when (currentFilter) {
            "all" -> allApplications
            else -> allApplications.filter { it.status == currentFilter }
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

    private fun showApplicationDetails(application: Application) {
        val intent = Intent(this, ApplicationDetailsActivity::class.java)
        intent.putExtra("APPLICATION_ID", application.applicationId)
        intent.putExtra("REFERENCE_NUMBER", application.referenceNumber)
        startActivity(intent)
    }

    private fun acceptApplication(application: Application) {
        progressBar.visibility = View.VISIBLE

        val currentUserEmail = auth.currentUser?.email ?: "COE"
        val currentTime = System.currentTimeMillis()

        val updates = hashMapOf<String, Any>(
            "status" to "accepted",
            "reviewedBy" to currentUserEmail,
            "reviewedDate" to currentTime
        )

        firestore.collection("applications")
            .document(application.applicationId)
            .update(updates)
            .addOnSuccessListener {
                // Create student account automatically
                createStudentAccount(application)
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error accepting: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("CoeAppReview", "Error accepting", e)
            }
    }

    private fun createStudentAccount(application: Application) {
        val email = application.email
        val defaultPassword = "123456"

        // First, fetch the course details to get department info
        firestore.collection("courses")
            .document(application.courseId)
            .get()
            .addOnSuccessListener { courseDoc ->
                if (!courseDoc.exists()) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: Course not found", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val course = courseDoc.toObject(Course::class.java)
                if (course == null) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this, "Error: Invalid course data", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Now create the Firebase Authentication account
                auth.createUserWithEmailAndPassword(email, defaultPassword)
                    .addOnSuccessListener { authResult ->
                        val userId = authResult.user?.uid ?: ""

                        // Create Student document with department info from course
                        val student = Student(
                            id = userId,
                            studentId = "", // Will be assigned during final enrollment
                            rollNumber = "", // Will be assigned during final enrollment
                            firstName = application.firstName,
                            lastName = application.lastName,
                            fullName = application.fullName,
                            email = application.email,
                            phoneNumber = application.phone,
                            dateOfBirth = application.dateOfBirth,

                            // Course Information
                            courseId = application.courseId,
                            courseName = application.courseName,

                            // Department Information (from Course)
                            departmentId = course.departmentId,
                            departmentName = course.department,

                            academicYear = application.preferredBatch,
                            admissionDate = System.currentTimeMillis(),
                            admissionYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR),
                            batch = application.preferredBatch,

                            userId = userId,
                            role = "student",
                            isActive = false, // Will be activated after profile completion
                            profileImageUrl = application.photoUrl,
                            profileCompleted = false,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )

                        // Save student to Firestore
                        firestore.collection("students")
                            .document(userId)
                            .set(student)
                            .addOnSuccessListener {
                                // Create user document for authentication
                                val user = hashMapOf(
                                    "uid" to userId,
                                    "email" to email,
                                    "role" to "student",
                                    "name" to application.fullName,
                                    "isFirstLogin" to true,
                                    "profileCompleted" to false,
                                    "departmentId" to course.departmentId,
                                    "departmentName" to course.department,
                                    "courseId" to application.courseId,
                                    "courseName" to application.courseName
                                )

                                firestore.collection("users")
                                    .document(userId)
                                    .set(user)
                                    .addOnSuccessListener {
                                        // Update department student count
                                        updateDepartmentStudentCount(course.departmentId)

                                        progressBar.visibility = View.GONE
                                        Toast.makeText(
                                            this,
                                            "Application accepted! Account created for ${application.fullName}",
                                            Toast.LENGTH_LONG
                                        ).show()

                                        // Send welcome email with credentials
                                        sendWelcomeEmail(application, defaultPassword)

                                        // Refresh the list
                                        loadApplications()
                                    }
                                    .addOnFailureListener { e ->
                                        progressBar.visibility = View.GONE
                                        Log.e("CoeAppReview", "Error creating user doc", e)
                                        Toast.makeText(
                                            this,
                                            "Error creating user document: ${e.message}",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                            }
                            .addOnFailureListener { e ->
                                progressBar.visibility = View.GONE
                                Toast.makeText(
                                    this,
                                    "Error creating student: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                Log.e("CoeAppReview", "Error creating student", e)
                            }
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = View.GONE

                        // Handle case where email already exists
                        if (e.message?.contains("already in use") == true) {
                            Toast.makeText(
                                this,
                                "Account already exists for this email. Updating application status only.",
                                Toast.LENGTH_LONG
                            ).show()
                            // Just mark application as accepted
                            loadApplications()
                        } else {
                            Toast.makeText(
                                this,
                                "Error creating account: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        Log.e("CoeAppReview", "Error creating auth account", e)
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Error fetching course details: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                Log.e("CoeAppReview", "Error fetching course", e)
            }
    }

    private fun updateDepartmentStudentCount(departmentId: String) {
        // Increment the totalStudents count in the department
        firestore.collection("departments")
            .document(departmentId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val currentCount = doc.getLong("totalStudents")?.toInt() ?: 0
                    firestore.collection("departments")
                        .document(departmentId)
                        .update("totalStudents", currentCount + 1)
                        .addOnSuccessListener {
                            Log.d("CoeAppReview", "Department student count updated")
                        }
                        .addOnFailureListener { e ->
                            Log.e("CoeAppReview", "Error updating department count", e)
                        }
                }
            }
    }

    private fun sendWelcomeEmail(application: Application, password: String) {
        // TODO: Implement email sending via Cloud Functions or backend API
        Log.d("CoeAppReview", """
        Welcome Email to: ${application.email}
        Name: ${application.fullName}
        Email: ${application.email}
        Temporary Password: $password
        Course: ${application.courseName}
        
        Instructions:
        1. Login to SYNAPSE MIS
        2. Change your password
        3. Complete your profile setup
    """.trimIndent())
    }



    private fun processAcceptance(application: Application) {
        showLoading(true)

        val currentUser = auth.currentUser
        val updates = hashMapOf<String, Any>(
            "status" to "accepted",
            "reviewedDate" to System.currentTimeMillis(),
            "reviewedBy" to (currentUser?.uid ?: ""),
            "reviewedByName" to (currentUser?.email ?: "COE")
        )

        firestore.collection("applications")
            .document(application.applicationId)
            .update(updates)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Application accepted!", Toast.LENGTH_SHORT).show()

                // TODO: Send email notification
                sendAcceptanceEmail(application)

                // Reload data
                loadApplications()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeAppReview", "Error accepting: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showRejectDialog(application: Application) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_reject_reason, null)
        val reasonInput = dialogView.findViewById<TextInputEditText>(R.id.reason_input)

        AlertDialog.Builder(this)
            .setTitle("Reject Application")
            .setMessage("Reject ${application.fullName}'s application?")
            .setView(dialogView)
            .setPositiveButton("Reject") { _, _ ->
                val reason = reasonInput.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this, "Please provide a reason", Toast.LENGTH_SHORT).show()
                } else {
                    processRejection(application, reason)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun processRejection(application: Application, reason: String) {
        showLoading(true)

        val currentUser = auth.currentUser
        val updates = hashMapOf<String, Any>(
            "status" to "rejected",
            "reviewedDate" to System.currentTimeMillis(),
            "reviewedBy" to (currentUser?.uid ?: ""),
            "reviewedByName" to (currentUser?.email ?: "COE"),
            "rejectionReason" to reason
        )

        firestore.collection("applications")
            .document(application.applicationId)
            .update(updates)
            .addOnSuccessListener {
                showLoading(false)
                Toast.makeText(this, "Application rejected", Toast.LENGTH_SHORT).show()

                // TODO: Send rejection email
                sendRejectionEmail(application, reason)

                // Reload data
                loadApplications()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e("CoeAppReview", "Error rejecting: ${e.message}")
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendAcceptanceEmail(application: Application) {
        // TODO: Implement email sending
        Log.d("CoeAppReview", "Sending acceptance email to ${application.email}")
        // You can use Firebase Cloud Functions or a backend API to send emails
    }

    private fun sendRejectionEmail(application: Application, reason: String) {
        // TODO: Implement email sending
        Log.d("CoeAppReview", "Sending rejection email to ${application.email}")
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}
