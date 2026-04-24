package com.projectbyyatin.synapsemis

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.animation.LinearInterpolator
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
import com.google.android.material.navigation.NavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Student

class StudentDashboardActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var navView: NavigationView
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private lateinit var tvStudentName: TextView
    private lateinit var tvRollNumber: TextView
    private lateinit var tvSemester: TextView
    private lateinit var tvClassName: TextView
    private lateinit var tvAttendance: TextView
    private lateinit var tvCgpa: TextView

    private lateinit var btnAttendance: MaterialCardView
    private lateinit var btnMarks: MaterialCardView
    private lateinit var btnTimetable: MaterialCardView
    private lateinit var btnProfile: MaterialCardView
    private lateinit var btnAssignments: MaterialCardView
    private lateinit var btnClass: MaterialCardView

    private val starViews = mutableListOf<ImageView>()
    private var currentStudent: Student? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_dashboard)

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        startStarAnimations()
        setupCards()
        loadStudentData()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        toolbar      = findViewById(R.id.toolbar)
        navView      = findViewById(R.id.nav_view)

        tvStudentName = findViewById(R.id.tv_student_name)
        tvRollNumber  = findViewById(R.id.tv_roll_number)
        tvSemester    = findViewById(R.id.tv_semester)
        tvClassName   = findViewById(R.id.tv_class_name)
        tvAttendance  = findViewById(R.id.tv_attendance)
        tvCgpa        = findViewById(R.id.tv_cgpa)

        btnAttendance  = findViewById(R.id.btn_attendance)
        btnMarks       = findViewById(R.id.btn_marks)
        btnTimetable   = findViewById(R.id.btn_timetable)
        btnProfile     = findViewById(R.id.btn_profile)
        btnAssignments = findViewById(R.id.btn_assignments)
        btnClass       = findViewById(R.id.btn_class)

        for (i in 1..10) {
            val starId  = resources.getIdentifier("dashboard_star$i", "id", packageName)
            val starView = findViewById<ImageView>(starId)
            if (starView != null) starViews.add(starView)
        }

        firestore = FirebaseFirestore.getInstance()
        auth      = FirebaseAuth.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Student Dashboard"
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navView.setNavigationItemSelectedListener(this)
    }

    private fun startStarAnimations() {
        starViews.forEachIndexed { index, imageView ->
            val duration = (1500..3500).random().toLong()
            ObjectAnimator.ofFloat(imageView, "alpha", 0.2f, 1.0f).apply {
                this.duration  = duration
                repeatCount    = ObjectAnimator.INFINITE
                repeatMode     = ObjectAnimator.REVERSE
                interpolator   = LinearInterpolator()
                startDelay     = (index * 200).toLong()
                start()
            }
            ObjectAnimator.ofFloat(imageView, "rotation", 0f, 360f).apply {
                this.duration = (5000..10000).random().toLong()
                repeatCount   = ObjectAnimator.INFINITE
                interpolator  = LinearInterpolator()
                start()
            }
        }
    }

    private fun loadStudentData() {
        val currentUser = auth.currentUser ?: run {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("students")
            .whereEqualTo("userId", currentUser.uid)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document    = documents.documents[0]
                    val studentData = document.data

                    currentStudent = Student(
                        id                       = document.id,
                        userId                   = currentUser.uid,
                        fullName                 = studentData?.get("fullName")?.toString() ?: "",
                        rollNumber               = studentData?.get("rollNumber")?.toString() ?: "",
                        currentSemester          = (studentData?.get("currentSemester") as? Long)?.toInt() ?: 1,
                        classId                  = studentData?.get("classId")?.toString() ?: "",
                        className                = studentData?.get("className")?.toString() ?: "",
                        totalAttendancePercentage = (studentData?.get("totalAttendancePercentage") as? Double) ?: 0.0,
                        cgpa                     = (studentData?.get("cgpa") as? Double) ?: 0.0,
                        email                    = studentData?.get("email")?.toString() ?: "",
                        isActive                 = studentData?.get("isActive") as? Boolean ?: true
                    )

                    displayStudentInfo()
                    updateNavigationHeader()
                    updateClassCard()
                } else {
                    Toast.makeText(this, "Student profile not found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("StudentDashboard", "Error loading student data", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayStudentInfo() {
        currentStudent?.let { student ->
            tvStudentName.text = student.fullName.ifBlank { "Student" }
            tvRollNumber.text  = student.rollNumber.ifBlank { "N/A" }
            tvSemester.text    = "${student.currentSemester}"
            tvClassName.text   = student.className.ifBlank { "No Class" }
            tvAttendance.text  = "${student.totalAttendancePercentage.toInt()}%"
            tvCgpa.text        = String.format("%.1f", student.cgpa)
        }
    }

    private fun updateNavigationHeader() {
        try {
            val headerView      = navView.getHeaderView(0)
            val navStudentName  = headerView.findViewById<TextView>(R.id.nav_student_name)
            val navStudentEmail = headerView.findViewById<TextView>(R.id.nav_student_email)
            currentStudent?.let {
                navStudentName.text  = it.fullName.ifBlank { "Student" }
                navStudentEmail.text = it.email.ifBlank { "No email" }
            }
        } catch (e: Exception) {
            Log.e("StudentDashboard", "Error updating nav header", e)
        }
    }

    private fun setupCards() {
        btnAttendance.setOnClickListener {
            Toast.makeText(this, "📚 Attendance - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnMarks.setOnClickListener {
            Toast.makeText(this, "📈 Marks - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        // ── FIX: corrected all references from `currentStudent.` / `student.`
        //         (currentStudent was used while null-safety not applied, and
        //          `student` variable didn't exist in that scope).
        //         Now using a local val with an early return guard.
        btnTimetable.setOnClickListener {
            val student = currentStudent
            if (student == null) {
                Toast.makeText(this, "Loading profile, please wait…", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, StudentTimetableActivity::class.java).apply {
                // userId is the Firebase UID — used internally to match attendance records
                putExtra("STUDENT_ID",   student.userId)
                putExtra("STUDENT_NAME", student.fullName)
                putExtra("CLASS_ID",     student.classId)
                putExtra("CLASS_NAME",   student.className)
                putExtra("SEMESTER",     student.currentSemester)
                putExtra("THRESHOLD",    75f)
            })
        }

        btnProfile.setOnClickListener {
            Toast.makeText(this, "👤 Profile - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnAssignments.setOnClickListener {
            Toast.makeText(this, "📝 Assignments - Coming Soon!", Toast.LENGTH_SHORT).show()
        }

        btnClass.setOnClickListener {
            if (currentStudent?.classId.isNullOrEmpty() != false) {
                startActivity(Intent(this, JoinClassActivity::class.java))
            } else {
                Toast.makeText(this, "✅ Already joined ${currentStudent?.className}!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateClassCard() {
        val classTitle    = btnClass.findViewById<TextView>(R.id.class_title)
        val classSubtitle = btnClass.findViewById<TextView>(R.id.class_subtitle)
        val classIcon     = btnClass.findViewById<ImageView>(R.id.class_icon)

        val student = currentStudent
        if (student == null) {
            classTitle.text    = "Loading..."
            classSubtitle.text = "Please wait"
            return
        }

        if (student.classId.isBlank()) {
            classTitle.text    = "Join Class"
            classSubtitle.text = "Tap to join using code"
            classIcon.setImageResource(android.R.drawable.ic_menu_add)
            btnClass.setCardBackgroundColor(0x33FF6B35.toInt())
        } else {
            classTitle.text    = student.className
            classSubtitle.text = "View details"
            classIcon.setImageResource(android.R.drawable.ic_menu_info_details)
            btnClass.setCardBackgroundColor(0x33667EEA.toInt())
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard  -> { /* already here */ }
            R.id.nav_attendance -> Toast.makeText(this, "📚 Attendance - Coming Soon!", Toast.LENGTH_SHORT).show()
            R.id.nav_marks      -> Toast.makeText(this, "📈 Marks - Coming Soon!", Toast.LENGTH_SHORT).show()
            R.id.nav_timetable  -> {
                val student = currentStudent
                if (student != null) {
                    startActivity(Intent(this, StudentTimetableActivity::class.java).apply {
                        putExtra("STUDENT_ID",   student.userId)
                        putExtra("STUDENT_NAME", student.fullName)
                        putExtra("CLASS_ID",     student.classId)
                        putExtra("CLASS_NAME",   student.className)
                        putExtra("SEMESTER",     student.currentSemester)
                        putExtra("THRESHOLD",    75f)
                    })
                } else {
                    Toast.makeText(this, "Loading profile, please wait…", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.nav_assignments -> Toast.makeText(this, "📝 Assignments - Coming Soon!", Toast.LENGTH_SHORT).show()
            R.id.nav_join_class  -> {
                if (currentStudent?.classId.isNullOrEmpty() != false) {
                    startActivity(Intent(this, JoinClassActivity::class.java))
                } else {
                    Toast.makeText(this, "✅ Already joined ${currentStudent?.className}!", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.nav_profile  -> Toast.makeText(this, "👤 Profile - Coming Soon!", Toast.LENGTH_SHORT).show()
            R.id.nav_settings -> Toast.makeText(this, "⚙️ Settings - Coming Soon!", Toast.LENGTH_SHORT).show()
            R.id.nav_logout   -> logout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun logout() {
        auth.signOut()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}