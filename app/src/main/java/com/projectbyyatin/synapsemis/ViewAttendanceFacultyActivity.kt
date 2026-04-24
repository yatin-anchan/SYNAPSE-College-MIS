package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.R
import com.projectbyyatin.synapsemis.adapters.ViewAttendanceAdapter
import com.projectbyyatin.synapsemis.faculty.ViewAttendanceItem
import java.text.SimpleDateFormat
import java.util.*

class ViewAttendanceFacultyActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ViewAttendanceFaculty"
    }

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: ViewAttendanceAdapter

    private val attendanceList = mutableListOf<ViewAttendanceItem>()

    private var facultyId = ""
    private var facultyDocId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_attendance_faculty)

        facultyId = intent.getStringExtra("FACULTY_ID") ?: ""
        facultyDocId = intent.getStringExtra("FACULTY_DOC_ID") ?: ""

        firestore = FirebaseFirestore.getInstance()

        initViews()
        setupRecycler()
        loadAttendance()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recycler_attendance)
        progressBar = findViewById(R.id.progress_bar)
        emptyText = findViewById(R.id.empty_text)
    }

    private fun setupRecycler() {
        adapter = ViewAttendanceAdapter(attendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadAttendance() {
        progressBar.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        firestore.collection("attendance")
            .whereEqualTo("markedBy", facultyDocId)
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { docs ->
                attendanceList.clear()

                for (doc in docs) {
                    attendanceList.add(
                        ViewAttendanceItem(
                            subjectName = doc.getString("subjectName") ?: "Subject",
                            className = doc.getString("className") ?: "Class",
                            period = doc.getString("period") ?: "-",
                            date = formatDate(doc.getTimestamp("date")?.toDate()),
                            present = doc.getLong("presentCount")?.toInt() ?: 0,
                            absent = doc.getLong("absentCount")?.toInt() ?: 0
                        )
                    )
                }

                progressBar.visibility = View.GONE

                if (attendanceList.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    adapter.notifyDataSetChanged()
                }

                Log.d(TAG, "Loaded ${attendanceList.size} attendance records")
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                emptyText.visibility = View.VISIBLE
                Log.e(TAG, "Failed to load attendance: ${e.message}", e)
            }
    }

    private fun formatDate(date: Date?): String {
        if (date == null) return "-"
        return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(date)
    }
}