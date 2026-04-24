package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.DeptStudentAdapter
import com.projectbyyatin.synapsemis.models.Student

class AddStudentsToDepartmentActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: DeptStudentAdapter
    private lateinit var firestore: FirebaseFirestore

    private lateinit var departmentId: String
    private lateinit var departmentName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_students_dept)

        departmentId = intent.getStringExtra("DEPARTMENT_ID")!!
        departmentName = intent.getStringExtra("DEPARTMENT_NAME")!!

        recycler = findViewById(R.id.recycler)
        progress = findViewById(R.id.progress)

        firestore = FirebaseFirestore.getInstance()

        adapter = DeptStudentAdapter(
            onAssign = { assign(it) },
            onRemove = {},
            onOpenProfile = {}
        )

        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        loadUnassignedStudents()
    }

    private fun loadUnassignedStudents() {
        progress.visibility = View.VISIBLE

        firestore.collection("students")
            .whereEqualTo("departmentId", "")
            .addSnapshotListener { snapshot, _ ->
                val list = snapshot?.toObjects(Student::class.java) ?: emptyList()
                progress.visibility = View.GONE
                adapter.submit(list)
            }
    }

    private fun assign(student: Student) {
        firestore.collection("students").document(student.id)
            .update(
                "departmentId", departmentId,
                "departmentName", departmentName
            )
            .addOnSuccessListener { finish() }
    }
}
