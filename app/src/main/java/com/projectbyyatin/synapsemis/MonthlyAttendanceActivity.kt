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
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.adapters.AttendanceAdapter
import com.projectbyyatin.synapsemis.models.Attendance
import com.projectbyyatin.synapsemis.models.ClassStudent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MonthlyAttendanceActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var selectedDateText: TextView
    private lateinit var btnSelectDate: MaterialButton
    private lateinit var btnMarkAll: MaterialButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var emptyView: View

    private lateinit var firestore: FirebaseFirestore
    private lateinit var adapter: AttendanceAdapter
    private var attendanceList = mutableListOf<Attendance>()
    private var studentsList = mutableListOf<ClassStudent>()

    private var classId: String = ""
    private var className: String = ""
    private var currentSemester: Int = 1
    private var selectedDate: String = ""
    private var selectedMonth: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monthly_attendance)

        classId = intent.getStringExtra("CLASS_ID") ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""
        currentSemester = intent.getIntExtra("CURRENT_SEMESTER", 1)

        // Set today as default date
        val calendar = Calendar.getInstance()
        selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        selectedMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

        initializeViews()
        setupToolbar()
        setupButtons()
        setupRecyclerView()
        loadStudents()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        selectedDateText = findViewById(R.id.selected_date_text)
        btnSelectDate = findViewById(R.id.btn_select_date)
        btnMarkAll = findViewById(R.id.btn_mark_all)
        recyclerView = findViewById(R.id.recycler_view)
        loadingProgress = findViewById(R.id.loading_progress)
        emptyView = findViewById(R.id.empty_view)

        firestore = FirebaseFirestore.getInstance()

        // Display selected date
        updateDateDisplay()
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Monthly Attendance"
        supportActionBar?.subtitle = className
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        btnSelectDate.setOnClickListener {
            showDatePicker()
        }

        btnMarkAll.setOnClickListener {
            markAllPresent()
        }
    }

    private fun setupRecyclerView() {
        adapter = AttendanceAdapter(
            attendanceList,
            onStatusChange = { attendance, newStatus ->
                updateAttendanceStatus(attendance, newStatus)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadStudents() {
        showLoading(true)

        firestore.collection("class_students")
            .whereEqualTo("classId", classId)
            .whereEqualTo("status", "approved")
            .get()
            .addOnSuccessListener { documents ->
                studentsList.clear()

                documents.forEach { document ->
                    val student = document.toObject(ClassStudent::class.java)
                    student.id = document.id
                    studentsList.add(student)
                }

                loadAttendanceForDate()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAttendanceForDate() {
        firestore.collection("attendance")
            .whereEqualTo("classId", classId)
            .whereEqualTo("date", selectedDate)
            .get()
            .addOnSuccessListener { documents ->
                val existingAttendance = mutableMapOf<String, Attendance>()

                documents.forEach { document ->
                    val attendance = document.toObject(Attendance::class.java)
                    attendance.id = document.id
                    existingAttendance[attendance.studentId] = attendance
                }

                // Create attendance list for all students
                attendanceList.clear()
                studentsList.forEach { student ->
                    val attendance = existingAttendance[student.studentId] ?: Attendance(
                        classId = classId,
                        studentId = student.studentId,
                        studentName = student.studentName,
                        studentRollNo = student.rollNumber,
                        date = selectedDate,
                        month = selectedMonth,
                        semester = currentSemester,
                        academicYear = "2025-2026",
                        status = "absent",
                        markedBy = "",
                        markedByName = "",
                        markedAt = 0L
                    )
                    attendanceList.add(attendance)
                }

                adapter.updateList(attendanceList)
                showLoading(false)
                updateEmptyView()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Toast.makeText(this, "Error loading attendance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showDatePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText("Select Date")
            .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
            .build()

        datePicker.addOnPositiveButtonClickListener { selection ->
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = selection

            selectedDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
            selectedMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(calendar.time)

            updateDateDisplay()
            loadAttendanceForDate()
        }

        datePicker.show(supportFragmentManager, "DATE_PICKER")
    }

    private fun updateDateDisplay() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy (EEEE)", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val parts = selectedDate.split("-")
        calendar.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())

        selectedDateText.text = dateFormat.format(calendar.time)
    }

    private fun updateAttendanceStatus(attendance: Attendance, newStatus: String) {
        val updatedAttendance = attendance.copy(
            status = newStatus,
            markedBy = "Admin", // Replace with actual user ID
            markedByName = "Admin Name",
            markedAt = System.currentTimeMillis()
        )

        if (attendance.id.isEmpty()) {
            // Create new attendance record
            firestore.collection("attendance")
                .add(updatedAttendance)
                .addOnSuccessListener { documentReference ->
                    attendance.id = documentReference.id
                    Toast.makeText(this, "Attendance marked: $newStatus", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Update existing attendance
            firestore.collection("attendance").document(attendance.id)
                .set(updatedAttendance)
                .addOnSuccessListener {
                    Toast.makeText(this, "Attendance updated: $newStatus", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun markAllPresent() {
        showLoading(true)
        var completedCount = 0
        val totalCount = attendanceList.size

        attendanceList.forEach { attendance ->
            updateAttendanceStatus(attendance, "present")
            completedCount++

            if (completedCount == totalCount) {
                loadAttendanceForDate()
                Toast.makeText(this, "All students marked present", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
        recyclerView.visibility = if (show) View.GONE else View.VISIBLE
    }

    private fun updateEmptyView() {
        emptyView.visibility = if (attendanceList.isEmpty()) View.VISIBLE else View.GONE
    }
}
