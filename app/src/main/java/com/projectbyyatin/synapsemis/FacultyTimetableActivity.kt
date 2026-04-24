package com.projectbyyatin.synapsemis

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Timetable
import com.projectbyyatin.synapsemis.models.TimetableSlot
import java.util.*

class FacultyTimetableActivity : AppCompatActivity() {

    companion object {
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

        // FIX 1: Moved getTodayName() into the single companion object
        // (was split into two companion objects causing "private companion object" error)
        fun getTodayName(): String {
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
            return days.getOrElse(Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - 2) { "Monday" }
        }
    }

    private val db = FirebaseFirestore.getInstance()

    private var facultyId   = ""
    private var facultyName = ""

    private lateinit var toolbar      : Toolbar
    private lateinit var tabLayout    : TabLayout
    private lateinit var progressBar  : ProgressBar
    private lateinit var layoutToday  : View
    private lateinit var tvTodayHeading: TextView
    private lateinit var rvToday       : RecyclerView
    private lateinit var tvEmptyToday  : TextView
    private lateinit var cardTodaySummary: MaterialCardView
    private lateinit var tvTodayCount  : TextView
    private lateinit var tvTodayHours  : TextView
    private lateinit var layoutWeekly  : View
    private lateinit var dayTabsWeekly : TabLayout
    private lateinit var rvWeekly      : RecyclerView
    private lateinit var tvEmptyWeekly : TextView
    private lateinit var layoutSummary : View
    private lateinit var rvSummary     : RecyclerView
    private lateinit var tvTotalWeekly : TextView
    private lateinit var tvTotalHours  : TextView
    private lateinit var tvSubjectCount: TextView

    private val allTimetables    = mutableListOf<Timetable>()
    // FIX 2: currentWeeklyDay init was calling getTodayName() before companion object
    // resolution at field init time — now safe because it's in the companion object
    private var currentWeeklyDay = ""

    private lateinit var todayAdapter  : FacultySlotAdapter
    private lateinit var weeklyAdapter : FacultySlotAdapter
    private lateinit var summaryAdapter: FacultySummaryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_faculty_timetable)

        // FIX 2 (continued): initialize after companion is definitely available
        currentWeeklyDay = getTodayName()

        facultyId   = intent.getStringExtra("FACULTY_ID")   ?: ""
        facultyName = intent.getStringExtra("FACULTY_NAME") ?: ""

        bindViews()
        setupToolbar()
        setupMainTabs()
        setupWeeklyDayTabs()
        setupRecyclers()
        loadTimetables()
    }

    private fun bindViews() {
        toolbar          = findViewById(R.id.toolbar)
        tabLayout        = findViewById(R.id.tab_layout)
        progressBar      = findViewById(R.id.progress_bar)
        layoutToday      = findViewById(R.id.layout_tab_today)
        tvTodayHeading   = findViewById(R.id.tv_today_heading)
        rvToday          = findViewById(R.id.rv_today)
        tvEmptyToday     = findViewById(R.id.tv_empty_today)
        cardTodaySummary = findViewById(R.id.card_today_summary)
        tvTodayCount     = findViewById(R.id.tv_today_count)
        tvTodayHours     = findViewById(R.id.tv_today_hours)
        layoutWeekly     = findViewById(R.id.layout_tab_weekly)
        dayTabsWeekly    = findViewById(R.id.day_tabs_weekly)
        rvWeekly         = findViewById(R.id.rv_weekly)
        tvEmptyWeekly    = findViewById(R.id.tv_empty_weekly)
        layoutSummary    = findViewById(R.id.layout_tab_summary)
        rvSummary        = findViewById(R.id.rv_summary)
        tvTotalWeekly    = findViewById(R.id.tv_total_weekly)
        tvTotalHours     = findViewById(R.id.tv_total_hours)
        tvSubjectCount   = findViewById(R.id.tv_subject_count)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title    = "My Timetable"
            subtitle = facultyName
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupMainTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("📅 Today"))
        tabLayout.addTab(tabLayout.newTab().setText("📋 Weekly"))
        tabLayout.addTab(tabLayout.newTab().setText("📊 Summary"))
        showMainTab(0)
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) = showMainTab(t.position)
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun showMainTab(pos: Int) {
        layoutToday.visibility   = if (pos == 0) View.VISIBLE else View.GONE
        layoutWeekly.visibility  = if (pos == 1) View.VISIBLE else View.GONE
        layoutSummary.visibility = if (pos == 2) View.VISIBLE else View.GONE
    }

    private fun setupWeeklyDayTabs() {
        val today = getTodayName()
        DAYS.forEach { day ->
            // FIX 3: tab.text= assignment on a freshly built tab works fine;
            // the original code chained .setText() which returns TabLayout.Tab (OK),
            // but then reassigned .text which is a CharSequence? property — both work,
            // keeping the safer property-assignment style
            val tab = dayTabsWeekly.newTab()
            tab.text = if (day == today) "${day.take(3)} ●" else day.take(3)
            dayTabsWeekly.addTab(tab)
        }
        dayTabsWeekly.getTabAt(DAYS.indexOf(today).coerceAtLeast(0))?.select()

        dayTabsWeekly.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(t: TabLayout.Tab) {
                currentWeeklyDay = DAYS[t.position]
                refreshWeeklyDay()
            }
            override fun onTabUnselected(t: TabLayout.Tab) {}
            override fun onTabReselected(t: TabLayout.Tab) {}
        })
    }

    private fun setupRecyclers() {
        todayAdapter = FacultySlotAdapter(emptyList())
        rvToday.layoutManager = LinearLayoutManager(this)
        rvToday.adapter = todayAdapter
        rvToday.isNestedScrollingEnabled = false

        weeklyAdapter = FacultySlotAdapter(emptyList())
        rvWeekly.layoutManager = LinearLayoutManager(this)
        rvWeekly.adapter = weeklyAdapter
        rvWeekly.isNestedScrollingEnabled = false

        summaryAdapter = FacultySummaryAdapter(emptyList())
        rvSummary.layoutManager = LinearLayoutManager(this)
        rvSummary.adapter = summaryAdapter
        rvSummary.isNestedScrollingEnabled = false
    }

    private fun loadTimetables() {
        progressBar.visibility = View.VISIBLE
        // Firestore document uses "active" (not "isActive") — query both to be safe
        db.collection("timetables")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snap ->
                progressBar.visibility = View.GONE
                allTimetables.clear()
                allTimetables.addAll(snap.documents.mapNotNull { doc ->
                    doc.toObject(Timetable::class.java)?.copy(id = doc.id)
                })
                populateToday()
                refreshWeeklyDay()
                populateSummary()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load timetable", Toast.LENGTH_SHORT).show()
            }
    }

    private fun populateToday() {
        val today = getTodayName()
        tvTodayHeading.text = "Today — $today"
        val slots = getSlotsForDay(today)
        todayAdapter.updateSlots(slots)
        if (slots.isEmpty()) {
            tvEmptyToday.visibility     = View.VISIBLE
            rvToday.visibility          = View.GONE
            cardTodaySummary.visibility = View.GONE
        } else {
            tvEmptyToday.visibility     = View.GONE
            rvToday.visibility          = View.VISIBLE
            cardTodaySummary.visibility = View.VISIBLE
            val totalMins = slots.sumOf { it.slot.durationMinutes }
            tvTodayCount.text = "${slots.size} lecture${if (slots.size != 1) "s" else ""}"
            tvTodayHours.text = "${totalMins / 60}h ${totalMins % 60}m teaching"
        }
    }

    private fun refreshWeeklyDay() {
        val slots = getSlotsForDay(currentWeeklyDay)
        weeklyAdapter.updateSlots(slots)
        tvEmptyWeekly.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        rvWeekly.visibility      = if (slots.isEmpty()) View.GONE    else View.VISIBLE
    }

    private fun populateSummary() {
        data class SubjectItem(
            val subjectName: String,
            val subjectCode: String,
            val classes    : MutableSet<String> = mutableSetOf(),
            var totalSlots : Int = 0,
            var totalMins  : Int = 0
        )

        val subjectMap      = mutableMapOf<String, SubjectItem>()
        var grandTotalSlots = 0
        var grandTotalMins  = 0

        allTimetables.forEach { tt ->
            DAYS.forEach { day ->
                (tt.schedule[day] ?: emptyList())
                    .filter { it.slotType == "lecture" && it.facultyId == facultyId }
                    .forEach { slot ->
                        val item = subjectMap.getOrPut(slot.subjectId) {
                            SubjectItem(slot.subjectName, slot.subjectCode)
                        }
                        item.classes.add(tt.className)
                        item.totalSlots++
                        item.totalMins += slot.durationMinutes
                        grandTotalSlots++
                        grandTotalMins += slot.durationMinutes
                    }
            }
        }

        summaryAdapter.updateItems(
            subjectMap.values.sortedBy { it.subjectName }.map {
                FacultySummaryRow(
                    it.subjectName, it.subjectCode,
                    it.classes.joinToString(", "),
                    it.totalSlots, it.totalMins
                )
            }
        )
        tvTotalWeekly.text  = "$grandTotalSlots lectures/week"
        tvTotalHours.text   = "${grandTotalMins / 60}h ${grandTotalMins % 60}m/week"
        tvSubjectCount.text = "${subjectMap.size} subject${if (subjectMap.size != 1) "s" else ""}"
    }

    // ── Inner data class accessible from adapter ───────────────────────────────
    data class FacultySlotItem(
        val className : String,
        val slot      : TimetableSlot
    )

    private fun getSlotsForDay(day: String): List<FacultySlotItem> {
        val result = mutableListOf<FacultySlotItem>()
        allTimetables.forEach { tt ->
            (tt.schedule[day] ?: emptyList())
                .filter { it.slotType == "lecture" && it.facultyId == facultyId }
                .forEach { slot -> result.add(FacultySlotItem(tt.className, slot)) }
        }
        return result.sortedBy { it.slot.startTimeMinutes }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter: Faculty slot card
// ─────────────────────────────────────────────────────────────────────────────
class FacultySlotAdapter(
    private var items: List<FacultyTimetableActivity.FacultySlotItem>
) : RecyclerView.Adapter<FacultySlotAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card      : MaterialCardView = v.findViewById(R.id.cardFacultySlot)
        val tvTime    : TextView         = v.findViewById(R.id.tvFacultySlotTime)
        val tvSubject : TextView         = v.findViewById(R.id.tvFacultySlotSubject)
        val tvClass   : TextView         = v.findViewById(R.id.tvFacultySlotClass)
        val tvRoom    : TextView         = v.findViewById(R.id.tvFacultySlotRoom)
        val tvType    : TextView         = v.findViewById(R.id.tvFacultySlotType)
        val tvDuration: TextView         = v.findViewById(R.id.tvFacultySlotDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_faculty_slot, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        val slot = item.slot

        h.tvTime.text     = "${fmt(slot.startTimeMinutes)} – ${fmt(slot.endTimeMinutes)}"
        h.tvSubject.text  = "${slot.subjectName} (${slot.subjectCode})"
        h.tvClass.text    = "🏫 ${item.className}"
        h.tvRoom.text     = if (slot.room.isNotEmpty()) "🚪 Room ${slot.room}" else "Room TBD"
        h.tvDuration.text = "${slot.durationMinutes} min"

        val (typeLabel, typeColor) = when (slot.lectureType) {
            "practical" -> "LAB"    to 0xFF6C63FF.toInt()
            else        -> "THEORY" to 0xFF4FACFE.toInt()
        }
        h.tvType.text = typeLabel
        h.tvType.setTextColor(typeColor)
        h.card.strokeColor = typeColor
        h.card.strokeWidth = 2
    }

    fun updateSlots(newItems: List<FacultyTimetableActivity.FacultySlotItem>) {
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
// Data class + Adapter: Faculty Summary
// ─────────────────────────────────────────────────────────────────────────────
data class FacultySummaryRow(
    val subjectName  : String,
    val subjectCode  : String,
    val classesStr   : String,
    val slotsPerWeek : Int,
    val minsPerWeek  : Int
)

class FacultySummaryAdapter(
    private var items: List<FacultySummaryRow>
) : RecyclerView.Adapter<FacultySummaryAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSubject : TextView = v.findViewById(R.id.tvSummarySubject)
        val tvClasses : TextView = v.findViewById(R.id.tvSummaryClasses)
        val tvSlots   : TextView = v.findViewById(R.id.tvSummarySlots)
        val tvHours   : TextView = v.findViewById(R.id.tvSummaryHours)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        LayoutInflater.from(parent.context).inflate(R.layout.item_faculty_summary_row, parent, false)
    )

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvSubject.text = "${item.subjectName} (${item.subjectCode})"
        h.tvClasses.text = "Classes: ${item.classesStr}"
        h.tvSlots.text   = "${item.slotsPerWeek} slots/week"
        h.tvHours.text   = "${item.minsPerWeek / 60}h ${item.minsPerWeek % 60}m/week"
    }

    fun updateItems(newItems: List<FacultySummaryRow>) {
        items = newItems
        notifyDataSetChanged()
    }
}