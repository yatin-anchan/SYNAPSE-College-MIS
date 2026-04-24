package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.DeptStudentAdapter
import com.projectbyyatin.synapsemis.models.Student

class ManageDeptStudentsActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var recycler: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var progress: ProgressBar
    private lateinit var fab: FloatingActionButton

    private lateinit var adapter: DeptStudentAdapter
    private lateinit var firestore: FirebaseFirestore

    private lateinit var departmentId: String
    private lateinit var departmentName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_dept_students)

        departmentId = intent.getStringExtra("DEPARTMENT_ID")!!
        departmentName = intent.getStringExtra("DEPARTMENT_NAME")!!

        toolbar = findViewById(R.id.toolbar)
        recycler = findViewById(R.id.recycler)
        emptyText = findViewById(R.id.empty_text)
        progress = findViewById(R.id.progress)
        fab = findViewById(R.id.fab_add)

        toolbar.setNavigationOnClickListener { finish() }

        firestore = FirebaseFirestore.getInstance()

        adapter = DeptStudentAdapter(
            onAssign = {},
            onRemove = { removeStudent(it) },
            onOpenProfile = { openStudentProfile(it) }
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        fab.setOnClickListener {
            val i = Intent(this, AddStudentsToDepartmentActivity::class.java)
            i.putExtra("DEPARTMENT_ID", departmentId)
            i.putExtra("DEPARTMENT_NAME", departmentName)
            startActivity(i)
        }

        loadStudents()
    }

    private fun loadStudents() {
        progress.visibility = View.VISIBLE

        firestore.collection("students")
            .whereEqualTo("departmentId", departmentId)
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Student::class.java) ?: emptyList()
                progress.visibility = View.GONE
                adapter.submit(list)
                emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
            }
    }

    private fun removeStudent(student: Student) {
        firestore.collection("students").document(student.id)
            .update(
                "departmentId", "",
                "departmentName", ""
            )
    }

    private fun openStudentProfile(student: Student) {
        val i = Intent(this, StudentDetailsActivity::class.java)
        i.putExtra("STUDENT_ID", student.id)
        startActivity(i)
    }
}
