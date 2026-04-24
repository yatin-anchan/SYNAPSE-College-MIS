package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
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
import com.projectbyyatin.synapsemis.adapters.SubjectsForMarksAdapter
import com.projectbyyatin.synapsemis.models.Exam
import com.projectbyyatin.synapsemis.models.ExamSubject

class FacultySubjectsListActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var examNameText: TextView
    private lateinit var instructionText: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var adapter: SubjectsForMarksAdapter

    private var examId: String = ""
    private var currentFacultyId = ""
    private var assignedSubjects = mutableListOf<ExamSubject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_subjects_list)

        examId = intent.getStringExtra("EXAM_ID") ?: ""

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadFacultySubjects()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        examNameText = findViewById(R.id.exam_name)
        instructionText = findViewById(R.id.instruction_text)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Subjects"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SubjectsForMarksAdapter { subject ->
            val intent = Intent(this, FacultyMarksEntryActivity::class.java)
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

    private fun loadFacultySubjects() {
        showLoading(true)

        val userId = auth.currentUser?.uid ?: return

        // Get faculty ID
        firestore.collection("users").document(userId)
            .get()
            .addOnSuccessListener { userDoc ->
                currentFacultyId = userDoc.getString("facultyId") ?: ""
                loadExamAndSubjects()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading faculty info", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun loadExamAndSubjects() {
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

                examNameText.text = exam.examName

                // Check if marks entry is enabled
                if (!exam.marksEntryEnabled) {
                    instructionText.text = "⚠️ Marks entry not yet enabled for this exam"
                    showLoading(false)
                    return@addOnSuccessListener
                }

                // Filter subjects assigned to this faculty
                filterFacultySubjects(exam.subjects)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun filterFacultySubjects(allSubjects: List<ExamSubject>) {
        // Get faculty assignments
        firestore.collection("facultySubjects")
            .whereEqualTo("facultyId", currentFacultyId)
            .get()
            .addOnSuccessListener { docs ->
                val assignedSubjectIds = docs.mapNotNull { it.getString("subjectId") }

                assignedSubjects = allSubjects.filter {
                    it.subjectId in assignedSubjectIds
                }.toMutableList()

                if (assignedSubjects.isEmpty()) {
                    instructionText.text = "No subjects assigned to you for this exam"
                } else {
                    instructionText.text = "Select a subject to enter marks"
                }

                adapter.updateList(assignedSubjects)
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading assignments: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }
}