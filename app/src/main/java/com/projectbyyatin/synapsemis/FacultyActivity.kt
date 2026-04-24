package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.projectbyyatin.synapsemis.adapters.FacultyAdapter
import com.projectbyyatin.synapsemis.models.Faculty
import com.projectbyyatin.synapsemis.CoeDashboardActivity // Add this import

class FacultyActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchFaculty: EditText
    private lateinit var chipGroupDepartments: ChipGroup
    private lateinit var facultyRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var facultyCount: TextView
    private lateinit var fabAddFaculty: FloatingActionButton

    private lateinit var facultyAdapter: FacultyAdapter
    private lateinit var firestore: FirebaseFirestore
    private var facultyList = mutableListOf<Faculty>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_management)

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearchFilter()
        setupDepartmentFilter()
        setupFAB()
        loadFacultyFromFirestore()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchFaculty = findViewById(R.id.search_faculty)
        chipGroupDepartments = findViewById(R.id.chip_group_departments)
        facultyRecyclerView = findViewById(R.id.faculty_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        facultyCount = findViewById(R.id.faculty_count)
        fabAddFaculty = findViewById(R.id.fab_add_faculty)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Faculty Management"
        toolbar.setNavigationOnClickListener {
            navigatetoCoe()
        }
    }

    private fun setupRecyclerView() {
        facultyAdapter = FacultyAdapter(
            facultyList,
            onViewClick = { faculty -> viewFacultyDetails(faculty) },
            onEditClick = { faculty -> editFaculty(faculty) },
            onDeleteClick = { faculty -> deleteFaculty(faculty) },
            onAccessToggle = { faculty, enabled -> toggleAppAccess(faculty, enabled) }
        )

        facultyRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@FacultyActivity)
            adapter = facultyAdapter
        }
    }

    private fun setupSearchFilter() {
        searchFaculty.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                facultyAdapter.filter(s.toString())
                updateFacultyCount()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupDepartmentFilter() {
        chipGroupDepartments.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val selectedChip = findViewById<Chip>(checkedIds[0])
                val department = selectedChip?.text.toString()
                facultyAdapter.filterByDepartment(department)
                updateFacultyCount()
            }
        }
    }

    private fun setupFAB() {
        fabAddFaculty.setOnClickListener {
            val intent = Intent(this, AddFacultyActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadFacultyFromFirestore() {
        firestore.collection("faculty")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading faculty: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                facultyList.clear()
                snapshots?.documents?.forEach { document ->
                    val faculty = document.toObject(Faculty::class.java)
                    faculty?.let {
                        it.id = document.id
                        facultyList.add(it)
                    }
                }

                facultyAdapter.updateList(facultyList)
                updateFacultyCount()
                updateEmptyState()
            }
    }

    private fun viewFacultyDetails(faculty: Faculty) {
        val intent = Intent(this, ViewFacultyActivity::class.java)
        intent.putExtra("FACULTY_ID", faculty.id)
        startActivity(intent)
    }

    private fun editFaculty(faculty: Faculty) {
        val intent = Intent(this, EditFacultyActivity::class.java)
        intent.putExtra("FACULTY_ID", faculty.id)
        startActivity(intent)
    }

    private fun deleteFaculty(faculty: Faculty) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Faculty")
            .setMessage("Are you sure you want to delete ${faculty.name}?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                firestore.collection("faculty").document(faculty.id)
                    .delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "${faculty.name} deleted successfully", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleAppAccess(faculty: Faculty, enabled: Boolean) {
        firestore.collection("faculty").document(faculty.id)
            .update("appAccessEnabled", enabled)
            .addOnSuccessListener {
                val status = if (enabled) "enabled" else "disabled"
                Toast.makeText(this, "App access $status for ${faculty.name}", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun navigatetoCoe() {
        val intent = Intent(this, CoeDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun updateFacultyCount() {
        val count = facultyAdapter.itemCount
        facultyCount.text = "$count Faculty Member${if (count != 1) "s" else ""}"
    }

    private fun updateEmptyState() {
        val isEmpty = facultyAdapter.itemCount == 0
        emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        facultyRecyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadFacultyFromFirestore() // ✅ FIXED - Now actually reloads data
    }
}
