package com.projectbyyatin.synapsemis

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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.MySubjectsAdapter
import com.projectbyyatin.synapsemis.models.Subject

class MySubjectsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MySubjectsActivity"
    }

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View
    private lateinit var emptyText: TextView
    private lateinit var statsCard: View
    private lateinit var totalSubjectsText: TextView
    private lateinit var theorySubjectsText: TextView
    private lateinit var practicalSubjectsText: TextView
    private lateinit var chipGroup: ChipGroup
    private lateinit var chipAll: Chip
    private lateinit var chipTheory: Chip
    private lateinit var chipPractical: Chip
    private lateinit var chipElective: Chip

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: MySubjectsAdapter

    private val allSubjects = mutableListOf<Subject>()
    private val filteredSubjects = mutableListOf<Subject>()

    private var authUid = ""
    private var facultyDocId = ""
    private var facultyName = ""
    private var departmentId = ""
    private var selectedFilter = "all"
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_subjects)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        authUid = auth.currentUser?.uid ?: ""
        facultyDocId = intent.getStringExtra("FACULTY_ID") ?: ""
        facultyName = intent.getStringExtra("FACULTY_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        initializeViews()
        setupToolbar()
        setupChips()
        setupRecyclerView()

        // Only load once on create
        resolveFacultyDocumentId()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)
        emptyText = findViewById(R.id.empty_text)
        statsCard = findViewById(R.id.stats_card)
        totalSubjectsText = findViewById(R.id.total_subjects_text)
        theorySubjectsText = findViewById(R.id.theory_subjects_text)
        practicalSubjectsText = findViewById(R.id.practical_subjects_text)
        chipGroup = findViewById(R.id.chip_group)
        chipAll = findViewById(R.id.chip_all)
        chipTheory = findViewById(R.id.chip_theory)
        chipPractical = findViewById(R.id.chip_practical)
        chipElective = findViewById(R.id.chip_elective)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Subjects"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupChips() {
        chipAll.setOnClickListener {
            selectedFilter = "all"
            applyFilter()
        }

        chipTheory.setOnClickListener {
            selectedFilter = "theory"
            applyFilter()
        }

        chipPractical.setOnClickListener {
            selectedFilter = "practical"
            applyFilter()
        }

        chipElective.setOnClickListener {
            selectedFilter = "elective"
            applyFilter()
        }

        chipAll.isChecked = true
    }

    private fun setupRecyclerView() {
        adapter = MySubjectsAdapter(
            filteredSubjects,  // ← ADD THIS - pass the list
            onSubjectClick = { subject -> showSubjectDetails(subject) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        Log.d(TAG, "RecyclerView setup complete")
    }

    private fun resolveFacultyDocumentId() {
        if (isLoading) {
            Log.d(TAG, "Already loading, skipping duplicate call")
            return
        }

        isLoading = true
        showLoading(true)

        Log.d(TAG, "=== RESOLVING FACULTY DOCUMENT ID ===")
        Log.d(TAG, "Auth UID: $authUid")
        Log.d(TAG, "Passed Faculty Doc ID: $facultyDocId")

        if (facultyDocId.isNotEmpty()) {
            Log.d(TAG, "Using passed faculty doc ID: $facultyDocId")
            loadMySubjects()
            return
        }

        firestore.collection("faculty").document(authUid)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    Log.d(TAG, "Found faculty doc with Auth UID as doc ID")
                    facultyDocId = authUid
                    loadMySubjects()
                } else {
                    Log.d(TAG, "Auth UID is not doc ID, searching by id field")
                    findFacultyByIdField()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error checking faculty doc: ${e.message}", e)
                findFacultyByIdField()
            }
    }

    private fun findFacultyByIdField() {
        firestore.collection("faculty")
            .whereEqualTo("id", authUid)
            .limit(1)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    facultyDocId = documents.documents[0].id
                    Log.d(TAG, "Found faculty doc by id field: $facultyDocId")
                    loadMySubjects()
                } else {
                    Log.e(TAG, "No faculty document found for Auth UID: $authUid")
                    isLoading = false
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Faculty profile not found. Please contact administrator.",
                        Toast.LENGTH_LONG
                    ).show()
                    updateEmptyView()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error finding faculty by id field: ${e.message}", e)
                isLoading = false
                showLoading(false)
                Toast.makeText(this, "Error loading faculty data", Toast.LENGTH_SHORT).show()
                updateEmptyView()
            }
    }

    private fun loadMySubjects() {
        allSubjects.clear()

        Log.d(TAG, "=== LOADING SUBJECTS ===")
        Log.d(TAG, "Using Faculty Doc ID: $facultyDocId")

        loadByAssignedFacultyId()
    }

    private fun loadByAssignedFacultyId() {
        Log.d(TAG, "Strategy 1: Query by assignedFacultyId = $facultyDocId")

        firestore.collection("subjects")
            .whereEqualTo("assignedFacultyId", facultyDocId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Strategy 1: Found ${documents.size()} subjects")

                if (!documents.isEmpty) {
                    documents.forEach { document ->
                        val subject = document.toObject(Subject::class.java)
                        subject.id = document.id
                        Log.d(TAG, "  - ${subject.name} (${subject.code})")
                        allSubjects.add(subject)
                    }
                    finishLoadingSubjects()
                } else {
                    Log.d(TAG, "Strategy 1 found nothing, trying Strategy 2")
                    loadByFacultyEmail()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Strategy 1 error: ${e.message}", e)
                loadByFacultyEmail()
            }
    }

    private fun loadByFacultyEmail() {
        val email = auth.currentUser?.email ?: ""

        if (email.isEmpty()) {
            Log.e(TAG, "Strategy 2: No email available")
            loadBySubjectNames()
            return
        }

        Log.d(TAG, "Strategy 2: Query by assignedFacultyEmail = $email")

        firestore.collection("subjects")
            .whereEqualTo("assignedFacultyEmail", email)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Strategy 2: Found ${documents.size()} subjects")

                if (!documents.isEmpty) {
                    documents.forEach { document ->
                        val subject = document.toObject(Subject::class.java)
                        subject.id = document.id
                        Log.d(TAG, "  - ${subject.name} (${subject.code})")

                        if (!allSubjects.any { it.id == subject.id }) {
                            allSubjects.add(subject)
                        }
                    }
                    finishLoadingSubjects()
                } else {
                    Log.d(TAG, "Strategy 2 found nothing, trying Strategy 3")
                    loadBySubjectNames()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Strategy 2 error: ${e.message}", e)
                loadBySubjectNames()
            }
    }

    private fun loadBySubjectNames() {
        Log.d(TAG, "Strategy 3: Load by subject names from faculty doc")

        firestore.collection("faculty").document(facultyDocId)
            .get()
            .addOnSuccessListener { facultyDoc ->
                if (!facultyDoc.exists()) {
                    Log.e(TAG, "Strategy 3: Faculty doc not found")
                    finishLoadingSubjects()
                    return@addOnSuccessListener
                }

                val subjectNames = facultyDoc.get("subjects") as? List<String> ?: emptyList()
                Log.d(TAG, "Strategy 3: Found ${subjectNames.size} subject names in faculty doc")

                if (subjectNames.isEmpty()) {
                    finishLoadingSubjects()
                    return@addOnSuccessListener
                }

                loadSubjectsBatch(subjectNames, 0)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Strategy 3 error: ${e.message}", e)
                finishLoadingSubjects()
            }
    }

    private fun loadSubjectsBatch(subjectNames: List<String>, startIndex: Int) {
        if (startIndex >= subjectNames.size) {
            finishLoadingSubjects()
            return
        }

        val endIndex = minOf(startIndex + 10, subjectNames.size)
        val batch = subjectNames.subList(startIndex, endIndex)

        Log.d(TAG, "Loading batch $startIndex to $endIndex: $batch")

        firestore.collection("subjects")
            .whereIn("name", batch)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "Batch returned ${documents.size()} subjects")

                documents.forEach { document ->
                    val subject = document.toObject(Subject::class.java)
                    subject.id = document.id

                    if (!allSubjects.any { it.id == subject.id }) {
                        allSubjects.add(subject)
                    }
                }

                loadSubjectsBatch(subjectNames, endIndex)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Batch loading error: ${e.message}", e)
                finishLoadingSubjects()
            }
    }

    private fun finishLoadingSubjects() {
        Log.d(TAG, "=== FINISH LOADING: Total ${allSubjects.size} subjects ===")

        // Sort by semester and name
        allSubjects.sortWith(compareBy({ it.semesterNumber }, { it.name }))

        // Update stats BEFORE applying filter
        updateStats()

        // Apply filter which will update adapter
        applyFilter()

        // Hide loading and update empty view
        isLoading = false
        showLoading(false)
        updateEmptyView()

        Log.d(TAG, "Filtered subjects count: ${filteredSubjects.size}")
        Log.d(TAG, "Adapter item count: ${adapter.itemCount}")
    }

    private fun applyFilter() {
        filteredSubjects.clear()

        when (selectedFilter) {
            "all" -> filteredSubjects.addAll(allSubjects)
            "theory" -> filteredSubjects.addAll(allSubjects.filter {
                it.type.contains("Theory", ignoreCase = true)
            })
            "practical" -> filteredSubjects.addAll(allSubjects.filter {
                it.type.contains("Practical", ignoreCase = true)
            })
            "elective" -> filteredSubjects.addAll(allSubjects.filter {
                it.type.contains("Elective", ignoreCase = true)
            })
        }

        Log.d(TAG, "applyFilter: ${filteredSubjects.size} subjects after filter '$selectedFilter'")

        // Just notify - don't call updateList since they share the same reference!
        runOnUiThread {
            adapter.notifyDataSetChanged()
            Log.d(TAG, "Adapter notified, new item count: ${adapter.itemCount}")
        }

        updateEmptyView()
    }

    private fun updateStats() {
        val total = allSubjects.size
        val theory = allSubjects.count { it.type.contains("Theory", ignoreCase = true) }
        val practical = allSubjects.count { it.type.contains("Practical", ignoreCase = true) }

        Log.d(TAG, "updateStats: total=$total, theory=$theory, practical=$practical")

        totalSubjectsText.text = total.toString()
        theorySubjectsText.text = theory.toString()
        practicalSubjectsText.text = practical.toString()

        statsCard.visibility = if (total > 0) View.VISIBLE else View.GONE
    }

    private fun showSubjectDetails(subject: Subject) {
        val message = buildString {
            append("Subject: ${subject.name}\n")
            append("Code: ${subject.code}\n")
            append("Type: ${subject.type}\n")
            append("Credits: ${subject.credits}\n")
            append("Semester: ${subject.semesterNumber}\n")
            append("Course: ${subject.courseName}\n")
            append("Department: ${subject.department}\n")
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Subject Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setNeutralButton("View Students") { _, _ ->
                Toast.makeText(this, "View students coming soon", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showLoading(show: Boolean) {
        Log.d(TAG, "showLoading: $show")
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        val shouldShowEmpty = filteredSubjects.isEmpty()

        Log.d(TAG, "updateEmptyView: shouldShowEmpty=$shouldShowEmpty, filteredCount=${filteredSubjects.size}")

        if (shouldShowEmpty) {
            emptyView.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE

            emptyText.text = when (selectedFilter) {
                "theory" -> "No theory subjects assigned"
                "practical" -> "No practical subjects assigned"
                "elective" -> "No elective subjects assigned"
                else -> "No subjects assigned to you yet"
            }
        } else {
            emptyView.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Don't reload on resume - it causes duplicate loading
        // Only reload if explicitly needed (e.g., after assignment changes)
        Log.d(TAG, "onResume called - not reloading to avoid duplicates")
    }
}