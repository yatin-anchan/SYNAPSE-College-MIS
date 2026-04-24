package com.projectbyyatin.synapsemis

import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
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
import com.projectbyyatin.synapsemis.adapters.DepartmentAdapter
import com.projectbyyatin.synapsemis.models.Department

class DepartmentsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchDepartment: EditText
    private lateinit var filterHeader: LinearLayout
    private lateinit var filterContent: LinearLayout
    private lateinit var filterExpandIcon: ImageView
    private lateinit var chipGroupCollege: ChipGroup
    private lateinit var chipGroupStream: ChipGroup
    private lateinit var chipJrCollege: Chip
    private lateinit var chipSrCollege: Chip
    private lateinit var chipScience: Chip
    private lateinit var chipCommerce: Chip
    private lateinit var chipArts: Chip
    private lateinit var chipAllStreams: Chip
    private lateinit var departmentRecyclerView: RecyclerView
    private lateinit var emptyState: LinearLayout
    private lateinit var departmentCount: TextView
    private lateinit var fabAddDepartment: FloatingActionButton

    private lateinit var departmentAdapter: DepartmentAdapter
    private lateinit var firestore: FirebaseFirestore
    private var departmentList = mutableListOf<Department>()

    private var selectedCollege: String = "JR"
    private var selectedStream: String = "All"
    private var isFilterExpanded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_departments)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                navigateToCoe()
            }
        })

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearchFilter()
        setupCollegeFilter()
        setupStreamFilter()
        setupFilterCollapse()
        setupFAB()
        loadDepartmentsFromFirestore()
    }

    private fun navigateToCoe() {
        val intent = Intent(this@DepartmentsActivity, CoeDashboardActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        searchDepartment = findViewById(R.id.search_department)
        filterHeader = findViewById(R.id.filter_header)
        filterContent = findViewById(R.id.filter_content)
        filterExpandIcon = findViewById(R.id.filter_expand_icon)
        chipGroupCollege = findViewById(R.id.chip_group_college)
        chipGroupStream = findViewById(R.id.chip_group_stream)
        chipJrCollege = findViewById(R.id.chip_jr_college)
        chipSrCollege = findViewById(R.id.chip_sr_college)
        chipScience = findViewById(R.id.chip_science)
        chipCommerce = findViewById(R.id.chip_commerce)
        chipArts = findViewById(R.id.chip_arts)
        chipAllStreams = findViewById(R.id.chip_all_streams)
        departmentRecyclerView = findViewById(R.id.department_recycler_view)
        emptyState = findViewById(R.id.empty_state)
        departmentCount = findViewById(R.id.department_count)
        fabAddDepartment = findViewById(R.id.fab_add_department)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Departments"
        toolbar.setNavigationOnClickListener {
            navigateToCoe()
        }
    }

    private fun setupRecyclerView() {
        departmentAdapter = DepartmentAdapter(
            departmentList,
            onManageClick = { department -> manageDepartment(department) },
            onDeleteClick = { department -> showDeleteConfirmation(department) }
        )

        departmentRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@DepartmentsActivity)
            adapter = departmentAdapter
        }
    }

    private fun setupSearchFilter() {
        searchDepartment.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }

            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupCollegeFilter() {
        chipGroupCollege.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedCollege = when (checkedIds[0]) {
                    R.id.chip_jr_college -> "JR"
                    R.id.chip_sr_college -> "SR"
                    else -> "JR"
                }
                applyFilters()
            }
        }
    }

    private fun setupStreamFilter() {
        chipGroupStream.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                selectedStream = when (checkedIds[0]) {
                    R.id.chip_all_streams -> "All"
                    R.id.chip_science -> "Science"
                    R.id.chip_commerce -> "Commerce"
                    R.id.chip_arts -> "Arts"
                    else -> "All"
                }
                applyFilters()
            }
        }
    }

    private fun setupFilterCollapse() {
        filterHeader.setOnClickListener {
            toggleFilterExpansion()
        }
    }

    private fun toggleFilterExpansion() {
        isFilterExpanded = !isFilterExpanded

        if (isFilterExpanded) {
            filterContent.visibility = View.VISIBLE
            ObjectAnimator.ofFloat(filterExpandIcon, "rotation", 0f, 180f).apply {
                duration = 300
                start()
            }
        } else {
            filterContent.visibility = View.GONE
            ObjectAnimator.ofFloat(filterExpandIcon, "rotation", 180f, 0f).apply {
                duration = 300
                start()
            }
        }
    }

    private fun applyFilters() {
        val searchQuery = searchDepartment.text.toString()
        departmentAdapter.filter(selectedCollege, selectedStream, searchQuery)
        updateDepartmentCount()
    }

    private fun setupFAB() {
        fabAddDepartment.setOnClickListener {
            val intent = Intent(this, AddDepartmentActivity::class.java)
            startActivity(intent)
        }
    }

    private fun loadDepartmentsFromFirestore() {
        firestore.collection("departments")
            .orderBy("name", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Toast.makeText(this, "Error loading departments: ${error.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                departmentList.clear()
                snapshots?.documents?.forEach { document ->
                    val department = document.toObject(Department::class.java)
                    department?.let {
                        it.id = document.id
                        departmentList.add(it)
                    }
                }

                departmentAdapter.updateList(departmentList)
                applyFilters()
                updateEmptyState()
            }
    }

    private fun manageDepartment(department: Department) {
        val intent = Intent(this, ManageDepartmentActivity::class.java)
        intent.putExtra("DEPARTMENT_ID", department.id)
        startActivity(intent)
    }

    private fun showDeleteConfirmation(department: Department) {
        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle("Delete Department")
            .setMessage("Are you sure you want to delete ${department.name}?\n\nThis action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteDepartment(department)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteDepartment(department: Department) {
        firestore.collection("departments").document(department.id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, "${department.name} deleted successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error deleting department: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateDepartmentCount() {
        val count = departmentAdapter.itemCount
        departmentCount.text = "$count Department${if (count != 1) "s" else ""}"
    }

    private fun updateEmptyState() {
        if (departmentList.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            departmentRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            departmentRecyclerView.visibility = View.VISIBLE
        }
    }
}
