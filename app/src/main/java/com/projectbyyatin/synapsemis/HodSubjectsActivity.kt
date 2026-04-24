package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.SubjectListAdapter
import com.projectbyyatin.synapsemis.models.Subject
import kotlin.jvm.java

class HodSubjectsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: TextView
    private lateinit var fabAdd: FloatingActionButton

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SubjectListAdapter
    private var subjectList = mutableListOf<Subject>()

    private var departmentId = ""
    private var departmentName = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_subjects)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        loadSubjects()
        setupFab()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)
        fabAdd = findViewById(R.id.fab_add)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Subjects"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SubjectListAdapter(subjectList) { subject ->
            // Open faculty assignment
            val intent = Intent(this, AssignFacultyToSubjectActivity::class.java)
            intent.putExtra("SUBJECT_ID", subject.id)
            intent.putExtra("SUBJECT_NAME", subject.name)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.getFilter().filter(newText ?: "")
                return true
            }
        })
    }


    private fun setupFab() {
        fabAdd.setOnClickListener {
            val intent = Intent(this, AddSubjectActivityHOD::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", departmentName)
            startActivity(intent)
        }
    }

    private fun loadSubjects() {
        showLoading(true)

        firestore.collection("subjects")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                subjectList.clear()

                documents.forEach { document ->
                    val subject = document.toObject(Subject::class.java)
                    subject.id = document.id
                    subjectList.add(subject)
                }

                adapter.updateList(subjectList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (subjectList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadSubjects()
    }
}