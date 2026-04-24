package com.projectbyyatin.synapsemis

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.projectbyyatin.synapsemis.models.Class
import java.text.SimpleDateFormat
import java.util.*

class DefaulterListActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()

    private val isoSdf     = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displaySdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    private lateinit var toolbar     : androidx.appcompat.widget.Toolbar

    private var currentUserDepartmentId: String = ""

    private lateinit var spinnerClass: Spinner
    private lateinit var etStartDate : TextInputEditText
    private lateinit var etEndDate   : TextInputEditText
    private lateinit var etThreshold : TextInputEditText
    private lateinit var btnGenerate : MaterialButton
    private lateinit var rvDefaulters: RecyclerView
    private lateinit var progressBar : LinearProgressIndicator
    private lateinit var tvEmpty     : TextView
    private lateinit var tvSummary   : TextView
    private lateinit var cardSummary : MaterialCardView

    private val classList    = mutableListOf<Class>()
    private var selectedClass: Class? = null
    private var startDate = ""
    private var endDate   = ""

    private val defaulterList = mutableListOf<DefaulterItem>()
    private lateinit var adapter: DefaulterAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_defaulter_list)

        currentUserDepartmentId = intent.getStringExtra("DEPARTMENT_ID") ?: ""

        bindViews()
        setupToolbar()
        setupRecyclerView()
        setupDatePickers()
        setupClickListeners()
        loadClasses()

        // Default: current month
        val cal = Calendar.getInstance()
        endDate = isoSdf.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        startDate = isoSdf.format(cal.time)
        etStartDate.setText(displaySdf.format(isoSdf.parse(startDate)!!))
        etEndDate.setText(displaySdf.format(isoSdf.parse(endDate)!!))
    }

    private fun bindViews() {
        toolbar      = findViewById(R.id.toolbar)
        spinnerClass = findViewById(R.id.spinnerClassDefaulter)
        etStartDate  = findViewById(R.id.etStartDate)
        etEndDate    = findViewById(R.id.etEndDate)
        etThreshold  = findViewById(R.id.etThreshold)
        btnGenerate  = findViewById(R.id.btnGenerateDefaulter)
        rvDefaulters = findViewById(R.id.rvDefaulters)
        progressBar  = findViewById(R.id.progressBarDefaulter)
        tvEmpty      = findViewById(R.id.tvEmptyDefaulter)
        tvSummary    = findViewById(R.id.tvDefaulterSummary)
        cardSummary  = findViewById(R.id.cardDefaulterSummary)
        etThreshold.setText("75")
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "Defaulter List"
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }
    }

    private fun setupRecyclerView() {
        adapter = DefaulterAdapter(defaulterList) { item ->
            startActivity(
                Intent(this, StudentAttendanceAnalysisActivity::class.java).apply {
                    putExtra("STUDENT_ID",   item.studentId)
                    putExtra("STUDENT_NAME", item.studentName)
                    putExtra("ROLL_NUMBER",  item.rollNumber)
                    putExtra("CLASS_ID",     selectedClass?.id ?: "")
                    putExtra("CLASS_NAME",   selectedClass?.className ?: "")
                }
            )
        }
        rvDefaulters.layoutManager = LinearLayoutManager(this)
        rvDefaulters.adapter = adapter
    }

    private fun setupDatePickers() {
        etStartDate.setOnClickListener { showDatePicker(true) }
        etEndDate.setOnClickListener   { showDatePicker(false) }
    }

    private fun showDatePicker(isStart: Boolean) {
        val cal = Calendar.getInstance()
        DatePickerDialog(this, { _, y, m, d ->
            cal.set(y, m, d)
            val raw = isoSdf.format(cal.time)
            if (isStart) { startDate = raw; etStartDate.setText(displaySdf.format(cal.time)) }
            else         { endDate   = raw; etEndDate.setText(displaySdf.format(cal.time)) }
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun setupClickListeners() {
        spinnerClass.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                selectedClass = if (pos > 0) classList[pos - 1] else null
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        btnGenerate.setOnClickListener { generateDefaulterList() }
    }

    private fun loadClasses() {
        showLoading(true)
        db.collection("classes")
            .whereEqualTo("active", true)
            .whereEqualTo("departmentId", currentUserDepartmentId)
            .get()
            .addOnSuccessListener { snapshot ->
                classList.clear()
                classList.addAll(snapshot.toObjects(Class::class.java))
                updateClassSpinner()
                showLoading(false)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                toast("Failed to load classes: ${e.message}")
            }
    }

    private fun updateClassSpinner() {
        val names = mutableListOf("-- Select Class --")
        names.addAll(classList.map { "${it.className} (${it.academicYear})" })
        spinnerClass.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, names
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // FETCH — query session docs by classId + date range,
    // then scan students[] array to build per-student per-subject aggregates
    // ─────────────────────────────────────────────────────────────────────────
    private fun generateDefaulterList() {
        val cls = selectedClass ?: run { toast("Please select a class"); return }
        if (startDate > endDate) { toast("Start date must be before end date"); return }

        val threshold = etThreshold.text?.toString()?.toFloatOrNull() ?: 75f

        showLoading(true)
        defaulterList.clear()
        adapter.notifyDataSetChanged()
        tvEmpty.visibility    = View.GONE
        cardSummary.visibility = View.GONE

        // Query all session docs for this class in the date range
        db.collection("attendance")
            .whereEqualTo("classId", cls.id)
            .whereGreaterThanOrEqualTo("date", startDate)   // "2026-02-01"
            .whereLessThanOrEqualTo("date",   endDate)      // "2026-02-28"
            .get()
            .addOnSuccessListener { sessionSnap ->
                if (sessionSnap.isEmpty) {
                    showLoading(false)
                    tvEmpty.text = "No attendance records found for this period"
                    tvEmpty.visibility = View.VISIBLE
                    return@addOnSuccessListener
                }

                // Aggregate: studentId → subjectId → { name, total, present }
                data class SubjectAgg(
                    val subjectName: String,
                    val subjectCode: String,
                    var total: Int = 0,
                    var present: Int = 0
                )
                // studentId → { name, rollNumber, subjects: Map<subjectId, SubjectAgg> }
                data class StudentData(
                    val name: String,
                    val rollNumber: String,
                    val subjects: MutableMap<String, SubjectAgg> = mutableMapOf()
                )

                val masterMap = mutableMapOf<String, StudentData>()

                sessionSnap.documents.forEach { sessionDoc ->
                    val studentsArray =
                        sessionDoc.get("students") as? List<Map<String, Any>> ?: return@forEach

                    studentsArray.forEach { studentMap ->
                        val studentId   = studentMap["studentId"]     as? String ?: return@forEach
                        val studentName = studentMap["studentName"]   as? String ?: ""
                        val rollNumber  = studentMap["studentRollNo"] as? String ?: ""
                        val subjectId   = studentMap["subjectId"]     as? String ?: return@forEach
                        val subjectName = studentMap["subjectName"]   as? String ?: "Unknown"
                        val subjectCode = studentMap["subjectCode"]   as? String ?: ""
                        val status      = studentMap["status"]        as? String ?: "absent"

                        val studentData = masterMap.getOrPut(studentId) {
                            StudentData(studentName, rollNumber)
                        }
                        val subjectAgg = studentData.subjects.getOrPut(subjectId) {
                            SubjectAgg(subjectName, subjectCode)
                        }
                        subjectAgg.total++
                        if (status == "present" || status == "late") subjectAgg.present++
                    }
                }

                // Build defaulter list
                defaulterList.clear()
                masterMap.forEach { (studentId, data) ->
                    val shortfalls = mutableListOf<SubjectShortfall>()
                    data.subjects.forEach { (subjectId, agg) ->
                        val percent = if (agg.total > 0)
                            agg.present.toFloat() / agg.total * 100f else 0f
                        if (percent < threshold) {
                            shortfalls.add(SubjectShortfall(
                                subjectId, agg.subjectName, agg.subjectCode,
                                agg.present, agg.total, percent
                            ))
                        }
                    }
                    if (shortfalls.isNotEmpty()) {
                        defaulterList.add(DefaulterItem(
                            studentId, data.name, data.rollNumber,
                            shortfalls.sortedBy { it.percent }
                        ))
                    }
                }

                defaulterList.sortBy { it.rollNumber }
                adapter.notifyDataSetChanged()
                showLoading(false)

                if (defaulterList.isEmpty()) {
                    tvEmpty.text = "No defaulters found 🎉"
                    tvEmpty.visibility = View.VISIBLE
                } else {
                    cardSummary.visibility = View.VISIBLE
                    tvSummary.text =
                        "🚨 ${defaulterList.size} student(s) below ${threshold.toInt()}%\n" +
                                "Period: ${formatDisplay(startDate)} → ${formatDisplay(endDate)}"
                }
            }
            .addOnFailureListener {
                showLoading(false)
                toast("Error loading attendance")
            }
    }

    private fun formatDisplay(d: String) =
        try { displaySdf.format(isoSdf.parse(d)!!) } catch (e: Exception) { d }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

// =============================================================================
// Models
// =============================================================================

data class SubjectShortfall(
    val subjectId   : String,
    val subjectName : String,
    val subjectCode : String,
    val attended    : Int,
    val total       : Int,
    val percent     : Float
)

data class DefaulterItem(
    val studentId         : String,
    val studentName       : String,
    val rollNumber        : String,
    val shortfallSubjects : List<SubjectShortfall>
)

// =============================================================================
// Adapter
// =============================================================================

class DefaulterAdapter(
    private val items  : List<DefaulterItem>,
    private val onClick: (DefaulterItem) -> Unit
) : RecyclerView.Adapter<DefaulterAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName : TextView        = v.findViewById(R.id.tvDefaulterName)
        val tvRoll : TextView        = v.findViewById(R.id.tvDefaulterRoll)
        val tvCount: TextView        = v.findViewById(R.id.tvDefaulterSubjectCount)
        val tvList : TextView        = v.findViewById(R.id.tvDefaulterSubjectList)
        val card   : MaterialCardView = v.findViewById(R.id.cardDefaulterItem)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_defaulter, parent, false)
        return VH(v)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val item = items[pos]
        h.tvName.text  = item.studentName
        h.tvRoll.text  = "Roll No: ${item.rollNumber}"
        h.tvCount.text = "${item.shortfallSubjects.size} subject(s) below threshold"
        h.tvList.text  = item.shortfallSubjects.joinToString("\n") {
            "• ${it.subjectName} (${it.subjectCode}) — ${"%.1f".format(it.percent)}% [${it.attended}/${it.total}]"
        }
        h.card.setOnClickListener { onClick(item) }
    }
}