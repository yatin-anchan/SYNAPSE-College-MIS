package com.projectbyyatin.synapsemis

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
import com.projectbyyatin.synapsemis.adapters.SelectFacultyForDepartmentAdapter
import com.projectbyyatin.synapsemis.models.Faculty

class AddFacultyToDepartmentActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SelectFacultyForDepartmentAdapter
    private var facultyList = mutableListOf<Faculty>()
    private var filteredList = mutableListOf<Faculty>()

    private var departmentId: String = ""
    private var departmentName: String = ""
    private var college: String = ""
    private var stream: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_faculty_to_department)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""
        college = intent.getStringExtra("COLLEGE") ?: ""
        stream = intent.getStringExtra("STREAM") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        loadUnassignedFaculty()
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
        supportActionBar?.title = "Add Faculty"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SelectFacultyForDepartmentAdapter(
            filteredList,
            onAddClick = { faculty -> showConfirmationDialog(faculty) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupSearch() {
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false

            override fun onQueryTextChange(newText: String?): Boolean {
                filterFaculty(newText ?: "")
                return true
            }
        })
    }

    private fun loadUnassignedFaculty() {
        showLoading(true)

        // Query faculty who are NOT assigned to any department
        firestore.collection("faculty")
            .whereEqualTo("departmentId", "")
            .get()
            .addOnSuccessListener { documents ->
                facultyList.clear()

                documents.forEach { document ->
                    val faculty = document.toObject(Faculty::class.java)
                    faculty.id = document.id
                    facultyList.add(faculty)
                }

                filteredList.clear()
                filteredList.addAll(facultyList)
                adapter.updateList(filteredList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading faculty: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun filterFaculty(query: String) {
        filteredList.clear()

        if (query.isEmpty()) {
            filteredList.addAll(facultyList)
        } else {
            val lowerQuery = query.lowercase()
            filteredList.addAll(facultyList.filter {
                it.name.lowercase().contains(lowerQuery) ||
                        it.email.lowercase().contains(lowerQuery) ||
                        it.designation.lowercase().contains(lowerQuery) ||
                        it.employeeId.lowercase().contains(lowerQuery)
            })
        }

        adapter.updateList(filteredList)
        updateEmptyView()
    }

    private fun showConfirmationDialog(faculty: Faculty) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Add Faculty to Department")
            .setMessage("Add ${faculty.name} to $departmentName?")
            .setPositiveButton("Add") { _, _ ->
                addFacultyToDepartment(faculty)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addFacultyToDepartment(faculty: Faculty) {
        showLoading(true)

        val updates = hashMapOf<String, Any>(
            "department" to departmentName,
            "departmentId" to departmentId,
            "college" to college,
            "stream" to stream
        )

        firestore.collection("faculty").document(faculty.id)
            .update(updates)
            .addOnSuccessListener {
                Toast.makeText(this, "${faculty.name} added to $departmentName", Toast.LENGTH_SHORT).show()

                // Remove from list
                facultyList.remove(faculty)
                filteredList.remove(faculty)
                adapter.updateList(filteredList)

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
        emptyView.visibility = if (filteredList.isEmpty()) View.VISIBLE else View.GONE
    }
}
