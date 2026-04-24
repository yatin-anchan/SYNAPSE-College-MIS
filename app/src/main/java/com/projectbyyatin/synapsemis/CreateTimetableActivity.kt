package com.projectbyyatin.synapsemis

import android.app.AlertDialog
import android.app.TimePickerDialog
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
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.*
import java.util.*

class CreateTimetableActivity : AppCompatActivity() {

    // ── Firebase ──────────────────────────────────────────────────────────────
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // ── Step 1 – Selection views ───────────────────────────────────────────────
    private lateinit var spinnerDept: Spinner
    private lateinit var spinnerCourse: Spinner
    private lateinit var spinnerClass: Spinner
    private lateinit var etDefaultDuration: TextInputEditText
    private lateinit var btnProceedToSchedule: MaterialButton

    // ── Step 2 – Schedule views ────────────────────────────────────────────────
    private lateinit var layoutStep1: View
    private lateinit var layoutStep2: View
    private lateinit var tabDays: TabHost          // or RadioGroup – replaced below with RadioGroup
    private lateinit var rgDays: RadioGroup
    private lateinit var recyclerSlots: RecyclerView
    private lateinit var btnAddLecture: MaterialButton
    private lateinit var btnAddBreak: MaterialButton
    private lateinit var btnSaveTimetable: MaterialButton
    private lateinit var tvSelectedDay: TextView
    private lateinit var tvClassHeader: TextView

    // ── Data ──────────────────────────────────────────────────────────────────
    private val departments = mutableListOf<Department>()
    private val courses = mutableListOf<Course>()
    private val classes = mutableListOf<Class>()
    private val subjects = mutableListOf<Subject>()

    private var selectedDept: Department? = null
    private var selectedCourse: Course? = null
    private var selectedClass: Class? = null
    private var defaultDurationMinutes = 60

    // schedule: day -> mutable list of slots
    private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val schedule: MutableMap<String, MutableList<TimetableSlot>> =
        DAYS.associateWith { mutableListOf<TimetableSlot>() }.toMutableMap()
    private var currentDay = "Monday"

    private lateinit var slotsAdapter: SlotsAdapter

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_timetable)

        setupToolbar()
        bindViews()
        setupDaySelector()
        loadDepartments()
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────
    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Create Timetable"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    // ── Bind all views ────────────────────────────────────────────────────────
    private fun bindViews() {
        layoutStep1 = findViewById(R.id.layout_step1)
        layoutStep2 = findViewById(R.id.layout_step2)

        // Step 1
        spinnerDept = findViewById(R.id.spinner_dept)
        spinnerCourse = findViewById(R.id.spinner_course)
        spinnerClass = findViewById(R.id.spinner_class)
        etDefaultDuration = findViewById(R.id.et_default_duration)
        btnProceedToSchedule = findViewById(R.id.btn_proceed)

        // Step 2
        rgDays = findViewById(R.id.rg_days)
        recyclerSlots = findViewById(R.id.recycler_slots)
        btnAddLecture = findViewById(R.id.btn_add_lecture)
        btnAddBreak = findViewById(R.id.btn_add_break)
        btnSaveTimetable = findViewById(R.id.btn_save_timetable)
        tvSelectedDay = findViewById(R.id.tv_selected_day)
        tvClassHeader = findViewById(R.id.tv_class_header)

        // RecyclerView
        slotsAdapter = SlotsAdapter(
            slots = mutableListOf(),
            onDelete = { slot -> deleteSlot(slot) },
            onEdit = { slot -> showSlotDialog(editSlot = slot) }
        )
        recyclerSlots.layoutManager = LinearLayoutManager(this)
        recyclerSlots.adapter = slotsAdapter

        // Proceed button
        btnProceedToSchedule.setOnClickListener { validateAndProceed() }

        // Add lecture / break buttons
        btnAddLecture.setOnClickListener { showSlotDialog(isBreak = false) }
        btnAddBreak.setOnClickListener { showSlotDialog(isBreak = true) }

        // Save
        btnSaveTimetable.setOnClickListener { saveTimetable() }

        // Initially show only step 1
        layoutStep1.visibility = View.VISIBLE
        layoutStep2.visibility = View.GONE
    }

    // ── Day selector (RadioGroup programmatic) ────────────────────────────────
    private fun setupDaySelector() {
        rgDays.removeAllViews()
        DAYS.forEach { day ->
            val rb = RadioButton(this).apply {
                text = day.take(3)  // Mon, Tue …
                tag = day
                id = View.generateViewId()
            }
            rgDays.addView(rb)
        }
        // Select Monday by default
        (rgDays.getChildAt(0) as? RadioButton)?.isChecked = true

        rgDays.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            currentDay = rb?.tag as? String ?: "Monday"
            tvSelectedDay.text = currentDay
            refreshSlotList()
        }
    }

    // ── Step 1: Load departments ───────────────────────────────────────────────
    private fun loadDepartments() {
        firestore.collection("departments")
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                departments.clear()
                departments.addAll(snap.documents.mapNotNull { doc ->
                    doc.toObject(Department::class.java)?.also { it.id = doc.id }
                })
                val names = listOf("Select Department") + departments.map { it.name }
                spinnerDept.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                    .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinnerDept.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        selectedDept = if (pos == 0) null else departments[pos - 1]
                        selectedCourse = null
                        selectedClass = null
                        if (selectedDept != null) loadCourses(selectedDept!!.id)
                        else resetCourseSpinner()
                    }
                }
            }
    }

    private fun resetCourseSpinner() {
        courses.clear()
        spinnerCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("Select Course"))
        resetClassSpinner()
    }

    private fun loadCourses(deptId: String) {
        firestore.collection("courses")
            .whereEqualTo("departmentId", deptId)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                courses.clear()
                courses.addAll(snap.documents.mapNotNull { doc ->
                    doc.toObject(Course::class.java)?.also { it.id = doc.id }
                })
                val names = listOf("Select Course") + courses.map { "${it.name} (${it.code})" }
                spinnerCourse.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                    .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinnerCourse.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        selectedCourse = if (pos == 0) null else courses[pos - 1]
                        selectedClass = null
                        if (selectedCourse != null) loadClasses(selectedCourse!!.id)
                        else resetClassSpinner()
                    }
                }
            }
    }

    private fun resetClassSpinner() {
        classes.clear()
        spinnerClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            listOf("Select Class"))
    }

    private fun loadClasses(courseId: String) {
        firestore.collection("classes")
            .whereEqualTo("courseId", courseId)
            .whereEqualTo("active", true)
            .get()
            .addOnSuccessListener { snap ->
                classes.clear()
                classes.addAll(snap.documents.mapNotNull { doc ->
                    doc.toObject(Class::class.java)?.also { it.id = doc.id }
                })
                val names = listOf("Select Class") + classes.map { it.className }
                spinnerClass.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                    .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

                spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        selectedClass = if (pos == 0) null else classes[pos - 1]
                    }
                }
            }
    }

    // ── Validate step 1 & move to step 2 ──────────────────────────────────────
    private fun validateAndProceed() {
        if (selectedDept == null) { toast("Please select a department"); return }
        if (selectedCourse == null) { toast("Please select a course"); return }
        if (selectedClass == null) { toast("Please select a class"); return }

        val durationText = etDefaultDuration.text?.toString()?.trim()
        val durationHrs = durationText?.toDoubleOrNull()
        if (durationHrs == null || durationHrs <= 0) {
            toast("Enter a valid lecture duration (hours)"); return
        }
        defaultDurationMinutes = (durationHrs * 60).toInt()

        // Check if timetable already exists for this class
        firestore.collection("timetables")
            .whereEqualTo("classId", selectedClass!!.id)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                if (!snap.isEmpty) {
                    toast("A timetable already exists for this class!")
                } else {
                    // Load subjects for the class's current semester + course
                    loadSubjects()
                }
            }
    }

    private fun loadSubjects() {
        firestore.collection("subjects")
            .whereEqualTo("courseId", selectedCourse!!.id)
            .whereEqualTo("semesterNumber", selectedClass!!.currentSemester)
            .whereEqualTo("isActive", true)
            .get()
            .addOnSuccessListener { snap ->
                subjects.clear()
                subjects.addAll(snap.documents.mapNotNull { doc ->
                    doc.toObject(Subject::class.java)?.also { it.id = doc.id }
                })

                // Move to step 2
                layoutStep1.visibility = View.GONE
                layoutStep2.visibility = View.VISIBLE
                tvClassHeader.text = "${selectedClass!!.className} | Sem ${selectedClass!!.currentSemester}"
                tvSelectedDay.text = currentDay
                refreshSlotList()
            }
            .addOnFailureListener { toast("Failed to load subjects") }
    }

    // ── Slot list refresh ──────────────────────────────────────────────────────
    private fun refreshSlotList() {
        val daySlots = schedule[currentDay] ?: mutableListOf()
        slotsAdapter.updateSlots(daySlots.sortedBy { it.startTimeMinutes })
    }

    // ── Add / Edit slot dialog ────────────────────────────────────────────────
    private fun showSlotDialog(isBreak: Boolean = false, editSlot: TimetableSlot? = null) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_slot, null)

        // Views inside dialog
        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tv_dialog_title)
        val layoutLecture = dialogView.findViewById<View>(R.id.layout_lecture_fields)
        val layoutBreak = dialogView.findViewById<View>(R.id.layout_break_fields)
        val spinnerSubject = dialogView.findViewById<Spinner>(R.id.spinner_subject)
        val rgLectureType = dialogView.findViewById<RadioGroup>(R.id.rg_lecture_type)
        val rbTheory = dialogView.findViewById<RadioButton>(R.id.rb_theory)
        val rbPractical = dialogView.findViewById<RadioButton>(R.id.rb_practical)
        val tvStartTime = dialogView.findViewById<TextView>(R.id.tv_start_time)
        val tvEndTime = dialogView.findViewById<TextView>(R.id.tv_end_time)
        val checkCustomDuration = dialogView.findViewById<CheckBox>(R.id.check_custom_duration)
        val etCustomDuration = dialogView.findViewById<TextInputEditText>(R.id.et_custom_duration)
        val etRoom = dialogView.findViewById<TextInputEditText>(R.id.et_room)
        val etBreakLabel = dialogView.findViewById<TextInputEditText>(R.id.et_break_label)
        val etBreakDuration = dialogView.findViewById<TextInputEditText>(R.id.et_break_duration)

        // Determine if we're editing a break or lecture
        val showingBreak = if (editSlot != null) editSlot.slotType == "break" else isBreak
        tvDialogTitle.text = if (editSlot != null) "Edit Slot" else if (showingBreak) "Add Break" else "Add Lecture"
        layoutLecture.visibility = if (showingBreak) View.GONE else View.VISIBLE
        layoutBreak.visibility = if (showingBreak) View.VISIBLE else View.GONE

        // Determine smart start time: last slot end time for this day
        val daySlots = schedule[currentDay] ?: mutableListOf()
        val smartStartMinutes = if (editSlot != null) {
            editSlot.startTimeMinutes
        } else {
            daySlots.maxByOrNull { it.endTimeMinutes }?.endTimeMinutes ?: (8 * 60) // default 8:00 AM
        }
        var selectedStartMinutes = smartStartMinutes
        var selectedDuration = if (editSlot != null) editSlot.durationMinutes else defaultDurationMinutes
        var selectedSubject: Subject? = null

        // Subject spinner
        if (!showingBreak) {
            val subjectNames = listOf("Select Subject") + subjects.map { "${it.name} (${it.code})" }
            spinnerSubject.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, subjectNames)
                .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

            // Restore editing subject
            if (editSlot != null && editSlot.subjectId.isNotEmpty()) {
                val idx = subjects.indexOfFirst { it.id == editSlot.subjectId }
                if (idx >= 0) {
                    spinnerSubject.setSelection(idx + 1)
                    selectedSubject = subjects[idx]
                }
            }

            spinnerSubject.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p: AdapterView<*>?) {}
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    selectedSubject = if (pos == 0) null else subjects[pos - 1]
                    // Control lecture type radio based on subject type
                    if (selectedSubject != null) {
                        val hasPractical = selectedSubject!!.type.equals("Practical", ignoreCase = true)
                        val isTheoryOnly = selectedSubject!!.type.equals("Theory", ignoreCase = true)
                        rbPractical.isEnabled = hasPractical || !isTheoryOnly
                        if (isTheoryOnly) rbTheory.isChecked = true
                    }
                }
            }

            // Restore lecture type
            if (editSlot != null) {
                if (editSlot.lectureType == "practical") rbPractical.isChecked = true
                else rbTheory.isChecked = true
            }

            // Room
            if (editSlot != null) etRoom.setText(editSlot.room)
        }

        // Start time
        fun updateTimeDisplay() {
            tvStartTime.text = minutesToTimeString(selectedStartMinutes)
            tvEndTime.text = minutesToTimeString(selectedStartMinutes + selectedDuration)
        }
        updateTimeDisplay()

        tvStartTime.setOnClickListener {
            val h = selectedStartMinutes / 60
            val m = selectedStartMinutes % 60
            TimePickerDialog(this, { _, hour, minute ->
                selectedStartMinutes = hour * 60 + minute
                updateTimeDisplay()
            }, h, m, false).show()
        }

        // Custom duration
        checkCustomDuration.isChecked = editSlot != null && editSlot.durationMinutes != defaultDurationMinutes
        etCustomDuration.visibility = if (checkCustomDuration.isChecked) View.VISIBLE else View.GONE
        if (checkCustomDuration.isChecked) etCustomDuration.setText("${editSlot!!.durationMinutes}")

        checkCustomDuration.setOnCheckedChangeListener { _, checked ->
            etCustomDuration.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                selectedDuration = defaultDurationMinutes
                updateTimeDisplay()
            }
        }

        etCustomDuration.setOnFocusChangeListener { _, _ ->
            val mins = etCustomDuration.text?.toString()?.toIntOrNull()
            if (mins != null && mins > 0) {
                selectedDuration = mins
                updateTimeDisplay()
            }
        }

        // Break fields restore
        if (showingBreak && editSlot != null) {
            etBreakLabel.setText(editSlot.breakLabel)
            etBreakDuration.setText("${editSlot.durationMinutes}")
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton("Save", null) // Override below to avoid auto-dismiss
            .setNegativeButton("Cancel", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                // Validate & build slot
                if (checkCustomDuration.isChecked) {
                    val mins = etCustomDuration.text?.toString()?.toIntOrNull()
                    if (mins != null && mins > 0) selectedDuration = mins
                }

                val slot = if (showingBreak) {
                    val label = etBreakLabel.text?.toString()?.trim().takeIf { !it.isNullOrEmpty() } ?: "Break"
                    val breakDur = etBreakDuration.text?.toString()?.toIntOrNull() ?: 15
                    TimetableSlot(
                        id = editSlot?.id ?: UUID.randomUUID().toString(),
                        slotType = "break",
                        breakLabel = label,
                        startTimeMinutes = selectedStartMinutes,
                        durationMinutes = breakDur,
                        endTimeMinutes = selectedStartMinutes + breakDur
                    )
                } else {
                    if (selectedSubject == null) { toast("Please select a subject"); return@setOnClickListener }
                    val lectureType = if (rbPractical.isChecked) "practical" else "theory"
                    val room = etRoom.text?.toString()?.trim() ?: ""
                    TimetableSlot(
                        id = editSlot?.id ?: UUID.randomUUID().toString(),
                        slotType = "lecture",
                        lectureType = lectureType,
                        subjectId = selectedSubject!!.id,
                        subjectName = selectedSubject!!.name,
                        subjectCode = selectedSubject!!.code,
                        subjectType = selectedSubject!!.type,
                        facultyId = selectedSubject!!.assignedFacultyId,
                        facultyName = selectedSubject!!.assignedFacultyName,
                        room = room,
                        startTimeMinutes = selectedStartMinutes,
                        durationMinutes = selectedDuration,
                        endTimeMinutes = selectedStartMinutes + selectedDuration
                    )
                }

                // Check for overlap (excluding the slot being edited)
                if (hasOverlap(slot, excludeId = editSlot?.id)) {
                    toast("This slot overlaps with an existing slot!")
                    return@setOnClickListener
                }

                // Remove old slot if editing
                if (editSlot != null) {
                    schedule[currentDay]?.removeAll { it.id == editSlot.id }
                }
                schedule[currentDay]?.add(slot)
                refreshSlotList()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // ── Overlap check ─────────────────────────────────────────────────────────
    private fun hasOverlap(newSlot: TimetableSlot, excludeId: String?): Boolean {
        val daySlots = schedule[currentDay] ?: return false
        return daySlots
            .filter { it.id != excludeId }
            .any { existing ->
                newSlot.startTimeMinutes < existing.endTimeMinutes &&
                        newSlot.endTimeMinutes > existing.startTimeMinutes
            }
    }

    // ── Delete slot ───────────────────────────────────────────────────────────
    private fun deleteSlot(slot: TimetableSlot) {
        AlertDialog.Builder(this)
            .setTitle("Delete Slot")
            .setMessage("Remove this slot from ${currentDay}?")
            .setPositiveButton("Delete") { _, _ ->
                schedule[currentDay]?.removeAll { it.id == slot.id }
                refreshSlotList()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ── Save timetable to Firestore ────────────────────────────────────────────
    private fun saveTimetable() {
        // Warn if some days are empty
        val emptyDays = DAYS.filter { schedule[it].isNullOrEmpty() }
        if (emptyDays.size == DAYS.size) {
            toast("Please add at least one lecture to any day!"); return
        }

        val proceed = {
            val timetable = Timetable(
                classId = selectedClass!!.id,
                className = selectedClass!!.className,
                courseId = selectedCourse!!.id,
                courseName = selectedCourse!!.name,
                departmentId = selectedDept!!.id,
                departmentName = selectedDept!!.name,
                semester = selectedClass!!.currentSemester,
                academicYear = selectedClass!!.academicYear,
                defaultLectureDurationMinutes = defaultDurationMinutes,
                schedule = schedule.mapValues { (_, slots) ->
                    slots.sortedBy { it.startTimeMinutes }
                },
                createdBy = auth.currentUser?.uid ?: "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isActive = true
            )

            btnSaveTimetable.isEnabled = false
            firestore.collection("timetables")
                .add(timetable)
                .addOnSuccessListener {
                    toast("Timetable saved successfully!")
                    finish()
                }
                .addOnFailureListener { e ->
                    btnSaveTimetable.isEnabled = true
                    toast("Failed to save: ${e.message}")
                }
        }

        if (emptyDays.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Empty Days")
                .setMessage("No lectures added for: ${emptyDays.joinToString(", ")}.\nSave anyway?")
                .setPositiveButton("Save") { _, _ -> proceed() }
                .setNegativeButton("Go Back", null)
                .show()
        } else {
            proceed()
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun minutesToTimeString(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val displayH = when {
            h == 0 -> 12
            h > 12 -> h - 12
            else -> h
        }
        return String.format("%d:%02d %s", displayH, m, amPm)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// ─────────────────────────────────────────────────────────────────────────────
// SlotsAdapter – used in CreateTimetableActivity & ManageTimetableActivity
// ─────────────────────────────────────────────────────────────────────────────
class SlotsAdapter(
    private var slots: List<TimetableSlot>,
    private val onDelete: ((TimetableSlot) -> Unit)? = null,
    private val onEdit: ((TimetableSlot) -> Unit)? = null,
    private val readOnly: Boolean = false
) : RecyclerView.Adapter<SlotsAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTime: TextView = view.findViewById(R.id.tv_slot_time)
        val tvTitle: TextView = view.findViewById(R.id.tv_slot_title)
        val tvSubtitle: TextView = view.findViewById(R.id.tv_slot_subtitle)
        val tvType: TextView = view.findViewById(R.id.tv_slot_type)
        val btnEdit: View = view.findViewById(R.id.btn_slot_edit)
        val btnDelete: View = view.findViewById(R.id.btn_slot_delete)
        val cardSlot: MaterialCardView = view.findViewById(R.id.card_slot)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timetable_slot, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val slot = slots[position]
        holder.tvTime.text = "${minutesToTime(slot.startTimeMinutes)} – ${minutesToTime(slot.endTimeMinutes)}"

        if (slot.slotType == "break") {
            holder.tvTitle.text = slot.breakLabel
            holder.tvSubtitle.text = "${slot.durationMinutes} min"
            holder.tvType.text = "BREAK"
            holder.tvType.setBackgroundResource(R.drawable.bg_chip_break) // tint differently
        } else {
            holder.tvTitle.text = "${slot.subjectName} (${slot.subjectCode})"
            holder.tvSubtitle.text = buildString {
                if (slot.facultyName.isNotEmpty()) append(slot.facultyName)
                if (slot.room.isNotEmpty()) append(" • Room ${slot.room}")
                if (slot.durationMinutes > 0) append(" • ${slot.durationMinutes} min")
            }
            holder.tvType.text = slot.lectureType.uppercase()
            holder.tvType.setBackgroundResource(
                if (slot.lectureType == "practical") R.drawable.bg_chip_practical
                else R.drawable.bg_chip_theory
            )
        }

        if (readOnly) {
            holder.btnEdit.visibility = View.GONE
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnEdit.visibility = View.VISIBLE
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnEdit.setOnClickListener { onEdit?.invoke(slot) }
            holder.btnDelete.setOnClickListener { onDelete?.invoke(slot) }
        }
    }

    override fun getItemCount() = slots.size

    fun updateSlots(newSlots: List<TimetableSlot>) {
        slots = newSlots
        notifyDataSetChanged()
    }

    private fun minutesToTime(minutes: Int): String {
        val h = (minutes / 60) % 24
        val m = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val dH = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return String.format("%d:%02d %s", dH, m, amPm)
    }
}