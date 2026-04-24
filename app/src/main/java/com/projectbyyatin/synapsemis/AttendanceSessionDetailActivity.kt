package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.SessionStudentAdapter
import com.projectbyyatin.synapsemis.models.Attendance

class AttendanceSessionDetailActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var toolbar: Toolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: TextView

    // Summary card views
    private lateinit var tvSessionDate: TextView
    private lateinit var tvSessionSubject: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvLateCount: TextView

    private val attendanceList = mutableListOf<Attendance>()
    private lateinit var adapter: SessionStudentAdapter

    private var reportId = ""
    private var subjectName = ""
    private var date = ""
    private var classId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendance_session_detail)

        reportId    = intent.getStringExtra("REPORT_ID") ?: ""
        subjectName = intent.getStringExtra("SUBJECT_NAME") ?: ""
        date        = intent.getStringExtra("DATE") ?: ""
        classId     = intent.getStringExtra("CLASS_ID") ?: ""

        initViews()
        setupToolbar()
        setupRecyclerView()
        loadSessionAttendance()
    }

    private fun initViews() {
        db             = FirebaseFirestore.getInstance()
        toolbar        = findViewById(R.id.toolbar)
        recyclerView   = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView      = findViewById(R.id.empty_view)
        tvSessionDate    = findViewById(R.id.tv_session_date)
        tvSessionSubject = findViewById(R.id.tv_session_subject)
        tvTotalCount     = findViewById(R.id.tv_total_count)
        tvPresentCount   = findViewById(R.id.tv_present_count)
        tvAbsentCount    = findViewById(R.id.tv_absent_count)
        tvLateCount      = findViewById(R.id.tv_late_count)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = subjectName
            subtitle = date
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SessionStudentAdapter(attendanceList)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadSessionAttendance() {
        showLoading(true)

        // Query attendance records for this specific date + classId
        db.collection("attendance")
            .whereEqualTo("classId", classId)
            .whereEqualTo("date", date)
            .orderBy("studentRollNo")
            .get()
            .addOnSuccessListener { snap ->
                attendanceList.clear()

                for (doc in snap) {
                    val att = doc.toObject(Attendance::class.java)
                    att.id = doc.id
                    attendanceList.add(att)
                }

                adapter.notifyDataSetChanged()
                updateSummaryCard(attendanceList)
                showLoading(false)

                if (attendanceList.isEmpty()) {
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                } else {
                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                emptyView.visibility = View.VISIBLE
            }
    }

    private fun updateSummaryCard(list: List<Attendance>) {
        val total   = list.size
        val present = list.count { it.status == "present" }
        val absent  = list.count { it.status == "absent" }
        val late    = list.count { it.status == "late" }

        tvSessionDate.text    = date
        tvSessionSubject.text = subjectName
        tvTotalCount.text     = total.toString()
        tvPresentCount.text   = present.toString()
        tvAbsentCount.text    = absent.toString()
        tvLateCount.text      = late.toString()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility    = if (show) View.GONE else View.VISIBLE
    }
}