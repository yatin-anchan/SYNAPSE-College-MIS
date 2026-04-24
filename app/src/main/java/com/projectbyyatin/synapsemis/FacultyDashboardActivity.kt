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
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import java.util.Calendar

class FacultyDashboardActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {

    companion object {
        private const val TAG = "FacultyDashboard"
    }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var welcomeText: TextView
    private lateinit var assignedSubjectsText: TextView
    private lateinit var assignedClassesText: TextView
    private lateinit var todayClassesText: TextView
    private lateinit var pendingTasksText: TextView
    private lateinit var departmentNameText: TextView

    private lateinit var btnMySubjects: MaterialCardView
    private lateinit var btnMyClasses: MaterialCardView
    private lateinit var btnMarkAttendance: MaterialCardView
    private lateinit var btnViewAttendance: MaterialCardView
    private lateinit var btnExamSchedule: MaterialCardView
    private lateinit var btnMyTimetable: MaterialCardView
    private lateinit var btnMarksEntry: MaterialCardView
    private lateinit var btnSessionRound: MaterialCardView

    private var facultyId = ""       // Firebase Auth UID — used for attendance, exams, subjects
    private var facultyDocId = ""    // Firestore faculty doc ID — used for timetable slot matching
    private var facultyName = ""
    private var departmentId = ""
    private var departmentName = ""
    private var facultyEmail = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_dashboard)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupNavigationDrawer()
        loadFacultyInfo()
        setupClickListeners()
        setupBackPress()
    }

    private fun initializeViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
        toolbar = findViewById(R.id.toolbar)

        welcomeText = findViewById(R.id.welcome_text)
        assignedSubjectsText = findViewById(R.id.assigned_subjects)
        assignedClassesText = findViewById(R.id.assigned_classes)
        todayClassesText = findViewById(R.id.today_classes)
        pendingTasksText = findViewById(R.id.pending_tasks)
        departmentNameText = findViewById(R.id.department_name_text)

        btnMySubjects = findViewById(R.id.btn_my_subjects)
        btnMyClasses = findViewById(R.id.btn_my_classes)
        btnMarkAttendance = findViewById(R.id.btn_mark_attendance)
        btnViewAttendance = findViewById(R.id.btn_view_attendance)
        btnExamSchedule = findViewById(R.id.btn_exam_schedule)
        btnMyTimetable = findViewById(R.id.btn_my_timetable)
        btnMarksEntry = findViewById(R.id.btn_marks_entry)
        btnSessionRound = findViewById(R.id.btn_session_round)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Faculty Dashboard"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setupNavigationDrawer() {
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        navigationView.setNavigationItemSelectedListener(this)
        updateNavigationHeader()
    }

    private fun updateNavigationHeader() {
        val headerView = navigationView.getHeaderView(0)
        val nameTextView = headerView.findViewById<TextView>(R.id.nav_header_name)
        val emailTextView = headerView.findViewById<TextView>(R.id.nav_header_email)
        val roleTextView = headerView.findViewById<TextView>(R.id.nav_header_role)

        val user = auth.currentUser ?: return

        // Query by userId field (Auth UID) to find the faculty document
        firestore.collection("faculty")
            .whereEqualTo("userId", user.uid)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                val doc = docs.documents.firstOrNull()
                if (doc != null) {
                    val name = doc.getString("name") ?: "Faculty"
                    val dept = doc.getString("department") ?: "Department"
                    val email = doc.getString("email") ?: user.email ?: ""

                    nameTextView.text = name
                    emailTextView.text = email
                    roleTextView.text = "Faculty - $dept"
                    Log.d(TAG, "Navigation header updated: $name, $dept")
                } else {
                    // Fallback: use Auth email
                    nameTextView.text = user.displayName ?: "Faculty"
                    emailTextView.text = user.email ?: ""
                    roleTextView.text = "Faculty"
                    Log.w(TAG, "Nav header: no faculty doc found for uid ${user.uid}")
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to update nav header: ${e.message}", e)
            }
    }

    private fun loadFacultyInfo() {
        val user = auth.currentUser ?: run {
            Log.e(TAG, "No authenticated user")
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        facultyId = user.uid
        Log.d(TAG, "Loading faculty info for Auth UID: $facultyId")

        // ✅ FIX: Query by "userId" field, NOT by document ID.
        // Faculty collection uses its own Firestore doc IDs (e.g. "Le4TMMAiqGgP4SLKB7Gh"),
        // which are different from the Firebase Auth UID stored in the "userId" field.
        firestore.collection("faculty")
            .whereEqualTo("userId", user.uid)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                val doc = docs.documents.firstOrNull()

                if (doc == null) {
                    // Try fallback: query by email
                    Log.w(TAG, "⚠️ No faculty doc with userId=${user.uid}, trying email fallback")
                    loadFacultyInfoByEmail(user.email ?: "")
                    return@addOnSuccessListener
                }

                // ✅ This is the Firestore doc ID used in timetable slots' "facultyId" field
                facultyDocId = doc.id
                facultyName = doc.getString("name") ?: "Faculty"
                departmentId = doc.getString("departmentId") ?: ""
                departmentName = doc.getString("department") ?: "Department"
                facultyEmail = doc.getString("email") ?: user.email ?: ""

                Log.d(TAG, "✅ Faculty loaded: $facultyName | authUid=$facultyId | docId=$facultyDocId")

                welcomeText.text = "Welcome, $facultyName"
                departmentNameText.text = departmentName
                loadFacultyStats()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load faculty info: ${e.message}", e)
                Toast.makeText(this, "Failed to load faculty info", Toast.LENGTH_SHORT).show()
                loadFacultyStats()
            }
    }

    /**
     * Fallback: if no "userId" field matches, try matching by email.
     * Also extracts the Firestore doc ID for timetable lookups.
     */
    private fun loadFacultyInfoByEmail(email: String) {
        if (email.isEmpty()) {
            Log.e(TAG, "❌ No email available for fallback lookup")
            welcomeText.text = "Welcome, Faculty"
            departmentNameText.text = "Department"
            loadFacultyStats()
            return
        }

        firestore.collection("faculty")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                val doc = docs.documents.firstOrNull()
                if (doc != null) {
                    facultyDocId = doc.id   // ✅ Firestore doc ID for timetable matching
                    facultyName = doc.getString("name") ?: "Faculty"
                    departmentId = doc.getString("departmentId") ?: ""
                    departmentName = doc.getString("department") ?: "Department"
                    facultyEmail = email

                    Log.d(TAG, "✅ Faculty loaded via email: $facultyName | docId=$facultyDocId")
                } else {
                    Log.w(TAG, "⚠️ Faculty NOT FOUND by email: $email — timetable won't filter correctly")
                    facultyDocId = ""
                    facultyName = "Faculty"
                    departmentName = "Department"
                    facultyEmail = email
                }

                welcomeText.text = "Welcome, $facultyName"
                departmentNameText.text = departmentName
                loadFacultyStats()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Email fallback failed: ${e.message}", e)
                welcomeText.text = "Welcome, Faculty"
                departmentNameText.text = "Department"
                loadFacultyStats()
            }
    }

    private fun loadFacultyStats() {
        Log.d(TAG, "Loading faculty stats... docId=$facultyDocId")

        assignedSubjectsText.text = "⏳"
        assignedClassesText.text = "⏳"
        todayClassesText.text = "⏳"
        pendingTasksText.text = "⏳"

        loadAssignedSubjects()
        loadAssignedClasses()
        loadTodayClasses()
        loadPendingTasks()
    }

    private fun loadAssignedSubjects() {
        Log.d(TAG, "📚 Loading assigned subjects for faculty: $facultyId")

        firestore.collection("subjects")
            .whereEqualTo("assignedFacultyId", facultyId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    assignedSubjectsText.text = docs.size().toString()
                    Log.d(TAG, "✅ Strategy 1: Found ${docs.size()} subjects by assignedFacultyId")
                    return@addOnSuccessListener
                }
                loadSubjectsByEmail()
            }
            .addOnFailureListener {
                Log.w(TAG, "Strategy 1 failed, trying email")
                loadSubjectsByEmail()
            }
    }

    private fun loadSubjectsByEmail() {
        if (facultyEmail.isEmpty()) {
            assignedSubjectsText.text = "0"
            return
        }
        firestore.collection("subjects")
            .whereEqualTo("assignedFacultyEmail", facultyEmail)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs ->
                assignedSubjectsText.text = docs.size().toString()
                Log.d(TAG, "✅ Strategy 2: Found ${docs.size()} subjects by email: $facultyEmail")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load subjects by email: ${e.message}", e)
                assignedSubjectsText.text = "0"
            }
    }

    private fun loadAssignedClasses() {
        Log.d(TAG, "📅 Loading today's lecture count for faculty docId: $facultyDocId")

        val today = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "Monday"
            Calendar.TUESDAY   -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY  -> "Thursday"
            Calendar.FRIDAY    -> "Friday"
            Calendar.SATURDAY  -> "Saturday"
            else               -> null
        }

        if (today == null) {
            assignedClassesText.text = "0"
            Log.d(TAG, "📅 Sunday — no lectures")
            return
        }

        firestore.collection("timetables")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { docs ->
                var lectureCount = 0

                docs.forEach { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val daySlots = (doc.get("schedule") as? Map<String, Any>)
                        ?.get(today) as? List<Map<String, Any>>
                        ?: return@forEach

                    daySlots.forEach { slot ->
                        val slotFacultyId = slot["facultyId"] as? String ?: ""
                        val slotType      = slot["slotType"]  as? String ?: ""
                        // ✅ Compare against facultyDocId (Firestore doc ID), NOT Auth UID
                        if (slotFacultyId == facultyDocId && slotType == "lecture") {
                            lectureCount++
                        }
                    }
                }

                assignedClassesText.text = lectureCount.toString()
                Log.d(TAG, "✅ Found $lectureCount lecture(s) today ($today) for docId=$facultyDocId")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "❌ Failed to load timetable: ${e.message}", e)
                assignedClassesText.text = "0"
            }
    }

    private fun loadTodayClasses() {
        val dayName = getCurrentDayName()
        Log.d(TAG, "📅 Loading today's classes for $dayName...")

        firestore.collection("timetable")
            .whereEqualTo("facultyId", facultyId)
            .whereEqualTo("day", dayName)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs ->
                todayClassesText.text = docs.size().toString()
                Log.d(TAG, "✅ Found ${docs.size()} classes for today ($dayName)")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load today's classes: ${e.message}", e)
                todayClassesText.text = "0"
            }
    }

    private fun getCurrentDayName(): String {
        return when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> "Monday"
            Calendar.TUESDAY   -> "Tuesday"
            Calendar.WEDNESDAY -> "Wednesday"
            Calendar.THURSDAY  -> "Thursday"
            Calendar.FRIDAY    -> "Friday"
            Calendar.SATURDAY  -> "Saturday"
            else               -> "Sunday"
        }
    }

    private fun loadPendingTasks() {
        Log.d(TAG, "📋 Loading pending tasks...")

        val attendanceTask = firestore.collection("attendance_schedule")
            .whereEqualTo("facultyId", facultyId)
            .whereEqualTo("marked", false)
            .get()

        val examsTask = firestore.collection("exams")
            .whereEqualTo("marksEntryEnabled", true)
            .whereEqualTo("isActive", true)
            .get()

        Tasks.whenAll(attendanceTask, examsTask)
            .addOnSuccessListener {
                var pendingCount = 0
                pendingCount += attendanceTask.result?.size() ?: 0
                pendingCount += examsTask.result?.size() ?: 0

                Log.d(TAG, "📌 Unmarked attendance: ${attendanceTask.result?.size() ?: 0}")
                Log.d(TAG, "📌 Pending exams: ${examsTask.result?.size() ?: 0}")

                pendingTasksText.text = pendingCount.toString()
                Log.d(TAG, "✅ Total pending tasks: $pendingCount")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to load pending tasks: ${e.message}", e)
                pendingTasksText.text = "0"
            }
    }

    private fun setupClickListeners() {
        btnMySubjects.setOnClickListener {
            startActivity(Intent(this, MySubjectsActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
                putExtra("FACULTY_NAME", facultyName)
                putExtra("DEPARTMENT_ID", departmentId)
            })
        }

        btnMyClasses.setOnClickListener {
            Toast.makeText(this, "My Classes - Coming Soon", Toast.LENGTH_SHORT).show()
        }

        // In FacultyDashboardActivity.kt — replace the btnMarkAttendance click listener:

        btnMarkAttendance.setOnClickListener {
            startActivity(Intent(this, MarkAttendanceFacultyActivity::class.java).apply {
                putExtra("FACULTY_ID",      facultyId)       // Auth UID
                putExtra("FACULTY_DOC_ID",  facultyDocId)    // Firestore doc ID
                putExtra("FACULTY_NAME",    facultyName)
                putExtra("FACULTY_EMAIL",   facultyEmail)
                putExtra("DEPARTMENT_ID",   departmentId)
                putExtra("DEPARTMENT_NAME", departmentName)
            })
        }

        btnViewAttendance.setOnClickListener {
            startActivity(Intent(this, ViewAttendanceFacultyActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)        // Auth UID
                putExtra("FACULTY_DOC_ID", facultyDocId) // Firestore doc ID
            })
        }

        btnExamSchedule.setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
//            startActivity(Intent(this, ManageExamsActivity::class.java).apply {
//                putExtra("FACULTY_ID", facultyId)
//                putExtra("FACULTY_NAME", facultyName)
//                putExtra("DEPARTMENT_ID", departmentId)
//            })
        }

        btnMyTimetable.setOnClickListener {
            if (facultyDocId.isEmpty()) {
                Toast.makeText(this, "Faculty profile not loaded yet, please wait", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "⚠️ Timetable button pressed but facultyDocId is empty!")
                return@setOnClickListener
            }
            // ✅ Pass facultyDocId (Firestore doc ID) — this matches what's stored in timetable slots
            Log.d(TAG, "Opening timetable with facultyDocId=$facultyDocId")
            startActivity(Intent(this, FacultyTimetableActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyDocId)
                putExtra("FACULTY_NAME", facultyName)
            })
        }

        btnMarksEntry.setOnClickListener {
            startActivity(Intent(this, FacultyMarksEntryListActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
                putExtra("FACULTY_NAME", facultyName)
                putExtra("DEPARTMENT_ID", departmentId)
            })
        }

        btnSessionRound.setOnClickListener {
            startActivity(Intent(this, SessionRoundActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
                putExtra("FACULTY_NAME", facultyName)
                putExtra("DEPARTMENT_ID", departmentId)
            })
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    AlertDialog.Builder(this@FacultyDashboardActivity)
                        .setTitle("Exit")
                        .setMessage("Do you want to exit?")
                        .setPositiveButton("Yes") { _, _ -> finish() }
                        .setNegativeButton("No", null)
                        .show()
                }
            }
        })
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_dashboard -> Unit
            R.id.nav_my_subjects -> startActivity(Intent(this, MySubjectsActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
                putExtra("FACULTY_NAME", facultyName)
                putExtra("DEPARTMENT_ID", departmentId)
            })
            R.id.nav_mark_attendance -> startActivity(Intent(this, MarkAttendanceFacultyActivity::class.java).apply {
                putExtra("FACULTY_ID",      facultyId)       // Auth UID
                putExtra("FACULTY_DOC_ID",  facultyDocId)    // Firestore doc ID
                putExtra("FACULTY_NAME",    facultyName)
                putExtra("FACULTY_EMAIL",   facultyEmail)
                putExtra("DEPARTMENT_ID",   departmentId)
                putExtra("DEPARTMENT_NAME", departmentName)
            })
            R.id.nav_view_attendance -> startActivity(Intent(this, ViewAttendanceFacultyActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)        // Auth UID
                putExtra("FACULTY_DOC_ID", facultyDocId) // Firestore doc ID
            })
            R.id.nav_marks_entry -> startActivity(Intent(this, FacultyMarksEntryListActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
                putExtra("FACULTY_NAME", facultyName)
                putExtra("DEPARTMENT_ID", departmentId)
            })
            R.id.nav_session_round -> startActivity(Intent(this, SessionRoundActivity::class.java).apply {
                putExtra("FACULTY_ID", facultyId)
                putExtra("FACULTY_NAME", facultyName)
                putExtra("DEPARTMENT_ID", departmentId)
            })
            R.id.nav_my_timetable -> {
                if (facultyDocId.isNotEmpty()) {
                    startActivity(Intent(this, FacultyTimetableActivity::class.java).apply {
                        putExtra("FACULTY_ID", facultyDocId)
                        putExtra("FACULTY_NAME", facultyName)
                    })
                } else {
                    Toast.makeText(this, "My Timetable - Coming Soon", Toast.LENGTH_SHORT).show()
                }
            }
            R.id.nav_profile -> Toast.makeText(this, "Profile - Coming Soon", Toast.LENGTH_SHORT).show()
            R.id.nav_logout -> logout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun logout() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Yes") { _, _ ->
                auth.signOut()
                startActivity(Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (facultyId.isNotEmpty()) {
            Log.d(TAG, "onResume: Reloading faculty stats")
            loadFacultyStats()
            updateNavigationHeader()
        }
    }
}