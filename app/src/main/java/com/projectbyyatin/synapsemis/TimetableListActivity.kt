package com.projectbyyatin.synapsemis

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Timetable

class TimetableListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var fabCreate: ExtendedFloatingActionButton
    private lateinit var emptyView: View
    private lateinit var filterChipGroup: ChipGroup
    private lateinit var progressBar: View

    private val firestore = FirebaseFirestore.getInstance()
    private val allTimetables = mutableListOf<Timetable>()
    private val filteredTimetables = mutableListOf<Timetable>()
    private lateinit var adapter: TimetableListAdapter
    private var selectedDepartment = "All"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timetable_list)

        setupToolbar()
        setupViews()
        loadTimetables()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Timetables"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews() {
        recyclerView = findViewById(R.id.recycler_timetables)
        fabCreate = findViewById(R.id.fab_create_timetable)
        emptyView = findViewById(R.id.empty_view)
        filterChipGroup = findViewById(R.id.chip_group_filter)
        progressBar = findViewById(R.id.progress_bar)

        adapter = TimetableListAdapter(filteredTimetables,
            onViewClick = { timetable ->
                // Open manage activity (view mode)
                val intent = Intent(this, ManageTimetableActivity::class.java)
                intent.putExtra(ManageTimetableActivity.EXTRA_TIMETABLE_ID, timetable.id)
                startActivity(intent)
            }
        )

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        fabCreate.setOnClickListener {
            startActivity(Intent(this, CreateTimetableActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        // Reload in case a new timetable was created
        loadTimetables()
    }

    private fun loadTimetables() {
        progressBar.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        emptyView.visibility = View.GONE

        firestore.collection("timetables")
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { documents ->
                progressBar.visibility = View.GONE
                allTimetables.clear()
                val departments = mutableSetOf("All")

                for (doc in documents) {
                    val timetable = doc.toObject(Timetable::class.java).copy(id = doc.id)
                    allTimetables.add(timetable)
                    if (timetable.departmentName.isNotEmpty()) {
                        departments.add(timetable.departmentName)
                    }
                }

                setupFilterChips(departments.sorted())
                applyFilter()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load timetables: ${e.message}", Toast.LENGTH_SHORT).show()
                emptyView.visibility = View.VISIBLE
            }
    }

    private fun setupFilterChips(departments: List<String>) {
        filterChipGroup.removeAllViews()
        departments.forEach { dept ->
            val chip = Chip(this)
            chip.text = dept
            chip.isCheckable = true
            chip.isChecked = dept == selectedDepartment
            chip.setOnClickListener {
                selectedDepartment = dept
                // Uncheck sibling chips
                for (i in 0 until filterChipGroup.childCount) {
                    val c = filterChipGroup.getChildAt(i) as? Chip
                    c?.isChecked = c?.text == dept
                }
                applyFilter()
            }
            filterChipGroup.addView(chip)
        }
    }

    private fun applyFilter() {
        filteredTimetables.clear()
        if (selectedDepartment == "All") {
            filteredTimetables.addAll(allTimetables)
        } else {
            filteredTimetables.addAll(allTimetables.filter { it.departmentName == selectedDepartment })
        }
        adapter.notifyDataSetChanged()

        val isEmpty = filteredTimetables.isEmpty()
        recyclerView.visibility = if (isEmpty) View.GONE else View.VISIBLE
        emptyView.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Adapter
// ─────────────────────────────────────────────────────────────────────────────
class TimetableListAdapter(
    private val items: List<Timetable>,
    private val onViewClick: (Timetable) -> Unit
) : RecyclerView.Adapter<TimetableListAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvClassName: TextView = view.findViewById(R.id.tv_class_name)
        val tvDeptCourse: TextView = view.findViewById(R.id.tv_dept_course)
        val tvSemester: TextView = view.findViewById(R.id.tv_semester)
        val tvAcademicYear: TextView = view.findViewById(R.id.tv_academic_year)
        val tvDaysCount: TextView = view.findViewById(R.id.tv_days_count)
        val tvDuration: TextView = view.findViewById(R.id.tv_duration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        holder.tvClassName.text = item.className
        holder.tvDeptCourse.text = "${item.departmentName} • ${item.courseName}"
        holder.tvSemester.text = "Semester ${item.semester}"
        holder.tvAcademicYear.text = item.academicYear
        holder.tvDaysCount.text = "${item.schedule.size} day(s) scheduled"
        holder.tvDuration.text = "Default: ${item.defaultLectureDurationMinutes} min/lecture"
        holder.itemView.setOnClickListener { onViewClick(item) }
    }

    override fun getItemCount() = items.size
}