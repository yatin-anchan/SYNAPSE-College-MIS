package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.ExamHistoryAdapter
import com.projectbyyatin.synapsemis.models.Exam
import kotlin.jvm.java

class ExamHistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var yearFilter: AutoCompleteTextView
    private lateinit var semesterFilter: AutoCompleteTextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: ExamHistoryAdapter
    private var examsList = mutableListOf<Exam>()

    private var selectedYear: String? = null
    private var selectedSemester: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exam_history)

        initializeViews()
        setupToolbar()
        setupFilters()
        //setupRecyclerView()
        loadHistory()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        yearFilter = findViewById(R.id.year_filter)
        semesterFilter = findViewById(R.id.semester_filter)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Exam History"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupFilters() {
        // Year filter
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val years = listOf("All Years") + (currentYear downTo currentYear - 5).map { "$it-${it+1}" }
        val yearAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, years)
        yearFilter.setAdapter(yearAdapter)
        yearFilter.setText("All Years", false)

        yearFilter.setOnItemClickListener { _, _, position, _ ->
            selectedYear = if (position == 0) null else years[position]
            loadHistory()
        }

        // Semester filter
        val semesters = listOf("All Semesters") + (1..8).map { "Semester $it" }
        val semesterAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, semesters)
        semesterFilter.setAdapter(semesterAdapter)
        semesterFilter.setText("All Semesters", false)

        semesterFilter.setOnItemClickListener { _, _, position, _ ->
            selectedSemester = if (position == 0) null else position
            loadHistory()
        }
    }

//    private fun setupRecyclerView() {
//        adapter = ExamHistoryAdapter(
//            examsList,
//            onViewClick = { exam -> viewExamResults(exam) }
//        )
//
//        recyclerView.layoutManager = LinearLayoutManager(this)
//        recyclerView.adapter = adapter
//    }

    private fun loadHistory() {
        showLoading(true)

        var query: Query = firestore.collection("exams")
            .whereEqualTo("status", "completed")
            .orderBy("endDate", Query.Direction.DESCENDING)

        query.get()
            .addOnSuccessListener { documents ->
                examsList.clear()

                documents.forEach { document ->
                    val exam = document.toObject(Exam::class.java)
                    exam.id = document.id

                    // Apply client-side filtering
                    var shouldAdd = true

                    selectedYear?.let { year ->
                        if (exam.academicYear != year) shouldAdd = false
                    }

                    selectedSemester?.let { semester ->
                        if (exam.semester != semester) shouldAdd = false
                    }

                    if (shouldAdd) {
                        examsList.add(exam)
                    }
                }

                adapter.updateList(examsList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

//    private fun viewExamResults(exam: Exam) {
//        //val intent = Intent(this, ExamResultsActivity::class.java)
//        intent.putExtra("EXAM_ID", exam.id)
//        intent.putExtra("EXAM_NAME", exam.examName)
//        startActivity(intent)
//    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (examsList.isEmpty()) View.VISIBLE else View.GONE
    }
}
