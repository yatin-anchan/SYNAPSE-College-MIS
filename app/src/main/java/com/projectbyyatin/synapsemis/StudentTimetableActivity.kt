package com.projectbyyatin.synapsemis

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Timetable
import com.projectbyyatin.synapsemis.models.TimetableSlot
import java.text.SimpleDateFormat
import java.util.*

class StudentTimetableActivity : AppCompatActivity() {

    companion object {
        // FIX 1: Consolidated into a single companion object.
        // Original had TWO companion objects (one private, one public) which is illegal in Kotlin.
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
        private val ISO  = SimpleDateFormat("yyyy-MM", Locale.getDefault())
        private const val DEFAULT_THRESHOLD = 75f

        fun getTodayName(): String {
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            return days.getOrElse(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) { "Monday" }
        }
    }

    private val db = FirebaseFirestore.getInstance()

    private var studentId   = ""
    private var studentName = ""
    private var classId     = ""
    private var className   = ""
    private var semester    = 1
    private var threshold   = DEFAULT_THRESHOLD

    private lateinit var toolbar        : Toolbar
    private lateinit var tabLayout      : TabLayout
    private lateinit var progressBar    : ProgressBar
    private lateinit var layoutToday    : View
    private lateinit var tvTodayLabel   : TextView
    private lateinit var rvToday        : RecyclerView
    private lateinit var tvEmptyToday   : TextView
    private lateinit var layoutWeekly   : View
    private lateinit var dayTabs        : TabLayout
    private lateinit var rvWeekly       : RecyclerView
    private lateinit var tvEmptyWeekly  : TextView
    private lateinit var layoutDanger   : View
    private lateinit var rvDanger       : RecyclerView
    private lateinit var tvEmptyDanger  : TextView
    private lateinit var cardOverall    : MaterialCardView
    private lateinit var tvOverallPct   : TextView
    private lateinit var tvOverallStatus: TextView
    private lateinit var progressOverall: LinearProgressIndicator
    private lateinit var tvDangerHint   : TextView

    private var timetable: Timetable? = null
    private val attendanceMap = mutableMapOf<String, SubjectAttendance>()

    // FIX 2: Don't call getTodayName() at field-init time.
    // Initialize in onCreate() instead to avoid any edge-case init ordering.
    private var currentWeeklyDay = ""

    private lateinit var todayAdapter  : StudentSlotAdapter
    private lateinit var weeklyAdapter : StudentSlotAdapter
    private lateinit var dangerAdapter : AttendanceDangerAdapter

    // ── Models ────────────────────────────────────────────────────────────────
    data class SubjectAttendance(
        val subjectId   : String,
        val subjectName : String,
        val subjectCode : String,
        var total       : Int = 0,
        var present     : Int = 0
    ) {
        // -1f means no data yet
        val percentage: Float
            get() = if (total > 0) present.toFloat() / total * 100f else -1f

        fun lecturesNeeded(target: Float): Int {
            if (percentage >= target) return 0
            if (total == 0) return 0
            var x = 0
            while (x < 500) {
                val newPct = (present + x).toFloat() / (total + x) * 100f
                if (newPct >= target) return x
                x++
            }
            return -1
        }

        fun canSkip(target: Float): Int {
            if (percentage < target) return 0
            var x = 0
            while (x < 500) {
                val newPct = present.toFloat() / (total + x + 1) * 100f
                if (newPct < target) return x
                x++
            }
            return x
        }

        fun warningLevel(target: Float): String = when {
            percentage == -1f    -> "no_data"
            percentage < 60f     -> "critical"
            percentage < target  -> "warning"
            canSkip(target) <= 2 -> "caution"
            else                 -> "safe"
        }
    }

    data class StudentSlotItem(
        val slot      : TimetableSlot,
        val attendance: SubjectAttendance?
    )

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_timetable)

        // FIX 2 (continued): safe to call here
        currentWeeklyDay = getTodayName()

        studentId   = intent.getStringExtra("STUDENT_ID")   ?: ""
        studentName = intent.getStringExtra("STUDENT_NAME") ?: ""
        classId     = intent.getStringExtra("CLASS_ID")     ?: ""
        className   = intent.getStringExtra("CLASS_NAME")   ?: ""
        semester    = intent.getIntExtra("SEMESTER", 1)
        threshold   = intent.getFloatExtra("THRESHOLD", DEFAULT_THRESHOLD)

        bindViews()
        setupToolbar()
        setupMainTabs()
        setupWeeklyDayTabs()
        setupRecyclers()
        loadData()
    }

    private fun bindViews() {
        toolbar         = findViewById(R.id.toolbar)
        tabLayout       = findViewById(R.id.tab_layout)
        progressBar     = findViewById(R.id.progress_bar)
        layoutToday     = findViewById(R.id.layout_tab_today)
        tvTodayLabel    = findViewById(R.id.tv_today_label)
        rvToday         = findViewById(R.id.rv_today)
        tvEmptyToday    = findViewById(R.id.tv_empty_today)
        layoutWeekly    = findViewById(R.id.layout_tab_weekly)
        dayTabs         = findViewById(R.id.day_tabs_weekly)
        rvWeekly        = findViewById(R.id.rv_weekly)
        tvEmptyWeekly   = findViewById(R.id.tv_empty_weekly)
        layoutDanger    = findViewById(R.id.layout_tab_danger)
        rvDanger        = findViewById(R.id.rv_danger)
        tvEmptyDanger   = findViewById(R.id.tv_empty_danger)
        cardOverall     = findViewById(R.id.card_overall)
        tvOverallPct    = findViewById(R.id.tv_overall_pct)
        tvOverallStatus = findViewById(R.id.tv_overall_status)
        progressOverall = findViewById(R.id.progress_overall)
        tvDangerHint    = findViewById(R.id.tv_danger_hint)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title    = "My Timetable"
            subtitle = className
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupMainTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("📅 Today"))
        tabLayout.addTab(tabLayout.newTab().setText("📋 Weekly"))
        tabLayout.addTab(tabLayout.newTab().setText("⚠️ Attendance"))
        showMainTab(0)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) = showMainTab(t.position)
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun showMainTab(pos: Int) {
        layoutToday.visibility  = if (pos == 0) View.VISIBLE else View.GONE
        layoutWeekly.visibility = if (pos == 1) View.VISIBLE else View.GONE
        layoutDanger.visibility = if (pos == 2) View.VISIBLE else View.GONE
    }

    private fun setupWeeklyDayTabs() {
        val today = getTodayName()
        DAYS.forEach { day ->
            val tab = dayTabs.newTab()
            tab.text = if (day == today) "${day.take(3)} ●" else day.take(3)
            dayTabs.addTab(tab)
        }
        dayTabs.getTabAt(DAYS.indexOf(today).coerceAtLeast(0))?.select()
        dayTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                currentWeeklyDay = DAYS[t.position]
                refreshWeekly()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclers() {
        todayAdapter = StudentSlotAdapter(emptyList(), threshold)
        rvToday.layoutManager = LinearLayoutManager(this)
        rvToday.adapter = todayAdapter
        rvToday.isNestedScrollingEnabled = false

        weeklyAdapter = StudentSlotAdapter(emptyList(), threshold)
        rvWeekly.layoutManager = LinearLayoutManager(this)
        rvWeekly.adapter = weeklyAdapter
        rvWeekly.isNestedScrollingEnabled = false

        dangerAdapter = AttendanceDangerAdapter(emptyList(), threshold)
        rvDanger.layoutManager = LinearLayoutManager(this)
        rvDanger.adapter = dangerAdapter
        rvDanger.isNestedScrollingEnabled = false
    }

    // ── Load ──────────────────────────────────────────────────────────────────
    private fun loadData() {
        progressBar.visibility = View.VISIBLE
        db.collection("timetables")
            .whereEqualTo("classId", classId)
            .get()
            .addOnSuccessListener { snap ->
                timetable = snap.documents.firstOrNull()
                    ?.let { it.toObject(Timetable::class.java)?.copy(id = it.id) }
                loadAttendance()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load timetable", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadAttendance() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)
        val sixMonthsAgo = "${ISO.format(cal.time)}-01"

        db.collection("attendance")
            .whereEqualTo("classId", classId)
            .whereGreaterThanOrEqualTo("date", sixMonthsAgo)
            .get()
            .addOnSuccessListener { sessionSnap ->
                attendanceMap.clear()
                sessionSnap.documents.forEach { sessionDoc ->
                    val studentsArray = sessionDoc.get("students") as? List<Map<String, Any>>
                        ?: return@forEach
                    studentsArray.forEach inner@{ sMap ->
                        if ((sMap["studentId"] as? String) != studentId) return@inner
                        val subjectId   = sMap["subjectId"]   as? String ?: return@inner
                        val subjectName = sMap["subjectName"] as? String ?: ""
                        val subjectCode = sMap["subjectCode"] as? String ?: ""
                        val status      = sMap["status"]      as? String ?: "absent"
                        val agg = attendanceMap.getOrPut(subjectId) {
                            SubjectAttendance(subjectId, subjectName, subjectCode)
                        }
                        agg.total++
                        if (status == "present" || status == "late") agg.present++
                    }
                }
                progressBar.visibility = View.GONE
                populateToday()
                refreshWeekly()
                populateDangerBoard()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                populateToday()
                refreshWeekly()
                populateDangerBoard()
            }
    }

    // ── Today ─────────────────────────────────────────────────────────────────
    private fun populateToday() {
        val today = getTodayName()
        tvTodayLabel.text = "Today — $today"
        val slots = getSlotsForDay(today)
        todayAdapter.updateSlots(slots)
        tvEmptyToday.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        rvToday.visibility      = if (slots.isEmpty()) View.GONE    else View.VISIBLE
    }

    // ── Weekly ────────────────────────────────────────────────────────────────
    private fun refreshWeekly() {
        val slots = getSlotsForDay(currentWeeklyDay)
        weeklyAdapter.updateSlots(slots)
        tvEmptyWeekly.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        rvWeekly.visibility      = if (slots.isEmpty()) View.GONE    else View.VISIBLE
    }

    private fun getSlotsForDay(day: String): List<StudentSlotItem> {
        val tt = timetable ?: return emptyList()
        return (tt.schedule[day] ?: emptyList())
            .filter { it.slotType == "lecture" }
            .sortedBy { it.startTimeMinutes }
            .map { slot -> StudentSlotItem(slot, attendanceMap[slot.subjectId]) }
    }

    // ── Danger Board ──────────────────────────────────────────────────────────
    private fun populateDangerBoard() {
        if (attendanceMap.isEmpty()) {
            tvEmptyDanger.visibility = View.VISIBLE
            rvDanger.visibility      = View.GONE
            cardOverall.visibility   = View.GONE
            return
        }

        val totalAll   = attendanceMap.values.sumOf { it.total }
        val presentAll = attendanceMap.values.sumOf { it.present }
        val overallPct = if (totalAll > 0) presentAll.toFloat() / totalAll * 100f else 0f

        cardOverall.visibility  = View.VISIBLE
        tvOverallPct.text       = "${"%.1f".format(overallPct)}%"
        progressOverall.progress = overallPct.toInt()

        val (statusText, statusColor) = when {
            overallPct < 60f       -> "🔴 CRITICAL" to Color.parseColor("#FF5252")
            overallPct < threshold -> "🟡 AT RISK"  to Color.parseColor("#FFB300")
            else                   -> "🟢 SAFE"     to Color.parseColor("#00C853")
        }
        tvOverallStatus.text = statusText
        tvOverallStatus.setTextColor(statusColor)
        progressOverall.setIndicatorColor(statusColor)

        val sorted = attendanceMap.values.sortedWith(
            compareBy(
                {
                    when (it.warningLevel(threshold)) {
                        "critical" -> 0; "warning" -> 1; "caution" -> 2; else -> 3
                    }
                },
                { it.percentage }
            )
        )

        dangerAdapter.updateItems(sorted)
        tvEmptyDanger.visibility = View.GONE
        rvDanger.visibility      = View.VISIBLE

        val criticalCount = sorted.count { it.warningLevel(threshold) == "critical" }
        val warningCount  = sorted.count { it.warningLevel(threshold) == "warning"  }
        tvDangerHint.text = when {
            criticalCount > 0 -> "⚠️ $criticalCount subject(s) critically low! You risk being placed on the defaulter list."
            warningCount  > 0 -> "📢 $warningCount subject(s) below ${threshold.toInt()}%. Attend upcoming lectures!"
            else              -> "✅ Your attendance is on track. Keep it up!"
        }
        tvDangerHint.setTextColor(when {
            criticalCount > 0 -> Color.parseColor("#FF5252")
            warningCount  > 0 -> Color.parseColor("#FFB300")
            else              -> Color.parseColor("#00C853")
        })
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter: Student slot card (Today + Weekly tabs)
// ─────────────────────────────────────────────────────────────────────────────
class StudentSlotAdapter(
    private var items    : List<StudentTimetableActivity.StudentSlotItem>,
    private val threshold: Float
) : RecyclerView.Adapter<StudentSlotAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card         : MaterialCardView         = v.findViewById(R.id.cardStudentSlot)
        val tvTime       : TextView                 = v.findViewById(R.id.tvSlotTime)
        val tvSubject    : TextView                 = v.findViewById(R.id.tvSlotSubject)
        val tvFaculty    : TextView                 = v.findViewById(R.id.tvSlotFaculty)
        val tvRoom       : TextView                 = v.findViewById(R.id.tvSlotRoom)
        val tvType       : TextView                 = v.findViewById(R.id.tvSlotType)
        val tvAttPct     : TextView                 = v.findViewById(R.id.tvSlotAttPct)
        val progressAtt  : LinearProgressIndicator  = v.findViewById(R.id.progressSlotAtt)
        val tvWarning    : TextView                 = v.findViewById(R.id.tvSlotWarning)
        val layoutWarning: View                     = v.findViewById(R.id.layoutSlotWarning)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_student_slot, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        val slot = item.slot
        val att  = item.attendance

        h.tvTime.text    = "${fmt(slot.startTimeMinutes)} – ${fmt(slot.endTimeMinutes)}"
        h.tvSubject.text = "${slot.subjectName} (${slot.subjectCode})"
        h.tvFaculty.text = if (slot.facultyName.isNotEmpty()) "👤 ${slot.facultyName}" else "Faculty TBA"
        h.tvRoom.text    = if (slot.room.isNotEmpty()) "🚪 Room ${slot.room}" else ""
        h.tvRoom.visibility = if (slot.room.isNotEmpty()) View.VISIBLE else View.GONE

        val (typeLabel, typeColor) = when (slot.lectureType) {
            "practical" -> "LAB"    to Color.parseColor("#6C63FF")
            else        -> "THEORY" to Color.parseColor("#4FACFE")
        }
        h.tvType.text = typeLabel
        h.tvType.setTextColor(typeColor)

        if (att == null || att.total == 0) {
            h.tvAttPct.text = "No data"
            h.tvAttPct.setTextColor(Color.parseColor("#88FFFFFF"))
            h.progressAtt.progress = 0
            h.progressAtt.setIndicatorColor(Color.parseColor("#555555"))
            h.layoutWarning.visibility = View.GONE
            h.card.strokeColor = Color.parseColor("#33FFFFFF")
            h.card.strokeWidth = 1
        } else {
            val pct   = att.percentage
            val level = att.warningLevel(threshold)

            val (pctColor, strokeColor, progressColor) = when (level) {
                "critical" -> Triple(Color.parseColor("#FF5252"), Color.parseColor("#FF5252"), Color.parseColor("#FF5252"))
                "warning"  -> Triple(Color.parseColor("#FFB300"), Color.parseColor("#FFB300"), Color.parseColor("#FFB300"))
                "caution"  -> Triple(Color.parseColor("#FFF176"), Color.parseColor("#FFF176"), Color.parseColor("#FFF176"))
                else       -> Triple(Color.parseColor("#00C853"), Color.parseColor("#1A3A1A"), Color.parseColor("#00C853"))
            }

            h.tvAttPct.text = "${"%.0f".format(pct)}%"
            h.tvAttPct.setTextColor(pctColor)
            h.progressAtt.progress = pct.toInt()
            h.progressAtt.setIndicatorColor(progressColor)
            h.card.strokeColor = strokeColor
            h.card.strokeWidth = if (level == "safe") 1 else 2

            val warningMsg = when (level) {
                "critical" -> {
                    val needed = att.lecturesNeeded(threshold)
                    if (needed > 0)
                        "🚨 ATTEND NEXT $needed LECTURES CONSECUTIVELY to avoid defaulter list!"
                    else
                        "🚨 Critically low attendance! Speak to your faculty immediately."
                }
                "warning" -> {
                    val needed = att.lecturesNeeded(threshold)
                    "⚠️ Below ${threshold.toInt()}%. Attend $needed more lectures to reach safe zone."
                }
                "caution" -> {
                    val canSkip = att.canSkip(threshold)
                    "📢 You can only afford to miss $canSkip more lecture(s) before falling below ${threshold.toInt()}%."
                }
                else -> ""
            }

            if (warningMsg.isNotEmpty()) {
                h.layoutWarning.visibility = View.VISIBLE
                h.tvWarning.text = warningMsg
                h.tvWarning.setTextColor(pctColor)
            } else {
                h.layoutWarning.visibility = View.GONE
            }
        }
    }

    fun updateSlots(newItems: List<StudentTimetableActivity.StudentSlotItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun fmt(mins: Int): String {
        val h    = mins / 60
        val m    = mins % 60
        val amPm = if (h < 12) "AM" else "PM"
        val dH   = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return "%d:%02d %s".format(dH, m, amPm)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter: Attendance Danger Board (Tab 2)
// ─────────────────────────────────────────────────────────────────────────────
class AttendanceDangerAdapter(
    private var items    : List<StudentTimetableActivity.SubjectAttendance>,
    private val threshold: Float
) : RecyclerView.Adapter<AttendanceDangerAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card     : MaterialCardView        = v.findViewById(R.id.cardDanger)
        val tvSubject: TextView                = v.findViewById(R.id.tvDangerSubject)
        val tvPct    : TextView                = v.findViewById(R.id.tvDangerPct)
        val tvStatus : TextView                = v.findViewById(R.id.tvDangerStatus)
        val tvPresent: TextView                = v.findViewById(R.id.tvDangerPresent)
        val progress : LinearProgressIndicator = v.findViewById(R.id.progressDanger)
        val tvAdvice : TextView                = v.findViewById(R.id.tvDangerAdvice)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_danger, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val att   = items[pos]
        val pct   = att.percentage
        val level = att.warningLevel(threshold)

        h.tvSubject.text = "${att.subjectName} (${att.subjectCode})"
        h.tvPresent.text = "${att.present}/${att.total} classes"

        if (pct == -1f) {
            h.tvPct.text    = "N/A"
            h.tvStatus.text = "No records"
            h.tvAdvice.text = "No attendance records found yet."
            h.progress.progress = 0
            h.card.strokeColor  = Color.parseColor("#33FFFFFF")
            h.card.strokeWidth  = 1
            return
        }

        h.tvPct.text        = "${"%.1f".format(pct)}%"
        h.progress.progress = pct.toInt()

        val (statusLabel, color) = when (level) {
            "critical" -> "🔴 DEFAULTER RISK" to Color.parseColor("#FF5252")
            "warning"  -> "🟡 AT RISK"         to Color.parseColor("#FFB300")
            "caution"  -> "🟠 CAUTION"         to Color.parseColor("#FF9800")
            else       -> "🟢 SAFE"            to Color.parseColor("#00C853")
        }
        h.tvStatus.text = statusLabel
        h.tvStatus.setTextColor(color)
        h.tvPct.setTextColor(color)
        h.progress.setIndicatorColor(color)
        h.card.strokeColor = color
        h.card.strokeWidth = if (level == "safe") 1 else 2

        h.tvAdvice.text = when (level) {
            "critical" -> {
                val needed = att.lecturesNeeded(threshold)
                buildString {
                    append("You currently have ${"%.1f".format(pct)}% attendance. ")
                    append("You MUST attend the next $needed lectures without missing any ")
                    append("to avoid being placed on the defaulter list.\n")
                    append("⚠️ Defaulters may not be allowed to sit for exams!")
                }
            }
            "warning" -> {
                val needed = att.lecturesNeeded(threshold)
                "You are below ${threshold.toInt()}%. Attend $needed more lectures to reach the safe zone. Do not miss any upcoming classes for this subject."
            }
            "caution" -> {
                val canSkip = att.canSkip(threshold)
                "You are above ${threshold.toInt()}% but close to the boundary. You can only miss $canSkip more lecture(s) before falling below the threshold."
            }
            else -> {
                val canSkip = att.canSkip(threshold)
                "Your attendance is healthy. You can afford to miss up to $canSkip lecture(s) and still stay above ${threshold.toInt()}%."
            }
        }
        h.tvAdvice.setTextColor(
            if (level == "safe") Color.parseColor("#AAFFFFFF") else color
        )
    }

    fun updateItems(newItems: List<StudentTimetableActivity.SubjectAttendance>) {
        items = newItems
        notifyDataSetChanged()
    }
}