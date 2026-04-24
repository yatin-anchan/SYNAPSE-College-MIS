package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.projectbyyatin.synapsemis.models.Faculty
import java.util.Calendar

class HodDashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var totalFacultyText: TextView
    private lateinit var totalStudentsText: TextView
    private lateinit var totalSubjectsText: TextView
    private lateinit var avgAttendanceText: TextView
    private lateinit var departmentNameText: TextView

    private lateinit var btnFaculty: MaterialCardView
    private lateinit var btnStudents: MaterialCardView
    private lateinit var btndeptDashboard: MaterialCardView
    private lateinit var btnAttendance: MaterialCardView
    private lateinit var btnTimetable: MaterialCardView
    private lateinit var btnLeaveApprovals: MaterialCardView

    private var departmentId = ""
    private var departmentName = ""
    private var hodName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_dashboard)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        setupBackPress()
        loadHodDepartmentInfo()
        setupClickListeners()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        departmentNameText = findViewById(R.id.department_name)
        totalFacultyText = findViewById(R.id.total_faculty)
        totalStudentsText = findViewById(R.id.total_students)
        totalSubjectsText = findViewById(R.id.total_subjects)
        avgAttendanceText = findViewById(R.id.avg_attendance)

        btnFaculty = findViewById(R.id.btn_faculty)
        btnStudents = findViewById(R.id.btn_students)
        btndeptDashboard = findViewById(R.id.btn_subjects)
        btnAttendance = findViewById(R.id.btn_attendance)
        btnTimetable = findViewById(R.id.btn_timetable)
        btnLeaveApprovals = findViewById(R.id.btn_leave_approvals)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "HOD Dashboard"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener(this)
        updateNavigationHeader()
    }

    private fun loadHodDepartmentInfo() {
        firestore.collection("faculty")
            .whereEqualTo("email", auth.currentUser?.email)
            .whereEqualTo("role", "hod")
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val hodDoc = querySnapshot.documents[0]
                    departmentId   = hodDoc.getString("departmentId") ?: ""
                    departmentName = hodDoc.getString("department") ?: ""
                    hodName        = hodDoc.getString("name") ?: ""

                    Log.d("HOD_DEBUG", "Found HOD: departmentId=$departmentId")
                    departmentNameText.text = "Department: $departmentName"
                    loadDepartmentStats()
                }
            }
    }

    private fun loadDepartmentStats() {
        if (departmentId.isEmpty()) return

        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs -> totalFacultyText.text = "${docs.size()}" }

        firestore.collection("students")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs -> totalStudentsText.text = "${docs.size()}" }

        firestore.collection("subjects")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs -> totalSubjectsText.text = "${docs.size()}" }

        loadTodayClassesCount()
        loadPendingLeavesCount()
    }

    private fun loadTodayClassesCount() {
        val today = getCurrentDayName()
        firestore.collection("timetable")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("day", today)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { /* update badge if needed */ }
    }

    private fun loadPendingLeavesCount() {
        firestore.collection("leave_requests")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("status", "pending")
            .whereEqualTo("hodId", auth.currentUser?.uid)
            .get()
            .addOnSuccessListener { /* update badge if needed */ }
    }

    private fun getCurrentDayName(): String {
        val days = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        return days[Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 1]
    }

    private fun setupClickListeners() {

        btnFaculty.setOnClickListener {
            navigateTo(DepartmentFacultyActivity::class.java)
        }

        btnStudents.setOnClickListener {
            navigateTo(ManageDeptStudentsActivity::class.java)
        }

        btndeptDashboard.setOnClickListener {
            navigateTo(HodDepartmentDashboardActivity::class.java)
        }

        // ── ATTENDANCE CARD → Reports (subject+month filter) ──────────────────
        btnAttendance.setOnClickListener {
            openAttendanceReports()
        }

        btnTimetable.setOnClickListener {
            navigateTo(HodTimetableActivity::class.java)
        }

        btnLeaveApprovals.setOnClickListener {
            navigateTo(HodLeaveApprovalsActivity::class.java)
        }
    }

    // ── Entry point: Attendance Reports ──────────────────────────────────────
    private fun openAttendanceReports() {
        if (departmentId.isEmpty()) {
            Toast.makeText(this, "⚠️ Department not assigned", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, AttendanceReportsActivity::class.java).apply {
                putExtra("DEPARTMENT_ID",   departmentId)
                putExtra("DEPARTMENT_NAME", departmentName)
                putExtra("CLASS_NAME",      departmentName)  // toolbar subtitle
                putExtra("USER_ROLE",       "hod")
                putExtra("FACULTY_ID",      auth.currentUser?.uid ?: "")
                putExtra("HOD_NAME",        hodName)
                // ✅ No CLASS_ID needed — user picks class from the dropdown inside the activity
            }
        )
    }


    // ── Entry point: Student Analytics ───────────────────────────────────────
    private fun openStudentAnalytics(classId: String, className: String) {
        if (departmentId.isEmpty()) {
            Toast.makeText(this, "⚠️ Department not assigned", Toast.LENGTH_SHORT).show()
            return
        }
        startActivity(
            Intent(this, StudentAttendanceAnalyticsActivity::class.java).apply {
                putExtra("CLASS_ID", classId)
                putExtra("CLASS_NAME", className)
                putExtra("DEPARTMENT_ID", departmentId)
                putExtra("DEPARTMENT_NAME", departmentName)
            }
        )
    }

    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val nameTextView  = headerView.findViewById<TextView>(R.id.nav_header_name)
        val emailTextView = headerView.findViewById<TextView>(R.id.nav_header_email)
        val roleTextView  = headerView.findViewById<TextView>(R.id.nav_header_role)

        auth.currentUser?.let { user ->
            firestore.collection("faculty").document(user.uid)
                .get()
                .addOnSuccessListener { doc ->
                    val faculty = doc.toObject<Faculty>()
                    nameTextView.text  = faculty?.name ?: "HOD"
                    emailTextView.text = faculty?.email ?: user.email ?: ""
                    roleTextView.text  = "HOD - $departmentName"
                }
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    showExitDialog()
                }
            }
        })
    }

    private fun showExitDialog() {
        AlertDialog.Builder(this)
            .setTitle("Exit Dashboard")
            .setMessage("Are you sure you want to exit?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> { /* already here */ }

            R.id.nav_faculty ->
                navigateTo(DepartmentFacultyActivity::class.java)

            R.id.nav_students ->
                navigateTo(ManageDeptStudentsActivity::class.java)

            // ── Attendance Reports (subject+month filter) ──────────────────
            R.id.nav_attendance ->
                openAttendanceReports()

            // ── Mark Attendance ────────────────────────────────────────────
            R.id.nav_mark_attendance -> {
                if (departmentId.isNotEmpty()) {
                    startActivity(
                        Intent(this, MarkAttendanceHODActivity::class.java).apply {
                            putExtra("DEPARTMENT_ID", departmentId)
                            putExtra("DEPARTMENT_NAME", departmentName)
                        }
                    )
                } else {
                    Toast.makeText(this, "⚠️ Department loading...", Toast.LENGTH_SHORT).show()
                }
            }

            R.id.nav_exams ->
                navigateTo(GenerateResultActivity_HOD::class.java)

            R.id.nav_marks_moderate_hod ->
                navigateTo(HodMarksModerationListActivity::class.java)

            R.id.nav_timetable ->
                navigateTo(HodTimetableActivity::class.java)

            R.id.nav_logout ->
                showLogoutDialog()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    // ── Generic navigation with department context ────────────────────────────
    private fun navigateTo(activityClass: Class<*>) {
        if (departmentId.isNotEmpty()) {
            startActivity(
                Intent(this, activityClass).apply {
                    putExtra("DEPARTMENT_ID", departmentId)
                    putExtra("DEPARTMENT_NAME", departmentName)
                    putExtra("HOD_NAME", hodName)
                }
            )
        } else {
            Toast.makeText(this, "⚠️ Please wait, department loading…", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (departmentId.isNotEmpty()) {
            loadDepartmentStats()
            updateNavigationHeader()
        }
    }
}