package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.WriteBatch
import com.projectbyyatin.synapsemis.adapters.PublishSubjectsAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.SubjectMarksStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.projectbyyatin.synapsemis.utils.SafeFirestoreParser

class CoeMarksPublishActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examNameText: TextView
    private lateinit var semesterText: TextView
    private lateinit var statsCard: View
    private lateinit var totalSubjectsText: TextView
    private lateinit var moderatedSubjectsText: TextView
    private lateinit var pendingSubjectsText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var publishButton: MaterialButton
    private lateinit var warningCard: View
    private lateinit var warningText: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: PublishSubjectsAdapter

    private var examId: String = ""
    private var examData: Exam? = null
    private var subjectsStatusList = mutableListOf<SubjectStatusItem>()
    private var currentCoeId = ""
    private var currentCoeName = ""

    data class SubjectStatusItem(
        val subjectId: String,
        val subjectName: String,
        val subjectCode: String,
        val courseId: String,
        val courseName: String,
        val status: SubjectMarksStatus,
        val canPublish: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_coe_marks_publish)

        examId = intent.getStringExtra("EXAM_ID") ?: ""
        if (examId.isEmpty()) {
            Toast.makeText(this, "No exam ID provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        verifyCoeAccess()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examNameText = findViewById(R.id.exam_name)
        semesterText = findViewById(R.id.semester_text)
        statsCard = findViewById(R.id.stats_card)
        totalSubjectsText = findViewById(R.id.total_subjects)
        moderatedSubjectsText = findViewById(R.id.moderated_subjects)
        pendingSubjectsText = findViewById(R.id.pending_subjects)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        publishButton = findViewById(R.id.publish_button)
        warningCard = findViewById(R.id.warning_card)
        warningText = findViewById(R.id.warning_text)

        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Publish Marks"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = PublishSubjectsAdapter(
            subjectsStatusList,
            onModerateClick = { item -> openModeration(item) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupButtons() {
        publishButton.setOnClickListener { showPublishConfirmation() }
    }

    private fun verifyCoeAccess() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val userId = auth.currentUser?.uid ?: run {
                    showError("User not authenticated")
                    return@launch
                }

                Log.d("COEAccess", "Checking user: $userId")

                val userDoc = withContext(Dispatchers.IO) {
                    firestore.collection("users").document(userId).get().await()
                }

                if (!userDoc.exists()) {
                    showError("User profile not found")
                    return@launch
                }

                val role = userDoc.getString("role")?.lowercase() ?: ""
                Log.d("COEAccess", "User role: $role")

                if (role != "coe") {
                    showError("Only COE can publish marks")
                    return@launch
                }

                // ✅ COE users don't need facultyId - they're super admins
                currentCoeId = userId  // Use userId as COE ID
                currentCoeName = userDoc.getString("name") ?: userDoc.getString("fullName") ?: "COE Admin"

                Log.d("COEAccess", "COE verified: $currentCoeName ($currentCoeId)")
                loadExamData()

            } catch (e: Exception) {
                Log.e("COEAccess", "Access verification failed", e)
                showError("Access check failed: ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }


    private fun loadExamData() {
        lifecycleScope.launch {
            try {
                showLoading(true)

                val document = firestore.collection("exams").document(examId).get().await()
                if (!document.exists()) {
                    showError("Exam not found")
                    return@launch
                }

                // ✅ FIXED - Line 126
                examData = SafeFirestoreParser.safeParseExam(document)

                examData?.let { exam ->
                    displayExamInfo(exam)
                    processSubjectsStatus(exam)
                } ?: run {
                    Log.e("ExamLoad", "Failed to parse exam data - using safe parser")
                    showError("Failed to parse exam data")
                }

            } catch (e: Exception) {
                Log.e("ExamLoad", "Error loading exam", e)
                Toast.makeText(this@CoeMarksPublishActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                showLoading(false)
            }
        }
    }


    private fun displayExamInfo(exam: Exam) {
        examNameText.text = exam.examName
        semesterText.text = "Semester ${exam.semester} | ${exam.academicYear}"

        if (exam.marksPublished) {
            warningCard.visibility = View.VISIBLE
            warningText.text = "✓ Marks already published on ${formatDate(exam.marksPublishedAt)}"
            publishButton.text = "Re-Publish Marks"
        }
    }

    private fun processSubjectsStatus(exam: Exam) {
        subjectsStatusList.clear()

        exam.subjects.forEach { subject ->
            val status = exam.subjectsMarksStatus[subject.subjectId] ?: SubjectMarksStatus(
                subjectId = subject.subjectId
            )

            val item = SubjectStatusItem(
                subjectId = subject.subjectId,
                subjectName = subject.subjectName,
                subjectCode = subject.subjectCode,
                courseId = subject.courseId,
                courseName = subject.courseName,
                status = status,
                canPublish = status.readyForPublish
            )

            subjectsStatusList.add(item)
        }

        // Sort by subject code
        subjectsStatusList.sortBy { it.subjectCode }

        adapter.updateList(subjectsStatusList)
        updateStats()
    }

    private fun updateStats() {
        val total = subjectsStatusList.size
        val moderated = subjectsStatusList.count { it.status.moderated }
        val pending = total - moderated

        totalSubjectsText.text = total.toString()
        moderatedSubjectsText.text = moderated.toString()
        pendingSubjectsText.text = pending.toString()

        // Can publish if all subjects are moderated
        val canPublish = pending == 0 && total > 0
        publishButton.isEnabled = canPublish

        if (!canPublish && total > 0) {
            warningCard.visibility = View.VISIBLE
            warningText.text = "⚠️ Cannot publish: $pending subject(s) pending moderation"
        } else if (canPublish) {
            warningCard.visibility = View.GONE
        }
    }

    private fun openModeration(item: SubjectStatusItem) {
        val intent = Intent(this, HodMarksModerationActivity::class.java).apply {
            putExtra("EXAM_ID", examId)
            putExtra("SUBJECT_ID", item.subjectId)
            putExtra("SUBJECT_NAME", item.subjectName)
            putExtra("COURSE_ID", item.courseId)
            putExtra("MAX_MARKS", examData?.subjects?.find { it.subjectId == item.subjectId }?.maxMarks ?: 100)
        }
        startActivity(intent)
    }

    private fun showPublishConfirmation() {
        val exam = examData ?: return

        val message = if (exam.marksPublished) {
            "⚠️ RE-PUBLISHING MARKS\n\n" +
                    "This will update previously published marks.\n\n" +
                    "• Marksheets will need regeneration\n" +
                    "• Students will see updated results\n\n" +
                    "Continue?"
        } else {
            "🎓 PUBLISH EXAM RESULTS\n\n" +
                    "This will:\n" +
                    "• Make marks visible to students\n" +
                    "• Enable marksheet generation\n" +
                    "• Lock marks from further changes\n" +
                    "  (except re-evaluation)\n\n" +
                    "All ${subjectsStatusList.size} subjects are ready.\n\n" +
                    "Publish now?"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Confirm Publication")
            .setMessage(message)
            .setPositiveButton("Publish") { _, _ -> publishMarks() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun publishMarks() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                val publishedAt = System.currentTimeMillis()

                // Update exam status
                val examRef = firestore.collection("exams").document(examId)
                examRef.update(
                    mapOf(
                        "marksPublished" to true,
                        "marksPublishedAt" to publishedAt,
                        "marksPublishedBy" to currentCoeId,
                        "status" to "completed"
                    )
                ).await()

                // Mark all exam marks as published
                publishAllMarks(publishedAt)

            } catch (e: Exception) {
                Log.e("PublishMarks", "Error updating exam", e)
                showLoading(false)
                Toast.makeText(this@CoeMarksPublishActivity, "Error publishing: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun publishAllMarks(publishedAt: Long) {
        lifecycleScope.launch {
            try {
                val documents = firestore.collection("examMarks")
                    .whereEqualTo("examId", examId)
                    .get()
                    .await()

                if (documents.isEmpty) {
                    showLoading(false)
                    Toast.makeText(this@CoeMarksPublishActivity, "No marks to publish", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Use batched writes for better performance
                val batches = mutableListOf<WriteBatch>()
                var currentBatch = firestore.batch()
                var operationCount = 0
                val maxBatchSize = 500

                documents.documents.forEach { doc ->
                    if (operationCount >= maxBatchSize) {
                        batches.add(currentBatch)
                        currentBatch = firestore.batch()
                        operationCount = 0
                    }

                    currentBatch.update(
                        doc.reference,
                        mapOf(
                            "isPublished" to true,
                            "publishedAt" to publishedAt
                        )
                    )
                    operationCount++
                }

                // Add the last batch
                if (operationCount > 0) {
                    batches.add(currentBatch)
                }

                // Commit all batches sequentially
                for ((index, batch) in batches.withIndex()) {
                    try {
                        batch.commit().await()
                        Log.d("PublishMarks", "Batch $index committed successfully")
                    } catch (e: Exception) {
                        Log.e("PublishMarks", "Batch $index failed", e)
                        throw e
                    }
                }

                showSuccessDialog()

            } catch (e: Exception) {
                Log.e("PublishMarks", "Error loading/publishing marks", e)
                showLoading(false)
                Toast.makeText(this@CoeMarksPublishActivity, "Error in batch $e.message", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSuccessDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("✓ Marks Published Successfully")
            .setMessage(
                "Results are now visible to students.\n\n" +
                        "Next Steps:\n" +
                        "• HODs can generate marksheets\n" +
                        "• Students can view their results\n" +
                        "• Marks are locked (re-evaluation available)"
            )
            .setPositiveButton("Done") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private fun formatDate(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(timestamp))
    }

    private fun showError(message: String) {
        showLoading(false)
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
        statsCard.visibility = if (show) View.GONE else View.VISIBLE
        publishButton.isEnabled = !show
    }

    override fun onResume() {
        super.onResume()
        // Reload data to reflect any moderation changes
        if (examData != null && currentCoeId.isNotEmpty()) {
            loadExamData()
        }
    }
}
