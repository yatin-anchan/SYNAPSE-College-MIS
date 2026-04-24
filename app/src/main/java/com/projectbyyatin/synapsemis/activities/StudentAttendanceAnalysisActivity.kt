package com.projectbyyatin.synapsemis

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.PercentFormatter
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Subject
import java.text.SimpleDateFormat
import java.util.*

class StudentAttendanceAnalysisActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private lateinit var studentId   : String
    private lateinit var studentName : String
    private lateinit var rollNumber  : String
    private lateinit var classId     : String
    private lateinit var currentMonth: String

    private lateinit var tvStudentName      : TextView
    private lateinit var tvRollNo           : TextView
    private lateinit var tvOverallPercent   : TextView
    private lateinit var tvAttendanceStatus : TextView
    private lateinit var tvDayStats         : TextView
    private lateinit var circularProgress   : CircularProgressIndicator
    private lateinit var spinnerSubject     : Spinner
    private lateinit var chipGroupMonth     : ChipGroup
    private lateinit var barChart           : BarChart
    private lateinit var lineChart          : LineChart
    private lateinit var pieChart           : PieChart
    private lateinit var cardRisk           : MaterialCardView
    private lateinit var tvRiskTitle        : TextView
    private lateinit var tvRiskDetail       : TextView
    private lateinit var cardShortfall      : MaterialCardView
    private lateinit var tvShortfallTitle   : TextView
    private lateinit var tvShortfallDetail  : TextView
    private lateinit var cardAiFeedback     : MaterialCardView
    private lateinit var tvAiFeedback       : TextView
    private lateinit var progressBar        : LinearProgressIndicator

    private val subjectList     = mutableListOf<Subject>()
    private var selectedSubjectId = ""
    private var selectedMonth     = ""

    private val SAFE   = 75f
    private val DANGER = 60f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_attendance_analysis)

        readExtras()
        bindViews()
        setupToolbar()
        setupMonthChips()
        loadSubjects()
    }

    private fun readExtras() {
        studentId    = intent.getStringExtra("STUDENT_ID")    ?: ""
        studentName  = intent.getStringExtra("STUDENT_NAME")  ?: ""
        rollNumber   = intent.getStringExtra("ROLL_NUMBER")   ?: ""
        classId      = intent.getStringExtra("CLASS_ID")      ?: ""
        currentMonth = intent.getStringExtra("CURRENT_MONTH")
            ?: SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        selectedMonth = currentMonth
    }

    private fun bindViews() {
        tvStudentName      = findViewById(R.id.tvStudentName)
        tvRollNo           = findViewById(R.id.tvRollNo)
        tvOverallPercent   = findViewById(R.id.tvOverallPercent)
        tvAttendanceStatus = findViewById(R.id.tvAttendanceStatus)
        tvDayStats         = findViewById(R.id.tvDayStats)
        circularProgress   = findViewById(R.id.circularProgress)
        spinnerSubject     = findViewById(R.id.spinnerSubjectAnalysis)
        chipGroupMonth     = findViewById(R.id.chipGroupMonth)
        barChart           = findViewById(R.id.barChartSubjects)
        lineChart          = findViewById(R.id.lineChartTrend)
        pieChart           = findViewById(R.id.pieChartBreakdown)
        cardRisk           = findViewById(R.id.cardRisk)
        tvRiskTitle        = findViewById(R.id.tvRiskTitle)
        tvRiskDetail       = findViewById(R.id.tvRiskDetail)
        cardShortfall      = findViewById(R.id.cardShortfall)
        tvShortfallTitle   = findViewById(R.id.tvShortfallTitle)
        tvShortfallDetail  = findViewById(R.id.tvShortfallDetail)
        cardAiFeedback     = findViewById(R.id.cardAiFeedback)
        tvAiFeedback       = findViewById(R.id.tvAiFeedback)
        progressBar        = findViewById(R.id.progressBarAnalysis)

        tvStudentName.text = studentName
        tvRollNo.text      = "Roll No: $rollNumber"
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            title = "Student Attendance Analysis"
            setDisplayHomeAsUpEnabled(true)
        }
    }

    private fun setupMonthChips() {
        chipGroupMonth.removeAllViews()
        val sdf   = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        val label = SimpleDateFormat("MMM yy",  Locale.getDefault())
        val cal   = Calendar.getInstance()

        repeat(6) {
            val key = sdf.format(cal.time)
            val chip = Chip(this).apply {
                text        = label.format(cal.time)
                isCheckable = true
                isChecked   = key == selectedMonth
                tag         = key
                setOnCheckedChangeListener { _, checked ->
                    if (checked) { selectedMonth = tag as String; loadAttendance() }
                }
            }
            chipGroupMonth.addView(chip)
            cal.add(Calendar.MONTH, -1)
        }
    }

    private fun loadSubjects() {
        db.collection("subjects")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                subjectList.clear()
                subjectList.addAll(snap.toObjects(Subject::class.java))

                val names = mutableListOf("All Subjects")
                names.addAll(subjectList.map { "${it.name} (${it.code})" })
                spinnerSubject.adapter =
                    ArrayAdapter(this, android.R.layout.simple_spinner_item, names)

                spinnerSubject.onItemSelectedListener =
                    object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                            selectedSubjectId = if (pos == 0) "" else subjectList[pos - 1].id
                            loadAttendance()
                        }
                        override fun onNothingSelected(p: AdapterView<*>?) {}
                    }
            }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FETCH — query session docs, scan students[] array for this student
    //
    // New structure: each doc in "attendance" = one session.
    // We query by classId + month (+ optional subjectId), then scan
    // each session's students[] array to find this student's record.
    // ─────────────────────────────────────────────────────────────────────────
    private fun loadAttendance() {
        showLoading(true)

        var query = db.collection("attendance")
            .whereEqualTo("classId", classId)
            .whereEqualTo("month",   selectedMonth)   // "2026-02"

        if (selectedSubjectId.isNotEmpty()) {
            query = query.whereEqualTo("subjectId", selectedSubjectId)
        }

        query.get()
            .addOnSuccessListener { sessionSnap ->
                var total   = 0
                var present = 0
                var late    = 0
                var absent  = 0

                sessionSnap.documents.forEach { sessionDoc ->
                    val studentsArray =
                        sessionDoc.get("students") as? List<Map<String, Any>> ?: return@forEach

                    // Find this student's record in the session
                    val studentRecord = studentsArray.firstOrNull { map ->
                        map["studentId"] as? String == studentId
                    } ?: return@forEach   // student not in this session

                    total++
                    when (studentRecord["status"] as? String) {
                        "present" -> present++
                        "late"    -> late++
                        else      -> absent++
                    }
                }

                processData(total, present, late, absent)
                showLoading(false)
            }
            .addOnFailureListener {
                showLoading(false)
                Toast.makeText(this, "Failed to load attendance", Toast.LENGTH_SHORT).show()
            }
    }

    private fun processData(total: Int, present: Int, late: Int, absent: Int) {
        if (total == 0) {
            updateOverall(0f, 0, 0, 0)
            barChart.clear()
            lineChart.clear()
            pieChart.clear()
            return
        }

        val percent = ((present + late).toFloat() / total) * 100f
        updateOverall(percent, present, absent, late)
        buildPie(present, absent, late)
    }

    private fun updateOverall(percent: Float, p: Int, a: Int, l: Int) {
        tvOverallPercent.text = "${percent.toInt()}%"
        circularProgress.progress = percent.toInt()
        tvDayStats.text = "Present: $p | Absent: $a | Late: $l"

        when {
            percent >= SAFE   -> {
                tvAttendanceStatus.text = "Safe"
                tvAttendanceStatus.setTextColor(Color.parseColor("#2E7D32"))
            }
            percent >= DANGER -> {
                tvAttendanceStatus.text = "At Risk"
                tvAttendanceStatus.setTextColor(Color.parseColor("#F57C00"))
            }
            else -> {
                tvAttendanceStatus.text = "Critical"
                tvAttendanceStatus.setTextColor(Color.parseColor("#C62828"))
            }
        }
    }

    private fun buildPie(p: Int, a: Int, l: Int) {
        val entries = mutableListOf<PieEntry>()
        if (p > 0) entries.add(PieEntry(p.toFloat(), "Present"))
        if (a > 0) entries.add(PieEntry(a.toFloat(), "Absent"))
        if (l > 0) entries.add(PieEntry(l.toFloat(), "Late"))

        val set = PieDataSet(entries, "").apply {
            colors = listOf(Color.GREEN, Color.RED, Color.YELLOW)
            valueFormatter = PercentFormatter(pieChart)
        }
        pieChart.data = PieData(set)
        pieChart.setUsePercentValues(true)
        pieChart.description.isEnabled = false
        pieChart.invalidate()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}