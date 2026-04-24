package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.projectbyyatin.synapsemis.adapters.SelectFacultyAdapter
import com.projectbyyatin.synapsemis.models.Faculty

class ChangeHODActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var searchView: SearchView
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: SelectFacultyAdapter
    private var facultyList = mutableListOf<Faculty>()

    private var departmentId: String = ""
    private var departmentName: String = ""
    private var currentHODId: String = ""

    companion object {
        const val ROLE_FACULTY = "faculty"
        const val ROLE_HOD = "hod"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_hod)

        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        initializeViews()
        setupToolbar()
        setupRecyclerView()
        setupSearch()
        loadCurrentHOD()
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
        supportActionBar?.title = "Change HOD"
        supportActionBar?.subtitle = departmentName
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SelectFacultyAdapter(facultyList) { faculty ->
            showConfirmationDialog(faculty)
        }

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

    private fun loadCurrentHOD() {
        firestore.collection("departments").document(departmentId)
            .get()
            .addOnSuccessListener { document ->
                currentHODId = document.getString("hodId") ?: ""
            }
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

    private fun showConfirmationDialog(faculty: Faculty) {
        val isCurrentHOD = faculty.id == currentHODId

        val message = if (isCurrentHOD) {
            "Remove ${faculty.name} as HOD of $departmentName?\n\nThey will be changed back to regular faculty."
        } else {
            "Assign ${faculty.name} as HOD of $departmentName?" +
                    if (currentHODId.isNotEmpty()) "\n\nCurrent HOD will be changed back to regular faculty." else ""
        }

        AlertDialog.Builder(this, R.style.CustomAlertDialog)
            .setTitle(if (isCurrentHOD) "Remove HOD" else "Change HOD")
            .setMessage(message)
            .setPositiveButton("Confirm") { _, _ ->
                if (isCurrentHOD) {
                    removeHOD(faculty)
                } else {
                    changeHOD(faculty)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun changeHOD(newHOD: Faculty) {
        showLoading(true)

        firestore.collection("users").document(newHOD.id)
            .get()
            .addOnSuccessListener { userDoc ->
                if (!userDoc.exists()) {
                    // ❌ Do NOT create user
                    showLoading(false)
                    Toast.makeText(
                        this,
                        "Cannot assign HOD. User account not found.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                // ✅ User exists → proceed
                performHODChange(newHOD)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }


    private fun performHODChange(newHOD: Faculty) {
        val batch = firestore.batch()

        // Update department
        val deptRef = firestore.collection("departments").document(departmentId)
        batch.update(deptRef, mapOf(
            "hod" to newHOD.name,
            "hodId" to newHOD.id,
            "hodEmail" to newHOD.email,
            "hodPhone" to (newHOD.phone ?: "")
        ))

        // Demote old HOD if exists
        if (currentHODId.isNotEmpty() && currentHODId != newHOD.id) {
            val oldHODFacultyRef = firestore.collection("faculty").document(currentHODId)
            batch.update(oldHODFacultyRef, "role", "faculty")

            val oldHODUserRef = firestore.collection("users").document(currentHODId)
            batch.update(oldHODUserRef, "role", "faculty")
        }

        // Promote new HOD in faculty collection
        val newHODFacultyRef = firestore.collection("faculty").document(newHOD.id)
        batch.update(newHODFacultyRef, "role", "hod")

        // Promote new HOD in users collection
        val newHODUserRef = firestore.collection("users").document(newHOD.id)
        batch.update(newHODUserRef, mapOf(
            "role" to "hod",
            "profileCompleted" to true
        ))

        // Commit with detailed logging
        batch.commit()
            .addOnSuccessListener {
                Log.d("ChangeHOD", "✅ Batch commit successful!")
                Log.d("ChangeHOD", "New HOD: ${newHOD.name} (${newHOD.id})")
                Log.d("ChangeHOD", "Role set to: hod")
                showLoading(false)
                Toast.makeText(this, "✅ ${newHOD.name} is now HOD", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("ChangeHOD", "❌ Batch commit FAILED: ${e.message}")
                e.printStackTrace()
                showLoading(false)
                Toast.makeText(this, "❌ Failed to assign HOD: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun removeHOD(faculty: Faculty) {
        showLoading(true)
        val batch = firestore.batch()

        // Clear HOD info from department
        val deptRef = firestore.collection("departments").document(departmentId)
        batch.update(deptRef, mapOf(
            "hod" to "",
            "hodId" to "",
            "hodEmail" to "",
            "hodPhone" to ""
        ))

        // Demote HOD in faculty collection
        val facultyRef = firestore.collection("faculty").document(faculty.id)
        batch.update(facultyRef, "role", "faculty")

        // Demote HOD in users collection
        val userRef = firestore.collection("users").document(faculty.id)
        batch.update(userRef, mapOf(
            "role" to "faculty",
            "profileCompleted" to true
        ))

        batch.commit()
            .addOnSuccessListener {
                Log.d("ChangeHOD", "✅ HOD removed successfully")
                showLoading(false)
                Toast.makeText(this, "✅ HOD removed", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Log.e("ChangeHOD", "❌ Failed to remove HOD: ${e.message}")
                e.printStackTrace()
                showLoading(false)
                Toast.makeText(this, "❌ Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (facultyList.isEmpty()) View.VISIBLE else View.GONE
    }
}