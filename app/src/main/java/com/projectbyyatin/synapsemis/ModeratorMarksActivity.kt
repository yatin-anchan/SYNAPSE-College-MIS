package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.ModeratorMarksAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamMarks
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus

class ModeratorMarksActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examNameText: TextView
    private lateinit var subjectNameText: TextView
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    private lateinit var facultyInfoText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var approveButton: MaterialButton
    private lateinit var reopenButton: MaterialButton
    private lateinit var warningCard: View
    private lateinit var warningText: TextView
    private lateinit var remarksInput: EditText

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: ModeratorMarksAdapter

    private var examId: String = ""
    private var subjectId: String = ""
    private var subjectName: String = ""
    private var courseId: String = ""
    private var maxMarks: Int = 100

    private var examData: Exam? = null
    private var marksList = mutableListOf<ExamMarks>()
    private var isModerated = false
    private var currentModeratorId = ""
    private var currentModeratorName = ""
    private var currentRole = "" // "hod" or "coe"
    private var departmentId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_moderator_marks)

        examId = intent.getStringExtra("EXAM_ID") ?: ""
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        courseId = intent.getStringExtra("COURSE_ID") ?: ""
        maxMarks = intent.getIntExtra("MAX_MARKS", 100)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadModeratorInfo()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examNameText = findViewById(R.id.exam_name)
        subjectNameText = findViewById(R.id.subject_name)
        statusText = findViewById(R.id.status_text)
        statsText = findViewById(R.id.stats_text)
        facultyInfoText = findViewById(R.id.faculty_info_text)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        approveButton = findViewById(R.id.approve_button)
        reopenButton = findViewById(R.id.reopen_button)
        warningCard = findViewById(R.id.warning_card)
        warningText = findViewById(R.id.warning_text)
        remarksInput = findViewById(R.id.remarks_input)

        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Moderate Marks"
        toolbar.setNavigationOnClickListener {
            if (!isModerated && adapter.hasChanges()) {
                showExitConfirmation()
            } else {
                finish()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ModeratorMarksAdapter(
            marksList,
            maxMarks,
            isLocked = false
        ) { updateStats() }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        approveButton.setOnClickListener { showApproveConfirmation() }
        reopenButton.setOnClickListener { showReopenConfirmation() }
    }

    private fun loadModeratorInfo() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                currentRole = userDoc.getString("role")?.lowercase() ?: ""
                val facultyId = userDoc.getString("facultyId") ?: ""

                if (currentRole !in listOf("hod", "coe")) {
                    showError("You don't have moderator permissions")
                    return@addOnSuccessListener
                }

                firestore.collection("faculty").document(facultyId)
                    .get()
                    .addOnSuccessListener { facultyDoc ->
                        currentModeratorId = facultyId
                        currentModeratorName = facultyDoc.getString("name") ?: "Moderator"
                        departmentId = facultyDoc.getString("departmentId") ?: ""

                        loadExamData()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error loading moderator info", Toast.LENGTH_SHORT).show()
                        finish()
                    }
            }
    }

    private fun loadExamData() {
        showLoading(true)

        firestore.collection("exams").document(examId)
            .get()
            .addOnSuccessListener { document ->
                examData = document.toObject(Exam::class.java)?.apply { id = document.id }

                if (!validateModerationAccess()) {
                    return@addOnSuccessListener
                }

                displayExamInfo()
                loadMarksData()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun validateModerationAccess(): Boolean {
        val exam = examData ?: return false

        if (exam.marksPublished) {
            warningText.text = "⚠️ Marks published. Only re-evaluation mode available."
            warningCard.visibility = View.VISIBLE
        }

        if (currentRole == "hod") {
            firestore.collection("courses").document(courseId)
                .get()
                .addOnSuccessListener { courseDoc ->
                    val courseDept = courseDoc.getString("departmentId") ?: ""
                    if (courseDept != departmentId) {
                        showError("You can only moderate subjects from your department")
                    }
                }
        }

        return true
    }

    private fun loadMarksData() {
        firestore.collection("examMarks")
            .whereEqualTo("examId", examId)
            .whereEqualTo("subjectId", subjectId)
            .get()
            .addOnSuccessListener { documents ->
                marksList.clear()
                documents.forEach { doc ->
                    val marks = doc.toObject(ExamMarks::class.java).apply { id = doc.id }
                    marksList.add(marks)
                }

                marksList.sortBy { it.studentRollNo }

                isModerated = marksList.firstOrNull()?.isModerated ?: false

                val firstMark = marksList.firstOrNull()
                if (firstMark != null) {
                    facultyInfoText.text =
                        "Entered by: ${firstMark.enteredByName} on ${formatDate(firstMark.enteredAt)}"
                    if (firstMark.isModerated) {
                        facultyInfoText.append(
                            "\nModerated by: ${firstMark.moderatedByName} on ${formatDate(firstMark.moderatedAt)}"
                        )
                    }
                }

                updateUI()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading marks: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayExamInfo() {
        examData?.let { exam ->
            examNameText.text = exam.examName
            subjectNameText.text = "$subjectName (Max Marks: $maxMarks)"
        }
    }

    private fun updateUI() {
        adapter.updateList(marksList)
        adapter.setLocked(false)
        updateStats()
        updateButtons()
    }

    private fun updateStats() {
        val submitted = marksList.firstOrNull()?.isSubmitted ?: false
        val moderated = marksList.firstOrNull()?.isModerated ?: false

        val total = marksList.size
        // A record is considered modified when the previous totals differ from the current totals.
        val modified = marksList.count { mark ->
            mark.isModerated &&
                    (mark.previousWrittenMarks != mark.writtenMarksObtained ||
                            mark.previousInternalMarks != mark.internalMarksObtained)
        }

        statsText.text = "Total Students: $total | Modified: $modified"

        when {
            moderated -> {
                statusText.text = "Status: Moderated & Approved ✓"
                statusText.setTextColor(getColor(android.R.color.holo_green_dark))
            }
            submitted -> {
                statusText.text = "Status: Submitted (Awaiting Moderation)"
                statusText.setTextColor(getColor(android.R.color.holo_orange_dark))
            }
            else -> {
                statusText.text = "Status: Not Submitted"
                statusText.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }

    private fun updateButtons() {
        val submitted = marksList.firstOrNull()?.isSubmitted ?: false

        approveButton.isEnabled = submitted && !isModerated
        reopenButton.isEnabled = isModerated
        reopenButton.visibility = if (isModerated) View.VISIBLE else View.GONE
    }

    private fun showApproveConfirmation() {
        val modified = adapter.getModifiedCount()

        val message = if (modified > 0) {
            "You have modified $modified student marks.\n\n✓ Approve and finalize these marks?\n\n⚠️ Once approved, marks are ready for publishing by COE."
        } else {
            "No modifications made.\n\n✓ Approve marks as submitted by faculty?\n\n⚠️ Once approved, marks are ready for publishing by COE."
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Approve Marks")
            .setMessage(message)
            .setPositiveButton("Approve") { _, _ -> approveMarks() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun approveMarks() {
        showLoading(true)
        val batch = firestore.batch()
        val moderatedAt = System.currentTimeMillis()
        val remarks = remarksInput.text.toString().trim()
        val firstMark = marksList.firstOrNull()

        marksList.forEach { marks ->
            // Snapshot previous values before overwriting (only on first moderation)
            if (!marks.isModerated) {
                marks.previousWrittenMarks = marks.writtenMarksObtained
                marks.previousInternalMarks = marks.internalMarksObtained
            }

            // Recalculate totals, percentage, CGPI and grade
            val total = marks.writtenMarksObtained + marks.internalMarksObtained
            val maxTotal = marks.writtenMaxMarks + marks.internalMaxMarks
            marks.totalMarksObtained = total
            marks.totalMaxMarks = maxTotal
            marks.percentage = if (marks.isAbsent || maxTotal == 0) 0f else (total / maxTotal) * 100f
            marks.cgpi = if (marks.isAbsent || maxTotal == 0) 0f else (total / maxTotal) * 10f
            marks.grade = ExamMarks.calculateGrade(marks.percentage)

            marks.isModerated = true
            marks.moderatedBy = currentModeratorId
            marks.moderatedByName = currentModeratorName
            marks.moderatedAt = moderatedAt
            marks.moderationRemarks = remarks

            val docRef = firestore.collection("examMarks").document(marks.id)
            batch.set(docRef, marks)
        }

        val statusUpdate = SubjectMarksStatus(
            subjectId = subjectId,
            totalStudents = marksList.size,
            marksEntered = marksList.size,
            marksSubmitted = true,
            submittedBy = firstMark?.enteredBy ?: "",
            submittedAt = firstMark?.submittedAt ?: System.currentTimeMillis(),
            moderated = true,
            moderatedBy = currentModeratorId,
            moderatedAt = System.currentTimeMillis(),
            readyForPublish = true
        )

        val examRef = firestore.collection("exams").document(examId)
        batch.update(examRef, "subjectsMarksStatus.$subjectId", statusUpdate)

        batch.commit()
            .addOnSuccessListener {
                isModerated = true
                updateUI()
                showLoading(false)
                Toast.makeText(this, "✓ Marks approved and ready for publishing", Toast.LENGTH_LONG).show()
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error approving: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showReopenConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reopen for Re-evaluation")
            .setMessage("This will allow you to modify marks again.\n\nTypical use case: Re-evaluation request\n\nContinue?")
            .setPositiveButton("Reopen") { _, _ -> reopenMarks() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun reopenMarks() {
        Toast.makeText(this, "You can now modify marks for re-evaluation", Toast.LENGTH_SHORT).show()
        isModerated = false
        updateButtons()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun showExitConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Unsaved Changes")
            .setMessage("You have unsaved changes. Exit without approving?")
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showError(message: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Access Denied")
            .setMessage(message)
            .setPositiveButton("OK") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
        approveButton.isEnabled = !show && !isModerated
    }

    override fun onBackPressed() {
        if (!isModerated && adapter.hasChanges()) {
            showExitConfirmation()
        } else {
            super.onBackPressed()
        }
    }
}