package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.SemestersAdapter
import com.projectbyyatin.synapsemis.models.Semester

class ManageSemestersActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SemestersAdapter
    private var semestersList = mutableListOf<Semester>()

    private var courseId: String = ""
    private var courseName: String = ""
    private var departmentId: String = ""
    private var departmentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_semesters)

        courseId = intent.getStringExtra("COURSE_ID") ?: ""
        courseName = intent.getStringExtra("COURSE_NAME") ?: ""
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadSemesters()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = courseName
        supportActionBar?.subtitle = "Semesters & Subjects"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SemestersAdapter(
            semestersList,
            onViewClick = { semester -> viewSubjects(semester) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadSemesters() {
        showLoading(true)

        firestore.collection("semesters")
            .whereEqualTo("courseId", courseId)
            .get()
            .addOnSuccessListener { documents ->
                semestersList.clear()

                // Sort manually in code instead of using orderBy
                val sortedDocuments = documents.sortedBy {
                    it.getLong("semesterNumber") ?: 0
                }

                sortedDocuments.forEach { document ->
                    val semester = document.toObject(Semester::class.java)
                    semester.id = document.id

                    // Count subjects for this semester
                    countSubjectsForSemester(semester)

                    semestersList.add(semester)
                }

                adapter.updateList(semestersList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading semesters: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun countSubjectsForSemester(semester: Semester) {
        firestore.collection("subjects")
            .whereEqualTo("semesterId", semester.id)
            .get()
            .addOnSuccessListener { documents ->
                val count = documents.size()

                // Update semester totalSubjects
                firestore.collection("semesters").document(semester.id)
                    .update("totalSubjects", count)

                semester.totalSubjects = count
                adapter.notifyDataSetChanged()
            }
    }

    private fun viewSubjects(semester: Semester) {
        val intent = Intent(this, ManageSubjectsActivity::class.java)
        intent.putExtra("SEMESTER_ID", semester.id)
        intent.putExtra("SEMESTER_NUMBER", semester.semesterNumber)
        intent.putExtra("SEMESTER_NAME", semester.semesterName)
        intent.putExtra("COURSE_ID", courseId)
        intent.putExtra("COURSE_NAME", courseName)
        intent.putExtra("DEPARTMENT_ID", departmentId)
        intent.putExtra("DEPARTMENT_NAME", departmentName)
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (semestersList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadSemesters()
    }
}
