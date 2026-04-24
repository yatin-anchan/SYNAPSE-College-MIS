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
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.ClassesAdapter
import com.projectbyyatin.synapsemis.models.Class

class ManageClassesActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var fab: FloatingActionButton
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: ClassesAdapter
    private var classesList = mutableListOf<Class>()

    private var departmentId: String = ""
    private var departmentName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_classes)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupFab()
        loadClasses()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        recyclerView = findViewById(R.id.recycler_view)
        fab = findViewById(R.id.fab)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Manage Classes"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ClassesAdapter(
            classesList,
            onViewClick = { classItem -> viewClassDetails(classItem) },
            onEditClick = { classItem -> editClass(classItem) }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupFab() {
        fab.setOnClickListener {
            val intent = Intent(this, AddClassActivity::class.java)
            intent.putExtra("DEPARTMENT_ID", departmentId)
            intent.putExtra("DEPARTMENT_NAME", departmentName)
            startActivity(intent)
        }
    }

    private fun loadClasses() {
        showLoading(true)

        firestore.collection("classes")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                classesList.clear()

                documents.forEach { document ->
                    val classItem = document.toObject(Class::class.java)
                    classItem.id = document.id
                    classesList.add(classItem)
                }

                adapter.updateList(classesList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading classes: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun viewClassDetails(classItem: Class) {
        val intent = Intent(this, ClassDetailsActivity::class.java)
        intent.putExtra("CLASS_ID", classItem.id)
        startActivity(intent)
    }

    private fun editClass(classItem: Class) {
        val intent = Intent(this, EditClassActivity::class.java)
        intent.putExtra("CLASS_ID", classItem.id)
        startActivity(intent)
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (classesList.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        loadClasses()
    }
}
