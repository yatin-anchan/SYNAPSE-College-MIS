package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Timetable
import com.projectbyyatin.synapsemis.models.TimetableSlot
import java.util.*

class HodTimetableActivity : AppCompatActivity() {

    companion object {
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    }

    private val firestore = FirebaseFirestore.getInstance()

    // Extras
    private var departmentId = ""
    private var departmentName = ""

    // Views
    private lateinit var toolbar: Toolbar
    private lateinit var tabLayout: TabLayout
    private lateinit var progressBar: View

    // Tab 0 – My Department
    private lateinit var layoutTabDepartment: View
    private lateinit var recyclerTimetables: RecyclerView
    private lateinit var emptyDeptView: View

    // Tab 1 – Today's Classes
    private lateinit var layoutTabToday: View
    private lateinit var tvTodayLabel: TextView
    private lateinit var recyclerTodaySlots: RecyclerView
    private lateinit var emptyTodayView: View

    // Tab 2 – Faculty Load
    private lateinit var layoutTabFacultyLoad: View
    private lateinit var recyclerFacultyLoad: RecyclerView
    private lateinit var emptyFacultyView: View

    // Data
    private val allTimetables = mutableListOf<Timetable>()
    private lateinit var timetableListAdapter: HodTimetableListAdapter
    private lateinit var todaySlotsAdapter: HodTodaySlotsAdapter
    private lateinit var facultyLoadAdapter: FacultyLoadAdapter

    // Today's day name
    private val todayDayName: String get() {
        val cal = Calendar.getInstance()
        return DAYS.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 2) { "Monday" }
        // Calendar: Sun=1,Mon=2...Sat=7  →  index = dayOfWeek-2 (Mon=0..Sat=5)
    }

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_timetable)

        departmentId   = intent.getStringExtra("DEPARTMENT_ID")   ?: ""
        departmentName = intent.getStringExtra("DEPARTMENT_NAME") ?: ""

        bindViews()
        setupToolbar()
        setupTabs()
        setupRecyclers()
        loadTimetables()
    }

    // ── Bind ──────────────────────────────────────────────────────────────────
    private fun bindViews() {
        toolbar              = findViewById(R.id.toolbar)
        tabLayout            = findViewById(R.id.tab_layout)
        progressBar          = findViewById(R.id.progress_bar)

        layoutTabDepartment  = findViewById(R.id.layout_tab_department)
        recyclerTimetables   = findViewById(R.id.recycler_timetables)
        emptyDeptView        = findViewById(R.id.empty_dept_view)

        layoutTabToday       = findViewById(R.id.layout_tab_today)
        tvTodayLabel         = findViewById(R.id.tv_today_label)
        recyclerTodaySlots   = findViewById(R.id.recycler_today_slots)
        emptyTodayView       = findViewById(R.id.empty_today_view)

        layoutTabFacultyLoad = findViewById(R.id.layout_tab_faculty_load)
        recyclerFacultyLoad  = findViewById(R.id.recycler_faculty_load)
        emptyFacultyView     = findViewById(R.id.empty_faculty_view)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Timetable — $departmentName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("📋 Department"))
        tabLayout.addTab(tabLayout.newTab().setText("📅 Today"))
        tabLayout.addTab(tabLayout.newTab().setText("👤 Faculty Load"))

        showTab(0)

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = showTab(tab.position)
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun showTab(position: Int) {
        layoutTabDepartment.visibility  = if (position == 0) View.VISIBLE else View.GONE
        layoutTabToday.visibility       = if (position == 1) View.VISIBLE else View.GONE
        layoutTabFacultyLoad.visibility = if (position == 2) View.VISIBLE else View.GONE
    }

    private fun setupRecyclers() {
        // Tab 0
        timetableListAdapter = HodTimetableListAdapter(allTimetables) { timetable ->
            val intent = Intent(this, HodViewTimetableActivity::class.java)
            intent.putExtra(HodViewTimetableActivity.EXTRA_TIMETABLE_ID, timetable.id)
            intent.putExtra(HodViewTimetableActivity.EXTRA_INITIAL_DAY, todayDayName)
            startActivity(intent)
        }
        recyclerTimetables.layoutManager = LinearLayoutManager(this)
        recyclerTimetables.adapter = timetableListAdapter

        // Tab 1
        todaySlotsAdapter = HodTodaySlotsAdapter(emptyList())
        recyclerTodaySlots.layoutManager = LinearLayoutManager(this)
        recyclerTodaySlots.adapter = todaySlotsAdapter

        // Tab 2
        facultyLoadAdapter = FacultyLoadAdapter(emptyList())
        recyclerFacultyLoad.layoutManager = LinearLayoutManager(this)
        recyclerFacultyLoad.adapter = facultyLoadAdapter
    }

    // ── Load ──────────────────────────────────────────────────────────────────
    private fun loadTimetables() {
        progressBar.visibility = View.VISIBLE
        firestore.collection("timetables")
            .whereEqualTo("departmentId", departmentId)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snap ->
                progressBar.visibility = View.GONE
                allTimetables.clear()
                allTimetables.addAll(snap.documents.mapNotNull { doc ->
                    doc.toObject(Timetable::class.java)?.copy(id = doc.id)
                }.sortedBy { it.className })

                timetableListAdapter.notifyDataSetChanged()
                emptyDeptView.visibility = if (allTimetables.isEmpty()) View.VISIBLE else View.GONE
                recyclerTimetables.visibility = if (allTimetables.isEmpty()) View.GONE else View.VISIBLE

                populateTodayTab()
                populateFacultyLoadTab()
            }
            .addOnFailureListener {
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load timetables", Toast.LENGTH_SHORT).show()
            }
    }

    // ── Today tab ─────────────────────────────────────────────────────────────
    private fun populateTodayTab() {
        val dayName = todayDayName
        tvTodayLabel.text = "Today — $dayName"

        // Collect ALL slots from ALL classes for today, annotated with class name
        val todayItems = mutableListOf<TodaySlotItem>()
        allTimetables.forEach { timetable ->
            val slots = (timetable.schedule[dayName] ?: emptyList())
                .sortedBy { it.startTimeMinutes }
            slots.forEach { slot ->
                todayItems.add(TodaySlotItem(
                    className   = timetable.className,
                    courseName  = timetable.courseName,
                    semester    = timetable.semester,
                    slot        = slot
                ))
            }
        }
        // Sort all items by start time across classes
        todayItems.sortBy { it.slot.startTimeMinutes }

        todaySlotsAdapter.updateItems(todayItems)
        emptyTodayView.visibility = if (todayItems.isEmpty()) View.VISIBLE else View.GONE
        recyclerTodaySlots.visibility = if (todayItems.isEmpty()) View.GONE else View.VISIBLE
    }

    // ── Faculty Load tab ──────────────────────────────────────────────────────
    private fun populateFacultyLoadTab() {
        // Map: facultyName → Map<dayName, lectureCount>
        val facultyMap = mutableMapOf<String, FacultyLoadItem>()

        allTimetables.forEach { timetable ->
            DAYS.forEach { day ->
                val slots = timetable.schedule[day] ?: emptyList()
                slots.filter { it.slotType == "lecture" && it.facultyName.isNotEmpty() }
                    .forEach { slot ->
                        val existing = facultyMap.getOrPut(slot.facultyName) {
                            FacultyLoadItem(facultyName = slot.facultyName)
                        }
                        existing.dayCount[day] = (existing.dayCount[day] ?: 0) + 1
                        existing.totalLectures++
                    }
            }
        }

        val items = facultyMap.values.sortedBy { it.facultyName }
        facultyLoadAdapter.updateItems(items)
        emptyFacultyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        recyclerFacultyLoad.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HodViewTimetableActivity  –  Read-only single timetable viewer, opens on today's day
// ─────────────────────────────────────────────────────────────────────────────
class HodViewTimetableActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TIMETABLE_ID = "TIMETABLE_ID"
        const val EXTRA_INITIAL_DAY  = "INITIAL_DAY"
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    }

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var toolbar: Toolbar
    private lateinit var tvClassName: TextView
    private lateinit var tvDeptCourse: TextView
    private lateinit var tvSemesterYear: TextView
    private lateinit var tvDefaultDuration: TextView
    private lateinit var rgDays: RadioGroup
    private lateinit var tvSelectedDay: TextView
    private lateinit var recyclerSlots: RecyclerView
    private lateinit var tvEmptyDay: View
    private lateinit var progressBar: View
    private lateinit var btnGeneratePdf: com.google.android.material.button.MaterialButton
    private lateinit var btnShare: com.google.android.material.button.MaterialButton
    private lateinit var tvTodayBadge: TextView

    private var timetableId = ""
    private var initialDay  = "Monday"
    private var currentDay  = "Monday"
    private var timetable: Timetable? = null
    private lateinit var slotsAdapter: SlotsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hod_view_timetable)

        timetableId = intent.getStringExtra(EXTRA_TIMETABLE_ID) ?: run { finish(); return }
        initialDay  = intent.getStringExtra(EXTRA_INITIAL_DAY) ?: getTodayName()
        currentDay  = initialDay

        bindViews()
        setupToolbar()
        setupDaySelector()
        loadTimetable()
    }

    private fun bindViews() {
        toolbar           = findViewById(R.id.toolbar)
        tvClassName       = findViewById(R.id.tv_class_name)
        tvDeptCourse      = findViewById(R.id.tv_dept_course)
        tvSemesterYear    = findViewById(R.id.tv_semester_year)
        tvDefaultDuration = findViewById(R.id.tv_default_duration)
        rgDays            = findViewById(R.id.rg_days)
        tvSelectedDay     = findViewById(R.id.tv_selected_day)
        recyclerSlots     = findViewById(R.id.recycler_slots)
        tvEmptyDay        = findViewById(R.id.tv_empty_day)
        progressBar       = findViewById(R.id.progress_bar)
        btnGeneratePdf    = findViewById(R.id.btn_generate_pdf)
        btnShare          = findViewById(R.id.btn_share)
        tvTodayBadge      = findViewById(R.id.tv_today_badge)

        slotsAdapter = SlotsAdapter(emptyList(), readOnly = true)
        recyclerSlots.layoutManager = LinearLayoutManager(this)
        recyclerSlots.adapter = slotsAdapter

        btnGeneratePdf.setOnClickListener { Toast.makeText(this, "Generating PDF…", Toast.LENGTH_SHORT).show() }
        btnShare.setOnClickListener { Toast.makeText(this, "Share coming soon", Toast.LENGTH_SHORT).show() }
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.title = "View Timetable"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDaySelector() {
        rgDays.removeAllViews()
        DAYS.forEach { day ->
            val rb = RadioButton(this).apply {
                text  = day.take(3)
                tag   = day
                id    = View.generateViewId()
                // Highlight today's button visually
                if (day == getTodayName()) {
                    setTextColor(ContextCompat.getColor(context, android.R.color.holo_blue_light))
                } else {
                    setTextColor(ContextCompat.getColor(context, android.R.color.white))
                }
            }
            rgDays.addView(rb)
        }

        // Auto-select today (or initial day)
        val targetIdx = DAYS.indexOf(initialDay).coerceAtLeast(0)
        (rgDays.getChildAt(targetIdx) as? RadioButton)?.isChecked = true
        tvSelectedDay.text = initialDay
        updateTodayBadge(initialDay)

        rgDays.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            currentDay = rb?.tag as? String ?: "Monday"
            tvSelectedDay.text = currentDay
            updateTodayBadge(currentDay)
            refreshSlots()
        }
    }

    private fun updateTodayBadge(day: String) {
        val isToday = day == getTodayName()
        tvTodayBadge.visibility = if (isToday) View.VISIBLE else View.GONE
    }

    private fun loadTimetable() {
        progressBar.visibility = View.VISIBLE
        firestore.collection("timetables").document(timetableId)
            .get()
            .addOnSuccessListener { doc ->
                progressBar.visibility = View.GONE
                timetable = doc.toObject(Timetable::class.java)?.copy(id = doc.id) ?: run {
                    finish(); return@addOnSuccessListener
                }
                displayInfo()
                refreshSlots()
            }
            .addOnFailureListener { finish() }
    }

    private fun displayInfo() {
        val t = timetable ?: return
        tvClassName.text       = t.className
        tvDeptCourse.text      = "${t.departmentName} • ${t.courseName}"
        tvSemesterYear.text    = "Semester ${t.semester} | ${t.academicYear}"
        tvDefaultDuration.text = "Default: ${t.defaultLectureDurationMinutes} min/lecture"
    }

    private fun refreshSlots() {
        val t = timetable ?: return
        val slots = (t.schedule[currentDay] ?: emptyList()).sortedBy { it.startTimeMinutes }
        slotsAdapter.updateSlots(slots)
        tvEmptyDay.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        recyclerSlots.visibility = if (slots.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun getTodayName(): String {
        val cal = Calendar.getInstance()
        val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
        return days.getOrElse(cal.get(Calendar.DAY_OF_WEEK) - 2) { "Monday" }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Data classes for HOD tabs
// ─────────────────────────────────────────────────────────────────────────────
data class TodaySlotItem(
    val className: String,
    val courseName: String,
    val semester: Int,
    val slot: TimetableSlot
)

data class FacultyLoadItem(
    val facultyName: String,
    val dayCount: MutableMap<String, Int> = mutableMapOf(),
    var totalLectures: Int = 0
)

// ─────────────────────────────────────────────────────────────────────────────
// Adapter: HOD Timetable List (Tab 0)
// ─────────────────────────────────────────────────────────────────────────────
class HodTimetableListAdapter(
    private val items: List<Timetable>,
    private val onClick: (Timetable) -> Unit
) : RecyclerView.Adapter<HodTimetableListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvClassName: TextView   = view.findViewById(R.id.tv_class_name)
        val tvDeptCourse: TextView  = view.findViewById(R.id.tv_dept_course)
        val tvSemester: TextView    = view.findViewById(R.id.tv_semester)
        val tvAcademicYear: TextView = view.findViewById(R.id.tv_academic_year)
        val tvDaysCount: TextView   = view.findViewById(R.id.tv_days_count)
        val tvDuration: TextView    = view.findViewById(R.id.tv_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = items[position]
        holder.tvClassName.text    = t.className
        holder.tvDeptCourse.text   = "${t.departmentName} • ${t.courseName}"
        holder.tvSemester.text     = "Sem ${t.semester}"
        holder.tvAcademicYear.text = t.academicYear
        holder.tvDaysCount.text    = "${t.schedule.count { it.value.isNotEmpty() }} days scheduled"
        holder.tvDuration.text     = "${t.defaultLectureDurationMinutes} min/lecture"
        holder.itemView.setOnClickListener { onClick(t) }
    }

    override fun getItemCount() = items.size
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter: Today's slots (Tab 1)
// ─────────────────────────────────────────────────────────────────────────────
class HodTodaySlotsAdapter(
    private var items: List<TodaySlotItem>
) : RecyclerView.Adapter<HodTodaySlotsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvClassName: TextView  = view.findViewById(R.id.tv_today_class_name)
        val tvTime: TextView       = view.findViewById(R.id.tv_today_time)
        val tvSubject: TextView    = view.findViewById(R.id.tv_today_subject)
        val tvFaculty: TextView    = view.findViewById(R.id.tv_today_faculty)
        val tvRoom: TextView       = view.findViewById(R.id.tv_today_room)
        val tvType: TextView       = view.findViewById(R.id.tv_today_type)
        val cardToday: MaterialCardView = view.findViewById(R.id.card_today_slot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_today_slot, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val slot = item.slot

        holder.tvClassName.text = "${item.className} • Sem ${item.semester}"

        val startH = slot.startTimeMinutes / 60
        val startM = slot.startTimeMinutes % 60
        val endH   = slot.endTimeMinutes / 60
        val endM   = slot.endTimeMinutes % 60
        holder.tvTime.text = "${fmt12h(startH, startM)} – ${fmt12h(endH, endM)}"

        if (slot.slotType == "break") {
            holder.tvSubject.text  = slot.breakLabel.ifBlank { "Break" }
            holder.tvFaculty.visibility = View.GONE
            holder.tvRoom.visibility    = View.GONE
            holder.tvType.text = "BREAK"
            holder.tvType.setBackgroundResource(R.drawable.bg_chip_break)
            holder.cardToday.setCardBackgroundColor(
                holder.itemView.context.resources.getColor(android.R.color.holo_red_dark, null).let {
                    android.graphics.Color.argb(60, 255, 80, 80)
                }
            )
        } else {
            holder.tvSubject.text = "${slot.subjectName} (${slot.subjectCode})"
            holder.tvFaculty.visibility = if (slot.facultyName.isNotEmpty()) View.VISIBLE else View.GONE
            holder.tvFaculty.text = "👤 ${slot.facultyName}"
            holder.tvRoom.visibility = if (slot.room.isNotEmpty()) View.VISIBLE else View.GONE
            holder.tvRoom.text = "🚪 Room ${slot.room}"
            holder.tvType.text = slot.lectureType.uppercase()
            holder.tvType.setBackgroundResource(
                if (slot.lectureType == "practical") R.drawable.bg_chip_practical
                else R.drawable.bg_chip_theory
            )
            holder.cardToday.setCardBackgroundColor(0x2A1A1A3E)
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<TodaySlotItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    private fun fmt12h(h: Int, m: Int): String {
        val hh   = (h % 24)
        val amPm = if (hh < 12) "AM" else "PM"
        val dH   = when { hh == 0 -> 12; hh > 12 -> hh - 12; else -> hh }
        return String.format("%d:%02d %s", dH, m, amPm)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter: Faculty Load (Tab 2)
// ─────────────────────────────────────────────────────────────────────────────
class FacultyLoadAdapter(
    private var items: List<FacultyLoadItem>
) : RecyclerView.Adapter<FacultyLoadAdapter.VH>() {

    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val DAY_ABBR = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvFacultyName: TextView = view.findViewById(R.id.tv_faculty_name)
        val tvTotalLectures: TextView = view.findViewById(R.id.tv_total_lectures)
        val tvMon: TextView = view.findViewById(R.id.tv_load_mon)
        val tvTue: TextView = view.findViewById(R.id.tv_load_tue)
        val tvWed: TextView = view.findViewById(R.id.tv_load_wed)
        val tvThu: TextView = view.findViewById(R.id.tv_load_thu)
        val tvFri: TextView = view.findViewById(R.id.tv_load_fri)
        val tvSat: TextView = view.findViewById(R.id.tv_load_sat)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_faculty_load, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvFacultyName.text   = item.facultyName
        holder.tvTotalLectures.text = "${item.totalLectures} lectures/week"

        val dayViews = listOf(holder.tvMon, holder.tvTue, holder.tvWed,
            holder.tvThu, holder.tvFri, holder.tvSat)
        DAYS.forEachIndexed { idx, day ->
            val count = item.dayCount[day] ?: 0
            dayViews[idx].text = if (count > 0) "$count" else "–"
            // Colour code: 0 = dim, 1-2 = normal, 3+ = highlight
            val alpha = when {
                count == 0 -> 0.3f
                count >= 3 -> 1.0f
                else -> 0.8f
            }
            dayViews[idx].alpha = alpha
        }
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<FacultyLoadItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}