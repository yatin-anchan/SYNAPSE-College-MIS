package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*
import com.projectbyyatin.synapsemis.models.Class
import com.projectbyyatin.synapsemis.models.Subject

class MarkAttendanceHODActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    // UI Components
    private lateinit var spinnerClass: Spinner
    private lateinit var spinnerSubject: Spinner
    private lateinit var etDate: TextInputEditText
    private lateinit var etStartTime: TextInputEditText
    private lateinit var etDuration: TextInputEditText
    private lateinit var tvEndTime: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var cardSummary: MaterialCardView
    private lateinit var tvPresentCount: TextView
    private lateinit var tvAbsentCount: TextView
    private lateinit var tvTotalCount: TextView
    private lateinit var btnViewDrafts: MaterialButton
    private lateinit var btnSaveDraft: MaterialButton
    private lateinit var btnProceed: MaterialButton

    private var departmentId = ""
    private var departmentName = ""
    private var selectedClassId = ""
    private var selectedSubjectId = ""

    private var classList = mutableListOf<Class>()
    private var subjectList = mutableListOf<Subject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mark_attendance)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Get department info from intent
        departmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        Log.d("MARK_ATTENDANCE", "✅ Received departmentId: '$departmentId'")
        Log.d("MARK_ATTENDANCE", "✅ Received departmentName: '$departmentName'")

        if (departmentId.isEmpty()) {
            Toast.makeText(this, "⚠️ Department ID missing", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeViews()
        setupToolbar()
        setupDateTimePickers()
        setupSpinners()
        setupButtons()

        loadClasses()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        spinnerClass = findViewById(R.id.spinner_class)
        spinnerSubject = findViewById(R.id.spinner_subject)
        etDate = findViewById(R.id.et_date)
        etStartTime = findViewById(R.id.et_start_time)
        etDuration = findViewById(R.id.et_duration)
        tvEndTime = findViewById(R.id.tv_end_time)
        recyclerView = findViewById(R.id.recycler_view)
        emptyView = findViewById(R.id.empty_view)
        loadingProgress = findViewById(R.id.loading_progress)
        cardSummary = findViewById(R.id.card_summary)
        tvPresentCount = findViewById(R.id.tv_present_count)
        tvAbsentCount = findViewById(R.id.tv_absent_count)
        tvTotalCount = findViewById(R.id.tv_total_count)
        btnViewDrafts = findViewById(R.id.btn_view_drafts)
        btnSaveDraft = findViewById(R.id.btn_save_draft)
        btnProceed = findViewById(R.id.btn_proceed)

        recyclerView.layoutManager = LinearLayoutManager(this)

        // Set today's date by default
        val today = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
        etDate.setText(today)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Mark Attendance"
            subtitle = departmentName
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupDateTimePickers() {
        // Date Picker
        etDate.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    val date = String.format("%02d/%02d/%04d", day, month + 1, year)
                    etDate.setText(date)
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        // Start Time Picker
        etStartTime.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    val time = String.format("%02d:%02d", hour, minute)
                    etStartTime.setText(time)
                    calculateEndTime()
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
            ).show()
        }

        // Duration input listener
        etDuration.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                calculateEndTime()
            }
        }
    }

    private fun calculateEndTime() {
        val startTime = etStartTime.text.toString()
        val durationStr = etDuration.text.toString()

        if (startTime.isNotEmpty() && durationStr.isNotEmpty()) {
            try {
                val duration = durationStr.toInt()
                val timeParts = startTime.split(":")
                val calendar = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    set(Calendar.MINUTE, timeParts[1].toInt())
                    add(Calendar.MINUTE, duration)
                }
                val endTime = String.format(
                    "%02d:%02d",
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE)
                )
                tvEndTime.text = "End Time: $endTime"
                tvEndTime.visibility = View.VISIBLE
            } catch (e: Exception) {
                tvEndTime.visibility = View.GONE
            }
        } else {
            tvEndTime.visibility = View.GONE
        }
    }

    private fun setupSpinners() {
        spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position <= classList.size) {
                    selectedClassId = classList[position - 1].id
                    Log.d("MARK_ATTENDANCE", "✅ Selected class ID: $selectedClassId")
                    loadSubjects()
                } else {
                    selectedClassId = ""
                    subjectList.clear()
                    updateSubjectSpinner()
                    showEmptyView()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position > 0 && position <= subjectList.size) {
                    selectedSubjectId = subjectList[position - 1].id
                    Log.d("MARK_ATTENDANCE", "✅ Selected subject ID: $selectedSubjectId")
                    loadStudents()
                } else {
                    selectedSubjectId = ""
                    showEmptyView()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupButtons() {
        btnViewDrafts.setOnClickListener {
            val intent = Intent(this, AttendanceDraftsActivity::class.java).apply {
                putExtra("DEPARTMENT_ID", departmentId)
                putExtra("DEPARTMENT_NAME", departmentName)
                putExtra("FACULTY_ID", auth.currentUser?.uid ?: "")
                putExtra("USER_ROLE", "hod")
            }
            startActivity(intent)
        }

        btnSaveDraft.setOnClickListener {
            if (validateInputs()) {
                saveDraft()
            }
        }
        btnProceed.setOnClickListener {
            if (validateInputs()) {
                proceedToMarkAttendance()
            }
        }
    }

    private fun loadClasses() {
        showLoading(true)

        firestore.collection("classes")
            .whereEqualTo("departmentId", departmentId)
            .get()
            .addOnSuccessListener { documents ->
                classList.clear()

                for (doc in documents) {
                    val classItem = doc.toObject(Class::class.java)
                    classItem.id = doc.id
                    classList.add(classItem)
                }

                Log.d("MARK_ATTENDANCE", "✅ Loaded ${classList.size} classes")
                updateClassSpinner()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Log.e("MARK_ATTENDANCE", "❌ Error loading classes", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                showLoading(false)
            }
    }

    private fun loadSubjects() {
        if (selectedClassId.isEmpty()) return

        showLoading(true)

        firestore.collection("classes").document(selectedClassId)
            .get()
            .addOnSuccessListener { classDoc ->
                val selectedClass = classDoc.toObject(Class::class.java)
                val currentSemester = selectedClass?.currentSemester ?: 1

                Log.d("MARK_ATTENDANCE", "📚 Class semester: $currentSemester")

                firestore.collection("subjects")
                    .whereEqualTo("departmentId", departmentId)
                    .whereEqualTo("semesterNumber", currentSemester)
                    .get()
                    .addOnSuccessListener { documents ->
                        subjectList.clear()
                        for (doc in documents) {
                            val subject = doc.toObject(Subject::class.java)
                            subject.id = doc.id
                            subjectList.add(subject)
                        }

                        Log.d("MARK_ATTENDANCE", "✅ Loaded ${subjectList.size} subjects for Sem $currentSemester")
                        updateSubjectSpinner()
                        showLoading(false)
                    }
                    .addOnFailureListener { e ->
                        Log.e("MARK_ATTENDANCE", "❌ Subjects error", e)
                        Toast.makeText(this, "No subjects for Sem $currentSemester", Toast.LENGTH_SHORT).show()
                        subjectList.clear()
                        updateSubjectSpinner()
                        showLoading(false)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("MARK_ATTENDANCE", "❌ Class error", e)
                showLoading(false)
            }
    }

    private fun loadStudents() {
        if (selectedClassId.isEmpty()) return

        showLoading(true)
        emptyView.visibility = View.GONE

        firestore.collection("students")
            .whereEqualTo("classId", selectedClassId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    emptyView.text = "No students found in this class"
                    emptyView.visibility = View.VISIBLE
                    recyclerView.visibility = View.GONE
                    cardSummary.visibility = View.GONE
                    btnProceed.visibility = View.GONE
                } else {
                    Log.d("MARK_ATTENDANCE", "✅ Loaded ${documents.size()} students")

                    tvTotalCount.text = "Total: ${documents.size()}"
                    tvPresentCount.text = "Present: ${documents.size()}"
                    tvAbsentCount.text = "Absent: 0"

                    emptyView.visibility = View.GONE
                    recyclerView.visibility = View.GONE
                    cardSummary.visibility = View.VISIBLE
                    btnProceed.visibility = View.VISIBLE
                }
                showLoading(false)
            }
            .addOnFailureListener { e ->
                Log.e("MARK_ATTENDANCE", "❌ Error loading students", e)
                Toast.makeText(this, "Error loading students: ${e.message}", Toast.LENGTH_SHORT).show()
                emptyView.text = "Error loading students"
                emptyView.visibility = View.VISIBLE
                btnProceed.visibility = View.GONE
                showLoading(false)
            }
    }

    private fun proceedToMarkAttendance() {
        val selectedClass = classList.find { it.id == selectedClassId }
        val selectedSubject = subjectList.find { it.id == selectedSubjectId }

        val intent = Intent(this, MarkStudentAttendanceActivity::class.java).apply {
            putExtra("DEPARTMENT_ID", departmentId)
            putExtra("DEPARTMENT_NAME", departmentName)
            putExtra("CLASS_ID", selectedClassId)
            putExtra("CLASS_NAME", selectedClass?.className ?: "")
            putExtra("SUBJECT_ID", selectedSubjectId)
            putExtra("SUBJECT_NAME", selectedSubject?.name ?: "")
            putExtra("DATE", etDate.text.toString())
            putExtra("START_TIME", etStartTime.text.toString())
            putExtra("END_TIME", tvEndTime.text.toString().replace("End Time: ", ""))
            putExtra("DURATION", etDuration.text.toString().toIntOrNull() ?: 0)
        }
        startActivity(intent)
    }

    private fun updateClassSpinner() {
        val items = mutableListOf("Select Class")
        items.addAll(classList.map { it.className })

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerClass.adapter = adapter
    }

    private fun updateSubjectSpinner() {
        val items = mutableListOf("Select Subject")
        items.addAll(subjectList.map { it.name.ifEmpty { "Unnamed Subject" } })

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSubject.adapter = adapter

        Log.d("MARK_ATTENDANCE", "Subject spinner updated: ${items.size} items")
    }

    private fun validateInputs(): Boolean {
        if (selectedClassId.isEmpty()) {
            Toast.makeText(this, "Please select a class", Toast.LENGTH_SHORT).show()
            return false
        }
        if (selectedSubjectId.isEmpty()) {
            Toast.makeText(this, "Please select a subject", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etDate.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please select a date", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etStartTime.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please select start time", Toast.LENGTH_SHORT).show()
            return false
        }
        if (etDuration.text.isNullOrEmpty()) {
            Toast.makeText(this, "Please enter duration", Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun saveDraft() {
        Toast.makeText(this, "Save Draft - Coming Soon", Toast.LENGTH_SHORT).show()
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun showEmptyView() {
        emptyView.text = "Please select class and subject"
        emptyView.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        cardSummary.visibility = View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}