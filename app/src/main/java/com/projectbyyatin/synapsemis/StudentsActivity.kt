package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.StudentsAdapter
import com.projectbyyatin.synapsemis.models.Student
import com.google.android.material.textfield.TextInputLayout
import android.widget.ArrayAdapter

class StudentsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar

    // Search
    private lateinit var searchInput: TextInputEditText

    // Filters UI (collapsible)
    private lateinit var filterHeader: LinearLayout
    private lateinit var filtersContent: LinearLayout
    private lateinit var filterExpandIcon: ImageView
    private var isFiltersExpanded = false

    // Filter fields
    private lateinit var departmentFilter: AutoCompleteTextView
    private lateinit var classFilter: AutoCompleteTextView
    private lateinit var batchFilter: AutoCompleteTextView
    private lateinit var genderFilter: AutoCompleteTextView
    private lateinit var statusFilter: AutoCompleteTextView

    // Recycler / state
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var totalCountText: TextView
    private lateinit var activeCountText: TextView
    private lateinit var inactiveCountText: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: StudentsAdapter
    private val studentsList = mutableListOf<Student>()
    private val filteredList = mutableListOf<Student>()

    // Current filter values
    private var selectedDepartment = "All"
    private var selectedClass = "All"
    private var selectedBatch = "All"
    private var selectedGender = "All"
    private var selectedStatus = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_students)

        firestore = FirebaseFirestore.getInstance()

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupFilterCollapse()
        setupFilters()
        setupSearch()
        loadStudents()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)

        // Stats
        totalCountText = findViewById(R.id.total_count)
        activeCountText = findViewById(R.id.active_count)
        inactiveCountText = findViewById(R.id.inactive_count)

        // Search
        searchInput = findViewById(R.id.search_input)

        // Collapsible filter views
        filterHeader = findViewById(R.id.filter_header)
        filtersContent = findViewById(R.id.filters_content)
        filterExpandIcon = findViewById(R.id.filter_expand_icon)

        // Filter text fields
        departmentFilter = findViewById(R.id.department_filter)
        classFilter = findViewById(R.id.class_filter)
        batchFilter = findViewById(R.id.batch_filter)
        genderFilter = findViewById(R.id.gender_filter)
        statusFilter = findViewById(R.id.status_filter)

        // Recycler / state
        recyclerView = findViewById(R.id.recycler_view)
        progressBar = findViewById(R.id.progress_bar)
        emptyView = findViewById(R.id.empty_view)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Students"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = StudentsAdapter(
            studentsList = filteredList
        ) { student ->
            // Navigate to details
            val intent = android.content.Intent(this, StudentDetailsActivity::class.java)
            intent.putExtra("STUDENT_ID", student.id)
            startActivity(intent)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFilterCollapse() {
        // Start collapsed
        filtersContent.visibility = View.GONE
        filterExpandIcon.rotation = 0f
        isFiltersExpanded = false

        filterHeader.setOnClickListener { toggleFilters() }
    }

    private fun toggleFilters() {
        isFiltersExpanded = !isFiltersExpanded

        if (isFiltersExpanded) {
            filtersContent.visibility = View.VISIBLE
            filterExpandIcon.animate()
                .rotation(180f)
                .setDuration(200)
                .start()
        } else {
            filtersContent.visibility = View.GONE
            filterExpandIcon.animate()
                .rotation(0f)
                .setDuration(200)
                .start()
        }
    }

    private fun setupFilters() {
        // Gender
        val genders = listOf("All", "Male", "Female", "Other")
        genderFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, genders)
        )
        genderFilter.setText("All", false)
        genderFilter.setOnItemClickListener { _, _, position, _ ->
            selectedGender = genders[position]
            applyFilters()
        }

        // Status
        val statuses = listOf("All", "Active", "Inactive")
        statusFilter.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statuses)
        )
        statusFilter.setText("All", false)
        statusFilter.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = statuses[position]
            applyFilters()
        }

        // Department dropdown from Firestore
        firestore.collection("departments")
            .get()
            .addOnSuccessListener { documents ->
                val departments = mutableListOf("All")
                for (doc in documents) {
                    doc.getString("name")?.let { departments.add(it) }
                }

                departmentFilter.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, departments)
                )
                departmentFilter.setText("All", false)
                departmentFilter.setOnItemClickListener { _, _, position, _ ->
                    selectedDepartment = departments[position]
                    applyFilters()
                }
            }

        // Class dropdown from Firestore
        firestore.collection("classes")
            .get()
            .addOnSuccessListener { documents ->
                val classes = mutableListOf("All")
                for (doc in documents) {
                    doc.getString("className")?.let { classes.add(it) }
                }

                classFilter.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, classes)
                )
                classFilter.setText("All", false)
                classFilter.setOnItemClickListener { _, _, position, _ ->
                    selectedClass = classes[position]
                    applyFilters()
                }
            }

        // Batch dropdown derived from students
        firestore.collection("students")
            .get()
            .addOnSuccessListener { documents ->
                val batchSet = mutableSetOf("All")
                for (doc in documents) {
                    doc.getString("academicYear")?.let { batchSet.add(it) }
                }
                val batchList = batchSet.toList().sorted()

                batchFilter.setAdapter(
                    ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, batchList)
                )
                batchFilter.setText("All", false)
                batchFilter.setOnItemClickListener { _, _, position, _ ->
                    selectedBatch = batchList[position]
                    applyFilters()
                }
            }
    }

    private fun setupSearch() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }
        })
    }

    private fun loadStudents() {
        progressBar.visibility = View.VISIBLE

        firestore.collection("students")
            .get()
            .addOnSuccessListener { documents ->
                studentsList.clear()
                for (document in documents) {
                    val student = document.toObject(Student::class.java)
                    student.id = document.id
                    studentsList.add(student)
                }
                applyFilters()
                updateCounts()
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            }
    }

    private fun applyFilters() {
        val query = searchInput.text?.toString()?.trim()?.lowercase() ?: ""

        filteredList.clear()
        filteredList.addAll(
            studentsList.filter { student ->
                val matchesSearch =
                    query.isEmpty() ||
                            student.fullName.lowercase().contains(query) ||
                            student.email.lowercase().contains(query) ||
                            student.studentId.lowercase().contains(query) ||
                            student.rollNumber.lowercase().contains(query)

                val matchesDept =
                    selectedDepartment == "All" || student.departmentName == selectedDepartment

                val matchesClass =
                    selectedClass == "All" || student.className == selectedClass

                val matchesBatch =
                    selectedBatch == "All" || student.academicYear == selectedBatch

                val matchesGender =
                    selectedGender == "All" || student.gender == selectedGender

                val matchesStatus =
                    selectedStatus == "All" ||
                            (selectedStatus == "Active" && student.isActive) ||
                            (selectedStatus == "Inactive" && !student.isActive)

                matchesSearch &&
                        matchesDept &&
                        matchesClass &&
                        matchesBatch &&
                        matchesGender &&
                        matchesStatus
            }
        )

        adapter.updateList(filteredList)
        updateEmptyView()
    }

    private fun updateCounts() {
        val total = studentsList.size
        val active = studentsList.count { it.isActive }
        val inactive = total - active

        totalCountText.text = "$total Total"
        activeCountText.text = "$active Active"
        inactiveCountText.text = "$inactive Inactive"
    }

    private fun updateEmptyView() {
        val isEmpty = filteredList.isEmpty()
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onResume() {
        super.onResume()
        loadStudents()
    }
}
