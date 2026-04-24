package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.views.AttendanceBarChartView
import com.projectbyyatin.synapsemis.views.AttendancePieChartView
import com.projectbyyatin.synapsemis.views.AttendanceSparklineView
import java.text.SimpleDateFormat
import java.util.*

class StudentAttendanceAnalyticsActivity : AppCompatActivity() {

    private val db         = FirebaseFirestore.getInstance()
    private val isoSdf     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displaySdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    // Toolbar / filter
    private lateinit var toolbar     : Toolbar
    private lateinit var etStartDate : TextInputEditText
    private lateinit var etEndDate   : TextInputEditText
    private lateinit var btnApply    : MaterialButton

    // Summary
    private lateinit var tvTotalStudents : TextView
    private lateinit var tvClassAvg      : TextView
    private lateinit var tvAtRisk        : TextView
    private lateinit var tvAiInsight     : TextView

    // Custom charts
    private lateinit var barChart : AttendanceBarChartView
    private lateinit var pieChart : AttendancePieChartView

    // List
    private lateinit var recyclerView   : RecyclerView
    private lateinit var loadingProgress: LinearProgressIndicator
    private lateinit var emptyView      : TextView

    private val barWidthDp = 32
    private val barGapDp = 16
    private val labelExtraSpaceDp = 24

    // Intent
    private var classId   = ""
    private var className = ""
    private var subjectId = ""
    private var startDate = ""
    private var endDate   = ""

    data class StudentSummary(
        val studentId   : String,
        val studentName : String,
        val rollNo      : String,
        val total       : Int,
        val present     : Int,
        val percentage  : Double,
        val risk        : String,
        val trend       : String,
        val monthlyPcts : List<Float>
    )

    private val summaryList = mutableListOf<StudentSummary>()
    private val dateAggMap  = mutableMapOf<String, Pair<Int, Int>>()  // date → (present, total)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_attendance_analytics)

        classId   = intent.getStringExtra("CLASS_ID")   ?: ""
        className = intent.getStringExtra("CLASS_NAME") ?: ""
        subjectId = intent.getStringExtra("SUBJECT_ID") ?: ""

        bindViews()
        setupToolbar()

        val cal = Calendar.getInstance()
        endDate = isoSdf.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        startDate = isoSdf.format(cal.time)
        etStartDate.setText(displaySdf.format(isoSdf.parse(startDate)!!))
        etEndDate.setText(displaySdf.format(isoSdf.parse(endDate)!!))

        setupDatePickers()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.isNestedScrollingEnabled = false

        loadAnalytics()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun bindViews() {
        toolbar            = findViewById(R.id.toolbar)
        etStartDate        = findViewById(R.id.etAnalyticsStartDate)
        etEndDate          = findViewById(R.id.etAnalyticsEndDate)
        btnApply           = findViewById(R.id.btnAnalyticsApply)
        tvTotalStudents    = findViewById(R.id.tv_total_students)
        tvClassAvg         = findViewById(R.id.tv_class_avg)
        tvAtRisk           = findViewById(R.id.tv_at_risk)
        tvAiInsight        = findViewById(R.id.tv_ai_insight)
        barChart           = findViewById(R.id.barChart)
        pieChart           = findViewById(R.id.pieChart)
        recyclerView       = findViewById(R.id.recycler_view)
        loadingProgress    = findViewById(R.id.loading_progress)
        emptyView          = findViewById(R.id.empty_view)
        btnApply.setOnClickListener { loadAnalytics() }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply { setDisplayHomeAsUpEnabled(true); title = "Student Analytics"; subtitle = className }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDatePickers() {
        etStartDate.setOnClickListener { pickDate(true) }
        etEndDate.setOnClickListener   { pickDate(false) }
    }

    private fun pickDate(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            val raw = isoSdf.format(cal.time)
            if (isStart) { startDate = raw; etStartDate.setText(displaySdf.format(cal.time)) }
            else         { endDate   = raw; etEndDate.setText(displaySdf.format(cal.time)) }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadAnalytics() {
        if (startDate > endDate) { toast("Start date must be before end date"); return }
        showLoading(true)
        summaryList.clear()
        dateAggMap.clear()

        var query = db.collection("attendance")
            .whereEqualTo("classId", classId)
            .whereGreaterThanOrEqualTo("date", startDate)
            .whereLessThanOrEqualTo("date", endDate)

        if (subjectId.isNotEmpty()) query = query.whereEqualTo("subjectId", subjectId)

        query.get()
            .addOnSuccessListener { sessionSnap ->
                data class RawRecord(val date: String, val month: String, val status: String)
                data class StudentRaw(val name: String, val roll: String, val records: MutableList<RawRecord> = mutableListOf())

                val studentRawMap = mutableMapOf<String, StudentRaw>()

                sessionSnap.documents.forEach { sessionDoc ->
                    val date          = sessionDoc.getString("date") ?: return@forEach
                    val studentsArray = sessionDoc.get("students") as? List<Map<String, Any>> ?: return@forEach
                    var sessionPresent = 0

                    studentsArray.forEach { sMap ->
                        val sid    = sMap["studentId"]     as? String ?: return@forEach
                        val sName  = sMap["studentName"]   as? String ?: ""
                        val sRoll  = sMap["studentRollNo"] as? String ?: ""
                        val status = sMap["status"]        as? String ?: "absent"
                        val month  = date.substring(0, 7)

                        studentRawMap.getOrPut(sid) { StudentRaw(sName, sRoll) }
                            .records.add(RawRecord(date, month, status))

                        if (status == "present" || status == "late") sessionPresent++
                    }

                    val prev = dateAggMap[date] ?: Pair(0, 0)
                    dateAggMap[date] = Pair(prev.first + sessionPresent, prev.second + studentsArray.size)
                }

                summaryList.clear()
                studentRawMap.forEach { (sid, raw) ->
                    val total   = raw.records.size
                    val present = raw.records.count { it.status == "present" || it.status == "late" }
                    val pct     = if (total > 0) present.toDouble() / total * 100.0 else 0.0

                    // Monthly % for sparkline
                    val monthlyMap = mutableMapOf<String, Pair<Int, Int>>()
                    raw.records.forEach { rec ->
                        val prev = monthlyMap[rec.month] ?: Pair(0, 0)
                        val isP  = rec.status == "present" || rec.status == "late"
                        monthlyMap[rec.month] = Pair(prev.first + if (isP) 1 else 0, prev.second + 1)
                    }
                    val monthlyPcts = monthlyMap.keys.sorted().map { m ->
                        val (p, t) = monthlyMap[m]!!
                        if (t > 0) p.toFloat() / t * 100f else 0f
                    }

                    // Trend
                    val sorted     = raw.records.sortedBy { it.date }
                    val half       = sorted.size / 2
                    val firstPct   = if (half == 0) pct else
                        sorted.take(half).count { it.status == "present" || it.status == "late" }.toDouble() / half * 100
                    val secondPct  = if (sorted.size - half == 0) pct else
                        sorted.drop(half).count { it.status == "present" || it.status == "late" }.toDouble() / (sorted.size - half) * 100
                    val trend = when {
                        secondPct - firstPct > 5.0 -> "improving"
                        firstPct - secondPct > 5.0 -> "declining"
                        else -> "stable"
                    }
                    val risk = when { pct < 60.0 -> "high"; pct < 75.0 -> "medium"; else -> "low" }

                    summaryList.add(StudentSummary(sid, raw.name, raw.roll, total, present, pct, risk, trend, monthlyPcts))
                }

                summaryList.sortWith(compareBy(
                    { when (it.risk) { "high" -> 0; "medium" -> 1; else -> 2 } },
                    { it.rollNo }
                ))

                showLoading(false)
                updateSummaryCard()
                drawBarChart()
                drawPieChart()
                recyclerView.adapter = StudentAnalyticsAdapter(summaryList)
                emptyView.visibility    = if (summaryList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (summaryList.isEmpty()) View.GONE    else View.VISIBLE
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("Error: ${e.message}")
                emptyView.visibility = View.VISIBLE
            }
    }

    private fun updateSummaryCard() {
        val atRisk = summaryList.count { it.risk == "high" || it.risk == "medium" } 
        val classAvg = if (summaryList.isNotEmpty()) summaryList.map { it.percentage }.average() else 0.0
        tvTotalStudents.text = summaryList.size.toString()
        tvAtRisk.text        = atRisk.toString()
        tvClassAvg.text      = "%.1f%%".format(classAvg)
        val declining = summaryList.count { it.trend == "declining" }
        val improving = summaryList.count { it.trend == "improving" }
        tvAiInsight.text = buildString {
            if (classAvg < 75) append("⚠️ Class avg ${classAvg.toInt()}% below 75%. ")
            else                append("✅ Class avg ${classAvg.toInt()}% on track. ")
            if (atRisk   > 0)  append("$atRisk critically at risk. ")
            if (declining > 0) append("$declining declining — intervene early. ")
            if (improving > 0) append("$improving improving. ")
        }
    }

    private fun drawBarChart() {
        if (dateAggMap.isEmpty()) { barChart.visibility = View.GONE; return }
        barChart.visibility = View.VISIBLE
        val fmt = SimpleDateFormat("dd MMM", Locale.getDefault())
        val data = dateAggMap.keys.sorted().associate { date ->
            val label = try { fmt.format(isoSdf.parse(date)!!) } catch (e: Exception) { date }
            val (p, t) = dateAggMap[date]!!
            label to (if (t > 0) p.toFloat() / t * 100f else 0f)
        }
        barChart.setData(data)
    }

    private fun drawPieChart() {
        if (summaryList.isEmpty()) { pieChart.visibility = View.GONE; return }
        pieChart.visibility = View.VISIBLE
        val totalPresent = summaryList.sumOf { it.present }.toFloat()
        val totalAbsent  = summaryList.sumOf { it.total - it.present }.toFloat()
        pieChart.setData(listOf("Present" to totalPresent, "Absent" to totalAbsent))
    }

    private fun showLoading(show: Boolean) {
        loadingProgress.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ─── Adapter ─────────────────────────────────────────────────────────────────

class StudentAnalyticsAdapter(
    private val items: List<StudentAttendanceAnalyticsActivity.StudentSummary>
) : RecyclerView.Adapter<StudentAnalyticsAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card       : MaterialCardView        = v.findViewById(R.id.cardStudentAnalytics)
        val tvName     : TextView                = v.findViewById(R.id.tvAnalyticsName)
        val tvRoll     : TextView                = v.findViewById(R.id.tvAnalyticsRoll)
        val tvPct      : TextView                = v.findViewById(R.id.tvAnalyticsPct)
        val tvStatus   : TextView                = v.findViewById(R.id.tvAnalyticsStatus)
        val tvTrend    : TextView                = v.findViewById(R.id.tvAnalyticsTrend)
        val tvPresent  : TextView                = v.findViewById(R.id.tvAnalyticsPresent)
        val progressBar: LinearProgressIndicator = v.findViewById(R.id.progressAnalytics)
        val sparkline  : AttendanceSparklineView = v.findViewById(R.id.sparklineView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_student_analytics, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvName.text    = item.studentName
        h.tvRoll.text    = "Roll: ${item.rollNo}"
        h.tvPct.text     = "${"%.1f".format(item.percentage)}%"
        h.tvPresent.text = "${item.present}/${item.total} classes attended"
        h.progressBar.progress = item.percentage.toInt()

        val (riskLabel, riskColor) = when (item.risk) {
            "high"   -> "🔴 HIGH RISK" to Color.parseColor("#FF5252")
            "medium" -> "🟡 AT RISK"   to Color.parseColor("#FFB300")
            else     -> "🟢 SAFE"      to Color.parseColor("#00C853")
        }
        h.tvStatus.text = riskLabel
        h.tvStatus.setTextColor(riskColor)
        h.tvPct.setTextColor(riskColor)
        h.progressBar.setIndicatorColor(riskColor)
        h.card.strokeColor = riskColor
        h.card.strokeWidth = 2

        h.tvTrend.text = when (item.trend) {
            "improving" -> "📈 Improving"
            "declining" -> "📉 Declining"
            else        -> "➡️ Stable"
        }

        if (item.monthlyPcts.size >= 2) {
            h.sparkline.visibility = View.VISIBLE
            h.sparkline.setData(item.monthlyPcts)
        } else {
            h.sparkline.visibility = View.GONE
        }
    }
}