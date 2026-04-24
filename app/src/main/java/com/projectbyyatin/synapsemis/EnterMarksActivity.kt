package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.SubjectsForMarksAdapter
import com.projectbyyatin.synapsemis.models.Exam

class EnterMarksActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examName: TextView
    private lateinit var semesterText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SubjectsForMarksAdapter

    private var examId: String = ""
    private var examData: Exam? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_enter_marks)

        examId = intent.getStringExtra("EXAM_ID") ?: ""
        val examNameText = intent.getStringExtra("EXAM_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadExamData()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examName = findViewById(R.id.exam_name)
        semesterText = findViewById(R.id.semester_text)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Enter Marks"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SubjectsForMarksAdapter { subject ->
            val intent = Intent(this, EnterSubjectMarksActivity::class.java)
            intent.putExtra("EXAM_ID", examId)
            intent.putExtra("SUBJECT_ID", subject.subjectId)
            intent.putExtra("SUBJECT_NAME", subject.subjectName)
            intent.putExtra("COURSE_ID", subject.courseId)
            intent.putExtra("MAX_MARKS", subject.maxMarks)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadExamData() {
        showLoading(true)

        firestore.collection("exams").document(examId)
            .get()
            .addOnSuccessListener { document ->
                examData = document.toObject(Exam::class.java)
                examData?.let {
                    it.id = document.id
                    displayExamData(it)
                    adapter.updateList(it.subjects)
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayExamData(exam: Exam) {
        examName.text = exam.examName
        semesterText.text = "Semester ${exam.semester} | ${exam.academicYear}"
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}
