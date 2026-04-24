package com.projectbyyatin.synapsemis

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.os.Trace.isEnabled
import android.view.MenuItem
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CoeDashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // Stats TextViews
    private lateinit var totalStudentsText: TextView
    private lateinit var totalFacultyText: TextView
    private lateinit var totalDepartmentsText: TextView
    private lateinit var totalClassesText: TextView
    private lateinit var avgAttendanceText: TextView
    private lateinit var avgExamMarksText: TextView
    private lateinit var complaintsCountText: TextView

    // Quick Action Cards
    private lateinit var btnAttendance: MaterialCardView
    private lateinit var btnExams: MaterialCardView
    private lateinit var btnTimetable: MaterialCardView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coe_dashboard)

        // Initialize Firebase
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Initialize views
        initializeViews()

        // Setup toolbar
        setupToolbar()

        // Setup navigation drawer
        setupNavigationDrawer()

        // Load dashboard data
        loadDashboardData()

        animateStars()

        // Setup click listeners
        setupClickListeners()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    // Let the system handle back press (finish activity, navigate back, etc.)
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }}
            )
    }



    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        // Stats TextViews
        totalStudentsText = findViewById(R.id.total_students)
        totalFacultyText = findViewById(R.id.total_faculty)
        totalDepartmentsText = findViewById(R.id.total_departments)
        totalClassesText = findViewById(R.id.total_classes)
        avgAttendanceText = findViewById(R.id.avg_attendance)
        avgExamMarksText = findViewById(R.id.avg_exam_marks)
        complaintsCountText = findViewById(R.id.complaints_count)

        // Quick Action Cards
        btnAttendance = findViewById(R.id.btn_attendance)
        btnExams = findViewById(R.id.btn_exams)
        btnTimetable = findViewById(R.id.btn_timetable)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
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

        // Update header with user info
        updateNavigationHeader()
    }

    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val nameTextView = headerView.findViewById<TextView>(R.id.nav_header_name)
        val emailTextView = headerView.findViewById<TextView>(R.id.nav_header_email)
        val roleTextView = headerView.findViewById<TextView>(R.id.nav_header_role)

        val currentUser = auth.currentUser
        if (currentUser != null) {
            firestore.collection("users").document(currentUser.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        nameTextView.text = document.getString("name") ?: "Vice Principal"
                        emailTextView.text = document.getString("email") ?: "coe@college.com"
                        roleTextView.text = "Controller of Examinations"
                    }
                }
        }
    }

    private fun loadDashboardData() {
        // Load total students
        firestore.collection("students")
            .get()
            .addOnSuccessListener { documents ->
                totalStudentsText.text = documents.size().toString()
            }

        // Load total faculty
        firestore.collection("faculty")
            .get()
            .addOnSuccessListener { documents ->
                totalFacultyText.text = (documents.size()/2).toString()
            }

        // Load total departments
        firestore.collection("departments")
            .get()
            .addOnSuccessListener { documents ->
                totalDepartmentsText.text = documents.size().toString()
            }

        // Load total classes
        firestore.collection("classes")
            .get()
            .addOnSuccessListener { documents ->
                totalClassesText.text = documents.size().toString()
            }

        // Load average attendance
        firestore.collection("attendance")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.size() > 0) {
                    var totalAttendance = 0.0
                    for (document in documents) {
                        totalAttendance += document.getDouble("percentage") ?: 0.0
                    }
                    val avgAttendance = totalAttendance / documents.size()
                    avgAttendanceText.text = String.format("%.1f%%", avgAttendance)
                }
            }

        // Load average exam marks
        firestore.collection("exams")
            .get()
            .addOnSuccessListener { documents ->
                if (documents.size() > 0) {
                    var totalMarks = 0.0
                    for (document in documents) {
                        totalMarks += document.getDouble("marks") ?: 0.0
                    }
                    val avgMarks = totalMarks / documents.size()
                    avgExamMarksText.text = String.format("%.1f%%", avgMarks)
                }
            }

        // Load pending complaints
        firestore.collection("complaints")
            .whereEqualTo("status", "pending")
            .get()
            .addOnSuccessListener { documents ->
                complaintsCountText.text = documents.size().toString()
            }
    }

    private fun setupClickListeners() {
        btnAttendance.setOnClickListener {
            startActivity(Intent(this, AttendanceReportActivity_COE::class.java))
        }

        btnExams.setOnClickListener {
            startActivity(Intent(this, ExamManagementActivity::class.java))
        }

        btnTimetable.setOnClickListener {
            startActivity(Intent(this, TimetableListActivity::class.java))
        }

        /*fabAdd.setOnClickListener {
            showAddOptions()
        }*/
    }


    private fun showAddOptions() {
        val options = arrayOf("Add Event", "Add Exam", "Add Announcement", "Add Committee")

        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
        builder.setTitle("Quick Add")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> Toast.makeText(this, "Add Event", Toast.LENGTH_SHORT).show()
                    1 -> Toast.makeText(this, "Add Exam", Toast.LENGTH_SHORT).show()
                    2 -> Toast.makeText(this, "Add Announcement", Toast.LENGTH_SHORT).show()
                    3 -> Toast.makeText(this, "Add Committee", Toast.LENGTH_SHORT).show()
                }
            }
        builder.create().show()
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> {
                // Already on dashboard
            }
            R.id.nav_departments -> {
                startActivity(Intent(this, DepartmentsActivity::class.java))
                finish()
            }

            R.id.nav_students -> {
                startActivity(Intent(this, StudentsActivity::class.java))
            }


            R.id.nav_events -> {
                Toast.makeText(this, "Events", Toast.LENGTH_SHORT).show()
                // TODO: startActivity(Intent(this, EventsActivity::class.java))
            }
            R.id.nav_attendance -> {
                startActivity(Intent(this, AttendanceReportActivity_COE::class.java))
            }

            R.id.nav_mark_attendance->{
                startActivity(Intent(this, MarkAttendanceCOEActivity::class.java))
            }
            R.id.nav_exams -> {
                startActivity(Intent(this, ExamManagementActivity::class.java))
            }

            R.id.nav_timetable -> {
                startActivity(Intent(this, TimetableListActivity::class.java))
            }
            R.id.nav_complaints -> {
                Toast.makeText(this, "Complaints", Toast.LENGTH_SHORT).show()
                // TODO: startActivity(Intent(this, ComplaintsActivity::class.java))
            }
            R.id.nav_committees -> {
                Toast.makeText(this, "Committees", Toast.LENGTH_SHORT).show()
                // TODO: startActivity(Intent(this, CommitteesActivity::class.java))
            }
            R.id.nav_chat -> {
                Toast.makeText(this, "Chat", Toast.LENGTH_SHORT).show()
                // TODO: startActivity(Intent(this, ChatActivity::class.java))
            }
            R.id.nav_profile -> {
                Toast.makeText(this, "Edit Profile", Toast.LENGTH_SHORT).show()
                // TODO: startActivity(Intent(this, ProfileActivity::class.java))
            }

            R.id.nav_faculties -> {
                startActivity(Intent(this, FacultyActivity::class.java))
                finish()
            }
            R.id.nav_logout -> {
                logout()
            }

            R.id.nav_applications -> {
                startActivity(Intent(this, CoeApplicationReviewActivity::class.java))
                drawerLayout.closeDrawers()
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun logout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun animateStars() {
        val stars = listOf(
            findViewById<ImageView>(R.id.dashboard_star1),
            findViewById<ImageView>(R.id.dashboard_star2),
            findViewById<ImageView>(R.id.dashboard_star3),
            findViewById<ImageView>(R.id.dashboard_star4),
            findViewById<ImageView>(R.id.dashboard_star5),
            findViewById<ImageView>(R.id.dashboard_star6),
            findViewById<ImageView>(R.id.dashboard_star7),
            findViewById<ImageView>(R.id.dashboard_star8),
            findViewById<ImageView>(R.id.dashboard_star9),
            findViewById<ImageView>(R.id.dashboard_star10),
            findViewById<ImageView>(R.id.dashboard_star11),
            findViewById<ImageView>(R.id.dashboard_star12)
        )

        stars.forEachIndexed { index, star ->
            val animator = ObjectAnimator.ofFloat(star, "alpha", 0.3f, 1f, 0.3f)
            animator.duration = 2000 + (index * 200L)
            animator.repeatCount = ValueAnimator.INFINITE
            animator.repeatMode = ValueAnimator.RESTART
            animator.startDelay = index * 300L
            animator.start()
        }
    }
}