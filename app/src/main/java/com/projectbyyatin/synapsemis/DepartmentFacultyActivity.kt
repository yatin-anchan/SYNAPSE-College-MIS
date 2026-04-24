package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.FacultyAdapter
import com.projectbyyatin.synapsemis.models.Faculty

class DepartmentFacultyActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: FacultyAdapter
    private var facultyList = mutableListOf<Faculty>()

    private var departmentId: String = ""
    private var departmentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_department_faculty)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        loadFacultyList()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchView = findViewById(R.id.search_view)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Faculty"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = FacultyAdapter(
            facultyList,
            onViewClick = { faculty -> viewFaculty(faculty) },
            onEditClick = { faculty -> editFaculty(faculty) },
            onDeleteClick = { faculty -> showDeleteConfirmation(faculty) },
            onAccessToggle = { faculty, enabled -> toggleAccess(faculty, enabled) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText ?: "")
                return true
            }
        })
    }

    private fun loadFacultyList() {
        showLoading(true)

        firestore.collection("faculty")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                facultyList.clear()

                documents.forEach { document ->
                    val faculty = document.toObject(Faculty::class.java)
                    faculty.id = document.id
                    facultyList.add(faculty)
                }

                adapter.updateList(facultyList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun viewFaculty(faculty: Faculty) {
        val intent = Intent(this, ViewFacultyActivity::class.java)
        intent.putExtra("FACULTY_ID", faculty.id)
        startActivity(intent)
    }

    private fun editFaculty(faculty: Faculty) {
        val intent = Intent(this, EditFacultyActivity::class.java)
        intent.putExtra("FACULTY_ID", faculty.id)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(faculty: Faculty) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Remove Faculty")
            .setMessage("Remove ${faculty.name} from $departmentName?\n\nThis will only unassign them from this department, not delete their account.")
            .setPositiveButton("Remove") { _, _ ->
                removeFaculty(faculty)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeFaculty(faculty: Faculty) {
        showLoading(true)

        firestore.collection("faculty").document(faculty.id)
            .update(mapOf(
                "department" to "",
                "departmentId" to "",
                "college" to "",
                "stream" to ""
            ))
            .addOnSuccessListener {
                Toast.makeText(this, "${faculty.name} removed from department", Toast.LENGTH_SHORT).show()
                loadFacultyList()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun toggleAccess(faculty: Faculty, enabled: Boolean) {
        firestore.collection("faculty").document(faculty.id)
            .update("appAccessEnabled", enabled)
            .addOnSuccessListener {
                Toast.makeText(
                    this,
                    "Access ${if (enabled) "enabled" else "disabled"} for ${faculty.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                loadFacultyList() // Reload to reset switch
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (facultyList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadFacultyList()
    }
}