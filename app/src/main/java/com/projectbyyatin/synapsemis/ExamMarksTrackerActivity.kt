package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore

data class SubjectProgressItem(
    val subjectId: String,
    val subjectName: String,
    val subjectCode: String,
    val totalStudents: Int,
    val marksEntered: Int,
    val absentCount: Int,
    val submittedCount: Int,
    val moderatedCount: Int,
    val publishedCount: Int
)

class SubjectProgressAdapter(
    private val items: MutableList<SubjectProgressItem>
) : RecyclerView.Adapter<SubjectProgressAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val subjectName: TextView    = v.findViewById(R.id.tv_subject_name)
        val subjectCode: TextView    = v.findViewById(R.id.tv_subject_code)
        val progressText: TextView   = v.findViewById(R.id.tv_progress_text)
        val absentText: TextView     = v.findViewById(R.id.tv_absent_text)
        val progressBar: ProgressBar = v.findViewById(R.id.progress_bar)
        val statusChip: TextView     = v.findViewById(R.id.tv_status_chip)
        val dot1: View               = v.findViewById(R.id.dot_1_fill)
        val dot2: View               = v.findViewById(R.id.dot_2_fill)
        val dot3: View               = v.findViewById(R.id.dot_3_fill)
        val dot4: View               = v.findViewById(R.id.dot_4_fill)
        val line12: View             = v.findViewById(R.id.line_12)
        val line23: View             = v.findViewById(R.id.line_23)
        val line34: View             = v.findViewById(R.id.line_34)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subject_progress, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        val ctx  = h.itemView.context

        h.subjectName.text = item.subjectName
        h.subjectCode.text = item.subjectCode

        val filled = item.marksEntered + item.absentCount
        val pct    = if (item.totalStudents > 0) (filled * 100 / item.totalStudents) else 0

        h.progressText.text    = "${item.marksEntered}/${item.totalStudents} marks entered"
        h.absentText.text      = if (item.absentCount > 0) "• ${item.absentCount} absent" else ""
        h.absentText.visibility = if (item.absentCount > 0) View.VISIBLE else View.GONE
        h.progressBar.progress  = pct

        val allSubmitted = item.totalStudents > 0 && item.submittedCount == item.totalStudents
        val allModerated = item.totalStudents > 0 && item.moderatedCount == item.totalStudents
        val allPublished = item.totalStudents > 0 && item.publishedCount == item.totalStudents

        val (chipText, chipColor) = when {
            allPublished                                    -> "Published"    to "#4CAF50"
            allModerated                                    -> "Moderated"    to "#2196F3"
            allSubmitted                                    -> "Submitted"    to "#FF9800"
            item.marksEntered > 0 || item.absentCount > 0  -> "In Progress"  to "#9C27B0"
            else                                            -> "Pending"      to "#607D8B"
        }
        h.statusChip.text = chipText
        h.statusChip.setBackgroundColor(android.graphics.Color.parseColor(chipColor))

        activateDot(ctx, h.dot1, h.line12, item.marksEntered > 0 || item.absentCount > 0)
        activateDot(ctx, h.dot2, h.line23, item.submittedCount > 0)
        activateDot(ctx, h.dot3, h.line34, item.moderatedCount > 0)
        activateDot(ctx, h.dot4, null,     allPublished)
    }

    private fun activateDot(ctx: android.content.Context, dot: View, line: View?, active: Boolean) {
        dot.visibility = if (active) View.GONE else View.VISIBLE
        line?.background = ContextCompat.getDrawable(
            ctx,
            if (active) R.drawable.stepper_line_active else R.drawable.stepper_line_inactive
        )
    }

    fun updateList(newItems: List<SubjectProgressItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}

class ExamMarksTrackerActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var tvExamName: TextView
    private lateinit var tvExamType: TextView
    private lateinit var overallProgress: TextView
    private lateinit var overallStatus: TextView
    private lateinit var overallProgressIndicator: CircularProgressIndicator
    private lateinit var overallDot1Fill: View
    private lateinit var overallDot2Fill: View
    private lateinit var overallDot3Fill: View
    private lateinit var overallDot4Fill: View
    private lateinit var overallLine12: View
    private lateinit var overallLine23: View
    private lateinit var overallLine34: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SubjectProgressAdapter
    private val subjectItems = mutableListOf<SubjectProgressItem>()

    private var filterExamId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_marks_tracker)

        filterExamId = intent.getStringExtra("FILTER_EXAM_ID")

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadMarksFromFirestore()
    }

    private fun initializeViews() {
        toolbar                  = findViewById(R.id.toolbar)
        recyclerView             = findViewById(R.id.recycler_view)
        loadingProgress          = findViewById(R.id.loading_progress)
        emptyView                = findViewById(R.id.empty_view)
        tvExamName               = findViewById(R.id.tv_exam_name)
        tvExamType               = findViewById(R.id.tv_exam_type)
        overallProgress          = findViewById(R.id.overall_progress)
        overallStatus            = findViewById(R.id.overall_status)
        overallProgressIndicator = findViewById(R.id.overall_progress_indicator)
        overallDot1Fill          = findViewById(R.id.overall_dot_1_fill)
        overallDot2Fill          = findViewById(R.id.overall_dot_2_fill)
        overallDot3Fill          = findViewById(R.id.overall_dot_3_fill)
        overallDot4Fill          = findViewById(R.id.overall_dot_4_fill)
        overallLine12            = findViewById(R.id.overall_line_12)
        overallLine23            = findViewById(R.id.overall_line_23)
        overallLine34            = findViewById(R.id.overall_line_34)
        firestore                = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Marks Progress"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SubjectProgressAdapter(subjectItems)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadMarksFromFirestore() {
        val examId = filterExamId
        if (examId.isNullOrEmpty()) {
            Toast.makeText(this, "No exam selected", Toast.LENGTH_SHORT).show()
            showLoading(false)
            updateEmptyView()
            return
        }

        showLoading(true)

        // Step 1: Load exam doc for header
        firestore.collection("exams").document(examId)
            .get()
            .addOnSuccessListener { examDoc ->
                val examName = examDoc.getString("examName") ?: ""
                val examType = examDoc.getString("examType") ?: ""
                val semester = (examDoc.get("semester") as? Number)?.toInt() ?: 0

                supportActionBar?.title = examName.ifEmpty { "Marks Progress" }
                tvExamName.text = examName
                tvExamType.text = buildString {
                    if (examType.isNotEmpty()) append(examType)
                    if (semester > 0) {
                        if (examType.isNotEmpty()) append("  •  ")
                        append("Semester $semester")
                    }
                }

                fetchExamMarks(examId)
            }
            .addOnFailureListener { fetchExamMarks(examId) }
    }

    // Step 2: Query examMarks collection
    private fun fetchExamMarks(examId: String) {
        firestore.collection("examMarks")
            .whereEqualTo("examId", examId)
            .get()
            .addOnSuccessListener { documents ->
                android.util.Log.d("MarksTracker", "examMarks docs: ${documents.size()}")

                data class MarkRecord(
                    val subjectId: String,
                    val subjectName: String,
                    val subjectCode: String,
                    val isAbsent: Boolean,
                    val writtenMarks: Float,
                    val internalMarks: Float,
                    val isSubmitted: Boolean,
                    val isModerated: Boolean,
                    val isPublished: Boolean
                )

                val grouped = mutableMapOf<String, MutableList<MarkRecord>>()

                documents.forEach { doc ->
                    val d = doc.data
                    val subjectId = d["subjectId"]?.toString() ?: return@forEach
                    grouped.getOrPut(subjectId) { mutableListOf() }.add(
                        MarkRecord(
                            subjectId     = subjectId,
                            subjectName   = d["subjectName"]?.toString() ?: "",
                            subjectCode   = d["subjectCode"]?.toString() ?: "",
                            isAbsent      = d["isAbsent"] as? Boolean ?: false,
                            writtenMarks  = (d["writtenMarksObtained"] as? Number)?.toFloat() ?: 0f,
                            internalMarks = (d["internalMarksObtained"] as? Number)?.toFloat() ?: 0f,
                            isSubmitted   = d["isSubmitted"] as? Boolean ?: false,
                            isModerated   = d["isModerated"] as? Boolean ?: false,
                            isPublished   = d["isPublished"] as? Boolean ?: false
                        )
                    )
                }

                subjectItems.clear()
                grouped.forEach { (subjectId, records) ->
                    val first          = records.first()
                    val totalStudents  = records.size
                    val absentCount    = records.count { it.isAbsent }
                    val marksEntered   = records.count {
                        !it.isAbsent && (it.writtenMarks > 0f || it.internalMarks > 0f)
                    }
                    val submittedCount = records.count { it.isSubmitted }
                    val moderatedCount = records.count { it.isModerated }
                    val publishedCount = records.count { it.isPublished }

                    subjectItems.add(
                        SubjectProgressItem(
                            subjectId      = subjectId,
                            subjectName    = first.subjectName,
                            subjectCode    = first.subjectCode,
                            totalStudents  = totalStudents,
                            marksEntered   = marksEntered,
                            absentCount    = absentCount,
                            submittedCount = submittedCount,
                            moderatedCount = moderatedCount,
                            publishedCount = publishedCount
                        )
                    )
                }

                subjectItems.sortBy { it.subjectName }

                adapter.updateList(subjectItems)
                updateOverallProgress()
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("MarksTracker", "examMarks fetch failed: ${e.message}")
                showLoading(false)
                updateEmptyView()
                Toast.makeText(this, "Failed to load marks: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun updateOverallProgress() {
        val totalSubjects     = subjectItems.size
        val enteredSubjects   = subjectItems.count { it.marksEntered > 0 || it.absentCount > 0 }
        val submittedSubjects = subjectItems.count {
            it.totalStudents > 0 && it.submittedCount == it.totalStudents
        }
        val moderatedSubjects = subjectItems.count {
            it.totalStudents > 0 && it.moderatedCount == it.totalStudents
        }
        val publishedSubjects = subjectItems.count {
            it.totalStudents > 0 && it.publishedCount == it.totalStudents
        }

        val totalStudents  = subjectItems.sumOf { it.totalStudents }
        val filledStudents = subjectItems.sumOf { it.marksEntered + it.absentCount }
        val pct = if (totalStudents > 0) (filledStudents * 100 / totalStudents) else 0

        overallProgress.text              = "$enteredSubjects/$totalSubjects subjects entered"
        overallStatus.text                = "$pct%"
        overallProgressIndicator.progress = pct

        activateOverallStep(overallDot1Fill, overallLine12, enteredSubjects > 0)
        activateOverallStep(overallDot2Fill, overallLine23, submittedSubjects > 0)
        activateOverallStep(overallDot3Fill, overallLine34, moderatedSubjects > 0)
        activateOverallStep(
            overallDot4Fill, null,
            totalSubjects > 0 && publishedSubjects == totalSubjects
        )
    }

    private fun activateOverallStep(fill: View, line: View?, active: Boolean) {
        fill.visibility = if (active) View.GONE else View.VISIBLE
        line?.background = ContextCompat.getDrawable(
            this,
            if (active) R.drawable.stepper_line_active else R.drawable.stepper_line_inactive
        )
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility    = if (show) View.GONE    else View.VISIBLE
    }

    private fun updateEmptyView() {
        val empty = subjectItems.isEmpty()
        emptyView.visibility    = if (empty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (empty) View.GONE    else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadMarksFromFirestore()
    }
}