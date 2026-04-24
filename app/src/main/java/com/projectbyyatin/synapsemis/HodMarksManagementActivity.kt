package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.HodMarksModerationListActivity.SubjectWithModerationStatus
import com.projectbyyatin.synapsemis.adapters.HodModerationSubjectsAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.Subject

/**
 * HodMarksManagementActivity
 *
 * Shows all department subjects for a SINGLE exam and lets the HOD tap into
 * each one for moderation. Replaces the old screen that had broken HOD lookup,
 * wrong adapter signature, and incorrect intent target.
 *
 * Flow:
 *   1. Load HOD doc from "hod" collection  → get departmentId
 *   2. Load all active subjects where departmentId matches
 *   3. Load the exam doc → filter its subjects to department ones
 *   4. Read subjectsMarksStatus from exam doc → populate isSubmitted / isModerated
 *   5. Bind to HodModerationSubjectsAdapter
 *   6. On tap → guard (locked / not submitted) then launch HodMarksModerationActivity
 */
class HodMarksManagementActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "HodMarksManagement"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var examNameText: TextView
    private lateinit var statsText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: HodModerationSubjectsAdapter

    private var examId = ""
    private var hodId = ""
    private var departmentId = ""

    // All department subjects for the current exam, with status attached
    private val subjectStatusList = mutableListOf<SubjectWithModerationStatus>()

    // Keep the loaded exam so onResume can reload without re-fetching HOD info
    private var currentExam: Exam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_marks_management)

        examId = intent.getStringExtra("EXAM_ID") ?: ""
        // Optional: caller may pass these to skip redundant Firestore reads
        hodId        = intent.getStringExtra("HOD_ID")        ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        if (examId.isBlank()) {
            Toast.makeText(this, "Missing exam data", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        firestore = FirebaseFirestore.getInstance()
        auth      = FirebaseAuth.getInstance()

        if (hodId.isEmpty()) hodId = auth.currentUser?.uid ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadHodInfo()
    }

    // ─── UI Setup ─────────────────────────────────────────────────────────────

    private fun initializeViews() {
        toolbar       = findViewById(R.id.toolbar)
        examNameText  = findViewById(R.id.exam_name)
        statsText     = findViewById(R.id.stats_text)
        recyclerView  = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Moderate Marks"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = HodModerationSubjectsAdapter { item ->
            openModeration(item)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    // ─── Data Loading ─────────────────────────────────────────────────────────

    /**
     * Step 1 — Resolve HOD's departmentId.
     * HOD records live in the "hod" collection, NOT "users" or "faculty".
     */
    private fun loadHodInfo() {
        showLoading(true)

        // If departmentId was passed by the caller we can skip this round-trip
        if (departmentId.isNotBlank()) {
            loadDepartmentSubjects()
            return
        }

        Log.d(TAG, "Loading HOD info for: $hodId")
        firestore.collection("hod").document(hodId)
            .get()
            .addOnSuccessListener { hodDoc ->
                departmentId = hodDoc.getString("departmentId") ?: ""
                Log.d(TAG, "HOD department: $departmentId")

                if (departmentId.isBlank()) {
                    showLoading(false)
                    Toast.makeText(this, "Department not assigned to HOD", Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                loadDepartmentSubjects()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "HOD lookup failed: ${e.message}", e)
                showLoading(false)
                Toast.makeText(this, "Error loading HOD data", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    /**
     * Step 2 — Load all active subjects that belong to this HOD's department.
     * Filtered client-side against the exam's subject list in the next step.
     */
    private fun loadDepartmentSubjects() {
        Log.d(TAG, "Loading department subjects for: $departmentId")

        firestore.collection("subjects")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { docs ->
                val departmentSubjects = docs.map { doc ->
                    doc.toObject(Subject::class.java).apply { id = doc.id }
                }
                Log.d(TAG, "Found ${departmentSubjects.size} department subjects")
                loadExam(departmentSubjects)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Subject load failed: ${e.message}", e)
                showLoading(false)
                Toast.makeText(this, "Error loading subjects", Toast.LENGTH_SHORT).show()
            }
    }

    /**
     * Step 3 — Load the exam document.
     * Step 4 — Cross-reference exam.subjects with departmentSubjects and
     *           attach isSubmitted / isModerated from subjectsMarksStatus.
     */
    private fun loadExam(departmentSubjects: List<Subject>) {
        Log.d(TAG, "Loading exam: $examId")

        firestore.collection("exams").document(examId)
            .get()
            .addOnSuccessListener { examDoc ->
                val exam = examDoc.toObject(Exam::class.java)?.apply { id = examDoc.id }
                if (exam == null) {
                    showLoading(false)
                    Toast.makeText(this, "Exam not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }

                currentExam = exam
                examNameText.text = exam.examName

                // Read the status map written by FacultyMarksEntryActivity and
                // HodMarksModerationActivity into exam.subjectsMarksStatus
                val statusMap = examDoc.get("subjectsMarksStatus") as? Map<*, *> ?: emptyMap<String, Any>()

                // Filter exam subjects to department ones only
                subjectStatusList.clear()
                exam.subjects
                    .filter { examSubject ->
                        departmentSubjects.any { it.id == examSubject.subjectId }
                    }
                    .forEach { examSubject ->
                        val subjectStatus = statusMap[examSubject.subjectId] as? Map<*, *>
                        subjectStatusList.add(
                            SubjectWithModerationStatus(
                                examSubject  = examSubject,
                                isSubmitted  = subjectStatus?.get("marksSubmitted") as? Boolean ?: false,
                                isModerated  = subjectStatus?.get("moderated")      as? Boolean ?: false
                            )
                        )
                    }

                subjectStatusList.sortBy { it.examSubject.subjectName }

                Log.d(TAG, "Department subjects in exam: ${subjectStatusList.size}")

                updateStats()
                adapter.updateList(subjectStatusList)
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Exam load failed: ${e.message}", e)
                showLoading(false)
                Toast.makeText(this, "Error loading exam: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    /**
     * Guards:
     *  • Already moderated → toast, no navigation
     *  • Not yet submitted → toast, no navigation
     *  • Ready             → launch HodMarksModerationActivity
     */
    private fun openModeration(item: SubjectWithModerationStatus) {
        val subject = item.examSubject

        if (item.isModerated) {
            Toast.makeText(this, "🔒 Already moderated and locked", Toast.LENGTH_SHORT).show()
            return
        }
        if (!item.isSubmitted) {
            Toast.makeText(this, "⏳ Faculty hasn't submitted marks yet", Toast.LENGTH_SHORT).show()
            return
        }

        val courseId = currentExam?.courseId?.takeIf { it.isNotBlank() }
            ?: subject.courseId.takeIf { it.isNotBlank() }
            ?: run {
                Toast.makeText(this, "Missing course ID", Toast.LENGTH_SHORT).show()
                return
            }

        Log.d(TAG, "Opening moderation — subject: ${subject.subjectId}")

        val intent = Intent(this, HodMarksModerationActivity::class.java).apply {
            putExtra("EXAM_ID",       examId)
            putExtra("SUBJECT_ID",    subject.subjectId)
            putExtra("SUBJECT_NAME",  subject.subjectName)
            putExtra("COURSE_ID",     courseId)
            putExtra("MAX_MARKS",     subject.maxMarks)
            putExtra("HOD_ID",        hodId)
            putExtra("DEPARTMENT_ID", departmentId)
        }
        startActivity(intent)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private fun updateStats() {
        val total      = subjectStatusList.size
        val moderated  = subjectStatusList.count { it.isModerated }
        val pending    = subjectStatusList.count { it.isSubmitted && !it.isModerated }
        val notReady   = total - moderated - pending

        statsText.text = "Total: $total  |  Moderated: $moderated  |  Pending: $pending  |  Not submitted: $notReady"
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility    = if (show) View.GONE    else View.VISIBLE
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    /**
     * Called when returning from HodMarksModerationActivity.
     * Re-fetches the exam document so any newly moderated subjects
     * immediately reflect their locked state.
     */
    override fun onResume() {
        super.onResume()
        if (departmentId.isNotBlank() && ::adapter.isInitialized) {
            loadDepartmentSubjects()
        }
    }
}