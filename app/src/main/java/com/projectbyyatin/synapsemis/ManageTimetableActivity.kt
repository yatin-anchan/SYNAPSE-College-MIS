package com.projectbyyatin.synapsemis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Timetable
import com.projectbyyatin.synapsemis.models.TimetableSlot
import java.io.File
import java.io.FileOutputStream

class ManageTimetableActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TIMETABLE_ID = "TIMETABLE_ID"
        private const val STORAGE_PERMISSION_CODE = 101
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    }

    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var tvClassName: TextView
    private lateinit var tvDeptCourse: TextView
    private lateinit var tvSemesterYear: TextView
    private lateinit var tvDefaultDuration: TextView
    private lateinit var rgDays: RadioGroup
    private lateinit var tvSelectedDay: TextView
    private lateinit var recyclerSlots: RecyclerView
    private lateinit var tvEmptyDay: View
    private lateinit var btnEdit: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var btnGeneratePdf: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var progressBar: View

    private var timetableId: String = ""
    private var timetable: Timetable? = null
    private var currentDay = "Monday"
    private lateinit var slotsAdapter: SlotsAdapter

    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manage_timetable)

        timetableId = intent.getStringExtra(EXTRA_TIMETABLE_ID) ?: run {
            Toast.makeText(this, "Invalid timetable", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupToolbar()
        bindViews()
        loadTimetable()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Manage Timetable"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun bindViews() {
        tvClassName = findViewById(R.id.tv_class_name)
        tvDeptCourse = findViewById(R.id.tv_dept_course)
        tvSemesterYear = findViewById(R.id.tv_semester_year)
        tvDefaultDuration = findViewById(R.id.tv_default_duration)
        rgDays = findViewById(R.id.rg_days)
        tvSelectedDay = findViewById(R.id.tv_selected_day)
        recyclerSlots = findViewById(R.id.recycler_slots)
        tvEmptyDay = findViewById(R.id.tv_empty_day)
        btnEdit = findViewById(R.id.btn_edit)
        btnDelete = findViewById(R.id.btn_delete)
        btnGeneratePdf = findViewById(R.id.btn_generate_pdf)
        btnShare = findViewById(R.id.btn_share)
        progressBar = findViewById(R.id.progress_bar)

        slotsAdapter = SlotsAdapter(emptyList(), readOnly = true)
        recyclerSlots.layoutManager = LinearLayoutManager(this)
        recyclerSlots.adapter = slotsAdapter

        setupDaySelector()

        btnEdit.setOnClickListener { openEditMode() }
        btnDelete.setOnClickListener { confirmDelete() }
        btnGeneratePdf.setOnClickListener { checkPermissionAndGeneratePdf() }
        btnShare.setOnClickListener { shareTimetable() }
    }

    private fun setupDaySelector() {
        rgDays.removeAllViews()
        DAYS.forEach { day ->
            val rb = RadioButton(this).apply {
                text = day.take(3)
                tag = day
                id = View.generateViewId()
            }
            rgDays.addView(rb)
        }
        (rgDays.getChildAt(0) as? RadioButton)?.isChecked = true

        rgDays.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            currentDay = rb?.tag as? String ?: "Monday"
            tvSelectedDay.text = currentDay
            refreshSlots()
        }
    }

    private fun loadTimetable() {
        progressBar.visibility = View.VISIBLE
        firestore.collection("timetables").document(timetableId)
            .get()
            .addOnSuccessListener { doc ->
                progressBar.visibility = View.GONE
                if (!doc.exists()) {
                    Toast.makeText(this, "Timetable not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                timetable = doc.toObject(Timetable::class.java)?.copy(id = doc.id)
                displayTimetableInfo()
                refreshSlots()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayTimetableInfo() {
        val t = timetable ?: return
        tvClassName.text = t.className
        tvDeptCourse.text = "${t.departmentName} • ${t.courseName}"
        tvSemesterYear.text = "Semester ${t.semester} | ${t.academicYear}"
        tvDefaultDuration.text = "Default lecture: ${t.defaultLectureDurationMinutes} min"
    }

    private fun refreshSlots() {
        val t = timetable ?: return
        val slots = (t.schedule[currentDay] ?: emptyList()).sortedBy { it.startTimeMinutes }
        slotsAdapter.updateSlots(slots)
        tvEmptyDay.visibility = if (slots.isEmpty()) View.VISIBLE else View.GONE
        recyclerSlots.visibility = if (slots.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun openEditMode() {
        val intent = Intent(this, EditTimetableActivity::class.java)
        intent.putExtra(EXTRA_TIMETABLE_ID, timetableId)
        startActivity(intent)
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Timetable")
            .setMessage("Are you sure you want to delete the timetable for ${timetable?.className}? This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> deleteTimetable() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteTimetable() {
        progressBar.visibility = View.VISIBLE
        firestore.collection("timetables").document(timetableId)
            .update(mapOf("active" to false, "updatedAt" to System.currentTimeMillis()))
            .addOnSuccessListener {
                Toast.makeText(this, "Timetable deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to delete: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ── PDF Generation ────────────────────────────────────────────────────────
    private fun checkPermissionAndGeneratePdf() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), STORAGE_PERMISSION_CODE
                )
                return
            }
        }
        generatePdf()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            generatePdf()
        } else {
            Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Generates a professional, official-style timetable PDF matching the
     * standard institutional timetable format:
     *  - Centred bold header (institution / department / course / class)
     *  - Bordered grid table with black header row (white text)
     *  - BREAK slots highlighted in solid red with white bold text
     *  - Lectures show subject code (bold); practicals show "PRACTICAL" label
     *  - Alternating light-grey row backgrounds for readability
     */
    private fun generatePdf(): File? {
        val t = timetable ?: return null
        val pdfDoc = PdfDocument()

        // A4 Landscape: 842 × 595 pt
        val pageWidth  = 842
        val pageHeight = 595
        val pageInfo   = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page       = pdfDoc.startPage(pageInfo)
        val canvas: Canvas = page.canvas

        // ── Paint factories ───────────────────────────────────────────────────
        val fillPaint   = Paint().apply { style = Paint.Style.FILL;   isAntiAlias = true }
        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 1f
            color = Color.BLACK;         isAntiAlias = true
        }

        fun centredText(size: Float, bold: Boolean = false, color: Int = Color.BLACK) = Paint().apply {
            textSize = size; isFakeBoldText = bold
            this.color = color; isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        // ── Draw filled + bordered rect ───────────────────────────────────────
        fun drawCell(l: Float, t: Float, w: Float, h: Float, bg: Int) {
            fillPaint.color = bg
            canvas.drawRect(l, t, l + w, t + h, fillPaint)
            canvas.drawRect(l, t, l + w, t + h, strokePaint)
        }

        // ── Draw text centred inside a cell, clipped, multi-line support ──────
        fun drawText(
            lines: List<String>, l: Float, top: Float, w: Float, h: Float,
            size: Float, bold: Boolean = false, color: Int = Color.BLACK
        ) {
            val p      = centredText(size, bold, color)
            val lineH  = size + 2f
            val totalH = lines.size * lineH
            var ty     = top + (h - totalH) / 2f + size
            canvas.save()
            canvas.clipRect(l + 2f, top + 1f, l + w - 2f, top + h - 1f)
            lines.forEach { line ->
                canvas.drawText(line, l + w / 2f, ty, p)
                ty += lineH
            }
            canvas.restore()
        }

        val margin = 22f

        // ── Header section ────────────────────────────────────────────────────
        // Line 1: Institution name (largest, bold)
        val institutionName = "ROYAL COLLEGE OF ARTS, SCINECE AND COMMERCE, MIRA ROAD (EAST)".uppercase()
        var yCursor = margin + 13f
        canvas.drawText(institutionName, pageWidth / 2f, yCursor, centredText(11f, bold = true))

        // Line 2: Department
        yCursor += 14f
        canvas.drawText(
            "DEPARTMENT OF ${t.departmentName.uppercase()}",
            pageWidth / 2f, yCursor, centredText(10f, bold = true)
        )

        // Line 3: Course full name
        yCursor += 13f
        canvas.drawText(
            t.courseName,
            pageWidth / 2f, yCursor, centredText(9f, bold = true)
        )

        // Line 4: Class / timetable title
        yCursor += 12f
        canvas.drawText(
            "${t.className} Time Table",
            pageWidth / 2f, yCursor, centredText(9f, bold = true)
        )

        // Horizontal rule
        yCursor += 8f
        canvas.drawLine(margin, yCursor, pageWidth - margin, yCursor, strokePaint)
        yCursor += 6f

        // ── Table dimensions ──────────────────────────────────────────────────
        val timeSlots  = getUniqueTimeSlots(t)      // sorted unique start-time strings
        val numSlots   = timeSlots.size.coerceAtLeast(1)
        val numDays    = DAYS.size                  // 6

        val tableLeft  = margin
        val tableWidth = pageWidth - 2f * margin
        val timeColW   = 80f                        // "TIME" label column
        val slotColW   = (tableWidth - timeColW) / numSlots
        val headerRowH = 22f
        val remainH    = pageHeight - yCursor - margin - headerRowH
        val dataRowH   = (remainH / numDays).coerceIn(30f, 65f)

        val COL_BLACK     = Color.BLACK
        val COL_WHITE     = Color.WHITE
        val COL_RED       = Color.RED
        val COL_ROW_EVEN  = Color.WHITE
        val COL_ROW_ODD   = Color.parseColor("#F2F2F2")

        // ── Header row ────────────────────────────────────────────────────────
        // "TIME" cell
        drawCell(tableLeft, yCursor, timeColW, headerRowH, COL_BLACK)
        drawText(listOf("TIME"), tableLeft, yCursor, timeColW, headerRowH, 8f, bold = true, color = COL_WHITE)

        // Time slot cells — show "HH:MM AM – HH:MM AM" by computing end from next slot or default duration
        timeSlots.forEachIndexed { idx, startTime ->
            val cx = tableLeft + timeColW + idx * slotColW
            drawCell(cx, yCursor, slotColW, headerRowH, COL_BLACK)

            // Build "Start – End" label from any slot at this time
            val anySlot = t.schedule.values.flatten()
                .find { minutesToTime(it.startTimeMinutes) == startTime }
            val endLabel = if (anySlot != null) minutesToTime(anySlot.endTimeMinutes) else ""
            val headerLabel = if (endLabel.isNotEmpty()) "$startTime –\n$endLabel" else startTime

            drawText(
                headerLabel.split("\n"), cx, yCursor, slotColW, headerRowH,
                7f, bold = true, color = COL_WHITE
            )
        }
        yCursor += headerRowH

        // ── Data rows ─────────────────────────────────────────────────────────
        DAYS.forEachIndexed { dayIdx, day ->
            val rowTop = yCursor + dayIdx * dataRowH
            val rowBg  = if (dayIdx % 2 == 0) COL_ROW_EVEN else COL_ROW_ODD

            // Day name cell
            drawCell(tableLeft, rowTop, timeColW, dataRowH, rowBg)
            drawText(listOf(day.uppercase()), tableLeft, rowTop, timeColW, dataRowH, 8f, bold = true)

            val daySlots = t.schedule[day] ?: emptyList()

            timeSlots.forEachIndexed { idx, startTime ->
                val cx   = tableLeft + timeColW + idx * slotColW
                val slot = daySlots.find { minutesToTime(it.startTimeMinutes) == startTime }

                when {
                    slot == null -> {
                        // Empty cell
                        drawCell(cx, rowTop, slotColW, dataRowH, rowBg)
                    }
                    slot.slotType == "break" -> {
                        // Red break cell
                        drawCell(cx, rowTop, slotColW, dataRowH, COL_RED)
                        drawText(
                            listOf(slot.breakLabel.ifBlank { "BREAK" }.uppercase()),
                            cx, rowTop, slotColW, dataRowH, 8f, bold = true, color = COL_WHITE
                        )
                    }
                    slot.lectureType.equals("practical", ignoreCase = true) -> {
                        drawCell(cx, rowTop, slotColW, dataRowH, rowBg)
                        drawText(
                            listOf("PRACTICAL"),
                            cx, rowTop, slotColW, dataRowH, 8f, bold = true
                        )
                    }
                    else -> {
                        // Regular lecture — show subject code or short name
                        drawCell(cx, rowTop, slotColW, dataRowH, rowBg)
                        val label =  slot.subjectName.take(12)
                        drawText(listOf(label), cx, rowTop, slotColW, dataRowH, 8f, bold = true)
                    }
                }
            }
        }

        pdfDoc.finishPage(page)

        // ── Save ──────────────────────────────────────────────────────────────
        val filename = "Timetable_${t.className.replace(" ", "_")}_${System.currentTimeMillis()}.pdf"
        val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS + "/Timetables"), filename)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), filename)
        }

        return try {
            file.parentFile?.mkdirs()
            pdfDoc.writeTo(FileOutputStream(file))
            pdfDoc.close()
            Toast.makeText(this, "PDF saved: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            file
        } catch (e: Exception) {
            pdfDoc.close()
            Toast.makeText(this, "PDF error: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }

    private fun getUniqueTimeSlots(t: Timetable): List<String> {
        return t.schedule.values
            .flatten()
            .map { minutesToTime(it.startTimeMinutes) }
            .distinct()
            .sortedBy { timeStringToMinutes(it) }
    }

    // ── Share ─────────────────────────────────────────────────────────────────
    private fun shareTimetable() {
        val options = arrayOf("Share as Text", "Share as PDF")
        AlertDialog.Builder(this)
            .setTitle("Share Timetable")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> shareAsText()
                    1 -> shareAsPdf()
                }
            }
            .show()
    }

    private fun shareAsText() {
        val t = timetable ?: return
        val sb = StringBuilder()
        sb.appendLine("📅 Timetable: ${t.className}")
        sb.appendLine("🏫 ${t.departmentName} | ${t.courseName}")
        sb.appendLine("📚 Semester ${t.semester} | ${t.academicYear}")
        sb.appendLine()
        DAYS.forEach { day ->
            val slots = (t.schedule[day] ?: emptyList()).sortedBy { it.startTimeMinutes }
            if (slots.isNotEmpty()) {
                sb.appendLine("── $day ──")
                slots.forEach { slot ->
                    val timeRange = "${minutesToTime(slot.startTimeMinutes)} – ${minutesToTime(slot.endTimeMinutes)}"
                    if (slot.slotType == "break") {
                        sb.appendLine("  ☕ $timeRange | ${slot.breakLabel}")
                    } else {
                        sb.appendLine("  📖 $timeRange | ${slot.subjectName} (${slot.lectureType.uppercase()})")
                        if (slot.facultyName.isNotEmpty()) sb.appendLine("       👤 ${slot.facultyName}")
                        if (slot.room.isNotEmpty()) sb.appendLine("       🚪 Room ${slot.room}")
                    }
                }
                sb.appendLine()
            }
        }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Timetable – ${t.className}")
            putExtra(Intent.EXTRA_TEXT, sb.toString())
        }
        startActivity(Intent.createChooser(shareIntent, "Share Timetable"))
    }

    private fun shareAsPdf() {
        val file = generatePdf() ?: return
        val uri: Uri = FileProvider.getUriForFile(
            this, "${applicationContext.packageName}.fileprovider", file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share PDF"))
    }

    // ── Time helpers ──────────────────────────────────────────────────────────
    private fun minutesToTime(minutes: Int): String {
        val h    = (minutes / 60) % 24
        val m    = minutes % 60
        val amPm = if (h < 12) "AM" else "PM"
        val dH   = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        return String.format("%d:%02d %s", dH, m, amPm)
    }

    private fun timeStringToMinutes(time: String): Int {
        return try {
            val parts = time.split(":", " ")
            var h     = parts[0].toInt()
            val m     = parts[1].toInt()
            val amPm  = parts[2]
            if (amPm == "PM" && h != 12) h += 12
            if (amPm == "AM" && h == 12) h = 0
            h * 60 + m
        } catch (e: Exception) { 0 }
    }
}

// ── EditTimetableActivity (unchanged) ─────────────────────────────────────────
class EditTimetableActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TIMETABLE_ID = "TIMETABLE_ID"
        private val DAYS = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    }

    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var timetableId: String

    private lateinit var rgDays: RadioGroup
    private lateinit var tvSelectedDay: TextView
    private lateinit var tvClassHeader: TextView
    private lateinit var recyclerSlots: RecyclerView
    private lateinit var btnAddLecture: MaterialButton
    private lateinit var btnAddBreak: MaterialButton
    private lateinit var btnSaveChanges: MaterialButton
    private lateinit var progressBar: View

    private var timetable: Timetable? = null
    private val editedSchedule: MutableMap<String, MutableList<TimetableSlot>> =
        DAYS.associateWith { mutableListOf<TimetableSlot>() }.toMutableMap()
    private var subjects = mutableListOf<com.projectbyyatin.synapsemis.models.Subject>()
    private var currentDay = "Monday"
    private var defaultDurationMinutes = 60
    private lateinit var slotsAdapter: SlotsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_timetable)
        timetableId = intent.getStringExtra(EXTRA_TIMETABLE_ID) ?: run { finish(); return }
        setupToolbar()
        bindViews()
        loadTimetable()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Edit Timetable"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { confirmDiscard() }
    }

    private fun bindViews() {
        rgDays         = findViewById(R.id.rg_days)
        tvSelectedDay  = findViewById(R.id.tv_selected_day)
        tvClassHeader  = findViewById(R.id.tv_class_header)
        recyclerSlots  = findViewById(R.id.recycler_slots)
        btnAddLecture  = findViewById(R.id.btn_add_lecture)
        btnAddBreak    = findViewById(R.id.btn_add_break)
        btnSaveChanges = findViewById(R.id.btn_save_timetable)
        progressBar    = findViewById(R.id.progress_bar)

        slotsAdapter = SlotsAdapter(
            slots    = emptyList(),
            onDelete = { slot -> deleteSlot(slot) },
            onEdit   = { slot -> showSlotDialog(editSlot = slot) }
        )
        recyclerSlots.layoutManager = LinearLayoutManager(this)
        recyclerSlots.adapter = slotsAdapter

        setupDaySelector()

        btnAddLecture.setOnClickListener  { showSlotDialog(isBreak = false) }
        btnAddBreak.setOnClickListener    { showSlotDialog(isBreak = true)  }
        btnSaveChanges.setOnClickListener { saveChanges() }
    }

    private fun setupDaySelector() {
        rgDays.removeAllViews()
        DAYS.forEach { day ->
            val rb = RadioButton(this).apply {
                text = day.take(3); tag = day; id = View.generateViewId()
            }
            rgDays.addView(rb)
        }
        (rgDays.getChildAt(0) as? RadioButton)?.isChecked = true
        rgDays.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            currentDay = rb?.tag as? String ?: "Monday"
            tvSelectedDay.text = currentDay
            refreshSlots()
        }
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
                val t = timetable!!
                defaultDurationMinutes = t.defaultLectureDurationMinutes
                DAYS.forEach { day ->
                    editedSchedule[day] = (t.schedule[day] ?: emptyList()).toMutableList()
                }
                tvClassHeader.text = "${t.className} | Sem ${t.semester}"
                refreshSlots()

                firestore.collection("subjects")
                    .whereEqualTo("courseId", t.courseId)
                    .whereEqualTo("semesterNumber", t.semester)
                    .whereEqualTo("isActive", true)
                    .get()
                    .addOnSuccessListener { snap ->
                        subjects.clear()
                        subjects.addAll(snap.documents.mapNotNull { d ->
                            d.toObject(com.projectbyyatin.synapsemis.models.Subject::class.java)
                                ?.also { it.id = d.id }
                        })
                    }
            }
            .addOnFailureListener { finish() }
    }

    private fun refreshSlots() {
        val slots = (editedSchedule[currentDay] ?: mutableListOf()).sortedBy { it.startTimeMinutes }
        slotsAdapter.updateSlots(slots)
    }

    private fun showSlotDialog(isBreak: Boolean = false, editSlot: TimetableSlot? = null) {
        TimetableSlotDialogHelper.show(
            activity         = this,
            subjects         = subjects,
            isBreak          = if (editSlot != null) editSlot.slotType == "break" else isBreak,
            editSlot         = editSlot,
            defaultDuration  = defaultDurationMinutes,
            currentDaySlots  = editedSchedule[currentDay] ?: mutableListOf(),
            onSave           = { slot ->
                if (editSlot != null) editedSchedule[currentDay]?.removeAll { it.id == editSlot.id }
                editedSchedule[currentDay]?.add(slot)
                refreshSlots()
            }
        )
    }

    private fun deleteSlot(slot: TimetableSlot) {
        AlertDialog.Builder(this)
            .setTitle("Delete Slot")
            .setMessage("Remove this slot from $currentDay?")
            .setPositiveButton("Delete") { _, _ ->
                editedSchedule[currentDay]?.removeAll { it.id == slot.id }
                refreshSlots()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveChanges() {
        progressBar.visibility = View.VISIBLE
        btnSaveChanges.isEnabled = false
        firestore.collection("timetables").document(timetableId)
            .update(
                mapOf(
                    "schedule"  to editedSchedule.mapValues { (_, slots) ->
                        slots.sortedBy { it.startTimeMinutes }
                    },
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Timetable updated!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                btnSaveChanges.isEnabled = true
                Toast.makeText(this, "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle("Discard Changes")
            .setMessage("Do you want to discard all unsaved changes?")
            .setPositiveButton("Discard") { _, _ -> finish() }
            .setNegativeButton("Keep Editing", null)
            .show()
    }
}